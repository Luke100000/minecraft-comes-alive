package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public class MCAGroundPathNavigation extends GroundPathNavigation {
    private static final float REQUIRED_PATH_LENGTH = 48.0F;
    private static final int VISITED_NODES_PER_BLOCK = 16;
    private static final int FALL_RESYNC_LOOKAHEAD = 2;
    private static final int FALL_RESYNC_HORIZONTAL_DISTANCE = 2;
    private static final int FALL_RESYNC_MIN_VERTICAL_DROP = 2;
    private final ClimbTraversal climbTraversal;

    public MCAGroundPathNavigation(Mob mob, Level level) {
        super(mob, level);
        this.climbTraversal = new ClimbTraversal(mob, level);
    }

    public boolean isControllingClimbable() {
        return this.climbTraversal.isActive(this.path, this.tick);
    }

    public boolean isControllingClimbableMovement() {
        return this.climbTraversal.ownsMovement(this.path, this.tick);
    }

    public double getControlledClimbableVelocity() {
        return this.climbTraversal.controlledVerticalVelocity(this.tick);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new MCAWalkNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        this.nodeEvaluator.setCanOpenDoors(true);
        int requiredPathBudget = Mth.floor(REQUIRED_PATH_LENGTH * VISITED_NODES_PER_BLOCK);
        int ordinaryBudget = Math.max(maxVisitedNodes, requiredPathBudget);
        return new PathFinder(this.nodeEvaluator, ordinaryBudget) {
            @Override
            public Path findPath(PathNavigationRegion region, Mob mob, Set<BlockPos> targets,
                                 float maxPathLength, int reachRange, float visitedNodesMultiplier) {
                float rangeMultiplier = Math.max(
                        1.0F,
                        maxPathLength * VISITED_NODES_PER_BLOCK / ordinaryBudget
                );
                return super.findPath(region, mob, targets, maxPathLength, reachRange,
                        visitedNodesMultiplier * rangeMultiplier);
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
            float extendedPathLength = Math.max(
                    (float)Config.getInstance().getVillagerPathfindingDistance(),
                    ordinaryPathLength
            );
            if (requiresExtendedPath(this.mob, target)) {
                float pathLength = this.mob.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                        ? extendedPathLength
                        : ordinaryPathLength;
                return super.createPath(
                        targets, radiusOffset, above, reachRange, pathLength
                );
            }

            Path ordinaryPath = super.createPath(targets, radiusOffset, above, reachRange, ordinaryPathLength);
            if (ordinaryPath == null
                    || ordinaryPath.canReach()
                    || isUsefulPartialPath(ordinaryPath, target)
                    || extendedPathLength <= ordinaryPathLength) {
                return ordinaryPath;
            }

            return preferEscalatedPath(
                    ordinaryPath,
                    super.createPath(targets, radiusOffset, above, reachRange, extendedPathLength)
            );
        }
        return super.createPath(targets, radiusOffset, above, reachRange, ordinaryPathLength);
    }

    private static Path preferEscalatedPath(Path boundedPath, Path escalatedPath) {
        if (escalatedPath != null
                && (escalatedPath.canReach() || isBetterPartialPath(escalatedPath, boundedPath))) {
            return escalatedPath;
        }
        return boundedPath;
    }

    private static boolean isBetterPartialPath(Path candidate, Path current) {
        return candidate.getDistToTarget() < current.getDistToTarget();
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

    private static float getOrdinaryPathLength(Mob mob) {
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
    public int getSurfaceY() {
        if (this.mob.isInWater() && this.canFloat()) {
            int surfaceY = this.mob.getBlockY();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(this.mob.getX(), surfaceY, this.mob.getZ());
            int steps = 0;

            while (this.level.getFluidState(pos).is(FluidTags.WATER)) {
                pos.setY(++surfaceY);
                if (++steps > 16) {
                    return this.mob.getBlockY();
                }
            }

            return surfaceY;
        }

        return Mth.floor(this.mob.getY() + 0.5D);
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
        if (!canUpdatePath()) {
            this.hasDelayedRecomputation = true;
            return;
        }
        super.recomputePath();
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
        if (this.mob.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) {
            return;
        }

        BlockPos destination = walkTarget.getTarget().currentBlockPosition();
        if (!completedPath.getTarget().equals(destination)
                || destination.distManhattan(this.mob.blockPosition()) <= walkTarget.getCloseEnoughDist()
                || !isUsefulPartialPath(completedPath, destination)) {
            return;
        }
        Path nextPath = this.createPath(destination, 0);
        if (nextPath == null
                || (!nextPath.canReach() && !isUsefulPartialPath(nextPath, destination))) {
            return;
        }
        this.moveTo(nextPath, walkTarget.getSpeedModifier());
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

        for (int index = currentIndex; index < path.getNodeCount(); index++) {
            if (path.getNodePos(index).getY() <= feetY + 1) {
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
