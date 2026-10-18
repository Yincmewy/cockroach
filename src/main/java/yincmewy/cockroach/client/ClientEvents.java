package yincmewy.cockroach.client;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;
import java.io.IOException;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yincmewy.cockroach.Cockroach;

@Mod.EventBusSubscriber(modid = Cockroach.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(Cockroach.MODID, "cockroach"), DefaultVertexFormat.NEW_ENTITY), shader -> CockroachGpuRenderer.shader = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(Cockroach.MODID, "cockroach_walk"), CockroachWalkRenderer.FORMAT), shader -> CockroachWalkRenderer.shader = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(Cockroach.MODID, "rainbow_lightning"), DefaultVertexFormat.POSITION_COLOR), shader -> RainbowLightningRenderer.shader = shader);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Cockroach.COCKROACH.get(), CockroachRenderer::new);
        event.registerEntityRenderer(Cockroach.RAINBOW_LIGHTNING.get(), RainbowLightningRenderer::new);
    }

    @SubscribeEvent
    public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> CockroachModel.reset());
    }
}
