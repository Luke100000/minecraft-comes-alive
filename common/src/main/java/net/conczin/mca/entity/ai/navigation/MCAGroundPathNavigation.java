package net.conczin.mca.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

public class MCAGroundPathNavigation extends GroundPathNavigation {
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

    public boolean shouldKeepCurrentClimbPathForFollowTarget(int targetY) {
        return this.climbTraversal.shouldKeepCurrentPathForFollowTarget(this.path, targetY);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new MCAWalkNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        this.nodeEvaluator.setCanOpenDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
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
    public void tick() {
        super.tick();
        this.climbTraversal.tick(this.path, this.speedModifier, this.tick);
    }

    @Override
    protected void followThePath() {
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
    protected double getGroundY(Vec3 position) {
        BlockPos targetPos = BlockPos.containing(position);
        if (this.climbTraversal.isClimbable(targetPos) && this.mob.onClimbable()) {
            return this.mob.getY();
        }
        return super.getGroundY(position);
    }
}
