package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.navigation.LongDistancePathTarget;
import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.conczin.mca.entity.ai.navigation.MultiTargetPositionTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ExtendedWalkTowardsTask {
    private static final WalkTargetResolver NO_WALK_TARGET_OVERRIDE = (world, entity, destination) -> Optional.empty();
    private static final PositionTrackerResolver NO_FINAL_TARGET_OVERRIDE = (world, entity, destination) -> Optional.empty();
    private static final Predicate<VillagerEntityMCA> ALWAYS_WALK = entity -> true;

    @FunctionalInterface
    public interface WalkTargetResolver {
        Optional<BlockPos> resolve(ServerLevel world, VillagerEntityMCA entity, GlobalPos destination);
    }

    @FunctionalInterface
    public interface PositionTrackerResolver {
        Optional<? extends PositionTracker> resolve(ServerLevel world, VillagerEntityMCA entity, GlobalPos destination);
    }

    private ExtendedWalkTowardsTask() {
    }

    public static OneShot<VillagerEntityMCA> create(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp) {
        return create(destination, speed, completionRange, maxRunTime, canGiveUp, onGiveUp, NO_WALK_TARGET_OVERRIDE, ALWAYS_WALK);
    }

    public static OneShot<VillagerEntityMCA> create(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp, Predicate<VillagerEntityMCA> shouldWalk) {
        return create(destination, speed, completionRange, maxRunTime, canGiveUp, onGiveUp, (world, entity, globalPos) -> Optional.empty(), shouldWalk);
    }

    public static OneShot<VillagerEntityMCA> create(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp, WalkTargetResolver walkTargetResolver) {
        return create(destination, speed, completionRange, maxRunTime, canGiveUp, onGiveUp, walkTargetResolver, ALWAYS_WALK);
    }

    public static OneShot<VillagerEntityMCA> create(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp, WalkTargetResolver walkTargetResolver, Predicate<VillagerEntityMCA> shouldWalk) {
        return createInternal(destination, speed, completionRange, maxRunTime, canGiveUp, onGiveUp,
                new Policy(walkTargetResolver, NO_FINAL_TARGET_OVERRIDE, shouldWalk, true));
    }

    public static OneShot<VillagerEntityMCA> createWithoutPoiRelease(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp) {
        return createInternal(
                destination,
                speed,
                completionRange,
                maxRunTime,
                canGiveUp,
                onGiveUp,
                new Policy(NO_WALK_TARGET_OVERRIDE, NO_FINAL_TARGET_OVERRIDE, ALWAYS_WALK, false)
        );
    }

    public static OneShot<VillagerEntityMCA> createWithFinalTarget(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp, PositionTrackerResolver finalTargetResolver) {
        return createInternal(destination, speed, completionRange, maxRunTime, canGiveUp, onGiveUp,
                new Policy(NO_WALK_TARGET_OVERRIDE, finalTargetResolver, ALWAYS_WALK, true));
    }

    private static OneShot<VillagerEntityMCA> createInternal(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp, Policy policy) {
        return BehaviorBuilder.create((context) -> {
            return context.group(
                    context.registered(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE),
                    context.registered(MemoryModuleType.WALK_TARGET),
                    context.present(destination)).apply(context,
                    (cantReachWalkTargetSince, walkTarget, destinationResult) -> {
                        return (world, entity, time) -> {
                            if (!policy.shouldWalk().test(entity)) {
                                return true;
                            }

                            GlobalPos globalPos = context.get(destinationResult);
                            Optional<BlockPos> resolvedTarget = policy.walkTargetResolver().resolve(world, entity, globalPos);
                            BlockPos targetPos = resolvedTarget.orElse(globalPos.pos());
                            int targetCompletionRange = resolvedTarget.isPresent() ? 0 : completionRange;
                            boolean sameDimension = globalPos.dimension() == world.dimension();

                            Optional<WalkTarget> currentWalkTarget = context.tryGet(walkTarget);
                            if (currentWalkTarget.isPresent()) {
                                PositionTracker currentTarget = currentWalkTarget.orElseThrow().getTarget();
                                if (!(currentTarget instanceof LongDistancePathTarget longDistanceTarget)) {
                                    return true;
                                }
                                if (sameDimension
                                        && longDistanceTarget.currentBlockPosition().equals(targetPos)
                                        && MCAGroundPathNavigation.requiresExtendedPath(entity, targetPos)) {
                                    return true;
                                }

                                walkTarget.erase();
                                cantReachWalkTargetSince.erase();
                                return true;
                            }

                            Optional<Long> optional = context.tryGet(cantReachWalkTargetSince);
                            long unreachableTicks = optional
                                    .map(since -> world.getGameTime() - since)
                                    .orElse(0L);
                            if (sameDimension && (optional.isEmpty() || unreachableTicks <= maxRunTime)) {
                                if (MCAGroundPathNavigation.requiresExtendedPath(entity, targetPos)) {
                                    walkTarget.set(new WalkTarget(
                                            new LongDistancePathTarget(targetPos),
                                            speed,
                                            targetCompletionRange
                                    ));
                                } else {
                                    Optional<? extends PositionTracker> finalTarget = policy.finalTargetResolver().resolve(world, entity, globalPos);
                                    if (finalTarget.isEmpty()) {
                                        if (targetPos.distManhattan(entity.blockPosition()) > targetCompletionRange) {
                                            walkTarget.set(new WalkTarget(targetPos, speed, targetCompletionRange));
                                        }
                                    } else {
                                        PositionTracker tracker = finalTarget.orElseThrow();
                                        if (!(tracker instanceof MultiTargetPositionTracker multiTarget)
                                                || !multiTarget.isReached(entity, 0)) {
                                            walkTarget.set(new WalkTarget(tracker, speed, 0));
                                        }
                                    }
                                }
                            } else {
                                if (canGiveUp.test(entity)) {
                                    if (policy.releasePoiOnGiveUp()) {
                                        entity.releasePoi(destination);
                                    }
                                    destinationResult.erase();
                                    cantReachWalkTargetSince.set(time);
                                    onGiveUp.accept(entity);
                                } else {
                                    cantReachWalkTargetSince.set(time);
                                }
                            }

                            return true;
                        };
            });
        });
    }

    private record Policy(
            WalkTargetResolver walkTargetResolver,
            PositionTrackerResolver finalTargetResolver,
            Predicate<VillagerEntityMCA> shouldWalk,
            boolean releasePoiOnGiveUp
    ) {
    }
}
