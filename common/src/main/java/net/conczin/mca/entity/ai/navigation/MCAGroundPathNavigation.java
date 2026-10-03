package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.PathingBlockInteraction;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.conczin.mca.entity.ai.brain.tasks.LocalInsideBrownianWalk;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public class MCAGroundPathNavigation extends GroundPathNavigation {
    private static final float REQUIRED_PATH_LENGTH = 48.0F;
    private static final int FALL_RESYNC_LOOKAHEAD = 2;
    private static final int FALL_RESYNC_HORIZONTAL_DISTANCE = 2;
    private static final int FALL_RESYNC_MIN_VERTICAL_DROP = 2;
    private static final long FAILED_EXTENDED_RETRY_TICKS = 100L;
    private static final long MAX_FAILED_EXTENDED_RETRY_TICKS = 800L;
    private static final double FAILED_EXTENDED_RETRY_MOVE_DISTANCE_SQR = 16.0D;
    private static final double FAILED_TARGET_INVALIDATION_RADIUS_SQR = 9.0D;
    private final ClimbTraversal climbTraversal;
    private FailedExtendedSearch failedExtendedSearch;
    private boolean recomputingPath;

    private static final class FailedExtendedSearch {
        private final BlockPos target;
        private final BlockPos origin;
        private long attemptedAt;
        private long retryTicks = FAILED_EXTENDED_RETRY_TICKS;
        private boolean invalidated;

        private FailedExtendedSearch(BlockPos target, BlockPos origin, long attemptedAt) {
            this.target = target.immutable();
            this.origin = origin.immutable();
            this.attemptedAt = attemptedAt;
        }
    }

    public MCAGroundPathNavigation(Mob mob, Level level) {
        super(mob, level);
        this.climbTraversal = new ClimbTraversal(mob, level);
    }

    public boolean hasClimbablePathContext() {
        return this.climbTraversal.hasPathContext(this.path, this.tick);
    }

    public boolean isControllingClimbableMovement() {
        return this.climbTraversal.ownsMovement(this.path, this.tick);
    }

    public boolean isDescendingThroughScaffolding() {
        return this.climbTraversal.isDescendingScaffolding(this.path, this.tick);
    }

    /**
     * Vanilla owns friction and horizontal travel. When climb traversal moved this tick,
     * preserve only its chosen vertical velocity for the next travel step.
     */
    public Vec3 adjustClimbableTravelMovement(Vec3 movement) {
        return this.climbTraversal.adjustTravelMovement(movement, this.tick);
    }

    @Override
    public Path createPath(BlockPos target, int reachRange) {
        PathRequestDiagnostics.recordNavigationRequest(this.mob);
        WalkTarget walkTarget = this.mob.getBrain()
                .getMemoryInternal(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        if (this.recomputingPath && walkTarget != null
                && walkTarget.getTarget() instanceof MultiTargetPositionTracker multiTarget) {
            // Brain retains the logical target and can refresh endpoints after a
            // block change; vanilla navigation remembers only its last chosen one.
            return this.createPath(multiTarget.getPathTargets(this.mob), 8, false, reachRange);
        }
        if (isExactStaticAirWalkTarget(target, walkTarget)) {
            return this.createPath(Set.of(target), 8, false, reachRange);
        }
        return super.createPath(target, reachRange);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        MCAWalkNodeEvaluator evaluator = new MCAWalkNodeEvaluator();
        this.nodeEvaluator = evaluator;
        evaluator.setCanOpenDoors(true);
        return new PathFinder(evaluator, maxVisitedNodes) {
            @Override
            public Path findPath(PathNavigationRegion region, Mob mob, Set<BlockPos> targets,
                                 float maxPathLength, int reachRange, float visitedNodesMultiplier) {
                if (!PathRequestDiagnostics.enabled()) {
                    return super.findPath(region, mob, targets, maxPathLength, reachRange, visitedNodesMultiplier);
                }
                long startedNanos = System.nanoTime();
                Path result = super.findPath(region, mob, targets, maxPathLength, reachRange,
                        visitedNodesMultiplier);
                PathRequestDiagnostics.recordSearch(mob, System.nanoTime() - startedNanos,
                        maxPathLength > getOrdinaryPathLength(mob), evaluator.expandedNodes());
                return result;
            }
        };
    }

    @Override
    protected Path createPath(Set<BlockPos> targets, int radiusOffset, boolean above, int reachRange) {
        WalkTarget walkTarget = this.mob.getBrain()
                .getMemoryInternal(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        float ordinaryPathLength = getOrdinaryPathLength(this.mob);
        if (targetsCurrentStaticWalkTarget(targets, walkTarget)) {
            BlockPos target = walkTarget.getTarget().currentBlockPosition();
            // Invalidate even when the next ordinary path is useful and returns early.
            // Otherwise a villager can move away, make progress, and come back to a
            // stale failed-search backoff at its original position.
            boolean suppressExtended = skipRepeatedFailedExtendedSearch(target);
            float extendedPathLength = Math.max(
                    (float)Config.getInstance().getVillagerPathfindingDistance(),
                    ordinaryPathLength
            );
            if (!suppressExtended && this.failedExtendedSearch != null
                    && extendedPathLength > ordinaryPathLength) {
                // A previous full search already exhausted the ordinary frontier.
                // Retry that search directly instead of expanding it twice.
                Path path = createPathWithLength(targets, radiusOffset, above, reachRange,
                        extendedPathLength, ordinaryPathLength);
                updateFailedExtendedSearch(target, path, null);
                return path;
            }
            if (requiresExtendedPath(this.mob, target)) {
                boolean useExtended = !suppressExtended && hasFailureEvidenceFor(target)
                        && extendedPathLength > ordinaryPathLength;
                float pathLength = useExtended
                        ? extendedPathLength
                        : ordinaryPathLength;
                Path path = createPathWithLength(targets, radiusOffset, above, reachRange,
                        pathLength, ordinaryPathLength);
                if (useExtended) {
                    updateFailedExtendedSearch(target, path, null);
                } else if (suppressExtended) {
                    PathRequestDiagnostics.recordSuppressedExtendedSearch(this.mob);
                }
                if (path != null && path.canReach()) {
                    clearFailedExtendedSearch();
                }
                return path;
            }

            Path ordinaryPath = createPathWithLength(targets, radiusOffset, above, reachRange,
                    ordinaryPathLength, ordinaryPathLength);
            if (ordinaryPath != null && ordinaryPath.canReach()) {
                clearFailedExtendedSearch();
            }
            if (ordinaryPath == null
                    || ordinaryPath.canReach()
                    || isUsefulPartialPath(ordinaryPath, target)
                    || walkTarget.getTarget() instanceof LocalInsideBrownianWalk.BrownianTarget
                    || extendedPathLength <= ordinaryPathLength) {
                return ordinaryPath;
            }

            if (suppressExtended) {
                PathRequestDiagnostics.recordSuppressedExtendedSearch(this.mob);
                return ordinaryPath;
            }

            Path extendedPath = createPathWithLength(targets, radiusOffset, above, reachRange,
                    extendedPathLength, ordinaryPathLength);
            updateFailedExtendedSearch(target, extendedPath, ordinaryPath);
            return preferEscalatedPath(ordinaryPath, extendedPath);
        }
        return createPathWithLength(targets, radiusOffset, above, reachRange,
                ordinaryPathLength, ordinaryPathLength);
    }

    private Path createPathWithLength(Set<BlockPos> targets, int radiusOffset, boolean above, int reachRange,
                                      float pathLength, float ordinaryPathLength) {
        float budgetMultiplier = Math.max(1.0F, pathLength / ordinaryPathLength);
        if (budgetMultiplier > 1.0F) {
            this.setMaxVisitedNodesMultiplier(budgetMultiplier);
        }
        try {
            return super.createPath(targets, radiusOffset, above, reachRange, pathLength);
        } finally {
            if (budgetMultiplier > 1.0F) {
                this.resetMaxVisitedNodesMultiplier();
            }
        }
    }

    private void updateFailedExtendedSearch(BlockPos target, Path extendedPath, Path ordinaryPath) {
        if (extendedPath == null || (!extendedPath.canReach()
                && !isUsefulPartialPath(extendedPath, target)
                && (ordinaryPath == null
                || extendedPath.getDistToTarget() >= ordinaryPath.getDistToTarget()))) {
            if (this.failedExtendedSearch == null || !this.failedExtendedSearch.target.equals(target)) {
                this.failedExtendedSearch = new FailedExtendedSearch(target, this.mob.blockPosition(),
                        this.level.getGameTime());
            } else {
                this.failedExtendedSearch.attemptedAt = this.level.getGameTime();
                this.failedExtendedSearch.retryTicks = Math.min(MAX_FAILED_EXTENDED_RETRY_TICKS,
                        this.failedExtendedSearch.retryTicks * 2L);
            }
        } else {
            clearFailedExtendedSearch();
        }
    }

    private boolean skipRepeatedFailedExtendedSearch(BlockPos target) {
        FailedExtendedSearch failure = this.failedExtendedSearch;
        if (failure == null) {
            return false;
        }
        if (!failure.target.equals(target) || !hasFailureEvidenceFor(target)
                || this.mob.blockPosition().distSqr(failure.origin) >= FAILED_EXTENDED_RETRY_MOVE_DISTANCE_SQR) {
            clearFailedExtendedSearch();
            return false;
        }
        long elapsed = this.level.getGameTime() - failure.attemptedAt;
        if (elapsed < 0L) {
            clearFailedExtendedSearch();
            return false;
        }
        return elapsed < failure.retryTicks;
    }

    private void clearFailedExtendedSearch() {
        this.failedExtendedSearch = null;
    }

    private boolean isExactStaticAirWalkTarget(BlockPos target, WalkTarget walkTarget) {
        return walkTarget != null
                && walkTarget.getCloseEnoughDist() == 0
                && walkTarget.getTarget() instanceof BlockPosTracker
                && walkTarget.getTarget().currentBlockPosition().equals(target)
                && this.level.isLoaded(target)
                && this.level.getBlockState(target).isAir();
    }

    private boolean hasFailureEvidenceFor(BlockPos target) {
        return this.mob instanceof VillagerEntityMCA villager
                && WalkTargetFailureMemory.hasFailureFor(villager, target);
    }

    private static Path preferEscalatedPath(Path boundedPath, Path escalatedPath) {
        if (escalatedPath != null
                && (escalatedPath.canReach() || isBetterPartialPath(escalatedPath, boundedPath))) {
            return escalatedPath;
        }
        return boundedPath;
    }

    static boolean isBetterPartialPath(Path candidate, Path current) {
        int distanceComparison = Float.compare(candidate.getDistToTarget(), current.getDistToTarget());
        return distanceComparison < 0
                || (distanceComparison == 0 && candidate.getNodeCount() < current.getNodeCount());
    }

    private static boolean targetsCurrentStaticWalkTarget(Set<BlockPos> targets, WalkTarget walkTarget) {
        if (walkTarget == null
                || !(walkTarget.getTarget() instanceof BlockPosTracker)
                || targets.size() != 1) {
            return false;
        }
        BlockPos pathTarget = targets.iterator().next();
        BlockPos logicalTarget = walkTarget.getTarget().currentBlockPosition();
        return pathTarget.getX() == logicalTarget.getX() && pathTarget.getZ() == logicalTarget.getZ();
    }

    public static boolean requiresExtendedPath(Mob mob, BlockPos target) {
        float ordinaryPathLength = getOrdinaryPathLength(mob);
        return mob.blockPosition().distSqr(target) > ordinaryPathLength * ordinaryPathLength;
    }

    public static float getOrdinaryPathLength(Mob mob) {
        return Math.max((float)mob.getAttributeValue(Attributes.FOLLOW_RANGE), REQUIRED_PATH_LENGTH);
    }

    public static boolean isUsefulPartialPath(Path path, BlockPos destination) {
        if (path == null
                || path.canReach()
                || path.getNodeCount() < 2
                || !path.getTarget().equals(destination)
                || path.getEndNode() == null) {
            return false;
        }
        return path.getDistToTarget() < path.getNodePos(0).distManhattan(destination);
    }

    @Override
    public boolean canCutCorner(PathType type) {
        return type != PathType.DOOR_OPEN && super.canCutCorner(type);
    }

    @Override
    protected boolean canUpdatePath() {
        if (super.canUpdatePath() || this.mob.onClimbable()) {
            return true;
        }
        return this.climbTraversal.ownsMovement(this.path, this.tick);
    }

    @Override
    public void recomputePath() {
        this.recomputingPath = true;
        try {
            super.recomputePath();
        } finally {
            this.recomputingPath = false;
        }
    }

    @Override
    public boolean shouldRecomputePath(BlockPos changed) {
        recordRetryInvalidation(changed);
        if (!super.shouldRecomputePath(changed)) {
            return false;
        }

        Path activePath = this.path;
        boolean shouldRecompute;
        if (activePath == null || !activePath.canReach()) {
            // A change outside a partial path can make its destination reachable.
            shouldRecompute = true;
        } else if (PathingBlockInteraction.canInteractWithFenceGate(this.level.getBlockState(changed))) {
            // MCA's evaluator accepts a closed, hand-operated fence gate as a door,
            // so toggling one cannot invalidate an already-reachable route.
            shouldRecompute = false;
        } else {
            // Vanilla's remaining-node-count radius can include blocks many blocks
            // away from an otherwise reachable route. Only invalidate such routes
            // for changes near their remaining nodes, including adjacent obstacles,
            // head clearance and supporting blocks below the villager's feet.
            int horizontalClearance = Mth.ceil(this.mob.getBbWidth() * 0.5F) + 1;
            int aboveClearance = Mth.ceil(this.mob.getBbHeight()) + 1;
            int belowClearance = 2;
            shouldRecompute = false;
            for (int i = activePath.getNextNodeIndex(); i < activePath.getNodeCount(); i++) {
                Node node = activePath.getNode(i);
                if (Math.abs(changed.getX() - node.x) <= horizontalClearance
                        && Math.abs(changed.getZ() - node.z) <= horizontalClearance
                        && changed.getY() >= node.y - belowClearance
                        && changed.getY() <= node.y + aboveClearance) {
                    shouldRecompute = true;
                    break;
                }
            }
            if (!shouldRecompute) {
                shouldRecompute = changed.closerToCenterThan(this.mob.position(), horizontalClearance + 1.0D);
            }
        }

        if (shouldRecompute) {
            PathRequestDiagnostics.recordBlockRecompute(this.mob, changed, activePath);
        }
        return shouldRecompute;
    }

    private void recordRetryInvalidation(BlockPos changed) {
        FailedExtendedSearch failure = this.failedExtendedSearch;
        if (failure == null || !hasFailureEvidenceFor(failure.target)) {
            return;
        }
        if (changed.distSqr(failure.target) <= FAILED_TARGET_INVALIDATION_RADIUS_SQR) {
            failure.invalidated = true;
        }
    }

    public boolean consumeRetryInvalidation(BlockPos target) {
        FailedExtendedSearch failure = this.failedExtendedSearch;
        if (failure == null || !failure.invalidated || !failure.target.equals(target)) {
            return false;
        }
        clearFailedExtendedSearch();
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        chainCompletedStaticWalkTarget();
        this.climbTraversal.tick(this.path, this.speedModifier, this.tick);
    }

    private void chainCompletedStaticWalkTarget() {
        Path completedPath = this.path;
        if (completedPath == null
                || !completedPath.isDone()
                || completedPath.canReach()
                || this.isStuck()) {
            return;
        }

        WalkTarget walkTarget = this.mob.getBrain()
                .getMemoryInternal(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        if (walkTarget == null || !(walkTarget.getTarget() instanceof BlockPosTracker)) {
            return;
        }
        if (this.mob instanceof VillagerEntityMCA villager
                && WalkTargetFailureMemory.hasFailureFor(villager, walkTarget.getTarget().currentBlockPosition())) {
            return;
        }

        BlockPos destination = walkTarget.getTarget().currentBlockPosition();
        if (!completedPath.getTarget().equals(destination)
                || destination.distManhattan(this.mob.blockPosition()) <= walkTarget.getCloseEnoughDist()
                || !isUsefulPartialPath(completedPath, destination)) {
            return;
        }
        // An endpoint closer to the goal is not proof that the villager moved.
        // Hand an unproductive continuation back to the destination's Brain
        // failure clock; its producer owns the next retry and eventual give-up.
        if (this.mob.blockPosition().distSqr(completedPath.getNodePos(0)) < 4.0D) {
            recordTerminalPartialFailure(destination);
            return;
        }
        Path nextPath = this.createPath(destination, 0);
        if (nextPath == null
                || (!nextPath.canReach() && !isUsefulPartialPath(nextPath, destination))) {
            // A completed partial remains installed after a failed continuation.
            // Without failure evidence, this same search runs every tick.
            recordTerminalPartialFailure(destination);
            return;
        }
        this.moveTo(nextPath, walkTarget.getSpeedModifier());
    }

    private void recordTerminalPartialFailure(BlockPos destination) {
        if (this.mob instanceof VillagerEntityMCA villager
                && !WalkTargetFailureMemory.hasFailureFor(villager, destination)) {
            WalkTargetFailureMemory.record(villager, destination, this.level.getGameTime());
        }
        PathRequestDiagnostics.recordDeferredProducerRetry(this.mob);
    }

    @Override
    protected void followThePath() {
        if (this.path == null || this.path.isDone()) {
            return;
        }

        resynchronizeGroundedPathAfterFall();
        if (this.path == null || this.path.isDone()) {
            return;
        }

        Vec3 position = this.getTempMobPos();
        if (!this.climbTraversal.followPath(this.path)) {
            super.followThePath();
            return;
        }
        this.doStuckDetection(position);
    }

    @Override
    protected void doStuckDetection(Vec3 position) {
        if (this.path != null && !this.path.isDone()) {
            Node next = this.path.getNextNode();
            if (next.x != this.timeoutCachedNode.getX() || next.y != this.timeoutCachedNode.getY()
                    || next.z != this.timeoutCachedNode.getZ()) {
                // Vanilla accumulates this timer across waypoints. Long MCA routes
                // then time out despite steady progress; time only the current node.
                // Keep vanilla's separate physical-progress check and stalled-node limit.
                this.timeoutTimer = 0L;
            }
        }
        super.doStuckDetection(position);
    }

    private void resynchronizeGroundedPathAfterFall() {
        Path path = this.path;
        if (path == null
                || path.isDone()
                || !this.mob.onGround()
                || this.mob.onClimbable()
                || this.climbTraversal.ownsMovement(path, this.tick)) {
            return;
        }

        int currentIndex = path.getNextNodeIndex();
        int feetY = this.mob.blockPosition().getY();
        if (path.getNodePos(currentIndex).getY() - feetY < FALL_RESYNC_MIN_VERTICAL_DROP) {
            return;
        }

        BlockPos feet = this.mob.blockPosition();
        int maxIndex = Math.min(path.getNodeCount() - 1, currentIndex + FALL_RESYNC_LOOKAHEAD);
        for (int index = currentIndex + 1; index <= maxIndex; index++) {
            BlockPos candidate = path.getNodePos(index);
            if (candidate.getY() <= feetY + 1 && isHorizontallyNear(feet, candidate)) {
                path.setNextNodeIndex(index);
                return;
            }
        }

        this.stop();
    }

    private static boolean isHorizontallyNear(BlockPos first, BlockPos second) {
        int dx = first.getX() - second.getX();
        int dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz <= FALL_RESYNC_HORIZONTAL_DISTANCE * FALL_RESYNC_HORIZONTAL_DISTANCE;
    }

    @Override
    protected double getGroundY(Vec3 position) {
        BlockPos targetPos = BlockPos.containing(position);
        if (this.climbTraversal.isClimbable(targetPos) && this.mob.onClimbable()) {
            return this.mob.getY();
        }
        return super.getGroundY(position);
    }
}
