package net.conczin.mca.server.world.data;

import net.minecraft.nbt.CompoundTag;

/** Stable logical-building metadata independent from any physical root Structure. */
final class LogicalBuilding {
    private final int id;
    private int groundStructureId;
    private int groundFloorId;
    private int mainRoomId;
    private boolean inheritanceEnabled;

    LogicalBuilding(int id,
                    int groundStructureId,
                    int groundFloorId,
                    int mainRoomId,
                    boolean inheritanceEnabled) {
        this.id = id;
        this.groundStructureId = groundStructureId;
        this.groundFloorId = groundFloorId;
        this.mainRoomId = mainRoomId;
        this.inheritanceEnabled = inheritanceEnabled;
    }

    LogicalBuilding(CompoundTag tag) {
        this(tag.getInt("id"),
                tag.getInt("groundStructureId"),
                tag.getInt("groundFloorId"),
                tag.contains("mainRoomId") ? tag.getInt("mainRoomId") : -1,
                !tag.contains("inheritanceEnabled") || tag.getBoolean("inheritanceEnabled"));
    }

    int id() {
        return id;
    }

    int groundStructureId() {
        return groundStructureId;
    }

    int groundFloorId() {
        return groundFloorId;
    }

    int mainRoomId() {
        return mainRoomId;
    }

    boolean inheritanceEnabled() {
        return inheritanceEnabled;
    }

    void setGroundFloor(int structureId, int floorId) {
        groundStructureId = structureId;
        groundFloorId = floorId;
    }

    void setMainRoomId(int mainRoomId) {
        this.mainRoomId = mainRoomId;
    }

    void setInheritanceEnabled(boolean inheritanceEnabled) {
        this.inheritanceEnabled = inheritanceEnabled;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putInt("groundStructureId", groundStructureId);
        tag.putInt("groundFloorId", groundFloorId);
        tag.putInt("mainRoomId", mainRoomId);
        tag.putBoolean("inheritanceEnabled", inheritanceEnabled);
        return tag;
    }
}
