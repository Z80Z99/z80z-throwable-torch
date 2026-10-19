package cn.blockforge.generated.moda3c26b8e.client;

import cn.blockforge.generated.moda3c26b8e.GeneratedMod;
import cn.blockforge.generated.moda3c26b8e.ThrownTorch;
import cn.blockforge.generated.moda3c26b8e.TorchNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = GeneratedMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            TorchNetwork.clientAnimation = TorchAnimations::receive;
            ThrownTorch.clientTrail = TorchTrail::sample;
            // 注册后模组列表的"配置"按钮才可点击。
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory(
                            (minecraft, parent) -> new TorchConfigScreen(parent)));
        });
    }
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GeneratedMod.THROWN_TORCH.get(), TorchRenderer::new);
    }
    private ClientSetup() {}
}
