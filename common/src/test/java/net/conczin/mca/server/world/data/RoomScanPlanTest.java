package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;

class RoomScanPlanTest {
    @Test
    void roomAdditionRequiresAnExactPersistedFloorTarget() {
        assertThrows(IllegalArgumentException.class, () -> RoomScanPlan.addRoom(-1, 0, BlockPos.ZERO));
        assertThrows(IllegalArgumentException.class, () -> RoomScanPlan.addRoom(1, -1, BlockPos.ZERO));
    }

    @Test
    void attachmentCannotBeExecutedWithoutItsSelectedGeometry() {
        assertThrows(IllegalArgumentException.class, () -> new RoomScanPlan(Optional.empty(),
                Village.RoomScanMode.ADD_BASEMENT, 1, -1, BlockPos.ZERO, BlockPos.ZERO, -1, -1));
    }

    @Test
    void updateCannotTargetAnUnselectedRoom() {
        assertThrows(IllegalArgumentException.class, () -> new RoomScanPlan(Optional.empty(),
                Village.RoomScanMode.UPDATE_ROOM, -1, Integer.MIN_VALUE, BlockPos.ZERO, BlockPos.ZERO, 1, 0));
    }
}
