package net.conczin.mca.server.world.data;

import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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

    /** Flat compatibility constructor used by fixtures and legacy migration only. */
    public StructureFloor(int id, int anchorY, int ceilingY, int floorNumber,
                          BuildingFloorRegion region) {
        this(id, floorNumber, geometryFromRegion(region, ceilingY, List.of()));
    }

    /** Flat compatibility constructor used by fixtures and legacy migration only. */
    public StructureFloor(int id, int anchorY, int ceilingY, int floorNumber,
                          BuildingFloorRegion region, List<ConnectorMarker> connectors) {
        this(id, floorNumber, geometryFromRegion(region, ceilingY, connectors));
    }

    public StructureFloor(int id, int anchorY, int ceilingY, BuildingFloorRegion region) {
        this(id, anchorY, ceilingY, 0, region);
    }

    public int anchorY() {
        return geometry.anchorY();
    }

    /** Derived projection only; never authoritative physical topology. */
    public BuildingFloorRegion region() {
        return geometry.projection();
    }

    public int area() {
        return region().area();
    }

    public boolean contains(int x, int z) {
        return !geometry.cellsAtColumn(x, z).isEmpty();
    }

    boolean containsPhysicalPosition(int x, int y, int z) {
        return geometry.physicalCellAt(x, y, z).isPresent();
    }

    boolean containsInteractionPosition(int x, int y, int z) {
        return geometry.interactionCellAt(x, y, z).isPresent();
    }

    boolean sameSemanticBand(StructureFloor other) {
        return other != null && sameSemanticBand(anchorY(), other.anchorY());
    }

    static boolean sameSemanticBand(int firstAnchorY, int secondAnchorY) {
        return Math.abs(firstAnchorY - secondAnchorY) <= BAND_TOLERANCE;
    }

    boolean overlapsFootprint(StructureFloor other) {
        return other != null && region().intersectionArea(other.region()) > 0;
    }

    boolean overlapsSameSemanticBand(StructureFloor other) {
        return sameSemanticBand(other) && overlapsFootprint(other);
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

    public List<ConnectorMarker> connectors() {
        return geometry.connectorMarkers();
    }

    int maxPhysicalCeilingY() {
        return geometry.maxPhysicalCeilingY();
    }

    /** Transitional physical accessor; semantic ceilings are Structure-level. */
    public int ceilingY() {
        return maxPhysicalCeilingY();
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
            tag.put("connectors", NbtHelper.fromList(connectors(), ConnectorMarker::save));
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

    StructureFloor withGeometry(FloorGeometry newGeometry) {
        return new StructureFloor(id, floorNumber, newGeometry);
    }

    public StructureFloor withFloorNumber(int newFloorNumber) {
        return new StructureFloor(id, newFloorNumber, geometry);
    }

    private static FloorGeometry geometryFromRegion(BuildingFloorRegion region,
                                                    int ceilingY,
                                                    Collection<ConnectorMarker> markers) {
        Objects.requireNonNull(region, "region");
        if (region.area() == 0) {
            throw new IllegalArgumentException("StructureFloor requires non-empty region geometry");
        }
        LinkedHashMap<BlockPos, FloorGeometry.Cell> cells = new LinkedHashMap<>();
        for (BlockPos pos : region.cells()) {
            cells.put(pos, new FloorGeometry.Cell(pos, pos.getY(), ceilingY));
        }
        Map<BlockPos, ConnectorType> connectors = new LinkedHashMap<>();
        if (markers != null) {
            for (ConnectorMarker marker : markers) {
                if (cells.containsKey(marker.pos())) connectors.put(marker.pos(), marker.type());
            }
        }
        return new FloorGeometry(cells.values(), connectors);
    }

    private static CompoundTag saveCell(FloorGeometry.Cell cell) {
        CompoundTag tag = new CompoundTag();
        tag.put("pos", NbtHelper.encodeBlockPos(cell.feet()));
        tag.putDouble("surfaceY", cell.surfaceY());
        tag.putInt("ceilingY", cell.ceilingY());
        return tag;
    }

    private static FloorGeometry.Cell loadCell(CompoundTag tag) {
        BlockPos pos = NbtHelper.decodeBlockPos(tag.get("pos"));
        if (pos == null) throw new IllegalArgumentException("FloorGeometry cell is missing pos");
        return new FloorGeometry.Cell(pos, tag.getDouble("surfaceY"), tag.getInt("ceilingY"));
    }

    private static Map<BlockPos, ConnectorType> connectorMap(CompoundTag tag,
                                                             Collection<FloorGeometry.Cell> cells) {
        Set<BlockPos> positions = cells.stream().map(FloorGeometry.Cell::feet)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Map<BlockPos, ConnectorType> result = new LinkedHashMap<>();
        for (ConnectorMarker marker : loadMarkers(tag)) {
            if (positions.contains(marker.pos())) result.put(marker.pos(), marker.type());
        }
        return Map.copyOf(result);
    }

    private static List<ConnectorMarker> loadMarkers(CompoundTag tag) {
        if (!tag.contains("connectors", Tag.TAG_LIST)) return List.of();
        List<ConnectorMarker> result = new ArrayList<>();
        for (ConnectorMarker marker : NbtHelper.toList(tag.getList("connectors", Tag.TAG_COMPOUND),
                value -> ConnectorMarker.load((CompoundTag) value))) {
            if (marker != null) result.add(marker);
        }
        return List.copyOf(result);
    }

    public record ConnectorMarker(BlockPos pos, ConnectorType type) {
        public ConnectorMarker {
            pos = pos.immutable();
            Objects.requireNonNull(type, "type");
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.put("pos", NbtHelper.encodeBlockPos(pos));
            tag.putString("type", type.serializedName());
            return tag;
        }

        static ConnectorMarker load(CompoundTag tag) {
            if (!tag.contains("pos") || !tag.contains("type")) return null;
            BlockPos pos = NbtHelper.decodeBlockPos(tag.get("pos"));
            ConnectorType type = ConnectorType.fromSerializedName(tag.getString("type"));
            return pos == null || type == null ? null : new ConnectorMarker(pos, type);
        }
    }

    public enum ConnectorType {
        LADDER("ladder"),
        TRAPDOOR("trapdoor"),
        DOOR("door"),
        GATE("gate");

        private final String serializedName;

        ConnectorType(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }

        public boolean vertical() {
            return this == LADDER || this == TRAPDOOR;
        }

        public boolean roomBoundary() {
            return this == DOOR || this == GATE;
        }

        static ConnectorType fromSerializedName(String name) {
            for (ConnectorType type : values()) {
                if (type.serializedName.equals(name)) return type;
            }
            return null;
        }
    }
}
