package yincmewy.cockroach.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yincmewy.cockroach.Cockroach;
import yincmewy.cockroach.ServerConfig;
import yincmewy.cockroach.entity.CockroachEntity;

@Mod.EventBusSubscriber(modid = Cockroach.MODID)
public final class CockroachCommands {
    private static final SimpleCommandExceptionType NO_COCKROACHES = new SimpleCommandExceptionType(Component.translatable("commands.cockroach.no_targets"));

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cockroach").requires(source -> source.hasPermission(2))
                .then(Commands.literal("fly").then(Commands.argument("targets", EntityArgument.entities())
                        .executes(ctx -> {
                            int count = 0;
                            for (var entity : EntityArgument.getEntities(ctx, "targets")) {
                                if (entity instanceof CockroachEntity roach && roach.startFlying()) count++;
                            }
                            int changed = count;
                            ctx.getSource().sendSuccess(() -> Component.translatable("commands.cockroach.flying", changed), true);
                            return count;
                        })))
                .then(Commands.literal("size").then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(CockroachEntity.MIN_SIZE, CockroachEntity.MAX_SIZE))
                                .executes(ctx -> {
                                    float scale = FloatArgumentType.getFloat(ctx, "scale");
                                    int count = 0;
                                    for (var entity : EntityArgument.getEntities(ctx, "targets")) {
                                        if (entity instanceof CockroachEntity roach) {
                                            roach.setCockroachSize(scale);
                                            count++;
                                        }
                                    }
                                    if (count == 0) throw NO_COCKROACHES.create();
                                    int changed = count;
                                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.cockroach.resized", changed, scale), true);
                                    return count;
                                }))))
                .then(Commands.literal("defaultsize")
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(CockroachEntity.MIN_SIZE, CockroachEntity.MAX_SIZE))
                                .executes(ctx -> {
                                    float scale = FloatArgumentType.getFloat(ctx, "scale");
                                    ServerConfig.DEFAULT_SIZE.set((double) scale);
                                    ServerConfig.SPEC.save();
                                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.cockroach.default_size", scale), true);
                                    return 1;
                                }))));
    }
}
