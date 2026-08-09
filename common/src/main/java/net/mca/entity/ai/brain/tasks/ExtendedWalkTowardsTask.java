package net.mca.entity.ai.brain.tasks;

import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.ai.brain.task.SingleTickTask;
import net.minecraft.entity.ai.brain.task.TaskTriggerer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ExtendedWalkTowardsTask {
    private static final long RANDOM_POS_RETRY_COOLDOWN = 20L;
    private static final int MAX_RANDOM_POS_ATTEMPTS = 32;
    @FunctionalInterface
    public interface WalkTargetResolver {
        Optional<BlockPos> resolve(ServerWorld world, VillagerEntityMCA entity, GlobalPos destination);
    }

    private ExtendedWalkTowardsTask() {
    }

    public static SingleTickTask<VillagerEntityMCA> create(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxDistance, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp) {
        return create(destination, speed, completionRange, maxDistance, maxRunTime, canGiveUp, onGiveUp, (world, entity, globalPos) -> Optional.empty(), entity -> true);
    }

    public static SingleTickTask<VillagerEntityMCA> create(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxDistance, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp, Predicate<VillagerEntityMCA> shouldWalk) {
        return create(destination, speed, completionRange, maxDistance, maxRunTime, canGiveUp, onGiveUp, (world, entity, globalPos) -> Optional.empty(), shouldWalk);
    }

    public static SingleTickTask<VillagerEntityMCA> create(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxDistance, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp, WalkTargetResolver walkTargetResolver) {
        return create(destination, speed, completionRange, maxDistance, maxRunTime, canGiveUp, onGiveUp, walkTargetResolver, entity -> true);
    }

    public static SingleTickTask<VillagerEntityMCA> create(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxDistance, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp, WalkTargetResolver walkTargetResolver, Predicate<VillagerEntityMCA> shouldWalk) {
        return TaskTriggerer.task((context) -> {
            return context.group(
                    context.queryMemoryOptional(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE),
                    context.queryMemoryAbsent(MemoryModuleType.WALK_TARGET),
                    context.queryMemoryValue(destination)).apply(context,
                    (cantReachWalkTargetSince, walkTarget, destinationResult) -> {
                        return (world, entity, time) -> {
                            if (!shouldWalk.test(entity)) {
                                return true;
                            }

                            GlobalPos globalPos = context.getValue(destinationResult);
                            Optional<Long> optional = context.getOptionalValue(cantReachWalkTargetSince);
                            if (optional.isPresent() && world.getTime() - optional.get() < RANDOM_POS_RETRY_COOLDOWN) {
                                return true;
                            }
                            if (globalPos.getDimension() == world.getRegistryKey() && (optional.isEmpty() || world.getTime() - optional.get() <= (long)maxRunTime)) {
                                Optional<BlockPos> resolvedTarget = walkTargetResolver.resolve(world, entity, globalPos);
                                BlockPos targetPos = resolvedTarget.orElse(globalPos.getPos());
                                int targetCompletionRange = resolvedTarget.isPresent() ? 0 : completionRange;
                                if (targetPos.getManhattanDistance(entity.getBlockPos()) > maxDistance) {
                                    Vec3d vec3d = null;
                                    for (int l = 0; l < MAX_RANDOM_POS_ATTEMPTS; l++) {
                                        Vec3d candidate = NoPenaltyTargeting.findTo(entity, 15, 7, Vec3d.ofBottomCenter(targetPos), 1.5707963705062866);
                                        if (candidate != null && BlockPos.ofFloored(candidate).getManhattanDistance(entity.getBlockPos()) <= maxDistance) {
                                            vec3d = candidate;
                                            break;
                                        }
                                    }

                                    if (vec3d == null) {
                                        cantReachWalkTargetSince.remember(time);
                                        if (canGiveUp.test(entity)) {
                                            entity.releaseTicketFor(destination);
                                            destinationResult.forget();
                                            onGiveUp.accept(entity);
                                        }
                                        return true;
                                    }

                                    walkTarget.remember(new WalkTarget(vec3d, speed, completionRange));
                                } else if (targetPos.getManhattanDistance(entity.getBlockPos()) > targetCompletionRange) {
                                    walkTarget.remember(new WalkTarget(targetPos, speed, targetCompletionRange));
                                }
                            } else {
                                if (canGiveUp.test(entity)) {
                                    entity.releaseTicketFor(destination);
                                    destinationResult.forget();
                                    cantReachWalkTargetSince.remember(time);
                                    onGiveUp.accept(entity);
                                } else {
                                    cantReachWalkTargetSince.remember(time);
                                }
                            }

                            return true;
                        };
                    });
        });
    }

}
