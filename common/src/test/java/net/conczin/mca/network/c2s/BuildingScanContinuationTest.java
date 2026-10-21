package net.conczin.mca.network.c2s;

import net.minecraft.core.BlockPos;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.RoomWorkflow;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class BuildingScanContinuationTest {
    @Test
    void physicalFailureDoesNotClaimTheSelectedRoomChanged() {
        var physical = outcome(RoomWorkflow.Status.FAILED, Building.validationResult.NOT_IN_BUILDING, 21);
        assertEquals("blueprint.scan.not_in_building",
                ReportBuildingMessage.workflowFeedbackKey(physical, ReportBuildingMessage.Action.SCAN_ROOM));
        var changed = outcome(RoomWorkflow.Status.FAILED, Building.validationResult.TARGET_CHANGED, 21);
        assertEquals("blueprint.targetChanged",
                ReportBuildingMessage.workflowFeedbackKey(changed, ReportBuildingMessage.Action.SCAN_ROOM));
        assertEquals("blueprint.targetChanged",
                ReportBuildingMessage.workflowFeedbackKey(changed, ReportBuildingMessage.Action.SET_ROOM_INHERITANCE));
    }

    @Test
    void workflowFeedbackRetainsSuccessAndTypeChoiceSemantics() {
        assertEquals("blueprint.roomAlreadyAdded", ReportBuildingMessage.workflowFeedbackKey(
                outcome(RoomWorkflow.Status.FAILED, Building.validationResult.IDENTICAL, -1),
                ReportBuildingMessage.Action.SCAN_ROOM));
        assertEquals("blueprint.roomUpdated", ReportBuildingMessage.workflowFeedbackKey(
                outcome(RoomWorkflow.Status.COMMITTED, Building.validationResult.SUCCESS, 21),
                ReportBuildingMessage.Action.SCAN_ROOM));
        assertNull(ReportBuildingMessage.workflowFeedbackKey(
                outcome(RoomWorkflow.Status.REQUIRES_TYPE_SELECTION, Building.validationResult.SUCCESS, 21),
                ReportBuildingMessage.Action.SCAN_ROOM));
    }

    private static RoomWorkflow.Outcome outcome(RoomWorkflow.Status status, Building.validationResult result, int expected) {
        return new RoomWorkflow.Outcome(status, result, BlockPos.ZERO, List.of(), expected, Integer.MIN_VALUE);
    }

    @Test
    void typeSelectionContinuationRejectsPlayerMovement() {
        BlockPos source = new BlockPos(4, 64, 8);

        assertTrue(ConfirmBuildingPolymorphMessage.matchesContinuationSource(source, source));
        assertFalse(ConfirmBuildingPolymorphMessage.matchesContinuationSource(source, source.east()));
        assertFalse(ConfirmBuildingPolymorphMessage.matchesContinuationSource(null, source));
    }

    @Test
    void floorRemovalRequiresCompletePhysicalIdentity() {
        ReportBuildingMessage.FloorRemovalTarget target = ReportBuildingMessage
                .parseFloorRemovalTarget("-2:7:3").orElseThrow();

        assertEquals(-2, target.floorNumber());
        assertEquals(7, target.structureId());
        assertEquals(3, target.floorId());
        assertTrue(ReportBuildingMessage.parseFloorRemovalTarget("-2").isEmpty());
        assertTrue(ReportBuildingMessage.parseFloorRemovalTarget("-2:7").isEmpty());
        assertTrue(ReportBuildingMessage.parseFloorRemovalTarget("-2:-1:3").isEmpty());
        assertTrue(ReportBuildingMessage.parseFloorRemovalTarget("-2:7:-1").isEmpty());
        assertTrue(ReportBuildingMessage.parseFloorRemovalTarget(Integer.MIN_VALUE + ":7:3").isEmpty());
        assertTrue(ReportBuildingMessage.parseFloorRemovalTarget("bad:7:3").isEmpty());
        assertTrue(ReportBuildingMessage.parseFloorRemovalTarget("-2:7:3:4").isEmpty());
        assertTrue(ReportBuildingMessage.parseFloorRemovalTarget(null).isEmpty());
    }
}
