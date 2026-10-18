package yincmewy.cockroach;

import net.minecraftforge.common.ForgeConfigSpec;
import yincmewy.cockroach.entity.CockroachEntity;

public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue DEFAULT_SIZE;
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        DEFAULT_SIZE = builder.comment("Default size multiplier for newly created cockroaches. Existing ones keep their saved size.")
                .defineInRange("defaultSize", 1.0D, CockroachEntity.MIN_SIZE, CockroachEntity.MAX_SIZE);
        SPEC = builder.build();
    }
    private ServerConfig() {}
}
