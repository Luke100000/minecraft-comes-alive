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
    private static final int AWAY_FALLBACK_DIRECTIONS = 32;
    private static final double[] AWAY_FALLBACK_RADII = {2.0D, 4.0D, 6.0D, 8.0D, 10.0D, 12.0D};
    private static final int FIRING_CANDIDATE_ATTEMPTS = 8;
    private static final int FIRING_HORIZONTAL_RANGE = 8;
    private static final int FIRING_VERTICAL_RANGE = 4;
    private static final double FIRING_LATERAL_STEP = 2.0D;
    private static final int EMERGENCY_ESCAPE_DIRECTIONS = 16;
    private static final double[] EMERGENCY_ESCAPE_RADII = {2.0D, 4.0D, 6.0D, 8.0D};
    private static final double NEARBY_THREAT_RANGE_SQUARED = 256.0D;
    private static final double MIN_USEFUL_DISTANCE_GAIN = 0.5D;
    private static final double MAX_REPOSITION_CLOSING_DISTANCE = 0.5D;

    private RangedCombatPositioning() {
    }

    static LivingEntity nearestMovementThreat(Mob entity, LivingEntity fallback) {
        return entity.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                .flatMap(visible -> visible.findClosest(candidate ->
                        RangedWeaponHelper.isValidAttackTarget(entity, candidate)
                                && Math.abs(entity.getY() - candidate.getY()) <= ArcherMovementTask.CLOSE_RANGE_VERTICAL_THREAT_DISTANCE
                                && GuardEnemiesSensor.isGuardEnemy(candidate, entity)))
                .orElse(fallback);
    }

    static List<LivingEntity> nearbyMovementThreats(Mob entity, LivingEntity fallback) {
        List<LivingEntity> threats = new ArrayList<>();
        entity.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).ifPresent(nearby -> nearby.stream()
                .filter(candidate -> isNearbyMovementThreat(entity, candidate))
                .filter(entity.getSensing()::hasLineOfSight)
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
        if (candidate != null
                && isWalkableDestination(entity, candidate)
                && hasStandingSpace(entity, candidate)
                && candidate.distanceToSqr(threat.position()) > currentDistanceSquared + MIN_USEFUL_DISTANCE_GAIN) {
            return Optional.of(candidate);
        }

        return findAwayFallbackPosition(entity, threat, desiredDistance, currentDistanceSquared);
    }

    private static Optional<Vec3> findAwayFallbackPosition(
            PathfinderMob entity,
            LivingEntity threat,
            double desiredDistance,
            double currentDistanceSquared
    ) {
        Vec3 origin = entity.position();
        double desiredDistanceSquared = desiredDistance * desiredDistance;
        Vec3 bestImprovement = null;
        double bestDistanceSquared = currentDistanceSquared;

        for (double radius : AWAY_FALLBACK_RADII) {
            Vec3 bestSafeAtRadius = null;
            double bestSafeDistanceSquared = Double.NEGATIVE_INFINITY;

            for (int direction = 0; direction < AWAY_FALLBACK_DIRECTIONS; direction++) {
                double angle = Math.PI * 2.0D * direction / AWAY_FALLBACK_DIRECTIONS;
                Vec3 candidate = origin.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
                if (!isWalkableDestination(entity, candidate) || !hasStandingSpace(entity, candidate)) {
                    continue;
                }

                double candidateDistanceSquared = candidate.distanceToSqr(threat.position());
                if (candidateDistanceSquared <= currentDistanceSquared + MIN_USEFUL_DISTANCE_GAIN) {
                    continue;
                }

                if (candidateDistanceSquared > bestDistanceSquared) {
                    bestDistanceSquared = candidateDistanceSquared;
                    bestImprovement = candidate;
                }

                if (candidateDistanceSquared >= desiredDistanceSquared
                        && candidateDistanceSquared > bestSafeDistanceSquared) {
                    bestSafeDistanceSquared = candidateDistanceSquared;
                    bestSafeAtRadius = candidate;
                }
            }

            if (bestSafeAtRadius != null) {
                return Optional.of(bestSafeAtRadius);
            }
        }

        return Optional.ofNullable(bestImprovement);
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

        Optional<Vec3> preferred = findPreferredLateralFiringPosition(
                entity,
                target,
                movementThreat,
                attackRangeSquared,
                minimumTargetDistanceSquared,
                minimumThreatDistanceSquared
        );
        if (preferred.isPresent()) {
            return preferred;
        }

        for (int i = 0; i < FIRING_CANDIDATE_ATTEMPTS; i++) {
            Vec3 candidate = LandRandomPos.getPos(entity, FIRING_HORIZONTAL_RANGE, FIRING_VERTICAL_RANGE);
            if (candidate == null
                    || !isValidFiringPosition(
                    entity,
                    target,
                    movementThreat,
                    attackRangeSquared,
                    minimumTargetDistanceSquared,
                    minimumThreatDistanceSquared,
                    candidate
            )) {
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

    private static Optional<Vec3> findPreferredLateralFiringPosition(
            PathfinderMob entity,
            LivingEntity target,
            LivingEntity movementThreat,
            double attackRangeSquared,
            double minimumTargetDistanceSquared,
            double minimumThreatDistanceSquared
    ) {
        Vec3 towardTarget = target.position().subtract(entity.position()).multiply(1.0D, 0.0D, 1.0D);
        if (towardTarget.lengthSqr() < 1.0E-6D) {
            return Optional.empty();
        }

        Vec3 lateral = new Vec3(-towardTarget.z, 0.0D, towardTarget.x).normalize();
        double preferredSign = (entity.getUUID().getLeastSignificantBits() & 1L) == 0L ? 1.0D : -1.0D;
        for (double distance = FIRING_LATERAL_STEP; distance <= FIRING_HORIZONTAL_RANGE; distance += FIRING_LATERAL_STEP) {
            Vec3 preferredCandidate = entity.position().add(lateral.scale(distance * preferredSign));
            if (isValidFiringPosition(
                    entity,
                    target,
                    movementThreat,
                    attackRangeSquared,
                    minimumTargetDistanceSquared,
                    minimumThreatDistanceSquared,
                    preferredCandidate
            )) {
                return Optional.of(preferredCandidate);
            }

            Vec3 oppositeCandidate = entity.position().add(lateral.scale(-distance * preferredSign));
            if (isValidFiringPosition(
                    entity,
                    target,
                    movementThreat,
                    attackRangeSquared,
                    minimumTargetDistanceSquared,
                    minimumThreatDistanceSquared,
                    oppositeCandidate
            )) {
                return Optional.of(oppositeCandidate);
            }
        }
        return Optional.empty();
    }

    private static boolean isValidFiringPosition(
            PathfinderMob entity,
            LivingEntity target,
            LivingEntity movementThreat,
            double attackRangeSquared,
            double minimumTargetDistanceSquared,
            double minimumThreatDistanceSquared,
            Vec3 candidate
    ) {
        if (!isWalkableDestination(entity, candidate) || !hasStandingSpace(entity, candidate)) {
            return false;
        }

        double candidateTargetDistanceSquared = candidate.distanceToSqr(target.position());
        if (candidateTargetDistanceSquared > attackRangeSquared
                || candidateTargetDistanceSquared < minimumTargetDistanceSquared) {
            return false;
        }

        if (movementThreat != null) {
            double candidateThreatDistanceSquared = candidate.distanceToSqr(movementThreat.position());
            if (candidateThreatDistanceSquared < ArcherMovementTask.KITE_ENTER_DISTANCE_SQUARED
                    || candidateThreatDistanceSquared < minimumThreatDistanceSquared) {
                return false;
            }
        }

        return hasLineOfSight(entity, candidate, target);
    }

    static boolean isStrafeSideWalkable(PathfinderMob entity, float lateralDirection) {
        float sign = Math.signum(lateralDirection);
        if (sign == 0.0F) {
            return false;
        }

        float yawRadians = entity.getYRot() * (float)(Math.PI / 180.0D);
        double dx = -sign * Math.sin(yawRadians);
        double dz = sign * Math.cos(yawRadians);
        return isMovementDirectionWalkable(entity, new Vec3(dx, 0.0D, dz));
    }

    static boolean isMovementDirectionWalkable(PathfinderMob entity, Vec3 direction) {
        Vec3 horizontal = new Vec3(direction.x, 0.0D, direction.z);
        if (horizontal.lengthSqr() < 1.0E-6D) {
            return false;
        }

        Vec3 candidate = entity.position().add(horizontal.normalize());
        return isWalkableDestination(entity, candidate) && hasStandingSpace(entity, candidate);
    }

    private static boolean isNearbyMovementThreat(Mob entity, LivingEntity candidate) {
        return candidate != entity
                && RangedWeaponHelper.isValidAttackTarget(entity, candidate)
                && Math.abs(entity.getY() - candidate.getY()) <= ArcherMovementTask.CLOSE_RANGE_VERTICAL_THREAT_DISTANCE
                && GuardEnemiesSensor.isGuardEnemy(candidate, entity);
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
