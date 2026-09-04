package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomPartitionerTest {
    @Test
    void gradualUnevenSurfaceRemainsOneComponentWithoutFlattening() {
        FloorGeometry geometry = geometry(Set.of(
                cell(0, 64, 0), cell(1, 64, 0), cell(2, 65, 0), cell(3, 66, 0)), Map.of());

        List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry);

        assertEquals(1, components.size());
        assertEquals(Set.of(64, 65, 66), components.getFirst().cells().stream()
                .map(cell -> cell.feet().getY()).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void adjacentColumnChoosesAllStepCompatibleCellsInsteadOfOneColumnRepresentative() {
        FloorGeometry.Cell start = cell(0, 90, 0);
        FloorGeometry.Cell tooLow = cell(1, 88, 0);
        FloorGeometry.Cell step = cell(1, 91, 0);
        FloorGeometry geometry = geometry(Set.of(start, tooLow, step), Map.of());

        List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry);

        RoomPartitioner.Component startComponent = components.stream()
                .filter(component -> component.contains(start.feet())).findFirst().orElseThrow();
        assertTrue(startComponent.contains(step.feet()));
        assertFalse(startComponent.contains(tooLow.feet()));
    }

    @Test
    void sameColumnCellsDoNotConnectByThemselves() {
        FloorGeometry.Cell lower = cell(0, 88, 0);
        FloorGeometry.Cell upper = cell(0, 91, 0);

        assertEquals(2, RoomPartitioner.partition(
                geometry(Set.of(lower, upper), Map.of())).size());
    }

    @Test
    void selectionUsesSourceHeightWhenTwoComponentsShareOneColumn() {
        FloorGeometry.Cell lower = cell(0, 88, 0);
        FloorGeometry.Cell upper = cell(0, 91, 0);
        FloorGeometry geometry = geometry(Set.of(lower, upper), Map.of());
        List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry);

        assertTrue(RoomPartitioner.select(new BlockPos(0, 91, 0), geometry, components)
                .contains(upper.feet()));
    }

    @Test
    void doorCellBelongsToOneRoomWithoutConnectingBothRooms() {
        BlockPos connectorCell = new BlockPos(1, 64, 0);
        FloorGeometry geometry = geometry(Set.of(
                cell(0, 64, 0), cell(1, 64, 0), cell(2, 64, 0)),
                Map.of(connectorCell, StructureFloor.ConnectorType.DOOR));

        List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry);

        assertEquals(2, components.size());
        assertEquals(1, components.stream().filter(component -> component.contains(connectorCell)).count());
        assertEquals(3, components.stream().mapToInt(RoomPartitioner.Component::area).sum());
    }

    @Test
    void connectorOwnerUsesLargestComponentThenStableBounds() {
        var small = new RoomPartitioner.Component(Set.of(cell(0, 64, 0)));
        var large = new RoomPartitioner.Component(Set.of(cell(2, 64, 0), cell(3, 64, 0)));

        assertEquals(large, RoomPartitioner.owner(List.of(small, large)));
    }

    private static FloorGeometry geometry(Set<FloorGeometry.Cell> cells,
                                          Map<BlockPos, StructureFloor.ConnectorType> connectors) {
        return new FloorGeometry(cells, connectors);
    }

    private static FloorGeometry.Cell cell(int x, int y, int z) {
        return new FloorGeometry.Cell(new BlockPos(x, y, z), y, y + 4);
    }
}
