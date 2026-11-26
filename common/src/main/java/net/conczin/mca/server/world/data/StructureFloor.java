package net.conczin.mca.server.world.data;

import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Registered Floor identity and storey reference around exact physical geometry.
 * The scanner chooses anchorY from ordinary surface evidence; stairs and later
 * room expansion must not move it. Cell membership is owned exclusively by geometry.
 */
public record StructureFloor(int id, int floorNumber, int anchorY, FloorGeometry geometry) {
    // Physical overlap candidates use a local height window, not storey membership.
    static final int ANCHOR_PROXIMITY = 2;

    public StructureFloor(int id, int floorNumber, FloorGeometry geometry) {
        this(id, floorNumber, geometry.anchorY(), geometry);
    }

    public StructureFloor {
        geometry = Objects.requireNonNull(geometry, "geometry");
        if (geometry.cells().isEmpty()) {
            throw new IllegalArgumentException("StructureFloor requires non-empty geometry");
        }
    }

    /** Derived projection only; never authoritative physical topology. */
    public int area() {
        return geometry.footprintArea();
    }

    public boolean contains(int x, int z) {
        return !geometry.cellsAtColumn(x, z).isEmpty();
    }

    boolean hasNearbyAnchor(StructureFloor other) {
        return other != null && hasNearbyAnchor(anchorY(), other.anchorY());
    }

    static boolean hasNearbyAnchor(int firstAnchorY, int secondAnchorY) {
        return Math.abs((long) firstAnchorY - secondAnchorY) <= ANCHOR_PROXIMITY;
    }

    boolean overlapsFootprint(StructureFloor other) {
        return other != null && geometry.footprintIntersectionArea(other.geometry) > 0;
    }

    boolean overlapsNearbyFloorBand(StructureFloor other) {
        return other != null
                && hasNearbyAnchor(other)
                && geometry.footprintIntersectionArea(other.geometry) > 0;
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
        if (hasNearbyAnchor(other)) return -1;
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
        tag.putInt("floorNumber", floorNumber);
        tag.putInt("anchorY", anchorY);
        List<FloorGeometry.Cell> sortedCells = geometry.cells().stream()
                .sorted(Comparator
                        .comparingInt((FloorGeometry.Cell cell) -> cell.feet().getX())
                        .thenComparingInt(cell -> cell.feet().getZ())
                        .thenComparingInt(cell -> cell.feet().getY()))
                .toList();
        long[] cellPositions = new long[sortedCells.size()];
        int[] ceilingYs = new int[sortedCells.size()];
        for (int i = 0; i < sortedCells.size(); i++) {
            FloorGeometry.Cell cell = sortedCells.get(i);
            cellPositions[i] = cell.feet().asLong();
            ceilingYs[i] = cell.ceilingY();
        }
        tag.putLongArray("cellPositions", cellPositions);
        tag.putIntArray("ceilingYs", ceilingYs);
        if (!connectors().isEmpty()) {
            tag.put("connectors", NbtHelper.fromList(connectors(), FloorConnector.Marker::save));
        }
        return tag;
    }

    public static StructureFloor load(CompoundTag tag) {
        List<FloorGeometry.Cell> cells;
        if (tag.getLongArray("cellPositions").isPresent()) {
            if (tag.getIntArray("ceilingYs").isEmpty()) {
                throw new IllegalArgumentException("StructureFloor is missing ceilingYs");
            }
            long[] cellPositions = tag.getLongArray("cellPositions").orElseThrow();
            int[] ceilingYs = tag.getIntArray("ceilingYs").orElseThrow();
            if (cellPositions.length == 0 || cellPositions.length != ceilingYs.length) {
                throw new IllegalArgumentException("StructureFloor has invalid packed geometry");
            }
            cells = new ArrayList<>(cellPositions.length);
            for (int i = 0; i < cellPositions.length; i++) {
                cells.add(new FloorGeometry.Cell(BlockPos.of(cellPositions[i]), ceilingYs[i]));
            }
        } else if (tag.getList("cells").isPresent()) {
            cells = NbtHelper.toList(
                    tag.getList("cells").orElseThrow(), value -> loadCell((CompoundTag) value));
        } else {
            throw new IllegalArgumentException("StructureFloor is missing required geometry");
        }

        int floorNumber = tag.getInt("floorNumber").orElse(0);
        List<FloorConnector.Marker> rawMarkers = loadMarkers(tag);
        List<FloorConnector.Marker> validMarkers;
        if (rawMarkers.isEmpty()) {
            validMarkers = List.of();
        } else {
            Set<BlockPos> validFeet = new HashSet<>(cells.size());
            for (FloorGeometry.Cell cell : cells) {
                validFeet.add(cell.feet());
            }
            validMarkers = rawMarkers.stream()
                    .filter(marker -> validFeet.contains(marker.floorCell()))
                    .toList();
        }
        FloorGeometry geometry = new FloorGeometry(cells, validMarkers);
        // Older saves derived this height from geometry; preserve that interpretation.
        int anchorY = tag.getInt("anchorY").orElse(geometry.anchorY());
        return new StructureFloor(tag.getInt("id").orElse(0), floorNumber, anchorY, geometry);
    }

    public StructureFloor withFloorNumber(int newFloorNumber) {
        return new StructureFloor(id, newFloorNumber, anchorY, geometry);
    }

    private static FloorGeometry.Cell loadCell(CompoundTag tag) {
        BlockPos pos = NbtHelper.decodeBlockPos(tag.get("pos"));
        if (pos == null) throw new IllegalArgumentException("FloorGeometry cell is missing pos");
        if (tag.getInt("ceilingY").isEmpty()) {
            throw new IllegalArgumentException("FloorGeometry cell is missing ceilingY");
        }
        return new FloorGeometry.Cell(pos, tag.getInt("ceilingY").orElse(pos.getY() + 1));
    }

    private static List<FloorConnector.Marker> loadMarkers(CompoundTag tag) {
        if (tag.getList("connectors").isEmpty()) return List.of();
        List<FloorConnector.Marker> result = new ArrayList<>();
        for (FloorConnector.Marker marker : NbtHelper.toList(tag.getList("connectors").orElseGet(net.minecraft.nbt.ListTag::new),
                value -> FloorConnector.Marker.load((CompoundTag) value))) {
            if (marker != null) result.add(marker);
        }
        return List.copyOf(result);
    }
}
