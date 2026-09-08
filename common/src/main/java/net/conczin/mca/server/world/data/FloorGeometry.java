package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Exact physical geometry for one semantic Floor. */
final class FloorGeometry {
    private final Set<Cell> cells;
    private final Map<BlockPos, FloorConnector.Type> connectorTypesByCell;
    private final Map<Long, List<Cell>> cellsByColumn;
    private final Map<BlockPos, Cell> cellsByPosition;

    FloorGeometry(Collection<Cell> cells,
                  Map<BlockPos, FloorConnector.Type> connectorTypesByCell) {
        LinkedHashMap<BlockPos, Cell> positions = new LinkedHashMap<>();
        for (Cell cell : cells) {
            Cell previous = positions.putIfAbsent(cell.feet(), cell);
            if (previous != null && !previous.equals(cell)) {
                throw new IllegalArgumentException("Conflicting FloorGeometry cells at " + cell.feet());
            }
        }
        this.cellsByPosition = Map.copyOf(positions);
        this.cells = Set.copyOf(positions.values());

        Map<BlockPos, FloorConnector.Type> connectors = connectorTypesByCell == null
                ? Map.of() : Map.copyOf(connectorTypesByCell);
        if (!this.cellsByPosition.keySet().containsAll(connectors.keySet())) {
            throw new IllegalArgumentException("Connector cell is not part of FloorGeometry");
        }
        this.connectorTypesByCell = connectors;
        this.cellsByColumn = indexColumns(this.cells);
    }

    static FloorGeometry flat(BuildingFloorRegion region,
                              int ceilingY,
                              Collection<FloorConnector.Marker> markers) {
        Objects.requireNonNull(region, "region");
        if (region.area() == 0) {
            throw new IllegalArgumentException("FloorGeometry requires non-empty region geometry");
        }
        Set<BlockPos> cells = region.cells();
        Map<BlockPos, FloorConnector.Type> connectors = new LinkedHashMap<>();
        if (markers != null) {
            for (FloorConnector.Marker marker : markers) {
                if (cells.contains(marker.pos())) connectors.put(marker.pos(), marker.type());
            }
        }
        return new FloorGeometry(cells.stream()
                .map(pos -> new Cell(pos, ceilingY))
                .toList(), connectors);
    }

    Set<Cell> cells() {
        return cells;
    }

    Map<BlockPos, FloorConnector.Type> connectorTypesByCell() {
        return connectorTypesByCell;
    }

    List<Cell> cellsAtColumn(int x, int z) {
        return cellsByColumn.getOrDefault(columnKey(x, z), List.of());
    }

    Optional<Cell> cellAt(BlockPos feet) {
        return Optional.ofNullable(cellsByPosition.get(feet));
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

    int maxPhysicalCeilingY() {
        return cells.stream().mapToInt(Cell::ceilingY).max().orElse(anchorY() + 2);
    }

    int minFeetY() {
        return cells.stream().mapToInt(cell -> cell.feet().getY()).min().orElse(anchorY());
    }

    Optional<Cell> physicalCellAt(int x, int y, int z) {
        return cellsAtColumn(x, z).stream()
                .filter(cell -> cell.feet().getY() <= y && y < cell.ceilingY())
                .max(Comparator.comparingInt(cell -> cell.feet().getY()));
    }

    Optional<Cell> interactionCellAt(int x, int y, int z) {
        Optional<Cell> physical = physicalCellAt(x, y, z);
        if (physical.isPresent()) return physical;
        return cellsAtColumn(x, z).stream()
                .filter(cell -> y == cell.feet().getY() - 1)
                .max(Comparator.comparingInt(cell -> cell.feet().getY()));
    }

    BuildingFloorRegion projection() {
        int y = anchorY();
        Set<BlockPos> projected = cells.stream()
                .map(cell -> new BlockPos(cell.feet().getX(), y, cell.feet().getZ()))
                .collect(Collectors.toUnmodifiableSet());
        return BuildingFloorRegion.fromFootprint(y, projected);
    }

    boolean sameFootprint(FloorGeometry other) {
        return other != null && cellsByColumn.keySet().equals(other.cellsByColumn.keySet());
    }

    int footprintArea() {
        return cellsByColumn.size();
    }

    int footprintIntersectionArea(FloorGeometry other) {
        if (other == null || cellsByColumn.isEmpty() || other.cellsByColumn.isEmpty()) return 0;
        Map<Long, List<Cell>> smaller = cellsByColumn.size() <= other.cellsByColumn.size()
                ? cellsByColumn : other.cellsByColumn;
        Map<Long, List<Cell>> larger = smaller == cellsByColumn ? other.cellsByColumn : cellsByColumn;
        int intersection = 0;
        for (Long column : smaller.keySet()) {
            if (larger.containsKey(column)) intersection++;
        }
        return intersection;
    }

    List<FloorConnector.Marker> connectorMarkers() {
        return connectorTypesByCell.entrySet().stream()
                .map(entry -> new FloorConnector.Marker(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt((FloorConnector.Marker marker) -> marker.pos().getX())
                        .thenComparingInt(marker -> marker.pos().getZ())
                        .thenComparingInt(marker -> marker.pos().getY())
                        .thenComparing(marker -> marker.type().serializedName()))
                .toList();
    }

    static long columnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static Map<Long, List<Cell>> indexColumns(Collection<Cell> cells) {
        Map<Long, List<Cell>> indexed = new LinkedHashMap<>();
        for (Cell cell : cells) {
            indexed.computeIfAbsent(columnKey(cell.feet().getX(), cell.feet().getZ()), ignored -> new ArrayList<>())
                    .add(cell);
        }
        indexed.replaceAll((ignored, column) -> column.stream()
                .sorted(Comparator.comparingInt(cell -> cell.feet().getY()))
                .toList());
        return Map.copyOf(indexed);
    }

    record Cell(BlockPos feet, int ceilingY) {
        Cell {
            Objects.requireNonNull(feet, "feet");
            if (ceilingY <= feet.getY()) {
                throw new IllegalArgumentException("FloorGeometry cell ceiling must be above feet");
            }
            feet = feet.immutable();
        }
    }
}
