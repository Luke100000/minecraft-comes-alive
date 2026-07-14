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
    public RoomScanPlan {
        building = building == null ? Optional.empty() : building;
        interactionSource = interactionSource.immutable();
        scanSeed = scanSeed.immutable();
    }

    public static RoomScanPlan addBuilding(BlockPos source) {
        return new RoomScanPlan(Optional.empty(), Village.RoomScanMode.ADD_BUILDING,
                -1, Integer.MIN_VALUE, source, source);
    }

    public Optional<Building> functionalRoom() {
        return mode == Village.RoomScanMode.UPDATE_ROOM ? building : Optional.empty();
    }
}
