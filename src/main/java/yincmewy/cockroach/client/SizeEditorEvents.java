package yincmewy.cockroach.client;

import net.minecraft.client.Minecraft;
import com.erchien.modelloader.client.render.ShaderDetection;
import net.minecraftforge.event.TickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yincmewy.cockroach.Cockroach;
import yincmewy.cockroach.entity.CockroachEntity;
import yincmewy.cockroach.network.SizeNetwork;

@Mod.EventBusSubscriber(modid = Cockroach.MODID, value = Dist.CLIENT)
public final class SizeEditorEvents {
    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) ShaderDetection.poll();
    }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide || !event.getItemStack().is(Cockroach.SIZE_EDITOR.get())
                || !(event.getTarget() instanceof CockroachEntity roach)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!SizeNetwork.canEdit(event.getEntity())) {
            event.getEntity().displayClientMessage(Component.translatable("screen.cockroach.size.permission"), true);
            return;
        }
        Minecraft.getInstance().setScreen(new SizeEditorScreen(roach));
    }
}
