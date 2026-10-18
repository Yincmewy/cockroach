package yincmewy.cockroach.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import yincmewy.cockroach.Cockroach;
import yincmewy.cockroach.entity.CockroachEntity;

@GameTestHolder(Cockroach.MODID)
@PrefixGameTestTemplate(false)
public final class CockroachGameTests {
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void entitySpawnsAndTicks(GameTestHelper helper) {
        helper.setBlock(1, 0, 1, Blocks.STONE);
        CockroachEntity roach = helper.spawn(Cockroach.COCKROACH.get(), new BlockPos(1, 1, 1));
        helper.assertTrue(roach.getMaxHealth() == 6.0F, "Cockroach attributes were not registered");
        helper.assertTrue(roach.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0.25D, "Wrong movement speed");
        helper.assertTrue(roach.getMobType() == MobType.ARTHROPOD, "Wrong mob type");
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(roach.isAlive() && !roach.isRemoved(), "Cockroach did not survive normal server ticks");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void spawnEggTargetsCockroach(GameTestHelper helper) {
        SpawnEggItem egg = (SpawnEggItem) Cockroach.COCKROACH_SPAWN_EGG.get();
        helper.assertTrue(egg.getType(null) == Cockroach.COCKROACH.get(), "Egg targets the wrong entity");
        helper.assertTrue(ForgeSpawnEggItem.fromEntityType(Cockroach.COCKROACH.get()) == egg, "Forge egg lookup was not registered");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void entitySurvivesSaveReload(GameTestHelper helper) {
        CockroachEntity original = helper.spawnWithNoFreeWill(Cockroach.COCKROACH.get(), 1, 1, 1);
        original.setHealth(3.0F);
        original.setCockroachSize(2.5F);
        CompoundTag saved = new CompoundTag();
        helper.assertTrue(original.save(saved), "Entity could not be saved");
        Entity loaded = EntityType.loadEntityRecursive(saved, helper.getLevel(), entity -> entity);
        helper.assertTrue(loaded instanceof CockroachEntity, "Entity type was not restored from NBT");
        helper.assertTrue(((CockroachEntity) loaded).getHealth() == 3.0F, "Health did not survive NBT round-trip");
        helper.assertTrue(((CockroachEntity) loaded).getCockroachSize() == 2.5F, "Size did not survive NBT round-trip");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void sizeChangesHitboxAndSyncedData(GameTestHelper helper) {
        CockroachEntity roach = helper.spawnWithNoFreeWill(Cockroach.COCKROACH.get(), 1, 1, 1);
        roach.setCockroachSize(1.0F);
        float original = roach.getBbWidth();
        roach.setCockroachSize(3.0F);
        helper.assertTrue(Math.abs(roach.getBbWidth() - original * 3) < 0.001F, "Width did not scale");
        helper.assertTrue(Math.abs(roach.getBbHeight() - 1.5F) < 0.001F, "Height did not scale");
        helper.assertTrue(Math.abs(roach.getEyeHeight() - 0.54F) < 0.001F, "Eye height did not scale");
        helper.assertTrue(roach.getEntityData().isDirty(), "Size change was not marked for client sync");
        roach.setCockroachSize(CockroachEntity.MIN_SIZE);
        helper.assertTrue(roach.getBbWidth() > 0, "Minimum size has an invalid hitbox");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void rejectsInvalidSizeAndSanitizesNbt(GameTestHelper helper) {
        CockroachEntity roach = helper.spawnWithNoFreeWill(Cockroach.COCKROACH.get(), 1, 1, 1);
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -1.0F, 0.0F, 65.0F}) {
            helper.assertTrue(!CockroachEntity.isValidSize(bad), "Invalid size accepted");
            boolean rejected = false;
            try { roach.setCockroachSize(bad); } catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "Setter accepted invalid size");
        }
        CompoundTag tag = new CompoundTag();
        roach.save(tag);
        tag.putFloat("CockroachSize", Float.NaN);
        roach.load(tag);
        helper.assertTrue(roach.getCockroachSize() == 1.0F, "Corrupt NBT size was not sanitized");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void commandResizesSelectedCockroach(GameTestHelper helper) {
        CockroachEntity roach = helper.spawnWithNoFreeWill(Cockroach.COCKROACH.get(), 1, 1, 1);
        String command = "cockroach size " + roach.getUUID() + " 1.75";
        var server = helper.getLevel().getServer();
        int changed = server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(2), command);
        helper.assertTrue(changed == 1 && roach.getCockroachSize() == 1.75F, "Size command did not update entity");
        helper.succeed();
    }
}
