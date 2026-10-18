package yincmewy.cockroach.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import yincmewy.cockroach.entity.ai.CockroachFlightGoal;
import yincmewy.cockroach.entity.ai.CockroachEscapeGoal;
import yincmewy.cockroach.entity.ai.CockroachDayRestGoal;
import yincmewy.cockroach.ServerConfig;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Server-safe entity: no Model Loader or Minecraft client references belong here. */
public final class CockroachEntity extends PathfinderMob {
    public static final float MIN_SIZE = 0.05F;
    public static final float MAX_SIZE = 64.0F;
    public static final float MODEL_LENGTH = 0.9F;
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(CockroachEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(CockroachEntity.class, EntityDataSerializers.BOOLEAN);
    private final PathNavigation groundNavigation;
    private final FlyingPathNavigation flyingNavigation;
    private final MoveControl groundMoveControl;
    private final FlyingMoveControl flyingMoveControl;
    private int flightTicks;
    private float flightAmount;
    private float previousFlightAmount;
    private float walkAmount, previousWalkAmount, walkPhase, previousWalkPhase;
    private int escapeTicks;
    private int escapeSequence;
    private Entity attacker;
    private Vec3 escapeOrigin;
    private boolean insomniaSpawned;
    private boolean resting;
    private CockroachFlightGoal flightGoal;

    public CockroachEntity(EntityType<? extends CockroachEntity> type, Level level) {
        super(type, level);
        groundNavigation = navigation;
        groundMoveControl = moveControl;
        flyingNavigation = new FlyingPathNavigation(this, level);
        flyingNavigation.setCanOpenDoors(false);
        flyingNavigation.setCanPassDoors(false);
        flyingNavigation.setCanFloat(false);
        flyingMoveControl = new FlyingMoveControl(this, 15, true);
        setMaxUpStep(0.6F);
        xpReward = 1;
        if (!level.isClientSide && ServerConfig.SPEC.isLoaded()) setCockroachSize(ServerConfig.DEFAULT_SIZE.get().floatValue());
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(SIZE, 1.0F);
        entityData.define(FLYING, false);
    }

    public boolean isFlying() { return entityData.get(FLYING); }
    public int getFlightTicks() { return flightTicks; }
    public float getFlightAmount(float partialTick) { return Mth.lerp(partialTick, previousFlightAmount, flightAmount); }
    public float getWalkAmount(float partialTick) { return Mth.lerp(partialTick, previousWalkAmount, walkAmount); }
    public float getWalkPhase(float partialTick) { return Mth.lerp(partialTick, previousWalkPhase, walkPhase); }
    public boolean wantsToEscape() { return escapeTicks > 0; }
    public int getEscapeSequence() { return escapeSequence; }
    public boolean isInsomniaSpawned() { return insomniaSpawned; }
    public void setInsomniaSpawned(boolean value) { insomniaSpawned = value; }
    public boolean isResting() { return resting; }
    public void setResting(boolean value) { resting = value; }
    public CockroachFlightGoal.Phase getFlightPhase() { return flightGoal.getPhase(); }
    public int getFaceHoverTicks() { return flightGoal.getFaceTicks(); }
    public Player getFaceTarget() { return flightGoal.getFaceTarget(); }
    public void clearEscapeIntent() {
        escapeTicks = 0;
        attacker = null;
        escapeOrigin = null;
    }
    public Vec3 getEscapeOrigin() {
        return attacker != null && attacker.isAlive() && attacker.level() == level() ? attacker.position() : escapeOrigin;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean accepted = super.hurt(source, amount);
        if (accepted && amount > 0 && isAlive() && !level().isClientSide) {
            attacker = source.getEntity();
            escapeOrigin = source.getSourcePosition();
            if (escapeOrigin == null) escapeOrigin = position().subtract(getLookAngle());
            escapeTicks = 120;
            escapeSequence++;
            // A fresh attack interrupts cruising/landing and requests a new route away from its source.
            if (isFlying()) flightTicks = Math.min(flightTicks, 12);
            navigation.stop();
            resting = false;
            flightGoal.onAttacked(attacker instanceof Player player ? player : null);
        }
        return accepted;
    }

    public boolean startFlying() {
        if (isFlying()) return true;
        if (isNoAi() || !isAlive() || isInWaterOrBubble() || isInLava() || isPassenger() || isLeashed()
                || !level().noCollision(this, getBoundingBox().move(0, 0.75D, 0))) return false;
        navigation.stop();
        navigation = flyingNavigation;
        moveControl = flyingMoveControl;
        entityData.set(FLYING, true);
        resting = false;
        setNoGravity(true);
        flightTicks = 0;
        fallDistance = 0;
        return true;
    }

    public void stopFlying() {
        if (!isFlying()) return;
        navigation.stop();
        navigation = groundNavigation;
        moveControl = groundMoveControl;
        entityData.set(FLYING, false);
        setNoGravity(false);
        setXRot(0);
        setYya(0);
        setZza(0);
        fallDistance = 0;
    }

    @Override
    public void tick() {
        previousFlightAmount = flightAmount;
        previousWalkAmount = walkAmount;
        previousWalkPhase = walkPhase;
        super.tick();
        if (!level().isClientSide) {
            if (escapeTicks > 0 && --escapeTicks == 0) {
                attacker = null;
                escapeOrigin = null;
            }
            if (isFlying()) {
                flightTicks++;
                fallDistance = 0;
                if (isNoAi() || !isAlive() || isInWaterOrBubble() || isInLava() || isPassenger() || isLeashed()
                        || (onGround() && flightTicks > 15)) stopFlying();
            }
        }
        flightAmount = Mth.clamp(flightAmount + (isFlying() ? 0.125F : -0.125F), 0, 1);
        double distance = Math.sqrt((getX() - xo) * (getX() - xo) + (getZ() - zo) * (getZ() - zo));
        boolean walking = onGround() && !isFlying() && !isPassenger() && isAlive();
        float normalized = walking ? (float)Math.min(distance / getCockroachSize(), 0.5D) : 0;
        walkAmount = Mth.lerp(0.35F, walkAmount, Math.min(1, normalized / 0.075F));
        if (walkAmount < 0.001F) walkAmount = 0;
        walkPhase += normalized / 0.45F;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate((isFlying() || flightAmount > 0) ? 0.6D * getCockroachSize() : 0);
    }

    public float getCockroachSize() { return entityData.get(SIZE); }
    public static boolean isValidSize(float value) { return Float.isFinite(value) && value >= MIN_SIZE && value <= MAX_SIZE; }
    public void setCockroachSize(float value) {
        if (!isValidSize(value)) throw new IllegalArgumentException("Size must be finite and between 0.05 and 64");
        entityData.set(SIZE, value);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SIZE.equals(key)) refreshDimensions();
        if (FLYING.equals(key)) setNoGravity(isFlying());
    }
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(getCockroachSize());
    }
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("CockroachSize", getCockroachSize());
        tag.putBoolean("CockroachFlying", isFlying());
        tag.putInt("CockroachFlightTicks", flightTicks);
        tag.putBoolean("InsomniaRoach", insomniaSpawned);
    }
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        insomniaSpawned = tag.getBoolean("InsomniaRoach");
        if (tag.contains("CockroachSize", Tag.TAG_ANY_NUMERIC)) {
            float value = tag.getFloat("CockroachSize");
            setCockroachSize(Float.isFinite(value) ? Mth.clamp(value, MIN_SIZE, MAX_SIZE) : 1.0F);
        }
        if (tag.getBoolean("CockroachFlying")) {
            if (startFlying()) flightTicks = Mth.clamp(tag.getInt("CockroachFlightTicks"), 0, 240);
            else setNoGravity(false);
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        SpawnGroupData group, CompoundTag tag) {
        if (reason == MobSpawnType.NATURAL) insomniaSpawned = true;
        return super.finalizeSpawn(level, difficulty, reason, group, tag);
    }

    @Override protected boolean shouldDespawnInPeaceful() { return insomniaSpawned; }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FLYING_SPEED, 0.35D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        flightGoal = new CockroachFlightGoal(this);
        goalSelector.addGoal(1, flightGoal);
        goalSelector.addGoal(2, new CockroachEscapeGoal(this));
        goalSelector.addGoal(3, new CockroachDayRestGoal(this));
        goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    @Override
    public MobType getMobType() {
        return MobType.ARTHROPOD;
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return dimensions.height * 0.36F;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SILVERFISH_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SILVERFISH_DEATH;
    }
}
