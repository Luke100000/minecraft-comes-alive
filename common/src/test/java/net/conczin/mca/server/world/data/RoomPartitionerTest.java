package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void partitionIndexesAcceptedTransitionsOnce() {
        Set<FloorGeometry.Cell> cells = new java.util.LinkedHashSet<>();
        List<SelectedFloorScanner.Transition> edges = new ArrayList<>();
        for (int x = 0; x < 64; x++) {
            cells.add(cell(x, 64, 0));
            if (x > 0) {
                edges.add(new SelectedFloorScanner.Transition(
                        new BlockPos(x - 1, 64, 0), new BlockPos(x, 64, 0)));
            }
        }
        CountingTransitions transitions = new CountingTransitions(edges);

        List<RoomPartitioner.Component> parts = RoomPartitioner.partition(geometry(cells, Map.of()), transitions);

        assertEquals(1, parts.size());
        assertEquals(64, parts.getFirst().area());
        assertEquals(1, transitions.iteratorCalls,
                "partition repeatedly scanned the full transition collection");
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
    void selectionDoesNotBorrowHorizontallyAdjacentFloorCell() {
        FloorGeometry.Cell onlyCell = cell(1, 64, 0);
        FloorGeometry geometry = geometry(Set.of(onlyCell), Map.of());
        List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry, transitions(geometry));

        assertNull(RoomPartitioner.select(new BlockPos(0, 64, 0), geometry, components));
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

        List<RoomPartitioner.Component> components = RoomPartitioner.partition(
                geometry, transitions, Map.of(connectorCell, Direction.WEST));

        assertEquals(2, components.size());
        assertEquals(1, components.stream().filter(component -> component.contains(connectorCell)).count());
        assertEquals(3, components.stream().mapToInt(RoomPartitioner.Component::area).sum());
    }

    @Test
    void preferredDoorSideBeatsAdjacentRoomSize() {
        BlockPos door = new BlockPos(2, 64, 0);
        FloorGeometry geometry = geometry(Set.of(
                cell(0, 64, 0), cell(1, 64, 0), cell(2, 64, 0),
                cell(3, 64, 0), cell(4, 64, 0), cell(5, 64, 0), cell(6, 64, 0)),
                Map.of(door, FloorConnector.Type.DOOR));

        List<RoomPartitioner.Component> components = RoomPartitioner.partition(
                geometry, transitions(geometry), Map.of(door, Direction.WEST));
        RoomPartitioner.Component owner = components.stream()
                .filter(component -> component.contains(door))
                .findFirst().orElseThrow();

        assertTrue(owner.contains(new BlockPos(1, 64, 0)));
        assertFalse(owner.contains(new BlockPos(3, 64, 0)));
    }

    @Test
    void preferredDoorSideMatchesOneBlockUnevenNeighbor() {
        BlockPos west = new BlockPos(1, 65, 0);
        BlockPos door = new BlockPos(2, 64, 0);
        BlockPos east = new BlockPos(3, 64, 0);
        FloorGeometry geometry = geometry(Set.of(
                cell(0, 65, 0), new FloorGeometry.Cell(west, 69),
                new FloorGeometry.Cell(door, 68), new FloorGeometry.Cell(east, 68),
                cell(4, 64, 0), cell(5, 64, 0)),
                Map.of(door, FloorConnector.Type.DOOR));

        List<RoomPartitioner.Component> components = RoomPartitioner.partition(
                geometry, transitions(geometry), Map.of(door, Direction.WEST));
        RoomPartitioner.Component owner = components.stream()
                .filter(component -> component.contains(door))
                .findFirst().orElseThrow();

        assertTrue(owner.contains(west));
        assertFalse(owner.contains(east));
    }

    @Test
    void doorWithoutOwnerSideDoesNotFallBackToLargerAdjacentRoom() {
        BlockPos door = new BlockPos(2, 64, 0);
        FloorGeometry geometry = geometry(Set.of(
                cell(0, 64, 0), cell(1, 64, 0), cell(2, 64, 0),
                cell(3, 64, 0), cell(4, 64, 0), cell(5, 64, 0), cell(6, 64, 0)),
                Map.of(door, FloorConnector.Type.DOOR));

        List<RoomPartitioner.Component> components = RoomPartitioner.partition(
                geometry, transitions(geometry), Map.of());
        RoomPartitioner.Component doorComponent = components.stream()
                .filter(component -> component.contains(door))
                .findFirst().orElseThrow();

        assertEquals(3, components.size());
        assertEquals(Set.of(door), doorComponent.floorCells());
    }

    @Test
    void selectionDoesNotBorrowAdjacentComponentWhenExactCellIsUnowned() {
        FloorGeometry.Cell sourceCell = cell(1, 64, 0);
        FloorGeometry geometry = geometry(Set.of(cell(0, 64, 0), sourceCell), Map.of());
        RoomPartitioner.Component adjacent = new RoomPartitioner.Component(Set.of(cell(0, 64, 0)));

        assertNull(RoomPartitioner.select(
                sourceCell.feet(), geometry, List.of(adjacent)));
    }

    @Test
    void threeArmIrregularFloorRemainsOneRoom() {
        FloorGeometry geometry = geometry(Set.of(
                cell(2, 64, 2),
                cell(1, 64, 2), cell(0, 64, 2),
                cell(3, 64, 2), cell(4, 64, 2),
                cell(2, 64, 3), cell(2, 64, 4)), Map.of());

        List<RoomPartitioner.Component> components = RoomPartitioner.partition(
                geometry, transitions(geometry));

        assertEquals(1, components.size());
        assertEquals(geometry.cells().size(), components.getFirst().area());
    }

    @Test
    void multipleDoorBoundariesPartitionEveryFloorCellExactlyOnce() {
        BlockPos firstDoor = new BlockPos(2, 64, 0);
        BlockPos secondDoor = new BlockPos(5, 64, 0);
        Set<FloorGeometry.Cell> cells = new java.util.LinkedHashSet<>();
        for (int x = 0; x <= 8; x++) cells.add(cell(x, 64, 0));
        FloorGeometry geometry = geometry(cells, Map.of(
                firstDoor, FloorConnector.Type.DOOR,
                secondDoor, FloorConnector.Type.DOOR));

        List<RoomPartitioner.Component> components = RoomPartitioner.partition(
                geometry, transitions(geometry), Map.of(
                        firstDoor, Direction.WEST,
                        secondDoor, Direction.WEST));
        Set<BlockPos> owned = components.stream()
                .flatMap(component -> component.floorCells().stream())
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(3, components.size());
        assertEquals(geometry.cells().size(), components.stream().mapToInt(RoomPartitioner.Component::area).sum());
        assertEquals(geometry.cells().size(), owned.size());
        assertEquals(1, components.stream().filter(component -> component.contains(firstDoor)).count());
        assertEquals(1, components.stream().filter(component -> component.contains(secondDoor)).count());
    }

    @Test
    void fallbackOwnerUsesLargestComponentThenStableBounds() {
        var small = new RoomPartitioner.Component(Set.of(cell(0, 64, 0)));
        var large = new RoomPartitioner.Component(Set.of(cell(2, 64, 0), cell(3, 64, 0)));

        assertEquals(large, RoomPartitioner.owner(List.of(small, large)));
    }

    @Test
    void fallbackOwnerUsesHeightToBreakStackedBoundsTie() {
        var lower = new RoomPartitioner.Component(Set.of(cell(0, 64, 0)));
        var upper = new RoomPartitioner.Component(Set.of(cell(0, 70, 0)));

        assertEquals(lower, RoomPartitioner.owner(List.of(upper, lower)));
        assertEquals(lower, RoomPartitioner.owner(List.of(lower, upper)));
    }

    @Test
    void fallbackOwnerUsesExactCellsToBreakIdenticalBoundsTie() {
        var first = new RoomPartitioner.Component(Set.of(
                cell(0, 64, 0), cell(1, 64, 1)));
        var second = new RoomPartitioner.Component(Set.of(
                cell(0, 64, 1), cell(1, 64, 0)));

        assertEquals(first, RoomPartitioner.owner(List.of(second, first)));
        assertEquals(first, RoomPartitioner.owner(List.of(first, second)));
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

    private static final class CountingTransitions extends AbstractCollection<SelectedFloorScanner.Transition> {
        private final List<SelectedFloorScanner.Transition> transitions;
        private int iteratorCalls;

        private CountingTransitions(List<SelectedFloorScanner.Transition> transitions) {
            this.transitions = List.copyOf(transitions);
        }

        @Override
        public Iterator<SelectedFloorScanner.Transition> iterator() {
            iteratorCalls++;
            return transitions.iterator();
        }

        @Override
        public int size() {
            return transitions.size();
        }
    }
}
