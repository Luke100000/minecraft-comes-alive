package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectedFloorScannerTest {
    @Test
    void stepDecisionUsesVanillaJumpThreshold() {
        assertTrue(FloorGeometry.canStep(64.0D, 65.0D));
        assertFalse(FloorGeometry.canStep(64.0D, 65.25D));
    }

    @Test
    void selectedFloorBandDoesNotClimbIntoAnotherStorey() {
        assertTrue(StructureFloor.sameSemanticBand(64, 66));
        assertFalse(StructureFloor.sameSemanticBand(64, 67));
    }

    @Test
    void floorBandSelectionSplitsThreeBlockStoreysAcrossWalkableStairs() {
        Set<FloorGeometry.Cell> cells = Set.of(
                cell(0, 88, 0), cell(1, 88, 0), cell(2, 88, 0), cell(3, 88, 0),
                cell(3, 89, 1),
                cell(3, 90, 2),
                cell(0, 91, 3), cell(1, 91, 3), cell(2, 91, 3), cell(3, 91, 3));

        Set<FloorGeometry.Cell> lower = SelectedFloorScanner
                .floorSelection(cells, new BlockPos(3, 90, 2)).selected().cells();
        Set<FloorGeometry.Cell> upper = SelectedFloorScanner
                .floorSelection(cells, new BlockPos(0, 91, 3)).selected().cells();

        assertEquals(Set.of(88, 89, 90), lower.stream()
                .map(cell -> cell.feet().getY()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of(91), upper.stream()
                .map(cell -> cell.feet().getY()).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void floorSelectionReturnsExactSelectedBandWithoutEmbeddingSemanticCeiling() {
        Set<FloorGeometry.Cell> cells = Set.of(
                cell(0, 88, 0), cell(1, 88, 0), cell(2, 88, 0), cell(3, 88, 0),
                cell(3, 89, 1),
                cell(3, 90, 2),
                cell(0, 91, 3), cell(1, 91, 3), cell(2, 91, 3), cell(3, 91, 3));

        FloorGeometry floor = SelectedFloorScanner.floorSelection(cells, new BlockPos(0, 88, 0)).selected();

        assertEquals(Set.of(88, 89, 90), floor.cells().stream()
                .map(cell -> cell.feet().getY()).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void connectedBandsRetainWalkableStoreyEvidenceForAttachments() {
        Set<FloorGeometry.Cell> cells = Set.of(
                cell(0, 88, 0), cell(1, 88, 0), cell(2, 88, 0), cell(3, 88, 0),
                cell(3, 89, 1),
                cell(3, 90, 2),
                cell(0, 91, 3), cell(1, 91, 3), cell(2, 91, 3), cell(3, 91, 3));

        List<FloorGeometry> bands = SelectedFloorScanner
                .floorSelection(cells, new BlockPos(0, 88, 0)).connected();

        assertEquals(List.of(88, 91), bands.stream()
                .map(FloorGeometry::anchorY).toList());
        assertTrue(bands.stream().allMatch(band -> band.projection().area() >= 4));
    }

    @Test
    void topStairAtNextStoreyHeightStaysWithLowerBandAcrossDoorGap() {
        BlockPos topStair = new BlockPos(6, 91, 0);
        BlockPos upperRoom = new BlockPos(8, 91, 0);
        Set<FloorGeometry.Cell> cells = Set.of(
                cell(0, 88, 0), cell(1, 88, 0), cell(2, 88, 0), cell(3, 88, 0),
                cell(4, 89, 0),
                cell(5, 90, 0),
                cell(6, 91, 0),
                // x=7 is the door boundary: the walkability scan can cross it, but it is not
                // an ordinary FloorGeometry cell.
                cell(8, 91, 0), cell(9, 91, 0), cell(10, 91, 0), cell(11, 91, 0));

        FloorGeometry lower = SelectedFloorScanner.floorSelection(cells, topStair).selected();
        FloorGeometry upper = SelectedFloorScanner.floorSelection(cells, upperRoom).selected();

        assertEquals(88, lower.anchorY());
        assertFalse(lower.cellsAtColumn(topStair.getX(), topStair.getZ()).isEmpty());
        assertTrue(lower.cellsAtColumn(upperRoom.getX(), upperRoom.getZ()).isEmpty());
        assertEquals(7, lower.projection().area());

        assertEquals(91, upper.anchorY());
        assertTrue(upper.cellsAtColumn(topStair.getX(), topStair.getZ()).isEmpty());
        assertFalse(upper.cellsAtColumn(upperRoom.getX(), upperRoom.getZ()).isEmpty());
        assertEquals(4, upper.projection().area());
    }

    @Test
    void floorBandSelectionKeepsLegitimateTwoBlockSurfaceVariationTogether() {
        Set<FloorGeometry.Cell> cells = Set.of(
                cell(0, 64, 0), cell(1, 64, 0), cell(2, 64, 0), cell(3, 64, 0),
                cell(3, 65, 1),
                cell(3, 66, 2));

        assertEquals(cells, SelectedFloorScanner
                .floorSelection(cells, new BlockPos(3, 65, 1)).selected().cells());
    }

    @Test
    void stackedTopStairColumnStaysOnLowerFloorWithoutLosingUpperRoom() {
        BlockPos stackedColumn = new BlockPos(6, 91, 0);
        BlockPos upperRoom = new BlockPos(8, 91, 0);
        Set<FloorGeometry.Cell> cells = Set.of(
                cell(0, 88, 0), cell(1, 88, 0), cell(2, 88, 0), cell(3, 88, 0),
                cell(6, 88, 0),
                cell(4, 89, 0),
                cell(5, 90, 0),
                cell(6, 91, 0),
                cell(8, 91, 0), cell(9, 91, 0), cell(10, 91, 0), cell(11, 91, 0));

        FloorGeometry lower = SelectedFloorScanner.floorSelection(cells, stackedColumn).selected();
        FloorGeometry upper = SelectedFloorScanner.floorSelection(cells, upperRoom).selected();

        assertEquals(List.of(88, 91), lower.cellsAtColumn(6, 0).stream()
                .map(cell -> cell.feet().getY()).toList());
        assertEquals(88, lower.anchorY());
        assertTrue(upper.cellAt(upperRoom).isPresent());
        assertTrue(upper.cellAt(stackedColumn).isEmpty());
    }

    private static FloorGeometry.Cell cell(int x, int y, int z) {
        return new FloorGeometry.Cell(new BlockPos(x, y, z), y, y + 4);
    }
}
