package net.conczin.mca.server.world.data;

import java.util.*;

/**
 * Pure geometry classifier that turns supported reachable interior cells into
 * meaningful horizontal floor regions. Minecraft-specific support detection is
 * intentionally kept in {@link Building}; this class only reasons about cells.
 */
final class BuildingFloorRegionDetector {
    static final int FLOOR_CLUSTER_TOLERANCE = 2;

    private static final int MIN_MEANINGFUL_AREA = 8;
    private static final double MIN_RELATIVE_AREA = 0.12D;
    private static final int MIN_LONG_COMPONENT_AREA = 8;

    private BuildingFloorRegionDetector() {
    }

    static List<DetectedRegion> detect(Collection<SupportedCell> supportedCells) {
        if (supportedCells == null || supportedCells.isEmpty()) {
            return List.of();
        }

        Map<Integer, Set<HorizontalCell>> byY = new TreeMap<>();
        for (SupportedCell cell : new LinkedHashSet<>(supportedCells)) {
            byY.computeIfAbsent(cell.y(), ignored -> new HashSet<>())
                    .add(new HorizontalCell(cell.x(), cell.z()));
        }

        int largestSliceArea = byY.values().stream().mapToInt(Set::size).max().orElse(0);
        int minimumArea = Math.max(
                MIN_MEANINGFUL_AREA,
                (int) Math.ceil(largestSliceArea * MIN_RELATIVE_AREA)
        );

        List<HeightSlice> meaningfulSlices = new ArrayList<>();
        for (Map.Entry<Integer, Set<HorizontalCell>> entry : byY.entrySet()) {
            List<DetectedComponent> components = findComponents(entry.getValue());
            int area = entry.getValue().size();
            if (area >= minimumArea && components.stream().anyMatch(BuildingFloorRegionDetector::isUsableComponent)) {
                meaningfulSlices.add(new HeightSlice(entry.getKey(), area, components));
            }
        }

        List<MutableBand> bands = new ArrayList<>();
        for (HeightSlice slice : meaningfulSlices) {
            MutableBand band = bands.isEmpty() ? null : bands.getLast();
            if (band == null || slice.y() - band.minY > FLOOR_CLUSTER_TOLERANCE) {
                band = new MutableBand(slice.y());
                bands.add(band);
            }
            band.add(slice);
        }

        return bands.stream().map(MutableBand::freeze).toList();
    }

    private static boolean isUsableComponent(DetectedComponent component) {
        int width = component.maxX() - component.minX() + 1;
        int depth = component.maxZ() - component.minZ() + 1;
        return (width >= 2 && depth >= 2) || component.area() >= MIN_LONG_COMPONENT_AREA;
    }

    private static List<DetectedComponent> findComponents(Set<HorizontalCell> cells) {
        Set<HorizontalCell> unvisited = new HashSet<>(cells);
        List<DetectedComponent> components = new ArrayList<>();

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

            components.add(new DetectedComponent(
                    minX, minZ, maxX, maxZ, componentCells.size(), buildSpans(componentCells)));
        }

        components.sort(Comparator
                .comparingInt(DetectedComponent::minX)
                .thenComparingInt(DetectedComponent::minZ)
                .thenComparingInt(DetectedComponent::maxX)
                .thenComparingInt(DetectedComponent::maxZ));
        return List.copyOf(components);
    }
    private static List<DetectedSpan> buildSpans(List<HorizontalCell> cells) {
        Map<Integer, List<Integer>> xsByZ = new TreeMap<>();
        for (HorizontalCell cell : cells) {
            xsByZ.computeIfAbsent(cell.z(), ignored -> new ArrayList<>()).add(cell.x());
        }

        List<DetectedSpan> spans = new ArrayList<>();
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
                    spans.add(new DetectedSpan(entry.getKey(), start, previous));
                    start = current;
                }
                previous = current;
            }
            spans.add(new DetectedSpan(entry.getKey(), start, previous));
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

    record SupportedCell(int x, int y, int z) {
    }

    record DetectedComponent(int minX,
                             int minZ,
                             int maxX,
                             int maxZ,
                             int area,
                             List<DetectedSpan> spans) {
        DetectedComponent {
            spans = List.copyOf(spans);
        }
    }

    record DetectedSpan(int z, int minX, int maxX) {
    }

    record DetectedRegion(int anchorY, int area, List<DetectedComponent> components) {
        DetectedRegion {
            components = List.copyOf(components);
        }
    }

    private record HorizontalCell(int x, int z) {
    }

    private record HeightSlice(int y, int area, List<DetectedComponent> components) {
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

        private DetectedRegion freeze() {
            HeightSlice anchor = slices.stream()
                    .max(Comparator.comparingInt(HeightSlice::area)
                            .thenComparing(Comparator.comparingInt(HeightSlice::y).reversed()))
                    .orElseThrow();

            int area = slices.stream().mapToInt(HeightSlice::area).sum();
            List<DetectedComponent> components = slices.stream()
                    .flatMap(slice -> slice.components().stream())
                    .sorted(Comparator
                            .comparingInt(DetectedComponent::minX)
                            .thenComparingInt(DetectedComponent::minZ)
                            .thenComparingInt(DetectedComponent::maxX)
                            .thenComparingInt(DetectedComponent::maxZ))
                    .toList();

            return new DetectedRegion(anchor.y(), area, components);
        }
    }
}
