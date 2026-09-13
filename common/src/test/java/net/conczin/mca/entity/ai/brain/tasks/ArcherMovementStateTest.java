package net.conczin.mca.entity.ai.brain.tasks;

import org.junit.jupiter.api.Test;

import static net.conczin.mca.entity.ai.brain.tasks.ArcherMovementTask.selectBaseState;
import static net.conczin.mca.entity.ai.brain.tasks.ArcherMovementTask.shouldCancelStrafe;
import static net.conczin.mca.entity.ai.brain.tasks.ArcherMovementTask.shouldStartStrafe;
import static net.conczin.mca.entity.ai.brain.tasks.RangedCombatState.APPROACH;
import static net.conczin.mca.entity.ai.brain.tasks.RangedCombatState.EMERGENCY_FLEE;
import static net.conczin.mca.entity.ai.brain.tasks.RangedCombatState.HOLD;
import static net.conczin.mca.entity.ai.brain.tasks.RangedCombatState.KITE;
import static net.conczin.mca.entity.ai.brain.tasks.RangedCombatState.REPOSITION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArcherMovementStateTest {
    @Test
    void emergencyUsesSeparateEnterAndExitThresholds() {
        assertEquals(EMERGENCY_FLEE, selectBaseState(HOLD, 100, 12.24, 0, 225, 20));
        assertEquals(EMERGENCY_FLEE, selectBaseState(EMERGENCY_FLEE, 100, 24.99, 0, 225, 20));
        assertEquals(KITE, selectBaseState(EMERGENCY_FLEE, 100, 25.0, 0, 225, 20));
    }

    @Test
    void kiteUsesSeparateEnterAndExitThresholds() {
        assertEquals(KITE, selectBaseState(HOLD, 100, 35.99, 0, 225, 20));
        assertEquals(KITE, selectBaseState(KITE, 100, 80.99, 0, 225, 20));
        assertEquals(HOLD, selectBaseState(KITE, 100, 81.0, 0, 225, 20));
    }

    @Test
    void verticalSeparationDisablesCloseRangeRetreatBands() {
        assertEquals(HOLD, selectBaseState(HOLD, 100, 4, 2.51, 225, 20));
        assertEquals(HOLD, selectBaseState(KITE, 100, 4, 2.51, 225, 20));
    }

    @Test
    void sustainedLosLossRepositionsButBriefLossHolds() {
        assertEquals(HOLD, selectBaseState(HOLD, 100, 100, 0, 225, -10));
        assertEquals(REPOSITION, selectBaseState(HOLD, 100, 100, 0, 225, -11));
    }

    @Test
    void sustainedLosLossBeatsKiteRangeAfterTargetIsOccluded() {
        assertEquals(REPOSITION, selectBaseState(REPOSITION, 25, 25, 0, 225, -11));
        assertEquals(REPOSITION, selectBaseState(KITE, 25, 25, 0, 225, -75));
    }

    @Test
    void emergencyDistanceStillBeatsLostSight() {
        assertEquals(EMERGENCY_FLEE, selectBaseState(REPOSITION, 9, 9, 0, 225, -75));
    }

    @Test
    void outOfRangeApproachesBeforeOrdinaryHold() {
        assertEquals(APPROACH, selectBaseState(HOLD, 226, 100, 0, 225, 40));
    }

    @Test
    void approachDoesNotDropAtTinyRangeBoundaryCrossing() {
        assertEquals(APPROACH, selectBaseState(HOLD, 226, 100, 0, 225, 40));
        assertEquals(APPROACH, selectBaseState(APPROACH, 224, 100, 0, 225, 40));
    }

    @Test
    void strafeRequiresStableHoldAndExpiredCooldown() {
        assertFalse(shouldStartStrafe(19, 0));
        assertFalse(shouldStartStrafe(20, 1));
        assertTrue(shouldStartStrafe(20, 0));
    }

    @Test
    void strafeCancelsOnAnySafetyOrValidityBreak() {
        assertTrue(shouldCancelStrafe(true, true, false, true, false));
        assertTrue(shouldCancelStrafe(true, true, false, false, true));
        assertTrue(shouldCancelStrafe(false, true, false, false, false));
        assertTrue(shouldCancelStrafe(true, false, false, false, false));
        assertTrue(shouldCancelStrafe(true, true, true, false, false));
        assertFalse(shouldCancelStrafe(true, true, false, false, false));
    }
}
