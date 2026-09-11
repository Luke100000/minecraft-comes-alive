package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        assertTrue(lower.sameFootprint(upper));
        assertFalse(lower.sameExactGeometry(upper));
        assertEquals(1, lower.footprintIntersectionArea(partial));
        assertFalse(lower.sameFootprint(partial));
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
        assertEquals(1, geometry.projection().area());
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
}
