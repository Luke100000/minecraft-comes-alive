package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.conczin.mca.entity.ai.navigation.LongDistancePathTarget;
import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.conczin.mca.entity.ai.navigation.MultiTargetPositionTracker;
import net.conczin.mca.entity.ai.navigation.PathRequestDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ExtendedWalkTowardsTask {
    private static final long UNREACHABLE_PATH_RETRY_TICKS = 20L;
    private static final long STALLED_DESTINATION_RETRY_TICKS = 100L;
    private static final double STALLED_DESTINATION_PROGRESS_SQR = 16.0D;
    private static final PositionTrackerResolver NO_FINAL_TARGET_OVERRIDE = (world, entity, destination) -> Optional.empty();

    @FunctionalInterface
    public interface PositionTrackerResolver {
        Optional<? extends PositionTracker> resolve(ServerLevel world, VillagerEntityMCA entity, GlobalPos destination);
    }

    private ExtendedWalkTowardsTask() {
    }

    public static OneShot<VillagerEntityMCA> create(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp) {
        return createInternal(destination, speed, completionRange, maxRunTime, canGiveUp, onGiveUp,
                new Policy(NO_FINAL_TARGET_OVERRIDE, true));
    }

    public static OneShot<VillagerEntityMCA> createWithoutPoiRelease(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp) {
        return createInternal(
                destination,
                speed,
                completionRange,
                maxRunTime,
                canGiveUp,
                onGiveUp,
                new Policy(NO_FINAL_TARGET_OVERRIDE, false)
        );
    }

    public static OneShot<VillagerEntityMCA> createWithFinalTarget(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp, PositionTrackerResolver finalTargetResolver) {
        return createInternal(destination, speed, completionRange, maxRunTime, canGiveUp, onGiveUp,
                new Policy(finalTargetResolver, true));
    }

    private static OneShot<VillagerEntityMCA> createInternal(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp, Policy policy) {
        WalkTargetRetryGate retryGate = new WalkTargetRetryGate(
                STALLED_DESTINATION_RETRY_TICKS,
                STALLED_DESTINATION_PROGRESS_SQR,
                1);
        return BehaviorBuilder.create((context) -> {
            return context.group(
                    context.registered(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE),
                    context.registered(MemoryModuleType.WALK_TARGET),
                    context.present(destination)).apply(context,
                    (cantReachWalkTargetSince, walkTarget, destinationResult) -> {
                        return (world, entity, time) -> {
                            GlobalPos globalPos = context.get(destinationResult);
                            BlockPos targetPos = globalPos.pos();
                            boolean sameDimension = globalPos.dimension() == world.dimension();
                            WalkTargetFailureMemory.clearIfTargetChanged(entity, globalPos);

                            WalkTarget currentWalkTarget = context.tryGet(walkTarget).orElse(null);
                            if (sameDimension) {
                                noteJourneyProgress(entity, retryGate, targetPos, currentWalkTarget, time);
                            }

                            if (currentWalkTarget != null) {
                                if (!preserveCurrentWalkTarget(entity, retryGate, globalPos, currentWalkTarget)) {
                                    walkTarget.erase();
                                    WalkTargetFailureMemory.clear(entity);
                                }
                                return true;
                            }

                            if (sameDimension && policy.finalTargetResolver() == NO_FINAL_TARGET_OVERRIDE
                                    && targetPos.distManhattan(entity.blockPosition()) <= completionRange) {
                                completeJourney(entity, retryGate, targetPos);
                                return true;
                            }

                            boolean routeInvalidated = sameDimension
                                    && entity.getNavigation() instanceof MCAGroundPathNavigation navigation
                                    && navigation.consumeRetryInvalidation(targetPos);
                            if (routeInvalidated) {
                                retryGate.reset(targetPos);
                            }
                            Optional<Long> failureSince = context.tryGet(cantReachWalkTargetSince);
                            long unreachableTicks = failureSince
                                    .map(since -> world.getGameTime() - since)
                                    .orElse(0L);
                            if (sameDimension && (failureSince.isEmpty() || unreachableTicks <= maxRunTime)) {
                                if (failureSince.isPresent()
                                        && !routeInvalidated
                                        && (unreachableTicks < UNREACHABLE_PATH_RETRY_TICKS
                                        || unreachableTicks % UNREACHABLE_PATH_RETRY_TICKS != 0L)) {
                                    return true;
                                }

                                WalkTarget proposedTarget = proposeWalkTarget(world, entity, globalPos,
                                        speed, completionRange, policy);
                                if (proposedTarget == null) {
                                    // Short trips may finish below the physical-progress threshold.
                                    completeJourney(entity, retryGate, targetPos);
                                    return true;
                                }
                                if (retryGate.tryReserve(targetPos, entity.blockPosition(), time)) {
                                    walkTarget.set(proposedTarget);
                                } else {
                                    // A partial path can clear vanilla's failure timestamp even
                                    // when the villager never moves. Retain failure ownership so
                                    // a permanently blocked POI can still expire.
                                    if (failureSince.isEmpty()) {
                                        WalkTargetFailureMemory.record(entity, globalPos, retryGate.stalledSince());
                                    }
                                    PathRequestDiagnostics.recordDeferredProducerRetry(entity);
                                }
                            } else {
                                if (canGiveUp.test(entity)) {
                                    if (policy.releasePoiOnGiveUp()) {
                                        entity.releasePoi(destination);
                                    }
                                    destinationResult.erase();
                                    WalkTargetFailureMemory.record(entity, globalPos, time);
                                    onGiveUp.accept(entity);
                                } else {
                                    WalkTargetFailureMemory.record(entity, globalPos, time);
                                }
                            }

                            return true;
                        };
            });
        });
    }

    private static void noteJourneyProgress(VillagerEntityMCA entity, WalkTargetRetryGate retryGate,
                                            BlockPos destination, @Nullable WalkTarget currentTarget, long time) {
        // Only this destination's transit (or the gap between segments) can
        // refresh its failure age. Combat movement belongs to another producer.
        boolean ownsMovement = currentTarget == null
                || (currentTarget.getTarget() instanceof BlockPosTracker transit
                && transit.currentBlockPosition().equals(destination));
        if (ownsMovement && retryGate.noteProgress(destination, entity.blockPosition(), time)) {
            WalkTargetFailureMemory.clear(entity);
        }
    }

    private static boolean preserveCurrentWalkTarget(VillagerEntityMCA entity, WalkTargetRetryGate retryGate,
                                                     GlobalPos destination, WalkTarget currentTarget) {
        PositionTracker tracker = currentTarget.getTarget();
        boolean matchesDestination = destination.dimension() == entity.level().dimension()
                && tracker.currentBlockPosition().equals(destination.pos());
        if (matchesDestination && isReached(entity, currentTarget)) {
            completeJourney(entity, retryGate, destination.pos());
        }
        return !(tracker instanceof LongDistancePathTarget)
                || (matchesDestination && MCAGroundPathNavigation.requiresExtendedPath(entity, destination.pos()));
    }

    @Nullable
    private static WalkTarget proposeWalkTarget(ServerLevel world, VillagerEntityMCA entity, GlobalPos destination,
                                                float speed, int completionRange, Policy policy) {
        BlockPos targetPos = destination.pos();
        if (MCAGroundPathNavigation.requiresExtendedPath(entity, targetPos)) {
            return new WalkTarget(new LongDistancePathTarget(targetPos), speed, completionRange);
        }

        Optional<? extends PositionTracker> finalTarget = policy.finalTargetResolver().resolve(world, entity, destination);
        if (finalTarget.isPresent()) {
            PositionTracker tracker = finalTarget.orElseThrow();
            return tracker instanceof MultiTargetPositionTracker multiTarget && multiTarget.isReached(entity, 0)
                    ? null
                    : new WalkTarget(tracker, speed, 0);
        }
        return targetPos.distManhattan(entity.blockPosition()) <= completionRange
                ? null
                : new WalkTarget(targetPos, speed, completionRange);
    }

    private static boolean isReached(VillagerEntityMCA entity, WalkTarget target) {
        if (target.getTarget() instanceof MultiTargetPositionTracker multiTarget) {
            return multiTarget.isReached(entity, target.getCloseEnoughDist());
        }
        return target.getTarget() instanceof BlockPosTracker tracker
                && tracker.currentBlockPosition().distManhattan(entity.blockPosition()) <= target.getCloseEnoughDist();
    }

    private static void completeJourney(VillagerEntityMCA entity, WalkTargetRetryGate retryGate, BlockPos destination) {
        retryGate.reset(destination);
        WalkTargetFailureMemory.clear(entity);
    }

    private record Policy(
            PositionTrackerResolver finalTargetResolver,
            boolean releasePoiOnGiveUp
    ) {
    }
}
