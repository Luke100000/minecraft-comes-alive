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

    /**
     * The lowest-level compatibility contract used everywhere MCA reasons about beds.
     *
     * <p>Do not replace this with a block tag check: MCA deliberately supports
     * vanilla-style modded beds implemented as {@link BedBlock} subclasses even when
     * they are absent from {@code #minecraft:beds}.</p>
     */
    public static boolean isCompatibleBedState(BlockState state) {
        return state.getBlock() instanceof BedBlock
                && state.hasProperty(BedBlock.FACING)
                && state.hasProperty(BedBlock.PART)
                && state.hasProperty(BedBlock.OCCUPIED);
    }

    /**
     * The canonical state represented by a HOME POI.
     */
    public static boolean isHomePoiState(BlockState state) {
        return isCompatibleBedState(state) && state.getValue(BedBlock.PART) == BedPart.HEAD;
    }

    /**
     * A HOME POI that may currently be acquired by a villager.
     */
    public static boolean isAvailableHomePoiState(BlockState state) {
        return isHomePoiState(state) && !state.getValue(BedBlock.OCCUPIED);
    }

    public static Stream<BlockState> homeStates(Block block) {
        if (!(block instanceof BedBlock)) {
            return Stream.empty();
        }

        return block.getStateDefinition()
                .getPossibleStates()
                .stream()
                .filter(BedPoiCompatibility::isHomePoiState);
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
