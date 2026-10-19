package cn.blockforge.generated.moda3c26b8e.client;

import cn.blockforge.generated.moda3c26b8e.TorchConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

/** 模组列表"配置"按钮的入口;改动即时生效,关闭时写回客户端 TOML。 */
public final class TorchConfigScreen extends Screen {
    private final Screen parent;

    public TorchConfigScreen(Screen parent) {
        super(Component.translatable("screen.z80z_throwable_torch.config.title"));
        this.parent = parent;
    }
    private static Component onOff(boolean value) {
        return Component.translatable(value ? "options.on" : "options.off");
    }
    private static Component animationLabel() {
        return Component.translatable("screen.z80z_throwable_torch.config.animation." + TorchConfig.ANIMATION.get());
    }
    @Override protected void init() {
        int left = this.width / 2 - 100;
        int top = Math.max(50, this.height / 2 - 70);
        addRenderableWidget(Button.builder(
                Component.translatable("screen.z80z_throwable_torch.config.animation", animationLabel()),
                button -> {
                    TorchConfig.AnimationStyle[] styles = TorchConfig.AnimationStyle.values();
                    TorchConfig.ANIMATION.set(styles[(TorchConfig.ANIMATION.get().ordinal() + 1) % styles.length]);
                    button.setMessage(Component.translatable("screen.z80z_throwable_torch.config.animation", animationLabel()));
                }).bounds(left, top, 200, 20).build());
        top = toggle(left, top + 24, "screen.z80z_throwable_torch.config.trail", TorchConfig.TRAIL);
        top = toggle(left, top, "screen.z80z_throwable_torch.config.sparks", TorchConfig.SPARKS);
        top = toggle(left, top, "screen.z80z_throwable_torch.config.chargeBar", TorchConfig.CHARGE_BAR);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left, top + 14, 200, 20).build());
    }
    /** 开关按钮:点击翻转取值并刷新文案,返回下一行的纵坐标。 */
    private int toggle(int x, int y, String key, ForgeConfigSpec.BooleanValue value) {
        addRenderableWidget(Button.builder(Component.translatable(key, onOff(value.get())), button -> {
            boolean next = !value.get();
            value.set(next);
            button.setMessage(Component.translatable(key, onOff(next)));
        }).bounds(x, y, 200, 20).build());
        return y + 24;
    }
    @Override public void onClose() {
        try { TorchConfig.SPEC.save(); } catch (Exception ignored) {}
        this.minecraft.setScreen(parent);
    }
    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderBackground(gui);
        super.render(gui, mouseX, mouseY, partialTick);
        gui.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }
}
