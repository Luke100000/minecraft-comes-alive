package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Exact transient geometry for one selected semantic floor. */
record FloorSurface(Set<Cell> cells,
                    Map<BlockPos, StructureFloor.ConnectorType> connectorTypesByFloorCell,
                    Map<Long, Cell> cellsByColumn) {
    /** Matches Minecraft 1.21.1 WalkNodeEvaluator.DEFAULT_MOB_JUMP_HEIGHT. */
    static final double MAX_STEP_HEIGHT = 1.125D;

    FloorSurface(Set<Cell> cells, Map<BlockPos, StructureFloor.ConnectorType> connectorTypesByFloorCell) {
        this(cells, connectorTypesByFloorCell, Map.of());
    }

    FloorSurface {
        LinkedHashSet<Cell> topologyCells = new LinkedHashSet<>(cells);
        connectorTypesByFloorCell = Map.copyOf(connectorTypesByFloorCell);
        Map<Long, Cell> ordinaryByColumn = indexByColumn(topologyCells);
        for (BlockPos connectorCell : connectorTypesByFloorCell.keySet()) {
            long key = columnKey(connectorCell.getX(), connectorCell.getZ());
            if (ordinaryByColumn.containsKey(key)) continue;
            Cell reference = nearestReferenceCell(connectorCell, topologyCells).orElse(null);
            double surfaceY = reference == null ? connectorCell.getY() : reference.surfaceY();
            int ceilingY = reference == null ? connectorCell.getY() + 2 : reference.ceilingY();
            topologyCells.add(new Cell(connectorCell, surfaceY, ceilingY));
        }
        cells = Set.copyOf(topologyCells);
        cellsByColumn = indexByColumn(cells);
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

    FloorSurface withConnectorTypes(Map<BlockPos, StructureFloor.ConnectorType> connectors) {
        return new FloorSurface(cells, connectors);
    }

    static boolean canStep(double fromSurfaceY, double toSurfaceY) {
        return Math.abs(toSurfaceY - fromSurfaceY) <= MAX_STEP_HEIGHT;
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
                        + cell.feet().getX() + "," + cell.feet().getZ()
                        + " first=" + previous + " second=" + cell);
            }
        }
        return Map.copyOf(indexed);
    }

    private static Optional<Cell> nearestReferenceCell(BlockPos connectorCell, Set<Cell> cells) {
        return cells.stream().min(Comparator
                .comparingInt((Cell cell) -> Math.abs(cell.feet().getX() - connectorCell.getX())
                        + Math.abs(cell.feet().getZ() - connectorCell.getZ()))
                .thenComparingInt(cell -> Math.abs(cell.feet().getY() - connectorCell.getY()))
                .thenComparingInt(cell -> cell.feet().getX())
                .thenComparingInt(cell -> cell.feet().getZ()));
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
