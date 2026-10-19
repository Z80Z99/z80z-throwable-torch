package cn.blockforge.generated.moda3c26b8e.client;

import cn.blockforge.generated.moda3c26b8e.GeneratedMod;
import cn.blockforge.generated.moda3c26b8e.TorchConfig;
import cn.blockforge.generated.moda3c26b8e.TorchServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeneratedMod.MOD_ID, value = Dist.CLIENT)
public final class TorchHud {
    @SubscribeEvent
    public static void render(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())
                || !TorchConfig.CHARGE_BAR.get() || !TorchInput.isCharging()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        GuiGraphics gui = event.getGuiGraphics();
        int center = event.getWindow().getGuiScaledWidth() / 2;
        int y = event.getWindow().getGuiScaledHeight() / 2 + 22;
        float power = TorchServer.power(TorchInput.elapsed());
        int width = Math.max(1, Math.round(80 * power));
        gui.fill(center - 42, y - 2, center + 42, y + 7, 0xB0201712);
        gui.fill(center - 40, y, center + 40, y + 5, 0xFF493323);
        gui.fill(center - 40, y, center - 40 + width, y + 5, power >= 1 ? 0xFFFFDF91 : 0xFFFFA33A);
        gui.drawCenteredString(mc.font, Component.translatable("hud.mod_a3c26b8e.charge", Math.round(power * 100)),
                center, y + 10, 0xFFE2B2);
    }
    private TorchHud() {}
}
