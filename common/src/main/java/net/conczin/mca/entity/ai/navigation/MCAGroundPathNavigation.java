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
    private static final double EXIT_HEIGHT_TOLERANCE = 0.25D;
    private static final double EXIT_VERTICAL_BIAS = 0.08D;
    private static final double UPWARD_EXIT_CLEARANCE_SPEED = 0.1D;
    private static final double EXIT_CROSSING_TOLERANCE = 0.001D;
    private int lastClimbMovementControlTick = Integer.MIN_VALUE;
    private double lastControlledClimbableVelocity = Double.NaN;

    public MCAGroundPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    public boolean isControllingClimbable() {
        return this.lastClimbMovementControlTick == this.tick
                || getClimbContext() != null;
    }

    public boolean isControllingClimbableMovement() {
        if (this.lastClimbMovementControlTick == this.tick) {
            return true;
        }

        ClimbContext context = getClimbContext();
        return context != null && ownsClimbableMotion(context);
    }

    public double getControlledClimbableVelocity() {
        return this.lastClimbMovementControlTick == this.tick
                ? this.lastControlledClimbableVelocity
                : Double.NaN;
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

        ClimbContext context = getClimbContext();
        return context != null && ownsClimbableMotion(context);
    }

    @Override
    public void tick() {
        super.tick();
        ClimbContext context = getClimbContext();
        if (context != null) {
            double controlledY = calculateControlledClimbableVelocity(context);
            if (!Double.isNaN(controlledY)) {
                this.lastClimbMovementControlTick = this.tick;
                this.lastControlledClimbableVelocity = controlledY;
            }
            applyClimbableMotion(context, controlledY);
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

        Vec3 position = this.getTempMobPos();
        if (hasReachedPartialHeightClimbable(context)) {
            advanceClimbPath();
            this.doStuckDetection(position);
            return;
        }

        if (this.mob.onClimbable()
                || hasLeftClimbableUpward(context)
                || isContinuingDownwardExit(context)) {
            boolean finalClimbableNode = context.exitsClimbable()
                    || this.path.getNextNodeIndex() + 1 >= this.path.getNodeCount();
            boolean reached;
            if (context.exitsClimbable()) {
                reached = hasCompletedClimbableExit(context);
            } else {
                double tolerance = finalClimbableNode
                        ? 0.0D
                        : context.verticalDirection() < 0
                        ? DESCENT_NODE_TOLERANCE
                        : ASCENT_NODE_TOLERANCE;
                reached = hasReachedHeight(context.targetNode().y, context.verticalDirection(), tolerance);
            }

            if (reached) {
                advanceClimbPath();
            }
        }

        this.doStuckDetection(position);
    }

    private void advanceClimbPath() {
        this.path.advance();
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
        if (isClimbable(targetPos) && this.mob.onClimbable()) {
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

        if (this.mob.onClimbable() && nextNodeIndex > 0) {
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

    private void applyClimbableMotion(ClimbContext context, double controlledY) {
        if (Double.isNaN(controlledY)) {
            return;
        }

        BlockPos climbablePos = findAttachedClimbable(context.climbableNode().asBlockPos());
        Vec3 anchor = getClimbableAnchor(climbablePos);
        double targetY = context.targetNode().y;
        boolean continuingUpwardExit = !this.mob.onClimbable()
                && isContinuingUpwardExit(context);
        boolean continuingDownwardExit = isContinuingDownwardExit(context);

        if (!this.mob.onClimbable() && !continuingUpwardExit && !continuingDownwardExit) {
            this.mob.getMoveControl().setWantedPosition(
                    anchor.x(),
                    context.climbableNode().y,
                    anchor.z(),
                    this.speedModifier
            );
            this.mob.setDeltaMovement(
                    horizontalVelocity(anchor.x() - this.mob.getX()),
                    controlledY,
                    horizontalVelocity(anchor.z() - this.mob.getZ())
            );
            return;
        }

        boolean atExitHeight = context.exitsClimbable() && isAtExitHeight(context, targetY);
        double targetX = anchor.x();
        double targetZ = anchor.z();
        if (context.exitsClimbable() && (!context.pathTargetsClimbable() || atExitHeight)) {
            targetX = context.targetNode().x + 0.5D;
            targetZ = context.targetNode().z + 0.5D;
        }

        this.mob.getMoveControl().setWantedPosition(targetX, targetY, targetZ, this.speedModifier);

        this.mob.setXxa(0.0F);
        this.mob.setZza(0.0F);
        this.mob.setDeltaMovement(
                horizontalVelocity(targetX - this.mob.getX()),
                controlledY,
                horizontalVelocity(targetZ - this.mob.getZ())
        );
    }

    private double calculateControlledClimbableVelocity(ClimbContext context) {
        boolean continuingUpwardExit = !this.mob.onClimbable()
                && isContinuingUpwardExit(context);
        boolean continuingDownwardExit = isContinuingDownwardExit(context);
        if (!this.mob.onClimbable() && !continuingUpwardExit && !continuingDownwardExit) {
            if (isEnteringUpwardClimb(context)) {
                return Mth.clamp(
                        context.climbableNode().y - this.mob.getY(),
                        0.0D,
                        CLIMB_VERTICAL_SPEED
                );
            }
            return Double.NaN;
        }

        return getControlledVerticalVelocity(context);
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

        if (context.exitsClimbable()
                && context.verticalDirection() != 0
                && isAtExitHeight(context, targetY)) {
            if (context.verticalDirection() > 0) {
                double minimumUpwardSpeed = isClimbable(this.mob.blockPosition())
                        ? EXIT_VERTICAL_BIAS
                        : UPWARD_EXIT_CLEARANCE_SPEED;
                controlledY = Math.max(controlledY, minimumUpwardSpeed);
            } else {
                controlledY = Math.min(controlledY, -EXIT_VERTICAL_BIAS);
            }
        }

        return controlledY;
    }

    private boolean ownsClimbableMotion(ClimbContext context) {
        return this.mob.onClimbable()
                || isContinuingUpwardExit(context)
                || isContinuingDownwardExit(context)
                || isEnteringUpwardClimb(context);
    }

    private boolean isEnteringUpwardClimb(ClimbContext context) {
        return context.pathTargetsClimbable()
                && context.verticalDirection() > 0
                && context.climbableNode().y - this.mob.getY() >= 0.5D
                && isHorizontallyAlignedWithClimbable(context.climbableNode());
    }

    private boolean isAtExitHeight(ClimbContext context, double targetY) {
        return hasReachedHeight(targetY, context.verticalDirection(), EXIT_HEIGHT_TOLERANCE);
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

    private boolean hasCompletedClimbableExit(ClimbContext context) {
        if (context.targetNode().y == context.climbableNode().y) {
            double tolerance = context.verticalDirection() < 0
                    ? EXIT_CROSSING_TOLERANCE
                    : ASCENT_NODE_TOLERANCE;
            return hasReachedHeight(context.targetNode().y, context.verticalDirection(), tolerance);
        }

        // The last ladder node owns the transition to an offset floor. MineColonies
        // likewise keeps ladder handling active beyond the final ladder block until
        // the entity has crossed the node in the climb direction. For MCA's graph the
        // following floor node already carries that continuation height, so do not
        // release ladder control merely because the ladder block's own Y was crossed.
        return hasCrossedHeight(context.targetNode().y, context.verticalDirection());
    }

    private boolean hasCrossedHeight(double targetY, int verticalDirection) {
        if (verticalDirection > 0) {
            return this.mob.getY() > targetY + EXIT_CROSSING_TOLERANCE;
        }
        if (verticalDirection < 0) {
            return this.mob.getY() < targetY - EXIT_CROSSING_TOLERANCE;
        }
        return Math.abs(targetY - this.mob.getY()) <= ASCENT_NODE_TOLERANCE;
    }

    private boolean hasLeftClimbableUpward(ClimbContext context) {
        return context.exitsClimbable()
                && context.verticalDirection() > 0
                && context.targetNode().y > context.climbableNode().y
                && this.mob.getY() > context.climbableNode().y;
    }

    private boolean isContinuingUpwardExit(ClimbContext context) {
        return hasLeftClimbableUpward(context)
                && this.mob.getY() <= context.targetNode().y + EXIT_CROSSING_TOLERANCE;
    }

    private boolean isContinuingDownwardExit(ClimbContext context) {
        return !this.mob.onClimbable()
                && context.exitsClimbable()
                && context.verticalDirection() < 0
                && isAtExitHeight(context, context.targetNode().y);
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

    private boolean isHorizontallyAlignedWithClimbable(Node climbableNode) {
        double maxDistanceToWaypoint = this.mob.getBbWidth() > 0.75F
                ? this.mob.getBbWidth() / 2.0F
                : 0.75F - this.mob.getBbWidth() / 2.0F;
        return Math.abs(this.mob.getX() - (climbableNode.x + 0.5D)) < maxDistanceToWaypoint
                && Math.abs(this.mob.getZ() - (climbableNode.z + 0.5D)) < maxDistanceToWaypoint;
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
