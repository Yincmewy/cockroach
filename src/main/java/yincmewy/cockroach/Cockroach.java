package yincmewy.cockroach;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import yincmewy.cockroach.entity.CockroachEntity;
import yincmewy.cockroach.entity.RainbowLightningBolt;
import yincmewy.cockroach.item.CockroachConverterItem;
import yincmewy.cockroach.network.SizeNetwork;

@Mod(Cockroach.MODID)
public final class Cockroach {
    public static final String MODID = "cockroach";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<EntityType<CockroachEntity>> COCKROACH = ENTITIES.register("cockroach",
            () -> EntityType.Builder.of(CockroachEntity::new, MobCategory.CREATURE)
                    .sized(0.65F, 0.5F).clientTrackingRange(8).updateInterval(3)
                    .build(MODID + ":cockroach"));
    public static final RegistryObject<Item> COCKROACH_SPAWN_EGG = ITEMS.register("cockroach_spawn_egg",
            () -> new ForgeSpawnEggItem(COCKROACH, 0x4B2519, 0xB77737, new Item.Properties()));

    public static final RegistryObject<Item> SIZE_EDITOR = ITEMS.register("size_editor", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> COCKROACH_CONVERTER = ITEMS.register("cockroach_converter",
            () -> new CockroachConverterItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<EntityType<RainbowLightningBolt>> RAINBOW_LIGHTNING = ENTITIES.register("rainbow_lightning",
            () -> EntityType.Builder.<RainbowLightningBolt>of(RainbowLightningBolt::new, MobCategory.MISC)
                    .sized(0.0F, 0.0F).clientTrackingRange(64).updateInterval(1)
                    .setShouldReceiveVelocityUpdates(false).noSave().fireImmune()
                    .build(MODID + ":rainbow_lightning"));

    public Cockroach(FMLJavaModLoadingContext context) {
        IEventBus bus = context.getModEventBus();
        ENTITIES.register(bus);
        ITEMS.register(bus);
        bus.addListener(this::registerAttributes);
        bus.addListener(this::addCreativeItems);
        context.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        context.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
        SizeNetwork.register();
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(COCKROACH.get(), CockroachEntity.createAttributes().build());
    }

    private void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(COCKROACH_SPAWN_EGG);
            event.accept(SIZE_EDITOR);
            event.accept(COCKROACH_CONVERTER);
        }
    }
}
