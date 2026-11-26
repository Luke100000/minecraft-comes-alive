package net.conczin.mca.entity.ai.brain.tasks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.jetbrains.annotations.Nullable;

/**
 * Limits repeated publications for one villager and one behavior when the
 * destination and the villager's position have not meaningfully changed.
 * Displacement permits another attempt, but only new progress toward the
 * destination renews its failure window.
 */
public final class WalkTargetRetryGate {
    private final long retryIntervalTicks;
    private final double progressDistanceSqr;
    private final int immediateRetries;
    @Nullable
    private final MemoryModuleType<GlobalPos> destinationMemory;
    private BlockPos previousDestination;
    private long previousAttemptTime;
    private BlockPos stalledOrigin;
    private BlockPos progressOrigin;
    private long stalledSince;
    private int immediateRetriesRemaining;
    private long openPreemptedSince = Long.MIN_VALUE;
    private long pendingFailureShift;
    private long unresolvedFailureSince = Long.MIN_VALUE;

    public WalkTargetRetryGate(long retryIntervalTicks, double progressDistanceSqr) {
        this(retryIntervalTicks, progressDistanceSqr, 0, null);
    }

    public WalkTargetRetryGate(long retryIntervalTicks, double progressDistanceSqr, int immediateRetries) {
        this(retryIntervalTicks, progressDistanceSqr, immediateRetries, null);
    }

    public WalkTargetRetryGate(long retryIntervalTicks, double progressDistanceSqr, int immediateRetries,
                               @Nullable MemoryModuleType<GlobalPos> destinationMemory) {
        this.retryIntervalTicks = retryIntervalTicks;
        this.progressDistanceSqr = progressDistanceSqr;
        this.immediateRetries = immediateRetries;
        this.destinationMemory = destinationMemory;
    }

    @Nullable
    public MemoryModuleType<GlobalPos> destinationMemory() {
        return destinationMemory;
    }

    @Nullable
    public BlockPos activeDestination() {
        return previousDestination;
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
                openPreemptedSince = Long.MIN_VALUE;
                pendingFailureShift = 0L;
                unresolvedFailureSince = Long.MIN_VALUE;
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
            openPreemptedSince = Long.MIN_VALUE;
            pendingFailureShift = 0L;
            unresolvedFailureSince = Long.MIN_VALUE;
        }
    }

    public void resetActive() {
        if (previousDestination != null) {
            reset(previousDestination);
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
        unresolvedFailureSince = Long.MIN_VALUE;
        return true;
    }

    long stalledSince() {
        return stalledSince;
    }

    public void noteFailure(BlockPos destination, long since) {
        if (previousDestination != null && previousDestination.equals(destination)) {
            unresolvedFailureSince = since;
        }
    }

    public void clearFailure() {
        unresolvedFailureSince = Long.MIN_VALUE;
    }

    public long unresolvedFailureSince(BlockPos destination) {
        return previousDestination != null && previousDestination.equals(destination)
                ? unresolvedFailureSince
                : Long.MIN_VALUE;
    }

    public void observePreemption(long time, boolean preempted) {
        if (previousDestination == null) {
            openPreemptedSince = Long.MIN_VALUE;
            pendingFailureShift = 0L;
            unresolvedFailureSince = Long.MIN_VALUE;
            return;
        }
        if (preempted) {
            if (openPreemptedSince == Long.MIN_VALUE || time < openPreemptedSince) {
                openPreemptedSince = time;
            }
        } else if (openPreemptedSince != Long.MIN_VALUE) {
            long delta = Math.max(0L, time - openPreemptedSince);
            openPreemptedSince = Long.MIN_VALUE;
            stalledSince += delta;
            pendingFailureShift += delta;
            if (unresolvedFailureSince != Long.MIN_VALUE) {
                unresolvedFailureSince += delta;
            }
        }
    }

    public long consumeFailureShift(long time) {
        observePreemption(time, false);
        long shift = pendingFailureShift;
        pendingFailureShift = 0L;
        return shift;
    }
}
