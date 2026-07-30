package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.Optional;

/**
 * Canonical action and identity plan shared by Blueprint projection and server execution.
 *
 * @param building registered Room at the interaction position, or the interaction
 *                 Structure's Main Room when the component is not registered yet
 * @param mode action derived from the current world and persisted Village state
 * @param targetBuildingId logical building receiving an attachment, or {@code -1}
 * @param prospectiveFloorNumber attachment floor-number preview, when applicable
 * @param interactionSource original player or diagnostic position
 * @param scanSeed exact enclosed position selected for physical scanning
 */
public record RoomScanPlan(Optional<Building> building,
                           Village.RoomScanMode mode,
                           int targetBuildingId,
                           int prospectiveFloorNumber,
                           BlockPos interactionSource,
                           BlockPos scanSeed) {
    private static final int NO_TARGET_BUILDING = -1;
    private static final int NO_PROSPECTIVE_FLOOR = Integer.MIN_VALUE;

    public RoomScanPlan {
        building = building == null ? Optional.empty() : building;
        interactionSource = interactionSource.immutable();
        scanSeed = scanSeed.immutable();
    }

    public static RoomScanPlan addBuilding(BlockPos source) {
        return new RoomScanPlan(Optional.empty(), Village.RoomScanMode.ADD_BUILDING,
                NO_TARGET_BUILDING, NO_PROSPECTIVE_FLOOR, source, source);
    }

    static RoomScanPlan updateRoom(Building room, BlockPos source) {
        return new RoomScanPlan(Optional.of(room), Village.RoomScanMode.UPDATE_ROOM,
                NO_TARGET_BUILDING, NO_PROSPECTIVE_FLOOR, source, source);
    }

    static RoomScanPlan addRoom(Building mainRoom, BlockPos source) {
        return new RoomScanPlan(Optional.ofNullable(mainRoom), Village.RoomScanMode.ADD_ROOM,
                NO_TARGET_BUILDING, NO_PROSPECTIVE_FLOOR, source, source);
    }

    static RoomScanPlan attachment(int targetBuildingId,
                                   int floorNumber,
                                   BlockPos source,
                                   BlockPos scanSeed) {
        Village.RoomScanMode mode = floorNumber < 0
                ? Village.RoomScanMode.ADD_BASEMENT : Village.RoomScanMode.ADD_FLOOR;
        return new RoomScanPlan(Optional.empty(), mode, targetBuildingId, floorNumber, source, scanSeed);
    }

    public Optional<Building> functionalRoom() {
        return mode == Village.RoomScanMode.UPDATE_ROOM ? building : Optional.empty();
    }
}
