package net.conczin.mca.fabric;

import net.conczin.mca.entity.ai.BedPoiCompatibility;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
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
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            Holder.Reference<PoiType> home = BuiltInRegistries.POINT_OF_INTEREST_TYPE
                    .getHolder(PoiTypes.HOME)
                    .orElseThrow();
            Set<BlockState> unregisteredStates = BedPoiCompatibility.unregisteredModdedHomeStates();

            PoiTypes.registerBlockStates(home, unregisteredStates);

            Set<BlockState> matchingStates = new HashSet<>(home.value().matchingStates());
            matchingStates.addAll(unregisteredStates);
            home.value().matchingStates = Set.copyOf(matchingStates);
        });
    }
}
