package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloorGeometryTest {

    @Test
    void canonicalCellContainsOnlyMembershipAndCeilingMetadata() {
        assertEquals(List.of("feet", "ceilingY"),
                Arrays.stream(FloorGeometry.Cell.class.getRecordComponents())
                        .map(component -> component.getName())
                        .toList());
    }

    @Test
    void footprintComparisonIgnoresCellHeightButPreservesColumns() {
        FloorGeometry lower = new FloorGeometry(Set.of(
                new FloorGeometry.Cell(new BlockPos(0, 64, 0), 67),
                new FloorGeometry.Cell(new BlockPos(1, 64, 0), 67)), Map.of());
        FloorGeometry upper = new FloorGeometry(Set.of(
                new FloorGeometry.Cell(new BlockPos(0, 70, 0), 73),
                new FloorGeometry.Cell(new BlockPos(1, 70, 0), 73)), Map.of());
        FloorGeometry partial = new FloorGeometry(Set.of(
                new FloorGeometry.Cell(new BlockPos(1, 80, 0), 83),
                new FloorGeometry.Cell(new BlockPos(2, 80, 0), 83)), Map.of());

        assertTrue(lower.sameProjectedFootprint(upper));
        assertFalse(lower.sameExactGeometry(upper));
        assertEquals(1, lower.footprintIntersectionArea(partial));
        assertFalse(lower.sameProjectedFootprint(partial));
    }

    @Test
    void cellPositionComparisonUsesExactXyzButIgnoresConnectorMetadata() {
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos second = new BlockPos(1, 65, 0);
        FloorGeometry geometry = new FloorGeometry(Set.of(
                new FloorGeometry.Cell(first, 67),
                new FloorGeometry.Cell(second, 68)), Map.of());
        FloorGeometry sameCellsWithDoor = new FloorGeometry(Set.of(
                new FloorGeometry.Cell(first, 70),
                new FloorGeometry.Cell(second, 72)), Map.of(second, FloorConnector.Type.DOOR));
        FloorGeometry sameFootprintDifferentHeight = new FloorGeometry(Set.of(
                new FloorGeometry.Cell(new BlockPos(0, 70, 0), 73),
                new FloorGeometry.Cell(new BlockPos(1, 71, 0), 74)), Map.of());

        assertTrue(geometry.sameCellPositions(sameCellsWithDoor));
        assertTrue(geometry.sameProjectedFootprint(sameFootprintDifferentHeight));
        assertFalse(geometry.sameCellPositions(sameFootprintDifferentHeight));
    }

    @Test
    void exactGeometryIncludesConnectorMetadata() {
        BlockPos door = new BlockPos(1, 64, 0);
        Set<FloorGeometry.Cell> cells = Set.of(cell(0, 64, 0), cell(1, 64, 0));
        FloorGeometry plain = new FloorGeometry(cells, Map.of());
        FloorGeometry doorway = new FloorGeometry(cells, Map.of(door, FloorConnector.Type.DOOR));

        assertFalse(plain.sameExactGeometry(doorway));
        assertTrue(doorway.sameExactGeometry(new FloorGeometry(
                cells, Map.of(door, FloorConnector.Type.DOOR))));
    }

    @Test
    void roomIdentityOverlapExcludesBoundaryConnectorCells() {
        BlockPos ordinary = new BlockPos(0, 64, 0);
        BlockPos door = new BlockPos(1, 64, 0);
        BlockPos other = new BlockPos(2, 64, 0);
        FloorGeometry geometry = new FloorGeometry(Set.of(
                cell(0, 64, 0), cell(1, 64, 0), cell(2, 64, 0)),
                Map.of(door, FloorConnector.Type.DOOR));

        assertEquals(0, geometry.roomIdentityOverlapCount(
                Set.of(ordinary, door), Set.of(door, other)));
        assertEquals(1, geometry.roomIdentityOverlapCount(
                Set.of(ordinary, door), Set.of(ordinary, door)));
    }

    @Test
    void sameColumnCellsAtDifferentHeightsAreBothCanonical() {
        FloorGeometry.Cell lower = cell(2, 88, 3);
        FloorGeometry.Cell upper = cell(2, 91, 3);

        FloorGeometry geometry = new FloorGeometry(Set.of(lower, upper), Map.of());

        assertEquals(List.of(lower, upper), geometry.cellsAtColumn(2, 3));
        assertEquals(Set.of(lower, upper), geometry.cells());
        assertEquals(1, geometry.footprintArea());
    }

    @Test
    void cellLookupUsesFullBlockPosition() {
        FloorGeometry.Cell lower = cell(2, 88, 3);
        FloorGeometry.Cell upper = cell(2, 91, 3);
        FloorGeometry geometry = new FloorGeometry(Set.of(lower, upper), Map.of());

        assertEquals(lower, geometry.cellAt(lower.feet()).orElseThrow());
        assertEquals(upper, geometry.cellAt(upper.feet()).orElseThrow());
    }

    @Test
    void anchorUsesLargestHeightSliceThenLowerYForEqualSlices() {
        FloorGeometry geometry = new FloorGeometry(Set.of(
                cell(0, 65, 0), cell(1, 65, 0),
                cell(0, 64, 1), cell(1, 64, 1)), Map.of());

        assertEquals(64, geometry.anchorY());
    }

    @Test
    void cellsAtColumnAreAlwaysSortedByY() {
        FloorGeometry.Cell low = cell(0, 63, 0);
        FloorGeometry.Cell middle = cell(0, 65, 0);
        FloorGeometry.Cell high = cell(0, 68, 0);

        FloorGeometry geometry = new FloorGeometry(Set.of(high, low, middle), Map.of());

        assertEquals(List.of(low, middle, high), geometry.cellsAtColumn(0, 0));
    }

    @Test
    void connectorMustReferenceExistingExactCell() {
        BlockPos connector = new BlockPos(1, 64, 0);
        FloorGeometry geometry = new FloorGeometry(Set.of(
                cell(0, 64, 0), cell(1, 64, 0)),
                Map.of(connector, FloorConnector.Type.DOOR));

        assertTrue(geometry.connectorTypesByCell().containsKey(connector));
        assertEquals(List.of(new FloorConnector.Marker(
                connector, FloorConnector.Type.DOOR)), geometry.connectorMarkers());

        assertThrows(IllegalArgumentException.class, () -> new FloorGeometry(
                Set.of(cell(0, 64, 0)), Map.of(connector, FloorConnector.Type.DOOR)));
    }

    private static FloorGeometry.Cell cell(int x, int y, int z) {
        return new FloorGeometry.Cell(new BlockPos(x, y, z), y + 4);
    }

    @Test
    void rectangularFloorColumnIndexDoesNotDegenerateOnCoordinateHashes() {
        List<FloorGeometry.Cell> cells = new ArrayList<>();
        for (int x = 0; x < 192; x++) {
            for (int z = 0; z < 192; z++) {
                cells.add(cell(x, 64, z));
            }
        }

        // Packed X/Z keys have very few distinct Long hashes in a square footprint.
        // This generous bound catches quadratic probing, not ordinary scan latency.
        assertTimeout(Duration.ofSeconds(2), () -> {
            FloorGeometry geometry = new FloorGeometry(cells, Map.of());
            assertEquals(36_864, geometry.footprintArea());
            for (int pass = 0; pass < 3; pass++) {
                for (FloorGeometry.Cell expected : cells) {
                    assertEquals(List.of(expected), geometry.cellsAtColumn(
                            expected.feet().getX(), expected.feet().getZ()));
                }
            }
        });
    }

    @Test
    void geometryOwnsReadOnlySnapshotsOfSuppliedCellsAndConnectors() {
        List<FloorGeometry.Cell> cells = new ArrayList<>(List.of(cell(0, 64, 0), cell(1, 64, 0)));
        Map<BlockPos, FloorConnector.Type> connectors = new HashMap<>();
        connectors.put(new BlockPos(1, 64, 0), FloorConnector.Type.DOOR);
        FloorGeometry geometry = new FloorGeometry(cells, connectors);

        cells.clear();
        connectors.clear();

        assertEquals(2, geometry.footprintArea());
        assertEquals(cell(0, 64, 0), geometry.cellAt(new BlockPos(0, 64, 0)).orElseThrow());
        assertTrue(geometry.isRoomBoundaryCell(new BlockPos(1, 64, 0)));
        assertThrows(UnsupportedOperationException.class, () -> geometry.cells().clear());
        assertThrows(UnsupportedOperationException.class, () -> geometry.connectorTypesByCell().clear());
        assertThrows(UnsupportedOperationException.class, () -> geometry.cellsAtColumn(0, 0).clear());
    }
}
