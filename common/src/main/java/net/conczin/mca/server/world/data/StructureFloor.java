package net.conczin.mca.server.world.data;

import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

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
        if (tag.getList("cells").isEmpty()) {
            throw new IllegalArgumentException("StructureFloor is missing required geometry");
        }
        List<FloorGeometry.Cell> cells = NbtHelper.toList(
                tag.getList("cells").orElseGet(ListTag::new), value -> loadCell((CompoundTag) value));
        int floorNumber = tag.getInt("floorNumber").orElse(0);
        FloorGeometry geometry = new FloorGeometry(cells, loadMarkers(tag).stream()
                .filter(marker -> cells.stream()
                        .anyMatch(cell -> cell.feet().equals(marker.floorCell())))
                .toList());
        // Older saves derived this height from geometry; preserve that interpretation.
        int anchorY = tag.getInt("anchorY").orElse(geometry.anchorY());
        return new StructureFloor(tag.getInt("id").orElse(0), floorNumber, anchorY, geometry);
    }

    public StructureFloor withFloorNumber(int newFloorNumber) {
        return new StructureFloor(id, newFloorNumber, anchorY, geometry);
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
