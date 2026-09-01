package net.conczin.mca.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

public class MCAGroundPathNavigation extends GroundPathNavigation {
    private static final double CLIMB_VERTICAL_SPEED = 0.16D;
    private static final double CLIMB_HORIZONTAL_SPEED = 0.12D;
    private static final double CLIMB_HORIZONTAL_GAIN = 0.35D;
    private static final double LADDER_ENTRY_OFFSET = 0.1D;
    private static final double ASCENT_NODE_TOLERANCE = 0.20D;
    private static final double DESCENT_NODE_TOLERANCE = 0.08D;
    private static final double FOLLOW_REVERSAL_MIN_VERTICAL_DISTANCE = 1.0D;
    private int lastClimbNodeAdvanceTick = Integer.MIN_VALUE;

    public MCAGroundPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    public boolean isControllingClimbable() {
        return getClimbContext() != null;
    }

    public boolean isControllingClimbableMovement() {
        ClimbContext context = getClimbContext();
        return context != null && context.pathTargetsClimbable();
    }

    public boolean canRetargetClimbableFollow(int targetY) {
        if (this.lastClimbNodeAdvanceTick != this.tick) {
            return false;
        }

        ClimbContext context = getClimbContext();
        if (context == null || context.verticalDirection() == 0) {
            return false;
        }

        double verticalDelta = targetY - this.mob.getY();
        if (Math.abs(verticalDelta) <= FOLLOW_REVERSAL_MIN_VERTICAL_DISTANCE) {
            return false;
        }

        int targetDirection = verticalDelta > 0.0D ? 1 : -1;
        return targetDirection == -context.verticalDirection();
    }

    public double getControlledClimbableVelocity() {
        ClimbContext context = getClimbContext();
        if (context == null) {
            return Double.NaN;
        }

        if (!context.pathTargetsClimbable()) {
            return getControlledVerticalVelocity(context);
        }

        boolean continuingUpwardExit = !this.mob.onClimbable()
                && isContinuingUpwardExit(context);
        if (!this.mob.onClimbable() && !continuingUpwardExit) {
            return Double.NaN;
        }

        return getControlledVerticalVelocity(context);
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
        return super.canUpdatePath() || this.mob.onClimbable();
    }

    @Override
    public void tick() {
        super.tick();
        ClimbContext context = getClimbContext();
        if (context != null) {
            applyClimbableMotion(context);
        }
    }

    @Override
    protected void followThePath() {
        if (this.path == null || this.path.isDone()) {
            return;
        }

        ClimbContext context = getClimbContext();
        if (context == null) {
            super.followThePath();
            return;
        }

        if (!context.pathTargetsClimbable()) {
            super.followThePath();
            return;
        }

        Vec3 position = this.getTempMobPos();
        if (hasReachedPartialHeightClimbable(context)) {
            advanceClimbPath(context);
            this.doStuckDetection(position);
            return;
        }

        if (this.mob.onClimbable() || hasLeftClimbableUpward(context)) {
            boolean finalClimbableNode = context.exitsClimbable()
                    || this.path.getNextNodeIndex() + 1 >= this.path.getNodeCount();
            boolean reached;
            if (context.exitsClimbable()) {
                reached = hasReachedHeight(
                        context.targetNode().y,
                        context.verticalDirection(),
                        0.0D
                );
            } else {
                double tolerance = finalClimbableNode
                        ? 0.0D
                        : context.verticalDirection() < 0
                        ? DESCENT_NODE_TOLERANCE
                        : ASCENT_NODE_TOLERANCE;
                reached = hasReachedHeight(context.targetNode().y, context.verticalDirection(), tolerance);
            }

            if (reached) {
                advanceClimbPath(context);
            }
        }

        this.doStuckDetection(position);
    }

    private void advanceClimbPath(ClimbContext context) {
        this.path.advance();
        if (!context.exitsClimbable() && context.verticalDirection() != 0) {
            this.lastClimbNodeAdvanceTick = this.tick;
        }
    }

