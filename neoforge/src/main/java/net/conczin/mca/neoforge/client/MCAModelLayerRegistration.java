package net.conczin.mca.neoforge.client;

import net.conczin.mca.MCA;
import net.conczin.mca.client.model.MCALayerDefinitions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = MCA.MOD_ID, value = Dist.CLIENT)
public final class MCAModelLayerRegistration {
    private MCAModelLayerRegistration() {
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        MCALayerDefinitions.register(event::registerLayerDefinition);
    }
}
