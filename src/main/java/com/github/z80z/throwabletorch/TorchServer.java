package com.github.z80z.throwabletorch;

import com.github.z80z.throwabletorch.api.TorchAnimationEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = GeneratedMod.MOD_ID)
public final class TorchServer {
    public static final int DELAY = 10;
    public static final int FULL_CHARGE = 30;
    public static final int MAX_HOLD = 1200;
    private static final Map<UUID, Charge> CHARGES = new HashMap<>();

    private static final class Charge {
        final InteractionHand hand;
        final ItemStack stack;
        final int slot;
        final int started;
        final ResourceKey<Level> dimension;
        boolean announced;
        Charge(ServerPlayer player, InteractionHand hand) {
            this.hand = hand;
            stack = player.getItemInHand(hand);
            slot = player.getInventory().selected;
            started = player.tickCount;
            dimension = player.level().dimension();
        }
        boolean valid(ServerPlayer player) {
            return eligible(player, hand) && player.level().dimension().equals(dimension)
                    && player.getItemInHand(hand) == stack
                    && (hand != InteractionHand.MAIN_HAND || player.getInventory().selected == slot)
                    && player.tickCount - started <= MAX_HOLD;
        }
    }
    public static float power(int ticks) {
        return Mth.clamp((ticks - DELAY) / (float) (FULL_CHARGE - DELAY), 0.0F, 1.0F);
    }
    private static boolean eligible(ServerPlayer player, InteractionHand hand) {
        return player.isAlive() && !player.isSpectator() && player.mayBuild()
                && !player.isUsingItem() && !player.isSleeping() && !player.isShiftKeyDown()
                && player.containerMenu == player.inventoryMenu
                && player.getItemInHand(hand).is(Items.TORCH)
                && !player.getCooldowns().isOnCooldown(Items.TORCH);
    }
    public static void handle(ServerPlayer player, TorchNetwork.Control packet) {
        if (packet.action() == TorchNetwork.Action.START) {
            if (!CHARGES.containsKey(player.getUUID()) && eligible(player, packet.hand())) {
                CHARGES.put(player.getUUID(), new Charge(player, packet.hand()));
            }
            return;
        }
        Charge charge = CHARGES.remove(player.getUUID());
        if (charge == null) return;
        int elapsed = player.tickCount - charge.started;
        if (packet.action() == TorchNetwork.Action.CANCEL || charge.hand != packet.hand()
                || !charge.valid(player) || elapsed < DELAY) {
            announce(player, charge.hand, TorchAnimationEvent.Phase.CANCEL, 0);
            return;
        }
        float power = power(elapsed);
        ThrownTorch torch = new ThrownTorch(player.level(), player, charge.stack, !player.getAbilities().instabuild);
        torch.shootFromRotation(player, player.getXRot(), player.getYRot(), -4.0F,
                0.65F + power * 1.25F, 0.35F);
        // 从眼前一段距离出手,避免投射物与火星贴在摄像机上;贴墙时按视线净空缩短,防止生成进墙体。
        double offset = 0.8;
        HitResult probe = player.pick(0.85, 1.0F, true);
        if (probe.getType() == HitResult.Type.BLOCK) {
            offset = Mth.clamp(probe.getLocation().distanceTo(player.getEyePosition()) - 0.15, 0.2, offset);
        }
        Vec3 look = player.getLookAngle();
        torch.setPos(player.getX() + look.x * offset,
                player.getEyeY() + look.y * offset - 0.08,
                player.getZ() + look.z * offset);
        if (!player.level().addFreshEntity(torch)) {
            announce(player, charge.hand, TorchAnimationEvent.Phase.CANCEL, 0);
            return;
        }
        if (!player.getAbilities().instabuild) charge.stack.shrink(1);
        player.awardStat(Stats.ITEM_USED.get(Items.TORCH));
        player.getCooldowns().addCooldown(Items.TORCH, 8);
        player.swing(charge.hand, true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.75F, 0.8F + power * 0.25F);
        announce(player, charge.hand, TorchAnimationEvent.Phase.RELEASE, power);
    }
    private static void announce(ServerPlayer player, InteractionHand hand, TorchAnimationEvent.Phase phase, float power) {
        MinecraftForge.EVENT_BUS.post(new TorchAnimationEvent(player, hand, phase, power));
        TorchNetwork.animate(player, hand, phase, power);
    }
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Charge charge = CHARGES.get(player.getUUID());
        if (charge == null) return;
        if (!charge.valid(player)) {
            CHARGES.remove(player.getUUID());
            announce(player, charge.hand, TorchAnimationEvent.Phase.CANCEL, 0);
        } else if (!charge.announced && player.tickCount - charge.started >= DELAY) {
            charge.announced = true;
            announce(player, charge.hand, TorchAnimationEvent.Phase.START, 0);
        }
    }
    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        CHARGES.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        CHARGES.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        CHARGES.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent
    public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof ServerPlayer target) {
            Charge charge = CHARGES.get(target.getUUID());
            if (charge != null && charge.announced) {
                TorchNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                        new TorchNetwork.Animation(target.getUUID(), charge.hand, TorchAnimationEvent.Phase.START,
                                power(target.tickCount - charge.started)));
            }
        }
    }
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { CHARGES.clear(); }
    private TorchServer() {}
}
