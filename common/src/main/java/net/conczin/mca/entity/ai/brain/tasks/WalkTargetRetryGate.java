package net.conczin.mca.entity.ai.brain.tasks;

import net.minecraft.core.BlockPos;

/**
 * Limits repeated publications for one villager and one behavior when the
 * destination and the villager's position have not meaningfully changed.
 * A new destination or physical progress permits an immediate new attempt.
 */
final class WalkTargetRetryGate {
    private final long retryIntervalTicks;
    private final double progressDistanceSqr;
    private final int immediateRetries;
    private BlockPos previousDestination;
    private BlockPos previousOrigin;
    private long previousAttemptTime;
    private BlockPos stalledOrigin;
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

    /** Checks eligibility without consuming a retry or changing its failure age. */
    boolean canReserve(BlockPos destination, BlockPos origin, long gameTime) {
        return !isSameStalledAttempt(destination, origin, gameTime)
                || gameTime - previousAttemptTime >= retryIntervalTicks
                || immediateRetriesRemaining > 0;
    }

    boolean tryReserve(BlockPos destination, BlockPos origin, long gameTime) {
        long elapsed = gameTime - previousAttemptTime;
        boolean unchanged = isSameStalledAttempt(destination, origin, gameTime);
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
            stalledSince = gameTime;
        }

        previousDestination = destination.immutable();
        previousOrigin = origin.immutable();
        previousAttemptTime = gameTime;
        return true;
    }

    private boolean isSameStalledAttempt(BlockPos destination, BlockPos origin, long gameTime) {
        return previousDestination != null
                && previousDestination.equals(destination)
                && origin.distSqr(stalledOrigin) < progressDistanceSqr
                && gameTime - previousAttemptTime >= 0L;
    }

    /** An active partial path may make progress without asking its producer for a new target. */
    boolean noteProgress(BlockPos destination, BlockPos origin, long gameTime) {
        if (previousDestination == null || !previousDestination.equals(destination)
                || origin.distSqr(stalledOrigin) < progressDistanceSqr) {
            return false;
        }
        previousDestination = null;
        stalledOrigin = origin.immutable();
        stalledSince = gameTime;
        immediateRetriesRemaining = immediateRetries;
        return true;
    }

    BlockPos previousOrigin() {
        return previousOrigin;
    }

    long previousAttemptTime() {
        return previousAttemptTime;
    }

    long stalledSince() {
        return stalledSince;
    }
}
