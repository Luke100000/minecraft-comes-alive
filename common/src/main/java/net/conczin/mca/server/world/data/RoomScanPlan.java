package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.Objects;
import java.util.Optional;

/**
 * Canonical action and identity plan shared by Blueprint projection and server execution.
 *
 * @param currentRoom registered Room physically selected at the interaction position, or empty
 * @param mode action derived from the current world and persisted Village state
 * @param targetBuildingId logical building receiving an attachment, or {@code -1}
 * @param prospectiveFloorNumber attachment floor-number preview, when applicable
 * @param interactionSource original player or diagnostic position
 * @param scanSeed exact selected position used to choose the planned Room/component within its Floor
 * @param targetStructureId persisted Structure receiving an in-Structure Room operation, or {@code -1}
 * @param targetFloorId persisted Floor receiving an in-Structure Room operation, or {@code -1}
 * @param selectedAttachmentFloor exact prospective Floor selected for an attachment, or {@code null}
 */
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
        interactionSource = interactionSource.immutable();
        scanSeed = scanSeed.immutable();
        Objects.requireNonNull(mode, "mode");
        boolean existingFloor = targetStructureId >= 0 && targetFloorId >= 0;
        boolean noExistingFloor = targetStructureId == NO_INTERACTION_STRUCTURE
                && targetFloorId == NO_INTERACTION_FLOOR;
        boolean noAttachment = targetBuildingId == NO_TARGET_BUILDING
                && prospectiveFloorNumber == NO_PROSPECTIVE_FLOOR && selectedAttachmentFloor == null;
        boolean valid = switch (mode) {
            case ADD_BUILDING -> currentRoom.isEmpty() && noExistingFloor && noAttachment;
            case ADD_ROOM -> currentRoom.isEmpty() && existingFloor && noAttachment;
            case UPDATE_ROOM -> currentRoom.filter(room -> room.getStructureId() == targetStructureId
                    && room.getFloorId() == targetFloorId).isPresent() && existingFloor && noAttachment;
            case ADD_FLOOR, ADD_BASEMENT -> currentRoom.isEmpty() && noExistingFloor
                    && targetBuildingId >= 0 && selectedAttachmentFloor != null
                    && prospectiveFloorNumber != NO_PROSPECTIVE_FLOOR
                    && (mode == Village.RoomScanMode.ADD_BASEMENT) == (prospectiveFloorNumber < 0);
        };
        if (!valid) throw new IllegalArgumentException("Inconsistent Room scan target for " + mode);
    }

    public RoomScanPlan(Optional<Building> currentRoom,
                        Village.RoomScanMode mode,
                        int targetBuildingId,
                        int prospectiveFloorNumber,
                        BlockPos interactionSource,
                        BlockPos scanSeed,
                        int targetStructureId,
                        int targetFloorId) {
        this(currentRoom, mode, targetBuildingId, prospectiveFloorNumber,
                interactionSource, scanSeed, targetStructureId, targetFloorId, null);
    }

    public static RoomScanPlan addBuilding(BlockPos source) {
        return new RoomScanPlan(Optional.empty(), Village.RoomScanMode.ADD_BUILDING,
                NO_TARGET_BUILDING, NO_PROSPECTIVE_FLOOR, source, source,
                NO_INTERACTION_STRUCTURE, NO_INTERACTION_FLOOR, null);
    }

    static RoomScanPlan updateRoom(Building room, BlockPos source) {
        return new RoomScanPlan(Optional.of(room), Village.RoomScanMode.UPDATE_ROOM,
                NO_TARGET_BUILDING, NO_PROSPECTIVE_FLOOR, source, source,
                room.getStructureId(), room.getFloorId(), null);
    }

    static RoomScanPlan addRoom(int structureId, int floorId, BlockPos source) {
        return addRoom(structureId, floorId, source, source);
    }

    static RoomScanPlan addRoom(int structureId,
                                int floorId,
                                BlockPos source,
                                BlockPos scanSeed) {
        return new RoomScanPlan(Optional.empty(), Village.RoomScanMode.ADD_ROOM,
                NO_TARGET_BUILDING, NO_PROSPECTIVE_FLOOR, source, scanSeed,
                structureId, floorId, null);
    }

    static RoomScanPlan attachment(int targetBuildingId,
                                   int floorNumber,
                                   BlockPos source,
                                   BlockPos scanSeed,
                                   StructureFloor selectedFloor) {
        Village.RoomScanMode mode = floorNumber < 0
                ? Village.RoomScanMode.ADD_BASEMENT : Village.RoomScanMode.ADD_FLOOR;
        return new RoomScanPlan(Optional.empty(), mode, targetBuildingId, floorNumber, source, scanSeed,
                NO_INTERACTION_STRUCTURE, NO_INTERACTION_FLOOR, selectedFloor);
    }

}
