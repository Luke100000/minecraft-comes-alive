package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Pure Room topology over one exact transient floor surface. */
final class FloorSurfacePartitioner {
    private static final double MAX_STEP_HEIGHT = 1.125D;
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final Comparator<Component> OWNER_ORDER =
            Comparator.comparingInt(Component::area).reversed()
                    .thenComparingInt(Component::minX)
                    .thenComparingInt(Component::minZ)
                    .thenComparingInt(Component::maxX)
                    .thenComparingInt(Component::maxZ);

    private FloorSurfacePartitioner() {
    }

    static List<Component> partition(FloorSurface surface) {
        Set<BlockPos> connectorCells = surface.connectorByFloorCell().keySet();
        Set<BlockPos> visited = new HashSet<>();
        List<Component> result = new ArrayList<>();

        List<FloorSurface.Cell> seeds = surface.cells().stream()
                .sorted(Comparator.comparingInt((FloorSurface.Cell cell) -> cell.feet().getX())
                        .thenComparingInt(cell -> cell.feet().getZ())
                        .thenComparingInt(cell -> cell.feet().getY()))
                .toList();
        for (FloorSurface.Cell seed : seeds) {
            if (connectorCells.contains(seed.feet()) || !visited.add(seed.feet())) continue;
            LinkedHashSet<FloorSurface.Cell> componentCells = new LinkedHashSet<>();
            ArrayDeque<FloorSurface.Cell> queue = new ArrayDeque<>();
            queue.addLast(seed);

            while (!queue.isEmpty()) {
                FloorSurface.Cell current = queue.removeFirst();
                componentCells.add(current);
                for (Direction direction : HORIZONTAL) {
                    int x = current.feet().getX() + direction.getStepX();
                    int z = current.feet().getZ() + direction.getStepZ();
                    FloorSurface.Cell next = surface.cellAtColumn(x, z).orElse(null);
                    if (next == null || connectorCells.contains(next.feet())
                            || visited.contains(next.feet()) || !connected(current, next)) {
                        continue;
                    }
                    visited.add(next.feet());
                    queue.addLast(next);
                }
            }
            result.add(new Component(componentCells));
        }

        result.sort(Comparator.comparingInt(Component::minX)
                .thenComparingInt(Component::minZ)
                .thenComparingInt(Component::maxX)
                .thenComparingInt(Component::maxZ));
        return List.copyOf(result);
    }

    private static boolean connected(FloorSurface.Cell first, FloorSurface.Cell second) {
        int dx = Math.abs(first.feet().getX() - second.feet().getX());
        int dz = Math.abs(first.feet().getZ() - second.feet().getZ());
        return dx + dz == 1 && Math.abs(first.surfaceY() - second.surfaceY()) <= MAX_STEP_HEIGHT;
    }

    static Component owner(Collection<Component> adjacent) {
        return adjacent.stream().min(OWNER_ORDER).orElse(null);
    }

    static List<Component> adjacent(BlockPos floorCell, Collection<Component> components) {
        List<Component> result = new ArrayList<>();
        for (Component component : components) {
            for (Direction direction : HORIZONTAL) {
                if (component.containsColumn(
                        floorCell.getX() + direction.getStepX(),
                        floorCell.getZ() + direction.getStepZ())) {
                    result.add(component);
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    static Component select(BlockPos source, FloorSurface surface, List<Component> components) {
        for (Component component : components) {
            if (component.containsColumn(source.getX(), source.getZ())) return component;
        }

        BlockPos sourceCell = surface.cellAtColumn(source.getX(), source.getZ())
                .map(FloorSurface.Cell::feet)
                .orElse(new BlockPos(source.getX(), surface.anchorY(), source.getZ()));
        List<Component> adjacent = adjacent(sourceCell, components);
        boolean connectorColumn = surface.connectorByFloorCell().keySet().stream()
                .anyMatch(cell -> cell.getX() == source.getX() && cell.getZ() == source.getZ());
        if (connectorColumn) return owner(adjacent);
        return adjacent.stream()
                .min(Comparator.comparingInt(Component::minX).thenComparingInt(Component::minZ))
                .orElse(null);
    }

    record Component(Set<FloorSurface.Cell> cells) {
        Component {
            cells = Set.copyOf(cells);
        }

        int area() {
            return cells.size();
        }

        int minX() {
            return cells.stream().mapToInt(cell -> cell.feet().getX()).min().orElse(0);
        }

        int minZ() {
            return cells.stream().mapToInt(cell -> cell.feet().getZ()).min().orElse(0);
        }

        int maxX() {
            return cells.stream().mapToInt(cell -> cell.feet().getX()).max().orElse(0);
        }

        int maxZ() {
            return cells.stream().mapToInt(cell -> cell.feet().getZ()).max().orElse(0);
        }

        boolean containsColumn(int x, int z) {
            return cells.stream().anyMatch(cell -> cell.feet().getX() == x && cell.feet().getZ() == z);
        }

        Set<BlockPos> projectedCells(int anchorY) {
            return cells.stream().map(cell -> new BlockPos(
                            cell.feet().getX(), anchorY, cell.feet().getZ()))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }
}
