package net.conczin.mca.entity.ai.brain.tasks;

import net.minecraft.core.BlockPos;

/**
 * Limits repeated publications for one villager and one behavior when the
 * destination and the villager's position have not meaningfully changed.
 * Displacement permits another attempt, but only new progress toward the
 * destination renews its failure window.
 */
final class WalkTargetRetryGate {
    private final long retryIntervalTicks;
    private final double progressDistanceSqr;
    private final int immediateRetries;
    private BlockPos previousDestination;
    private long previousAttemptTime;
    private BlockPos stalledOrigin;
    private BlockPos progressOrigin;
    private long stalledSince;
    private int immediateRetriesRemaining;

    WalkTargetRetryGate(long retryIntervalTicks, double progressDistanceSqr) {
        this(retryIntervalTicks, progressDistanceSqr, 0);
    }

    WalkTargetRetryGate(long retryIntervalTicks, double progressDistanceSqr, int immediateRetries) {
        this.retryIntervalTicks = retryIntervalTicks;
        this.progressDistanceSqr = progressDistanceSqr;
        this.immediateRetries = immediateRetries;
    }

    boolean tryReserve(BlockPos destination, BlockPos origin, long gameTime) {
        long elapsed = gameTime - previousAttemptTime;
        boolean sameJourney = previousDestination != null
                && previousDestination.equals(destination)
                && elapsed >= 0L;
        boolean unchanged = sameJourney && origin.distSqr(stalledOrigin) < progressDistanceSqr;
        if (unchanged) {
            if (elapsed < retryIntervalTicks) {
                if (immediateRetriesRemaining == 0) {
                    return false;
                }
                immediateRetriesRemaining--;
            }
        } else {
            immediateRetriesRemaining = immediateRetries;
            stalledOrigin = origin.immutable();
            if (sameJourney) {
                recordProgress(destination, origin, gameTime);
            } else {
                progressOrigin = origin.immutable();
                stalledSince = gameTime;
            }
        }

        previousDestination = destination.immutable();
        previousAttemptTime = gameTime;
        return true;
    }

    /** Completion or a changed route ends the matching retry episode. */
    void reset(BlockPos destination) {
        if (previousDestination != null && previousDestination.equals(destination)) {
            previousDestination = null;
        }
    }

    /** An active partial path may make progress without asking its producer for a new target. */
    boolean noteProgress(BlockPos destination, BlockPos origin, long gameTime) {
        if (previousDestination == null || !previousDestination.equals(destination)
                || !recordProgress(destination, origin, gameTime)) {
            return false;
        }
        stalledOrigin = origin.immutable();
        // Make the next request eligible without consuming its early escalation.
        previousAttemptTime = gameTime - retryIntervalTicks;
        immediateRetriesRemaining = immediateRetries;
        return true;
    }

    private boolean recordProgress(BlockPos destination, BlockPos origin, long gameTime) {
        if (origin.distSqr(progressOrigin) < progressDistanceSqr
                || progressOrigin.distSqr(destination) - origin.distSqr(destination) < progressDistanceSqr) {
            return false;
        }
        progressOrigin = origin.immutable();
        stalledSince = gameTime;
        return true;
    }

    long stalledSince() {
        return stalledSince;
    }
}
