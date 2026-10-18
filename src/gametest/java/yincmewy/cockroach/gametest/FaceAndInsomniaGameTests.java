package yincmewy.cockroach.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import yincmewy.cockroach.Cockroach;
import yincmewy.cockroach.entity.CockroachEntity;
import yincmewy.cockroach.entity.ai.CockroachFlightGoal.Phase;

import java.util.UUID;

@GameTestHolder(Cockroach.MODID)
@PrefixGameTestTemplate(false)
public final class FaceAndInsomniaGameTests {
    @BeforeBatch(batch = "roach_night")
    public static void night(ServerLevel level) { setTime(level, 18000); }
    @BeforeBatch(batch = "roach_day")
    public static void day(ServerLevel level) { setTime(level, 1000); }
    @BeforeBatch(batch = "roach_dawn")
    public static void beforeDawn(ServerLevel level) { setTime(level, 18000); }
    @BeforeBatch(batch = "roach_dusk")
    public static void beforeDusk(ServerLevel level) { setTime(level, 1000); }

    private static void setTime(ServerLevel level, long time) {
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.setDayTime(time);
    }

    private static ServerPlayer player(GameTestHelper helper, int x, int z) {
        var level = helper.getLevel();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "roach-test"));
        player.setGameMode(GameType.SURVIVAL);
        Vec3 pos = helper.absoluteVec(new Vec3(x + 0.5, 2, z + 0.5));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        level.addNewPlayer(player);
        return player;
    }

    private static void remove(ServerPlayer player) { player.discard(); }

    @GameTest(template = "hunt_arena", batch = "roach_night", timeoutTicks = 600)
    public static void retaliationReachesFaceHoversTwoSecondsAndLeaves(GameTestHelper helper) {
        ServerPlayer player = player(helper, 24, 24);
        CockroachEntity roach = helper.spawn(Cockroach.COCKROACH.get(), 21, 2, 24);
        helper.runAfterDelay(2, () -> roach.hurt(helper.getLevel().damageSources().playerAttack(player), 1));
        final long[] arrived = {-1};
        final boolean[] escaped = {false}, finished = {false};
        StringBuilder history = new StringBuilder();
        Phase[] last = {null};
        helper.onEachTick(() -> {
            if (finished[0]) return;
            Phase phase = roach.getFlightPhase();
            if (phase != last[0]) { history.append(helper.getTick()).append(':').append(phase).append(' '); last[0] = phase; }
            if (phase == Phase.ESCAPE) escaped[0] = true;
            if (phase == Phase.FACE) {
                if (arrived[0] < 0) arrived[0] = helper.getTick();
                helper.assertTrue(escaped[0] && roach.getFaceTarget() == player, "Did not flee then return to the attacking player");
                helper.assertTrue(roach.position().add(0, roach.getBbHeight() * 0.5, 0).distanceTo(player.getEyePosition()) < 1.3, "Hover missed the player's face");
            } else if (arrived[0] >= 0 && phase == Phase.RETREAT) {
                helper.assertTrue(helper.getTick() - arrived[0] == 40, "Face hover was not exactly 40 ticks: " + history);
                helper.assertTrue(player.getHealth() == player.getMaxHealth(), "Face swoop dealt unintended melee damage");
                finished[0] = true;
                remove(player);
                helper.succeed();
            }
        });
        helper.runAfterDelay(570, () -> { if (!finished[0]) helper.fail("Never completed face pass: " + history + " pos=" + roach.position() + " target=" + player.position()); });
    }

    @GameTest(template = "hunt_arena", batch = "roach_night", timeoutTicks = 600)
    public static void hittingFaceHoverInterruptsPass(GameTestHelper helper) {
        interruptPass(helper, Phase.FACE);
    }

    @GameTest(template = "hunt_arena", batch = "roach_night", timeoutTicks = 600)
    public static void hittingSwoopInterruptsPass(GameTestHelper helper) {
        interruptPass(helper, Phase.SWOOP);
    }

    private static void interruptPass(GameTestHelper helper, Phase interruptAt) {
        ServerPlayer player = player(helper, 24, 24);
        CockroachEntity roach = helper.spawn(Cockroach.COCKROACH.get(), 21, 2, 24);
        helper.runAfterDelay(2, () -> roach.hurt(helper.getLevel().damageSources().playerAttack(player), 1));
        boolean[] interrupted = {false};
        helper.onEachTick(() -> {
            if (!interrupted[0] && roach.getFlightPhase() == interruptAt) {
                interrupted[0] = true;
                helper.assertTrue(roach.hurt(helper.getLevel().damageSources().playerAttack(player), 1), "Interrupting hit was rejected");
                helper.assertTrue(roach.getFlightPhase() == Phase.RETREAT && roach.getFaceHoverTicks() == 0, "Hit did not immediately cancel the face pass");
                helper.runAfterDelay(25, () -> {
                    helper.assertTrue(roach.getFlightPhase() != Phase.FACE && roach.getFlightPhase() != Phase.SWOOP, "Interrupted pass immediately restarted");
                    remove(player);
                    helper.succeed();
                });
            }
        });
        helper.runAfterDelay(570, () -> { if (!interrupted[0]) helper.fail("Never reached " + interruptAt + "; current=" + roach.getFlightPhase()); });
    }

    @GameTest(template = "hunt_arena", batch = "roach_night", timeoutTicks = 700)
    public static void naturalCockroachHuntsWithoutBeingAttacked(GameTestHelper helper) {
        ServerPlayer player = player(helper, 24, 24);
        CockroachEntity roach = helper.spawn(Cockroach.COCKROACH.get(), 23, 10, 24);
        roach.setInsomniaSpawned(true);
        roach.startFlying();
        int[] passes = {0};
        Phase[] last = {null};
        helper.onEachTick(() -> {
            Phase phase = roach.getFlightPhase();
            if (phase == Phase.FACE && last[0] != Phase.FACE) {
                helper.assertTrue(roach.getFaceTarget() == player && !roach.wantsToEscape(), "Night hunt selected the wrong player or became escape");
                if (++passes[0] == 2) {
                    remove(player);
                    helper.succeed();
                }
            }
            last[0] = phase;
        });
        helper.runAfterDelay(680, () -> { if (passes[0] < 2) helper.fail("Night hunting did not repeat: passes=" + passes[0] + ", phase=" + roach.getFlightPhase()); });
    }

    @GameTest(template = "hunt_arena", batch = "roach_night", timeoutTicks = 40)
    public static void naturalPhantomAddsCockroachButSummonedPhantomDoesNot(GameTestHelper helper) {
        var level = helper.getLevel();
        Phantom natural = newPhantom(helper, MobSpawnType.NATURAL, 20);
        newPhantom(helper, MobSpawnType.COMMAND, 27);
        helper.runAfterDelay(4, () -> {
            var roaches = level.getEntitiesOfClass(CockroachEntity.class, new AABB(helper.absolutePos(new BlockPos(24, 12, 24))).inflate(12));
            helper.assertTrue(natural.isAlive() && !natural.isRemoved(), "Original phantom was replaced");
            helper.assertTrue(roaches.size() == 1 && roaches.get(0).isInsomniaSpawned(), "Natural phantom did not produce exactly one nocturnal companion");
            CompoundTag saved = new CompoundTag();
            roaches.get(0).save(saved);
            var loaded = (CockroachEntity) EntityType.loadEntityRecursive(saved, level, entity -> entity);
            helper.assertTrue(loaded != null && loaded.isInsomniaSpawned(), "Nighttime behavior marker was not saved");
            helper.succeed();
        });
    }

    private static Phantom newPhantom(GameTestHelper helper, MobSpawnType reason, int x) {
        var level = helper.getLevel();
        Phantom phantom = EntityType.PHANTOM.create(level);
        phantom.moveTo(helper.absoluteVec(new Vec3(x, 12, 24)));
        phantom.setNoAi(true);
        ForgeEventFactory.onFinalizeSpawn(phantom, level, level.getCurrentDifficultyAt(phantom.blockPosition()), reason, null, null);
        level.addFreshEntity(phantom);
        return phantom;
    }

    @GameTest(template = "hunt_arena", batch = "roach_day", timeoutTicks = 400)
    public static void daylightFindsShadeRestsAndDoesNotBurnOrFlee(GameTestHelper helper) {
        for (int x = 22; x <= 35; x++) for (int z = 16; z <= 32; z++) helper.setBlock(x, 4, z, Blocks.STONE);
        ServerPlayer nearby = player(helper, 17, 24);
        CockroachEntity roach = helper.spawn(Cockroach.COCKROACH.get(), 18, 2, 24);
        roach.setInsomniaSpawned(true);
        boolean[] waiting = {false};
        helper.onEachTick(() -> {
            helper.assertTrue(!roach.isFlying() && !roach.wantsToEscape() && !roach.isOnFire(), "Unattacked day roach flew, fled or burned");
            if (roach.isResting() && !waiting[0]) {
                waiting[0] = true;
                Vec3 rest = roach.position();
                helper.runAfterDelay(30, () -> {
                    helper.assertTrue(roach.isResting() && !helper.getLevel().canSeeSky(roach.blockPosition()), "Did not remain in shade");
                    helper.assertTrue(roach.position().distanceToSqr(rest) < 0.25, "Resting cockroach kept wandering");
                    remove(nearby);
                    helper.succeed();
                });
            }
        });
    }

    @GameTest(template = "hunt_arena", batch = "roach_dawn", timeoutTicks = 180)
    public static void dawnStopsUnprovokedFlight(GameTestHelper helper) {
        ServerPlayer player = player(helper, 24, 24);
        CockroachEntity roach = helper.spawn(Cockroach.COCKROACH.get(), 22, 10, 24);
        roach.setInsomniaSpawned(true);
        roach.startFlying();
        helper.runAfterDelay(30, () -> helper.getLevel().setDayTime(1000));
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(!roach.isFlying() && !roach.isNoGravity() && roach.onGround(), "Daylight did not end night flight");
            helper.assertTrue(!roach.isOnFire(), "Natural cockroach burned in sunlight");
            remove(player);
            helper.succeed();
        });
    }

    @GameTest(template = "hunt_arena", batch = "roach_dusk", timeoutTicks = 500)
    public static void duskWakesRestingCockroachToHunt(GameTestHelper helper) {
        for (int x = 22; x <= 26; x++) for (int z = 22; z <= 26; z++) helper.setBlock(x, 4, z, Blocks.STONE);
        ServerPlayer player = player(helper, 18, 24);
        CockroachEntity roach = helper.spawn(Cockroach.COCKROACH.get(), 24, 2, 24);
        roach.setInsomniaSpawned(true);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(roach.isResting() && !roach.isFlying(), "Day roach did not rest");
            helper.getLevel().setDayTime(18000);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 20 && roach.getFlightPhase() == Phase.FACE, "Nightfall did not wake roach for a face pass");
            helper.assertTrue(!roach.isResting(), "Night hunter remained resting");
            remove(player);
        });
    }
}
