package net.conczin.mca.server.world.data;

import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Objects;

/** Stable persistent identity for one physical storey in a Structure. */
public record StructureFloor(int id, int anchorY, int ceilingY, int floorNumber,
                             BuildingFloorRegion region, List<ConnectorMarker> connectors) {
    public StructureFloor {
        connectors = connectors == null ? List.of() : List.copyOf(connectors);
    }

    public StructureFloor(int id, int anchorY, int ceilingY, int floorNumber, BuildingFloorRegion region) {
        this(id, anchorY, ceilingY, floorNumber, region, List.of());
    }

    public StructureFloor(int id, int anchorY, int ceilingY, BuildingFloorRegion region) {
        this(id, anchorY, ceilingY, 0, region);
    }

    public boolean contains(int x, int z) {
        return region != null && region.containsHorizontally(x, z);
    }

    public int area() {
        return region == null ? 0 : region.area();
    }

    int verticalGapTo(StructureFloor other) {
        if (ceilingY <= other.anchorY) return other.anchorY - ceilingY;
        if (other.ceilingY <= anchorY) return anchorY - other.ceilingY;
        return -1;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putInt("anchorY", anchorY);
        tag.putInt("ceilingY", ceilingY);
        if (region != null) {
            tag.put("region", region.save());
        }
        if (!connectors.isEmpty()) {
            tag.put("connectors", NbtHelper.fromList(connectors, ConnectorMarker::save));
        }
        return tag;
    }

    public static StructureFloor load(CompoundTag tag) {
        BuildingFloorRegion region = tag.contains("region")
                ? BuildingFloorRegion.load(tag.getCompound("region"))
                : new BuildingFloorRegion(tag.getInt("anchorY"), 0, java.util.List.of());
        int floorNumber = tag.contains("floorNumber") ? tag.getInt("floorNumber") : 0;
        List<ConnectorMarker> connectors = tag.contains("connectors", Tag.TAG_LIST)
                ? NbtHelper.toList(tag.getList("connectors", Tag.TAG_COMPOUND),
                value -> ConnectorMarker.load((CompoundTag) value)).stream()
                .filter(Objects::nonNull)
                .toList()
                : List.of();
        return new StructureFloor(tag.getInt("id"), tag.getInt("anchorY"), tag.getInt("ceilingY"),
                floorNumber, region, connectors);
    }

    public StructureFloor withGeometry(int anchorY, int ceilingY, BuildingFloorRegion region) {
        return new StructureFloor(id, anchorY, ceilingY, floorNumber, region, connectors);
    }

    public StructureFloor withFloorNumber(int newFloorNumber) {
        return new StructureFloor(id, anchorY, ceilingY, newFloorNumber, region, connectors);
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

        static ConnectorType fromSerializedName(String name) {
            for (ConnectorType type : values()) {
                if (type.serializedName.equals(name)) return type;
            }
            return null;
        }
    }
}
