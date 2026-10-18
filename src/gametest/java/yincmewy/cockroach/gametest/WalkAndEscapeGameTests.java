package yincmewy.cockroach.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import yincmewy.cockroach.Cockroach;
import yincmewy.cockroach.entity.CockroachEntity;

@GameTestHolder(Cockroach.MODID)
@PrefixGameTestTemplate(false)
public final class WalkAndEscapeGameTests {
    @GameTest(template = "flight_arena", timeoutTicks = 90)
    public static void attackTriggersFlightAwayFromActualAttacker(GameTestHelper helper) {
        var attacker = helper.spawnWithNoFreeWill(EntityType.PIG, 12, 2, 8);
        CockroachEntity roach = helper.spawn(Cockroach.COCKROACH.get(), new BlockPos(12, 2, 12));
        double startDistance = roach.distanceToSqr(attacker);
        helper.runAfterDelay(2, () -> {
            roach.hurt(helper.getLevel().damageSources().mobAttack(attacker), 1);
            helper.assertTrue(roach.wantsToEscape() && roach.getEscapeOrigin().equals(attacker.position()), "Actual attacker was not remembered");
        });
        helper.runAfterDelay(55, () -> {
            helper.assertTrue(roach.hurtTime == 0 && roach.wantsToEscape(), "Escape ended with the brief hurt animation");
            helper.assertTrue(roach.isFlying() && roach.distanceToSqr(attacker) > startDistance + 9, "Did not fly away from non-player attacker");
            helper.succeed();
        });
    }

    @GameTest(template = "flight_arena", timeoutTicks = 90)
    public static void blockedTakeoffRunsAwayAndRetries(GameTestHelper helper) {
        for (int x = 1; x < 23; x++) for (int z = 1; z < 23; z++) helper.setBlock(x, 3, z, Blocks.STONE);
        var attacker = helper.spawnWithNoFreeWill(EntityType.PIG, 12, 2, 8);
        CockroachEntity roach = helper.spawn(Cockroach.COCKROACH.get(), new BlockPos(12, 2, 12));
        double startDistance = roach.distanceToSqr(attacker);
        helper.runAfterDelay(2, () -> roach.hurt(helper.getLevel().damageSources().mobAttack(attacker), 1));
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(!roach.isFlying() && !roach.isNoGravity(), "Flew through a solid ceiling");
            helper.assertTrue(roach.distanceToSqr(attacker) > startDistance + 1, "Blocked takeoff did not fall back to ground escape");
            for (int x = 1; x < 23; x++) for (int z = 1; z < 23; z++) helper.setBlock(x, 3, z, Blocks.AIR);
        });
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(roach.isFlying(), "Did not retry takeoff after the ceiling cleared and hurtTime expired");
            helper.succeed();
        });
    }

    @GameTest(template = "flight_arena", timeoutTicks = 80)
    public static void walkingFollowsMovementAndStopsAtIdle(GameTestHelper helper) {
        CockroachEntity roach = helper.spawnWithNoFreeWill(Cockroach.COCKROACH.get(), 8, 2, 12);
        BlockPos destination = helper.absolutePos(new BlockPos(18, 2, 12));
        helper.runAfterDelay(2, () -> roach.getNavigation().moveTo(destination.getX(), destination.getY(), destination.getZ(), 1));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(roach.getWalkAmount(1) > 0.05F && roach.getWalkPhase(1) > 0, "Movement did not drive walking animation");
            helper.assertTrue(!roach.isFlying(), "Unattacked cockroach unexpectedly took flight");
            roach.getNavigation().stop();
        });
        helper.runAfterDelay(55, () -> {
            helper.assertTrue(roach.getWalkAmount(1) < 0.01F, "Walking animation kept running while idle");
            helper.succeed();
        });
    }

    @GameTest(template = "flight_arena", timeoutTicks = 40)
    public static void rejectedDamageDoesNotTriggerEscape(GameTestHelper helper) {
        CockroachEntity roach = helper.spawn(Cockroach.COCKROACH.get(), new BlockPos(12, 2, 12));
        roach.setInvulnerable(true);
        helper.assertTrue(!roach.hurt(helper.getLevel().damageSources().generic(), 1), "Invulnerable entity accepted damage");
        helper.assertTrue(!roach.wantsToEscape() && !roach.isFlying(), "Rejected damage triggered escape");
        helper.succeed();
    }
}
