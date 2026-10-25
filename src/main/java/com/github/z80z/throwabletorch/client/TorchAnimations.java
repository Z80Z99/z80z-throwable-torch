package com.github.z80z.throwabletorch.client;

import com.github.z80z.throwabletorch.GeneratedMod;
import com.github.z80z.throwabletorch.TorchConfig;
import com.github.z80z.throwabletorch.TorchNetwork;
import com.github.z80z.throwabletorch.TorchServer;
import com.github.z80z.throwabletorch.api.TorchAnimationEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = GeneratedMod.MOD_ID, value = Dist.CLIENT)
public final class TorchAnimations {
    private record Animation(InteractionHand hand, TorchAnimationEvent.Phase phase, int start, float power) {
        float age(Player player, float partial) { return player.tickCount - start + partial; }
        float charge(Player player, float partial) {
            return phase == TorchAnimationEvent.Phase.RELEASE ? power
                    : Mth.clamp(age(player, partial) / (TorchServer.FULL_CHARGE - TorchServer.DELAY), 0, 1);
        }
        float armRotation(Player player, float partial) {
            if (phase == TorchAnimationEvent.Phase.START) {
                float pull = Mth.clamp(age(player, partial) / 6.0F, 0, 1);
                return Mth.lerp(pull, -0.3F, -2.65F - charge(player, partial) * 0.35F);
            }
            float t = Mth.clamp(age(player, partial) / 8.0F, 0, 1);
            // 前三刻过肩前甩，后五刻回到自然持物姿势。
            return t < 0.375F ? Mth.lerp(t / 0.375F, -2.65F - power * 0.35F, -0.65F)
                    : Mth.lerp((t - 0.375F) / 0.625F, -0.65F, -0.3F);
        }
    }
    private record SavedPose(HumanoidModel.ArmPose left, HumanoidModel.ArmPose right) {}
    private static final Map<UUID, Animation> ANIMATIONS = new HashMap<>();
    private static final Map<HumanoidModel<?>, SavedPose> SAVED = new IdentityHashMap<>();
    private static final HumanoidModel.ArmPose THROW_POSE = HumanoidModel.ArmPose.create(
            "MOD_A3C26B8E_THROW", false, (model, entity, arm) -> {
                if (!(entity instanceof Player player)) return;
                Animation animation = current(player);
                if (animation == null) return;
                float partial = Minecraft.getInstance().getFrameTime();
                ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
                part.xRot = animation.armRotation(player, partial) + model.head.xRot * 0.12F;
                part.yRot = (arm == HumanoidArm.RIGHT ? -1 : 1) * 0.16F;
                part.zRot = (arm == HumanoidArm.RIGHT ? 1 : -1) * 0.13F;
            });

