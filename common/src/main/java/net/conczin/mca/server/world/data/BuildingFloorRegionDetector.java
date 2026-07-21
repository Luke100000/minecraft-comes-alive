package net.conczin.mca.server.world.data;

import java.util.*;

/**
 * Pure geometry classifier that turns canonical reachable Floor cells into
 * meaningful horizontal floor regions. Minecraft-specific membership detection stays
 * in {@link StructureScanner}; this class only reasons about cells.
 */
final class BuildingFloorRegionDetector {
    static final int FLOOR_CLUSTER_TOLERANCE = 2;

    private static final int MIN_MEANINGFUL_AREA = 8;
    private static final double MIN_RELATIVE_AREA = 0.12D;
    private static final int MIN_LONG_COMPONENT_AREA = 8;

    private BuildingFloorRegionDetector() {
    }

    static List<BuildingFloorRegion> detect(Collection<FloorCell> floorCells) {
        if (floorCells == null || floorCells.isEmpty()) {
            return List.of();
        }

        Map<Integer, Set<HorizontalCell>> byY = new TreeMap<>();
        for (FloorCell cell : new LinkedHashSet<>(floorCells)) {
            byY.computeIfAbsent(cell.y(), ignored -> new HashSet<>())
                    .add(new HorizontalCell(cell.x(), cell.z()));
        }

        List<HeightSlice> slices = byY.entrySet().stream()
                .map(entry -> new HeightSlice(entry.getKey(), Set.copyOf(entry.getValue())))
                .toList();

        // Nearby support heights belong to one physical storey. Cluster first, then decide
        // whether the projected X/Z footprint is meaningful. Filtering individual Y slices first
        // drops small rooms whenever furniture, slabs, or raised blocks split one storey across
        // several support heights (for example 6 cells at Y and 5 at Y+1 for one 3x3 room).
        List<MutableBand> bands = new ArrayList<>();
        for (HeightSlice slice : slices) {
            MutableBand band = bands.isEmpty() ? null : bands.getLast();
            if (band == null || slice.y() - band.minY > FLOOR_CLUSTER_TOLERANCE) {
                band = new MutableBand(slice.y());
                bands.add(band);
            }
            band.add(slice);
        }

        int largestBandArea = bands.stream().mapToInt(MutableBand::projectedArea).max().orElse(0);
        int minimumArea = Math.max(
                MIN_MEANINGFUL_AREA,
                (int) Math.ceil(largestBandArea * MIN_RELATIVE_AREA)
        );

        return bands.stream()
                .filter(band -> band.isMeaningful(minimumArea))
                .map(MutableBand::freeze)
                .toList();
    }

    private static boolean isUsableComponent(BuildingFloorRegion.Component component) {
        int width = component.maxX() - component.minX() + 1;
        int depth = component.maxZ() - component.minZ() + 1;
        return (width >= 2 && depth >= 2) || component.area() >= MIN_LONG_COMPONENT_AREA;
    }

    private static List<BuildingFloorRegion.Component> findComponents(Set<HorizontalCell> cells) {
        Set<HorizontalCell> unvisited = new HashSet<>(cells);
        List<BuildingFloorRegion.Component> components = new ArrayList<>();

        while (!unvisited.isEmpty()) {
            HorizontalCell start = unvisited.iterator().next();
            unvisited.remove(start);

            ArrayDeque<HorizontalCell> queue = new ArrayDeque<>();
            queue.add(start);

            int minX = start.x();
            int minZ = start.z();
            int maxX = start.x();
            int maxZ = start.z();
            List<HorizontalCell> componentCells = new ArrayList<>();

            while (!queue.isEmpty()) {
                HorizontalCell current = queue.removeFirst();
                componentCells.add(current);
                minX = Math.min(minX, current.x());
                minZ = Math.min(minZ, current.z());
                maxX = Math.max(maxX, current.x());
                maxZ = Math.max(maxZ, current.z());

                enqueueIfPresent(unvisited, queue, new HorizontalCell(current.x() + 1, current.z()));
                enqueueIfPresent(unvisited, queue, new HorizontalCell(current.x() - 1, current.z()));
                enqueueIfPresent(unvisited, queue, new HorizontalCell(current.x(), current.z() + 1));
                enqueueIfPresent(unvisited, queue, new HorizontalCell(current.x(), current.z() - 1));
            }

            components.add(new BuildingFloorRegion.Component(
                    minX, minZ, maxX, maxZ, componentCells.size(), buildSpans(componentCells)));
        }

        components.sort(Comparator
                .comparingInt(BuildingFloorRegion.Component::minX)
                .thenComparingInt(BuildingFloorRegion.Component::minZ)
                .thenComparingInt(BuildingFloorRegion.Component::maxX)
                .thenComparingInt(BuildingFloorRegion.Component::maxZ));
        return List.copyOf(components);
    }

    private static List<BuildingFloorRegion.Span> buildSpans(List<HorizontalCell> cells) {
        Map<Integer, List<Integer>> xsByZ = new TreeMap<>();
        for (HorizontalCell cell : cells) {
            xsByZ.computeIfAbsent(cell.z(), ignored -> new ArrayList<>()).add(cell.x());
        }

        List<BuildingFloorRegion.Span> spans = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : xsByZ.entrySet()) {
            List<Integer> xs = entry.getValue().stream().sorted().toList();
            if (xs.isEmpty()) {
                continue;
            }

            int start = xs.getFirst();
            int previous = start;
            for (int i = 1; i < xs.size(); i++) {
                int current = xs.get(i);
                if (current != previous + 1) {
                    spans.add(new BuildingFloorRegion.Span(entry.getKey(), start, previous));
                    start = current;
                }
                previous = current;
            }
            spans.add(new BuildingFloorRegion.Span(entry.getKey(), start, previous));
        }
        return List.copyOf(spans);
    }

    private static void enqueueIfPresent(Set<HorizontalCell> unvisited,
                                         ArrayDeque<HorizontalCell> queue,
                                         HorizontalCell candidate) {
        if (unvisited.remove(candidate)) {
            queue.addLast(candidate);
        }
    }

    record FloorCell(int x, int y, int z) {
    }

    private record HorizontalCell(int x, int z) {
    }

    private record HeightSlice(int y, Set<HorizontalCell> cells) {
        private HeightSlice {
            cells = Set.copyOf(cells);
        }

        private int area() {
            return cells.size();
        }
    }

    private static final class MutableBand {
        private final int minY;
        private final List<HeightSlice> slices = new ArrayList<>();

        private MutableBand(int minY) {
            this.minY = minY;
        }

        private void add(HeightSlice slice) {
            slices.add(slice);
        }

        private Set<HorizontalCell> projectedCells() {
            LinkedHashSet<HorizontalCell> cells = new LinkedHashSet<>();
            slices.forEach(slice -> cells.addAll(slice.cells()));
            return cells;
        }

        private int projectedArea() {
            return projectedCells().size();
        }

        private boolean isMeaningful(int minimumArea) {
            Set<HorizontalCell> cells = projectedCells();
            return cells.size() >= minimumArea
                    && findComponents(cells).stream().anyMatch(BuildingFloorRegionDetector::isUsableComponent);
        }

        private BuildingFloorRegion freeze() {
            HeightSlice anchor = slices.stream()
                    .max(Comparator.comparingInt(HeightSlice::area)
                            .thenComparing(Comparator.comparingInt(HeightSlice::y).reversed()))
                    .orElseThrow();

            Set<HorizontalCell> cells = projectedCells();
            return new BuildingFloorRegion(anchor.y(), cells.size(), findComponents(cells));
        }
    }
}
