package yincmewy.cockroach.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import yincmewy.cockroach.Cockroach;
import yincmewy.cockroach.entity.CockroachEntity;

import java.util.Optional;
import java.util.function.Supplier;

public final class SizeNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(Cockroach.MODID, "size"), () -> VERSION, VERSION::equals, VERSION::equals);

    private SizeNetwork() {}
    public static void register() {
        CHANNEL.registerMessage(0, SetSize.class, SetSize::encode, SetSize::decode, SetSize::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    public static boolean canEdit(Player player) {
        return player != null && (player.isCreative() || player.hasPermissions(2));
    }
    public static boolean holdsEditor(Player player) {
        return player.getMainHandItem().is(Cockroach.SIZE_EDITOR.get()) || player.getOffhandItem().is(Cockroach.SIZE_EDITOR.get());
    }

    public record SetSize(int entityId, float size) {
        private void encode(FriendlyByteBuf buffer) { buffer.writeVarInt(entityId); buffer.writeFloat(size); }
        private static SetSize decode(FriendlyByteBuf buffer) { return new SetSize(buffer.readVarInt(), buffer.readFloat()); }
        private void handle(Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                ServerPlayer player = context.getSender();
                if (!canEdit(player) || !holdsEditor(player) || !CockroachEntity.isValidSize(size)
                        || player.getCooldowns().isOnCooldown(Cockroach.SIZE_EDITOR.get())) return;
                Entity target = player.level().getEntity(entityId);
                if (!(target instanceof CockroachEntity roach) || !roach.isAlive()
                        || !roach.getBoundingBox().inflate(8.0D).contains(player.getEyePosition())) return;
                roach.setCockroachSize(size);
                player.getCooldowns().addCooldown(Cockroach.SIZE_EDITOR.get(), 5);
                player.displayClientMessage(Component.translatable("commands.cockroach.resized", 1, size), true);
            });
            context.setPacketHandled(true);
        }
    }
}
