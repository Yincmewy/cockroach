package yincmewy.cockroach.client;

import com.erchien.modelloader.api.ModelHandle;
import com.erchien.modelloader.api.ModelLoaderAPI;
import com.erchien.modelloader.client.render.ModelInstance;
import com.erchien.modelloader.client.render.RenderOptions;
import net.minecraft.resources.ResourceLocation;
import yincmewy.cockroach.Cockroach;

/** Shared ground and articulated flight LODs. Every tier uses the original bind-pose origin. */
final class CockroachModel {
    static final ResourceLocation LOCATION = location("cockroach.glb");
    static final float NATIVE_LENGTH = 2.9424458F;
    static final float CENTER_X = 0.001948595F, BOTTOM_Y = 0.001940777F, CENTER_Z = -0.54609415F;
    private static final ResourceLocation[] LOCATIONS = {LOCATION, location("cockroach_medium.glb"), location("cockroach_low.glb"),
            location("cockroach_flight_high.glb"), location("cockroach_flight_medium.glb"), location("cockroach_flight_low.glb")};
    private static final ModelHandle[] HANDLES = new ModelHandle[6];
    private static final Entry[] ENTRIES = new Entry[6];
    private static final boolean[] REPORTED = new boolean[6];

    private CockroachModel() {}
    private static ResourceLocation location(String file) { return ResourceLocation.fromNamespaceAndPath(Cockroach.MODID, "models/entity/" + file); }
    static ModelInstance get() { Entry entry = getEntry(0); return entry == null ? null : entry.instance(); }
    static Entry getForLod(int lod) {
        return getForLod(lod, false);
    }
    static Entry getForLod(int lod, boolean flight) {
        int offset = flight ? 3 : 0;
        Entry requested = getEntry(offset + lod);
        if (requested != null) return requested;
        // Show the already-loaded coarse model while a closer tier is loading.
        for (int i = offset + 2; i >= offset; i--) if (ENTRIES[i] != null) return ENTRIES[i];
        Entry coarse = lod == 2 ? null : getEntry(offset + 2);
        return coarse != null || !flight ? coarse : getForLod(lod, false);
    }
    static Entry getEntry(int lod) {
        ModelHandle handle = HANDLES[lod];
        if (handle == null) handle = HANDLES[lod] = ModelLoaderAPI.get(LOCATIONS[lod]);
        if (handle.isFailed()) {
            if (!REPORTED[lod]) {
                Cockroach.LOGGER.error("Could not load cockroach LOD {}. F3+T retries loading.", lod, handle.error());
                REPORTED[lod] = true;
            }
            return null;
        }
        if (!handle.isReady()) return null;
        if (ENTRIES[lod] == null) {
            ModelInstance instance = handle.newInstance();
            if (instance == null) return null;
            instance.options().attached().anchor(RenderOptions.Anchor.ORIGIN).light(RenderOptions.LightMode.WORLD);
            ENTRIES[lod] = new Entry(instance, new CockroachGpuRenderer.Mesh(handle.baked()), handle.triangleCount());
            Cockroach.LOGGER.info("Loaded shared cockroach LOD {}: {} triangles", lod, handle.triangleCount());
        }
        return ENTRIES[lod];
    }
    static void reset() {
        for (int i = 0; i < HANDLES.length; i++) {
            if (ENTRIES[i] != null) ENTRIES[i].instance().dispose();
            ENTRIES[i] = null;
            if (HANDLES[i] != null) ModelLoaderAPI.unload(LOCATIONS[i]);
            HANDLES[i] = null;
            REPORTED[i] = false;
        }
        CockroachGpuRenderer.clearRenderTypes();
        CockroachWalkRenderer.reset();
    }
    record Entry(ModelInstance instance, CockroachGpuRenderer.Mesh mesh, long triangles) {}
}
