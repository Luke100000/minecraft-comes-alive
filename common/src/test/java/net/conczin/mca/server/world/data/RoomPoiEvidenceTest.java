package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomPoiEvidenceTest {
    @Test
    void perimeterIncludesWallColumnWithoutAddingItToRoomFootprint() {
        FloorSurface.Cell interior = new FloorSurface.Cell(new BlockPos(1, 64, 1), 64.0D, 68);
        FloorSurface surface = new FloorSurface(Set.of(interior), Map.of());
        var component = new FloorSurfacePartitioner.Component(Set.of(interior));

        Set<BlockPos> candidates = RoomPoiEvidence.candidates(surface, component, Set.of());

        assertTrue(candidates.contains(new BlockPos(0, 65, 1)));
        assertTrue(candidates.contains(new BlockPos(1, 63, 1)));
        assertFalse(component.projectedCells(surface.anchorY()).contains(new BlockPos(0, 64, 1)));
    }

    @Test
    void unevenCellsUseTheirOwnLocalVerticalEvidenceRange() {
        FloorSurface.Cell low = new FloorSurface.Cell(new BlockPos(0, 64, 0), 64.0D, 68);
        FloorSurface.Cell high = new FloorSurface.Cell(new BlockPos(1, 66, 0), 66.0D, 72);
        var surface = new FloorSurface(Set.of(low, high), Map.of());
        var component = new FloorSurfacePartitioner.Component(Set.of(low, high));

        Set<BlockPos> candidates = RoomPoiEvidence.candidates(surface, component, Set.of());

        assertTrue(candidates.contains(new BlockPos(1, 71, 1)));
    }
}
