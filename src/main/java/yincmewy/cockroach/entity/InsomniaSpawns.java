package yincmewy.cockroach.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yincmewy.cockroach.Cockroach;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Add one companion to actual vanilla insomnia phantom spawns, retaining all vanilla spawn gates. */
@Mod.EventBusSubscriber(modid = Cockroach.MODID)
public final class InsomniaSpawns {
    private static final String PAIRED = "CockroachInsomniaCompanion";
    private static final Map<ServerLevel, List<Phantom>> PENDING = new IdentityHashMap<>();
    private InsomniaSpawns() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onJoin(EntityJoinLevelEvent event) {
        // Never query blocks or add another entity while the join/chunk-load event is in progress.
        if (!event.loadedFromDisk() && event.getLevel() instanceof ServerLevel level
                && event.getEntity() instanceof Phantom phantom && phantom.getSpawnType() == MobSpawnType.NATURAL
                && !phantom.getPersistentData().getBoolean(PAIRED)) {
            PENDING.computeIfAbsent(level, ignored -> new ArrayList<>()).add(phantom);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        List<Phantom> pending = PENDING.remove(level);
        if (pending == null) return;
        if (level.getDifficulty() == Difficulty.PEACEFUL || !level.getGameRules().getBoolean(GameRules.RULE_DOINSOMNIA)
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        for (Phantom phantom : pending) {
            if (phantom.isRemoved() || !phantom.isAddedToWorld() || phantom.getPersistentData().getBoolean(PAIRED)) continue;
            phantom.getPersistentData().putBoolean(PAIRED, true);
            for (int attempt = 0; attempt < 8; attempt++) {
                CockroachEntity roach = Cockroach.COCKROACH.get().create(level);
                if (roach == null) break;
                roach.moveTo(phantom.position().add(level.random.nextInt(7) - 3, level.random.nextInt(3) - 1, level.random.nextInt(7) - 3));
                if (!level.hasChunkAt(roach.blockPosition()) || !level.getWorldBorder().isWithinBounds(roach.blockPosition())
                        || !level.noCollision(roach) || !level.getFluidState(roach.blockPosition()).isEmpty()) continue;
                ForgeEventFactory.onFinalizeSpawn(roach, level, level.getCurrentDifficultyAt(roach.blockPosition()), MobSpawnType.NATURAL, null, null);
                roach.setInsomniaSpawned(true);
                if (!roach.startFlying()) continue;
                level.addFreshEntity(roach);
                break;
            }
        }
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) PENDING.remove(level);
    }
}