    private boolean hasReachedPartialHeightClimbable(ClimbContext context) {
        if (this.mob.onClimbable() || !this.mob.onGround()) {
            return false;
        }

        Node climbableNode = context.climbableNode();
        if (this.mob.blockPosition().getY() >= climbableNode.y
                || Mth.floor(this.mob.getY() + 0.5D) != climbableNode.y
                || Math.abs(this.mob.getY() - climbableNode.y) >= 1.0D) {
            return false;
        }

        double maxDistanceToWaypoint = this.mob.getBbWidth() > 0.75F
                ? this.mob.getBbWidth() / 2.0F
                : 0.75F - this.mob.getBbWidth() / 2.0F;
        return Math.abs(this.mob.getX() - (climbableNode.x + 0.5D)) < maxDistanceToWaypoint
                && Math.abs(this.mob.getZ() - (climbableNode.z + 0.5D)) < maxDistanceToWaypoint;
    }

    @Override
    protected double getGroundY(Vec3 position) {
        BlockPos targetPos = BlockPos.containing(position);
        if (isClimbable(targetPos)) {
            return this.mob.getY();
        }
        return super.getGroundY(position);
    }

    private ClimbContext getClimbContext() {
        Path path = this.path;
        if (path == null || path.getNodeCount() == 0) {
            return null;
        }

        if (path.isDone()) {
            return getCompletedClimbContext(path);
        }

        int nextNodeIndex = path.getNextNodeIndex();
        if (nextNodeIndex < 0 || nextNodeIndex >= path.getNodeCount()) {
            return null;
        }

        Node nextNode = path.getNode(nextNodeIndex);
        if (isClimbable(nextNode.asBlockPos())) {
            Node followingNode = nextNodeIndex + 1 < path.getNodeCount()
                    ? path.getNode(nextNodeIndex + 1)
                    : null;
            boolean exitsClimbable = followingNode != null
                    && !isClimbable(followingNode.asBlockPos());
            return new ClimbContext(
                    nextNode,
                    exitsClimbable ? followingNode : nextNode,
                    true,
                    exitsClimbable,
                    getVerticalDirection(path, nextNodeIndex)
            );
        }

        if (nextNodeIndex > 0) {
            Node previousNode = path.getNode(nextNodeIndex - 1);
            if (isClimbable(previousNode.asBlockPos())) {
                return new ClimbContext(
                        previousNode,
                        nextNode,
                        false,
                        true,
                        getVerticalDirection(path, nextNodeIndex - 1)
                );
            }
        }

        return null;
    }

    private ClimbContext getCompletedClimbContext(Path path) {
        if (!this.mob.onClimbable()) {
            return null;
        }

        int endNodeIndex = path.getNodeCount() - 1;
        Node endNode = path.getNode(endNodeIndex);
        if (isClimbable(endNode.asBlockPos())) {
            return new ClimbContext(
                    endNode,
                    endNode,
                    true,
                    false,
                    getVerticalDirection(path, endNodeIndex)
            );
        }

        return null;
    }

    private static int getVerticalDirection(Path path, int climbableNodeIndex) {
        Node climbableNode = path.getNode(climbableNodeIndex);
        if (climbableNodeIndex + 1 < path.getNodeCount()) {
            Node followingNode = path.getNode(climbableNodeIndex + 1);
            int direction = Integer.compare(followingNode.y, climbableNode.y);
            if (direction != 0) {
                return direction;
            }
        }

        if (climbableNodeIndex > 0) {
            Node previousNode = path.getNode(climbableNodeIndex - 1);
            int direction = Integer.compare(climbableNode.y, previousNode.y);
            if (direction != 0) {
                return direction;
            }
        }

        // A partial path can terminate on a single climbable height. Preserve the
        // path's intended vertical direction instead of deriving it from overshoot.
        return Integer.compare(path.getTarget().getY(), climbableNode.y);
    }

