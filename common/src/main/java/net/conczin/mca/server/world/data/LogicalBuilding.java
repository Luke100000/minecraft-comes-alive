package net.conczin.mca.server.world.data;

import net.minecraft.nbt.CompoundTag;

/** Stable logical-building metadata independent from any physical root Structure. */
final class LogicalBuilding {
    private final int id;
    private int mainRoomId;
    private boolean inheritanceEnabled;

    LogicalBuilding(int id,
                    int mainRoomId,
                    boolean inheritanceEnabled) {
        this.id = id;
        this.mainRoomId = mainRoomId;
        this.inheritanceEnabled = inheritanceEnabled;
    }

    LogicalBuilding(CompoundTag tag) {
        this(tag.getInt("id"),
                tag.contains("mainRoomId") ? tag.getInt("mainRoomId") : -1,
                !tag.contains("inheritanceEnabled") || tag.getBoolean("inheritanceEnabled"));
    }

    int id() {
        return id;
    }

    int mainRoomId() {
        return mainRoomId;
    }

    boolean inheritanceEnabled() {
        return inheritanceEnabled;
    }

    void setMainRoomId(int mainRoomId) {
        this.mainRoomId = mainRoomId;
    }

    void setInheritanceEnabled(boolean inheritanceEnabled) {
        this.inheritanceEnabled = inheritanceEnabled;
    }

    LogicalBuilding copy() {
        return new LogicalBuilding(id, mainRoomId, inheritanceEnabled);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putInt("mainRoomId", mainRoomId);
        tag.putBoolean("inheritanceEnabled", inheritanceEnabled);
        return tag;
    }
}
