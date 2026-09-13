package net.conczin.mca.entity.ai;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Bridges modded vanilla-style beds into Minecraft's HOME POI system.
 *
 * <p>MCA deliberately treats {@link BedBlock} as the compatibility contract instead of
 * guessing from block tags. The existing villager AI can then continue to use the vanilla
 * POI manager for discovery, ownership and occupancy.</p>
 */
public final class BedPoiCompatibility {
    private BedPoiCompatibility() {
    }

    public static Stream<BlockState> homeStates(Block block) {
        if (!(block instanceof BedBlock)) {
            return Stream.empty();
        }

        return block.getStateDefinition()
                .getPossibleStates()
                .stream()
                .filter(state -> state.hasProperty(BedBlock.PART))
                .filter(state -> state.getValue(BedBlock.PART) == BedPart.HEAD);
    }

    public static Set<BlockState> unregisteredModdedHomeStates() {
        return BuiltInRegistries.BLOCK.stream()
                .filter(block -> block instanceof BedBlock)
                .filter(block -> !"minecraft".equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace()))
                .flatMap(BedPoiCompatibility::homeStates)
                .filter(state -> PoiTypes.forState(state).isEmpty())
                .collect(Collectors.toSet());
    }
}
