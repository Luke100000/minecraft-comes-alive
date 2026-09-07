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
    void connectorCellIsAssignedAfterPartitionToOneDeterministicRoom() {
        BlockPos connector = new BlockPos(1, 64, 0);
        FloorGeometry geometry = new FloorGeometry(Set.of(
                cell(0, 64, 0), cell(1, 64, 0),
                cell(2, 64, 0), cell(3, 64, 0)),
                Map.of(connector, FloorConnector.Type.DOOR));
        List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry);
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

        List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry);

        assertEquals(1, components.size());
        RoomPartitioner.Component component = components.getFirst();
        assertTrue(component.containsColumn(connector.getX(), connector.getZ()));
        assertTrue(component.floorCells()
                .contains(connector));
    }

    @Test
    void outerConnectorSourceSelectsItsOnlyAdjacentRoom() {
        BlockPos connector = new BlockPos(0, 64, 0);
        FloorGeometry geometry = new FloorGeometry(Set.of(
                cell(0, 64, 0), cell(1, 64, 0), cell(2, 64, 0), cell(3, 64, 0), cell(4, 64, 0)),
                Map.of(connector, FloorConnector.Type.DOOR));
        List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry);

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
        List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry);
        RoomPartitioner.Component owner = components.stream()
                .filter(component -> component.contains(connector)).findFirst().orElseThrow();

        RoomPartitioner.Component selected = RoomPartitioner.select(connector, geometry, components);

        assertEquals(owner, selected);
        assertTrue(selected.floorCells()
                .contains(connector));
    }

    @Test
    void connectorFloorMembershipDependsOnTouchingSurfaceNotVerticalTraversalType() {
        BlockPos connector = new BlockPos(1, 65, 0);
        FloorGeometry geometry = new FloorGeometry(Set.of(
                cell(0, 64, 0), cell(2, 64, 0)), Map.of());

        Set<BlockPos> cells = StructureConnector.floorMembershipCells(connector, geometry);

        assertEquals(Set.of(new BlockPos(1, 64, 0)), cells);
    }

    @Test
    void connectorOnAnotherStoreyCannotClaimFloorByColumnAlone() {
        BlockPos upperDoor = new BlockPos(-303, 91, -1623);
        FloorGeometry lowerFloor = new FloorGeometry(Set.of(
                cell(-302, 88, -1623), cell(-301, 88, -1623)), Map.of());

        assertTrue(StructureConnector.floorMembershipCells(upperDoor, lowerFloor).isEmpty());
    }

    private static FloorGeometry.Cell cell(int x, int y, int z) {
        return new FloorGeometry.Cell(new BlockPos(x, y, z), y, y + 4);
    }

    private static RoomPartitioner.Component component(Set<FloorGeometry.Cell> cells) {
        return new RoomPartitioner.Component(cells);
    }
}
