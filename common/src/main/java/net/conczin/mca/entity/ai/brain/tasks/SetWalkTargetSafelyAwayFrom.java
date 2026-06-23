package net.conczin.mca.entity.ai.brain.tasks;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class SetWalkTargetSafelyAwayFrom {
    private static final int CANDIDATE_ATTEMPTS = 10;

    private SetWalkTargetSafelyAwayFrom() {
    }

    public static OneShot<PathfinderMob> entity(
            MemoryModuleType<? extends Entity> memory,
            float speedModifier,
            int startDistance,
            int safeDistance,
            boolean interruptCurrentWalk
    ) {
        return BehaviorBuilder.create(
                instance -> instance.group(instance.registered(MemoryModuleType.WALK_TARGET), instance.present(memory))
                        .apply(instance, (walkTarget, walkAwayFrom) -> (level, body, timestamp) -> {
                            Optional<WalkTarget> currentTarget = instance.tryGet(walkTarget);
                            Vec3 bodyPosition = body.position();
                            Vec3 avoidPosition = instance.get(walkAwayFrom).position();
                            if (!bodyPosition.closerThan(avoidPosition, startDistance)) {
                                return false;
                            }

                            double safeDistanceSquared = safeDistance * safeDistance;
                            if (currentTarget.isPresent() && !interruptCurrentWalk) {
                                Vec3 currentPosition = currentTarget.get().getTarget().currentPosition();
                                Vec3 currentDirection = currentPosition.subtract(bodyPosition);
                                Vec3 avoidDirection = avoidPosition.subtract(bodyPosition);
                                if (currentPosition.distanceToSqr(avoidPosition) >= safeDistanceSquared
                                        && currentDirection.dot(avoidDirection) < 0.0) {
                                    return false;
                                }
                            }

                            Vec3 bestPosition = null;
                            double bestDistanceSquared = bodyPosition.distanceToSqr(avoidPosition);
                            for (int i = 0; i < CANDIDATE_ATTEMPTS; i++) {
                                Vec3 candidate = LandRandomPos.getPosAway(body, 16, 7, avoidPosition);
                                if (candidate == null) {
                                    continue;
                                }

                                double candidateDistanceSquared = candidate.distanceToSqr(avoidPosition);
                                if (candidateDistanceSquared > bestDistanceSquared) {
                                    bestPosition = candidate;
                                    bestDistanceSquared = candidateDistanceSquared;
                                    if (candidateDistanceSquared >= safeDistanceSquared) {
                                        break;
                                    }
                                }
                            }

                            if (bestPosition == null) {
                                return false;
                            }

                            walkTarget.set(new WalkTarget(bestPosition, speedModifier, 0));
                            return true;
                        })
        );
    }
}
