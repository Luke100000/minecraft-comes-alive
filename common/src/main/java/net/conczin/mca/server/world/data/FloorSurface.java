package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Exact transient geometry for one selected semantic floor. */
record FloorSurface(Set<Cell> cells,
                    Map<BlockPos, BlockPos> connectorByFloorCell,
                    Map<Long, Cell> cellsByColumn) {
    /** Matches Minecraft 1.21.1 WalkNodeEvaluator.DEFAULT_MOB_JUMP_HEIGHT. */
    static final double MAX_STEP_HEIGHT = 1.125D;
    static final int BAND_TOLERANCE = 2;

    FloorSurface(Set<Cell> cells, Map<BlockPos, BlockPos> connectorByFloorCell) {
        this(Set.copyOf(cells), Map.copyOf(connectorByFloorCell), indexByColumn(cells));
    }

    FloorSurface {
        cells = Set.copyOf(cells);
        connectorByFloorCell = Map.copyOf(connectorByFloorCell);
        cellsByColumn = Map.copyOf(cellsByColumn);
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
        return Optional.ofNullable(cellsByColumn.get(columnKey(x, z)));
    }

    static boolean canStep(double fromSurfaceY, double toSurfaceY) {
        return Math.abs(toSurfaceY - fromSurfaceY) <= MAX_STEP_HEIGHT;
    }

    static boolean withinBand(int seedY, int candidateY) {
        return Math.abs(candidateY - seedY) <= BAND_TOLERANCE;
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

    private static Map<Long, Cell> indexByColumn(Set<Cell> cells) {
        LinkedHashMap<Long, Cell> indexed = new LinkedHashMap<>();
        for (Cell cell : cells) {
            long key = columnKey(cell.feet().getX(), cell.feet().getZ());
            Cell previous = indexed.putIfAbsent(key, cell);
            if (previous != null && !previous.equals(cell)) {
                throw new IllegalArgumentException("FloorSurface has multiple cells in one X/Z column: "
                        + cell.feet().getX() + "," + cell.feet().getZ());
            }
        }
        return Map.copyOf(indexed);
    }

    static long columnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    record Cell(BlockPos feet, double surfaceY, int ceilingY) {
        Cell {
            feet = feet.immutable();
        }
    }
}
