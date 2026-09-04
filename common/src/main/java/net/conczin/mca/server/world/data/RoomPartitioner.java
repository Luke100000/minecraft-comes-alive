package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Pure Room topology over one exact FloorGeometry. */
final class RoomPartitioner {
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final Comparator<Component> OWNER_ORDER =
            Comparator.comparingInt(Component::area).reversed()
                    .thenComparingInt(Component::minX)
                    .thenComparingInt(Component::minZ)
                    .thenComparingInt(Component::maxX)
                    .thenComparingInt(Component::maxZ);

    private RoomPartitioner() {
    }

    static List<Component> partition(FloorGeometry geometry) {
        Set<BlockPos> boundaryCells = geometry.connectorTypesByCell().entrySet().stream()
                .filter(entry -> entry.getValue().roomBoundary())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        Set<BlockPos> visited = new HashSet<>();
        List<Component> openComponents = new ArrayList<>();

        List<FloorGeometry.Cell> seeds = geometry.cells().stream()
                .sorted(Comparator.comparingInt((FloorGeometry.Cell cell) -> cell.feet().getX())
                        .thenComparingInt(cell -> cell.feet().getZ())
                        .thenComparingInt(cell -> cell.feet().getY()))
                .toList();
        for (FloorGeometry.Cell seed : seeds) {
            if (boundaryCells.contains(seed.feet()) || !visited.add(seed.feet())) continue;
            LinkedHashSet<FloorGeometry.Cell> componentCells = new LinkedHashSet<>();
            ArrayDeque<FloorGeometry.Cell> queue = new ArrayDeque<>();
            queue.addLast(seed);

            while (!queue.isEmpty()) {
                FloorGeometry.Cell current = queue.removeFirst();
                componentCells.add(current);
                for (Direction direction : HORIZONTAL) {
                    int x = current.feet().getX() + direction.getStepX();
                    int z = current.feet().getZ() + direction.getStepZ();
                    for (FloorGeometry.Cell next : geometry.cellsAtColumn(x, z)) {
                        if (boundaryCells.contains(next.feet())
                                || visited.contains(next.feet())
                                || !FloorGeometry.canStep(current.surfaceY(), next.surfaceY())) {
                            continue;
                        }
                        visited.add(next.feet());
                        queue.addLast(next);
                    }
                }
            }
            openComponents.add(new Component(componentCells));
        }

        List<Component> result = assignBoundaryClusters(geometry, boundaryCells, openComponents);
        result.sort(Comparator.comparingInt(Component::minX)
                .thenComparingInt(Component::minZ)
                .thenComparingInt(Component::minY)
                .thenComparingInt(Component::maxX)
                .thenComparingInt(Component::maxZ)
                .thenComparingInt(Component::maxY));
        return List.copyOf(result);
    }

    private static List<Component> assignBoundaryClusters(FloorGeometry geometry,
                                                           Set<BlockPos> boundaryCells,
                                                           List<Component> openComponents) {
        if (boundaryCells.isEmpty()) return new ArrayList<>(openComponents);

        List<Set<FloorGeometry.Cell>> clusters = boundaryClusters(geometry, boundaryCells);
        Map<Component, LinkedHashSet<FloorGeometry.Cell>> additions = new HashMap<>();
        List<Component> unowned = new ArrayList<>();
        for (Set<FloorGeometry.Cell> cluster : clusters) {
            LinkedHashSet<Component> adjacent = new LinkedHashSet<>();
            for (FloorGeometry.Cell cell : cluster) {
                adjacent.addAll(adjacent(cell, openComponents));
            }
            Component owner = owner(adjacent);
            if (owner == null) {
                unowned.add(new Component(cluster));
            } else {
                additions.computeIfAbsent(owner, ignored -> new LinkedHashSet<>()).addAll(cluster);
            }
        }

        List<Component> result = new ArrayList<>();
        for (Component component : openComponents) {
            LinkedHashSet<FloorGeometry.Cell> cells = new LinkedHashSet<>(component.cells());
            cells.addAll(additions.getOrDefault(component, new LinkedHashSet<>()));
            result.add(new Component(cells));
        }
        result.addAll(unowned);
        return result;
    }

