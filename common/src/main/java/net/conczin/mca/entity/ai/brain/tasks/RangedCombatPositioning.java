package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.ai.RangedWeaponHelper;
import net.conczin.mca.entity.ai.brain.sensor.GuardEnemiesSensor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

final class RangedCombatPositioning {
    private static final int AWAY_HORIZONTAL_RANGE = 12;
    private static final int AWAY_VERTICAL_RANGE = 5;
    private static final int AWAY_CANDIDATE_ATTEMPTS = 8;
    private static final int FIRING_CANDIDATE_ATTEMPTS = 8;
    private static final int FIRING_HORIZONTAL_RANGE = 8;
    private static final int FIRING_VERTICAL_RANGE = 4;
    private static final double CLOSE_RANGE_VERTICAL_THREAT_DISTANCE = 2.5D;
    private static final double KITE_ENTER_DISTANCE_SQUARED = 36.0D;
    private static final double MIN_USEFUL_DISTANCE_GAIN = 0.5D;
    private static final double MAX_REPOSITION_CLOSING_DISTANCE = 0.5D;

    private RangedCombatPositioning() {
    }

    static LivingEntity nearestMovementThreat(Mob entity, LivingEntity fallback) {
        return entity.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                .flatMap(visible -> visible.findClosest(candidate ->
                        RangedWeaponHelper.isValidAttackTarget(entity, candidate)
                                && Math.abs(entity.getY() - candidate.getY()) <= CLOSE_RANGE_VERTICAL_THREAT_DISTANCE
                                && GuardEnemiesSensor.isGuardEnemy(candidate, entity)))
                .orElse(fallback);
    }

    static Optional<Vec3> findAwayPosition(PathfinderMob entity, LivingEntity threat, double desiredDistance) {
        double currentDistanceSquared = entity.distanceToSqr(threat);
        double desiredDistanceSquared = desiredDistance * desiredDistance;
        Vec3 best = null;
        double bestDistanceSquared = currentDistanceSquared;

        for (int i = 0; i < AWAY_CANDIDATE_ATTEMPTS; i++) {
            Vec3 candidate = LandRandomPos.getPosAway(entity, AWAY_HORIZONTAL_RANGE, AWAY_VERTICAL_RANGE, threat.position());
            if (candidate == null || !isWalkableDestination(entity, candidate) || !hasStandingSpace(entity, candidate)) {
                continue;
            }

            double candidateDistanceSquared = candidate.distanceToSqr(threat.position());
            if (candidateDistanceSquared <= currentDistanceSquared + MIN_USEFUL_DISTANCE_GAIN) {
                continue;
            }
            if (candidateDistanceSquared >= desiredDistanceSquared) {
                return Optional.of(candidate);
            }
            if (candidateDistanceSquared > bestDistanceSquared) {
                bestDistanceSquared = candidateDistanceSquared;
                best = candidate;
            }
        }

        return Optional.ofNullable(best);
    }

    static Optional<Vec3> findFiringPosition(
            PathfinderMob entity,
            LivingEntity target,
            LivingEntity movementThreat,
            double attackRangeSquared
    ) {
        Vec3 best = null;
        double bestTravelDistanceSquared = Double.POSITIVE_INFINITY;
        double minimumTargetDistance = Math.max(0.0D, entity.distanceTo(target) - MAX_REPOSITION_CLOSING_DISTANCE);
        double minimumTargetDistanceSquared = minimumTargetDistance * minimumTargetDistance;
        double minimumThreatDistanceSquared = 0.0D;
        if (movementThreat != null) {
            double minimumThreatDistance = Math.max(0.0D,
                    entity.distanceTo(movementThreat) - MAX_REPOSITION_CLOSING_DISTANCE);
            minimumThreatDistanceSquared = minimumThreatDistance * minimumThreatDistance;
        }

        for (int i = 0; i < FIRING_CANDIDATE_ATTEMPTS; i++) {
            Vec3 candidate = LandRandomPos.getPos(entity, FIRING_HORIZONTAL_RANGE, FIRING_VERTICAL_RANGE);
            double candidateTargetDistanceSquared = candidate == null
                    ? 0.0D
                    : candidate.distanceToSqr(target.position());
            if (candidate == null
                    || !isWalkableDestination(entity, candidate)
                    || !hasStandingSpace(entity, candidate)
                    || candidateTargetDistanceSquared > attackRangeSquared
                    || candidateTargetDistanceSquared < minimumTargetDistanceSquared
                    || movementThreat != null && (candidate.distanceToSqr(movementThreat.position()) < KITE_ENTER_DISTANCE_SQUARED
                    || candidate.distanceToSqr(movementThreat.position()) < minimumThreatDistanceSquared)
                    || !hasLineOfSight(entity, candidate, target)) {
                continue;
            }

            double travelDistanceSquared = candidate.distanceToSqr(entity.position());
            if (travelDistanceSquared < bestTravelDistanceSquared) {
                bestTravelDistanceSquared = travelDistanceSquared;
                best = candidate;
            }
        }

        return Optional.ofNullable(best);
    }

    static boolean isStrafeSideWalkable(PathfinderMob entity, float lateralDirection) {
        float sign = Math.signum(lateralDirection);
        if (sign == 0.0F) {
            return false;
        }

        float yawRadians = entity.getYRot() * (float)(Math.PI / 180.0D);
        double dx = -sign * Math.sin(yawRadians);
        double dz = sign * Math.cos(yawRadians);
        Vec3 candidate = entity.position().add(dx, 0.0D, dz);
        return isWalkableDestination(entity, candidate) && hasStandingSpace(entity, candidate);
    }

    private static boolean isWalkableDestination(PathfinderMob entity, Vec3 candidate) {
        NodeEvaluator evaluator = entity.getNavigation().getNodeEvaluator();
        return evaluator == null || evaluator.getPathType(entity, BlockPos.containing(candidate)) == PathType.WALKABLE;
    }

    private static boolean hasStandingSpace(PathfinderMob entity, Vec3 candidate) {
        Vec3 offset = candidate.subtract(entity.position());
        return entity.level().noCollision(entity, entity.getBoundingBox().move(offset));
    }

    private static boolean hasLineOfSight(PathfinderMob entity, Vec3 candidate, LivingEntity target) {
        Vec3 from = candidate.add(0.0D, entity.getEyeHeight(), 0.0D);
        return entity.level().clip(new ClipContext(
                from,
                target.getEyePosition(),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                entity
        )).getType() == HitResult.Type.MISS;
    }
}
