package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Exact transient geometry for one selected semantic floor. */
record FloorSurface(Set<Cell> cells, Map<BlockPos, BlockPos> connectorByFloorCell) {
    FloorSurface {
        cells = Set.copyOf(cells);
        connectorByFloorCell = Map.copyOf(connectorByFloorCell);
    }

    int anchorY() {
        Map<Integer, Long> counts = cells.stream().collect(Collectors.groupingBy(
                cell -> cell.feet().getY(), Collectors.counting()));
        return counts.entrySet().stream()
                .max(Comparator.<Map.Entry<Integer, Long>>comparingLong(Map.Entry::getValue)
                        .thenComparing(Comparator.comparingInt(Map.Entry<Integer, Long>::getKey).reversed()))
                .map(Map.Entry::getKey)
                .orElse(0);
    }

    Optional<Cell> cellAtColumn(int x, int z) {
        return cells.stream()
                .filter(cell -> cell.feet().getX() == x && cell.feet().getZ() == z)
                .findFirst();
    }

    Set<BlockPos> projectedCells() {
        int y = anchorY();
        return cells.stream()
                .map(cell -> new BlockPos(cell.feet().getX(), y, cell.feet().getZ()))
                .collect(Collectors.toUnmodifiableSet());
    }

    BuildingFloorRegion persistedRegion() {
        return BuildingFloorRegion.fromFootprint(anchorY(), projectedCells());
    }

    int maxCeilingY() {
        return cells.stream().mapToInt(Cell::ceilingY).max().orElse(anchorY() + 2);
    }

    record Cell(BlockPos feet, double surfaceY, int ceilingY) {
        Cell {
            feet = feet.immutable();
        }
    }
}
