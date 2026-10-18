package yincmewy.cockroach.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import yincmewy.cockroach.entity.CockroachEntity;

import java.util.EnumSet;

/** Walk under cover during daylight and occupy MOVE/LOOK while resting there. */
public final class CockroachDayRestGoal extends Goal {
    private final CockroachEntity roach;
    private int nextSearch;
    public CockroachDayRestGoal(CockroachEntity roach) {
        this.roach = roach;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }
    @Override public boolean canUse() { return roach.isInsomniaSpawned() && roach.level().isDay() && !roach.wantsToEscape() && !roach.isFlying(); }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() { nextSearch = 0; }
    @Override public void stop() { roach.setResting(false); roach.getNavigation().stop(); }

    public boolean isShade(BlockPos pos) {
        return !roach.level().canSeeSky(pos) && roach.level().getBrightness(LightLayer.BLOCK, pos) <= 7;
    }
    @Override
    public void tick() {
        if (!roach.onGround()) return;
        if (isShade(roach.blockPosition())) {
            roach.setResting(true);
            roach.getNavigation().stop();
            return;
        }
        roach.setResting(false);
        if (roach.tickCount < nextSearch) return;
        nextSearch = roach.tickCount + 40;
        if (!roach.getNavigation().isDone()) return;
        for (int i = 0; i < 100; i++) {
            BlockPos candidate = roach.blockPosition().offset(roach.getRandom().nextInt(25) - 12,
                    roach.getRandom().nextInt(7) - 3, roach.getRandom().nextInt(25) - 12);
            if (!roach.level().hasChunkAt(candidate) || !isShade(candidate)
                    || !roach.level().getFluidState(candidate).isEmpty()
                    || !roach.level().getBlockState(candidate.below()).isFaceSturdy(roach.level(), candidate.below(), Direction.UP)) continue;
            Vec3 destination = Vec3.atBottomCenterOf(candidate);
            if (!roach.level().noCollision(roach, roach.getBoundingBox().move(destination.subtract(roach.position())))) continue;
            var path = roach.getNavigation().createPath(candidate, 0);
            if (path != null && path.canReach()) {
                roach.getNavigation().moveTo(path, 1.0D);
                return;
            }
        }
    }
}
