package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectedFloorScannerTest {
    @Test
    void stepDecisionUsesVanillaJumpThreshold() {
        assertTrue(SelectedFloorScanner.canStep(64.0D, 65.0D));
        assertFalse(SelectedFloorScanner.canStep(64.0D, 65.25D));
    }

    @Test
    void selectedFloorBandDoesNotClimbIntoAnotherStorey() {
        assertTrue(SelectedFloorScanner.withinSelectedFloorBand(64, 66));
        assertFalse(SelectedFloorScanner.withinSelectedFloorBand(64, 67));
    }

    @Test
    void floorBandSelectionSplitsThreeBlockStoreysAcrossWalkableStairs() {
        Set<FloorSurface.Cell> cells = Set.of(
                cell(0, 88, 0), cell(1, 88, 0), cell(2, 88, 0), cell(3, 88, 0),
                cell(3, 89, 1),
                cell(3, 90, 2),
                cell(0, 91, 3), cell(1, 91, 3), cell(2, 91, 3), cell(3, 91, 3));

        Set<FloorSurface.Cell> lower = SelectedFloorScanner.selectFloorBand(cells, 90);
        Set<FloorSurface.Cell> upper = SelectedFloorScanner.selectFloorBand(cells, 91);

        assertEquals(Set.of(88, 89, 90), lower.stream()
                .map(cell -> cell.feet().getY()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of(91), upper.stream()
                .map(cell -> cell.feet().getY()).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void floorBandSelectionKeepsLegitimateTwoBlockSurfaceVariationTogether() {
        Set<FloorSurface.Cell> cells = Set.of(
                cell(0, 64, 0), cell(1, 64, 0), cell(2, 64, 0), cell(3, 64, 0),
                cell(3, 65, 1),
                cell(3, 66, 2));

        assertEquals(cells, SelectedFloorScanner.selectFloorBand(cells, 65));
    }

    private static FloorSurface.Cell cell(int x, int y, int z) {
        return new FloorSurface.Cell(new BlockPos(x, y, z), y, y + 4);
    }
}
