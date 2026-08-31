package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.LinkedHashSet;
import java.util.Set;

/** Produces geometry candidates for configured Room POIs after topology has been decided. */
final class RoomPoiEvidence {
    private RoomPoiEvidence() {
    }

    static Set<BlockPos> candidates(FloorSurface surface,
                                    FloorSurfacePartitioner.Component component,
                                    Set<BlockPos> ownedConnectorCells) {
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
        Set<Long> componentColumns = component.cells().stream()
                .map(cell -> FloorSurface.columnKey(cell.feet().getX(), cell.feet().getZ()))
                .collect(java.util.stream.Collectors.toSet());

        for (FloorSurface.Cell cell : component.cells()) {
            addColumn(result, cell.feet().getX(), cell.feet().getZ(),
                    cell.feet().getY() - 1, cell.ceilingY());
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                int x = cell.feet().getX() + direction.getStepX();
                int z = cell.feet().getZ() + direction.getStepZ();
                if (!componentColumns.contains(FloorSurface.columnKey(x, z))) {
                    addColumn(result, x, z, cell.feet().getY() - 1, cell.ceilingY());
                }
            }
        }

        result.addAll(ownedConnectorCells);
        return Set.copyOf(result);
    }

    private static void addColumn(Set<BlockPos> result, int x, int z, int minY, int ceilingY) {
        for (int y = minY; y < ceilingY; y++) {
            result.add(new BlockPos(x, y, z));
        }
    }
}
