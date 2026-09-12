package net.conczin.mca.server.world.data;

import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Stable persisted identity for one semantic storey around exact 3D geometry. */
public record StructureFloor(int id, int floorNumber, FloorGeometry geometry) {
    static final int BAND_TOLERANCE = 2;

    public StructureFloor {
        geometry = Objects.requireNonNull(geometry, "geometry");
        if (geometry.cells().isEmpty()) {
            throw new IllegalArgumentException("StructureFloor requires non-empty geometry");
        }
    }

    public int anchorY() {
        return geometry.anchorY();
    }

    /** Derived projection only; never authoritative physical topology. */
    public BuildingFloorRegion region() {
        return geometry.projection();
    }

    public int area() {
        return geometry.footprintArea();
    }

    public boolean contains(int x, int z) {
        return !geometry.cellsAtColumn(x, z).isEmpty();
    }

    boolean sameSemanticBand(StructureFloor other) {
        return other != null && sameSemanticBand(anchorY(), other.anchorY());
    }

    static boolean sameSemanticBand(int firstAnchorY, int secondAnchorY) {
        return Math.abs(firstAnchorY - secondAnchorY) <= BAND_TOLERANCE;
    }

    static Map<StructureFloor, Integer> floorNumbers(Collection<StructureFloor> floors,
                                                     StructureFloor groundFloor) {
        if (floors == null || groundFloor == null) return Map.of();
        List<StructureFloor> ordered = floors.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(StructureFloor::anchorY)
                        .thenComparingInt(StructureFloor::id))
                .toList();
        if (ordered.isEmpty()) return Map.of();

        List<List<StructureFloor>> bands = new ArrayList<>();
        for (StructureFloor floor : ordered) {
            List<StructureFloor> band = bands.isEmpty() ? null : bands.getLast();
            if (band == null || floor.anchorY() - band.getFirst().anchorY() > BAND_TOLERANCE) {
                band = new ArrayList<>();
                bands.add(band);
            }
            band.add(floor);
        }

        int groundBand = -1;
        for (int index = 0; index < bands.size() && groundBand < 0; index++) {
            if (bands.get(index).stream().anyMatch(groundFloor::equals)) groundBand = index;
        }
        if (groundBand < 0) return Map.of();

        Map<StructureFloor, Integer> numbers = new HashMap<>();
        for (int bandIndex = 0; bandIndex < bands.size(); bandIndex++) {
            int floorNumber = bandIndex - groundBand;
            for (StructureFloor floor : bands.get(bandIndex)) numbers.put(floor, floorNumber);
        }
        return Map.copyOf(numbers);
    }

    boolean overlapsFootprint(StructureFloor other) {
        return other != null && geometry.footprintIntersectionArea(other.geometry) > 0;
    }

    boolean overlapsSameSemanticBand(StructureFloor other) {
        return other != null && matchesSemanticStorey(other.geometry);
    }

    /** Persisted-Floor identity matching for a freshly observed exact geometry. */
    boolean matchesSemanticStorey(FloorGeometry other) {
        return other != null
                && sameSemanticBand(anchorY(), other.anchorY())
                && geometry.footprintIntersectionArea(other) > 0;
    }

    int verticalGapTo(StructureFloor other) {
        int ownMin = geometry.minFeetY();
        int ownMax = geometry.maxPhysicalCeilingY();
        int otherMin = other.geometry.minFeetY();
        int otherMax = other.geometry.maxPhysicalCeilingY();
        if (ownMax <= otherMin) return otherMin - ownMax;
        if (otherMax <= ownMin) return ownMin - otherMax;
        return -1;
    }

    int attachmentGapTo(StructureFloor other) {
        if (sameSemanticBand(other)) return -1;
        return Math.max(0, verticalGapTo(other));
    }

    public List<FloorConnector.Marker> connectors() {
        return geometry.connectorMarkers();
    }

    int maxPhysicalCeilingY() {
        return geometry.maxPhysicalCeilingY();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.put("cells", NbtHelper.fromList(geometry.cells().stream()
                .sorted(Comparator
                        .comparingInt((FloorGeometry.Cell cell) -> cell.feet().getX())
                        .thenComparingInt(cell -> cell.feet().getZ())
                        .thenComparingInt(cell -> cell.feet().getY()))
                .toList(), StructureFloor::saveCell));
        if (!connectors().isEmpty()) {
            tag.put("connectors", NbtHelper.fromList(connectors(), FloorConnector.Marker::save));
        }
        return tag;
    }

    public static StructureFloor load(CompoundTag tag) {
        if (!tag.contains("cells", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("StructureFloor is missing required geometry");
        }
        List<FloorGeometry.Cell> cells = NbtHelper.toList(
                tag.getList("cells", Tag.TAG_COMPOUND), value -> loadCell((CompoundTag) value));
        return new StructureFloor(tag.getInt("id"), 0,
                new FloorGeometry(cells, connectorMap(tag, cells)));
    }

    public StructureFloor withFloorNumber(int newFloorNumber) {
        return new StructureFloor(id, newFloorNumber, geometry);
    }

    private static CompoundTag saveCell(FloorGeometry.Cell cell) {
        CompoundTag tag = new CompoundTag();
        tag.put("pos", NbtHelper.encodeBlockPos(cell.feet()));
        tag.putInt("ceilingY", cell.ceilingY());
        return tag;
    }

    private static FloorGeometry.Cell loadCell(CompoundTag tag) {
        BlockPos pos = NbtHelper.decodeBlockPos(tag.get("pos"));
        if (pos == null) throw new IllegalArgumentException("FloorGeometry cell is missing pos");
        if (!tag.contains("ceilingY", Tag.TAG_INT)) {
            throw new IllegalArgumentException("FloorGeometry cell is missing ceilingY");
        }
        return new FloorGeometry.Cell(pos, tag.getInt("ceilingY"));
    }

    private static Map<BlockPos, FloorConnector.Type> connectorMap(CompoundTag tag,
                                                                   Collection<FloorGeometry.Cell> cells) {
        Set<BlockPos> positions = cells.stream().map(FloorGeometry.Cell::feet)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Map<BlockPos, FloorConnector.Type> result = new LinkedHashMap<>();
        for (FloorConnector.Marker marker : loadMarkers(tag)) {
            if (positions.contains(marker.pos())) result.put(marker.pos(), marker.type());
        }
        return Map.copyOf(result);
    }

    private static List<FloorConnector.Marker> loadMarkers(CompoundTag tag) {
        if (!tag.contains("connectors", Tag.TAG_LIST)) return List.of();
        List<FloorConnector.Marker> result = new ArrayList<>();
        for (FloorConnector.Marker marker : NbtHelper.toList(tag.getList("connectors", Tag.TAG_COMPOUND),
                value -> FloorConnector.Marker.load((CompoundTag) value))) {
            if (marker != null) result.add(marker);
        }
        return List.copyOf(result);
    }
}
