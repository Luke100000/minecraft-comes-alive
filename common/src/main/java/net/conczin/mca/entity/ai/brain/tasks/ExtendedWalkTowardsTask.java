package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.conczin.mca.entity.ai.navigation.MultiTargetPositionTracker;
import net.conczin.mca.entity.ai.navigation.PathRequestDiagnostics;
import net.conczin.mca.entity.ai.navigation.PersistentPathTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
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
        Object gateKey = new Object();
        return BehaviorBuilder.create((context) -> {
            return context.group(
                    context.registered(MemoryModuleType.WALK_TARGET),
                    context.registered(destination)).apply(context,
                    (walkTarget, destinationResult) -> {
                        return (world, entity, time) -> {
                            WalkTargetRetryGate retryGate = entity.getVillagerBrain().getOrCreateRetryGate(
                                    gateKey,
                                    () -> new WalkTargetRetryGate(
                                            STALLED_DESTINATION_RETRY_TICKS,
                                            STALLED_DESTINATION_PROGRESS_SQR,
                                            1,
                                            destination));
                            Optional<GlobalPos> rememberedDestination = context.tryGet(destinationResult);
                            if (rememberedDestination.isEmpty()) {
                                retractOwnedWalkTarget(entity, destination, true)
                                        .ifPresent(ownedDestination -> WalkTargetFailureMemory.clearForProducer(entity, destination, ownedDestination));
                                if (retryGate.assignment() != null) {
                                    WalkTargetFailureMemory.clearForProducer(entity, destination, retryGate.assignment());
                                }
                                return true;
                            }

                            GlobalPos globalPos = rememberedDestination.orElseThrow();
                            if (retryGate.assignment() != null && !globalPos.equals(retryGate.assignment())) {
                                WalkTargetFailureMemory.clearForProducer(entity, destination, retryGate.assignment());
                            }
                            retryGate.bindAssignment(globalPos);
                            BlockPos targetPos = globalPos.pos();
                            boolean sameDimension = globalPos.dimension() == world.dimension();
                            WalkTarget currentWalkTarget = context.tryGet(walkTarget).orElse(null);
                            if (currentWalkTarget instanceof DestinationOwnedWalkTarget owned
                                    && owned.ownedBy(destination) && !owned.matches(destination, globalPos)) {
                                retractOwnedMovement(entity, destination);
                                WalkTargetFailureMemory.clearForProducer(entity, destination, owned.destination());
                                return true;
                            }
                            if (currentWalkTarget != null && !(currentWalkTarget instanceof DestinationOwnedWalkTarget owned
                                    && owned.ownedBy(destination))) {
                                // The current producer also owns any failure recorded for its movement.
                                retryGate.observePreemption(time, true);
                                return true;
                            }
                            WalkTargetFailureMemory.resumeTarget(entity, globalPos, retryGate, time);
                            if (sameDimension) {
                                noteJourneyProgress(entity, retryGate, destination, globalPos, currentWalkTarget, time);
                            }
                            // BehaviorBuilder captures memory values before this trigger runs.
                            Optional<Long> failureSince = entity.getBrain()
                                    .getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
                            long unreachableTicks = failureSince
                                    .map(since -> time - since)
                                    .orElse(0L);

                            boolean expiredOwnedFailure = sameDimension
                                    && currentWalkTarget instanceof DestinationOwnedWalkTarget ownedTarget
                                    && ownedTarget.matches(destination, globalPos)
                                    && failureSince.isPresent()
                                    && unreachableTicks > maxRunTime
                                    && WalkTargetFailureMemory.hasFailureForCommand(entity, currentWalkTarget);
                            if (expiredOwnedFailure) {
                                // A persistent owned journey may retain WALK_TARGET past its failure deadline.
                                // Release that command so the ordinary give-up policy can run below.
                                retractOwnedMovement(entity, destination);
                            } else if (currentWalkTarget != null) {
                                if (!preserveCurrentWalkTarget(world, entity, retryGate, destination, globalPos, currentWalkTarget, policy)) {
                                    retractOwnedMovement(entity, destination);
                                }
                                return true;
                            }

                            if (sameDimension && policy.finalTargetResolver() == NO_FINAL_TARGET_OVERRIDE
                                    && targetPos.distManhattan(entity.blockPosition()) <= completionRange) {
                                completeJourney(entity, destination, globalPos);
                                return true;
                            }

                            boolean routeInvalidated = sameDimension
                                    && entity.getNavigation() instanceof MCAGroundPathNavigation navigation
                                    && navigation.consumeRetryInvalidation(targetPos);
                            if (routeInvalidated) {
                                retryGate.permitRetryAfterInvalidation(targetPos, time);
                            }
                            if (sameDimension && (failureSince.isEmpty() || unreachableTicks <= maxRunTime)) {
                                if (failureSince.isPresent()
                                        && !routeInvalidated
                                        && unreachableTicks < UNREACHABLE_PATH_RETRY_TICKS) {
                                    return true;
                                }

                                // ConditionalTask can run this producer every other tick.
                                // Exact failure-age multiples can then be missed forever;
                                // the retry gate owns the interval between attempts.
                                WalkTarget proposedTarget = proposeWalkTarget(world, entity, destination, globalPos,
                                        speed, completionRange, policy);
                                if (proposedTarget == null) {
                                    // Short trips may finish below the physical-progress threshold.
                                    completeJourney(entity, destination, globalPos);
                                    return true;
                                }
                                if (retryGate.tryReserve(targetPos, entity.blockPosition(), time)) {
                                    walkTarget.set(proposedTarget);
                                } else {
                                    // A partial path can clear vanilla's failure timestamp even
                                    // when the villager never moves. Retain failure ownership so
                                    // a permanently blocked POI can still expire.
                                    if (failureSince.isEmpty()) {
                                        WalkTargetFailureMemory.recordForProducer(entity, destination, globalPos, retryGate.stalledSince());
                                    }
                                    PathRequestDiagnostics.recordDeferredProducerRetry(entity);
                                }
                            } else {
                                if (canGiveUp.test(entity)) {
                                    retractOwnedMovement(entity, destination);
                                    WalkTargetFailureMemory.clearForProducer(entity, destination, globalPos);
                                    if (policy.releasePoiOnGiveUp()) {
                                        entity.releasePoi(destination);
                                    }
                                    destinationResult.erase();
                                    onGiveUp.accept(entity);
                                } else {
                                    // Giving up is forbidden by this producer's policy; start another timeout window.
                                    WalkTargetFailureMemory.clearFailureForProducer(entity, destination, globalPos);
                                    WalkTargetFailureMemory.recordForProducer(entity, destination, globalPos, time);
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
            WalkTargetFailureMemory.clearFailureForProducer(entity, destinationMemory, destination);
        }
    }

    private static boolean preserveCurrentWalkTarget(ServerLevel world, VillagerEntityMCA entity, WalkTargetRetryGate retryGate,
                                                     MemoryModuleType<GlobalPos> destinationMemory, GlobalPos destination,
                                                     WalkTarget currentTarget, Policy policy) {
        if (currentTarget instanceof DestinationOwnedWalkTarget ownedFinalTarget) {
            if (!ownedFinalTarget.ownedBy(destinationMemory)) {
                return true;
            }
            if (!ownedFinalTarget.destination().equals(destination)) {
                return false;
            }
            if (MultiTargetPositionTracker.isReached(entity, currentTarget.getTarget(), currentTarget.getCloseEnoughDist())) {
                completeJourney(entity, destinationMemory, destination);
            }
            return !(currentTarget.getTarget() instanceof PersistentPathTarget)
                    || policy.finalTargetResolver() == NO_FINAL_TARGET_OVERRIDE
                    || MCAGroundPathNavigation.requiresExtendedPath(entity, destination.pos())
                    || policy.finalTargetResolver().resolve(world, entity, destination).isEmpty();
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
            return MultiTargetPositionTracker.isReached(entity, tracker, 0)
                    ? null
                    : new DestinationOwnedWalkTarget(destinationMemory, destination, tracker, speed, 0);
        }
        return targetPos.distManhattan(entity.blockPosition()) <= completionRange
                ? null
                : new DestinationOwnedWalkTarget(destinationMemory, destination,
                new PersistentPathTarget(targetPos), speed, completionRange);
    }

    private static void completeJourney(VillagerEntityMCA entity, MemoryModuleType<GlobalPos> memory, GlobalPos destination) {
        WalkTargetFailureMemory.clearForProducer(entity, memory, destination);
    }

    private record Policy(
            PositionTrackerResolver finalTargetResolver,
            boolean releasePoiOnGiveUp
    ) {
    }

    public static boolean clearOwnedWalkTarget(VillagerEntityMCA entity, MemoryModuleType<GlobalPos> destinationMemory) {
        return retractOwnedWalkTarget(entity, destinationMemory, false).isPresent();
    }

    public static boolean retractOwnedMovement(VillagerEntityMCA entity, MemoryModuleType<GlobalPos> destinationMemory) {
        return retractOwnedWalkTarget(entity, destinationMemory, true).isPresent();
    }

    /** An active POI producer must keep observing its own WALK_TARGET, but never a foreign one. */
    public static boolean ownsWalkTarget(VillagerEntityMCA entity, MemoryModuleType<GlobalPos> destinationMemory) {
        return entity.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                .filter(target -> target instanceof DestinationOwnedWalkTarget owned
                        && owned.ownedBy(destinationMemory))
                .isPresent();
    }

    /**
     * Persistence is an attribute of the logical journey, not necessarily its
     * physical PositionTracker: HOME may finish through a multi-position bed approach.
     * Other transient trackers must still respect vanilla sink timeouts.
     */
    public static boolean isPersistentJourney(WalkTarget target) {
        return target != null && (target.getTarget() instanceof PersistentPathTarget
                || target instanceof WalkTargetFailureMemory.TargetIdentityOwner
                && target.getTarget() instanceof MultiTargetPositionTracker);
    }

    private static Optional<GlobalPos> retractOwnedWalkTarget(VillagerEntityMCA entity,
                                                               MemoryModuleType<GlobalPos> destinationMemory,
                                                               boolean retractPath) {
        Optional<WalkTarget> currentTarget = entity.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET);
        if (currentTarget.isEmpty()
                || !(currentTarget.orElseThrow() instanceof DestinationOwnedWalkTarget ownedTarget)
                || !ownedTarget.ownedBy(destinationMemory)) {
            return Optional.empty();
        }

        if (retractPath && entity.getNavigation() instanceof MCAGroundPathNavigation navigation) {
            navigation.retractWalkTarget(ownedTarget);
        }
        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        WalkTargetFailureMemory.clearFailureForProducer(entity, destinationMemory, ownedTarget.destination());
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

        @Override
        public MemoryModuleType<GlobalPos> failureDestinationMemory() {
            return destinationMemory;
        }
    }
}
