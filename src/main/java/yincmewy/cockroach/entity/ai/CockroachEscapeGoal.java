package yincmewy.cockroach.entity.ai;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import yincmewy.cockroach.entity.CockroachEntity;

import java.util.EnumSet;

/** Ground fallback while the higher-priority flight goal keeps looking for takeoff clearance. */
public final class CockroachEscapeGoal extends Goal {
    private final CockroachEntity roach;
    private int nextPath;

    public CockroachEscapeGoal(CockroachEntity roach) {
        this.roach = roach;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() { return roach.wantsToEscape() && !roach.isFlying(); }
    @Override
    public boolean canContinueToUse() { return canUse(); }
    @Override
    public boolean requiresUpdateEveryTick() { return true; }
    @Override
    public void start() { nextPath = 0; }
    @Override
    public void stop() { roach.getNavigation().stop(); }
    @Override
    public void tick() {
        if (roach.tickCount < nextPath) return;
        nextPath = roach.tickCount + 10;
        var threat = roach.getEscapeOrigin();
        if (threat == null) return;
        var destination = LandRandomPos.getPosAway(roach, 10, 3, threat);
        if (destination != null) roach.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.8D);
    }
}
