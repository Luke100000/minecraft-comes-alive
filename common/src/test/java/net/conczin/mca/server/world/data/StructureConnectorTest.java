package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureConnectorTest {
    @Test
    void verticalColumnConnectsOnlyFloorsItPhysicallyReaches() {
        StructureFloor candidate = floor(64, 68);
        StructureFloor connectedBelow = floor(60, 64);
        StructureFloor distantBelow = floor(52, 56);
        List<BlockPos> actualConnectorColumn = List.of(
                new BlockPos(0, 62, 0),
                new BlockPos(0, 63, 0),
                new BlockPos(0, 64, 0));

        assertTrue(StructureConnector.connectsFloors(actualConnectorColumn, candidate, connectedBelow));
        assertFalse(StructureConnector.connectsFloors(actualConnectorColumn, candidate, distantBelow));
    }

    @Test
    void verticalConnectorProbeSpansEntireFloorBandIncludingSupportBlock() {
        StructureFloor floor = floor(64, 68);

        assertEquals(List.of(63, 64, 65, 66, 67),
                StructureConnector.verticalProbePositions(floor, new BlockPos(0, 64, 0)).stream()
                        .map(BlockPos::getY)
                        .toList());
    }

    @Test
    void verticalColumnStillConnectsDistinctBandsWithLegacyOverlappingCeilings() {
        StructureFloor staleLower = floor(88, 93);
        StructureFloor upper = floor(91, 94);
        List<BlockPos> connectorColumn = List.of(
                new BlockPos(0, 90, 0),
                new BlockPos(0, 91, 0));

        assertTrue(StructureConnector.connectsFloors(connectorColumn, upper, staleLower));
    }

    @Test
    void legacyCeilingOverlapDoesNotMakeUpperOnlyConnectorReachLowerFloor() {
        StructureFloor staleLower = floor(88, 93);
        StructureFloor upper = floor(91, 94);

        assertFalse(StructureConnector.connectsFloors(
                List.of(new BlockPos(0, 92, 0)), upper, staleLower));
    }

    @Test
    void connectorMembershipProjectsExactHandoffHeightIntoConnectorColumn() {
        BlockPos left = new BlockPos(0, 64, 0);
        BlockPos right = new BlockPos(2, 64, 0);
        BlockPos connector = new BlockPos(1, 64, 0);
        FloorGeometry geometry = new FloorGeometry(Set.of(
                new FloorGeometry.Cell(left, 64, 68),
                new FloorGeometry.Cell(right, 64, 68)), java.util.Map.of());

        Set<BlockPos> membership = StructureConnector.floorMembershipCells(
                connector, geometry);

        assertEquals(Set.of(connector), membership);
    }

    @Test
    void connectorAssociationsMaterializeBoundaryCellBeforeGeometryValidation() {
        BlockPos left = new BlockPos(0, 64, 0);
        BlockPos right = new BlockPos(2, 64, 0);
        BlockPos connector = new BlockPos(1, 64, 0);
        FloorGeometry geometry = new FloorGeometry(Set.of(
                new FloorGeometry.Cell(left, 64, 68),
                new FloorGeometry.Cell(right, 64, 68)), java.util.Map.of());

        FloorGeometry augmented = StructureConnector.withConnectorAssociations(
                geometry, java.util.Map.of(connector, StructureFloor.ConnectorType.DOOR));

        assertTrue(augmented.cellAt(connector).isPresent());
        assertEquals(StructureFloor.ConnectorType.DOOR,
                augmented.connectorTypesByCell().get(connector));
    }

    private static StructureFloor floor(int anchorY, int ceilingY) {
        return new StructureFloor(0, anchorY, ceilingY, 0,
                BuildingFloorRegion.fromFootprint(anchorY, Set.of(
                        new BlockPos(0, anchorY, 0),
                        new BlockPos(1, anchorY, 0),
                        new BlockPos(0, anchorY, 1),
                        new BlockPos(1, anchorY, 1))),
                List.of(new StructureFloor.ConnectorMarker(
                        new BlockPos(0, anchorY, 0), StructureFloor.ConnectorType.LADDER)));
    }
}
