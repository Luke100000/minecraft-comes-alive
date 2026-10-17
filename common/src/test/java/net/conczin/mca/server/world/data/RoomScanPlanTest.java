package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomScanPlanTest {
    @Test
    void roomAdditionRequiresAnExactPersistedFloorTarget() {
        assertThrows(IllegalArgumentException.class, () -> RoomScanPlan.addRoom(-1, 0, BlockPos.ZERO));
        assertThrows(IllegalArgumentException.class, () -> RoomScanPlan.addRoom(1, -1, BlockPos.ZERO));
    }

    @Test
    void attachmentPlanCarriesIdentityWithoutOwningScanGeometry() {
        RoomScanPlan plan = RoomScanPlan.attachment(1, -1, BlockPos.ZERO, BlockPos.ZERO);

        assertEquals(Village.RoomScanMode.ADD_ATTACHMENT, plan.mode());
        assertEquals(1, plan.targetBuildingId());
        assertEquals(-1, plan.prospectiveFloorNumber());
    }

    @Test
    void updateRequiresASelectedRoom() {
        assertThrows(IllegalArgumentException.class, () -> RoomScanPlan.updateRoom(null, BlockPos.ZERO));
    }

    @Test
    void updateCapturesTheSelectedFloorIdentity() {
        Building room = new Building(BlockPos.ZERO);
        room.setStructureId(3);
        room.setFloorId(7);
        RoomScanPlan plan = RoomScanPlan.updateRoom(room, BlockPos.ZERO);

        room.setStructureId(4);
        room.setFloorId(8);

        assertEquals(3, plan.targetStructureId());
        assertEquals(7, plan.targetFloorId());
    }

    @Test
    void prospectiveFloorPresenceIsOwnedByThePlan() {
        RoomScanPlan addBuilding = RoomScanPlan.addBuilding(BlockPos.ZERO);
        RoomScanPlan attachment = RoomScanPlan.attachment(
                1, -1, BlockPos.ZERO, BlockPos.ZERO);

        assertFalse(addBuilding.hasProspectiveFloor());
        assertTrue(attachment.hasProspectiveFloor());
        RoomScanPlan unnumbered = RoomScanPlan.attachment(
                1, Integer.MIN_VALUE, BlockPos.ZERO, BlockPos.ZERO);
        assertFalse(unnumbered.hasProspectiveFloor());
        assertEquals(1, unnumbered.targetBuildingId());
    }
}
