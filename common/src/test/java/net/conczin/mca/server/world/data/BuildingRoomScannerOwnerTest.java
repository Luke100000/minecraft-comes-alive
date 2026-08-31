package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildingRoomScannerOwnerTest {
    @Test
    void upperDoorHalfNormalizesToOneLowerConnector() {
        BlockPos upper = new BlockPos(4, 65, 7);

        assertEquals(new BlockPos(4, 64, 7),
                StructureConnector.normalizeDoorHalf(upper, DoubleBlockHalf.UPPER));
        assertEquals(upper,
                StructureConnector.normalizeDoorHalf(upper, DoubleBlockHalf.LOWER));
    }

    @Test
    void connectorOwnerChoosesLargestAdjacentInterior() {
        FloorSurfacePartitioner.Component outside = component(Set.of(cell(-1, 64, 0)));
        FloorSurfacePartitioner.Component interior = component(Set.of(
                cell(1, 64, 0), cell(2, 64, 0), cell(3, 64, 0)));

        assertEquals(interior, FloorSurfacePartitioner.owner(List.of(outside, interior)));
    }

    @Test
    void equalAreaComponentsUseStableBoundsTieBreak() {
        FloorSurfacePartitioner.Component first = component(Set.of(
                cell(-4, 64, 0), cell(-3, 64, 0)));
        FloorSurfacePartitioner.Component second = component(Set.of(
                cell(1, 64, 0), cell(2, 64, 0)));

        assertEquals(first, FloorSurfacePartitioner.owner(List.of(second, first)));
    }

    private static FloorSurface.Cell cell(int x, int y, int z) {
        return new FloorSurface.Cell(new BlockPos(x, y, z), y, y + 4);
    }

    private static FloorSurfacePartitioner.Component component(Set<FloorSurface.Cell> cells) {
        return new FloorSurfacePartitioner.Component(cells);
    }
}
