package yincmewy.cockroach.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import yincmewy.cockroach.Cockroach;
import yincmewy.cockroach.entity.CockroachEntity;

@GameTestHolder(Cockroach.MODID)
@PrefixGameTestTemplate(false)
public final class FlightGameTests {
    @GameTest(template = "flight_arena", timeoutTicks = 340)
    public static void takesOffFliesAndLands(GameTestHelper helper) {
        CockroachEntity roach = helper.spawn(Cockroach.COCKROACH.get(), new BlockPos(12, 2, 12));
        double ground = roach.getY();
        helper.assertTrue(roach.startFlying(), "Could not start flight in open space: " + diagnostics(roach));
        helper.assertTrue(roach.getNavigation() instanceof FlyingPathNavigation && roach.isNoGravity(), "Flight navigation/gravity not enabled");
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(roach.isFlying() && roach.getY() > ground + 0.6, "Cockroach did not gain altitude");
            helper.assertTrue(roach.getFlightAmount(1) == 1, "Wings did not finish opening");
        });
        helper.runAfterDelay(315, () -> {
            helper.assertTrue(roach.isAlive() && roach.getHealth() == roach.getMaxHealth(), "Flight/landing caused damage");
            helper.assertTrue(!roach.isFlying() && roach.onGround(), "Cockroach did not land");
            helper.assertTrue(roach.getNavigation() instanceof GroundPathNavigation && !roach.isNoGravity(), "Ground movement was not restored");
            helper.assertTrue(roach.getFlightAmount(1) == 0, "Wings did not fold after landing");
            helper.succeed();
        });
    }

    @GameTest(template = "flight_arena", timeoutTicks = 80)
    public static void frightenedCockroachTakesOff(GameTestHelper helper) {
        CockroachEntity roach = helper.spawn(Cockroach.COCKROACH.get(), new BlockPos(12, 2, 12));
        helper.runAfterDelay(2, () -> roach.hurt(helper.getLevel().damageSources().generic(), 1));
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(roach.isFlying() && roach.getY() > helper.absolutePos(new BlockPos(12, 2, 12)).getY() + 0.5, "Hurt cockroach did not take off: " + diagnostics(roach));
            roach.setNoAi(true);
        });
        helper.runAfterDelay(27, () -> {
            helper.assertTrue(!roach.isFlying() && !roach.isNoGravity(), "Disabling AI left cockroach hovering");
            helper.succeed();
        });
    }

    @GameTest(template = "flight_arena", timeoutTicks = 60)
    public static void flightSurvivesSaveAndRespectsObstacles(GameTestHelper helper) {
        CockroachEntity roach = helper.spawn(Cockroach.COCKROACH.get(), new BlockPos(12, 2, 12));
        helper.setBlock(12, 3, 12, Blocks.STONE);
        helper.assertTrue(!roach.startFlying(), "Takeoff ignored a low ceiling");
        helper.setBlock(12, 3, 12, Blocks.AIR);
        helper.assertTrue(roach.startFlying(), "Takeoff failed after clearing ceiling: " + diagnostics(roach));
        helper.runAfterDelay(20, () -> {
            CompoundTag saved = new CompoundTag();
            roach.save(saved);
            roach.discard();
            CockroachEntity loaded = (CockroachEntity) EntityType.loadEntityRecursive(saved, helper.getLevel(), entity -> entity);
            helper.assertTrue(loaded != null && loaded.isFlying() && loaded.isNoGravity(), "Airborne state was lost on reload");
            helper.assertTrue(loaded.getNavigation() instanceof FlyingPathNavigation && loaded.getFlightTicks() > 0, "Flight controller/timer not restored");
            loaded.stopFlying();
            helper.assertTrue(!loaded.isFlying() && !loaded.isNoGravity(), "Explicit landing failed");
            helper.succeed();
        });
    }

    private static String diagnostics(CockroachEntity roach) {
        return "alive=" + roach.isAlive() + ", noAI=" + roach.isNoAi() + ", water=" + roach.isInWaterOrBubble()
                + ", lava=" + roach.isInLava() + ", passenger=" + roach.isPassenger() + ", leashed=" + roach.isLeashed()
                + ", clearance=" + roach.level().noCollision(roach, roach.getBoundingBox().move(0, 0.75, 0))
                + ", pos=" + roach.position() + ", box=" + roach.getBoundingBox()
                + ", above=" + roach.level().getBlockState(roach.blockPosition().above())
                + ", feet=" + roach.level().getBlockState(roach.blockPosition()) + ", flight=" + roach.isFlying();
    }
}
