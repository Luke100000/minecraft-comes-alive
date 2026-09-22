package net.conczin.mca.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public class MCAGroundPathNavigation extends GroundPathNavigation {
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
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    @Override
    protected Path createPath(Set<BlockPos> targets, int radiusOffset, boolean above, int reachRange) {
        WalkTarget walkTarget = this.mob.getBrain()
                .getMemoryInternal(MemoryModuleType.WALK_TARGET)
                .orElse(null);
        if (walkTarget != null
                && walkTarget.getTarget() instanceof LongDistancePathTarget longDistanceTarget
                && targetsLongDistanceDestination(targets, longDistanceTarget)) {
            float maxPathLength = (float)Math.max(
                    longDistanceTarget.requestedPathLength(),
                    this.mob.getAttributeValue(Attributes.FOLLOW_RANGE)
            );
            return super.createPath(targets, radiusOffset, above, reachRange, maxPathLength);
        }
        return super.createPath(targets, radiusOffset, above, reachRange);
    }

    private static boolean targetsLongDistanceDestination(Set<BlockPos> targets, LongDistancePathTarget target) {
        if (targets.size() != 1) {
            return false;
        }
        BlockPos pathTarget = targets.iterator().next();
        BlockPos logicalTarget = target.currentBlockPosition();
        return pathTarget.getX() == logicalTarget.getX() && pathTarget.getZ() == logicalTarget.getZ();
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
        this.climbTraversal.tick(this.path, this.speedModifier, this.tick);
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
