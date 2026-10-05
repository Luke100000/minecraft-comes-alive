package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.conczin.mca.entity.ai.navigation.PersistentPathTarget;
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
                    context.registered(destination)).apply(context,
                    (cantReachWalkTargetSince, walkTarget, destinationResult) -> {
                        return (world, entity, time) -> {
                            Optional<GlobalPos> rememberedDestination = context.tryGet(destinationResult);
                            if (rememberedDestination.isEmpty()) {
                                retractOwnedWalkTarget(entity, destination)
                                        .ifPresent(ownedDestination -> retryGate.reset(ownedDestination.pos()));
                                return true;
                            }

                            GlobalPos globalPos = rememberedDestination.orElseThrow();
                            BlockPos targetPos = globalPos.pos();
                            boolean sameDimension = globalPos.dimension() == world.dimension();
                            WalkTargetFailureMemory.clearIfTargetChanged(entity, globalPos);

                            WalkTarget currentWalkTarget = context.tryGet(walkTarget).orElse(null);
                            if (sameDimension) {
                                noteJourneyProgress(entity, retryGate, destination, globalPos, currentWalkTarget, time);
                            }

                            if (currentWalkTarget != null) {
                                if (!preserveCurrentWalkTarget(entity, retryGate, destination, globalPos, currentWalkTarget, policy)) {
                                    walkTarget.erase();
                                    if (currentWalkTarget instanceof DestinationOwnedWalkTarget ownedTarget
                                            && ownedTarget.ownedBy(destination)) {
                                        WalkTargetFailureMemory.clearIfTargetMatches(entity, ownedTarget.destination());
                                    }
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

                                WalkTarget proposedTarget = proposeWalkTarget(world, entity, destination, globalPos,
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
                                            MemoryModuleType<GlobalPos> destinationMemory, GlobalPos destination,
                                            @Nullable WalkTarget currentTarget, long time) {
        BlockPos targetPos = destination.pos();
        // A flank or its return leg has not established a route to the destination.
        // Navigation clears failure itself when the detour finds a reachable path.
        if (entity.getNavigation() instanceof MCAGroundPathNavigation navigation
                && navigation.isTakingDetourTo(targetPos)) {
            return;
        }
        // Only this destination's transit (or the gap between segments) can
        // refresh its failure age. Combat movement belongs to another producer.
        boolean ownsMovement = currentTarget == null
                || currentTarget instanceof DestinationOwnedWalkTarget ownedTarget
                && ownedTarget.matches(destinationMemory, destination);
        if (ownsMovement && retryGate.noteProgress(targetPos, entity.blockPosition(), time)) {
            WalkTargetFailureMemory.clear(entity);
        }
    }

    private static boolean preserveCurrentWalkTarget(VillagerEntityMCA entity, WalkTargetRetryGate retryGate,
                                                     MemoryModuleType<GlobalPos> destinationMemory, GlobalPos destination,
                                                     WalkTarget currentTarget, Policy policy) {
        if (currentTarget instanceof DestinationOwnedWalkTarget ownedFinalTarget) {
            if (!ownedFinalTarget.ownedBy(destinationMemory)) {
                return true;
            }
            if (!ownedFinalTarget.destination().equals(destination)) {
                return false;
            }
            if (isReached(entity, currentTarget)) {
                completeJourney(entity, retryGate, destination.pos());
            }
            return !(currentTarget.getTarget() instanceof PersistentPathTarget)
                    || policy.finalTargetResolver() == NO_FINAL_TARGET_OVERRIDE
                    || MCAGroundPathNavigation.requiresExtendedPath(entity, destination.pos());
        }
        return true;
    }

    @Nullable
    private static WalkTarget proposeWalkTarget(ServerLevel world, VillagerEntityMCA entity,
                                                MemoryModuleType<GlobalPos> destinationMemory, GlobalPos destination,
                                                float speed, int completionRange, Policy policy) {
        BlockPos targetPos = destination.pos();
        if (MCAGroundPathNavigation.requiresExtendedPath(entity, targetPos)) {
            return new DestinationOwnedWalkTarget(destinationMemory, destination,
                    new PersistentPathTarget(targetPos), speed, completionRange);
        }

        Optional<? extends PositionTracker> finalTarget = policy.finalTargetResolver().resolve(world, entity, destination);
        if (finalTarget.isPresent()) {
            PositionTracker tracker = finalTarget.orElseThrow();
            return tracker instanceof MultiTargetPositionTracker multiTarget && multiTarget.isReached(entity, 0)
                    ? null
                    : new DestinationOwnedWalkTarget(destinationMemory, destination, tracker, speed, 0);
        }
        return targetPos.distManhattan(entity.blockPosition()) <= completionRange
                ? null
                : new DestinationOwnedWalkTarget(destinationMemory, destination,
                new PersistentPathTarget(targetPos), speed, completionRange);
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

    public static boolean clearOwnedWalkTarget(VillagerEntityMCA entity, MemoryModuleType<GlobalPos> destinationMemory) {
        return retractOwnedWalkTarget(entity, destinationMemory).isPresent();
    }

    private static Optional<GlobalPos> retractOwnedWalkTarget(VillagerEntityMCA entity,
                                                               MemoryModuleType<GlobalPos> destinationMemory) {
        Optional<WalkTarget> currentTarget = entity.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET);
        if (currentTarget.isEmpty()
                || !(currentTarget.orElseThrow() instanceof DestinationOwnedWalkTarget ownedTarget)
                || !ownedTarget.ownedBy(destinationMemory)) {
            return Optional.empty();
        }

        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        WalkTargetFailureMemory.clearIfTargetMatches(entity, ownedTarget.destination());
        return Optional.of(ownedTarget.destination());
    }

    private static final class DestinationOwnedWalkTarget extends WalkTarget implements WalkTargetFailureMemory.TargetIdentityOwner {
        private final MemoryModuleType<GlobalPos> destinationMemory;
        private final GlobalPos destination;

        private DestinationOwnedWalkTarget(MemoryModuleType<GlobalPos> destinationMemory, GlobalPos destination,
                                           PositionTracker target, float speed, int closeEnoughDist) {
            super(target, speed, closeEnoughDist);
            this.destinationMemory = destinationMemory;
            this.destination = destination;
        }

        private boolean ownedBy(MemoryModuleType<GlobalPos> destinationMemory) {
            return this.destinationMemory == destinationMemory;
        }

        private boolean matches(MemoryModuleType<GlobalPos> destinationMemory, GlobalPos destination) {
            return ownedBy(destinationMemory) && this.destination.equals(destination);
        }

        private GlobalPos destination() {
            return destination;
        }

        @Override
        public GlobalPos failureTarget() {
            return destination;
        }
    }
}
