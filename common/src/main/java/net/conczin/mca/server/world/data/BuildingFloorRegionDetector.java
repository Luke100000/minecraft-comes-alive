package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.*;

/** Groups reachable horizontal Floor anchors into storeys. Heights within two blocks stay in the
 * current storey; the next higher band becomes the next Floor and therefore the previous Floor's upper bound. */
final class BuildingFloorRegionDetector {
    static final int FLOOR_CLUSTER_TOLERANCE = 2;
    private static final int MIN_FLOOR_AREA = 4;

    private BuildingFloorRegionDetector() {
    }

    static List<BuildingFloorRegion> detect(Collection<FloorCell> floorCells) {
        if (floorCells == null || floorCells.isEmpty()) return List.of();

        Map<Integer, Set<BlockPos>> byY = new TreeMap<>();
        for (FloorCell cell : new LinkedHashSet<>(floorCells)) {
            byY.computeIfAbsent(cell.y(), ignored -> new LinkedHashSet<>())
                    .add(new BlockPos(cell.x(), cell.y(), cell.z()));
        }

        List<HeightSlice> meaningfulSlices = new ArrayList<>();
        for (Map.Entry<Integer, Set<BlockPos>> entry : byY.entrySet()) {
            HeightSlice slice = new HeightSlice(entry.getKey(), Set.copyOf(entry.getValue()));
            BuildingFloorRegion region = BuildingFloorRegion.fromFootprint(slice.y(), slice.cells());
            if (region.components().stream().anyMatch(component -> component.area() >= MIN_FLOOR_AREA)) {
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
        return bands.stream().map(MutableBand::freeze).toList();
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
