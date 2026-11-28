package net.conczin.mca.entity.ai.brain.tasks;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WalkTargetRetryGateTest {
    private static final BlockPos DESTINATION = new BlockPos(12, 64, 12);
    private static final BlockPos START = new BlockPos(0, 64, 0);

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
    void routeChangeAllowsOneImmediateRetryForTheSameDestination() {
        WalkTargetRetryGate gate = new WalkTargetRetryGate(100, 16);

        assertTrue(gate.tryReserve(DESTINATION, START, 100));
        assertFalse(gate.tryReserve(DESTINATION, START, 120));

        gate.reset(DESTINATION.east());
        assertFalse(gate.tryReserve(DESTINATION, START, 121));

        gate.reset(DESTINATION);
        assertTrue(gate.tryReserve(DESTINATION, START, 122));
        assertFalse(gate.tryReserve(DESTINATION, START, 123));

        gate.reset(DESTINATION);
        assertTrue(gate.tryReserve(DESTINATION, START, 124));
        assertFalse(gate.tryReserve(DESTINATION, START, 125));
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
        assertEquals(120L, gate.stalledSince(), "the retry must retain the time of the actual progress");
        assertFalse(gate.noteProgress(DESTINATION.east(), START.east(10), 123),
                "progress toward a different target cannot clear failure for this destination");
    }

    @Test
    void repeatedFailedFlanksAndReturnLegsDoNotRenewFailureAge() {
        WalkTargetRetryGate gate = new WalkTargetRetryGate(100, 16, 1);
        BlockPos destination = START.east(10);
        BlockPos flank = START.north(8);
        BlockPos approach = START.east(4);
        assertTrue(gate.tryReserve(destination, START, 100));
        assertTrue(gate.tryReserve(destination, flank, 200), "displacement may permit another search");
        assertEquals(100L, gate.stalledSince(), "moving away is not progress toward the destination");
        assertTrue(gate.noteProgress(destination, approach, 230));
        assertTrue(gate.tryReserve(destination, approach, 231));

        for (int lap = 0; lap < 8; lap++) {
            long time = 400L + lap * 200L;
            assertTrue(gate.tryReserve(destination, flank, time));
            assertFalse(gate.noteProgress(destination, approach, time + 30L),
                    "returning to an already reached approach must not refresh failure");
            assertTrue(gate.tryReserve(destination, approach, time + 100L));
            assertEquals(230L, gate.stalledSince(), "repeated attempts must retain the failure window");
        }
        assertTrue(gate.noteProgress(destination, START.east(8), 2_100L),
                "new progress toward the destination must still refresh failure");
    }

    @Test
    void circlingSlightlyCloserDoesNotCountAsMeaningfulProgress() {
        WalkTargetRetryGate gate = new WalkTargetRetryGate(100, 16, 1);
        BlockPos destination = START.east(10);
        BlockPos approach = START.east(4);
        assertTrue(gate.tryReserve(destination, approach, 100));
        assertFalse(gate.noteProgress(destination, START.offset(7, 0, 5), 150),
                "crossing to the other side of a blocked approach barely reduces its distance");
        assertTrue(gate.tryReserve(destination, START.offset(7, 0, 5), 200));
        assertEquals(100L, gate.stalledSince());
        assertTrue(gate.noteProgress(destination, START.east(8), 230),
                "substantial new distance gain still renews failure");
    }

    @Test
    void progressRetainsOneEarlyRetryEvenAfterTheOldIntervalElapsed() {
        for (long delay : new long[]{20L, 150L}) {
            WalkTargetRetryGate gate = new WalkTargetRetryGate(100, 16, 1);
            assertTrue(gate.tryReserve(DESTINATION, START, 100));
            assertTrue(gate.noteProgress(DESTINATION, START.east(5), 100 + delay));
            assertTrue(gate.tryReserve(DESTINATION, START.east(5), 101 + delay));
            assertTrue(gate.tryReserve(DESTINATION, START.east(5), 102 + delay));
            assertFalse(gate.tryReserve(DESTINATION, START.east(5), 103 + delay),
                    "progress must grant the initial request and one early escalation");
        }
    }

    @Test
    void completingRepeatedShortTripsStartsFreshRetryEpisodes() {
        WalkTargetRetryGate gate = new WalkTargetRetryGate(100, 16, 1);
        BlockPos nearbyDestination = START.east(2);

        for (int trip = 0; trip < 4; trip++) {
            long started = 100L + trip * 4L;
            assertTrue(gate.tryReserve(nearbyDestination, START, started),
                    "a completed short trip must not delay another departure to the same destination");
            assertEquals(started, gate.stalledSince(), "a new trip must not inherit old failure age");
            assertTrue(gate.tryReserve(nearbyDestination, START, started + 1L),
                    "each new trip retains its own early retry");
            assertFalse(gate.tryReserve(nearbyDestination, START, started + 2L),
                    "an unfinished trip still backs off after its early retry");
            assertFalse(gate.noteProgress(nearbyDestination, nearbyDestination, started + 3L),
                    "the two-block trip must remain below the ordinary progress threshold");
            gate.reset(nearbyDestination);
        }
    }

    @Test
    void repeatedFailureReportsRetainTheShiftedEpisodeUntilRecovery() {
        WalkTargetRetryGate gate = new WalkTargetRetryGate(100, 16, 1);
        assertTrue(gate.tryReserve(DESTINATION, START, 100));
        gate.noteFailure(DESTINATION, 100);
        gate.observePreemption(115, true);
        gate.observePreemption(165, false);

        gate.noteFailure(DESTINATION, 100);
        assertEquals(150L, gate.unresolvedFailureSince(DESTINATION));
        gate.noteFailure(DESTINATION, 180);
        assertEquals(150L, gate.unresolvedFailureSince(DESTINATION));

        gate.clearFailure();
        gate.noteFailure(DESTINATION, 200);
        assertEquals(200L, gate.unresolvedFailureSince(DESTINATION));
    }

    @Test
    void closedPreemptionIntervalsShiftStalledAndUnresolvedFailureWithoutAccruingPostPreemptionIdleTicks() {
        WalkTargetRetryGate gate = new WalkTargetRetryGate(100, 16, 1, null);
        assertTrue(gate.tryReserve(DESTINATION, START, 100));
        gate.noteFailure(DESTINATION, 100);

        // Preemption flash 1: [115, 165) = 50 ticks, closed at 165 before consumer runs at 180.
        gate.observePreemption(115, true);
        gate.observePreemption(140, true);
        gate.observePreemption(165, false);
        // Non-preempted idle ticks [165, 180) must NOT accrue into shift.
        gate.observePreemption(175, false);

        // Preemption flash 2: [180, 210) = 30 ticks, still open when the producer resumes at 210.
        gate.observePreemption(180, true);
        gate.observePreemption(210, false);
        assertEquals(80L, gate.unresolvedFailureSince(DESTINATION) - 100L);
        assertEquals(180L, gate.stalledSince());
        assertEquals(180L, gate.unresolvedFailureSince(DESTINATION));
        gate.observePreemption(215, false);
        assertEquals(180L, gate.stalledSince());
        assertEquals(180L, gate.unresolvedFailureSince(DESTINATION));

        // Real progress toward target clears unresolvedFailureSince.
        assertTrue(gate.noteProgress(DESTINATION, START.east(5), 220));
        assertEquals(Long.MIN_VALUE, gate.unresolvedFailureSince(DESTINATION));
    }
}
