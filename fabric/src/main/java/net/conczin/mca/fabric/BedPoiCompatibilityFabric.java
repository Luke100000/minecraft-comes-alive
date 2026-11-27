package net.conczin.mca.fabric;

import net.conczin.mca.entity.ai.BedPoiCompatibility;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/** Fabric bridge for extending the vanilla HOME POI after every mod has registered its blocks. */
final class BedPoiCompatibilityFabric {
    private BedPoiCompatibilityFabric() {
    }

    static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
                BedPoiCompatibility.registerMissingHomePois(BedPoiCompatibilityFabric::registerPoiStates));
    }

    private static void registerPoiStates(ResourceKey<PoiType> typeKey, Set<BlockState> states) {
        Holder.Reference<PoiType> type = BuiltInRegistries.POINT_OF_INTEREST_TYPE.getHolder(typeKey).orElseThrow();
        PoiTypes.registerBlockStates(type, states);

        Set<BlockState> matchingStates = new HashSet<>(type.value().matchingStates());
        matchingStates.addAll(states);
        type.value().matchingStates = Set.copyOf(matchingStates);
    }
}
