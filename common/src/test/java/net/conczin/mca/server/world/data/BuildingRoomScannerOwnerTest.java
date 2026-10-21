package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
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
        RoomPartitioner.Component outside = component(Set.of(cell(-1, 64, 0)));
        RoomPartitioner.Component interior = component(Set.of(
                cell(1, 64, 0), cell(2, 64, 0), cell(3, 64, 0)));

        assertEquals(interior, RoomPartitioner.owner(List.of(outside, interior)));
    }

    @Test
    void equalAreaComponentsUseStableBoundsTieBreak() {
        RoomPartitioner.Component first = component(Set.of(
                cell(-4, 64, 0), cell(-3, 64, 0)));
        RoomPartitioner.Component second = component(Set.of(
                cell(1, 64, 0), cell(2, 64, 0)));

        assertEquals(first, RoomPartitioner.owner(List.of(second, first)));
    }

    @Test
    void materializingSelectedRoomsKeepsSharedPerimeterEvidenceExclusive() {
        var left = component(Set.of(cell(0, 64, 0), cell(0, 64, 1),
                cell(0, 64, 2), cell(0, 64, 3)));
        var right = component(Set.of(cell(2, 64, 0), cell(2, 64, 1),
                cell(2, 64, 2), cell(2, 64, 3)));
        List<RoomPartitioner.Component> components = List.of(left, right);
        BlockPos source = new BlockPos(0, 64, 0);
        List<BuildingRoomScanner.Result> rooms = List.of(
                BuildingRoomScanner.materialize(source, 100, components, left),
                BuildingRoomScanner.materialize(source, 100, components, right));

        assertEquals(2, rooms.size());
        assertEquals(Building.validationResult.SUCCESS, rooms.get(0).status());
        assertEquals(Building.validationResult.SUCCESS, rooms.get(1).status());
        Set<BlockPos> leftPoi = visitedPoiCells(rooms.get(0));
        Set<BlockPos> rightPoi = visitedPoiCells(rooms.get(1));
        assertTrue(leftPoi.contains(new BlockPos(1, 65, 0)));
        assertFalse(rightPoi.contains(new BlockPos(1, 65, 0)));
        assertEquals(RoomPoiEvidence.candidates(List.of(left, right), left), leftPoi);
        assertEquals(RoomPoiEvidence.candidates(List.of(left, right), right), rightPoi);
    }

    @Test
    void connectorCellIsAssignedAfterPartitionToOneDeterministicRoom() {
        BlockPos connector = new BlockPos(1, 64, 0);
        FloorGeometry geometry = new FloorGeometry(Set.of(
                cell(0, 64, 0), cell(1, 64, 0),
                cell(2, 64, 0), cell(3, 64, 0)),
                Map.of(connector, FloorConnector.Type.DOOR));
        List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry, transitions(geometry));
        RoomPartitioner.Component owner = components.stream()
                .filter(component -> component.contains(connector)).findFirst().orElseThrow();

        Set<BlockPos> ownerFootprint = owner.floorCells();
        RoomPartitioner.Component other = components.stream()
                .filter(component -> !component.equals(owner))
                .findFirst().orElseThrow();
        Set<BlockPos> otherFootprint = other.floorCells();

        assertTrue(ownerFootprint.contains(connector));
        assertFalse(otherFootprint.contains(connector));
    }

    @Test
    void verticalConnectorCellParticipatesInNormalRoomTopologyAndFootprint() {
        BlockPos connector = new BlockPos(1, 64, 0);
        FloorGeometry geometry = new FloorGeometry(Set.of(
                cell(0, 64, 0), cell(1, 64, 0), cell(2, 64, 0), cell(3, 64, 0)),
                Map.of(connector, FloorConnector.Type.TRAPDOOR));

        List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry, transitions(geometry));

        assertEquals(1, components.size());
        RoomPartitioner.Component component = components.getFirst();
        assertTrue(component.floorCells()
                .contains(connector));
    }

    @Test
    void outerConnectorSourceSelectsItsOnlyAdjacentRoom() {
        BlockPos connector = new BlockPos(0, 64, 0);
        FloorGeometry geometry = new FloorGeometry(Set.of(
                cell(0, 64, 0), cell(1, 64, 0), cell(2, 64, 0), cell(3, 64, 0), cell(4, 64, 0)),
                Map.of(connector, FloorConnector.Type.DOOR));
        List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry, transitions(geometry));

        RoomPartitioner.Component selected = RoomPartitioner.select(connector, geometry, components);

        assertEquals(1, components.size());
        assertEquals(components.getFirst(), selected);
        assertTrue(selected.floorCells()
                .contains(connector));
    }

    @Test
    void sharedConnectorSourceSelectsTheSameDeterministicOwnerAsItsFloorCell() {
        BlockPos connector = new BlockPos(2, 64, 0);
        FloorGeometry geometry = new FloorGeometry(Set.of(
                cell(0, 64, 0), cell(1, 64, 0), cell(2, 64, 0),
                cell(3, 64, 0), cell(4, 64, 0), cell(5, 64, 0), cell(6, 64, 0)),
                Map.of(connector, FloorConnector.Type.DOOR));
        List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry, transitions(geometry));
        RoomPartitioner.Component owner = components.stream()
                .filter(component -> component.contains(connector)).findFirst().orElseThrow();

        RoomPartitioner.Component selected = RoomPartitioner.select(connector, geometry, components);

        assertEquals(owner, selected);
        assertTrue(selected.floorCells()
                .contains(connector));
    }

    private static FloorGeometry.Cell cell(int x, int y, int z) {
        return new FloorGeometry.Cell(new BlockPos(x, y, z), y + 4);
    }

    private static Set<BlockPos> visitedPoiCells(BuildingRoomScanner.Result result) {
        Set<BlockPos> cells = new HashSet<>();
        result.visitPoiCells(pos -> cells.add(pos.immutable()));
        return cells;
    }

    private static RoomPartitioner.Component component(Set<FloorGeometry.Cell> cells) {
        return new RoomPartitioner.Component(cells);
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
