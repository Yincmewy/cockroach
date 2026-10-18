package yincmewy.cockroach.entity;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/** Harmless lightning visuals and sounds. Conversion belongs to the converter item. */
public final class RainbowLightningBolt extends LightningBolt {
    private int life = 2;
    private int flashes;

    public RainbowLightningBolt(EntityType<? extends LightningBolt> type, Level level) {
        super(type, level);
        setVisualOnly(true);
        setDamage(0.0F);
        flashes = random.nextInt(3) + 1;
    }

    @Override
    public void tick() {
        // Restore vanilla's first-flash sounds locally on each tracking client, once per bolt.
        if (life == 2 && level().isClientSide && !isSilent()) {
            level().playLocalSound(getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.WEATHER, 10000.0F, 0.8F + random.nextFloat() * 0.2F, false);
            level().playLocalSound(getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                    SoundSource.WEATHER, 2.0F, 0.5F + random.nextFloat() * 0.2F, false);
        }
        // LightningBolt.tick() still powers rods and cleans copper even with visualOnly=true.
        // Keep its visual lifetime/seed changes without invoking any vanilla strike effects.
        if (--life < 0) {
            if (flashes == 0) {
                discard();
            } else if (life < -random.nextInt(10)) {
                flashes--;
                life = 1;
                seed = random.nextLong();
            }
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
