package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static FloorSurface.Cell cell(int x, int y, int z) {
        return new FloorSurface.Cell(new BlockPos(x, y, z), y, y + 4);
    }
}
