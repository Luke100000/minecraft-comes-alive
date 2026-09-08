package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
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
    void acceptedTransitionIsUndirectedForRoomConnectivity() {
        BlockPos a = new BlockPos(0, 64, 0);
        BlockPos b = new BlockPos(1, 64, 0);
        SelectedFloorScanner.Transition edge = new SelectedFloorScanner.Transition(a, b);

        assertTrue(edge.connects(a, b));
        assertTrue(edge.connects(b, a));
    }

    @Test
    void lowerStoreyOwnsSparseUpwardTransitionButNotUpperRoom() {
        Set<SelectedFloorScanner.SurfaceCell> cells = staircaseCells();
        Set<SelectedFloorScanner.Transition> transitions = staircaseTransitions();
        BlockPos topStair = new BlockPos(6, 91, 0);

        SelectedFloorScanner.StoreyScan lower = SelectedFloorScanner.selectStorey(
                cells, transitions, topStair);

        assertEquals(88, lower.floor().anchorY());
        assertTrue(lower.floor().cellAt(topStair).isPresent());
        assertTrue(lower.floor().cellAt(new BlockPos(8, 91, 0)).isEmpty());
        assertTrue(lower.transitions().contains(new SelectedFloorScanner.Transition(
                new BlockPos(5, 90, 0), topStair)));
    }

    @Test
    void upperRoomDoesNotReclaimLowerOwnedTopTransition() {
        BlockPos topStair = new BlockPos(6, 91, 0);
        BlockPos upperRoom = new BlockPos(8, 91, 0);

        SelectedFloorScanner.StoreyScan upper = SelectedFloorScanner.selectStorey(
                staircaseCells(), staircaseTransitions(), upperRoom);

        assertEquals(91, upper.floor().anchorY());
        assertTrue(upper.floor().cellAt(topStair).isEmpty());
        assertTrue(upper.floor().cellAt(upperRoom).isPresent());
        assertEquals(4, upper.floor().cells().size());
    }

    @Test
    void upperRoomDoesNotReclaimAdjacentLowerOwnedTopTransition() {
        BlockPos topStair = new BlockPos(6, 91, 0);
        BlockPos upperRoom = new BlockPos(8, 91, 0);
        Set<SelectedFloorScanner.SurfaceCell> cells = Set.of(
                cell(0, 88, 0), cell(1, 88, 0), cell(2, 88, 0), cell(3, 88, 0),
                cell(4, 89, 0), cell(5, 90, 0), cell(6, 91, 0),
                cell(7, 91, 0), cell(8, 91, 0), cell(9, 91, 0), cell(10, 91, 0));
        Set<SelectedFloorScanner.Transition> transitions = Set.of(
                edge(0, 88, 1, 88), edge(1, 88, 2, 88), edge(2, 88, 3, 88),
                edge(3, 88, 4, 89), edge(4, 89, 5, 90), edge(5, 90, 6, 91),
                edge(6, 91, 7, 91), edge(7, 91, 8, 91), edge(8, 91, 9, 91),
                edge(9, 91, 10, 91));

        SelectedFloorScanner.StoreyScan upper = SelectedFloorScanner.selectStorey(
                cells, transitions, upperRoom);

        assertEquals(91, upper.floor().anchorY());
        assertTrue(upper.floor().cellAt(topStair).isEmpty());
        assertTrue(upper.floor().cellAt(upperRoom).isPresent());
    }

    @Test
    void stackedSameColumnCellsSurviveSelectedStoreyTraversal() {
        BlockPos lowerStack = new BlockPos(6, 88, 0);
        BlockPos upperStack = new BlockPos(6, 91, 0);
        Set<SelectedFloorScanner.SurfaceCell> cells = Set.of(
                cell(0, 88, 0), cell(1, 88, 0), cell(2, 88, 0), cell(3, 88, 0),
                cell(4, 89, 0),
                cell(5, 90, 0),
                cell(6, 88, 0), cell(6, 91, 0));
        Set<SelectedFloorScanner.Transition> transitions = Set.of(
                edge(0, 88, 1, 88), edge(1, 88, 2, 88), edge(2, 88, 3, 88),
                edge(3, 88, 4, 89), edge(4, 89, 5, 90), edge(5, 90, 6, 91),
                edge(3, 88, 6, 88));

        SelectedFloorScanner.StoreyScan lower = SelectedFloorScanner.selectStorey(
                cells, transitions, upperStack);

        assertEquals(List.of(88, 91), lower.floor().cellsAtColumn(6, 0).stream()
                .map(cell -> cell.feet().getY()).toList());
        assertTrue(lower.floor().cellAt(lowerStack).isPresent());
    }

    @Test
    void unevenCellsAcrossThreeIntegerHeightsRemainOneStorey() {
        Set<SelectedFloorScanner.SurfaceCell> cells = Set.of(
                cell(0, 64, 0), cell(1, 64, 0), cell(2, 65, 0), cell(3, 66, 0));
        Set<SelectedFloorScanner.Transition> transitions = Set.of(
                edge(0, 64, 1, 64), edge(1, 64, 2, 65), edge(2, 65, 3, 66));

        SelectedFloorScanner.StoreyScan scan = SelectedFloorScanner.selectStorey(
                cells, transitions, new BlockPos(2, 65, 0));

        assertEquals(cells.stream().map(SelectedFloorScanner.SurfaceCell::canonical)
                .collect(java.util.stream.Collectors.toSet()), scan.floor().cells());
    }

    @Test
    void selectedStoreyDoesNotDependOnIterationOrder() {
        ArrayList<SelectedFloorScanner.SurfaceCell> forward = new ArrayList<>(staircaseCells());
        ArrayList<SelectedFloorScanner.SurfaceCell> reversed = new ArrayList<>(forward);
        java.util.Collections.reverse(reversed);
        BlockPos seed = new BlockPos(6, 91, 0);

        SelectedFloorScanner.StoreyScan first = SelectedFloorScanner.selectStorey(
                forward, staircaseTransitions(), seed);
        SelectedFloorScanner.StoreyScan second = SelectedFloorScanner.selectStorey(
                reversed, staircaseTransitions(), seed);

        assertEquals(first.floor().cells(), second.floor().cells());
        assertEquals(first.transitions(), second.transitions());
    }

    private static Set<SelectedFloorScanner.SurfaceCell> staircaseCells() {
        return Set.of(
                cell(0, 88, 0), cell(1, 88, 0), cell(2, 88, 0), cell(3, 88, 0),
                cell(4, 89, 0), cell(5, 90, 0), cell(6, 91, 0),
                cell(8, 91, 0), cell(9, 91, 0), cell(10, 91, 0), cell(11, 91, 0));
    }

    private static Set<SelectedFloorScanner.Transition> staircaseTransitions() {
        return Set.of(
                edge(0, 88, 1, 88), edge(1, 88, 2, 88), edge(2, 88, 3, 88),
                edge(3, 88, 4, 89), edge(4, 89, 5, 90), edge(5, 90, 6, 91),
                edge(8, 91, 9, 91), edge(9, 91, 10, 91), edge(10, 91, 11, 91));
    }

    private static SelectedFloorScanner.Transition edge(int x1, int y1, int x2, int y2) {
        return new SelectedFloorScanner.Transition(
                new BlockPos(x1, y1, 0), new BlockPos(x2, y2, 0));
    }

    private static SelectedFloorScanner.SurfaceCell cell(int x, int y, int z) {
        return new SelectedFloorScanner.SurfaceCell(new BlockPos(x, y, z), y, y + 4);
    }
}
