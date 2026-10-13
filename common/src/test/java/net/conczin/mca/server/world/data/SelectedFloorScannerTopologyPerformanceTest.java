package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectedFloorScannerTopologyPerformanceTest {
    @Test
    void largeGridNeighborIndexAvoidsDenseCoordinateHashProbeClusters() {
        EqualityCount comparisons = new EqualityCount();
        BlockPos[][] cells = new BlockPos[80][80];
        for (int x = 0; x < 80; x++) {
            for (int z = 0; z < 80; z++) cells[x][z] = new CountingBlockPos(x, 64, z, comparisons);
        }
        List<SelectedFloorScanner.Transition> edges = new ArrayList<>();
        for (int x = 0; x < 80; x++) {
            for (int z = 0; z < 80; z++) {
                if (x > 0) edges.add(new SelectedFloorScanner.Transition(cells[x - 1][z], cells[x][z]));
                if (z > 0) edges.add(new SelectedFloorScanner.Transition(cells[x][z - 1], cells[x][z]));
            }
        }

        var neighbors = SelectedFloorScanner.Transition.neighborIndex(edges);
        assertEquals(6400, neighbors.size());
        for (int x = 0; x < 80; x++) {
            for (int z = 0; z < 80; z++) {
                int degree = (x > 0 ? 1 : 0) + (x < 79 ? 1 : 0) + (z > 0 ? 1 : 0) + (z < 79 ? 1 : 0);
                assertEquals(degree, neighbors.get(cells[x][z]).size());
            }
        }
        assertEquals(Set.of(cells[1][0], cells[0][1]), Set.copyOf(neighbors.get(cells[0][0])));
        System.out.println("Dense coordinate neighbor-index equality probes: " + comparisons.count);
        assertTrue(comparisons.count < 51_200,
                "Dense coordinate hashes caused excessive neighbor-index probes: " + comparisons.count);
    }

    @Test
    void neighborIndexAndNeighborListsRemainImmutable() {
        BlockPos first = new BlockPos(1, 64, 1);
        BlockPos second = first.east();
        var neighbors = SelectedFloorScanner.Transition.neighborIndex(
                List.of(new SelectedFloorScanner.Transition(first, second)));

        assertThrows(UnsupportedOperationException.class, () -> neighbors.put(first, List.of()));
        assertThrows(UnsupportedOperationException.class, () -> neighbors.get(first).add(first));
        assertEquals(List.of(second), neighbors.get(first));
    }

    private static final class EqualityCount {
        private int count;
    }

    /** Real Minecraft coordinate hashing/equality, with only comparison work observed. */
    private static final class CountingBlockPos extends BlockPos {
        private final EqualityCount comparisons;

        private CountingBlockPos(int x, int y, int z, EqualityCount comparisons) {
            super(x, y, z);
            this.comparisons = comparisons;
        }

        @Override
        public boolean equals(Object other) {
            comparisons.count++;
            return super.equals(other);
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }
    }
}