    private static List<Set<FloorGeometry.Cell>> boundaryClusters(FloorGeometry geometry,
                                                                  Set<BlockPos> boundaryCells) {
        Set<BlockPos> visited = new HashSet<>();
        List<Set<FloorGeometry.Cell>> result = new ArrayList<>();
        List<FloorGeometry.Cell> seeds = boundaryCells.stream()
                .map(geometry::cellAt)
                .flatMap(java.util.Optional::stream)
                .sorted(Comparator.comparingInt((FloorGeometry.Cell cell) -> cell.feet().getX())
                        .thenComparingInt(cell -> cell.feet().getZ())
                        .thenComparingInt(cell -> cell.feet().getY()))
                .toList();
        for (FloorGeometry.Cell seed : seeds) {
            if (!visited.add(seed.feet())) continue;
            LinkedHashSet<FloorGeometry.Cell> cluster = new LinkedHashSet<>();
            ArrayDeque<FloorGeometry.Cell> queue = new ArrayDeque<>();
            queue.addLast(seed);
            while (!queue.isEmpty()) {
                FloorGeometry.Cell current = queue.removeFirst();
                cluster.add(current);
                for (Direction direction : HORIZONTAL) {
                    int x = current.feet().getX() + direction.getStepX();
                    int z = current.feet().getZ() + direction.getStepZ();
                    for (FloorGeometry.Cell next : geometry.cellsAtColumn(x, z)) {
                        if (!boundaryCells.contains(next.feet())
                                || visited.contains(next.feet())
                                || !FloorGeometry.canStep(current.surfaceY(), next.surfaceY())) {
                            continue;
                        }
                        visited.add(next.feet());
                        queue.addLast(next);
                    }
                }
            }
            if (!cluster.isEmpty()) result.add(Set.copyOf(cluster));
        }
        return List.copyOf(result);
    }

    static Component owner(Collection<Component> adjacent) {
        return adjacent.stream().min(OWNER_ORDER).orElse(null);
    }

    static List<Component> adjacent(FloorGeometry.Cell floorCell, Collection<Component> components) {
        List<Component> result = new ArrayList<>();
        for (Component component : components) {
            boolean touches = false;
            for (Direction direction : HORIZONTAL) {
                int x = floorCell.feet().getX() + direction.getStepX();
                int z = floorCell.feet().getZ() + direction.getStepZ();
                if (component.cells().stream().anyMatch(candidate ->
                        candidate.feet().getX() == x
                                && candidate.feet().getZ() == z
                                && FloorGeometry.canStep(floorCell.surfaceY(), candidate.surfaceY()))) {
                    touches = true;
                    break;
                }
            }
            if (touches) result.add(component);
        }
        return List.copyOf(result);
    }

    static Component select(BlockPos source, FloorGeometry geometry, List<Component> components) {
        FloorGeometry.Cell sourceCell = resolveSourceCell(source, geometry);
        if (sourceCell == null) return null;

        for (Component component : components) {
            if (component.contains(sourceCell.feet())) return component;
        }
        return owner(adjacent(sourceCell, components));
    }

    private static FloorGeometry.Cell resolveSourceCell(BlockPos source, FloorGeometry geometry) {
        List<FloorGeometry.Cell> column = geometry.cellsAtColumn(source.getX(), source.getZ());
        if (column.isEmpty()) return nearestAdjacentCell(source, geometry);

        FloorGeometry.Cell interior = column.stream()
                .filter(cell -> cell.feet().getY() <= source.getY() && source.getY() < cell.ceilingY())
                .max(Comparator.comparingInt(cell -> cell.feet().getY()))
                .orElse(null);
        if (interior != null) return interior;

        return column.stream()
                .min(Comparator.comparingInt((FloorGeometry.Cell cell) -> Math.abs(cell.feet().getY() - source.getY()))
                        .thenComparingInt(cell -> cell.feet().getY()))
                .orElse(null);
    }

    private static FloorGeometry.Cell nearestAdjacentCell(BlockPos source, FloorGeometry geometry) {
        return geometry.cells().stream()
                .filter(cell -> Math.abs(cell.feet().getX() - source.getX())
                        + Math.abs(cell.feet().getZ() - source.getZ()) == 1)
                .min(Comparator.comparingInt((FloorGeometry.Cell cell) -> Math.abs(cell.feet().getY() - source.getY()))
                        .thenComparingInt(cell -> cell.feet().getX())
                        .thenComparingInt(cell -> cell.feet().getZ())
                        .thenComparingInt(cell -> cell.feet().getY()))
                .orElse(null);
    }

    record Component(Set<FloorGeometry.Cell> cells) {
        Component {
            cells = Set.copyOf(cells);
        }

        int area() {
            return cells.size();
        }

        boolean contains(BlockPos feet) {
            return cells.stream().anyMatch(cell -> cell.feet().equals(feet));
        }

        boolean containsColumn(int x, int z) {
            return cells.stream().anyMatch(cell -> cell.feet().getX() == x && cell.feet().getZ() == z);
        }

        int minX() {
            return cells.stream().mapToInt(cell -> cell.feet().getX()).min().orElse(0);
        }

        int minY() {
            return cells.stream().mapToInt(cell -> cell.feet().getY()).min().orElse(0);
        }

        int minZ() {
            return cells.stream().mapToInt(cell -> cell.feet().getZ()).min().orElse(0);
        }

        int maxX() {
            return cells.stream().mapToInt(cell -> cell.feet().getX()).max().orElse(0);
        }

        int maxY() {
            return cells.stream().mapToInt(cell -> cell.feet().getY()).max().orElse(0);
        }

        int maxZ() {
            return cells.stream().mapToInt(cell -> cell.feet().getZ()).max().orElse(0);
        }

        Set<BlockPos> floorCells() {
            return cells.stream().map(FloorGeometry.Cell::feet).collect(Collectors.toUnmodifiableSet());
        }
    }
}
