package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.Objects;
import java.util.Optional;

/** Canonical action and identity plan shared by Blueprint projection and server execution. */
public record RoomScanPlan(Optional<Building> currentRoom,
                           Village.RoomScanMode mode,
                           int targetBuildingId,
                           int prospectiveFloorNumber,
                           BlockPos interactionSource,
                           BlockPos scanSeed,
                           int targetStructureId,
                           int targetFloorId,
                           StructureFloor selectedAttachmentFloor) {
    private static final int NO_TARGET_BUILDING = -1;
    private static final int NO_PROSPECTIVE_FLOOR = Integer.MIN_VALUE;
    private static final int NO_INTERACTION_STRUCTURE = -1;
    private static final int NO_INTERACTION_FLOOR = -1;

    public RoomScanPlan {
        currentRoom = currentRoom == null ? Optional.empty() : currentRoom;
        interactionSource = Objects.requireNonNull(interactionSource, "interactionSource").immutable();
        scanSeed = Objects.requireNonNull(scanSeed, "scanSeed").immutable();
        Objects.requireNonNull(mode, "mode");

        boolean existingFloor = targetStructureId >= 0 && targetFloorId >= 0;
        boolean noExistingFloor = targetStructureId == NO_INTERACTION_STRUCTURE
                && targetFloorId == NO_INTERACTION_FLOOR;
        boolean noAttachment = targetBuildingId == NO_TARGET_BUILDING
                && prospectiveFloorNumber == NO_PROSPECTIVE_FLOOR
                && selectedAttachmentFloor == null;
        boolean valid = switch (mode) {
            case ADD_BUILDING -> currentRoom.isEmpty() && noExistingFloor && noAttachment;
            case ADD_ROOM -> currentRoom.isEmpty() && existingFloor && noAttachment;
            case UPDATE_ROOM -> currentRoom.filter(room -> room.getStructureId() == targetStructureId
                    && room.getFloorId() == targetFloorId).isPresent() && existingFloor && noAttachment;
            case ADD_ATTACHMENT -> currentRoom.isEmpty() && noExistingFloor
                    && targetBuildingId >= 0 && selectedAttachmentFloor != null
                    && prospectiveFloorNumber != NO_PROSPECTIVE_FLOOR;
        };
        if (!valid) throw new IllegalArgumentException("Inconsistent Room scan target for " + mode);
    }

    public static RoomScanPlan addBuilding(BlockPos source) {
        return new RoomScanPlan(Optional.empty(), Village.RoomScanMode.ADD_BUILDING,
                NO_TARGET_BUILDING, NO_PROSPECTIVE_FLOOR, source, source,
                NO_INTERACTION_STRUCTURE, NO_INTERACTION_FLOOR, null);
    }

    public static RoomScanPlan updateRoom(Building room, BlockPos source) {
        if (room == null) throw new IllegalArgumentException("Update Room requires a selected Room");
        return new RoomScanPlan(Optional.of(room), Village.RoomScanMode.UPDATE_ROOM,
                NO_TARGET_BUILDING, NO_PROSPECTIVE_FLOOR, source, source,
                room.getStructureId(), room.getFloorId(), null);
    }

    public static RoomScanPlan addRoom(int structureId, int floorId, BlockPos source) {
        return addRoom(structureId, floorId, source, source);
    }

    public static RoomScanPlan addRoom(int structureId,
                                       int floorId,
                                       BlockPos source,
                                       BlockPos scanSeed) {
        return new RoomScanPlan(Optional.empty(), Village.RoomScanMode.ADD_ROOM,
                NO_TARGET_BUILDING, NO_PROSPECTIVE_FLOOR, source, scanSeed,
                structureId, floorId, null);
    }

    public static RoomScanPlan attachment(int targetBuildingId,
                                          int floorNumber,
                                          BlockPos source,
                                          BlockPos scanSeed,
                                          StructureFloor selectedFloor) {
        return new RoomScanPlan(Optional.empty(), Village.RoomScanMode.ADD_ATTACHMENT,
                targetBuildingId, floorNumber, source, scanSeed,
                NO_INTERACTION_STRUCTURE, NO_INTERACTION_FLOOR, selectedFloor);
    }

    public boolean hasProspectiveFloor() {
        return prospectiveFloorNumber != NO_PROSPECTIVE_FLOOR;
    }
}
