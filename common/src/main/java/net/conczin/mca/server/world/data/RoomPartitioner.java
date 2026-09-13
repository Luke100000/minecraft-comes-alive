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
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Pure Room topology over one exact FloorGeometry. */
final class RoomPartitioner {
    private static final Comparator<BlockPos> CELL_ORDER = Comparator
            .comparingInt((BlockPos pos) -> pos.getX())
            .thenComparingInt(pos -> pos.getZ())
            .thenComparingInt(pos -> pos.getY());
    private static final Comparator<Component> COMPONENT_ORDER = (first, second) ->
            compareFloorCellSets(first.floorCells(), second.floorCells());
    private static final Comparator<Component> OWNER_ORDER =
            Comparator.comparingInt(Component::area).reversed()
                    .thenComparing(COMPONENT_ORDER);

    private RoomPartitioner() {
    }

    static List<Component> partition(FloorGeometry geometry,
                                     Collection<SelectedFloorScanner.Transition> transitions) {
        return partition(geometry, transitions, Map.of(), Set.of());
    }

    static List<Component> partition(FloorGeometry geometry,
                                     Collection<SelectedFloorScanner.Transition> transitions,
                                     Map<BlockPos, Direction> doorOwnerSides) {
        return partition(geometry, transitions, doorOwnerSides, Set.of());
    }

    static List<Component> partition(FloorGeometry geometry,
                                     Collection<SelectedFloorScanner.Transition> transitions,
                                     Map<BlockPos, Direction> doorOwnerSides,
                                     Collection<BlockPos> storeyEdgeCells) {
        Collection<SelectedFloorScanner.Transition> acceptedTransitions =
                transitions == null ? List.of() : transitions;
        Map<BlockPos, Direction> ownerSides = doorOwnerSides == null
                ? Map.of() : Map.copyOf(doorOwnerSides);
        Map<BlockPos, List<BlockPos>> transitionNeighbors =
                SelectedFloorScanner.Transition.neighborIndex(acceptedTransitions);
        Set<BlockPos> connectorBoundaryCells = geometry.connectorTypesByCell().keySet().stream()
                .filter(geometry::isRoomBoundaryCell)
                .collect(Collectors.toSet());
        Set<BlockPos> storeyBoundaryCells = storeyEdgeCells == null
                ? Set.of()
                : storeyEdgeCells.stream()
                .filter(pos -> geometry.cellAt(pos).isPresent())
                .filter(pos -> !connectorBoundaryCells.contains(pos))
                .collect(Collectors.toSet());
        Set<BlockPos> boundaryCells = new HashSet<>(connectorBoundaryCells);
        boundaryCells.addAll(storeyBoundaryCells);
        Set<BlockPos> visited = new HashSet<>();
        List<Component> openComponents = new ArrayList<>();

        List<FloorGeometry.Cell> seeds = geometry.cells().stream()
                .sorted(Comparator.comparingInt((FloorGeometry.Cell cell) -> cell.feet().getX())
                        .thenComparingInt(cell -> cell.feet().getZ())
                        .thenComparingInt(cell -> cell.feet().getY()))
                .toList();
        for (FloorGeometry.Cell seed : seeds) {
            Set<FloorGeometry.Cell> component = connectedCells(
                    geometry, seed, visited, cell -> !boundaryCells.contains(cell.feet()), transitionNeighbors);
            if (!component.isEmpty()) openComponents.add(new Component(component));
        }

        List<Component> result = assignBoundaryClusters(
                geometry, connectorBoundaryCells, openComponents, transitionNeighbors, ownerSides);
        result = assignBoundaryClusters(
                geometry, storeyBoundaryCells, result, transitionNeighbors, Map.of());
        result.sort(COMPONENT_ORDER);
        return List.copyOf(result);
    }

