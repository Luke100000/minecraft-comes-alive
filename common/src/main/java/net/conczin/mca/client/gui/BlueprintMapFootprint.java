package net.conczin.mca.client.gui;

import net.conczin.mca.server.world.data.BuildingFloorRegion;

import java.util.*;

final class BlueprintMapFootprint {
    private BlueprintMapFootprint() {
    }

    static Set<Cell> rectangle(int minX, int minZ, int maxX, int maxZ) {
        LinkedHashSet<Cell> cells = new LinkedHashSet<>();
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                cells.add(new Cell(x, z));
            }
        }
        return cells;
    }

    static Set<Cell> fromFloorRegions(Collection<BuildingFloorRegion> regions) {
        LinkedHashSet<Cell> cells = new LinkedHashSet<>();
        for (BuildingFloorRegion region : regions) {
            for (BuildingFloorRegion.Component component : region.components()) {
                cells.addAll(fromComponent(component));
            }
        }
        return cells;
    }

    static Set<Cell> fromComponent(BuildingFloorRegion.Component component) {
        if (component.spans().isEmpty()) {
            return rectangle(component.minX(), component.minZ(), component.maxX(), component.maxZ());
        }

        LinkedHashSet<Cell> cells = new LinkedHashSet<>();
        for (BuildingFloorRegion.Span span : component.spans()) {
            for (int x = span.minX(); x <= span.maxX(); x++) {
                cells.add(new Cell(x, span.z()));
            }
        }
        return cells;
    }

    /**
     * Expands an exact footprint by a fixed horizontal width without converting it
     * to a min/max rectangle. L/U-shaped structures therefore keep their topology.
     */
    static Set<Cell> expand(Set<Cell> cells, int width) {
        if (cells.isEmpty() || width <= 0) {
            return Set.copyOf(cells);
        }

        LinkedHashSet<Cell> expanded = new LinkedHashSet<>();
        for (Cell cell : cells) {
            for (int dz = -width; dz <= width; dz++) {
                for (int dx = -width; dx <= width; dx++) {
                    expanded.add(new Cell(cell.x() + dx, cell.z() + dz));
                }
            }
        }
        return Set.copyOf(expanded);
    }

    /** One canonical cell -> fill spans -> boundary-edge pipeline for every Blueprint map region. */
    static Shape shape(Set<Cell> cells) {
        Set<Cell> canonical = Set.copyOf(cells);
        return new Shape(canonical, rowSpans(canonical), outerEdges(canonical));
    }

    static List<RowSpan> rowSpans(Set<Cell> cells) {
        if (cells.isEmpty()) {
            return List.of();
        }

        Map<Integer, List<Integer>> xsByZ = new TreeMap<>();
        for (Cell cell : cells) {
            xsByZ.computeIfAbsent(cell.z(), ignored -> new ArrayList<>()).add(cell.x());
        }

        List<RowSpan> spans = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : xsByZ.entrySet()) {
            List<Integer> xs = entry.getValue().stream().sorted().toList();
            int start = xs.getFirst();
            int previous = start;
            for (int index = 1; index < xs.size(); index++) {
                int x = xs.get(index);
                if (x != previous + 1) {
                    spans.add(new RowSpan(entry.getKey(), start, previous));
                    start = x;
                }
                previous = x;
            }
            spans.add(new RowSpan(entry.getKey(), start, previous));
        }
        return List.copyOf(spans);
    }

    static List<Edge> outerEdges(Set<Cell> cells) {
        if (cells.isEmpty()) {
            return List.of();
        }

        // Store unit boundary segments, then merge adjacent collinear segments.
        // This preserves holes and concave L/U topology while drastically reducing the
        // number of per-frame GUI fills and hover-edge checks.
        Map<Integer, List<Integer>> horizontalByZ = new TreeMap<>();
        Map<Integer, List<Integer>> verticalByX = new TreeMap<>();

        for (Cell cell : cells) {
            int x = cell.x();
            int z = cell.z();
            if (!cells.contains(new Cell(x, z - 1))) {
                horizontalByZ.computeIfAbsent(z, ignored -> new ArrayList<>()).add(x);
            }
            if (!cells.contains(new Cell(x, z + 1))) {
                horizontalByZ.computeIfAbsent(z + 1, ignored -> new ArrayList<>()).add(x);
            }
            if (!cells.contains(new Cell(x - 1, z))) {
                verticalByX.computeIfAbsent(x, ignored -> new ArrayList<>()).add(z);
            }
            if (!cells.contains(new Cell(x + 1, z))) {
                verticalByX.computeIfAbsent(x + 1, ignored -> new ArrayList<>()).add(z);
            }
        }

        List<Edge> edges = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : horizontalByZ.entrySet()) {
            addMergedHorizontalEdges(edges, entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Integer, List<Integer>> entry : verticalByX.entrySet()) {
            addMergedVerticalEdges(edges, entry.getKey(), entry.getValue());
        }
        return List.copyOf(edges);
    }

    private static void addMergedHorizontalEdges(List<Edge> edges, int z, List<Integer> positions) {
        List<Integer> xs = positions.stream().distinct().sorted().toList();
        int start = xs.getFirst();
        int previous = start;
        for (int index = 1; index < xs.size(); index++) {
            int x = xs.get(index);
            if (x != previous + 1) {
                edges.add(new Edge(start, z, previous + 1, z));
                start = x;
            }
            previous = x;
        }
        edges.add(new Edge(start, z, previous + 1, z));
    }

    private static void addMergedVerticalEdges(List<Edge> edges, int x, List<Integer> positions) {
        List<Integer> zs = positions.stream().distinct().sorted().toList();
        int start = zs.getFirst();
        int previous = start;
        for (int index = 1; index < zs.size(); index++) {
            int z = zs.get(index);
            if (z != previous + 1) {
                edges.add(new Edge(x, start, x, previous + 1));
                start = z;
            }
            previous = z;
        }
        edges.add(new Edge(x, start, x, previous + 1));
    }

    record Shape(Set<Cell> cells, List<RowSpan> spans, List<Edge> edges) {
        Shape {
            cells = Set.copyOf(cells);
            spans = List.copyOf(spans);
            edges = List.copyOf(edges);
        }
    }

    record Cell(int x, int z) {
    }

    record RowSpan(int z, int minX, int maxX) {
    }

    record Edge(int x0, int z0, int x1, int z1) {
    }
}
