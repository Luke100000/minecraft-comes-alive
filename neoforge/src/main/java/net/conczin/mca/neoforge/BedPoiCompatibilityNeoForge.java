package net.conczin.mca.neoforge;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.BedPoiCompatibility;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.poi.ExtendPoiTypesEvent;

/** NeoForge bridge for extending the vanilla HOME POI with vanilla-style modded beds. */
@EventBusSubscriber(modid = MCA.MOD_ID)
public final class BedPoiCompatibilityNeoForge {
    private BedPoiCompatibilityNeoForge() {
    }

    @SubscribeEvent
    public static void extendPoiTypes(ExtendPoiTypesEvent event) {
        event.addStatesToPoi(PoiTypes.HOME, BedPoiCompatibility.unregisteredModdedHomeStates());
    }
}
