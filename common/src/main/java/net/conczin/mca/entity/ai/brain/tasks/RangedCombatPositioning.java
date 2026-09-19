package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.ai.RangedWeaponHelper;
import net.conczin.mca.entity.ai.brain.sensor.GuardEnemiesSensor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class RangedCombatPositioning {
    private static final int FIRING_CANDIDATE_ATTEMPTS = 8;
    private static final int FIRING_HORIZONTAL_RANGE = 8;
    private static final int FIRING_VERTICAL_RANGE = 4;
    private static final double FIRING_LATERAL_STEP = 2.0D;
    private static final int GROUP_ESCAPE_SEARCH_RADIUS = 8;
    private static final int GROUP_ESCAPE_ONWARD_LOOKAHEAD = 4;
    private static final int GROUP_ESCAPE_GRAPH_RADIUS = GROUP_ESCAPE_SEARCH_RADIUS + GROUP_ESCAPE_ONWARD_LOOKAHEAD;
    private static final int GROUP_ESCAPE_VERTICAL_RANGE = 5;
    private static final List<Direction> GROUP_ESCAPE_DIRECTIONS = List.of(
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    );
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

    static Optional<Vec3> findGroupEscapePosition(
            PathfinderMob entity,
            List<? extends LivingEntity> threats,
            double desiredDistance
    ) {
        GroupEscapeCandidates candidates = findGroupEscapeCandidates(entity, threats, desiredDistance);
        return Optional.ofNullable(candidates.safe() != null ? candidates.safe() : candidates.bestNonClosingImprovement());
    }

    static Optional<Vec3> findEmergencyEscapePosition(
            PathfinderMob entity,
            List<? extends LivingEntity> threats,
            double desiredDistance
    ) {
        GroupEscapeCandidates candidates = findGroupEscapeCandidates(entity, threats, desiredDistance);
        return Optional.ofNullable(candidates.safe() != null ? candidates.safe() : candidates.bestImprovement());
    }

    private static GroupEscapeCandidates findGroupEscapeCandidates(
            PathfinderMob entity,
            List<? extends LivingEntity> threats,
            double desiredDistance
    ) {
        if (threats.isEmpty()) {
            return new GroupEscapeCandidates(null, null, null);
        }

        Vec3 origin = entity.position();
        BlockPos originBlock = entity.blockPosition();
        double currentMinimumDistanceSquared = minimumDistanceSquared(origin, threats);
        double desiredDistanceSquared = desiredDistance * desiredDistance;
        Map<BlockPos, Integer> reachablePositions = collectReachableEscapePositions(entity, originBlock);
        Set<BlockPos> reachablePositionSet = reachablePositions.keySet();
        EscapeCandidate bestSafe = null;
        EscapeCandidate bestNonClosingImprovement = null;
        EscapeCandidate bestImprovement = null;

        for (Map.Entry<BlockPos, Integer> entry : reachablePositions.entrySet()) {
            BlockPos position = entry.getKey();
            if (position.equals(originBlock)
                    || horizontalDistanceSquared(originBlock, position) > GROUP_ESCAPE_SEARCH_RADIUS * GROUP_ESCAPE_SEARCH_RADIUS) {
                continue;
            }

            Vec3 candidatePosition = Vec3.atBottomCenterOf(position);
            double candidateMinimumDistanceSquared = minimumDistanceSquared(candidatePosition, threats);
            if (candidateMinimumDistanceSquared <= currentMinimumDistanceSquared + MIN_USEFUL_DISTANCE_GAIN) {
                continue;
            }

            int onwardSpace = countOnwardReachableSpace(originBlock, position, reachablePositionSet);
            EscapeCandidate candidate = new EscapeCandidate(
                    candidatePosition,
                    candidateMinimumDistanceSquared,
                    entry.getValue(),
                    onwardSpace
            );

            if (isBetterImprovement(candidate, bestImprovement)) {
                bestImprovement = candidate;
            }

            boolean opensEveryThreat = opensDistanceFromEveryThreat(origin, candidatePosition, threats);
            if (!opensEveryThreat) {
                continue;
            }

            if (isBetterImprovement(candidate, bestNonClosingImprovement)) {
                bestNonClosingImprovement = candidate;
            }
            if (candidateMinimumDistanceSquared >= desiredDistanceSquared && isBetterSafeCandidate(candidate, bestSafe)) {
                bestSafe = candidate;
            }
        }

        return new GroupEscapeCandidates(
                bestSafe == null ? null : bestSafe.position(),
                bestNonClosingImprovement == null ? null : bestNonClosingImprovement.position(),
                bestImprovement == null ? null : bestImprovement.position()
        );
    }

    private static Map<BlockPos, Integer> collectReachableEscapePositions(PathfinderMob entity, BlockPos origin) {
        Map<BlockPos, Integer> travelSteps = new LinkedHashMap<>();
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        travelSteps.put(origin, 0);
        frontier.add(origin);

        while (!frontier.isEmpty()) {
            BlockPos current = frontier.removeFirst();
            int nextTravelSteps = travelSteps.get(current) + 1;
            for (Direction direction : GROUP_ESCAPE_DIRECTIONS) {
                BlockPos next = resolveAdjacentEscapePosition(entity, current, direction);
                if (next == null
                        || Math.abs(next.getY() - origin.getY()) > GROUP_ESCAPE_VERTICAL_RANGE
                        || horizontalDistanceSquared(origin, next) > GROUP_ESCAPE_GRAPH_RADIUS * GROUP_ESCAPE_GRAPH_RADIUS
                        || travelSteps.containsKey(next)) {
                    continue;
                }
                travelSteps.put(next, nextTravelSteps);
                frontier.addLast(next);
            }
        }
        return travelSteps;
    }

    private static BlockPos resolveAdjacentEscapePosition(PathfinderMob entity, BlockPos current, Direction direction) {
        BlockPos adjacent = current.relative(direction);
        if (isSafeEscapePosition(entity, adjacent)) {
            return adjacent;
        }
        BlockPos above = adjacent.above();
        if (isSafeEscapePosition(entity, above)) {
            return above;
        }
        BlockPos below = adjacent.below();
        return isSafeEscapePosition(entity, below) ? below : null;
    }

    private static boolean isSafeEscapePosition(PathfinderMob entity, BlockPos position) {
        Vec3 candidate = Vec3.atBottomCenterOf(position);
        return entity.getNavigation().isStableDestination(position)
                && isWalkableDestination(entity, candidate)
                && hasStandingSpace(entity, candidate);
    }

    private static int countOnwardReachableSpace(BlockPos origin, BlockPos start, Set<BlockPos> reachablePositions) {
        int minimumHorizontalDistanceSquared = horizontalDistanceSquared(origin, start);
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<OnwardSearchNode> frontier = new ArrayDeque<>();
        visited.add(start);
        frontier.add(new OnwardSearchNode(start, 0));

        while (!frontier.isEmpty()) {
            OnwardSearchNode node = frontier.removeFirst();
            if (node.steps() >= GROUP_ESCAPE_ONWARD_LOOKAHEAD) {
                continue;
            }

            for (Direction direction : GROUP_ESCAPE_DIRECTIONS) {
                BlockPos adjacent = node.position().relative(direction);
                for (int yOffset = -1; yOffset <= 1; yOffset++) {
                    BlockPos neighbor = adjacent.offset(0, yOffset, 0);
                    if (!reachablePositions.contains(neighbor)) {
                        continue;
                    }
                    if (horizontalDistanceSquared(origin, neighbor) >= minimumHorizontalDistanceSquared
                            && visited.add(neighbor)) {
                        frontier.addLast(new OnwardSearchNode(neighbor, node.steps() + 1));
                    }
                    break;
                }
            }
        }
        return visited.size() - 1;
    }

    private static boolean isBetterSafeCandidate(EscapeCandidate candidate, EscapeCandidate currentBest) {
        if (currentBest == null || candidate.onwardSpace() != currentBest.onwardSpace()) {
            return currentBest == null || candidate.onwardSpace() > currentBest.onwardSpace();
        }
        if (candidate.travelSteps() != currentBest.travelSteps()) {
            return candidate.travelSteps() < currentBest.travelSteps();
        }
        return candidate.minimumDistanceSquared() > currentBest.minimumDistanceSquared();
    }

    private static boolean isBetterImprovement(EscapeCandidate candidate, EscapeCandidate currentBest) {
        if (currentBest == null || candidate.onwardSpace() != currentBest.onwardSpace()) {
            return currentBest == null || candidate.onwardSpace() > currentBest.onwardSpace();
        }
        if (candidate.minimumDistanceSquared() != currentBest.minimumDistanceSquared()) {
            return candidate.minimumDistanceSquared() > currentBest.minimumDistanceSquared();
        }
        return candidate.travelSteps() < currentBest.travelSteps();
    }

    private static int horizontalDistanceSquared(BlockPos first, BlockPos second) {
        int x = second.getX() - first.getX();
        int z = second.getZ() - first.getZ();
        return x * x + z * z;
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

    private record GroupEscapeCandidates(Vec3 safe, Vec3 bestNonClosingImprovement, Vec3 bestImprovement) {
    }

    private record EscapeCandidate(Vec3 position, double minimumDistanceSquared, int travelSteps, int onwardSpace) {
    }

    private record OnwardSearchNode(BlockPos position, int steps) {
    }
}
