package net.conczin.mca.entity.ai.brain.tasks;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalkTargetRetryGateTest {
    private static final BlockPos DESTINATION = new BlockPos(12, 64, 12);
    private static final BlockPos START = new BlockPos(0, 64, 0);

    @Test
    void checkingAvailabilityDoesNotStartLocalStrollCooldown() {
        WalkTargetRetryGate gate = new WalkTargetRetryGate(40, 1);

        assertTrue(gate.canReserve(START, START, 100));
        assertTrue(gate.canReserve(START, START, 101));
        assertTrue(gate.tryReserve(START, START, 102));
        assertFalse(gate.canReserve(START, START, 103));
        assertFalse(gate.canReserve(START, START, 141));
        assertTrue(gate.canReserve(START, START, 142));
        assertTrue(gate.tryReserve(START, START, 142));
        assertFalse(gate.canReserve(START, START, 143));
    }

    @Test
    void checkingAvailabilityDoesNotConsumeImmediateRetryOrChangeDestination() {
        WalkTargetRetryGate gate = new WalkTargetRetryGate(100, 16, 1);
        assertTrue(gate.tryReserve(DESTINATION, START, 100));

        assertTrue(gate.canReserve(DESTINATION, START, 120));
        assertTrue(gate.canReserve(DESTINATION, START, 120));
        assertTrue(gate.tryReserve(DESTINATION, START, 120));
        assertFalse(gate.canReserve(DESTINATION, START, 140));
        assertTrue(gate.canReserve(DESTINATION.east(), START, 140));
        assertTrue(gate.canReserve(DESTINATION, START.east(4), 140));
        assertTrue(gate.canReserve(DESTINATION, START, 10));
        assertFalse(gate.canReserve(DESTINATION, START, 140));
        assertEquals(100L, gate.stalledSince());
        assertEquals(120L, gate.previousAttemptTime());
        assertTrue(gate.tryReserve(DESTINATION, START, 220));
    }

    @Test
    void unchangedDestinationWithoutProgressCannotImmediatelyRepeatSearch() {
        WalkTargetRetryGate gate = new WalkTargetRetryGate(100, 16);

        assertTrue(gate.tryReserve(DESTINATION, START, 100));
        assertFalse(gate.tryReserve(DESTINATION, START, 120));
        assertFalse(gate.tryReserve(DESTINATION, START.east(3), 140));
        assertTrue(gate.tryReserve(DESTINATION, START, 200));
        assertFalse(gate.tryReserve(DESTINATION, START, 220));
    }

    @Test
    void meaningfulMovementOrChangedDestinationAllowsImmediateRetry() {
        WalkTargetRetryGate gate = new WalkTargetRetryGate(100, 16);

        assertTrue(gate.tryReserve(DESTINATION, START, 100));
        assertTrue(gate.tryReserve(DESTINATION, START.east(4), 101));
        assertFalse(gate.tryReserve(DESTINATION, START.east(4), 102));
        assertTrue(gate.tryReserve(DESTINATION.east(), START.east(4), 103));
    }

    @Test
    void localStrollCanUseShorterCooldownAndResetsAfterSingleBlockStep() {
        WalkTargetRetryGate gate = new WalkTargetRetryGate(40, 1);

        assertTrue(gate.tryReserve(START, START, 100));
        assertFalse(gate.tryReserve(START, START, 101));
        assertTrue(gate.tryReserve(START, START.east(), 102));
        assertFalse(gate.tryReserve(START, START.east(), 103));
        assertTrue(gate.tryReserve(START, START.east(), 142));
    }

    @Test
    void backwardsClockChangeDoesNotKeepOldCooldown() {
        WalkTargetRetryGate gate = new WalkTargetRetryGate(100, 16);

        assertTrue(gate.tryReserve(DESTINATION, START, 100));
        assertTrue(gate.tryReserve(DESTINATION, START, 10));
    }

    @Test
    void persistentDestinationGetsOneEarlyEscalationBeforeBackoff() {
        WalkTargetRetryGate gate = new WalkTargetRetryGate(100, 16, 1);

        assertTrue(gate.tryReserve(DESTINATION, START, 100));
        assertTrue(gate.tryReserve(DESTINATION, START, 120));
        assertFalse(gate.tryReserve(DESTINATION, START, 140));
        assertTrue(gate.tryReserve(DESTINATION, START, 220));
        assertFalse(gate.tryReserve(DESTINATION, START, 240));
    }

    @Test
    void stalledDestinationKeepsItsOriginalFailureAgeAcrossScheduledRetries() {
        WalkTargetRetryGate gate = new WalkTargetRetryGate(100, 16, 1);
        assertTrue(gate.tryReserve(DESTINATION, START, 100));
        assertTrue(gate.tryReserve(DESTINATION, START, 120));
        assertFalse(gate.tryReserve(DESTINATION, START, 140));
        assertEquals(100L, gate.stalledSince());
        assertTrue(gate.tryReserve(DESTINATION, START, 220));
        assertFalse(gate.tryReserve(DESTINATION, START, 240));
        assertEquals(100L, gate.stalledSince(), "periodic retries must not reset the give-up clock");
        assertTrue(gate.tryReserve(DESTINATION, START.east(4), 241));
        assertEquals(241L, gate.stalledSince(), "physical progress starts a new retry episode");
        assertTrue(gate.tryReserve(DESTINATION.east(), START.east(4), 242));
        assertEquals(242L, gate.stalledSince(), "a changed target starts a new retry episode");
    }

    @Test
    void progressAccumulatesAcrossSeveralSmallSteps() {
        WalkTargetRetryGate gate = new WalkTargetRetryGate(100, 16);
        assertTrue(gate.tryReserve(DESTINATION, START, 100));
        assertTrue(gate.tryReserve(DESTINATION, START.east(3), 200));
        assertEquals(100L, gate.stalledSince());
        assertTrue(gate.tryReserve(DESTINATION, START.east(6), 201));
        assertEquals(201L, gate.stalledSince(),
                "six blocks of cumulative progress must not be mistaken for an unchanged stalled position");
    }

    @Test
    void movingAlongAnActivePartialPathClearsTheOldRetryEpisodeExactlyOnce() {
        WalkTargetRetryGate gate = new WalkTargetRetryGate(100, 16, 1);
        assertTrue(gate.tryReserve(DESTINATION, START, 100));
        assertFalse(gate.noteProgress(DESTINATION, START.east(3), 110));
        assertTrue(gate.noteProgress(DESTINATION, START.east(5), 120));
        assertEquals(120L, gate.stalledSince());
        assertFalse(gate.noteProgress(DESTINATION, START.east(5), 121));
        assertTrue(gate.tryReserve(DESTINATION, START.east(5), 122),
                "movement must allow a fresh search even before the old cooldown expires");
        assertEquals(122L, gate.stalledSince());
        assertFalse(gate.noteProgress(DESTINATION.east(), START.east(10), 123),
                "progress toward a different target cannot clear failure for this destination");
    }
}
