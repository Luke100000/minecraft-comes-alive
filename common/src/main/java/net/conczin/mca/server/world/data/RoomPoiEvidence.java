package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Produces geometry candidates for configured Room POIs after topology has been decided. */
final class RoomPoiEvidence {
    private RoomPoiEvidence() {
    }

    static Set<BlockPos> candidates(Collection<RoomPartitioner.Component> components,
                                    RoomPartitioner.Component component) {
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
        OccupancyIndex occupancy = OccupancyIndex.create(components);

        for (FloorGeometry.Cell cell : component.cells()) {
            addColumn(result, cell.feet().getX(), cell.feet().getZ(),
                    cell.feet().getY() - 1, cell.ceilingY());
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                int x = cell.feet().getX() + direction.getStepX();
                int z = cell.feet().getZ() + direction.getStepZ();
                if (occupancy.ownsPerimeterColumn(component, cell, x, z)) {
                    addColumn(result, x, z, cell.feet().getY() - 1, cell.ceilingY());
                }
            }
        }
        return Set.copyOf(result);
    }

    private static boolean overlapsPoiInterval(FloorGeometry.Cell sourceCell,
                                               FloorGeometry.Cell candidate) {
        int sourceMinY = sourceCell.feet().getY() - 1;
        int sourceMaxY = sourceCell.ceilingY();
        return candidate.feet().getY() - 1 < sourceMaxY
                && sourceMinY < candidate.ceilingY();
    }

    private static void addColumn(Set<BlockPos> result, int x, int z, int minY, int ceilingY) {
        for (int y = minY; y < ceilingY; y++) {
            result.add(new BlockPos(x, y, z));
        }
    }

    private record Column(int x, int z) {
    }

    private record IndexedCell(int componentIndex, FloorGeometry.Cell cell) {
    }

    private record OccupancyIndex(
            List<RoomPartitioner.Component> components,
            Map<Column, List<IndexedCell>> cellsByColumn) {

        private static OccupancyIndex create(Collection<RoomPartitioner.Component> components) {
            List<RoomPartitioner.Component> orderedComponents = new ArrayList<>(components.size());
            Map<Column, List<IndexedCell>> cellsByColumn = new HashMap<>();
            int componentIndex = 0;
            for (RoomPartitioner.Component candidate : components) {
                orderedComponents.add(candidate);
                for (FloorGeometry.Cell cell : candidate.cells()) {
                    cellsByColumn.computeIfAbsent(column(cell), ignored -> new ArrayList<>())
                            .add(new IndexedCell(componentIndex, cell));
                }
                componentIndex++;
            }
            return new OccupancyIndex(List.copyOf(orderedComponents), immutableLists(cellsByColumn));
        }

        private boolean ownsPerimeterColumn(RoomPartitioner.Component component,
                                            FloorGeometry.Cell sourceCell,
                                            int x,
                                            int z) {
            List<IndexedCell> occupants = cellsByColumn.getOrDefault(new Column(x, z), List.of());
            if (occupants.stream().anyMatch(candidate -> overlapsPoiInterval(sourceCell, candidate.cell()))) {
                return false;
            }
            return component.equals(adjacentOwner(sourceCell, x, z));
        }

        private RoomPartitioner.Component adjacentOwner(FloorGeometry.Cell sourceCell, int x, int z) {
            BitSet adjacent = new BitSet(components.size());
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                Column neighbor = new Column(x + direction.getStepX(), z + direction.getStepZ());
                for (IndexedCell candidate : cellsByColumn.getOrDefault(neighbor, List.of())) {
                    if (Math.abs(candidate.cell().feet().getY() - sourceCell.feet().getY()) <= 1) {
                        adjacent.set(candidate.componentIndex());
                    }
                }
            }

            List<RoomPartitioner.Component> adjacentComponents = new ArrayList<>(adjacent.cardinality());
            for (int index = adjacent.nextSetBit(0); index >= 0; index = adjacent.nextSetBit(index + 1)) {
                adjacentComponents.add(components.get(index));
            }
            return RoomPartitioner.owner(adjacentComponents);
        }

        private static Column column(FloorGeometry.Cell cell) {
            return new Column(cell.feet().getX(), cell.feet().getZ());
        }

        private static <T> Map<Column, List<T>> immutableLists(Map<Column, List<T>> source) {
            Map<Column, List<T>> copy = new HashMap<>();
            source.forEach((column, values) -> copy.put(column, List.copyOf(values)));
            return Map.copyOf(copy);
        }
    }
}
