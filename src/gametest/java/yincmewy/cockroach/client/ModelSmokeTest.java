package yincmewy.cockroach.client;

import com.erchien.modelloader.client.render.BakedPart;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import yincmewy.cockroach.Cockroach;
import yincmewy.cockroach.entity.RainbowLightningBolt;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Opt-in GPU regression/benchmark, excluded from the distributable JAR. */
@Mod.EventBusSubscriber(modid = Cockroach.MODID, value = Dist.CLIENT)
public final class ModelSmokeTest {
    private static int ticks;
    private static boolean finished;
    private static boolean reloaded;
    private static CockroachModel.Entry oldEntry;
    private static final long[] COUNTS = {29454, 3983, 794, 29474, 3999, 812};

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) throws Exception {
        if (!Boolean.getBoolean("cockroach.modelSmokeTest") || event.phase != TickEvent.Phase.END || finished) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
        GLFW.glfwHideWindow(mc.getWindow().getWindow());
        Path output = Path.of(System.getProperty("cockroach.smokeOutput"));
        Files.createDirectories(output);
        if (++ticks == 1) Files.writeString(output.resolve("result.txt"), "RUNNING");
        try {
            CockroachModel.Entry[] models = new CockroachModel.Entry[6];
            for (int i = 0; i < models.length; i++) models[i] = CockroachModel.getEntry(i);
            if (Arrays.stream(models).anyMatch(model -> model == null)) {
                if (ticks > 1200) throw new IllegalStateException("Timed out loading LOD models");
                return;
            }
            for (int i = 0; i < models.length; i++) {
                if (models[i].triangles() != COUNTS[i] || models[i].instance().getTextures().isEmpty()) throw new IllegalStateException("Bad LOD geometry/textures: " + i);
            }
            if (CockroachGpuRenderer.shader == null) throw new IllegalStateException("GPU shader was not registered");
            if (RainbowLightningRenderer.shader == null) throw new IllegalStateException("Rainbow lightning shader was not registered");
            if (CockroachRenderer.selectLod(4, 1) != 0 || CockroachRenderer.selectLod(100, 1) != 1
                    || CockroachRenderer.selectLod(1600, 1) != 2 || CockroachRenderer.selectLod(1600, 10) != 0) throw new IllegalStateException("Size-aware LOD regression");
            if (!reloaded) {
                exerciseRenderer(mc, Arrays.copyOf(models, 3), output);
                FlightModelSmokeTest.exercise(mc, Arrays.copyOfRange(models, 3, 6), output);
                exerciseLightningRenderer(mc, output);
                oldEntry = models[0];
                CockroachModel.reset();
                reloaded = true;
                return;
            }
            if (models[0] == oldEntry) throw new IllegalStateException("Resource reload reused disposed model");
            if (!CockroachWalkRenderer.ready()) throw new IllegalStateException("Walking skin could not reload");
            for (CockroachModel.Entry model : models) {
                for (BakedPart part : model.instance().baked().parts) if (!part.isUploaded()) throw new IllegalStateException("Missing GPU buffer after reload");
            }
            Files.writeString(output.resolve("result.txt"), "PASS: original Take 001 weighted walking animation at all 3 LODs, GPU/CPU skinning agreement, flight LODs/textures, animated elytra/hindwings/legs, unfolding/flapping silhouettes, compatibility rendering, lighting/overlay, size-aware LOD, cached VBOs, resource reload and rainbow lightning.\nSee benchmark.txt for isolated rendering timing (not world FPS).\n");
            Cockroach.LOGGER.info("COCKROACH_MODEL_SMOKE_PASS: {}", output);
        } catch (Throwable error) {
            Files.writeString(output.resolve("result.txt"), "FAIL: " + error);
            throw new RuntimeException("Cockroach client smoke test failed", error);
        }
        finished = true;
        mc.stop();
    }

    private static PoseStack pose() {
        PoseStack pose = new PoseStack();
        pose.mulPose(Axis.XP.rotationDegrees(25F));
        pose.mulPose(Axis.YP.rotationDegrees(145F));
        float scale = 0.9F / CockroachModel.NATIVE_LENGTH;
        pose.scale(scale, scale, scale);
        pose.translate(-CockroachModel.CENTER_X, -CockroachModel.BOTTOM_Y, -CockroachModel.CENTER_Z);
        return pose;
    }

    private static void exerciseRenderer(Minecraft mc, CockroachModel.Entry[] models, Path output) throws Exception {
        TextureTarget target = new TextureTarget(768, 768, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            target.setClearColor(0.12F, 0.16F, 0.2F, 1.0F);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-0.65F, 0.65F, -0.45F, 0.85F, -10F, 10F), VertexSorting.ORTHOGRAPHIC_Z);
            modelView.setIdentity();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            RenderSystem.setShaderFogStart(100F);
            RenderSystem.setShaderFogEnd(200F);
            for (int lod = 0; lod < models.length; lod++) {
                target.clear(Minecraft.ON_OSX);
                target.bindWrite(true);
                CockroachGpuRenderer.render(models[lod].mesh(), pose(), LightTexture.FULL_BRIGHT, false);
                try (NativeImage image = Screenshot.takeScreenshot(target)) {
                    int changed = 0;
                    int brownPixels = 0;
                    int background = image.getPixelRGBA(0, 0);
                    for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                        int pixel = image.getPixelRGBA(x, y);
                        if (pixel != background) changed++;
                        int red = pixel & 255, green = (pixel >>> 8) & 255, blue = (pixel >>> 16) & 255;
                        if (red > green * 1.2 && red > blue * 1.4 && red > 40) brownPixels++;
                    }
                    if (changed < 3000) throw new IllegalStateException("GPU rendered an empty image for LOD " + lod);
                    image.writeToFile(output.resolve("cockroach-lod" + lod + ".png"));
                    if (brownPixels < 5000) throw new IllegalStateException("Missing brown diffuse texture for LOD " + lod);
                }
            }
            // Lighting/hurt may vary across entities, but the shared VBO must retain its fixed light value.
            BakedPart part = models[0].instance().baked().parts.get(0);
            var buffer = part.buffer();
            CockroachGpuRenderer.render(models[0].mesh(), pose(), 0, true);
            if (part.buffer() != buffer || part.bakedLight() != LightTexture.FULL_BRIGHT) throw new IllegalStateException("Per-entity lighting reuploaded the shared buffer");
            if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new IllegalStateException("OpenGL rendering error");

            // Same 100 draws / transforms / target, alternate ordering, median of seven samples after warmup.
            MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(new BufferBuilder(16 * 1024 * 1024));
            StringBuilder report = new StringBuilder("100 cockroaches, isolated render submission + GPU completion; not in-world FPS.\n");
            for (int lod = 0; lod < 3; lod++) {
                double[] baseline = new double[7], fast = new double[7];
                for (int i = -2; i < 7; i++) {
                    double a, b;
                    if ((i & 1) == 0) { a = time(models[lod], buffers, target, false); b = time(models[lod], buffers, target, true); }
                    else { b = time(models[lod], buffers, target, true); a = time(models[lod], buffers, target, false); }
                    if (i >= 0) { baseline[i] = a; fast[i] = b; }
                }
                Arrays.sort(baseline); Arrays.sort(fast);
                report.append(String.format(java.util.Locale.ROOT, "LOD %d (%d triangles): buffered %.3f ms; cached GPU %.3f ms; %.2fx faster\n", lod, COUNTS[lod], baseline[3], fast[3], baseline[3] / fast[3]));
            }
            Files.writeString(output.resolve("benchmark.txt"), report.toString());
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            target.destroyBuffers();
            mc.getMainRenderTarget().bindWrite(true);
        }
    }
    private static double time(CockroachModel.Entry model, MultiBufferSource.BufferSource buffers, TextureTarget target, boolean fast) {
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        PoseStack pose = pose();
        GL11.glFinish();
        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            if (fast) CockroachGpuRenderer.render(model.mesh(), pose, LightTexture.FULL_BRIGHT, false);
            else model.instance().render(pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
        if (!fast) buffers.endBatch();
        GL11.glFinish();
        return (System.nanoTime() - start) / 1_000_000.0D;
    }

    private static void exerciseLightningRenderer(Minecraft mc, Path output) throws Exception {
        TextureTarget target = new TextureTarget(512, 768, true, Minecraft.ON_OSX);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            target.setClearColor(0.0F, 0.0F, 0.0F, 1.0F);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-30, 30, -4, 132, -200, 200), VertexSorting.ORTHOGRAPHIC_Z);
            modelView.setIdentity();
            RenderSystem.applyModelViewMatrix();
            // This renderer needs only the vanilla seed and position; no client world is required.
            RainbowLightningBolt bolt = new RainbowLightningBolt(Cockroach.RAINBOW_LIGHTNING.get(), null);
            bolt.seed = 12345L;
            var renderer = mc.getEntityRenderDispatcher().getRenderer(bolt);
            if (!(renderer instanceof RainbowLightningRenderer)) throw new IllegalStateException("Wrong lightning renderer registered");
            var buffers = MultiBufferSource.immediate(new BufferBuilder(256 * 1024));
            renderer.render(bolt, 0, 0, new PoseStack(), buffers, LightTexture.FULL_BRIGHT);
            buffers.endBatch();
            try (NativeImage image = Screenshot.takeScreenshot(target)) {
                int redPixels = 0, greenPixels = 0, bluePixels = 0;
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getPixelRGBA(x, y);
                    int red = pixel & 255, green = (pixel >>> 8) & 255, blue = (pixel >>> 16) & 255;
                    if (red > 40 && red > green * 1.4 && red > blue * 1.4) redPixels++;
                    if (green > 40 && green > red * 1.4 && green > blue * 1.4) greenPixels++;
                    if (blue > 40 && blue > red * 1.4 && blue > green * 1.4) bluePixels++;
                }
                image.writeToFile(output.resolve("rainbow-lightning.png"));
                if (Math.min(redPixels, Math.min(greenPixels, bluePixels)) < 100) {
                    throw new IllegalStateException("Lightning shader did not render a rainbow: " + redPixels + "/" + greenPixels + "/" + bluePixels);
                }
            }
            if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new IllegalStateException("Lightning OpenGL rendering error");
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            target.destroyBuffers();
            mc.getMainRenderTarget().bindWrite(true);
        }
    }
}
