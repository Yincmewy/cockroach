package yincmewy.cockroach.client;

import com.erchien.modelloader.api.model.ModelNode;
import com.erchien.modelloader.api.model.Material;
import com.erchien.modelloader.client.render.BakedModel;
import com.erchien.modelloader.client.render.BakedPart;
import com.erchien.modelloader.client.render.ShaderDetection;
import com.erchien.modelloader.client.render.ModelRenderTypes;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import yincmewy.cockroach.Config;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Uses Model Loader0's existing GPU buffers. Light/overlay changes are tiny uniforms, never VBO uploads. */
final class CockroachGpuRenderer extends RenderType {
    static ShaderInstance shader;
    private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();
    private static final Map<ResourceLocation, RenderType> TRANSLUCENT_TYPES = new HashMap<>();
    private static final Matrix4f MODEL_VIEW = new Matrix4f();
    private static final Matrix3f NORMAL = new Matrix3f();

    private CockroachGpuRenderer(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                                 boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }
    static boolean available() {
        return shader != null && !Config.COMPATIBILITY_RENDERER.get() && !ShaderDetection.isShadersActive();
    }
    static void clearRenderTypes() { TYPES.clear(); TRANSLUCENT_TYPES.clear(); }
    private static RenderType type(ResourceLocation texture, boolean translucent) {
        return (translucent ? TRANSLUCENT_TYPES : TYPES).computeIfAbsent(texture, key -> create("cockroach_static_gpu", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.TRIANGLES, 256, false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(() -> shader))
                        .setTextureState(new TextureStateShard(key, false, false))
                        .setTransparencyState(translucent ? TRANSLUCENT_TRANSPARENCY : NO_TRANSPARENCY)
                        .setWriteMaskState(translucent ? COLOR_WRITE : COLOR_DEPTH_WRITE)
                        .setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(NO_OVERLAY)
                        .createCompositeState(false)));
    }
    static void render(Mesh mesh, PoseStack pose, int packedLight, boolean hurt) {
        render(mesh, pose, packedLight, hurt, 0, 0);
    }
    static void render(Mesh mesh, PoseStack pose, int packedLight, boolean hurt, float flight, float age) {
        ShaderInstance active = shader;
        if (active == null) return;
        active.safeGetUniform("LightCoords").set(packedLight & 0xffff, (packedLight >>> 16) & 0xffff);
        active.safeGetUniform("HurtAmount").set(hurt ? 0.35F : 0.0F);
        for (Part part : mesh.parts) {
            if (part.kind == 2 && flight < 0.01F) continue;
            BakedPart baked = part.baked;
            // Full-bright is only a fixed value stored in the VBO. Actual world lighting is sampled
            // with LightCoords in our shader, so moving between light levels does not reupload geometry.
            baked.ensureUploaded(LightTexture.FULL_BRIGHT);
            RenderType renderType = type(baked.texture(), baked.material.alphaMode == Material.AlphaMode.BLEND);
            renderType.setupRenderState();
            try {
                MODEL_VIEW.set(pose.last().pose()).mul(part.local);
                animate(MODEL_VIEW, part, flight, age);
                NORMAL.set(MODEL_VIEW).invert().transpose();
                active.safeGetUniform("ModelNormalMat").set(NORMAL);
                baked.buffer().bind();
                baked.buffer().drawWithShader(MODEL_VIEW, RenderSystem.getProjectionMatrix(), active);
            } finally {
                VertexBuffer.unbind();
                renderType.clearRenderState();
            }
        }
    }

    /** Same rigid transforms for shader-pack/glowing compatibility; vertices remain cached. */
    static void renderBuffered(Mesh mesh, PoseStack pose, MultiBufferSource buffers, int light, int overlay, float flight, float age) {
        for (Part part : mesh.parts) {
            if (part.kind == 2 && flight < 0.01F) continue;
            BakedPart baked = part.baked;
            MODEL_VIEW.set(pose.last().pose()).mul(part.local);
            animate(MODEL_VIEW, part, flight, age);
            NORMAL.set(MODEL_VIEW).invert().transpose();
            RenderType type = baked.material.alphaMode == Material.AlphaMode.BLEND
                    ? ModelRenderTypes.translucent(baked.texture(), false) : ModelRenderTypes.cutout(baked.texture(), false);
            baked.emitTo(buffers.getBuffer(type), MODEL_VIEW, NORMAL, light, overlay, 1.0F);
        }
    }

    private static void animate(Matrix4f matrix, Part part, float flight, float age) {
        float side = part.side;
        if (part.kind == 1) {
            matrix.rotateY(-side * 0.95F * flight).rotateX(0.30F * flight);
        } else if (part.kind == 2) {
            float unfold = Mth.clamp((flight - 0.2F) / 0.8F, 0, 1);
            matrix.rotateZ(side * (0.15F + 0.65F * Mth.sin(age * 2.1F)) * unfold)
                    .rotateY(-side * 1.25F * unfold).scale(Math.max(0.01F, unfold));
        } else if (part.kind == 3) {
            matrix.rotateX(-0.55F * flight);
        } else if (part.kind == 4) {
            matrix.rotateZ(side * 0.5F * flight);
        } else if (part.kind == 5) {
            matrix.rotateX(0.45F * flight);
        }
    }
    static final class Mesh {
        final Part[] parts;
        Mesh(BakedModel baked) {
            Map<ModelNode, Matrix4f> matrices = new IdentityHashMap<>();
            for (ModelNode root : baked.source.roots) cacheTransforms(root, new Matrix4f(), matrices);
            parts = new Part[baked.parts.size()];
            for (int i = 0; i < parts.length; i++) {
                BakedPart part = baked.parts.get(i);
                String name = part.node.name;
                int kind = name.startsWith("elytron_") ? 1 : name.startsWith("wing_") ? 2
                        : name.startsWith("leg_") ? (name.endsWith("front") ? 3 : name.endsWith("middle") ? 4 : 5) : 0;
                parts[i] = new Part(part, new Matrix4f(matrices.getOrDefault(part.node, new Matrix4f())), kind, name.contains("left") ? -1 : 1);
            }
        }
        private static void cacheTransforms(ModelNode node, Matrix4f parent, Map<ModelNode, Matrix4f> out) {
            Matrix4f world = new Matrix4f(parent).mul(node.localMatrix());
            out.put(node, world);
            for (ModelNode child : node.children) cacheTransforms(child, world, out);
        }
    }
    private record Part(BakedPart baked, Matrix4f local, int kind, int side) {}
}
