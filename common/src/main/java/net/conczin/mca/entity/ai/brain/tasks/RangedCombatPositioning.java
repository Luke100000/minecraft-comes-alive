package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.ai.RangedWeaponHelper;
import net.conczin.mca.entity.ai.StrafeTerrain;
import net.conczin.mca.entity.ai.navigation.CombatEscapePositionTracker;
import net.conczin.mca.entity.ai.navigation.MultiTargetPositionTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class RangedCombatPositioning {
    private static final int FIRING_HORIZONTAL_RANGE = 8;
    private static final int FIRING_VERTICAL_RANGE = 4;
    private static final double FIRING_LATERAL_STEP = 2.0D;
    private static final int ESCAPE_SEARCH_RADIUS = 8;
    private static final int ESCAPE_ONWARD_LOOKAHEAD = 4;
    private static final int ESCAPE_GRAPH_RADIUS = ESCAPE_SEARCH_RADIUS + ESCAPE_ONWARD_LOOKAHEAD;
    private static final int ESCAPE_VERTICAL_RANGE = 5;
    private static final int ESCAPE_TARGETS_PER_SECTOR = 2;
    private static final int ESCAPE_TARGET_LIMIT = 12;
    private static final List<Direction> HORIZONTAL_DIRECTIONS = List.of(
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    );
    private static final double NEARBY_THREAT_RANGE_SQUARED = 256.0D;
    private static final double MIN_USEFUL_DISTANCE_GAIN = 0.5D;
    private static final double MAX_APPROACH_BACKTRACK = 0.5D;
    private static final int[] APPROACH_FORWARD_OFFSETS = {0, 2, 4};
    private static final int[] APPROACH_LATERAL_OFFSETS = {-4, 4, -6, 6};
    private static final int[] CANDIDATE_VERTICAL_OFFSETS = {0, 1, -1, 2, -2};
    private static final double STRAFE_PROBE_STEP = 0.5D;
    private static final double MAX_STRAFE_CLEARANCE = 2.5D;

    private RangedCombatPositioning() {
    }

    static ThreatContext collectThreatContext(Mob entity, LivingEntity attackTarget) {
        List<LivingEntity> hazards = new ArrayList<>();
        List<LivingEntity> escapeThreats = new ArrayList<>();
        entity.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).ifPresent(nearby -> {
            for (LivingEntity candidate : nearby) {
                if (!isNearbyMovementThreat(entity, candidate)
                        || !isEscapeDriver(entity, candidate, attackTarget)) {
                    continue;
                }
                if (entity.getSensing().hasLineOfSight(candidate)) {
                    addEscapeThreat(hazards, candidate);
                }
                addEscapeThreat(escapeThreats, candidate);
            }
        });

        if (attackTarget != null
                && entity.distanceToSqr(attackTarget) <= NEARBY_THREAT_RANGE_SQUARED
                && isNearbyMovementThreat(entity, attackTarget)) {
            addEscapeThreat(hazards, attackTarget);
            addEscapeThreat(escapeThreats, attackTarget);
        }
        LivingEntity lastAttacker = entity.getLastHurtByMob();
        addEscapeThreatIfNearby(entity, escapeThreats, lastAttacker, attackTarget);
        if (lastAttacker != null && entity.distanceToSqr(lastAttacker) <= NEARBY_THREAT_RANGE_SQUARED
                && isNearbyMovementThreat(entity, lastAttacker)) {
            addEscapeThreat(hazards, lastAttacker);
        }

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

    static Optional<MultiTargetPositionTracker> findRangedEvasionTarget(
            PathfinderMob entity,
            LivingEntity target,
            ThreatContext threats,
            double desiredDistance,
            double attackRangeSquared
    ) {
        return findEscapeTarget(entity, threats.escapeThreats(), threats.hazards(), desiredDistance,
                false, target, attackRangeSquared);
    }

    private static Optional<MultiTargetPositionTracker> findEscapeTarget(
            PathfinderMob entity,
            List<? extends LivingEntity> escapeThreats,
            List<? extends LivingEntity> hazards,
            double desiredDistance,
            boolean emergency
    ) {
        return findEscapeTarget(entity, escapeThreats, hazards, desiredDistance, emergency, null, Double.POSITIVE_INFINITY);
    }

    private static Optional<MultiTargetPositionTracker> findEscapeTarget(
            PathfinderMob entity,
            List<? extends LivingEntity> escapeThreats,
            List<? extends LivingEntity> hazards,
            double desiredDistance,
            boolean emergency,
            LivingEntity firingTarget,
            double attackRangeSquared
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
        EscapeGraph graph = collectReachableEscapePositions(entity, originBlock);
        Map<BlockPos, Integer> reachablePositions = graph.travelSteps();

        for (Map.Entry<BlockPos, Integer> entry : reachablePositions.entrySet()) {
            BlockPos position = entry.getKey();
            if (position.equals(originBlock)
                    || horizontalDistanceSquared(originBlock, position) > ESCAPE_SEARCH_RADIUS * ESCAPE_SEARCH_RADIUS) {
                continue;
            }

            Vec3 candidatePosition = Vec3.atBottomCenterOf(position);
            // WALKABLE_DOOR nodes belong in the reachability graph so escape routing can pass
            // through a closed hand-operable door/gate, but a collision-blocked transit node
            // must never become the final escape target.
            if (!hasStandingSpace(entity, candidatePosition)) {
                continue;
            }
            // Ranged evasion must preserve a usable shot rather than buy melee
            // spacing by leaving range and immediately having to approach again.
            if (firingTarget != null && (candidatePosition.distanceToSqr(firingTarget.position()) > attackRangeSquared
                    || !hasLineOfSight(entity, candidatePosition, firingTarget)
                    || RangedWeaponHelper.getBowHoldingHand(entity) != null
                    && !RangedWeaponHelper.canShootBowFrom(entity, candidatePosition, firingTarget))) {
                continue;
            }
            double candidateMinimumDistanceSquared = minimumDistanceSquared(candidatePosition, escapeThreats);
            if (candidateMinimumDistanceSquared <= currentMinimumDistanceSquared + MIN_USEFUL_DISTANCE_GAIN) {
                continue;
            }

            boolean opensEveryThreat = opensDistanceFromEveryThreat(origin, candidatePosition, escapeThreats);
            OnwardReachability onward = measureOnwardReachability(originBlock, position, graph.edges());
            EscapeCandidate candidate = new EscapeCandidate(
                    candidatePosition,
                    candidateMinimumDistanceSquared,
                    minimumDistanceSquared(candidatePosition, hazards),
                    entry.getValue(),
                    onward.progress(),
                    onward.space(),
                    onward.demonstratedContinuation(),
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
                .comparing(EscapeCandidate::demonstratedContinuation).reversed()
                .thenComparing(Comparator.comparingInt(EscapeCandidate::onwardProgress).reversed())
                .thenComparing(Comparator.comparingInt(EscapeCandidate::elevationGain).reversed())
                .thenComparing(Comparator.comparingInt(EscapeCandidate::onwardSpace).reversed())
                .thenComparing(Comparator.comparingDouble(EscapeCandidate::minimumDistanceSquared).reversed())
                .thenComparing(Comparator.comparingDouble(EscapeCandidate::minimumHazardDistanceSquared).reversed())
                .thenComparingInt(EscapeCandidate::travelSteps)
                .thenComparingInt(candidate -> BlockPos.containing(candidate.position()).getX())
                .thenComparingInt(candidate -> BlockPos.containing(candidate.position()).getY())
                .thenComparingInt(candidate -> BlockPos.containing(candidate.position()).getZ());
        Comparator<EscapeCandidate> emergencyComparator = Comparator
                .comparing(EscapeCandidate::demonstratedContinuation).reversed()
                .thenComparing(Comparator.comparingInt(EscapeCandidate::onwardProgress).reversed())
                .thenComparing(Comparator.comparingInt(EscapeCandidate::elevationGain).reversed())
                .thenComparing(Comparator.comparingInt(EscapeCandidate::onwardSpace).reversed())
                .thenComparing(Comparator.comparingDouble(EscapeCandidate::minimumDistanceSquared).reversed())
                .thenComparing(Comparator.comparingDouble(EscapeCandidate::minimumHazardDistanceSquared).reversed())
                .thenComparingInt(EscapeCandidate::travelSteps);
        safeCandidates.sort(safeComparator);
        nonClosingCandidates.sort(safeComparator);
        emergencyFallbackCandidates.sort(emergencyComparator);
        List<EscapeCandidate> selected = !safeCandidates.isEmpty()
                ? safeCandidates
                : !nonClosingCandidates.isEmpty()
                ? nonClosingCandidates
                : emergencyFallbackCandidates;
        return createEscapeTarget(origin, selected, emergency && safeCandidates.isEmpty()
                && nonClosingCandidates.isEmpty(), emergency, escapeThreats, hazards);
    }

    private static EscapeGraph collectReachableEscapePositions(PathfinderMob entity, BlockPos origin) {
        Map<BlockPos, Integer> travelSteps = new LinkedHashMap<>();
        Map<BlockPos, List<BlockPos>> edges = new LinkedHashMap<>();
        // Each node is inspected from several adjacent graph cells. Terrain and
        // entity collision cannot change during this synchronous planning call.
        Map<BlockPos, Boolean> safePositions = new HashMap<>();
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        travelSteps.put(origin, 0);
        frontier.add(origin);

        while (!frontier.isEmpty()) {
            BlockPos current = frontier.removeFirst();
            int nextTravelSteps = travelSteps.get(current) + 1;
            List<BlockPos> neighbors = new ArrayList<>(4);
            for (Direction direction : HORIZONTAL_DIRECTIONS) {
                BlockPos next = resolveAdjacentEscapePosition(entity, current, direction, safePositions);
                if (next == null
                        || Math.abs(next.getY() - origin.getY()) > ESCAPE_VERTICAL_RANGE
                        || horizontalDistanceSquared(origin, next) > ESCAPE_GRAPH_RADIUS * ESCAPE_GRAPH_RADIUS) {
                    continue;
                }
                neighbors.add(next);
                if (travelSteps.containsKey(next)) {
                    continue;
                }
                travelSteps.put(next, nextTravelSteps);
                frontier.addLast(next);
            }
            edges.put(current, List.copyOf(neighbors));
        }
        return new EscapeGraph(travelSteps, edges);
    }

    private static BlockPos resolveAdjacentEscapePosition(PathfinderMob entity, BlockPos current,
                                                         Direction direction, Map<BlockPos, Boolean> safePositions) {
        BlockPos adjacent = current.relative(direction);
        if (isSafeEscapePosition(entity, adjacent, safePositions)) {
            return adjacent;
        }
        BlockPos above = adjacent.above();
        if (isSafeEscapePosition(entity, above, safePositions)) {
            return above;
        }
        BlockPos below = adjacent.below();
        return isSafeEscapePosition(entity, below, safePositions) ? below : null;
    }

    private static boolean isSafeEscapePosition(PathfinderMob entity, BlockPos position,
                                                Map<BlockPos, Boolean> safePositions) {
        Boolean cached = safePositions.get(position);
        if (cached != null) {
            return cached;
        }
        Vec3 candidate = Vec3.atBottomCenterOf(position);
        PathType pathType = pathTypeAt(entity, position);
        boolean safe = entity.getNavigation().isStableDestination(position)
                && isTraversablePathType(pathType)
                && (pathType == PathType.WALKABLE_DOOR || hasStandingSpace(entity, candidate));
        safePositions.put(position, safe);
        return safe;
    }

    private static OnwardReachability measureOnwardReachability(
            BlockPos origin,
            BlockPos start,
            Map<BlockPos, List<BlockPos>> edges
    ) {
        int minimumHorizontalDistanceSquared = horizontalDistanceSquared(origin, start);
        int maximumHorizontalDistanceSquared = minimumHorizontalDistanceSquared;
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<OnwardSearchNode> frontier = new ArrayDeque<>();
        boolean demonstratedContinuation = false;
        visited.add(start);
        frontier.add(new OnwardSearchNode(start, 0));

        while (!frontier.isEmpty()) {
            OnwardSearchNode node = frontier.removeFirst();
            if (node.steps() >= ESCAPE_ONWARD_LOOKAHEAD) {
                demonstratedContinuation |= horizontalDistanceSquared(origin, node.position()) > minimumHorizontalDistanceSquared;
                continue;
            }

            for (BlockPos neighbor : edges.getOrDefault(node.position(), List.of())) {
                if (horizontalDistanceSquared(origin, neighbor) >= minimumHorizontalDistanceSquared
                        && visited.add(neighbor)) {
                    maximumHorizontalDistanceSquared = Math.max(
                            maximumHorizontalDistanceSquared,
                            horizontalDistanceSquared(origin, neighbor));
                    frontier.addLast(new OnwardSearchNode(neighbor, node.steps() + 1));
                }
            }
        }
        return new OnwardReachability(
                maximumHorizontalDistanceSquared - minimumHorizontalDistanceSquared,
                visited.size() - 1, demonstratedContinuation);
    }

    private static int horizontalDistanceSquared(BlockPos first, BlockPos second) {
        int x = second.getX() - first.getX();
        int z = second.getZ() - first.getZ();
        return x * x + z * z;
    }

    private static Optional<MultiTargetPositionTracker> createEscapeTarget(
            Vec3 origin,
            List<EscapeCandidate> candidates,
            boolean degraded,
            boolean emergency,
            List<? extends LivingEntity> escapeThreats,
            List<? extends LivingEntity> hazards
    ) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        List<EscapeCandidate> selected = new ArrayList<>();
        int[] perSector = new int[8];
        for (EscapeCandidate candidate : candidates) {
            int sector = escapeSector(origin, candidate.position());
            if (perSector[sector] >= ESCAPE_TARGETS_PER_SECTOR) {
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
        List<Set<BlockPos>> ranked = new ArrayList<>(selected.size());
        for (EscapeCandidate candidate : selected) {
            BlockPos block = BlockPos.containing(candidate.position());
            targets.add(block);
            ranked.add(Set.of(block));
        }
        BlockPos preferred = BlockPos.containing(selected.getFirst().position());
        return Optional.of(new EscapePositionTarget(preferred, Set.copyOf(targets), List.copyOf(ranked),
                degraded, emergency, List.copyOf(escapeThreats), List.copyOf(hazards)));
    }

    private static int escapeSector(Vec3 origin, Vec3 candidate) {
        double angle = Math.atan2(candidate.z - origin.z, candidate.x - origin.x);
        int sector = (int)Math.floor((angle + Math.PI) / (Math.PI / 4.0D));
        return Math.floorMod(sector, 8);
    }

    private static class CandidatePositionTarget implements MultiTargetPositionTracker {
        private final BlockPos preferred;
        private final Set<BlockPos> targets;

        private CandidatePositionTarget(BlockPos preferred, Set<BlockPos> targets) {
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

    private static final class EscapePositionTarget extends CandidatePositionTarget implements CombatEscapePositionTracker {
        private final List<Set<BlockPos>> ranked;
        private final boolean degraded;
        private final boolean emergency;
        private final List<LivingEntity> originalDrivers;
        private final List<LivingEntity> originalHazards;

        private EscapePositionTarget(BlockPos preferred, Set<BlockPos> targets,
                                     List<Set<BlockPos>> ranked, boolean degraded, boolean emergency,
                                     List<? extends LivingEntity> drivers,
                                     List<? extends LivingEntity> hazards) {
            super(preferred, targets);
            this.ranked = ranked;
            this.degraded = degraded;
            this.emergency = emergency;
            this.originalDrivers = List.copyOf(drivers);
            this.originalHazards = List.copyOf(hazards);
        }

        @Override
        public List<Set<BlockPos>> getRankedPathTargets(Mob mob) {
            return ranked;
        }

        @Override
        public boolean isPathSafe(Mob mob, Path path) {
            return evaluatePathSafety(mob, path, degraded);
        }

        @Override
        public boolean isDegradedEmergencyPathSafe(Mob mob, Path path) {
            return emergency && evaluatePathSafety(mob, path, true);
        }

        private boolean evaluatePathSafety(Mob mob, Path path, boolean degradedSafety) {
            LivingEntity currentTarget = mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            ThreatContext current = collectThreatContext(mob, currentTarget);
            // Direct tactical callers without brain target memory still supply their
            // own living, moving threat references; don't lose those in validation.
            if (current.escapeThreats().isEmpty()) {
                current = new ThreatContext(originalHazards, originalDrivers, null);
            }
            // Clearance may relax for the admitted pursuers, never for a newly
            // active attacker. This policy belongs to the route, including its
            // first validation and native path admission.
            return (!degradedSafety || originalDrivers.containsAll(current.escapeThreats()))
                    && isEscapePathSafe(mob, path, current, degradedSafety);
        }
    }

    private static final class FiringPositionTarget extends CandidatePositionTarget {
        private final LivingEntity target;
        private final double attackRangeSquared;

        private FiringPositionTarget(BlockPos preferred, Set<BlockPos> positions,
                                     LivingEntity target, double attackRangeSquared) {
            super(preferred, positions);
            this.target = target;
            this.attackRangeSquared = attackRangeSquared;
        }

        @Override
        public boolean isReached(Mob mob, int closeEnoughDistance) {
            // Native waypoint tolerance may enter the right block while a lip or
            // column still obscures the shot. Its navigation owner already keeps
            // following the final node until this semantic arrival predicate holds.
            return super.isReached(mob, closeEnoughDistance)
                    && mob instanceof PathfinderMob archer
                    && RangedWeaponHelper.isValidAttackTarget(mob, target)
                    && mob.distanceToSqr(target) <= attackRangeSquared
                    && mob.hasLineOfSight(target)
                    && RangedWeaponHelper.canShootBowFrom(archer, mob.position(), target);
        }
    }

    static boolean isEscapePathSafe(Mob mob, Path path, ThreatContext threats, boolean degraded) {
        if (path.getNodeCount() == 0 || path.getNextNodeIndex() >= path.getNodeCount()) {
            return false;
        }
        Vec3 origin = mob.position();
        Vec3 previous = origin;
        double previousFloorY = path.getEntityPosAtNode(mob, Math.max(0, path.getNextNodeIndex() - 1)).y;
        for (int index = path.getNextNodeIndex(); index < path.getNodeCount(); index++) {
            Vec3 next = path.getEntityPosAtNode(mob, index);
            // A jump changes the mob's physical height, not the terrain transition
            // already described by the native path's consecutive floor nodes.
            boolean trappingDrop = previousFloorY - next.y > Math.max(1.0D, mob.maxUpStep()) + 1.0E-3D;
            previousFloorY = next.y;
            // Navigation may retain the center of the block already occupied as its
            // first node. Off-center mobs must not invalidate an outward escape just
            // because that stale starting-center point is marginally closer to a threat.
            if (index == path.getNextNodeIndex() && BlockPos.containing(next).equals(mob.blockPosition())) {
                continue;
            }
            if (trappingDrop || !degraded && (!preservesHazardClearance(origin, next, threats.hazards())
                    || !preservesHazardClearanceAlongSegment(previous, next, threats.hazards()))) {
                return false;
            }
            previous = next;
        }
        return !degraded || minimumDistanceSquared(previous, threats.escapeThreats())
                > minimumDistanceSquared(origin, threats.escapeThreats()) + MIN_USEFUL_DISTANCE_GAIN;
    }

    static Optional<MultiTargetPositionTracker> findFiringTarget(
            PathfinderMob entity,
            LivingEntity target,
            List<? extends LivingEntity> hazards,
            double attackRangeSquared
    ) {
        return findFiringTarget(entity, target, hazards, attackRangeSquared,
                collectReachableEscapePositions(entity, entity.blockPosition()));
    }

    static Optional<MultiTargetPositionTracker> findRepositionTarget(
            PathfinderMob entity, LivingEntity target, ThreatContext threats, double attackRangeSquared) {
        EscapeGraph graph = collectReachableEscapePositions(entity, entity.blockPosition());
        Optional<MultiTargetPositionTracker> firing = findFiringTarget(
                entity, target, threats.hazards(), attackRangeSquared, graph);
        return firing.isPresent() ? firing : findRecoveryTarget(entity, target, threats, attackRangeSquared, graph, false);
    }

    private static Optional<MultiTargetPositionTracker> findFiringTarget(
            PathfinderMob entity, LivingEntity target, List<? extends LivingEntity> hazards,
            double attackRangeSquared, EscapeGraph graph) {
        double minimumTargetDistance = Math.min(entity.distanceTo(target), Math.sqrt(ArcherMovementTask.KITE_ENTER_DISTANCE_SQUARED));
        double minimumTargetDistanceSquared = minimumTargetDistance * minimumTargetDistance;
        Set<BlockPos> candidates = new LinkedHashSet<>();

        collectPreferredLateralFiringPositions(
                entity,
                target,
                hazards,
                attackRangeSquared,
                minimumTargetDistanceSquared,
                candidates
        );
        candidates.retainAll(graph.travelSteps().keySet());
        Optional<MultiTargetPositionTracker> preferred = createFiringTarget(candidates, target, attackRangeSquared);
        if (preferred.isPresent()) {
            return preferred;
        }

        candidates.clear();

        Vec3 toward = target.position().subtract(entity.position()).multiply(1, 0, 1).normalize();
        for (int distance = 2; distance <= FIRING_HORIZONTAL_RANGE; distance += 2) {
            Vec3 feet = findWalkableCandidateNear(entity, entity.position().add(toward.scale(distance)));
            if (feet != null && graph.travelSteps().containsKey(BlockPos.containing(feet))
                    && isValidFiringPosition(entity, target, hazards, attackRangeSquared,
                    minimumTargetDistanceSquared, feet)) {
                candidates.add(BlockPos.containing(feet));
            }
        }
        if (!candidates.isEmpty()) {
            return createFiringTarget(candidates, target, attackRangeSquared);
        }

        // The reachable graph is already available. Search its actual standing
        // positions instead of sampling points that can miss a narrow firing slit
        // or climb through solid terrain onto an unreachable roof.
        BlockPos origin = entity.blockPosition();
        List<BlockPos> nearby = graph.travelSteps().keySet().stream()
                .filter(position -> !position.equals(origin)
                        && horizontalDistanceSquared(origin, position) <= FIRING_HORIZONTAL_RANGE * FIRING_HORIZONTAL_RANGE
                        && Math.abs(position.getY() - origin.getY()) <= FIRING_VERTICAL_RANGE)
                .sorted(Comparator.comparingInt(graph.travelSteps()::get))
                .toList();
        for (BlockPos position : nearby) {
            if (isValidFiringPosition(entity, target, hazards, attackRangeSquared,
                    minimumTargetDistanceSquared, Vec3.atBottomCenterOf(position))) {
                candidates.add(position);
                if (candidates.size() >= ESCAPE_TARGET_LIMIT) {
                    break;
                }
            }
        }

        return createFiringTarget(candidates, target, attackRangeSquared);
    }

    static Optional<MultiTargetPositionTracker> findRecoveryTarget(
            PathfinderMob entity, LivingEntity target, ThreatContext threats, double attackRangeSquared) {
        return findRecoveryTarget(entity, target, threats, attackRangeSquared,
                collectReachableEscapePositions(entity, entity.blockPosition()), false);
    }

    static Optional<MultiTargetPositionTracker> findRoomierTarget(
            PathfinderMob entity, LivingEntity target, ThreatContext threats, double attackRangeSquared) {
        return findRecoveryTarget(entity, target, threats, attackRangeSquared,
                collectReachableEscapePositions(entity, entity.blockPosition()), true);
    }

    private static Optional<MultiTargetPositionTracker> findRecoveryTarget(
            PathfinderMob entity, LivingEntity target, ThreatContext threats, double attackRangeSquared,
            EscapeGraph graph, boolean requireMoreRoom) {
        BlockPos origin = entity.blockPosition();
        // The body may still be supported while its centre overhangs a ledge.
        // The graph seed then is not a standing cell; measure the actual footing
        // beside it rather than mistaking the same narrow ledge for more room.
        BlockPos standingOrigin = origin;
        if (!isSafeEscapePosition(entity, origin, new HashMap<>())) {
            standingOrigin = graph.travelSteps().entrySet().stream()
                    .filter(entry -> entry.getValue() == 1)
                    .min(Comparator.comparingDouble(entry ->
                            Vec3.atBottomCenterOf(entry.getKey()).distanceToSqr(entity.position())))
                    .map(Map.Entry::getKey).orElse(origin);
        }
        int originSpace = measureOnwardReachability(standingOrigin, standingOrigin, graph.edges()).space();
        boolean originHasShot = isValidFiringPosition(entity, target, threats.hazards(), attackRangeSquared,
                ArcherMovementTask.KITE_ENTER_DISTANCE_SQUARED, entity.position());
        List<RecoveryCandidate> candidates = new ArrayList<>();
        for (var entry : graph.travelSteps().entrySet()) {
            BlockPos position = entry.getKey();
            int distance = horizontalDistanceSquared(origin, position);
            if (distance < 4 || distance > ESCAPE_SEARCH_RADIUS * ESCAPE_SEARCH_RADIUS) {
                continue;
            }
            Vec3 feet = Vec3.atBottomCenterOf(position);
            double targetDistance = feet.distanceToSqr(target.position());
            if (targetDistance < ArcherMovementTask.KITE_ENTER_DISTANCE_SQUARED
                    || targetDistance > attackRangeSquared || !hasStandingSpace(entity, feet)
                    || !preservesHazardClearance(entity.position(), feet, threats.hazards())
                    || !preservesHazardClearanceAlongSegment(entity.position(), feet, threats.hazards())) {
                continue;
            }
            boolean usableShot = isValidFiringPosition(entity, target, threats.hazards(), attackRangeSquared,
                    ArcherMovementTask.KITE_ENTER_DISTANCE_SQUARED, feet);
            OnwardReachability onward = measureOnwardReachability(position, position, graph.edges());
            // A covered waypoint must lead out of the depression or toward more
            // room. Merely walking along another sealed corridor is not recovery.
            if (requireMoreRoom && onward.space() <= originSpace) {
                continue;
            }
            boolean gainsShot = usableShot && !originHasShot;
            if (!gainsShot && (!onward.demonstratedContinuation()
                    || position.getY() <= origin.getY() && onward.space() <= originSpace)) {
                continue;
            }
            candidates.add(new RecoveryCandidate(position, usableShot, onward.space(), entry.getValue()));
        }
        candidates.sort(Comparator.comparing(RecoveryCandidate::usableShot).reversed()
                .thenComparing(Comparator.comparingInt(RecoveryCandidate::onwardSpace).reversed())
                .thenComparingInt(RecoveryCandidate::travelSteps));
        Set<BlockPos> exits = new LinkedHashSet<>();
        for (RecoveryCandidate candidate : candidates) {
            exits.add(candidate.position());
            if (exits.size() >= ESCAPE_TARGET_LIMIT) {
                break;
            }
        }
        if (exits.isEmpty()) {
            return Optional.empty();
        }
        // Recovery shares admission and revalidation of the actual native route
        // with strict escapes, without permitting degraded emergency clearance.
        List<Set<BlockPos>> ranked = exits.stream().map(Set::of).toList();
        return Optional.of(new EscapePositionTarget(exits.iterator().next(), Set.copyOf(exits), ranked,
                false, false, threats.escapeThreats(), threats.hazards()));
    }

    private static Optional<MultiTargetPositionTracker> createFiringTarget(
            Set<BlockPos> candidates, LivingEntity target, double attackRangeSquared) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new FiringPositionTarget(candidates.iterator().next(), Set.copyOf(candidates), target, attackRangeSquared));
    }

    private static void collectPreferredLateralFiringPositions(
            PathfinderMob entity,
            LivingEntity target,
            List<? extends LivingEntity> hazards,
            double attackRangeSquared,
            double minimumTargetDistanceSquared,
            Set<BlockPos> candidates
    ) {
        Vec3 towardTarget = target.position().subtract(entity.position()).multiply(1.0D, 0.0D, 1.0D);
        if (towardTarget.lengthSqr() < 1.0E-6D) {
            return;
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
                candidates.add(BlockPos.containing(preferredCandidate));
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
                candidates.add(BlockPos.containing(oppositeCandidate));
            }
        }
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

        return hasLineOfSight(entity, candidate, target)
                && (RangedWeaponHelper.getBowHoldingHand(entity) == null
                || RangedWeaponHelper.canShootBowFrom(entity, candidate, target));
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

    @Nullable
    static Vec3 findSafeStrafeLanding(PathfinderMob entity, float lateralDirection,
                                      List<? extends LivingEntity> hazards) {
        float sign = Math.signum(lateralDirection);
        if (sign == 0) return null;
        Vec3 landing = StrafeTerrain.findLanding(entity, strafeOffset(entity, sign, STRAFE_PROBE_STEP));
        return landing != null && preservesHazardClearance(entity.position(), landing, hazards) ? landing : null;
    }

    static float findBestStrafeDirection(
            PathfinderMob entity,
            LivingEntity target,
            List<? extends LivingEntity> hazards
    ) {
        StrafeCandidate left = scoreStrafeSide(entity, target, -1.0F, hazards);
        StrafeCandidate right = scoreStrafeSide(entity, target, 1.0F, hazards);
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

    static boolean hasVisibleStrafeStep(PathfinderMob entity, LivingEntity target, Vec3 landing) {
        return hasLineOfSight(entity, landing, target);
    }

    private static StrafeCandidate scoreStrafeSide(
            PathfinderMob entity,
            LivingEntity target,
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
            Vec3 candidate = StrafeTerrain.findLanding(entity, strafeOffset(entity, sign, distance));
            if (candidate == null || !preservesHazardClearance(origin, candidate, hazards)
                    || !hasLineOfSight(entity, candidate, target)) {
                break;
            }
            clearance = distance;
            furthest = candidate;
        }

        // Running STRAFE revalidates this same probe every tick, so startup only needs one safe step.
        // The longer sweep still matters for ranking: prefer the side with more room when both are usable.
        if (clearance < STRAFE_PROBE_STEP) {
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
        // LivingEntity travels with lateral input in X and forward input in Z.
        return new Vec3(
                sign * Math.cos(yawRadians) * distance,
                0.0D,
                sign * Math.sin(yawRadians) * distance
        );
    }

    private static boolean isNearbyMovementThreat(Mob entity, LivingEntity candidate) {
        return candidate != entity
                && RangedWeaponHelper.isValidAttackTarget(entity, candidate)
                && (isCloseRangeMovementThreat(entity, candidate) || isActiveRangedMovementThreat(entity, candidate))
                && !candidate.isRemoved();
    }

    private static boolean isActiveRangedMovementThreat(Mob entity, LivingEntity candidate) {
        return candidate instanceof RangedAttackMob && candidate instanceof Mob attacker
                && attacker.getTarget() == entity
                && entity.distanceToSqr(candidate) <= NEARBY_THREAT_RANGE_SQUARED
                && entity.getSensing().hasLineOfSight(candidate);
    }

    static boolean isCloseRangeMovementThreat(Mob entity, LivingEntity candidate) {
        double separation = Math.abs(entity.getY() - candidate.getY());
        if (separation <= ArcherMovementTask.CLOSE_RANGE_VERTICAL_THREAT_DISTANCE) {
            return true;
        }
        if (separation > ESCAPE_VERTICAL_RANGE || !(candidate instanceof Mob pursuer)
                || pursuer.getTarget() != entity) {
            return false;
        }
        Path path = pursuer.getNavigation().getPath();
        if (path == null || path.isDone()) {
            return false;
        }
        // Inspect the pursuer's existing local route, never pathfind on its behalf.
        // A disconnected shelf has no descending route; a staircase or jump does.
        for (int index = path.getNextNodeIndex(); index < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 12); index++) {
            BlockPos node = path.getNodePos(index);
            if (Math.abs(node.getY() - entity.getY()) <= ArcherMovementTask.CLOSE_RANGE_VERTICAL_THREAT_DISTANCE
                    && horizontalDistanceSquared(entity.blockPosition(), node) <= ArcherMovementTask.KITE_ENTER_DISTANCE_SQUARED) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEscapeDriver(Mob entity, LivingEntity candidate, LivingEntity attackTarget) {
        return candidate == attackTarget
                || entity.getLastHurtByMob() == candidate
                || candidate instanceof Mob mob && mob.getTarget() == entity;
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
        return isTraversablePathType(pathTypeAt(entity, BlockPos.containing(candidate)));
    }

    private static PathType pathTypeAt(PathfinderMob entity, BlockPos position) {
        NodeEvaluator evaluator = entity.getNavigation().getNodeEvaluator();
        return evaluator == null ? null : evaluator.getPathType(entity, position);
    }

    private static boolean isTraversablePathType(PathType pathType) {
        return pathType == null || pathType == PathType.WALKABLE
                || pathType == PathType.WALKABLE_DOOR || pathType == PathType.DOOR_OPEN;
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
            int onwardProgress,
            int onwardSpace,
            boolean demonstratedContinuation,
            int elevationGain
    ) {
    }

    private record RecoveryCandidate(BlockPos position, boolean usableShot, int onwardSpace, int travelSteps) {
    }

    private record OnwardReachability(int progress, int space, boolean demonstratedContinuation) {
    }

    private record EscapeGraph(Map<BlockPos, Integer> travelSteps, Map<BlockPos, List<BlockPos>> edges) {
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
