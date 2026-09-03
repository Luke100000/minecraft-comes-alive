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

    static Set<BlockPos> candidates(FloorSurface surface,
                                    Collection<FloorSurfacePartitioner.Component> components,
                                    FloorSurfacePartitioner.Component component) {
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
        int floorMinY = surface.anchorY() - 1;
        int floorCeilingY = surface.maxCeilingY();
        Set<Long> componentColumns = component.cells().stream()
                .map(cell -> FloorSurface.columnKey(cell.feet().getX(), cell.feet().getZ()))
                .collect(java.util.stream.Collectors.toSet());

        for (FloorSurface.Cell cell : component.cells()) {
            addColumn(result, cell.feet().getX(), cell.feet().getZ(),
                    floorMinY, floorCeilingY);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                int x = cell.feet().getX() + direction.getStepX();
                int z = cell.feet().getZ() + direction.getStepZ();
                if (!componentColumns.contains(FloorSurface.columnKey(x, z))
                        && ownsPerimeterColumn(component, components, x, z)) {
                    addColumn(result, x, z, floorMinY, floorCeilingY);
                }
            }
        }
        return Set.copyOf(result);
    }

    private static boolean ownsPerimeterColumn(FloorSurfacePartitioner.Component component,
                                               Collection<FloorSurfacePartitioner.Component> components,
                                               int x,
                                               int z) {
        if (components.stream().anyMatch(candidate -> candidate.containsColumn(x, z))) return false;
        BlockPos perimeter = new BlockPos(x, 0, z);
        return component.equals(FloorSurfacePartitioner.owner(
                FloorSurfacePartitioner.adjacent(perimeter, components)));
    }

    private static void addColumn(Set<BlockPos> result, int x, int z, int minY, int ceilingY) {
        for (int y = minY; y < ceilingY; y++) {
            result.add(new BlockPos(x, y, z));
        }
    }
}
