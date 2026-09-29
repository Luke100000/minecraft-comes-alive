package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Produces geometry candidates for configured Room POIs after topology has been decided. */
final class RoomPoiEvidence {
    private RoomPoiEvidence() {
    }

    static Set<BlockPos> candidates(Collection<RoomPartitioner.Component> components,
                                    RoomPartitioner.Component component) {
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();

        for (FloorGeometry.Cell cell : component.cells()) {
            addColumn(result, cell.feet().getX(), cell.feet().getZ(),
                    cell.feet().getY() - 1, cell.ceilingY());
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                int x = cell.feet().getX() + direction.getStepX();
                int z = cell.feet().getZ() + direction.getStepZ();
                if (!occupiesPoiColumn(component, cell, x, z)
                        && ownsPerimeterColumn(component, components, cell, x, z)) {
                    addColumn(result, x, z, cell.feet().getY() - 1, cell.ceilingY());
                }
            }
        }
        return Set.copyOf(result);
    }

    private static boolean ownsPerimeterColumn(RoomPartitioner.Component component,
                                               Collection<RoomPartitioner.Component> components,
                                               FloorGeometry.Cell sourceCell,
                                               int x,
                                               int z) {
        if (components.stream().anyMatch(candidate -> occupiesPoiColumn(candidate, sourceCell, x, z))) {
            return false;
        }
        FloorGeometry.Cell perimeter = new FloorGeometry.Cell(
                new BlockPos(x, sourceCell.feet().getY(), z),
                sourceCell.ceilingY());
        return component.equals(RoomPartitioner.owner(RoomPartitioner.adjacent(perimeter, components)));
    }

    private static boolean occupiesPoiColumn(RoomPartitioner.Component component,
                                             FloorGeometry.Cell sourceCell,
                                             int x,
                                             int z) {
        int sourceMinY = sourceCell.feet().getY() - 1;
        int sourceMaxY = sourceCell.ceilingY();
        return component.cells().stream().anyMatch(candidate ->
                candidate.feet().getX() == x
                        && candidate.feet().getZ() == z
                        && candidate.feet().getY() - 1 < sourceMaxY
                        && sourceMinY < candidate.ceilingY());
    }

    private static void addColumn(Set<BlockPos> result, int x, int z, int minY, int ceilingY) {
        for (int y = minY; y < ceilingY; y++) {
            result.add(new BlockPos(x, y, z));
        }
    }
}
