package cn.blockforge.generated.moda3c26b8e.client;

import cn.blockforge.generated.moda3c26b8e.GeneratedMod;
import cn.blockforge.generated.moda3c26b8e.TorchNetwork;
import cn.blockforge.generated.moda3c26b8e.TorchServer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeneratedMod.MOD_ID, value = Dist.CLIENT)
public final class TorchInput {
    private static InteractionHand hand;
    private static ItemStack heldStack;
    private static int started;
    private static int slot;
    private static boolean blockedUntilRelease;
    private static boolean announced;
    private static Object levelIdentity;

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void interact(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (event.isAttack() && hand != null) {
            cancel(true);
            return;
        }
        if (!event.isUseItem() || player == null || mc.gameMode == null || mc.screen != null) return;
        if (hand != null || blockedUntilRelease) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (!player.isAlive() || player.isSpectator() || !player.mayBuild()
                || player.isShiftKeyDown() || player.isUsingItem()
                || !player.getItemInHand(event.getHand()).is(Items.TORCH)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (player.getCooldowns().isOnCooldown(Items.TORCH)) {
            blockedUntilRelease = true;
            return;
        }
        hand = event.getHand();
        heldStack = player.getItemInHand(hand);
        slot = player.getInventory().selected;
        started = player.tickCount;
        announced = false;
        levelIdentity = mc.level;
        // 本次拦截早于原版 useItem，先同步快捷栏，避免刚换到火把就按住时服务器仍看到旧物品。
        player.connection.send(new ServerboundSetCarriedItemPacket(slot));
        TorchNetwork.control(TorchNetwork.Action.START, hand);
    }
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != levelIdentity) {
            // 切世界时不将旧世界的短按/动画带进新世界。
            if (hand != null) cancel(false);
            TorchAnimations.clear();
            TorchTrail.clear();
            levelIdentity = mc.level;
        }
        if (!mc.options.keyUse.isDown()) blockedUntilRelease = false;
        if (hand == null) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || mc.screen != null
                || !mc.isWindowActive() || !player.isAlive() || player.isSpectator()
                || player.isShiftKeyDown() || player.isUsingItem() || player.isSleeping()
                || player.getItemInHand(hand) != heldStack
                || !heldStack.is(Items.TORCH)
                || (hand == InteractionHand.MAIN_HAND && player.getInventory().selected != slot)
                || elapsed() > TorchServer.MAX_HOLD) {
            cancel(true);
            return;
        }
        int ticks = elapsed();
        if (!mc.options.keyUse.isDown()) {
            InteractionHand released = hand;
            hand = null;
            heldStack = null;
            if (ticks >= TorchServer.DELAY) {
                TorchNetwork.control(TorchNetwork.Action.RELEASE, released);
            } else {
                TorchNetwork.control(TorchNetwork.Action.CANCEL, released);
                shortClick(mc, released);
            }
            return;
        }
        if (!announced && ticks >= TorchServer.DELAY) {
            announced = true;
            TorchAnimations.startLocal(player, hand);
        }
    }
    private static void shortClick(Minecraft mc, InteractionHand usedHand) {
        if (mc.player == null || mc.gameMode == null) return;
        InteractionResult result = InteractionResult.PASS;
        HitResult target = mc.hitResult;
        if (target instanceof BlockHitResult block && target.getType() == HitResult.Type.BLOCK) {
            result = mc.gameMode.useItemOn(mc.player, usedHand, block);
        } else if (target instanceof EntityHitResult entity) {
            result = mc.gameMode.interactAt(mc.player, entity.getEntity(), entity, usedHand);
            if (!result.consumesAction()) result = mc.gameMode.interact(mc.player, entity.getEntity(), usedHand);
        }
        if (result == InteractionResult.PASS) result = mc.gameMode.useItem(mc.player, usedHand);
        if (result.shouldSwing()) mc.player.swing(usedHand);
    }
    private static void cancel(boolean send) {
        if (hand != null) {
            Minecraft mc = Minecraft.getInstance();
            if (send && mc.getConnection() != null) TorchNetwork.control(TorchNetwork.Action.CANCEL, hand);
            if (mc.player != null) TorchAnimations.cancelLocal(mc.player, hand);
        }
        hand = null;
        heldStack = null;
        blockedUntilRelease = true;
    }
    public static int elapsed() {
        LocalPlayer player = Minecraft.getInstance().player;
        return hand == null || player == null ? 0 : Math.max(0, player.tickCount - started);
    }
    public static boolean isCharging() { return hand != null && elapsed() >= TorchServer.DELAY; }
    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        if (event.getItemStack().is(Items.TORCH)) {
            event.getToolTip().add(Component.translatable("tooltip.mod_a3c26b8e.throw").withStyle(ChatFormatting.GOLD));
            event.getToolTip().add(Component.translatable("tooltip.mod_a3c26b8e.place").withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.translatable("tooltip.mod_a3c26b8e.cancel").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        cancel(false);
        blockedUntilRelease = false;
        levelIdentity = null;
        TorchAnimations.clear();
        TorchTrail.clear();
    }
    private TorchInput() {}
}
