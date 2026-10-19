package cn.blockforge.generated.moda3c26b8e.client;

import cn.blockforge.generated.moda3c26b8e.GeneratedMod;
import cn.blockforge.generated.moda3c26b8e.ThrownTorch;
import cn.blockforge.generated.moda3c26b8e.TorchConfig;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;

@Mod.EventBusSubscriber(modid = GeneratedMod.MOD_ID, value = Dist.CLIENT)
public final class TorchTrail extends RenderType {
    private record Segment(Vec3 from, Vec3 to, long born) {}
    private static final Deque<Segment> SEGMENTS = new ArrayDeque<>();
    private static final RenderType GLOW = create("z80z_throwable_torch_trail", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, true,
            CompositeState.builder().setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(ADDITIVE_TRANSPARENCY).setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));
    private TorchTrail() {
        super("unused", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> {});
    }
    public static void sample(ThrownTorch.FlightSample sample) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !TorchConfig.TRAIL.get() || sample.from().distanceToSqr(sample.to()) < 0.00001) return;
        if (SEGMENTS.size() >= 4096) SEGMENTS.removeFirst();
        SEGMENTS.addLast(new Segment(sample.from(), sample.to(), mc.level.getGameTime()));
    }
    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { clear(); return; }
        long now = mc.level.getGameTime();
        SEGMENTS.removeIf(segment -> now - segment.born >= 6 || now < segment.born);
        if (!TorchConfig.TRAIL.get() || SEGMENTS.isEmpty()) return;
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = pose.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(GLOW);
        for (Segment segment : SEGMENTS) {
            if (segment.to.distanceToSqr(camera) > 128 * 128) continue;
            float fade = Mth.clamp(1.0F - (now - segment.born + event.getPartialTick()) / 6.0F, 0, 1);
            Vec3 direction = segment.to.subtract(segment.from);
            Vec3 side = direction.cross(camera.subtract(segment.from)).normalize();
            if (side.lengthSqr() < 0.0001) side = new Vec3(0.025, 0, 0);
            ribbon(out, matrix, segment, side.scale(0.042), 255, 94, 12, (int) (70 * fade));
            ribbon(out, matrix, segment, side.scale(0.012), 255, 205, 100, (int) (200 * fade));
        }
        buffers.endBatch(GLOW);
        pose.popPose();
    }
    private static void ribbon(VertexConsumer out, Matrix4f matrix, Segment segment, Vec3 side,
                               int red, int green, int blue, int alpha) {
        vertex(out, matrix, segment.from.add(side), red, green, blue, alpha);
        vertex(out, matrix, segment.to.add(side), red, green, blue, alpha);
        vertex(out, matrix, segment.to.subtract(side), red, green, blue, alpha);
        vertex(out, matrix, segment.from.subtract(side), red, green, blue, alpha);
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 pos, int red, int green, int blue, int alpha) {
        out.vertex(matrix, (float) pos.x, (float) pos.y + 0.1F, (float) pos.z).color(red, green, blue, alpha).endVertex();
    }
    public static void clear() { SEGMENTS.clear(); }
}
