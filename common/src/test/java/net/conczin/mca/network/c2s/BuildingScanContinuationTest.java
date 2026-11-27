package net.conczin.mca.network.c2s;

import net.conczin.mca.resources.Rank;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.RoomWorkflow;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuildingScanContinuationTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }
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

    @Test
    void reportBuildingForceTypeOnlyAllowsBlockedOrClear() {
        assertTrue(ReportBuildingMessage.isAllowedForcedType("blocked"));
        assertTrue(ReportBuildingMessage.isAllowedForcedType(null));
        assertFalse(ReportBuildingMessage.isAllowedForcedType(""));
        assertFalse(ReportBuildingMessage.isAllowedForcedType("armory"));
        assertFalse(ReportBuildingMessage.isAllowedForcedType("big_house"));
        assertFalse(ReportBuildingMessage.isAllowedForcedType("nonexistent"));
    }

    @Test
    void saveVillageMessageEnforcesVillageIdPerFieldRanksBoundsAndMarksDirty() {
        DirtyTrackingVillage village = new DirtyTrackingVillage(7);
        village.setTaxes(0.25f);
        village.setPopulationThreshold(0.5f);
        village.setMarriageThreshold(0.75f);
        village.dirtyCount = 0;

        // Wrong village ID is rejected.
        new SaveVillageMessage(8, 0.9f, 0.9f, 0.9f).applyTo(village, Rank.MAYOR);
        assertEquals(0, village.dirtyCount);
        assertEquals(0.25f, village.getTaxes());

        // PEASANT cannot modify any setting.
        new SaveVillageMessage(7, 0.9f, 0.9f, 0.9f).applyTo(village, Rank.PEASANT);
        assertEquals(0, village.dirtyCount);
        assertEquals(0.25f, village.getTaxes());
        assertEquals(0.5f, village.getPopulationThreshold());
        assertEquals(0.75f, village.getMarriageThreshold());

        // MERCHANT can modify taxes only (even when packet includes other fields).
        new SaveVillageMessage(7, 0.375f, 0.125f, 0.125f).applyTo(village, Rank.MERCHANT);
        assertEquals(1, village.dirtyCount);
        assertEquals(0.375f, village.getTaxes());
        assertEquals(0.5f, village.getPopulationThreshold());
        assertEquals(0.75f, village.getMarriageThreshold());

        // NOBLE can modify taxes and populationThreshold, but not marriageThreshold.
        new SaveVillageMessage(7, 0.5f, 0.25f, 0.125f).applyTo(village, Rank.NOBLE);
        assertEquals(3, village.dirtyCount);
        assertEquals(0.5f, village.getTaxes());
        assertEquals(0.25f, village.getPopulationThreshold());
        assertEquals(0.75f, village.getMarriageThreshold());

        // MAYOR can modify all three; out-of-range values are clamped and NaN is ignored.
        new SaveVillageMessage(7, Float.NaN, -0.5f, 2.5f).applyTo(village, Rank.MAYOR);
        assertEquals(5, village.dirtyCount);
        assertEquals(0.5f, village.getTaxes());
        assertEquals(0.0f, village.getPopulationThreshold());
        assertEquals(1.0f, village.getMarriageThreshold());
    }

    @Test
    void renameVillageAndToggleAutoScanValidateAndMarkDirty() {
        DirtyTrackingVillage village = new DirtyTrackingVillage(7);
        village.setName("Default");
        village.dirtyCount = 0;

        new RenameVillageMessage(8, "Other").applyTo(village);
        new RenameVillageMessage(7, "").applyTo(village);
        new RenameVillageMessage(7, "   ").applyTo(village);
        new RenameVillageMessage(7, "x".repeat(33)).applyTo(village);
        assertEquals("Default", village.getName());
        assertEquals(0, village.dirtyCount);

        new RenameVillageMessage(7, "Oakdale").applyTo(village);
        assertEquals("Oakdale", village.getName());
        assertEquals(1, village.dirtyCount);

        boolean initialAutoScan = village.isAutoScan();
        village.toggleAutoScan();
        assertEquals(!initialAutoScan, village.isAutoScan());
        assertEquals(2, village.dirtyCount);
    }

    private static final class DirtyTrackingVillage extends Village {
        private int dirtyCount;

        private DirtyTrackingVillage(int id) {
            super(id, null);
        }

        @Override
        public void markDirty() {
            dirtyCount++;
        }
    }
}