    private static List<Component> assignBoundaryClusters(FloorGeometry geometry,
                                                           Set<BlockPos> boundaryCells,
                                                           List<Component> openComponents,
                                                           Map<BlockPos, List<BlockPos>> transitionNeighbors,
                                                           Map<BlockPos, Direction> doorOwnerSides) {
        if (boundaryCells.isEmpty()) return new ArrayList<>(openComponents);

        List<Set<FloorGeometry.Cell>> clusters = boundaryClusters(
                geometry, boundaryCells, transitionNeighbors);
        Map<BlockPos, Component> componentByCell = new HashMap<>();
        for (Component component : openComponents) {
            for (FloorGeometry.Cell cell : component.cells()) componentByCell.put(cell.feet(), component);
        }
        Map<Component, LinkedHashSet<FloorGeometry.Cell>> additions = new HashMap<>();
        List<Component> unowned = new ArrayList<>();
        for (Set<FloorGeometry.Cell> cluster : clusters) {
            LinkedHashSet<Component> adjacent = new LinkedHashSet<>();
            for (FloorGeometry.Cell cell : cluster) {
                for (BlockPos neighbor : transitionNeighbors.getOrDefault(cell.feet(), List.of())) {
                    Component component = componentByCell.get(neighbor);
                    if (component != null) adjacent.add(component);
                }
            }
            boolean containsDoor = cluster.stream().anyMatch(cell ->
                    geometry.connectorTypesByCell().get(cell.feet()) == FloorConnector.Type.DOOR);
            Component owner = containsDoor
                    ? doorOwner(cluster, componentByCell, transitionNeighbors, doorOwnerSides)
                    : owner(adjacent);
            if (containsDoor && owner == null && adjacent.size() == 1) {
                owner = adjacent.iterator().next();
            }
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

    private static Component doorOwner(Set<FloorGeometry.Cell> cluster,
                                       Map<BlockPos, Component> componentByCell,
                                       Map<BlockPos, List<BlockPos>> transitionNeighbors,
                                       Map<BlockPos, Direction> doorOwnerSides) {
        LinkedHashSet<Component> preferred = new LinkedHashSet<>();
        for (FloorGeometry.Cell cell : cluster) {
            BlockPos boundary = cell.feet();
            Direction side = doorOwnerSides.get(boundary);
            if (side == null) continue;
            for (BlockPos neighbor : transitionNeighbors.getOrDefault(boundary, List.of())) {
                if (!isOnSide(boundary, neighbor, side)) continue;
                Component component = componentByCell.get(neighbor);
                if (component != null) preferred.add(component);
            }
        }
        return preferred.size() == 1 ? preferred.iterator().next() : null;
    }

    private static boolean isOnSide(BlockPos boundary, BlockPos neighbor, Direction side) {
        return neighbor.getX() - boundary.getX() == side.getStepX()
                && neighbor.getZ() - boundary.getZ() == side.getStepZ();
    }

    private static List<Set<FloorGeometry.Cell>> boundaryClusters(FloorGeometry geometry,
                                                                  Set<BlockPos> boundaryCells,
                                                                  Map<BlockPos, List<BlockPos>> transitionNeighbors) {
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
            Set<FloorGeometry.Cell> cluster = connectedCells(
                    geometry, seed, visited, cell -> boundaryCells.contains(cell.feet()), transitionNeighbors);
            if (!cluster.isEmpty()) result.add(Set.copyOf(cluster));
        }
        return List.copyOf(result);
    }

    private static Set<FloorGeometry.Cell> connectedCells(FloorGeometry geometry,
                                                           FloorGeometry.Cell seed,
                                                           Set<BlockPos> visited,
                                                           Predicate<FloorGeometry.Cell> included,
                                                           Map<BlockPos, List<BlockPos>> transitionNeighbors) {
        if (!included.test(seed) || !visited.add(seed.feet())) return Set.of();
        LinkedHashSet<FloorGeometry.Cell> component = new LinkedHashSet<>();
        ArrayDeque<FloorGeometry.Cell> queue = new ArrayDeque<>();
        queue.addLast(seed);
        while (!queue.isEmpty()) {
            FloorGeometry.Cell current = queue.removeFirst();
            component.add(current);
            for (BlockPos nextPos : transitionNeighbors.getOrDefault(current.feet(), List.of())) {
                FloorGeometry.Cell next = nextPos == null ? null : geometry.cellAt(nextPos).orElse(null);
                if (next == null || !included.test(next) || !visited.add(next.feet())) continue;
                queue.addLast(next);
            }
        }
        return Set.copyOf(component);
    }

    static Component owner(Collection<Component> adjacent) {
        return adjacent.stream().min(OWNER_ORDER).orElse(null);
    }

    static int compareFloorCellSets(Set<BlockPos> first, Set<BlockPos> second) {
        List<BlockPos> orderedFirst = first.stream().sorted(CELL_ORDER).toList();
        List<BlockPos> orderedSecond = second.stream().sorted(CELL_ORDER).toList();
        int sharedSize = Math.min(orderedFirst.size(), orderedSecond.size());
        for (int index = 0; index < sharedSize; index++) {
            int comparison = CELL_ORDER.compare(orderedFirst.get(index), orderedSecond.get(index));
            if (comparison != 0) return comparison;
        }
        return Integer.compare(orderedFirst.size(), orderedSecond.size());
    }

    static List<Component> adjacent(FloorGeometry.Cell floorCell, Collection<Component> components) {
        return components.stream().filter(component -> component.cells().stream().anyMatch(candidate ->
                        Math.abs(candidate.feet().getX() - floorCell.feet().getX())
                                + Math.abs(candidate.feet().getZ() - floorCell.feet().getZ()) == 1
                                && Math.abs(candidate.feet().getY() - floorCell.feet().getY()) <= 1))
                .toList();
    }

    static Component select(BlockPos source, FloorGeometry geometry, List<Component> components) {
        FloorGeometry.Cell sourceCell = resolveSourceCell(source, geometry);
        if (sourceCell == null) return null;

        return components.stream()
                .filter(component -> component.contains(sourceCell.feet()))
                .findFirst()
                .orElse(null);
    }

    private static FloorGeometry.Cell resolveSourceCell(BlockPos source, FloorGeometry geometry) {
        List<FloorGeometry.Cell> column = geometry.cellsAtColumn(source.getX(), source.getZ());
        if (column.isEmpty()) return null;

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

        BlockPos nearestCell(BlockPos source) {
            return cells.stream()
                    .map(FloorGeometry.Cell::feet)
                    .min(Comparator.comparingInt((BlockPos cell) ->
                                    Math.abs(cell.getX() - source.getX())
                                            + Math.abs(cell.getY() - source.getY())
                                            + Math.abs(cell.getZ() - source.getZ()))
                            .thenComparingInt(BlockPos::getX)
                            .thenComparingInt(BlockPos::getY)
                            .thenComparingInt(BlockPos::getZ))
                    .orElse(source)
                    .immutable();
        }

        Set<BlockPos> floorCells() {
            return cells.stream().map(FloorGeometry.Cell::feet).collect(Collectors.toUnmodifiableSet());
        }
    }
}
