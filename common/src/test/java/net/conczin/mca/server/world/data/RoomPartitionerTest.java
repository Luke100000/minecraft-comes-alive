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
    void partitionUsesOnlyAcceptedFreshTransitions() {
        FloorGeometry geometry = geometry(Set.of(
                cell(0, 64, 0), cell(1, 64, 0), cell(2, 64, 0)), Map.of());
        Set<SelectedFloorScanner.Transition> transitions = Set.of(
                new SelectedFloorScanner.Transition(
                        new BlockPos(0, 64, 0), new BlockPos(1, 64, 0)));

        List<RoomPartitioner.Component> parts = RoomPartitioner.partition(geometry, transitions);

        assertEquals(2, parts.size());
        assertTrue(parts.stream().anyMatch(part -> part.area() == 2));
        assertTrue(parts.stream().anyMatch(part -> part.area() == 1));
    }

    @Test
    void gradualUnevenSurfaceRemainsOneComponentWithoutFlattening() {
        FloorGeometry geometry = geometry(Set.of(
                cell(0, 64, 0), cell(1, 64, 0), cell(2, 65, 0), cell(3, 66, 0)), Map.of());

        List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry, transitions(geometry));

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

        List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry, transitions(geometry));

        RoomPartitioner.Component startComponent = components.stream()
                .filter(component -> component.contains(start.feet())).findFirst().orElseThrow();
        assertTrue(startComponent.contains(step.feet()));
        assertFalse(startComponent.contains(tooLow.feet()));
    }

    @Test
    void sameColumnCellsDoNotConnectByThemselves() {
        FloorGeometry.Cell lower = cell(0, 88, 0);
        FloorGeometry.Cell upper = cell(0, 91, 0);

        FloorGeometry geometry = geometry(Set.of(lower, upper), Map.of());
        assertEquals(2, RoomPartitioner.partition(geometry, transitions(geometry)).size());
    }

    @Test
    void selectionUsesSourceHeightWhenTwoComponentsShareOneColumn() {
        FloorGeometry.Cell lower = cell(0, 88, 0);
        FloorGeometry.Cell upper = cell(0, 91, 0);
        FloorGeometry geometry = geometry(Set.of(lower, upper), Map.of());
        List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry, transitions(geometry));

        assertTrue(RoomPartitioner.select(new BlockPos(0, 91, 0), geometry, components)
                .contains(upper.feet()));
    }

    @Test
    void doorCellBelongsToOneRoomWithoutConnectingBothRooms() {
        BlockPos connectorCell = new BlockPos(1, 64, 0);
        FloorGeometry geometry = geometry(Set.of(
                cell(0, 64, 0), cell(1, 64, 0), cell(2, 64, 0)),
                Map.of(connectorCell, FloorConnector.Type.DOOR));
        Set<SelectedFloorScanner.Transition> transitions = Set.of(
                new SelectedFloorScanner.Transition(new BlockPos(0, 64, 0), connectorCell),
                new SelectedFloorScanner.Transition(connectorCell, new BlockPos(2, 64, 0)));

        List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry, transitions);

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
                                          Map<BlockPos, FloorConnector.Type> connectors) {
        return new FloorGeometry(cells, connectors);
    }

    private static FloorGeometry.Cell cell(int x, int y, int z) {
        return new FloorGeometry.Cell(new BlockPos(x, y, z), y + 4);
    }

    private static Set<SelectedFloorScanner.Transition> transitions(FloorGeometry geometry) {
        java.util.LinkedHashSet<SelectedFloorScanner.Transition> transitions = new java.util.LinkedHashSet<>();
        for (FloorGeometry.Cell first : geometry.cells()) {
            for (FloorGeometry.Cell second : geometry.cells()) {
                int horizontal = Math.abs(first.feet().getX() - second.feet().getX())
                        + Math.abs(first.feet().getZ() - second.feet().getZ());
                if (horizontal == 1 && Math.abs(first.feet().getY() - second.feet().getY()) <= 1) {
                    transitions.add(new SelectedFloorScanner.Transition(first.feet(), second.feet()));
                }
            }
        }
        return Set.copyOf(transitions);
    }
}
