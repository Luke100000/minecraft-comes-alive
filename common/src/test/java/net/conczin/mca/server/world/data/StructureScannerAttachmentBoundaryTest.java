package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StructureScannerAttachmentBoundaryTest {
    @Test
    void oneSelectedSurfaceProducesExactlyOnePersistedFloor() {
        FloorGeometry surface = surfaceAt(74, Set.of(
                new BlockPos(0, 74, 0), new BlockPos(1, 74, 0),
                new BlockPos(0, 74, 1), new BlockPos(1, 74, 1)));

        StructureFloor floor = TestStructureFloors.create(0, 0, surface);

        assertEquals(74, floor.anchorY());
        assertEquals(4, floor.area());
    }

    private static FloorGeometry surfaceAt(int y, Set<BlockPos> positions) {
        LinkedHashSet<FloorGeometry.Cell> cells = new LinkedHashSet<>();
        for (BlockPos pos : positions) {
            cells.add(new FloorGeometry.Cell(pos, pos.getY() + 4));
        }
        return new FloorGeometry(cells, Map.of());
    }
}
