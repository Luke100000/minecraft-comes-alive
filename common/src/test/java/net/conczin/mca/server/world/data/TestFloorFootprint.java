package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/** Flat exact-cell fixture for tests that do not need Minecraft scanning. */
public record TestFloorFootprint(int anchorY, Set<BlockPos> cells) {
    public TestFloorFootprint {
        cells = cells == null ? Set.of() : Set.copyOf(cells);
    }

    public static TestFloorFootprint fromFootprint(int anchorY, Collection<BlockPos> footprintCells) {
        Set<BlockPos> cells = footprintCells == null ? Set.of() : footprintCells.stream()
                .map(pos -> new BlockPos(pos.getX(), anchorY, pos.getZ()))
                .collect(Collectors.toUnmodifiableSet());
        return new TestFloorFootprint(anchorY, cells);
    }
}
