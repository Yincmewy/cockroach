package yincmewy.cockroach.client;

import com.erchien.modelloader.client.render.ModelRenderTypes;
import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import yincmewy.cockroach.Cockroach;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** GPU weighted skinning of the original Take 001 clip, shared by every entity and all LODs. */
final class CockroachWalkRenderer extends RenderType {
    static final int JOINTS = 31;
    static final VertexFormat FORMAT = new VertexFormat(ImmutableMap.<String, VertexFormatElement>builder()
            .put("Position", element(VertexFormatElement.Usage.POSITION, 3))
            .put("Normal", element(VertexFormatElement.Usage.NORMAL, 3))
            .put("UV0", element(VertexFormatElement.Usage.UV, 2))
            .put("Joints", element(VertexFormatElement.Usage.GENERIC, 4))
            .put("Weights", element(VertexFormatElement.Usage.GENERIC, 4)).build());
    static ShaderInstance shader;
    private static final ResourceLocation DATA = ResourceLocation.fromNamespaceAndPath(Cockroach.MODID, "models/entity/cockroach_walk.bin");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Cockroach.MODID, "models/entity/cockroach_basecolor.png");
    private static final RenderType TYPE = create("cockroach_walk_gpu", FORMAT, VertexFormat.Mode.TRIANGLES, 256,
            false, false, CompositeState.builder().setShaderState(new ShaderStateShard(() -> shader))
                    .setTextureState(new TextureStateShard(TEXTURE, false, false)).setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP).setOverlayState(NO_OVERLAY).createCompositeState(false));
    private static Mesh[] meshes;
    private static float[] poses;
    private static int frames;
    private static boolean failed;
    private static final float[] BONES = new float[JOINTS * 16];
    private static final Matrix3f NORMAL = new Matrix3f();

    private CockroachWalkRenderer() {
        super("cockroach_walk_gpu", FORMAT, VertexFormat.Mode.TRIANGLES, 256, false, false, () -> {}, () -> {});
    }

    private static VertexFormatElement element(VertexFormatElement.Usage usage, int count) {
        return new VertexFormatElement(0, VertexFormatElement.Type.FLOAT, usage, count);
    }

    static boolean ready() {
        if (meshes != null) return true;
        if (failed) return false;
        try (var stream = Minecraft.getInstance().getResourceManager().open(DATA)) {
            ByteBuffer data = ByteBuffer.wrap(stream.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
            if (data.getInt() != 0x314b5743) throw new IllegalStateException("Invalid walk cache header");
            frames = data.getInt();
            int joints = data.getInt();
            float duration = data.getFloat();
            int lods = data.getInt();
            if (frames != 48 || joints != JOINTS || lods != 3 || duration <= 0) throw new IllegalStateException("Invalid source animation metadata");
            poses = floats(data, frames * joints * 16);
            Mesh[] loaded = new Mesh[lods];
            for (int lod = 0; lod < lods; lod++) {
                int vertices = data.getInt(), indices = data.getInt();
                if (vertices <= 0 || vertices > 100000 || indices <= 0 || indices > 500000) throw new IllegalStateException("Invalid walk geometry");
                float[] attributes = floats(data, vertices * 16);
                int[] triangles = new int[indices];
                for (int i = 0; i < indices; i++) {
                    triangles[i] = data.getInt();
                    if (triangles[i] < 0 || triangles[i] >= vertices) throw new IllegalStateException("Invalid walk index");
                }
                loaded[lod] = new Mesh(attributes, triangles);
            }
            if (data.hasRemaining()) throw new IllegalStateException("Unexpected walk data");
            meshes = loaded;
            return true;
        } catch (Exception error) {
            failed = true;
            Cockroach.LOGGER.error("Could not load original cockroach walking animation; using static model until resource reload", error);
            return false;
        }
    }

    private static float[] floats(ByteBuffer data, int count) {
        float[] result = new float[count];
        for (int i = 0; i < count; i++) result[i] = data.getFloat();
        return result;
    }

    static void reset() {
        if (meshes != null) for (Mesh mesh : meshes) if (mesh.buffer != null) mesh.buffer.close();
        meshes = null;
        poses = null;
        failed = false;
    }

    private static void sample(float phase) {
        float frame = (phase - (float)Math.floor(phase)) * frames;
        int first = (int)frame, second = (first + 1) % frames;
        float alpha = frame - first;
        for (int i = 0; i < BONES.length; i++) {
            float a = poses[first * BONES.length + i];
            BONES[i] = a + (poses[second * BONES.length + i] - a) * alpha;
        }
    }

    static void render(int lod, PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                       boolean hurt, float phase, float amount, boolean gpu) {
        if (!ready()) return;
        sample(phase);
        Mesh mesh = meshes[lod];
        if (!gpu || shader == null) {
            renderBuffered(mesh, pose, buffers, light, overlay, amount);
            return;
        }
        mesh.upload();
        shader.safeGetUniform("SkinMatrices").set(BONES);
        shader.safeGetUniform("WalkAmount").set(amount);
        shader.safeGetUniform("LightCoords").set(light & 0xffff, (light >>> 16) & 0xffff);
        shader.safeGetUniform("HurtAmount").set(hurt ? 0.35F : 0);
        NORMAL.set(pose.last().pose()).invert().transpose();
        shader.safeGetUniform("ModelNormalMat").set(NORMAL);
        TYPE.setupRenderState();
        try {
            mesh.buffer.bind();
            mesh.buffer.drawWithShader(pose.last().pose(), RenderSystem.getProjectionMatrix(), shader);
        } finally {
            VertexBuffer.unbind();
            TYPE.clearRenderState();
        }
    }

    private static void renderBuffered(Mesh mesh, PoseStack pose, MultiBufferSource buffers, int light, int overlay, float amount) {
        mesh.skin(amount);
        var consumer = buffers.getBuffer(ModelRenderTypes.cutout(TEXTURE, false));
        Matrix4f matrix = pose.last().pose();
        NORMAL.set(matrix).invert().transpose();
        for (int index : mesh.indices) {
            int at = index * 6, raw = index * 16;
            consumer.vertex(matrix, mesh.skinned[at], mesh.skinned[at + 1], mesh.skinned[at + 2])
                    .color(1F, 1F, 1F, 1F).uv(mesh.vertices[raw + 6], mesh.vertices[raw + 7])
                    .overlayCoords(overlay).uv2(light)
                    .normal(NORMAL, mesh.skinned[at + 3], mesh.skinned[at + 4], mesh.skinned[at + 5]).endVertex();
        }
    }

    private static final class Mesh {
        private final float[] vertices;
        private final int[] indices;
        private final float[] skinned;
        private VertexBuffer buffer;
        private Mesh(float[] vertices, int[] indices) {
            this.vertices = vertices;
            this.indices = indices;
            skinned = new float[vertices.length / 16 * 6];
        }
        private void upload() {
            if (buffer != null) return;
            ByteBuffer bytes = MemoryUtil.memAlloc(indices.length * FORMAT.getVertexSize()).order(ByteOrder.nativeOrder());
            try {
                for (int index : indices) for (int i = 0; i < 16; i++) bytes.putFloat(vertices[index * 16 + i]);
                bytes.flip();
                BufferBuilder builder = new BufferBuilder(bytes.remaining());
                builder.begin(VertexFormat.Mode.TRIANGLES, FORMAT);
                builder.putBulkData(bytes);
                buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                buffer.bind();
                buffer.upload(builder.end());
                VertexBuffer.unbind();
            } finally { MemoryUtil.memFree(bytes); }
        }
        private void skin(float amount) {
            for (int vertex = 0; vertex < vertices.length / 16; vertex++) {
                int raw = vertex * 16, out = vertex * 6;
                for (int row = 0; row < 3; row++) {
                    float position = 0, normal = 0;
                    for (int influence = 0; influence < 4; influence++) {
                        float weight = vertices[raw + 12 + influence];
                        if (weight == 0) continue;
                        int bone = (int)vertices[raw + 8 + influence] * 16;
                        position += weight * (BONES[bone + row] * vertices[raw] + BONES[bone + 4 + row] * vertices[raw + 1]
                                + BONES[bone + 8 + row] * vertices[raw + 2] + BONES[bone + 12 + row]);
                        normal += weight * (BONES[bone + row] * vertices[raw + 3] + BONES[bone + 4 + row] * vertices[raw + 4]
                                + BONES[bone + 8 + row] * vertices[raw + 5]);
                    }
                    skinned[out + row] = vertices[raw + row] + (position - vertices[raw + row]) * amount;
                    skinned[out + 3 + row] = vertices[raw + 3 + row] + (normal - vertices[raw + 3 + row]) * amount;
                }
                float length = (float)Math.sqrt(skinned[out + 3] * skinned[out + 3] + skinned[out + 4] * skinned[out + 4] + skinned[out + 5] * skinned[out + 5]);
                for (int i = 3; i < 6; i++) skinned[out + i] /= Math.max(length, 1.0E-6F);
            }
        }
    }
}