    private void applyClimbableMotion(ClimbContext context) {
        if (!context.pathTargetsClimbable()) {
            Vec3 movement = this.mob.getDeltaMovement();
            this.mob.setDeltaMovement(
                    movement.x(),
                    getControlledVerticalVelocity(context),
                    movement.z()
            );
            return;
        }

        BlockPos climbablePos = findAttachedClimbable(context.climbableNode().asBlockPos());
        Vec3 anchor = getClimbableAnchor(climbablePos);
        double targetY = context.targetNode().y;
        boolean continuingUpwardExit = !this.mob.onClimbable()
                && isContinuingUpwardExit(context);

        if (!this.mob.onClimbable() && !continuingUpwardExit) {
            this.mob.getMoveControl().setWantedPosition(
                    anchor.x(), this.mob.getY(), anchor.z(), this.speedModifier
            );
            Vec3 movement = this.mob.getDeltaMovement();
            this.mob.setDeltaMovement(
                    horizontalVelocity(anchor.x() - this.mob.getX()),
                    movement.y(),
                    horizontalVelocity(anchor.z() - this.mob.getZ())
            );
            return;
        }

        this.mob.getMoveControl().setWantedPosition(anchor.x(), targetY, anchor.z(), this.speedModifier);

        this.mob.setXxa(0.0F);
        this.mob.setZza(0.0F);
        this.mob.setDeltaMovement(
                horizontalVelocity(anchor.x() - this.mob.getX()),
                getControlledVerticalVelocity(context),
                horizontalVelocity(anchor.z() - this.mob.getZ())
        );
    }

    private double getControlledVerticalVelocity(ClimbContext context) {
        double targetY = context.targetNode().y;
        double verticalDelta = targetY - this.mob.getY();
        double controlledY;
        if (context.verticalDirection() > 0) {
            controlledY = Mth.clamp(verticalDelta, 0.0D, CLIMB_VERTICAL_SPEED);
        } else if (context.verticalDirection() < 0) {
            controlledY = Mth.clamp(verticalDelta, -CLIMB_VERTICAL_SPEED, 0.0D);
        } else {
            controlledY = Mth.clamp(verticalDelta, -CLIMB_VERTICAL_SPEED, CLIMB_VERTICAL_SPEED);
        }

        return controlledY;
    }

    private boolean hasReachedHeight(double targetY, int verticalDirection, double tolerance) {
        if (verticalDirection > 0) {
            return this.mob.getY() >= targetY - tolerance;
        }
        if (verticalDirection < 0) {
            return this.mob.getY() <= targetY + tolerance;
        }
        return Math.abs(targetY - this.mob.getY()) <= tolerance;
    }

    private boolean hasLeftClimbableUpward(ClimbContext context) {
        return context.exitsClimbable()
                && context.verticalDirection() > 0
                && context.targetNode().y > context.climbableNode().y
                && this.mob.getY() > context.climbableNode().y;
    }

    private boolean isContinuingUpwardExit(ClimbContext context) {
        return hasLeftClimbableUpward(context)
                && this.mob.getY() <= context.targetNode().y;
    }

    private Vec3 getClimbableAnchor(BlockPos pos) {
        BlockState state = this.level.getBlockState(pos);
        Vec3 center = Vec3.atCenterOf(pos);
        if (state.getBlock() instanceof LadderBlock) {
            Direction facing = state.getValue(LadderBlock.FACING);
            return center.add(
                    facing.getStepX() * LADDER_ENTRY_OFFSET,
                    0.0D,
                    facing.getStepZ() * LADDER_ENTRY_OFFSET
            );
        }
        return center;
    }

    private static double horizontalVelocity(double delta) {
        return Mth.clamp(
                delta * CLIMB_HORIZONTAL_GAIN,
                -CLIMB_HORIZONTAL_SPEED,
                CLIMB_HORIZONTAL_SPEED
        );
    }

    private BlockPos findAttachedClimbable(BlockPos fallback) {
        BlockPos mobPos = this.mob.blockPosition();
        if (isClimbable(mobPos)) {
            return mobPos;
        }

        BlockPos above = mobPos.above();
        if (isClimbable(above)) {
            return above;
        }

        BlockPos below = mobPos.below();
        if (isClimbable(below)) {
            return below;
        }

        return fallback;
    }

    private boolean isClimbable(BlockPos pos) {
        return this.level.getBlockState(pos).is(BlockTags.CLIMBABLE);
    }

    private record ClimbContext(
            Node climbableNode,
            Node targetNode,
            boolean pathTargetsClimbable,
            boolean exitsClimbable,
            int verticalDirection
    ) {
    }
}
