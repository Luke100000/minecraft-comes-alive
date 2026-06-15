package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class ExtendedWalkTowardsTask {
    @FunctionalInterface
    public interface WalkTargetResolver {
        Optional<BlockPos> resolve(ServerLevel world, VillagerEntityMCA entity, GlobalPos destination);
    }

    public ExtendedWalkTowardsTask() {
    }

    public static OneShot<VillagerEntityMCA> create(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxDistance, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp) {
        return create(destination, speed, completionRange, maxDistance, maxRunTime, canGiveUp, onGiveUp, (world, entity, globalPos) -> Optional.empty());
    }

    public static OneShot<VillagerEntityMCA> create(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxDistance, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp, WalkTargetResolver walkTargetResolver) {
        return BehaviorBuilder.create((context) -> {
            return context.group(
                    context.registered(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE),
                    context.absent(MemoryModuleType.WALK_TARGET),
                    context.present(destination)).apply(context,
                    (cantReachWalkTargetSince, walkTarget, destinationResult) -> {
                        return (world, entity, time) -> {
                            GlobalPos globalPos = context.get(destinationResult);
                            Optional<Long> optional = context.tryGet(cantReachWalkTargetSince);
                            if (globalPos.dimension() == world.dimension() && (optional.isEmpty() || world.getGameTime() - optional.get() <= (long) maxRunTime)) {
                                BlockPos targetPos = walkTargetResolver.resolve(world, entity, globalPos).orElse(globalPos.pos());
                                if (targetPos.distManhattan(entity.blockPosition()) > maxDistance) {
                                    Vec3 vec3d = null;
                                    int l = 0;

                                    while (vec3d == null || (BlockPos.containing(vec3d)).distManhattan(entity.blockPosition()) > maxDistance) {
                                        vec3d = DefaultRandomPos.getPosTowards(entity, 15, 7, Vec3.atBottomCenterOf(targetPos), 1.5707963705062866);
                                        ++l;
                                        if (l == 1000) {
                                            entity.releasePoi(destination);
                                            destinationResult.erase();
                                            cantReachWalkTargetSince.set(time);
                                            return true;
                                        }
                                    }

                                    walkTarget.set(new WalkTarget(vec3d, speed, completionRange));
                                } else if (targetPos.distManhattan(entity.blockPosition()) > completionRange) {
                                    walkTarget.set(new WalkTarget(targetPos, speed, completionRange));
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

    public static Optional<BlockPos> findBedStandPosition(ServerLevel world, VillagerEntityMCA entity, GlobalPos destination) {
        BlockPos bedPos = destination.pos();
        BlockState bedState = world.getBlockState(bedPos);
        if (!bedState.is(BlockTags.BEDS)) {
            return Optional.empty();
        }

        Direction facing = bedState.getValue(BedBlock.FACING);
        BlockPos footPos = bedState.getValue(BedBlock.PART) == BedPart.FOOT ? bedPos : bedPos.relative(facing.getOpposite());
        BlockPos headPos = bedState.getValue(BedBlock.PART) == BedPart.HEAD ? bedPos : bedPos.relative(facing);

        return Stream.of(
                        footPos.relative(facing.getClockWise()),
                        footPos.relative(facing.getCounterClockWise()),
                        headPos.relative(facing.getClockWise()),
                        headPos.relative(facing.getCounterClockWise()),
                        footPos.relative(facing.getOpposite()),
                        headPos.relative(facing)
                )
                .distinct()
                .filter(candidate -> bedPos.closerToCenterThan(Vec3.atCenterOf(candidate), 2.0))
                .filter(candidate -> entity.getNavigation().isStableDestination(candidate))
                .filter(candidate -> world.noCollision(entity, entity.getBoundingBox().move(Vec3.atBottomCenterOf(candidate).subtract(entity.position()))))
                .min(Comparator.comparingInt(candidate -> candidate.distManhattan(entity.blockPosition())));
    }
}