    public static void receive(TorchNetwork.Animation packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Player player = mc.level.getPlayerByUUID(packet.playerId());
        if (player == null) return;
        if (packet.phase() == TorchAnimationEvent.Phase.CANCEL) {
            Animation removed = ANIMATIONS.remove(packet.playerId());
            if (removed != null) post(player, packet.hand(), packet.phase(), packet.charge());
        } else if (packet.phase() == TorchAnimationEvent.Phase.START) {
            Animation existing = ANIMATIONS.get(packet.playerId());
            // 本地预测已经开始时，不重复触发接管事件或重置进度。
            if (existing == null || existing.phase != TorchAnimationEvent.Phase.START) {
                int offset = Math.round(packet.charge() * (TorchServer.FULL_CHARGE - TorchServer.DELAY));
                ANIMATIONS.put(packet.playerId(), new Animation(packet.hand(), packet.phase(), player.tickCount - offset, 0));
                post(player, packet.hand(), packet.phase(), packet.charge());
            }
        } else {
            ANIMATIONS.put(packet.playerId(), new Animation(packet.hand(), packet.phase(), player.tickCount, packet.charge()));
            post(player, packet.hand(), packet.phase(), packet.charge());
        }
    }
    public static void startLocal(Player player, InteractionHand hand) {
        Animation existing = ANIMATIONS.get(player.getUUID());
        if (existing == null || existing.phase != TorchAnimationEvent.Phase.START) {
            ANIMATIONS.put(player.getUUID(), new Animation(hand, TorchAnimationEvent.Phase.START, player.tickCount, 0));
            post(player, hand, TorchAnimationEvent.Phase.START, 0);
        }
    }
    public static void cancelLocal(Player player, InteractionHand hand) {
        if (ANIMATIONS.remove(player.getUUID()) != null) post(player, hand, TorchAnimationEvent.Phase.CANCEL, 0);
    }
    private static void post(Player player, InteractionHand hand, TorchAnimationEvent.Phase phase, float power) {
        MinecraftForge.EVENT_BUS.post(new TorchAnimationEvent(player, hand, phase, power));
    }
    private static Animation current(Player player) {
        Animation animation = ANIMATIONS.get(player.getUUID());
        if (animation != null && (!player.isAlive() || player.isSpectator()
                || (animation.phase == TorchAnimationEvent.Phase.RELEASE && animation.age(player, 0) >= 8)
                || animation.age(player, 0) > TorchServer.MAX_HOLD)) {
            ANIMATIONS.remove(player.getUUID());
            return null;
        }
        return animation;
    }
    private static boolean shouldPose(Player player, Animation animation, boolean firstPerson, float partial) {
        boolean canceled = MinecraftForge.EVENT_BUS.post(new TorchAnimationEvent.Pose(player, animation.hand,
                animation.phase, animation.charge(player, partial), firstPerson, partial));
        return !canceled && TorchConfig.ANIMATION.get() == TorchConfig.AnimationStyle.OVER_SHOULDER
                && !player.isSwimming() && !player.isFallFlying() && !player.isSleeping();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void thirdPerson(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)
                || !(event.getRenderer().getModel() instanceof HumanoidModel<?> model)) return;
        Animation animation = current(player);
        if (animation == null || !shouldPose(player, animation, false, event.getPartialTick())) return;
        SAVED.put(model, new SavedPose(model.leftArmPose, model.rightArmPose));
        HumanoidArm arm = animation.hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        // 此事件在 PlayerRenderer 设置持物姿势之后、模型 setupAnim 之前触发。
        if (arm == HumanoidArm.RIGHT) model.rightArmPose = THROW_POSE;
        else model.leftArmPose = THROW_POSE;
    }
    @SubscribeEvent
    public static void restoreThirdPerson(RenderLivingEvent.Post<?, ?> event) {
        if (event.getRenderer().getModel() instanceof HumanoidModel<?> model) {
            SavedPose saved = SAVED.remove(model);
            if (saved != null) { model.leftArmPose = saved.left; model.rightArmPose = saved.right; }
        }
    }
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void firstPerson(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null) return;
        Animation animation = current(player);
        if (animation == null || event.getHand() != animation.hand
                || !shouldPose(player, animation, true, event.getPartialTick())) return;
        event.setCanceled(true);
        HumanoidArm arm = animation.hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean right = arm == HumanoidArm.RIGHT;
        int side = right ? 1 : -1;
        float amount = Mth.clamp((-animation.armRotation(player, event.getPartialTick()) - 0.3F) / 2.7F, 0, 1);
        // 第一人称只动画手上的物品:固定变换的悬浮手臂无法在全部蓄力阶段贴合物品,故不绘制。
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(side * amount * 0.12F, amount * 0.23F, amount * 0.24F);
        pose.mulPose(Axis.XP.rotationDegrees(-amount * 48));
        pose.mulPose(Axis.ZP.rotationDegrees(side * -amount * 12));
        mc.gameRenderer.itemInHandRenderer.renderItem(player, event.getItemStack(),
                right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                !right, pose, event.getMultiBufferSource(), event.getPackedLight());
        pose.popPose();
    }
    @SubscribeEvent
    public static void cleanup(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.level == null) return;
        ANIMATIONS.entrySet().removeIf(entry -> {
            Player player = mc.level.getPlayerByUUID(entry.getKey());
            if (player == null || !player.isAlive()) return true;
            Animation animation = entry.getValue();
            float age = animation.age(player, 0);
            return age < -TorchServer.FULL_CHARGE || age > TorchServer.MAX_HOLD
                    || (animation.phase == TorchAnimationEvent.Phase.RELEASE && age >= 8);
        });
    }
    public static void clear() { ANIMATIONS.clear(); SAVED.clear(); }
    private TorchAnimations() {}
}
