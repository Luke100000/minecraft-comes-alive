package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ExtendedWalkTowardsTask {
    public ExtendedWalkTowardsTask() {
    }

    public static OneShot<VillagerEntityMCA> create(MemoryModuleType<GlobalPos> destination, float speed, int completionRange, int maxDistance, int maxRunTime, Predicate<VillagerEntityMCA> canGiveUp, Consumer<VillagerEntityMCA> onGiveUp) {
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
                                if (globalPos.pos().distManhattan(entity.blockPosition()) > maxDistance) {
                                    Vec3 vec3d = null;
                                    int l = 0;

                                    while (vec3d == null || (BlockPos.containing(vec3d)).distManhattan(entity.blockPosition()) > maxDistance) {
                                        vec3d = DefaultRandomPos.getPosTowards(entity, 15, 7, Vec3.atBottomCenterOf(globalPos.pos()), 1.5707963705062866);
                                        ++l;
                                        if (l == 1000) {
                                            entity.releasePoi(destination);
                                            destinationResult.erase();
                                            cantReachWalkTargetSince.set(time);
                                            return true;
                                        }
                                    }

                                    walkTarget.set(new WalkTarget(vec3d, speed, completionRange));
                                } else if (globalPos.pos().distManhattan(entity.blockPosition()) > completionRange) {
                                    walkTarget.set(new WalkTarget(globalPos.pos(), speed, completionRange));
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
