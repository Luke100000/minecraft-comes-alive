package net.conczin.mca.server.world.data;

import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/** Connector metadata owned by exact Floor geometry rather than its persistence wrapper. */
public final class FloorConnector {
    private FloorConnector() {
    }

    public enum Type {
        LADDER("ladder"),
        TRAPDOOR("trapdoor"),
        DOOR("door"),
        GATE("gate");

        private final String serializedName;

        Type(String serializedName) {
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

        static Type fromBlockState(BlockState state) {
            if (state.getBlock() instanceof LadderBlock) return LADDER;
            if (state.getBlock() instanceof TrapDoorBlock) return TRAPDOOR;
            if (state.getBlock() instanceof DoorBlock) return DOOR;
            if (state.getBlock() instanceof FenceGateBlock) return GATE;
            return null;
        }

        public static Type fromSerializedName(String name) {
            for (Type type : values()) {
                if (type.serializedName.equals(name)) return type;
            }
            return null;
        }
    }

    public record Marker(BlockPos pos, Type type) {
        public Marker {
            pos = pos.immutable();
            Objects.requireNonNull(type, "type");
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.put("pos", NbtHelper.encodeBlockPos(pos));
            tag.putString("type", type.serializedName());
            return tag;
        }

        static Marker load(CompoundTag tag) {
            if (!tag.contains("pos") || !tag.contains("type")) return null;
            BlockPos pos = NbtHelper.decodeBlockPos(tag.get("pos"));
            Type type = Type.fromSerializedName(tag.getString("type"));
            return pos == null || type == null ? null : new Marker(pos, type);
        }
    }
}
