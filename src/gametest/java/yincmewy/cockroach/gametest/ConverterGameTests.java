package yincmewy.cockroach.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import yincmewy.cockroach.Cockroach;
import yincmewy.cockroach.entity.CockroachEntity;
import yincmewy.cockroach.entity.RainbowLightningBolt;
import yincmewy.cockroach.item.CockroachConverterItem;
import yincmewy.cockroach.item.ConverterEvents;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import java.util.List;

@GameTestHolder(Cockroach.MODID)
@PrefixGameTestTemplate(false)
public final class ConverterGameTests {
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void conversionPreservesVolumeAndState(GameTestHelper helper) {
        for (EntityType<? extends Mob> type : List.of(EntityType.COW, EntityType.SILVERFISH, EntityType.SLIME)) {
            Mob original = helper.spawnWithNoFreeWill(type, 1, 1, 1);
            original.setNoAi(true);
            original.setCustomName(Component.literal("Converted"));
            original.setCustomNameVisible(true);
            original.setPersistenceRequired();
            original.setHealth(original.getMaxHealth() * 0.5F);
            Vec3 position = original.position();
            double volume = volume(original);
            CockroachEntity roach = CockroachConverterItem.convert(helper.getLevel(), original);
            helper.assertTrue(roach != null && original.isRemoved(), "Mob was not replaced");
            helper.assertTrue(Math.abs(volume(roach) / volume - 1) < 0.00001, "Conversion changed hitbox volume for " + type);
            helper.assertTrue(roach.position().equals(position), "Conversion moved the mob");
            helper.assertTrue(roach.getHealth() == roach.getMaxHealth() * 0.5F, "Health fraction was not preserved");
            helper.assertTrue(roach.isNoAi() && roach.isPersistenceRequired(), "AI/persistence flags were lost");
            helper.assertTrue(roach.getCustomName().getString().equals("Converted") && roach.isCustomNameVisible(), "Name was lost");
            helper.assertTrue(!roach.isOnFire(), "Converted mob caught fire");
            roach.discard();
        }
        Cow baby = helper.spawnWithNoFreeWill(EntityType.COW, 1, 1, 1);
        baby.setBaby(true);
        double babyVolume = volume(baby);
        CockroachEntity smallRoach = CockroachConverterItem.convert(helper.getLevel(), baby);
        helper.assertTrue(smallRoach != null && Math.abs(volume(smallRoach) / babyVolume - 1) < 0.00001, "Baby volume was not preserved");
        helper.assertTrue(!CockroachConverterItem.canConvert(smallRoach), "Existing cockroach should not be replaced");
        helper.assertTrue(!CockroachConverterItem.canConvert(helper.makeMockPlayer()), "Player should not be replaced");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void rainbowLightningOnlyRenders(GameTestHelper helper) {
        helper.setBlock(1, 0, 1, Blocks.OXIDIZED_COPPER);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.LIGHTNING_ROD.defaultBlockState().setValue(LightningRodBlock.FACING, Direction.UP));
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, 2, 1, 2);
        cow.setNoGravity(true);
        float health = cow.getHealth();
        RainbowLightningBolt bolt = helper.spawn(Cockroach.RAINBOW_LIGHTNING.get(), 1.5F, 2.0F, 1.5F);
        // With Y=2 exactly, vanilla's strike position would power the rod at Y=1.
        helper.runAfterDelay(1, () -> {
            helper.assertBlockProperty(new BlockPos(1, 1, 1), LightningRodBlock.POWERED, false);
            helper.assertBlockPresent(Blocks.OXIDIZED_COPPER, 1, 0, 1);
            helper.assertTrue(cow.getHealth() == health && !cow.isOnFire(), "Visual bolt damaged/burned a mob");
        });
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(bolt.isRemoved(), "Visual bolt never expired");
            helper.assertTrue(bolt.getBlocksSetOnFire() == 0, "Visual bolt placed fire");
            helper.assertTrue(cow.isAlive() && cow.getHealth() == health && !cow.isOnFire(), "Visual bolt altered the mob");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void rightClickTargetsMobAndAppliesCooldown(GameTestHelper helper) {
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, 1, 1, 2);
        double volume = volume(cow);
        var player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Cockroach.COCKROACH_CONVERTER.get()));
        // Look down slightly from eye height toward the cow, within the air-use ray.
        Vec3 eye = cow.position().add(0, 0.8, -2);
        player.moveTo(eye.x, eye.y - player.getEyeHeight(), eye.z, 0.0F, 0.0F);
        CockroachConverterItem converter = (CockroachConverterItem) Cockroach.COCKROACH_CONVERTER.get();
        var result = converter.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result.getResult().consumesAction() && cow.isRemoved(), "Right-click ray did not convert the mob");
        var roaches = helper.getLevel().getEntitiesOfClass(CockroachEntity.class, new AABB(cow.blockPosition()).inflate(2));
        helper.assertTrue(roaches.size() == 1 && Math.abs(volume(roaches.get(0)) / volume - 1) < 0.00001, "Strike did not create an equal-volume cockroach");
        helper.assertTrue(player.getCooldowns().isOnCooldown(converter), "Missing item cooldown");
        Cow second = helper.spawnWithNoFreeWill(EntityType.COW, 1, 1, 2);
        converter.activate(helper.getLevel(), player, second.position());
        helper.assertTrue(!second.isRemoved(), "Cooldown allowed a second conversion");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void directInteractionOverridesMobInteraction(GameTestHelper helper) {
        var villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, 1, 1, 1);
        var player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Cockroach.COCKROACH_CONVERTER.get()));
        PlayerInteractEvent.EntityInteractSpecific event = new PlayerInteractEvent.EntityInteractSpecific(
                player, InteractionHand.MAIN_HAND, villager, Vec3.ZERO);
        ConverterEvents.interactSpecific(event);
        helper.assertTrue(event.isCanceled() && event.getCancellationResult().consumesAction(), "Normal mob interaction was not consumed");
        helper.assertTrue(villager.isRemoved(), "Direct interaction did not convert villager");
        helper.assertTrue(player.getHealth() == player.getMaxHealth() && !player.isOnFire(), "Caster was damaged/burned");
        helper.succeed();
    }

    private static double volume(Entity entity) {
        AABB box = entity.getBoundingBox();
        return box.getXsize() * box.getYsize() * box.getZsize();
    }
}
