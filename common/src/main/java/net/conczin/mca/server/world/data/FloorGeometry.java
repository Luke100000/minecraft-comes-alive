package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private final Set<FloorConnector.Marker> connectorMarkers;
    private final Map<Long, List<Cell>> cellsByColumn;
    private final Map<BlockPos, Cell> cellsByPosition;

    FloorGeometry(Collection<Cell> cells,
                  Map<BlockPos, FloorConnector.Type> connectorTypesByCell) {
        this(cells, markersFromTypes(connectorTypesByCell));
    }

    FloorGeometry(Collection<Cell> cells,
                  Collection<FloorConnector.Marker> connectorMarkers) {
        LinkedHashMap<BlockPos, Cell> positions = new LinkedHashMap<>();
        for (Cell cell : cells) {
            Cell previous = positions.putIfAbsent(cell.feet(), cell);
            if (previous != null && !previous.equals(cell)) {
                throw new IllegalArgumentException("Conflicting FloorGeometry cells at " + cell.feet());
            }
        }
        this.cellsByPosition = Map.copyOf(positions);
        this.cells = Set.copyOf(positions.values());

        LinkedHashMap<BlockPos, FloorConnector.Type> connectors = new LinkedHashMap<>();
        LinkedHashSet<FloorConnector.Marker> markers = new LinkedHashSet<>();
        Collection<FloorConnector.Marker> suppliedMarkers = connectorMarkers == null
                ? List.of() : connectorMarkers;
        for (FloorConnector.Marker marker : suppliedMarkers) {
            if (!this.cellsByPosition.containsKey(marker.floorCell())) {
                throw new IllegalArgumentException("Connector floor cell is not part of FloorGeometry");
            }
            connectors.putIfAbsent(marker.floorCell(), marker.type());
            markers.add(marker);
        }
        this.connectorTypesByCell = Map.copyOf(connectors);
        this.connectorMarkers = Set.copyOf(markers);
        this.cellsByColumn = indexColumns(this.cells);
    }

    Set<Cell> cells() {
        return cells;
    }

    Map<BlockPos, FloorConnector.Type> connectorTypesByCell() {
        return connectorTypesByCell;
    }

    boolean isRoomBoundaryCell(BlockPos cell) {
        FloorConnector.Type connector = connectorTypesByCell.get(cell);
        return connector != null && connector.roomBoundary();
    }

    boolean isRoomIdentityCell(BlockPos cell) {
        return !isRoomBoundaryCell(cell);
    }

    long roomIdentityOverlapCount(Set<BlockPos> first, Set<BlockPos> second) {
        if (first == null || second == null || first.isEmpty() || second.isEmpty()) return 0L;
        Set<BlockPos> candidates = first.size() <= second.size() ? first : second;
        Set<BlockPos> other = candidates == first ? second : first;
        return candidates.stream()
                .filter(this::isRoomIdentityCell)
                .filter(other::contains)
                .count();
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

    static Bounds bounds(Collection<Cell> cells, int minYOffset) {
        var iterator = cells.iterator();
        if (!iterator.hasNext()) throw new IllegalArgumentException("Floor bounds require non-empty cells");
        Cell first = iterator.next();
        BlockPos firstFeet = first.feet();
        int minX = firstFeet.getX(), minY = firstFeet.getY() + minYOffset, minZ = firstFeet.getZ();
        int maxX = minX, maxY = first.ceilingY() - 1, maxZ = minZ;
        while (iterator.hasNext()) {
            Cell cell = iterator.next();
            BlockPos feet = cell.feet();
            minX = Math.min(minX, feet.getX());
            minY = Math.min(minY, feet.getY() + minYOffset);
            minZ = Math.min(minZ, feet.getZ());
            maxX = Math.max(maxX, feet.getX());
            maxY = Math.max(maxY, cell.ceilingY() - 1);
            maxZ = Math.max(maxZ, feet.getZ());
        }
        return new Bounds(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
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

    boolean sameProjectedFootprint(FloorGeometry other) {
        return other != null && cellsByColumn.keySet().equals(other.cellsByColumn.keySet());
    }

    /** Exact Floor-cell membership, intentionally ignoring ceiling and connector metadata. */
    boolean sameCellPositions(FloorGeometry other) {
        return other != null && cellsByPosition.keySet().equals(other.cellsByPosition.keySet());
    }

    boolean sameExactGeometry(FloorGeometry other) {
        return other != null
                && cells.equals(other.cells)
                && connectorMarkers.equals(other.connectorMarkers);
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
        return connectorMarkers.stream()
                .sorted(Comparator.comparingInt((FloorConnector.Marker marker) -> marker.pos().getX())
                        .thenComparingInt(marker -> marker.pos().getZ())
                        .thenComparingInt(marker -> marker.pos().getY())
                        .thenComparing(marker -> marker.type().serializedName()))
                .toList();
    }

    private static Collection<FloorConnector.Marker> markersFromTypes(
            Map<BlockPos, FloorConnector.Type> connectorTypesByCell) {
        if (connectorTypesByCell == null || connectorTypesByCell.isEmpty()) return List.of();
        return connectorTypesByCell.entrySet().stream()
                .map(entry -> new FloorConnector.Marker(entry.getKey(), entry.getValue()))
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

    record Bounds(BlockPos min, BlockPos max) {}
}
