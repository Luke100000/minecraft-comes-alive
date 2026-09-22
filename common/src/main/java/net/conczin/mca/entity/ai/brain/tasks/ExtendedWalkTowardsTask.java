package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.navigation.LongDistancePathTarget;
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

    public static OneShot<VillagerEntityMCA> create(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxDistance, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp) {
        return create(destination, speed, completionRange, maxDistance, maxRunTime, canGiveUp, onGiveUp, (world, entity, globalPos) -> Optional.empty(), entity -> true);
    }

    public static OneShot<VillagerEntityMCA> create(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxDistance, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp, Predicate<VillagerEntityMCA> shouldWalk) {
        return create(destination, speed, completionRange, maxDistance, maxRunTime, canGiveUp, onGiveUp, (world, entity, globalPos) -> Optional.empty(), shouldWalk);
    }

    public static OneShot<VillagerEntityMCA> create(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxDistance, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp, WalkTargetResolver walkTargetResolver) {
        return create(destination, speed, completionRange, maxDistance, maxRunTime, canGiveUp, onGiveUp, walkTargetResolver, entity -> true);
    }

    public static OneShot<VillagerEntityMCA> create(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxDistance, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp, WalkTargetResolver walkTargetResolver, Predicate<VillagerEntityMCA> shouldWalk) {
        return createInternal(destination, speed, completionRange, maxDistance, maxRunTime, canGiveUp, onGiveUp, walkTargetResolver, (world, entity, globalPos) -> Optional.empty(), shouldWalk);
    }

    public static OneShot<VillagerEntityMCA> createWithFinalTarget(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxDistance, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp, PositionTrackerResolver finalTargetResolver) {
        return createInternal(destination, speed, completionRange, maxDistance, maxRunTime, canGiveUp, onGiveUp, (world, entity, globalPos) -> Optional.empty(), finalTargetResolver, entity -> true);
    }

    private static OneShot<VillagerEntityMCA> createInternal(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxDistance, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp, WalkTargetResolver walkTargetResolver, PositionTrackerResolver finalTargetResolver, Predicate<VillagerEntityMCA> shouldWalk) {
        return BehaviorBuilder.create((context) -> {
            return context.group(
                    context.registered(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE),
                    context.registered(MemoryModuleType.WALK_TARGET),
                    context.present(destination)).apply(context,
                    (cantReachWalkTargetSince, walkTarget, destinationResult) -> {
                        return (world, entity, time) -> {
                            if (!shouldWalk.test(entity)) {
                                return true;
                            }

                            GlobalPos globalPos = context.get(destinationResult);
                            Optional<BlockPos> resolvedTarget = walkTargetResolver.resolve(world, entity, globalPos);
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
                                        && LongDistancePathTarget.isNeeded(entity, targetPos)) {
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
                                if (LongDistancePathTarget.isNeeded(entity, targetPos)) {
                                    walkTarget.set(new WalkTarget(
                                            new LongDistancePathTarget(targetPos, maxDistance),
                                            speed,
                                            targetCompletionRange
                                    ));
                                } else {
                                    Optional<? extends PositionTracker> finalTarget = finalTargetResolver.resolve(world, entity, globalPos);
                                    if (finalTarget.isEmpty()) {
                                        if (targetPos.distManhattan(entity.blockPosition()) > targetCompletionRange) {
                                            walkTarget.set(new WalkTarget(targetPos, speed, targetCompletionRange));
                                        } else if (currentWalkTarget.isPresent()) {
                                            walkTarget.erase();
                                        }
                                    } else {
                                        PositionTracker tracker = finalTarget.orElseThrow();
                                        if (!(tracker instanceof MultiTargetPositionTracker multiTarget)
                                                || !multiTarget.isReached(entity, 0)) {
                                            walkTarget.set(new WalkTarget(tracker, speed, 0));
                                        } else if (currentWalkTarget.isPresent()) {
                                            walkTarget.erase();
                                        }
                                    }
                                }
                            } else {
                                if (currentWalkTarget.isPresent()) {
                                    walkTarget.erase();
                                }
                                if (canGiveUp.test(entity)) {
                                    entity.releasePoi(destination);
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
}
