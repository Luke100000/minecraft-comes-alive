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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class RangedCombatPositioning {
    private static final int AWAY_HORIZONTAL_RANGE = 12;
    private static final int AWAY_VERTICAL_RANGE = 5;
    private static final int FIRING_CANDIDATE_ATTEMPTS = 8;
    private static final int FIRING_HORIZONTAL_RANGE = 8;
    private static final int FIRING_VERTICAL_RANGE = 4;
    private static final int EMERGENCY_ESCAPE_DIRECTIONS = 16;
    private static final double[] EMERGENCY_ESCAPE_RADII = {2.0D, 4.0D, 6.0D, 8.0D};
    private static final double CLOSE_RANGE_VERTICAL_THREAT_DISTANCE = 2.5D;
    private static final double KITE_ENTER_DISTANCE_SQUARED = 36.0D;
    private static final double NEARBY_THREAT_RANGE_SQUARED = 256.0D;
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

    static List<LivingEntity> nearbyMovementThreats(Mob entity, LivingEntity fallback) {
        List<LivingEntity> threats = new ArrayList<>();
        entity.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).ifPresent(nearby -> nearby.stream()
                .filter(candidate -> isNearbyMovementThreat(entity, candidate))
                .forEach(threats::add));

        if (fallback != null
                && !threats.contains(fallback)
                && entity.distanceToSqr(fallback) <= NEARBY_THREAT_RANGE_SQUARED
                && isNearbyMovementThreat(entity, fallback)) {
            threats.add(fallback);
        }
        return threats;
    }

    static Optional<Vec3> findAwayPosition(PathfinderMob entity, LivingEntity threat, double desiredDistance) {
        double currentDistanceSquared = entity.distanceToSqr(threat);
        int horizontalRange = Math.max(AWAY_HORIZONTAL_RANGE, (int)Math.ceil(desiredDistance));
        Vec3 candidate = LandRandomPos.getPosAway(entity, horizontalRange, AWAY_VERTICAL_RANGE, threat.position());
        if (candidate == null || !isWalkableDestination(entity, candidate) || !hasStandingSpace(entity, candidate)) {
            return Optional.empty();
        }

        return candidate.distanceToSqr(threat.position()) > currentDistanceSquared + MIN_USEFUL_DISTANCE_GAIN
                ? Optional.of(candidate)
                : Optional.empty();
    }

    static Optional<Vec3> findEmergencyEscapePosition(
            PathfinderMob entity,
            List<? extends LivingEntity> threats,
            double desiredDistance
    ) {
        if (threats.isEmpty()) {
            return Optional.empty();
        }

        Vec3 origin = entity.position();
        double currentMinimumDistanceSquared = minimumDistanceSquared(origin, threats);
        double desiredDistanceSquared = desiredDistance * desiredDistance;
        Vec3 bestImprovement = null;
        double bestMinimumDistanceSquared = currentMinimumDistanceSquared;

        for (double radius : EMERGENCY_ESCAPE_RADII) {
            Vec3 bestSafeAtRadius = null;
            double bestSafeMinimumDistanceSquared = Double.NEGATIVE_INFINITY;

            for (int direction = 0; direction < EMERGENCY_ESCAPE_DIRECTIONS; direction++) {
                double angle = Math.PI * 2.0D * direction / EMERGENCY_ESCAPE_DIRECTIONS;
                Vec3 candidate = origin.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
                if (!isWalkableDestination(entity, candidate) || !hasStandingSpace(entity, candidate)) {
                    continue;
                }

                double minimumDistanceSquared = minimumDistanceSquared(candidate, threats);
                if (minimumDistanceSquared <= currentMinimumDistanceSquared + MIN_USEFUL_DISTANCE_GAIN) {
                    continue;
                }

                if (minimumDistanceSquared > bestMinimumDistanceSquared) {
                    bestMinimumDistanceSquared = minimumDistanceSquared;
                    bestImprovement = candidate;
                }

                if (minimumDistanceSquared >= desiredDistanceSquared
                        && opensDistanceFromEveryThreat(origin, candidate, threats)
                        && minimumDistanceSquared > bestSafeMinimumDistanceSquared) {
                    bestSafeMinimumDistanceSquared = minimumDistanceSquared;
                    bestSafeAtRadius = candidate;
                }
            }

            if (bestSafeAtRadius != null) {
                return Optional.of(bestSafeAtRadius);
            }
        }

        return Optional.ofNullable(bestImprovement);
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

    private static boolean isNearbyMovementThreat(Mob entity, LivingEntity candidate) {
        return candidate != entity
                && RangedWeaponHelper.isValidAttackTarget(entity, candidate)
                && Math.abs(entity.getY() - candidate.getY()) <= CLOSE_RANGE_VERTICAL_THREAT_DISTANCE
                && GuardEnemiesSensor.isGuardEnemy(candidate, entity)
                && entity.getSensing().hasLineOfSight(candidate);
    }

    private static double minimumDistanceSquared(Vec3 candidate, List<? extends LivingEntity> threats) {
        double minimum = Double.POSITIVE_INFINITY;
        for (LivingEntity threat : threats) {
            minimum = Math.min(minimum, candidate.distanceToSqr(threat.position()));
        }
        return minimum;
    }

    private static boolean opensDistanceFromEveryThreat(
            Vec3 origin,
            Vec3 candidate,
            List<? extends LivingEntity> threats
    ) {
        for (LivingEntity threat : threats) {
            if (candidate.distanceToSqr(threat.position()) <= origin.distanceToSqr(threat.position()) + MIN_USEFUL_DISTANCE_GAIN) {
                return false;
            }
        }
        return true;
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
