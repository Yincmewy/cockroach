package yincmewy.cockroach;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue MODEL_YAW;
    public static final ForgeConfigSpec.DoubleValue MODEL_Y_OFFSET;
    public static final ForgeConfigSpec.DoubleValue HIGH_DETAIL_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue LOW_DETAIL_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue MAX_RENDER_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue COMPATIBILITY_RENDERER;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("model");
        MODEL_YAW = b.comment("Additional yaw in degrees. Size is now controlled by the server/editor, not this file.")
                .defineInRange("yawOffset", 0.0D, -180.0D, 180.0D);
        MODEL_Y_OFFSET = b.defineInRange("heightOffset", 0.0D, -2.0D, 2.0D);
        b.pop().push("performance");
        HIGH_DETAIL_DISTANCE = b.comment("Use the original 29454-triangle model within this distance, measured in entity-size units.")
                .defineInRange("highDetailDistance", 6.0D, 0.0D, 64.0D);
        LOW_DETAIL_DISTANCE = b.comment("Switch from 3983 to 794 triangles past this distance. Clamped above highDetailDistance.")
                .defineInRange("lowDetailDistance", 20.0D, 1.0D, 128.0D);
        MAX_RENDER_DISTANCE = b.comment("Maximum center-to-camera distance in blocks; enlarged cockroaches extend this by their radius.")
                .defineInRange("maxRenderDistance", 96.0D, 16.0D, 512.0D);
        COMPATIBILITY_RENDERER = b.comment("Force the slower buffered path for graphics-mod compatibility. Shader packs/glowing automatically use it. LOD still applies.")
                .define("compatibilityRenderer", false);
        b.pop();
        SPEC = b.build();
    }
    private Config() {}
}
