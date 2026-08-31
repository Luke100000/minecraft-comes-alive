package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StructureScannerAttachmentBoundaryTest {
    @Test
    void oneSelectedSurfaceProducesExactlyOnePersistedFloor() {
        FloorSurface surface = surfaceAt(74, Set.of(
                new BlockPos(0, 74, 0), new BlockPos(1, 74, 0),
                new BlockPos(0, 74, 1), new BlockPos(1, 74, 1)));

        StructureFloor floor = StructureScanner.persistedFloor(surface);

        assertEquals(74, floor.anchorY());
        assertEquals(4, floor.area());
    }

    @Test
    void persistedOtherStoreyIsNotAWorldTraversalBoundaryConcept() {
        assertFalse(Arrays.stream(StructureScanner.class.getDeclaredClasses())
                .anyMatch(type -> type.getSimpleName().equals("PersistedFloorBoundary")));
    }

    private static FloorSurface surfaceAt(int y, Set<BlockPos> positions) {
        LinkedHashSet<FloorSurface.Cell> cells = new LinkedHashSet<>();
        for (BlockPos pos : positions) {
            cells.add(new FloorSurface.Cell(pos, pos.getY(), pos.getY() + 4));
        }
        return new FloorSurface(cells, Map.of());
    }
}
