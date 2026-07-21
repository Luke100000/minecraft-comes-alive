package net.conczin.mca.server.world.data;

import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;

import java.util.*;

/** Clusters canonical Floor cells into nearby vertical storey bands. */
final class BuildingFloorRegionDetector {
    static final int FLOOR_CLUSTER_TOLERANCE = 2;
    private static final int MIN_MEANINGFUL_AREA = 8;
    private static final double MIN_RELATIVE_AREA = 0.12D;
    private static final int MIN_LONG_COMPONENT_AREA = 8;

    private BuildingFloorRegionDetector() {
    }

    static List<BuildingFloorRegion> detect(Collection<FloorCell> floorCells) {
        if (floorCells == null || floorCells.isEmpty()) return List.of();

        Map<Integer, Set<BlockPos>> byY = new TreeMap<>();
        for (FloorCell cell : new LinkedHashSet<>(floorCells)) {
            byY.computeIfAbsent(cell.y(), ignored -> new LinkedHashSet<>())
                    .add(new BlockPos(cell.x(), cell.y(), cell.z()));
        }

        int largestSliceArea = byY.values().stream().mapToInt(Set::size).max().orElse(0);
        int minimumArea = Math.max(MIN_MEANINGFUL_AREA,
                (int) Math.ceil(largestSliceArea * MIN_RELATIVE_AREA));

        List<HeightSlice> meaningfulSlices = new ArrayList<>();
        for (Map.Entry<Integer, Set<BlockPos>> entry : byY.entrySet()) {
            HeightSlice slice = new HeightSlice(entry.getKey(), Set.copyOf(entry.getValue()));
            BuildingFloorRegion region = BuildingFloorRegion.fromFootprint(slice.y(), slice.cells());
            if (slice.area() >= minimumArea
                    && region.components().stream().anyMatch(BuildingFloorRegionDetector::isUsableComponent)) {
                meaningfulSlices.add(slice);
            }
        }

        List<MutableBand> bands = new ArrayList<>();
        for (HeightSlice slice : meaningfulSlices) {
            MutableBand band = bands.isEmpty() ? null : bands.getLast();
            if (band == null || slice.y() - band.minY > FLOOR_CLUSTER_TOLERANCE) {
                band = new MutableBand(slice.y());
                bands.add(band);
            }
            band.slices.add(slice);
        }
        List<BuildingFloorRegion> regions = bands.stream().map(MutableBand::freeze).toList();
        MCA.LOGGER.info("[FloorDebug][Slices] raw={} largestSlice={} minimumArea={} meaningful={} regions={}",
                byY.entrySet().stream().map(entry -> entry.getKey() + ":" + entry.getValue().size()).toList(),
                largestSliceArea, minimumArea,
                meaningfulSlices.stream().map(slice -> slice.y() + ":" + slice.area()).toList(),
                regions.stream().map(region -> region.anchorY() + ":" + region.area()).toList());
        return regions;
    }

    private static boolean isUsableComponent(BuildingFloorRegion.Component component) {
        int width = component.maxX() - component.minX() + 1;
        int depth = component.maxZ() - component.minZ() + 1;
        return (width >= 2 && depth >= 2) || component.area() >= MIN_LONG_COMPONENT_AREA;
    }

    record FloorCell(int x, int y, int z) {
    }

    private record HeightSlice(int y, Set<BlockPos> cells) {
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

        private BuildingFloorRegion freeze() {
            HeightSlice anchor = slices.stream()
                    .max(Comparator.comparingInt(HeightSlice::area)
                            .thenComparing(Comparator.comparingInt(HeightSlice::y).reversed()))
                    .orElseThrow();
            LinkedHashSet<BlockPos> projected = new LinkedHashSet<>();
            for (HeightSlice slice : slices) {
                for (BlockPos cell : slice.cells()) {
                    projected.add(new BlockPos(cell.getX(), anchor.y(), cell.getZ()));
                }
            }
            return BuildingFloorRegion.fromFootprint(anchor.y(), projected);
        }
    }
}
