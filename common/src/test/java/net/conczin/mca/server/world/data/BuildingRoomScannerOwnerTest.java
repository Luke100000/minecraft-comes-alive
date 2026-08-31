package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void connectorCellIsAssignedAfterPartitionToOneDeterministicRoom() {
        BlockPos connector = new BlockPos(1, 64, 0);
        FloorSurface surface = new FloorSurface(Set.of(
                cell(0, 64, 0), cell(1, 64, 0),
                cell(2, 64, 0), cell(3, 64, 0)),
                Map.of(connector, connector));
        List<FloorSurfacePartitioner.Component> components = FloorSurfacePartitioner.partition(surface);
        FloorSurfacePartitioner.Component owner = FloorSurfacePartitioner.owner(
                FloorSurfacePartitioner.adjacent(connector, components));

        Set<BlockPos> ownerFootprint = BuildingRoomScanner.footprintForComponent(
                surface, components, owner, 64);
        FloorSurfacePartitioner.Component other = components.stream()
                .filter(component -> !component.equals(owner))
                .findFirst().orElseThrow();
        Set<BlockPos> otherFootprint = BuildingRoomScanner.footprintForComponent(
                surface, components, other, 64);

        assertTrue(ownerFootprint.contains(connector));
        assertFalse(otherFootprint.contains(connector));
    }

    private static FloorSurface.Cell cell(int x, int y, int z) {
        return new FloorSurface.Cell(new BlockPos(x, y, z), y, y + 4);
    }

    private static FloorSurfacePartitioner.Component component(Set<FloorSurface.Cell> cells) {
        return new FloorSurfacePartitioner.Component(cells);
    }
}
