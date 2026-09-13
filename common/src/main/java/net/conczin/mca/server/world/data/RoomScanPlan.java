package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.Optional;

/**
 * Canonical action and identity plan shared by Blueprint projection and server execution.
 *
 * @param currentRoom registered Room physically selected at the interaction position, or empty
 * @param mode action derived from the current world and persisted Village state
 * @param targetBuildingId logical building receiving an attachment, or {@code -1}
 * @param prospectiveFloorNumber attachment floor-number preview, when applicable
 * @param interactionSource original player or diagnostic position
 * @param scanSeed exact enclosed position selected for physical scanning
 * @param targetStructureId persisted Structure receiving an in-Structure Room operation, or {@code -1}
 * @param targetFloorId persisted Floor receiving an in-Structure Room operation, or {@code -1}
 */
public record RoomScanPlan(Optional<Building> currentRoom,
                           Village.RoomScanMode mode,
                           int targetBuildingId,
                           int prospectiveFloorNumber,
                           BlockPos interactionSource,
                           BlockPos scanSeed,
                           int targetStructureId,
                           int targetFloorId) {
    private static final int NO_TARGET_BUILDING = -1;
    private static final int NO_PROSPECTIVE_FLOOR = Integer.MIN_VALUE;
    private static final int NO_INTERACTION_STRUCTURE = -1;
    private static final int NO_INTERACTION_FLOOR = -1;

    public RoomScanPlan {
        currentRoom = currentRoom == null ? Optional.empty() : currentRoom;
        interactionSource = interactionSource.immutable();
        scanSeed = scanSeed.immutable();
    }

    public static RoomScanPlan addBuilding(BlockPos source) {
        return new RoomScanPlan(Optional.empty(), Village.RoomScanMode.ADD_BUILDING,
                NO_TARGET_BUILDING, NO_PROSPECTIVE_FLOOR, source, source,
                NO_INTERACTION_STRUCTURE, NO_INTERACTION_FLOOR);
    }

    static RoomScanPlan updateRoom(Building room, BlockPos source) {
        return new RoomScanPlan(Optional.of(room), Village.RoomScanMode.UPDATE_ROOM,
                NO_TARGET_BUILDING, NO_PROSPECTIVE_FLOOR, source, source,
                room.getStructureId(), room.getFloorId());
    }

    static RoomScanPlan addRoom(int structureId, int floorId, BlockPos source) {
        return addRoom(structureId, floorId, source, source);
    }

    static RoomScanPlan addRoom(int structureId,
                                int floorId,
                                BlockPos source,
                                BlockPos scanSeed) {
        return new RoomScanPlan(Optional.empty(), Village.RoomScanMode.ADD_ROOM,
                NO_TARGET_BUILDING, NO_PROSPECTIVE_FLOOR, source, scanSeed, structureId, floorId);
    }

    static RoomScanPlan attachment(int targetBuildingId,
                                   int floorNumber,
                                   BlockPos source,
                                   BlockPos scanSeed) {
        Village.RoomScanMode mode = floorNumber < 0
                ? Village.RoomScanMode.ADD_BASEMENT : Village.RoomScanMode.ADD_FLOOR;
        return new RoomScanPlan(Optional.empty(), mode, targetBuildingId, floorNumber, source, scanSeed,
                NO_INTERACTION_STRUCTURE, NO_INTERACTION_FLOOR);
    }

}
