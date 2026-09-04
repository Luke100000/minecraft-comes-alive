package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScannedFloorTest {
    @Test
    void semanticCeilingCanStopAtNextStoreyBelowPhysicalRoof() {
        FloorSurface surface = new FloorSurface(Set.of(
                cell(0, 88, 0), cell(1, 88, 0), cell(2, 88, 0), cell(3, 88, 0),
                cell(3, 89, 1), cell(3, 90, 2)), Map.of());

        ScannedFloor floor = new ScannedFloor(surface, 91);

        assertEquals(91, floor.semanticCeilingY());
        assertEquals(94, floor.surface().maxCeilingY());
    }

    @Test
    void physicalFloorUsesPhysicalRoofWithoutNextStorey() {
        FloorSurface surface = new FloorSurface(Set.of(
                new FloorSurface.Cell(new BlockPos(0, 64, 0), 64, 72),
                new FloorSurface.Cell(new BlockPos(1, 64, 0), 64, 72)), Map.of());

        assertEquals(72, ScannedFloor.physical(surface).semanticCeilingY());
    }

    @Test
    void persistenceRetainsOnlySparseCellsExactlyOnSemanticCeilingBoundary() {
        BlockPos topStair = new BlockPos(6, 91, 0);
        FloorSurface surface = new FloorSurface(Set.of(
                cell(0, 88, 0), cell(1, 88, 0), cell(2, 88, 0), cell(3, 88, 0),
                cell(4, 89, 0), cell(5, 90, 0),
                new FloorSurface.Cell(topStair, 91, 94)), Map.of());

        StructureFloor persisted = new ScannedFloor(surface, 91).persistedFloor();

        assertEquals(Set.of(new BlockPos(6, 91, 0)), persisted.ceilingBoundaryRegion().cells());
    }

    private static FloorSurface.Cell cell(int x, int y, int z) {
        return new FloorSurface.Cell(new BlockPos(x, y, z), y, y + 4);
    }
}
