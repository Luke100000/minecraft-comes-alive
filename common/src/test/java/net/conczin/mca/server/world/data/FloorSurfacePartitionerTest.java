package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FloorSurfacePartitionerTest {
    @Test
    void gradualUnevenSurfaceRemainsOneComponentWithoutFlattening() {
        FloorSurface surface = surface(Set.of(
                cell(0, 64, 0), cell(1, 64, 0), cell(2, 65, 0), cell(3, 66, 0)), Map.of());

        List<FloorSurfacePartitioner.Component> components = FloorSurfacePartitioner.partition(surface);

        assertEquals(1, components.size());
        assertEquals(Set.of(64, 65, 66), components.getFirst().cells().stream()
                .map(cell -> cell.feet().getY()).collect(Collectors.toSet()));
    }

    @Test
    void doorCellBelongsToOneRoomWithoutConnectingBothRooms() {
        BlockPos connectorCell = new BlockPos(1, 64, 0);
        FloorSurface surface = surface(Set.of(
                cell(0, 64, 0), cell(1, 64, 0), cell(2, 64, 0)),
                Map.of(connectorCell, StructureFloor.ConnectorType.DOOR));

        List<FloorSurfacePartitioner.Component> components = FloorSurfacePartitioner.partition(surface);

        assertEquals(2, components.size());
        assertEquals(1, components.stream().filter(component -> component.containsColumn(1, 0)).count());
        assertEquals(3, components.stream().mapToInt(FloorSurfacePartitioner.Component::area).sum());
    }

    @Test
    void connectorOwnerUsesLargestComponentThenStableBounds() {
        var small = component(Set.of(cell(0, 64, 0)));
        var large = component(Set.of(cell(2, 64, 0), cell(3, 64, 0)));

        assertEquals(large, FloorSurfacePartitioner.owner(List.of(small, large)));
    }

    private static FloorSurface surface(Set<FloorSurface.Cell> cells,
                                        Map<BlockPos, StructureFloor.ConnectorType> connectors) {
        return new FloorSurface(cells, connectors);
    }

    private static FloorSurface.Cell cell(int x, int y, int z) {
        return new FloorSurface.Cell(new BlockPos(x, y, z), y, y + 4);
    }

    private static FloorSurfacePartitioner.Component component(Set<FloorSurface.Cell> cells) {
        return new FloorSurfacePartitioner.Component(cells);
    }
}
