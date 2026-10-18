package yincmewy.cockroach.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.world.entity.LightningBolt;

/** Reuse all vanilla bolt geometry, replacing only its lightning RenderType. */
public final class RainbowLightningRenderer extends LightningBoltRenderer {
    static ShaderInstance shader;

    private static final class RainbowRenderType extends RenderType {
        private static final RenderType TYPE = create("cockroach_rainbow_lightning",
                DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 1536, false, false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTransparencyState(ADDITIVE_TRANSPARENCY)
                        .setCullState(NO_CULL).setLightmapState(NO_LIGHTMAP)
                        .setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private RainbowRenderType() {
            super("cockroach_rainbow_lightning", DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS, 1536, false, false, () -> {}, () -> {});
        }
    }

    public RainbowLightningRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(LightningBolt entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight) {
        if (shader == null) return;
        MultiBufferSource.BufferSource immediate = buffers instanceof MultiBufferSource.BufferSource source
                ? source : Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType type = RainbowRenderType.TYPE;
        // Flush each bolt so another entity cannot overwrite its height before the draw.
        immediate.endBatch(type);
        shader.safeGetUniform("FlowTime").set((System.currentTimeMillis() % 1_000_000L) / 1000.0F);
        shader.safeGetUniform("EntityWorldY").set((float) entity.getY());
        MultiBufferSource proxy = requested -> requested == RenderType.lightning()
                ? immediate.getBuffer(type) : buffers.getBuffer(requested);
        super.render(entity, yaw, partialTick, pose, proxy, packedLight);
        immediate.endBatch(type);
    }
}
