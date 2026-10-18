package yincmewy.cockroach.item;

import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yincmewy.cockroach.Cockroach;

/** Consume interactions before villagers, horses, etc. open their normal interaction UI. */
@Mod.EventBusSubscriber(modid = Cockroach.MODID)
public final class ConverterEvents {
    private ConverterEvents() {}

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        convertInteraction(event, event.getTarget().position());
    }

    @SubscribeEvent
    public static void interactSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        convertInteraction(event, event.getTarget().position());
    }

    private static void convertInteraction(PlayerInteractEvent event, net.minecraft.world.phys.Vec3 position) {
        if (!(event.getItemStack().getItem() instanceof CockroachConverterItem converter)) return;
        converter.activate(event.getLevel(), event.getEntity(), position);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
    }
}
