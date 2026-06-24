package net.mca.entity.ai.brain.tasks;

import net.mca.entity.VillagerEntityMCA;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.ai.brain.task.SingleTickTask;
import net.minecraft.entity.ai.brain.task.TaskTriggerer;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class ExtendedWalkTowardsTask {
    @FunctionalInterface
    public interface WalkTargetResolver {
        Optional<BlockPos> resolve(ServerWorld world, VillagerEntityMCA entity, GlobalPos destination);
    }

    public ExtendedWalkTowardsTask() {
    }

    public static SingleTickTask<VillagerEntityMCA> create(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxDistance, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp) {
        return create(destination, speed, completionRange, maxDistance, maxRunTime, canGiveUp, onGiveUp, (world, entity, globalPos) -> Optional.empty());
    }

    public static SingleTickTask<VillagerEntityMCA> create(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxDistance, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp, WalkTargetResolver walkTargetResolver) {
        return TaskTriggerer.task((context) -> {
            return context.group(
                    context.queryMemoryOptional(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE),
                    context.queryMemoryAbsent(MemoryModuleType.WALK_TARGET),
                    context.queryMemoryValue(destination)).apply(context,
                    (cantReachWalkTargetSince, walkTarget, destinationResult) -> {
                        return (world, entity, time) -> {
                            GlobalPos globalPos = context.getValue(destinationResult);
                            Optional<Long> optional = context.getOptionalValue(cantReachWalkTargetSince);
                            if (globalPos.getDimension() == world.getRegistryKey() && (optional.isEmpty() || world.getTime() - optional.get() <= (long)maxRunTime)) {
                                BlockPos targetPos = walkTargetResolver.resolve(world, entity, globalPos).orElse(globalPos.getPos());
                                if (targetPos.getManhattanDistance(entity.getBlockPos()) > maxDistance) {
                                    Vec3d vec3d = null;
                                    int l = 0;

                                    while (vec3d == null || (BlockPos.ofFloored(vec3d)).getManhattanDistance(entity.getBlockPos()) > maxDistance) {
                                        vec3d = NoPenaltyTargeting.findTo(entity, 15, 7, Vec3d.ofBottomCenter(targetPos), 1.5707963705062866);
                                        ++l;
                                        if (l == 1000) {
                                            entity.releaseTicketFor(destination);
                                            destinationResult.forget();
                                            cantReachWalkTargetSince.remember(time);
                                            return true;
                                        }
                                    }

                                    walkTarget.remember(new WalkTarget(vec3d, speed, completionRange));
                                } else if (targetPos.getManhattanDistance(entity.getBlockPos()) > completionRange) {
                                    walkTarget.remember(new WalkTarget(targetPos, speed, completionRange));
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

    public static Optional<BlockPos> findBedStandPosition(ServerWorld world, VillagerEntityMCA entity, GlobalPos destination) {
        if (entity.isSleeping()) {
            return Optional.empty();
        }

        BlockPos bedPos = destination.getPos();
        BlockState bedState = world.getBlockState(bedPos);
        if (!bedState.isIn(BlockTags.BEDS)) {
            return Optional.empty();
        }

        Direction facing = bedState.contains(BedBlock.FACING) ? bedState.get(BedBlock.FACING) : Direction.NORTH;
        BlockPos footPos;
        BlockPos headPos;
        if (bedState.contains(BedBlock.PART)) {
            footPos = bedState.get(BedBlock.PART) == BedPart.FOOT ? bedPos : bedPos.offset(facing.getOpposite());
            headPos = bedState.get(BedBlock.PART) == BedPart.HEAD ? bedPos : bedPos.offset(facing);
        } else {
            footPos = bedPos;
            headPos = bedPos;
        }

        return Stream.of(
                        footPos.offset(facing.rotateYClockwise()),
                        footPos.offset(facing.rotateYCounterclockwise()),
                        headPos.offset(facing.rotateYClockwise()),
                        headPos.offset(facing.rotateYCounterclockwise()),
                        footPos.offset(facing.getOpposite()),
                        headPos.offset(facing)
                )
                .distinct()
                .filter(candidate -> bedPos.isWithinDistance(Vec3d.ofCenter(candidate), 2.0))
                .filter(candidate -> entity.getNavigation().isValidPosition(candidate))
                .filter(candidate -> {
                    Vec3d offset = Vec3d.ofBottomCenter(candidate).subtract(entity.getPos());
                    return entity.doesNotCollide(offset.x, offset.y, offset.z);
                })
                .min(Comparator.comparingInt(candidate -> candidate.getManhattanDistance(entity.getBlockPos())));
    }
}
