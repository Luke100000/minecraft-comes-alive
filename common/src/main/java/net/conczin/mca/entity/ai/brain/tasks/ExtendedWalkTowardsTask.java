package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.navigation.MultiTargetPositionTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ExtendedWalkTowardsTask {
    private static final long RANDOM_POS_RETRY_COOLDOWN = 20L;
    private static final int MAX_RANDOM_POS_ATTEMPTS = 32;

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
                    context.absent(MemoryModuleType.WALK_TARGET),
                    context.present(destination)).apply(context,
                    (cantReachWalkTargetSince, walkTarget, destinationResult) -> {
                        return (world, entity, time) -> {
                            if (!shouldWalk.test(entity)) {
                                return true;
                            }

                            GlobalPos globalPos = context.get(destinationResult);
                            Optional<Long> optional = context.tryGet(cantReachWalkTargetSince);
                            if (optional.isPresent() && world.getGameTime() - optional.get() < RANDOM_POS_RETRY_COOLDOWN) {
                                return true;
                            }
                            if (globalPos.dimension() == world.dimension() && (optional.isEmpty() || world.getGameTime() - optional.get() <= (long) maxRunTime)) {
                                Optional<BlockPos> resolvedTarget = walkTargetResolver.resolve(world, entity, globalPos);
                                BlockPos targetPos = resolvedTarget.orElse(globalPos.pos());
                                int targetCompletionRange = resolvedTarget.isPresent() ? 0 : completionRange;
                                if (targetPos.distManhattan(entity.blockPosition()) > maxDistance) {
                                    Vec3 vec3d = null;
                                    for (int tries = 0; tries < MAX_RANDOM_POS_ATTEMPTS; tries++) {
                                        Vec3 candidate = DefaultRandomPos.getPosTowards(entity, 15, 7, Vec3.atBottomCenterOf(targetPos), 1.5707963705062866);
                                        if (candidate != null && BlockPos.containing(candidate).distManhattan(entity.blockPosition()) <= maxDistance) {
                                            vec3d = candidate;
                                            break;
                                        }
                                    }

                                    if (vec3d == null) {
                                        cantReachWalkTargetSince.set(time);

                                        if (canGiveUp.test(entity)) {
                                            entity.releasePoi(destination);
                                            destinationResult.erase();
                                            onGiveUp.accept(entity);
                                        }

                                        return true;
                                    }

                                    walkTarget.set(new WalkTarget(vec3d, speed, completionRange));
                                } else {
                                    Optional<? extends PositionTracker> finalTarget = finalTargetResolver.resolve(world, entity, globalPos);
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
