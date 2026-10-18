package yincmewy.cockroach.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import yincmewy.cockroach.Config;
import yincmewy.cockroach.entity.CockroachEntity;

public final class CockroachRenderer extends EntityRenderer<CockroachEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("cockroach", "models/entity/cockroach_basecolor.png");
    public CockroachRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0.3F; }

    static int selectLod(double distanceSquared, float size) {
        double normalized = distanceSquared / ((double) size * size);
        double near = Config.HIGH_DETAIL_DISTANCE.get();
        double far = Math.max(near, Config.LOW_DETAIL_DISTANCE.get());
        return normalized <= near * near ? 0 : normalized <= far * far ? 1 : 2;
    }
    @Override
    public boolean shouldRender(CockroachEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        double limit = Config.MAX_RENDER_DISTANCE.get() + entity.getBbWidth();
        if (entity.distanceToSqr(camX, camY, camZ) > limit * limit) return false;
        return super.shouldRender(entity, frustum, camX, camY, camZ);
    }
    @Override
    public void render(CockroachEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight) {
        float size = entity.getCockroachSize();
        float flight = entity.getFlightAmount(partialTick);
        float walk = entity.getWalkAmount(partialTick);
        float age = entity.tickCount + partialTick + entity.getId() * 0.37F;
        shadowRadius = Math.min(4.0F, 0.3F * size);
        if (!entity.isInvisible()) {
            var camera = entityRenderDispatcher.camera.getPosition();
            int lod = selectLod(entity.distanceToSqr(camera), size);
            CockroachModel.Entry model = CockroachModel.getForLod(lod, flight > 0);
            if (model != null) {
                pose.pushPose();
                try {
                    float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
                    pose.translate(0.0D, Config.MODEL_Y_OFFSET.get() * size, 0.0D);
                    pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw + Config.MODEL_YAW.get().floatValue()));
                    pose.translate(0, flight * size * (0.025F + 0.012F * Mth.sin(age * 0.6F)), 0);
                    pose.mulPose(Axis.XP.rotationDegrees(-10.0F * flight));
                    float scale = CockroachEntity.MODEL_LENGTH * size / CockroachModel.NATIVE_LENGTH;
                    pose.scale(scale, scale, scale);
                    pose.translate(-CockroachModel.CENTER_X, -CockroachModel.BOTTOM_Y, -CockroachModel.CENTER_Z);
                    boolean hurt = entity.hurtTime > 0 || entity.deathTime > 0;
                    boolean gpu = CockroachGpuRenderer.available() && !Minecraft.getInstance().shouldEntityAppearGlowing(entity);
                    if (flight == 0 && walk > 0.001F && CockroachWalkRenderer.ready()) {
                        CockroachWalkRenderer.render(lod, pose, buffers, packedLight, OverlayTexture.pack(0.0F, hurt),
                                hurt, entity.getWalkPhase(partialTick), walk, gpu);
                    } else if (gpu) {
                        CockroachGpuRenderer.render(model.mesh(), pose, packedLight, hurt, flight, age);
                    } else if (flight > 0) {
                        CockroachGpuRenderer.renderBuffered(model.mesh(), pose, buffers, packedLight, OverlayTexture.pack(0.0F, hurt), flight, age);
                    } else {
                        model.instance().render(pose, buffers, packedLight, OverlayTexture.pack(0.0F, hurt));
                    }
                } finally { pose.popPose(); }
            }
        }
        super.render(entity, yaw, partialTick, pose, buffers, packedLight);
    }
    @Override
    protected boolean shouldShowName(CockroachEntity entity) {
        return entity.hasCustomName() && (entity.shouldShowName() || entity == entityRenderDispatcher.crosshairPickEntity);
    }
    @Override
    public ResourceLocation getTextureLocation(CockroachEntity entity) { return TEXTURE; }
}
