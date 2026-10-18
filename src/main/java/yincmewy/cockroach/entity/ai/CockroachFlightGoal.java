package yincmewy.cockroach.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import yincmewy.cockroach.entity.CockroachEntity;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.EnumSet;

/** Phantom-style circle/swoop passes, replacing melee damage with a two-second face hover. */
public final class CockroachFlightGoal extends Goal {
    public enum Phase { TAKEOFF, ESCAPE, CIRCLE, SWOOP, FACE, RETREAT, CRUISE, LAND }
    public static final int FACE_DURATION = 40;
    private final CockroachEntity roach;
    private Phase phase = Phase.LAND;
    private int phaseTicks, faceTicks, nextAttempt, nextSwoop, nextPath;
    private Player faceTarget;
    private boolean retaliation;
    private Vec3 waypoint;
    private Vec3 landing;
    private float orbitAngle;

    public CockroachFlightGoal(CockroachEntity roach) {
        this.roach = roach;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    public Phase getPhase() { return phase; }
    public int getFaceTicks() { return faceTicks; }
    @Nullable public Player getFaceTarget() { return faceTarget; }

    public void onAttacked(@Nullable Player player) {
        retaliation = true;
        nextAttempt = 0;
        if (roach.isFlying() && (phase == Phase.SWOOP || phase == Phase.FACE)) {
            if (validPlayer(player, true)) faceTarget = player;
            nextSwoop = roach.tickCount + 200;
            enter(Phase.RETREAT);
        } else {
            faceTarget = validPlayer(player, true) ? player : null;
            enter(Phase.ESCAPE);
        }
    }

    @Override
    public boolean canUse() {
        if (roach.isFlying()) return true;
        if (roach.tickCount < nextAttempt) return false;
        nextAttempt = roach.tickCount + 5;
        if (roach.wantsToEscape()) return roach.startFlying();
        retaliation = false;
        if (!nightHuntAllowed()) return false;
        faceTarget = findNightTarget();
        nextAttempt = roach.tickCount + 20;
        return faceTarget != null && roach.startFlying();
    }

    @Override public boolean canContinueToUse() { return roach.isFlying(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void start() {
        if (!roach.wantsToEscape()) retaliation = false;
        orbitAngle = roach.getRandom().nextFloat() * Mth.TWO_PI;
        if (!retaliation && nightHuntAllowed() && !validPlayer(faceTarget, false)) faceTarget = findNightTarget();
        enter(Phase.TAKEOFF);
    }

    @Override
    public void stop() {
        roach.stopFlying();
        faceTarget = null;
        retaliation = false;
        enter(Phase.LAND);
    }

    private void enter(Phase next) {
        phase = next;
        phaseTicks = 0;
        faceTicks = 0;
        waypoint = null;
        landing = null;
        nextPath = 0;
        roach.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (roach.isInsomniaSpawned() && !nightHuntAllowed() && !retaliation) {
            roach.stopFlying();
            return;
        }
        if (faceTarget != null && !validPlayer(faceTarget, retaliation)) {
            faceTarget = null;
            if (phase == Phase.SWOOP || phase == Phase.FACE) enter(Phase.RETREAT);
        }
        phaseTicks++;
        switch (phase) {
            case TAKEOFF -> {
                flyToward(roach.position().add(0, 1, 0), 1);
                if (phaseTicks >= 12) enter(retaliation ? Phase.ESCAPE : faceTarget != null ? Phase.CIRCLE : Phase.CRUISE);
            }
            case ESCAPE -> {
                flyAway();
                if (phaseTicks >= 60) {
                    roach.clearEscapeIntent();
                    enter(faceTarget != null ? Phase.CIRCLE : Phase.LAND);
                }
            }
            case CIRCLE -> circle();
            case SWOOP -> swoop();
            case FACE -> hoverAtFace();
            case RETREAT -> {
                flyAway();
                if (phaseTicks >= 60) {
                    roach.clearEscapeIntent();
                    retaliation = false;
                    faceTarget = nightHuntAllowed() ? (validPlayer(faceTarget, false) ? faceTarget : findNightTarget()) : null;
                    enter(faceTarget != null ? Phase.CIRCLE : Phase.LAND);
                }
            }
            case CRUISE -> {
                flyAway();
                if (phaseTicks >= 75) enter(Phase.LAND);
            }
            case LAND -> land();
        }
    }

    private boolean nightHuntAllowed() {
        return roach.isInsomniaSpawned() && !roach.level().isDay() && roach.level().getDifficulty() != Difficulty.PEACEFUL;
    }

    private boolean validPlayer(@Nullable Player player, boolean provoked) {
        return player != null && player.isAlive() && !player.isSpectator() && (provoked || !player.isCreative())
                && player.level() == roach.level() && roach.distanceToSqr(player) < 64 * 64;
    }

    @Nullable
    private Player findNightTarget() {
        return roach.level().getEntitiesOfClass(Player.class, roach.getBoundingBox().inflate(16, 64, 16),
                player -> validPlayer(player, false) && roach.hasLineOfSight(player)).stream()
                .max(Comparator.comparingDouble(Player::getY)).orElse(null);
    }

    private void circle() {
        if (faceTarget == null) {
            if (phaseTicks % 20 == 1 && nightHuntAllowed()) faceTarget = findNightTarget();
            if (faceTarget == null) { enter(Phase.LAND); return; }
        }
        if (waypoint == null || roach.position().distanceToSqr(waypoint) < 2 || phaseTicks % 30 == 0) {
            orbitAngle += 0.6F;
            waypoint = faceTarget.position().add(7 * Mth.cos(orbitAngle), 8 + Math.min(4, roach.getBbHeight()), 7 * Mth.sin(orbitAngle));
        }
        flyToward(waypoint, 1.3D);
        if (phaseTicks >= 40 && roach.tickCount >= nextSwoop && roach.getY() > faceTarget.getEyeY() + 2
                && roach.hasLineOfSight(faceTarget)) enter(Phase.SWOOP);
        else if (retaliation && phaseTicks >= 160) enter(Phase.RETREAT);
    }

    /** Position the body immediately in front of the eyes, leaving room for both collision boxes. */
    public Vec3 facePosition(Player player) {
        double distance = Math.max(0.65D, roach.getBbWidth() * 0.65D + player.getBbWidth() * 0.5D);
        return player.getEyePosition().add(player.getLookAngle().scale(distance)).add(0, -roach.getBbHeight() * 0.5D, 0);
    }

    private void swoop() {
        if (faceTarget == null) { enter(Phase.RETREAT); return; }
        Vec3 face = facePosition(faceTarget);
        if (!roach.hasLineOfSight(faceTarget) || !clearAt(face) || phaseTicks > 160) {
            nextSwoop = roach.tickCount + 200;
            enter(Phase.RETREAT);
            return;
        }
        // Vanilla flight steering has too much inertia for a face-sized endpoint.
        // Proportional braking prevents overshooting the eye position into the floor.
        movePrecisely(face, 0.55D);
        if (roach.position().distanceToSqr(face) <= 0.35D * 0.35D) {
            enter(Phase.FACE);
            roach.setDeltaMovement(Vec3.ZERO);
        }
    }

    private void hoverAtFace() {
        if (faceTarget == null) { enter(Phase.RETREAT); return; }
        Vec3 face = facePosition(faceTarget);
        if (!clearAt(face) || !roach.hasLineOfSight(faceTarget) || roach.position().distanceToSqr(face) > 9) {
            enter(Phase.RETREAT);
            nextSwoop = roach.tickCount + 200;
            return;
        }
        // Follow with collision-respecting velocity, never teleport or attach the player.
        movePrecisely(face, 0.5D);
        roach.getLookControl().setLookAt(faceTarget.getEyePosition());
        if (++faceTicks >= FACE_DURATION) {
            nextSwoop = roach.tickCount + (8 + roach.getRandom().nextInt(5)) * 20;
            enter(Phase.RETREAT);
        }
    }

    private void movePrecisely(Vec3 destination, double maxSpeed) {
        roach.getNavigation().stop();
        roach.getMoveControl().setWantedPosition(roach.getX(), roach.getY(), roach.getZ(), 0);
        roach.setSpeed(0);
        roach.setXxa(0);
        roach.setYya(0);
        roach.setZza(0);
        Vec3 motion = destination.subtract(roach.position()).scale(0.45D);
        if (motion.lengthSqr() > maxSpeed * maxSpeed) motion = motion.normalize().scale(maxSpeed);
        roach.setDeltaMovement(motion);
        Vec3 heading = phase == Phase.FACE && faceTarget != null ? faceTarget.position().subtract(roach.position()) : motion;
        if (heading.horizontalDistanceSqr() > 1.0E-5) {
            roach.setYRot((float)(Mth.atan2(heading.z, heading.x) * Mth.RAD_TO_DEG) - 90);
            roach.yBodyRot = roach.getYRot();
        }
    }

    private void flyAway() {
        if (waypoint == null || phaseTicks % 15 == 0 || roach.position().distanceToSqr(waypoint) < 1) {
            Vec3 threat = roach.getEscapeOrigin();
            if (threat == null && faceTarget != null) threat = faceTarget.position();
            Vec3 away = threat == null ? new Vec3(Mth.cos(orbitAngle), 0, Mth.sin(orbitAngle))
                    : roach.position().subtract(threat).multiply(1, 0, 1).normalize();
            if (away.lengthSqr() < 0.01) away = new Vec3(1, 0, 0);
            waypoint = roach.position().add(away.scale(5)).add(roach.getRandom().nextInt(3) - 1, 0.5, roach.getRandom().nextInt(3) - 1);
        }
        flyToward(waypoint, 1.5D);
    }

    private void flyToward(Vec3 destination, double speed) {
        Vec3 middle = new Vec3(0, roach.getBbHeight() * 0.5D, 0);
        boolean visible = roach.level().clip(new ClipContext(roach.position().add(middle), destination.add(middle),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, roach)).getType() == HitResult.Type.MISS;
        if (visible && clearAt(destination)) {
            roach.getNavigation().stop();
            roach.getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, speed);
        } else if (roach.tickCount >= nextPath) {
            nextPath = roach.tickCount + 10;
            roach.getNavigation().moveTo(destination.x, destination.y, destination.z, speed);
        }
    }

    private void land() {
        retaliation = false;
        faceTarget = null;
        roach.clearEscapeIntent();
        if (phaseTicks > 120) { roach.stopFlying(); return; }
        if (landing == null || phaseTicks % 30 == 0) landing = findLanding();
        if (landing == null) {
            roach.getMoveControl().setWantedPosition(roach.getX(), roach.getY() - 1, roach.getZ(), 0.6D);
        } else if (roach.position().distanceToSqr(landing) < 2.25) {
            roach.getNavigation().stop();
            roach.getMoveControl().setWantedPosition(landing.x, landing.y - 0.15, landing.z, 0.65D);
        } else flyToward(landing, 0.8D);
    }

    @Nullable
    private Vec3 findLanding() {
        for (int attempt = 0; attempt < 10; attempt++) {
            Vec3 start = roach.position().add(attempt == 0 ? 0 : roach.getRandom().nextInt(11) - 5, 0.5,
                    attempt == 0 ? 0 : roach.getRandom().nextInt(11) - 5);
            var hit = roach.level().clip(new ClipContext(start, start.add(0, -40, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, roach));
            if (hit.getType() == HitResult.Type.MISS || !roach.level().getFluidState(hit.getBlockPos()).isEmpty()) continue;
            Vec3 candidate = hit.getLocation().add(0, 0.03, 0);
            if (clearAt(candidate)) return candidate;
        }
        return null;
    }

    private boolean clearAt(Vec3 position) {
        BlockPos block = BlockPos.containing(position);
        return roach.level().hasChunkAt(block) && roach.level().getWorldBorder().isWithinBounds(block)
                && roach.level().getFluidState(block).isEmpty()
                && roach.level().noCollision(roach, roach.getBoundingBox().move(position.subtract(roach.position())));
    }
}
