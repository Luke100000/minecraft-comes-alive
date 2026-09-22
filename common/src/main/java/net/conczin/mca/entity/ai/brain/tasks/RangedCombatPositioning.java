package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.ai.RangedWeaponHelper;
import net.conczin.mca.entity.ai.brain.sensor.GuardEnemiesSensor;
import net.conczin.mca.entity.ai.navigation.CombatEscapePositionTracker;
import net.conczin.mca.entity.ai.navigation.MultiTargetPositionTracker;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class RangedCombatPositioning {
    private static final int FIRING_CANDIDATE_ATTEMPTS = 8;
    private static final int FIRING_HORIZONTAL_RANGE = 8;
    private static final int FIRING_VERTICAL_RANGE = 4;
    private static final double FIRING_LATERAL_STEP = 2.0D;
    private static final int ESCAPE_SEARCH_RADIUS = 8;
    private static final int ESCAPE_ONWARD_LOOKAHEAD = 4;
    private static final int ESCAPE_GRAPH_RADIUS = ESCAPE_SEARCH_RADIUS + ESCAPE_ONWARD_LOOKAHEAD;
    private static final int ESCAPE_VERTICAL_RANGE = 5;
    private static final int ESCAPE_TARGETS_PER_SECTOR = 2;
    private static final int ESCAPE_TARGET_LIMIT = 12;
    private static final int ESCAPE_ONWARD_SCORE_SLACK = 1;
    private static final List<Direction> HORIZONTAL_DIRECTIONS = List.of(
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    );
    private static final double NEARBY_THREAT_RANGE_SQUARED = 256.0D;
    private static final double MIN_USEFUL_DISTANCE_GAIN = 0.5D;
    private static final double MAX_REPOSITION_CLOSING_DISTANCE = 0.5D;
    private static final double MAX_APPROACH_BACKTRACK = 0.5D;
    private static final int[] APPROACH_FORWARD_OFFSETS = {0, 2, 4};
    private static final int[] APPROACH_LATERAL_OFFSETS = {-4, 4, -6, 6};
    private static final int[] CANDIDATE_VERTICAL_OFFSETS = {0, 1, -1, 2, -2};
    private static final double STRAFE_PROBE_STEP = 0.5D;
    private static final double MIN_STRAFE_CLEARANCE = 1.5D;
    private static final double MAX_STRAFE_CLEARANCE = 2.5D;

    private RangedCombatPositioning() {
    }

    static ThreatContext collectThreatContext(Mob entity, LivingEntity attackTarget) {
        List<LivingEntity> hazards = new ArrayList<>();
        List<LivingEntity> escapeThreats = new ArrayList<>();
        entity.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).ifPresent(nearby -> {
            for (LivingEntity candidate : nearby) {
                if (!isNearbyMovementThreat(entity, candidate)) {
                    continue;
                }
                if (entity.getSensing().hasLineOfSight(candidate)) {
                    addEscapeThreat(hazards, candidate);
                }
                if (isEscapeDriver(entity, candidate, attackTarget)) {
                    addEscapeThreat(escapeThreats, candidate);
                }
            }
        });

        if (attackTarget != null
                && entity.distanceToSqr(attackTarget) <= NEARBY_THREAT_RANGE_SQUARED
                && isNearbyMovementThreat(entity, attackTarget)) {
            addEscapeThreat(hazards, attackTarget);
            addEscapeThreat(escapeThreats, attackTarget);
        }
        addEscapeThreatIfNearby(entity, escapeThreats, entity.getLastHurtByMob(), attackTarget);

        LivingEntity nearestEscapeThreat = null;
        double nearestDistanceSquared = Double.POSITIVE_INFINITY;
        for (LivingEntity threat : escapeThreats) {
            double distanceSquared = entity.distanceToSqr(threat);
            if (distanceSquared < nearestDistanceSquared) {
                nearestEscapeThreat = threat;
                nearestDistanceSquared = distanceSquared;
            }
        }
        return new ThreatContext(List.copyOf(hazards), List.copyOf(escapeThreats), nearestEscapeThreat);
    }

    private static void addEscapeThreatIfNearby(
            Mob entity,
            List<LivingEntity> escapeThreats,
            LivingEntity candidate,
            LivingEntity attackTarget
    ) {
        if (candidate != null
                && entity.distanceToSqr(candidate) <= NEARBY_THREAT_RANGE_SQUARED
                && isNearbyMovementThreat(entity, candidate)
                && isEscapeDriver(entity, candidate, attackTarget)) {
            addEscapeThreat(escapeThreats, candidate);
        }
    }

    private static void addEscapeThreat(List<LivingEntity> escapeThreats, LivingEntity candidate) {
        if (!escapeThreats.contains(candidate)) {
            escapeThreats.add(candidate);
        }
    }

    static Optional<MultiTargetPositionTracker> findGroupEscapeTarget(
            PathfinderMob entity,
            List<? extends LivingEntity> escapeThreats,
            List<? extends LivingEntity> hazards,
            double desiredDistance
    ) {
        return findEscapeTarget(entity, escapeThreats, hazards, desiredDistance, false);
    }

    static Optional<MultiTargetPositionTracker> findEmergencyEscapeTarget(
            PathfinderMob entity,
            List<? extends LivingEntity> escapeThreats,
            List<? extends LivingEntity> hazards,
            double desiredDistance
    ) {
        return findEscapeTarget(entity, escapeThreats, hazards, desiredDistance, true);
    }

    private static Optional<MultiTargetPositionTracker> findEscapeTarget(
            PathfinderMob entity,
            List<? extends LivingEntity> escapeThreats,
            List<? extends LivingEntity> hazards,
            double desiredDistance,
            boolean emergency
    ) {
        if (escapeThreats.isEmpty()) {
            return Optional.empty();
        }

        Vec3 origin = entity.position();
        BlockPos originBlock = entity.blockPosition();
        double currentMinimumDistanceSquared = minimumDistanceSquared(origin, escapeThreats);
        double desiredDistanceSquared = desiredDistance * desiredDistance;
        List<EscapeCandidate> safeCandidates = new ArrayList<>();
        List<EscapeCandidate> nonClosingCandidates = new ArrayList<>();
        List<EscapeCandidate> emergencyFallbackCandidates = new ArrayList<>();
        Map<BlockPos, Integer> reachablePositions = collectReachableEscapePositions(entity, originBlock);
        Set<BlockPos> reachablePositionSet = reachablePositions.keySet();

        for (Map.Entry<BlockPos, Integer> entry : reachablePositions.entrySet()) {
            BlockPos position = entry.getKey();
            if (position.equals(originBlock)
                    || horizontalDistanceSquared(originBlock, position) > ESCAPE_SEARCH_RADIUS * ESCAPE_SEARCH_RADIUS) {
                continue;
            }

            Vec3 candidatePosition = Vec3.atBottomCenterOf(position);
            double candidateMinimumDistanceSquared = minimumDistanceSquared(candidatePosition, escapeThreats);
            if (candidateMinimumDistanceSquared <= currentMinimumDistanceSquared + MIN_USEFUL_DISTANCE_GAIN) {
                continue;
            }

            boolean opensEveryThreat = opensDistanceFromEveryThreat(origin, candidatePosition, escapeThreats);
            EscapeCandidate candidate = new EscapeCandidate(
                    candidatePosition,
                    candidateMinimumDistanceSquared,
                    minimumDistanceSquared(candidatePosition, hazards),
                    entry.getValue(),
                    countOnwardReachableSpace(originBlock, position, reachablePositionSet),
                    position.getY() - originBlock.getY()
            );
            boolean preservesHazards = preservesHazardClearance(origin, candidatePosition, hazards)
                    && preservesHazardClearanceAlongSegment(origin, candidatePosition, hazards);

            if (preservesHazards && opensEveryThreat && candidateMinimumDistanceSquared >= desiredDistanceSquared) {
                safeCandidates.add(candidate);
            } else if (preservesHazards && opensEveryThreat) {
                nonClosingCandidates.add(candidate);
            } else if (emergency) {
                emergencyFallbackCandidates.add(candidate);
            }
        }

        Comparator<EscapeCandidate> safeComparator = Comparator
                .comparingInt(EscapeCandidate::onwardSpace).reversed()
                .thenComparing(Comparator.comparingDouble(EscapeCandidate::minimumDistanceSquared).reversed())
                .thenComparing(Comparator.comparingDouble(EscapeCandidate::minimumHazardDistanceSquared).reversed())
                .thenComparingInt(EscapeCandidate::travelSteps)
                .thenComparing(Comparator.comparingInt(EscapeCandidate::elevationGain).reversed());
        Comparator<EscapeCandidate> emergencyComparator = Comparator
                .comparingDouble(EscapeCandidate::minimumDistanceSquared).reversed()
                .thenComparing(Comparator.comparingDouble(EscapeCandidate::minimumHazardDistanceSquared).reversed())
                .thenComparing(Comparator.comparingInt(EscapeCandidate::onwardSpace).reversed())
                .thenComparingInt(EscapeCandidate::travelSteps)
                .thenComparing(Comparator.comparingInt(EscapeCandidate::elevationGain).reversed());
        safeCandidates.sort(safeComparator);
        nonClosingCandidates.sort(safeComparator);
        emergencyFallbackCandidates.sort(emergencyComparator);
        List<EscapeCandidate> selected = !safeCandidates.isEmpty()
                ? safeCandidates
                : !nonClosingCandidates.isEmpty()
                ? nonClosingCandidates
                : emergencyFallbackCandidates;
        return createEscapeTarget(origin, selected);
    }

    private static Map<BlockPos, Integer> collectReachableEscapePositions(PathfinderMob entity, BlockPos origin) {
        Map<BlockPos, Integer> travelSteps = new LinkedHashMap<>();
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        travelSteps.put(origin, 0);
        frontier.add(origin);

        while (!frontier.isEmpty()) {
            BlockPos current = frontier.removeFirst();
            int nextTravelSteps = travelSteps.get(current) + 1;
            for (Direction direction : HORIZONTAL_DIRECTIONS) {
                BlockPos next = resolveAdjacentEscapePosition(entity, current, direction);
                if (next == null
                        || Math.abs(next.getY() - origin.getY()) > ESCAPE_VERTICAL_RANGE
                        || horizontalDistanceSquared(origin, next) > ESCAPE_GRAPH_RADIUS * ESCAPE_GRAPH_RADIUS
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

    private static int countOnwardReachableSpace(
            BlockPos origin,
            BlockPos start,
            Set<BlockPos> reachablePositions
    ) {
        int minimumHorizontalDistanceSquared = horizontalDistanceSquared(origin, start);
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<OnwardSearchNode> frontier = new ArrayDeque<>();
        visited.add(start);
        frontier.add(new OnwardSearchNode(start, 0));

        while (!frontier.isEmpty()) {
            OnwardSearchNode node = frontier.removeFirst();
            if (node.steps() >= ESCAPE_ONWARD_LOOKAHEAD) {
                continue;
            }

            for (Direction direction : HORIZONTAL_DIRECTIONS) {
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

    private static int horizontalDistanceSquared(BlockPos first, BlockPos second) {
        int x = second.getX() - first.getX();
        int z = second.getZ() - first.getZ();
        return x * x + z * z;
    }

    private static Optional<MultiTargetPositionTracker> createEscapeTarget(
            Vec3 origin,
            List<EscapeCandidate> candidates
    ) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        int bestOnwardSpace = candidates.stream()
                .mapToInt(EscapeCandidate::onwardSpace)
                .max()
                .orElse(0);
        int minimumCompetitiveOnwardSpace = Math.max(0, bestOnwardSpace - ESCAPE_ONWARD_SCORE_SLACK);
        boolean[] competitiveSectors = new boolean[8];
        for (EscapeCandidate candidate : candidates) {
            if (candidate.onwardSpace() == bestOnwardSpace) {
                competitiveSectors[escapeSector(origin, candidate.position())] = true;
            }
        }

        List<EscapeCandidate> selected = new ArrayList<>();
        int[] perSector = new int[8];
        for (EscapeCandidate candidate : candidates) {
            int sector = escapeSector(origin, candidate.position());
            if (!competitiveSectors[sector]
                    || candidate.onwardSpace() < minimumCompetitiveOnwardSpace
                    || perSector[sector] >= ESCAPE_TARGETS_PER_SECTOR) {
                continue;
            }
            selected.add(candidate);
            perSector[sector]++;
            if (selected.size() >= ESCAPE_TARGET_LIMIT) {
                break;
            }
        }
        if (selected.isEmpty()) {
            return Optional.empty();
        }

        LinkedHashSet<BlockPos> targets = new LinkedHashSet<>();
        for (EscapeCandidate candidate : selected) {
            targets.add(BlockPos.containing(candidate.position()));
        }
        BlockPos preferred = BlockPos.containing(selected.getFirst().position());
        return Optional.of(new EscapePositionTarget(preferred, Set.copyOf(targets)));
    }

    private static int escapeSector(Vec3 origin, Vec3 candidate) {
        double angle = Math.atan2(candidate.z - origin.z, candidate.x - origin.x);
        int sector = (int)Math.floor((angle + Math.PI) / (Math.PI / 4.0D));
        return Math.floorMod(sector, 8);
    }

    private static final class EscapePositionTarget implements CombatEscapePositionTracker {
        private final BlockPos preferred;
        private final Set<BlockPos> targets;

        private EscapePositionTarget(BlockPos preferred, Set<BlockPos> targets) {
            this.preferred = preferred.immutable();
            this.targets = targets;
        }

        @Override
        public Vec3 currentPosition() {
            return Vec3.atBottomCenterOf(preferred);
        }

        @Override
        public BlockPos currentBlockPosition() {
            return preferred;
        }

        @Override
        public boolean isVisibleBy(LivingEntity livingEntity) {
            return true;
        }

        @Override
        public Set<BlockPos> getPathTargets(Mob mob) {
            return targets;
        }

        @Override
        public boolean isReached(Mob mob, int closeEnoughDistance) {
            for (BlockPos target : targets) {
                if (target.distManhattan(mob.blockPosition()) <= closeEnoughDistance) {
                    return true;
                }
            }
            return false;
        }
    }

    static Optional<Vec3> findFiringPosition(
            PathfinderMob entity,
            LivingEntity target,
            List<? extends LivingEntity> hazards,
            double attackRangeSquared
    ) {
        Vec3 best = null;
        double bestTravelDistanceSquared = Double.POSITIVE_INFINITY;
        double minimumTargetDistance = Math.max(0.0D, entity.distanceTo(target) - MAX_REPOSITION_CLOSING_DISTANCE);
        double minimumTargetDistanceSquared = minimumTargetDistance * minimumTargetDistance;

        Optional<Vec3> preferred = findPreferredLateralFiringPosition(
                entity,
                target,
                hazards,
                attackRangeSquared,
                minimumTargetDistanceSquared
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
                    hazards,
                    attackRangeSquared,
                    minimumTargetDistanceSquared,
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
            List<? extends LivingEntity> hazards,
            double attackRangeSquared,
            double minimumTargetDistanceSquared
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
                    hazards,
                    attackRangeSquared,
                    minimumTargetDistanceSquared,
                    preferredCandidate
            )) {
                return Optional.of(preferredCandidate);
            }

            Vec3 oppositeCandidate = entity.position().add(lateral.scale(-distance * preferredSign));
            if (isValidFiringPosition(
                    entity,
                    target,
                    hazards,
                    attackRangeSquared,
                    minimumTargetDistanceSquared,
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
            List<? extends LivingEntity> hazards,
            double attackRangeSquared,
            double minimumTargetDistanceSquared,
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

        if (!preservesHazardClearance(entity.position(), candidate, hazards)) {
            return false;
        }

        return hasLineOfSight(entity, candidate, target);
    }

    static Optional<Vec3> findApproachPosition(
            PathfinderMob entity,
            LivingEntity target,
            List<? extends LivingEntity> secondaryHazards
    ) {
        if (secondaryHazards.isEmpty()) {
            return Optional.empty();
        }

        Vec3 origin = entity.position();
        double currentTargetDistance = origin.distanceTo(target.position());
        Vec3 towardTarget = target.position().subtract(origin).multiply(1.0D, 0.0D, 1.0D);
        if (towardTarget.lengthSqr() < 1.0E-6D) {
            return Optional.empty();
        }
        towardTarget = towardTarget.normalize();
        Vec3 lateral = new Vec3(-towardTarget.z, 0.0D, towardTarget.x);

        Vec3 best = null;
        double bestTargetDistance = Double.POSITIVE_INFINITY;
        double bestHazardDistanceSquared = Double.NEGATIVE_INFINITY;
        for (int forwardOffset : APPROACH_FORWARD_OFFSETS) {
            for (int lateralOffset : APPROACH_LATERAL_OFFSETS) {
                Vec3 desired = origin
                        .add(towardTarget.scale(forwardOffset))
                        .add(lateral.scale(lateralOffset));
                Vec3 candidate = findWalkableCandidateNear(entity, desired);
                if (candidate == null) {
                    continue;
                }

                double targetDistance = candidate.distanceTo(target.position());
                if (targetDistance > currentTargetDistance + MAX_APPROACH_BACKTRACK
                        || !preservesHazardClearance(origin, candidate, secondaryHazards)
                        || !preservesHazardClearanceAlongSegment(origin, candidate, secondaryHazards)) {
                    continue;
                }

                double hazardDistanceSquared = minimumDistanceSquared(candidate, secondaryHazards);
                if (targetDistance < bestTargetDistance
                        || targetDistance == bestTargetDistance
                        && hazardDistanceSquared > bestHazardDistanceSquared) {
                    best = candidate;
                    bestTargetDistance = targetDistance;
                    bestHazardDistanceSquared = hazardDistanceSquared;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static Vec3 findWalkableCandidateNear(PathfinderMob entity, Vec3 desired) {
        return findWalkableCandidateNear(entity, desired, CANDIDATE_VERTICAL_OFFSETS);
    }

    private static Vec3 findWalkableCandidateNear(PathfinderMob entity, Vec3 desired, int[] verticalOffsets) {
        BlockPos base = BlockPos.containing(desired);
        for (int verticalOffset : verticalOffsets) {
            BlockPos position = base.above(verticalOffset);
            Vec3 candidate = Vec3.atBottomCenterOf(position);
            if (entity.getNavigation().isStableDestination(position)
                    && isWalkableDestination(entity, candidate)
                    && hasStandingSpace(entity, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    static boolean isImmediateStrafeStepWalkable(
            PathfinderMob entity,
            float lateralDirection,
            List<? extends LivingEntity> hazards
    ) {
        float sign = Math.signum(lateralDirection);
        if (sign == 0.0F) {
            return false;
        }
        Vec3 origin = entity.position();
        Vec3 candidate = origin.add(strafeOffset(entity, sign, STRAFE_PROBE_STEP));
        return isWalkableDestination(entity, candidate)
                && hasStandingSpace(entity, candidate)
                && preservesHazardClearance(origin, candidate, hazards);
    }

    static float findBestStrafeDirection(
            PathfinderMob entity,
            List<? extends LivingEntity> hazards
    ) {
        StrafeCandidate left = scoreStrafeSide(entity, -1.0F, hazards);
        StrafeCandidate right = scoreStrafeSide(entity, 1.0F, hazards);
        if (left == null) {
            return right == null ? 0.0F : right.direction();
        }
        if (right == null) {
            return left.direction();
        }
        if (left.clearance() != right.clearance()) {
            return left.clearance() > right.clearance() ? left.direction() : right.direction();
        }
        if (left.hazardDistanceSquared() != right.hazardDistanceSquared()) {
            return left.hazardDistanceSquared() > right.hazardDistanceSquared()
                    ? left.direction()
                    : right.direction();
        }
        return (entity.getUUID().getLeastSignificantBits() & 1L) == 0L ? 1.0F : -1.0F;
    }

    private static StrafeCandidate scoreStrafeSide(
            PathfinderMob entity,
            float lateralDirection,
            List<? extends LivingEntity> hazards
    ) {
        float sign = Math.signum(lateralDirection);
        if (sign == 0.0F) {
            return null;
        }

        Vec3 origin = entity.position();
        double clearance = 0.0D;
        Vec3 furthest = origin;

        for (double distance = STRAFE_PROBE_STEP;
             distance <= MAX_STRAFE_CLEARANCE + 1.0E-6D;
             distance += STRAFE_PROBE_STEP) {
            Vec3 candidate = origin.add(strafeOffset(entity, sign, distance));
            if (!isWalkableDestination(entity, candidate)
                    || !hasStandingSpace(entity, candidate)
                    || !preservesHazardClearance(origin, candidate, hazards)) {
                break;
            }
            clearance = distance;
            furthest = candidate;
        }

        if (clearance < MIN_STRAFE_CLEARANCE) {
            return null;
        }
        return new StrafeCandidate(
                sign,
                clearance,
                minimumDistanceSquared(furthest, hazards)
        );
    }

    private static Vec3 strafeOffset(PathfinderMob entity, float sign, double distance) {
        float yawRadians = entity.getYRot() * (float)(Math.PI / 180.0D);
        return new Vec3(
                -sign * Math.sin(yawRadians) * distance,
                0.0D,
                sign * Math.cos(yawRadians) * distance
        );
    }

    private static boolean isNearbyMovementThreat(Mob entity, LivingEntity candidate) {
        return candidate != entity
                && RangedWeaponHelper.isValidAttackTarget(entity, candidate)
                && Math.abs(entity.getY() - candidate.getY()) <= ArcherMovementTask.CLOSE_RANGE_VERTICAL_THREAT_DISTANCE
                && GuardEnemiesSensor.isGuardEnemy(candidate, entity);
    }

    private static boolean isEscapeDriver(Mob entity, LivingEntity candidate, LivingEntity attackTarget) {
        return candidate == attackTarget
                || entity.getLastHurtByMob() == candidate
                || candidate instanceof Mob mob && mob.getTarget() == entity
                || entity.distanceToSqr(candidate) < ArcherMovementTask.EMERGENCY_ENTER_DISTANCE_SQUARED;
    }

    private static double minimumDistanceSquared(Vec3 candidate, List<? extends LivingEntity> threats) {
        double minimum = Double.POSITIVE_INFINITY;
        for (LivingEntity threat : threats) {
            minimum = Math.min(minimum, candidate.distanceToSqr(threat.position()));
        }
        return minimum;
    }

    private static boolean preservesHazardClearance(
            Vec3 origin,
            Vec3 candidate,
            List<? extends LivingEntity> hazards
    ) {
        double desiredClearance = Math.sqrt(ArcherMovementTask.KITE_ENTER_DISTANCE_SQUARED);
        for (LivingEntity hazard : hazards) {
            double requiredDistance = Math.min(origin.distanceTo(hazard.position()), desiredClearance);
            if (candidate.distanceTo(hazard.position()) + 1.0E-6D < requiredDistance) {
                return false;
            }
        }
        return true;
    }

    private static boolean preservesHazardClearanceAlongSegment(
            Vec3 origin,
            Vec3 candidate,
            List<? extends LivingEntity> hazards
    ) {
        double desiredClearance = Math.sqrt(ArcherMovementTask.KITE_ENTER_DISTANCE_SQUARED);
        for (LivingEntity hazard : hazards) {
            double requiredDistance = Math.min(origin.distanceTo(hazard.position()), desiredClearance);
            if (distanceToSegment(hazard.position(), origin, candidate) + 1.0E-6D < requiredDistance) {
                return false;
            }
        }
        return true;
    }

    private static double distanceToSegment(Vec3 point, Vec3 from, Vec3 to) {
        Vec3 segment = to.subtract(from);
        double lengthSquared = segment.lengthSqr();
        if (lengthSquared < 1.0E-12D) {
            return point.distanceTo(from);
        }
        double progress = point.subtract(from).dot(segment) / lengthSquared;
        progress = Math.max(0.0D, Math.min(1.0D, progress));
        return point.distanceTo(from.add(segment.scale(progress)));
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

    private record EscapeCandidate(
            Vec3 position,
            double minimumDistanceSquared,
            double minimumHazardDistanceSquared,
            int travelSteps,
            int onwardSpace,
            int elevationGain
    ) {
    }

    private record OnwardSearchNode(BlockPos position, int steps) {
    }

    private record StrafeCandidate(float direction, double clearance, double hazardDistanceSquared) {
    }

    record ThreatContext(
            List<LivingEntity> hazards,
            List<LivingEntity> escapeThreats,
            LivingEntity nearestEscapeThreat
    ) {
    }

}
