package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.*;
import java.util.stream.Collectors;

/** Compact derived X/Z projection for one semantic Floor band. */
public record BuildingFloorRegion(int anchorY, List<Component> components) {
    public BuildingFloorRegion {
        components = List.copyOf(components);
    }

    public int area() {
        return components.stream().mapToInt(Component::area).sum();
    }

    public boolean containsHorizontally(int x, int z) {
        return components.stream().anyMatch(component -> component.containsHorizontally(x, z));
    }

    public Set<BlockPos> cells() {
        return components.stream()
                .flatMap(component -> component.cells(anchorY).stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    static BuildingFloorRegion fromFootprint(int anchorY, Collection<BlockPos> footprintCells) {
        if (footprintCells == null || footprintCells.isEmpty()) {
            return new BuildingFloorRegion(anchorY, List.of());
        }
        Set<Cell> cells = footprintCells.stream()
                .map(pos -> new Cell(pos.getX(), pos.getZ()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Component> components = splitComponents(cells);
        return new BuildingFloorRegion(anchorY, components);
    }

    private static List<Component> splitComponents(Set<Cell> cells) {
        Set<Cell> remaining = new HashSet<>(cells);
        List<Component> components = new ArrayList<>();
        while (!remaining.isEmpty()) {
            Cell seed = remaining.stream().min(Comparator.comparingInt(Cell::x).thenComparingInt(Cell::z)).orElseThrow();
            remaining.remove(seed);
            ArrayDeque<Cell> queue = new ArrayDeque<>();
            List<Cell> connected = new ArrayList<>();
            queue.add(seed);
            while (!queue.isEmpty()) {
                Cell current = queue.removeFirst();
                connected.add(current);
                for (Cell next : List.of(
                        new Cell(current.x() + 1, current.z()), new Cell(current.x() - 1, current.z()),
                        new Cell(current.x(), current.z() + 1), new Cell(current.x(), current.z() - 1))) {
                    if (remaining.remove(next)) queue.addLast(next);
                }
            }
            components.add(componentFromCells(connected));
        }
        components.sort(Comparator.comparingInt(Component::minX).thenComparingInt(Component::minZ)
                .thenComparingInt(Component::maxX).thenComparingInt(Component::maxZ));
        return List.copyOf(components);
    }

    private static Component componentFromCells(Collection<Cell> cells) {
        Map<Integer, TreeSet<Integer>> xsByZ = new TreeMap<>();
        for (Cell cell : cells) xsByZ.computeIfAbsent(cell.z(), ignored -> new TreeSet<>()).add(cell.x());
        List<Span> spans = new ArrayList<>();
        for (Map.Entry<Integer, TreeSet<Integer>> entry : xsByZ.entrySet()) {
            int z = entry.getKey();
            Iterator<Integer> xs = entry.getValue().iterator();
            if (!xs.hasNext()) continue;
            int start = xs.next(), previous = start;
            while (xs.hasNext()) {
                int x = xs.next();
                if (x != previous + 1) {
                    spans.add(new Span(z, start, previous));
                    start = x;
                }
                previous = x;
            }
            spans.add(new Span(z, start, previous));
        }
        return new Component(spans);
    }

    private record Cell(int x, int z) {
    }

    public record Component(List<Span> spans) {
        public Component {
            spans = spans == null ? List.of() : spans.stream()
                    .sorted(Comparator.comparingInt(Span::z).thenComparingInt(Span::minX))
                    .toList();
        }

        public int minX() {
            return spans.stream().mapToInt(Span::minX).min().orElse(0);
        }

        public int minZ() {
            return spans.stream().mapToInt(Span::z).min().orElse(0);
        }

        public int maxX() {
            return spans.stream().mapToInt(Span::maxX).max().orElse(0);
        }

        public int maxZ() {
            return spans.stream().mapToInt(Span::z).max().orElse(0);
        }

        public int area() {
            return spans.stream().mapToInt(span -> span.maxX() - span.minX() + 1).sum();
        }

        public boolean containsHorizontally(int x, int z) {
            return spans.stream().anyMatch(span -> span.containsHorizontally(x, z));
        }

        Set<BlockPos> cells(int anchorY) {
            LinkedHashSet<BlockPos> cells = new LinkedHashSet<>();
            for (Span span : spans) {
                for (int x = span.minX(); x <= span.maxX(); x++) cells.add(new BlockPos(x, anchorY, span.z()));
            }
            return Set.copyOf(cells);
        }

    }

    public record Span(int z, int minX, int maxX) {
        public boolean containsHorizontally(int x, int z) {
            return this.z == z && x >= minX && x <= maxX;
        }
    }
}
