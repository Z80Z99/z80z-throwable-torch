package com.github.z80z.throwabletorch.client;

import com.github.z80z.throwabletorch.GeneratedMod;
import com.github.z80z.throwabletorch.ThrownTorch;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GeneratedMod.MOD_ID, value = Dist.CLIENT)
public final class TorchRenderer extends EntityRenderer<ThrownTorch> {
    public TorchRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.08F;
    }
    @Override public void render(ThrownTorch entity, float yaw, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int packedLight) {
        pose.pushPose();
        Vec3 motion = entity.getDeltaMovement();
        float facing = motion.horizontalDistanceSqr() > 1.0E-6
                ? (float) (Math.atan2(motion.x, motion.z) * (180.0 / Math.PI)) : yaw;
        pose.mulPose(Axis.YP.rotationDegrees(facing));
        // 沿飞行方向前空翻,按游戏刻匀速推进,与显示帧率无关。
        float tumble = (entity.tickCount + partialTick) * 24.0F + entity.getId() * 47.0F;
        pose.mulPose(Axis.XP.rotationDegrees(tumble));
        pose.scale(1.25F, 1.25F, 1.25F);
        pose.translate(-0.5, -0.3125, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.TORCH.defaultBlockState(),
                pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, packedLight);
    }
    @Override public ResourceLocation getTextureLocation(ThrownTorch entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
