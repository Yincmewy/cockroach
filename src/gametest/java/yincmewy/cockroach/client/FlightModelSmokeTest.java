package yincmewy.cockroach.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.nio.file.Path;

final class FlightModelSmokeTest {
    static void exercise(Minecraft mc, CockroachModel.Entry[] models, Path output) throws Exception {
        TextureTarget target = new TextureTarget(768, 768, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            target.setClearColor(0.12F, 0.16F, 0.2F, 1.0F);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-0.8F, 0.8F, -0.5F, 1.1F, -10, 10), VertexSorting.ORTHOGRAPHIC_Z);
            modelView.setIdentity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShaderFogStart(100);
            RenderSystem.setShaderFogEnd(200);
            var buffers = MultiBufferSource.immediate(new BufferBuilder(8 * 1024 * 1024));
            for (int lod = 0; lod < models.length; lod++) {
                var model = models[lod];
                if (model.instance().baked().parts.size() != 11 || model.instance().getTextures().size() != 2) {
                    throw new IllegalStateException("Flight rig is missing articulated parts/textures at LOD " + lod);
                }
                int[] previous = null;
                for (int frame = 0; frame < 4; frame++) {
                    target.clear(Minecraft.ON_OSX);
                    target.bindWrite(true);
                    float amount = frame == 0 ? 0 : frame == 1 ? 0.5F : 1;
                    float age = frame == 3 ? 1.8F : 0.6F;
                    CockroachGpuRenderer.render(model.mesh(), pose(), LightTexture.FULL_BRIGHT, false, amount, age);
                    try (NativeImage image = Screenshot.takeScreenshot(target)) {
                        int[] pixels = pixels(image);
                        if (previous != null && differences(previous, pixels) < 500) throw new IllegalStateException("Wings did not unfold/flap at LOD " + lod + " frame " + frame);
                        previous = pixels;
                        image.writeToFile(output.resolve("flight-lod" + lod + "-frame" + frame + ".png"));
                    }
                }
                var wing = model.instance().baked().parts.get(9);
                var vbo = wing.buffer();
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                CockroachGpuRenderer.renderBuffered(model.mesh(), pose(), buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1.8F);
                buffers.endBatch();
                try (NativeImage image = Screenshot.takeScreenshot(target)) {
                    int[] fallback = pixels(image);
                    int nonBackground = 0, background = fallback[0];
                    for (int pixel : fallback) if (pixel != background) nonBackground++;
                    if (nonBackground < 8000) throw new IllegalStateException("Empty flight compatibility rendering");
                    int missing = 0;
                    for (int i = 0; i < fallback.length; i++) {
                        if ((fallback[i] != background) != (previous[i] != previous[0])) missing++;
                    }
                    if (missing > 500) throw new IllegalStateException("Flight compatibility rendering lost wing/body geometry: " + missing);
                    image.writeToFile(output.resolve("flight-lod" + lod + "-buffered.png"));
                }
                if (vbo != wing.buffer()) throw new IllegalStateException("Flight animation replaced the cached wing VBO");
            }
            exerciseWalk(target, buffers, output);
            if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new IllegalStateException("Flight rendering OpenGL error");
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            target.destroyBuffers();
            mc.getMainRenderTarget().bindWrite(true);
        }
    }

    private static void exerciseWalk(TextureTarget target, MultiBufferSource.BufferSource buffers, Path output) throws Exception {
        if (CockroachWalkRenderer.shader == null || !CockroachWalkRenderer.ready()) throw new IllegalStateException("Original walk animation/shader did not load");
        for (int lod = 0; lod < 3; lod++) {
            int[] previous = null;
            for (int frame = 0; frame < 4; frame++) {
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                CockroachWalkRenderer.render(lod, pose(), buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        false, frame * 0.25F, 1, true);
                try (NativeImage image = Screenshot.takeScreenshot(target)) {
                    int[] pixels = pixels(image);
                    if (previous != null && differences(previous, pixels) < 500) throw new IllegalStateException("Original gait did not animate at LOD " + lod);
                    previous = pixels;
                    image.writeToFile(output.resolve("walk-lod" + lod + "-frame" + frame + ".png"));
                }
            }
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            CockroachWalkRenderer.render(lod, pose(), buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    false, 0.75F, 1, false);
            buffers.endBatch();
            try (NativeImage image = Screenshot.takeScreenshot(target)) {
                int[] pixels = pixels(image);
                int difference = 0, foreground = 0;
                for (int i = 0; i < pixels.length; i++) {
                    if (pixels[i] != pixels[0]) foreground++;
                    if ((pixels[i] != pixels[0]) != (previous[i] != previous[0])) difference++;
                }
                if (foreground < 5000 || difference > 500) throw new IllegalStateException("GPU/CPU walking skinning mismatch: " + difference + ", visible=" + foreground);
                image.writeToFile(output.resolve("walk-lod" + lod + "-buffered.png"));
            }
        }
    }

    private static PoseStack pose() {
        PoseStack pose = new PoseStack();
        pose.mulPose(Axis.XP.rotationDegrees(35));
        pose.mulPose(Axis.YP.rotationDegrees(150));
        float scale = 0.9F / CockroachModel.NATIVE_LENGTH;
        pose.scale(scale, scale, scale);
        pose.translate(-CockroachModel.CENTER_X, -CockroachModel.BOTTOM_Y, -CockroachModel.CENTER_Z);
        return pose;
    }

    private static int[] pixels(NativeImage image) {
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) pixels[y * image.getWidth() + x] = image.getPixelRGBA(x, y);
        return pixels;
    }

    private static int differences(int[] a, int[] b) {
        int count = 0;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) count++;
        return count;
    }
}
