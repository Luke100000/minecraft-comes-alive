package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloorSurfaceTest {
    @Test
    void keepsExactCellHeightsWhileProjectingOnePersistedFootprint() {
        FloorSurface surface = new FloorSurface(Set.of(
                cell(0, 64, 0),
                cell(1, 64, 0),
                cell(2, 65, 0),
                cell(3, 66, 0)), Map.of());

        assertEquals(Set.of(64, 65, 66), surface.cells().stream()
                .map(cell -> cell.feet().getY()).collect(Collectors.toSet()));
        assertEquals(64, surface.anchorY());
        assertEquals(4, surface.persistedRegion().area());
        assertTrue(surface.persistedRegion().cells().stream().allMatch(pos -> pos.getY() == 64));
    }

    @Test
    void anchorUsesLargestHeightSliceThenLowerYForEqualSlices() {
        FloorSurface surface = new FloorSurface(Set.of(
                cell(0, 65, 0), cell(1, 65, 0),
                cell(0, 64, 1), cell(1, 64, 1)), Map.of());

        assertEquals(64, surface.anchorY());
    }

    @Test
    void duplicateColumnFailureReportsBothCompetingSurfaceCells() {
        FloorSurface.Cell lower = cell(2, 64, 3);
        FloorSurface.Cell upper = cell(2, 65, 3);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new FloorSurface(Set.of(lower, upper), Map.of()));

        assertTrue(error.getMessage().contains(lower.toString()));
        assertTrue(error.getMessage().contains(upper.toString()));
    }

    @Test
    void rejectsGeometryThatSpansMoreThanOneSemanticFloorBand() {
        assertThrows(IllegalArgumentException.class,
                () -> new FloorSurface(Set.of(cell(0, 64, 0), cell(1, 67, 0)), Map.of()));
    }

    @Test
    void persistedRegionIncludesConnectorFloorCells() {
        BlockPos connector = new BlockPos(1, 64, 0);
        FloorSurface surface = new FloorSurface(Set.of(
                cell(0, 64, 0), cell(2, 64, 0)),
                Map.of(connector, StructureFloor.ConnectorType.TRAPDOOR));

        BuildingFloorRegion persisted = surface.persistedRegion();

        assertTrue(persisted.containsHorizontally(0, 0));
        assertTrue(persisted.containsHorizontally(1, 0));
        assertTrue(persisted.containsHorizontally(2, 0));
        assertEquals(3, persisted.area());
    }

    private static FloorSurface.Cell cell(int x, int y, int z) {
        return new FloorSurface.Cell(new BlockPos(x, y, z), y, y + 4);
    }
}
