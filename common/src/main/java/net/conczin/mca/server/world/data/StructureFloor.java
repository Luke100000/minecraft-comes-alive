package net.conczin.mca.server.world.data;

import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Objects;

/** Stable persistent identity for one physical storey in a Structure. */
public record StructureFloor(int id, int anchorY, int ceilingY, int floorNumber,
                             BuildingFloorRegion region,
                             BuildingFloorRegion ceilingBoundaryRegion,
                             List<ConnectorMarker> connectors) {
    static final int BAND_TOLERANCE = 2;

    public StructureFloor {
        region = Objects.requireNonNull(region, "region");
        if (region.area() == 0) {
            throw new IllegalArgumentException("StructureFloor requires non-empty region geometry");
        }
        ceilingBoundaryRegion = ceilingBoundaryRegion == null
                ? BuildingFloorRegion.fromFootprint(ceilingY, List.of())
                : ceilingBoundaryRegion.withAnchorY(ceilingY);
        for (BlockPos cell : ceilingBoundaryRegion.cells()) {
            if (!region.containsHorizontally(cell.getX(), cell.getZ())) {
                throw new IllegalArgumentException("StructureFloor ceiling boundary must be a subset of its region");
            }
        }
        connectors = connectors == null ? List.of() : List.copyOf(connectors);
    }

    public StructureFloor(int id, int anchorY, int ceilingY, int floorNumber, BuildingFloorRegion region) {
        this(id, anchorY, ceilingY, floorNumber, region, null, List.of());
    }

    public StructureFloor(int id, int anchorY, int ceilingY, int floorNumber,
                          BuildingFloorRegion region, List<ConnectorMarker> connectors) {
        this(id, anchorY, ceilingY, floorNumber, region, null, connectors);
    }

    public StructureFloor(int id, int anchorY, int ceilingY, BuildingFloorRegion region) {
        this(id, anchorY, ceilingY, 0, region);
    }

    public boolean contains(int x, int z) {
        return region.containsHorizontally(x, z);
    }

    public int area() {
        return region.area();
    }

    boolean containsPhysicalPosition(int x, int y, int z) {
        if (!contains(x, z)) return false;
        return y >= anchorY && y < ceilingY
                || y == ceilingY && ceilingBoundaryRegion.containsHorizontally(x, z);
    }

    boolean containsInteractionPosition(int x, int y, int z) {
        return contains(x, z) && (y == anchorY - 1 || containsPhysicalPosition(x, y, z));
    }

    boolean sameSemanticBand(StructureFloor other) {
        return other != null && sameSemanticBand(anchorY, other.anchorY);
    }

    static boolean sameSemanticBand(int firstAnchorY, int secondAnchorY) {
        return Math.abs(firstAnchorY - secondAnchorY) <= BAND_TOLERANCE;
    }

    boolean overlapsFootprint(StructureFloor other) {
        return other != null && region.intersectionArea(other.region) > 0;
    }

    boolean overlapsSameSemanticBand(StructureFloor other) {
        return sameSemanticBand(other) && overlapsFootprint(other);
    }

    int verticalGapTo(StructureFloor other) {
        if (ceilingY <= other.anchorY) return other.anchorY - ceilingY;
        if (other.ceilingY <= anchorY) return anchorY - other.ceilingY;
        return -1;
    }

    int attachmentGapTo(StructureFloor other) {
        if (sameSemanticBand(other)) return -1;
        return Math.max(0, verticalGapTo(other));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putInt("anchorY", anchorY);
        tag.putInt("ceilingY", ceilingY);
        tag.put("region", region.save());
        if (ceilingBoundaryRegion.area() > 0) {
            tag.put("ceilingBoundaryRegion", ceilingBoundaryRegion.save());
        }
        if (!connectors.isEmpty()) {
            tag.put("connectors", NbtHelper.fromList(connectors, ConnectorMarker::save));
        }
        return tag;
    }

    public static StructureFloor load(CompoundTag tag) {
        if (!tag.contains("region", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("StructureFloor is missing required region geometry");
        }
        BuildingFloorRegion region = BuildingFloorRegion.load(tag.getCompound("region"));
        int ceilingY = tag.getInt("ceilingY");
        BuildingFloorRegion ceilingBoundaryRegion = tag.contains("ceilingBoundaryRegion", Tag.TAG_COMPOUND)
                ? BuildingFloorRegion.load(tag.getCompound("ceilingBoundaryRegion")).withAnchorY(ceilingY)
                : BuildingFloorRegion.fromFootprint(ceilingY, List.of());
        List<ConnectorMarker> connectors = tag.contains("connectors", Tag.TAG_LIST)
                ? NbtHelper.toList(tag.getList("connectors", Tag.TAG_COMPOUND),
                value -> ConnectorMarker.load((CompoundTag) value)).stream()
                .filter(Objects::nonNull)
                .toList()
                : List.of();
        return new StructureFloor(tag.getInt("id"), tag.getInt("anchorY"), ceilingY,
                0, region, ceilingBoundaryRegion, connectors);
    }

    public StructureFloor withGeometry(int anchorY, int ceilingY, BuildingFloorRegion region) {
        BuildingFloorRegion boundary = this.ceilingY == ceilingY
                ? ceilingBoundaryRegion
                : BuildingFloorRegion.fromFootprint(ceilingY, List.of());
        return new StructureFloor(id, anchorY, ceilingY, floorNumber, region, boundary, connectors);
    }

    public StructureFloor withFloorNumber(int newFloorNumber) {
        return new StructureFloor(id, anchorY, ceilingY, newFloorNumber,
                region, ceilingBoundaryRegion, connectors);
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
