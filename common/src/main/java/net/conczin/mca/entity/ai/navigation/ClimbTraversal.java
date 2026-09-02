package net.conczin.mca.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the climb-specific interpretation and execution of an already-computed path.
 * Path construction stays in {@link MCAWalkNodeEvaluator}; the navigation only asks
 * this controller whether the current path segment needs climb handling.
 */
final class ClimbTraversal {
    private static final double VERTICAL_SPEED = 0.16D;
    private static final double HORIZONTAL_SPEED = 0.12D;
    private static final double HORIZONTAL_GAIN = 0.35D;
    private static final double LADDER_ENTRY_OFFSET = 0.1D;
    private static final double ASCENT_NODE_TOLERANCE = 0.20D;
    private static final double DESCENT_NODE_TOLERANCE = 0.08D;
    private static final double EXIT_HEIGHT_TOLERANCE = 0.25D;
    private static final double EXIT_VERTICAL_BIAS = 0.08D;
    private static final double UPWARD_EXIT_CLEARANCE_SPEED = 0.1D;
    private static final double EXIT_CROSSING_TOLERANCE = 0.001D;

    private final Mob mob;
    private final Level level;
    private int lastMovementControlTick = Integer.MIN_VALUE;
    private double lastControlledVerticalVelocity = Double.NaN;

    ClimbTraversal(Mob mob, Level level) {
        this.mob = mob;
        this.level = level;
    }

    boolean isActive(@Nullable Path path, int navigationTick) {
        return this.lastMovementControlTick == navigationTick || resolve(path) != null;
    }

    boolean ownsMovement(@Nullable Path path, int navigationTick) {
        if (this.lastMovementControlTick == navigationTick) {
            return true;
        }

        Context context = resolve(path);
        return context != null && ownsMotion(context);
    }

    double controlledVerticalVelocity(int navigationTick) {
        return this.lastMovementControlTick == navigationTick
                ? this.lastControlledVerticalVelocity
                : Double.NaN;
    }

    boolean shouldKeepCurrentPathForFollowTarget(@Nullable Path path, int targetY) {
        Context context = resolve(path);
        if (context == null || context.verticalDirection() == 0) {
            return false;
        }

        double verticalDelta = targetY - this.mob.getY();
        if (Math.abs(verticalDelta) <= 1.0D) {
            return true;
        }

        int targetDirection = verticalDelta > 0.0D ? 1 : -1;
        return targetDirection == context.verticalDirection();
    }

    void tick(@Nullable Path path, double speedModifier, int navigationTick) {
        Context context = resolve(path);
        if (context == null) {
            return;
        }

        double controlledY = calculateVerticalVelocity(context);
        if (!Double.isNaN(controlledY)) {
            this.lastMovementControlTick = navigationTick;
            this.lastControlledVerticalVelocity = controlledY;
        }
        applyMotion(context, controlledY, speedModifier);
    }

    /**
     * @return true when the current path segment is climb-owned and vanilla path
     * following must be skipped for this tick.
     */
    boolean followPath(Path path) {
        Context context = resolve(path);
        if (context == null) {
            return false;
        }

        if (hasReachedPartialHeightClimbable(context)) {
            path.advance();
            return true;
        }

        if (this.mob.onClimbable()
                || hasLeftClimbableUpward(context)
                || isContinuingDownwardExit(context)) {
            boolean finalClimbableNode = context.exitsClimbable()
                    || path.getNextNodeIndex() + 1 >= path.getNodeCount();
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
                path.advance();
            }
        }
        return true;
    }

    boolean isClimbable(BlockPos pos) {
        return this.level.getBlockState(pos).is(BlockTags.CLIMBABLE);
    }

    @Nullable
    private Context resolve(@Nullable Path path) {
        if (path == null || path.getNodeCount() == 0) {
            return null;
        }

        if (path.isDone()) {
            return resolveCompleted(path);
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
            return new Context(
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
                return new Context(
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

    @Nullable
    private Context resolveCompleted(Path path) {
        if (!this.mob.onClimbable()) {
            return null;
        }

        int endNodeIndex = path.getNodeCount() - 1;
        Node endNode = path.getNode(endNodeIndex);
        if (!isClimbable(endNode.asBlockPos())) {
            return null;
        }

        return new Context(
                endNode,
                endNode,
                true,
                false,
                getVerticalDirection(path, endNodeIndex)
        );
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

        return Integer.compare(path.getTarget().getY(), climbableNode.y);
    }

    private boolean hasReachedPartialHeightClimbable(Context context) {
        if (this.mob.onClimbable() || !this.mob.onGround()) {
            return false;
        }

        Node climbableNode = context.climbableNode();
        if (this.mob.blockPosition().getY() >= climbableNode.y
                || Mth.floor(this.mob.getY() + 0.5D) != climbableNode.y
                || Math.abs(this.mob.getY() - climbableNode.y) >= 1.0D) {
            return false;
        }

        return isHorizontallyAlignedWithClimbable(climbableNode);
    }

    private void applyMotion(Context context, double controlledY, double speedModifier) {
        if (Double.isNaN(controlledY)) {
            return;
        }

        if (isApproachingClimbableExitNode(context)) {
            Vec3 pathAnchor = getClimbableAnchor(context.climbableNode().asBlockPos());
            this.mob.getMoveControl().setWantedPosition(
                    pathAnchor.x(),
                    context.climbableNode().y,
                    pathAnchor.z(),
                    speedModifier
            );
            this.mob.setXxa(0.0F);
            this.mob.setZza(0.0F);
            this.mob.setDeltaMovement(
                    horizontalVelocity(pathAnchor.x() - this.mob.getX()),
                    controlledY,
                    horizontalVelocity(pathAnchor.z() - this.mob.getZ())
            );
            return;
        }

        BlockPos climbablePos = findAttachedClimbable(context.climbableNode().asBlockPos());
        Vec3 anchor = getClimbableAnchor(climbablePos);
        double targetY = context.targetNode().y;
        boolean continuingUpwardExit = !this.mob.onClimbable() && isContinuingUpwardExit(context);
        boolean continuingDownwardExit = isContinuingDownwardExit(context);

        if (!this.mob.onClimbable() && !continuingUpwardExit && !continuingDownwardExit) {
            this.mob.getMoveControl().setWantedPosition(
                    anchor.x(),
                    context.climbableNode().y,
                    anchor.z(),
                    speedModifier
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
        if (context.exitsClimbable()
                && (!context.pathTargetsClimbable()
                || atExitHeight
                || context.verticalDirection() < 0)) {
            targetX = context.targetNode().x + 0.5D;
            targetZ = context.targetNode().z + 0.5D;
        }

        this.mob.getMoveControl().setWantedPosition(targetX, targetY, targetZ, speedModifier);
        this.mob.setXxa(0.0F);
        this.mob.setZza(0.0F);
        this.mob.setDeltaMovement(
                horizontalVelocity(targetX - this.mob.getX()),
                controlledY,
                horizontalVelocity(targetZ - this.mob.getZ())
        );
    }

    private double calculateVerticalVelocity(Context context) {
        if (isApproachingClimbableExitNode(context)) {
            return 0.0D;
        }

        boolean continuingUpwardExit = !this.mob.onClimbable() && isContinuingUpwardExit(context);
        boolean continuingDownwardExit = isContinuingDownwardExit(context);
        if (!this.mob.onClimbable() && !continuingUpwardExit && !continuingDownwardExit) {
            if (isEnteringUpwardClimb(context)) {
                return Mth.clamp(
                        context.climbableNode().y - this.mob.getY(),
                        0.0D,
                        VERTICAL_SPEED
                );
            }
            return Double.NaN;
        }

        return getControlledVerticalVelocity(context);
    }

    private double getControlledVerticalVelocity(Context context) {
        double targetY = context.targetNode().y;
        double verticalDelta = targetY - this.mob.getY();
        double controlledY;
        if (context.verticalDirection() > 0) {
            controlledY = Mth.clamp(verticalDelta, 0.0D, VERTICAL_SPEED);
        } else if (context.verticalDirection() < 0) {
            controlledY = Mth.clamp(verticalDelta, -VERTICAL_SPEED, 0.0D);
        } else {
            controlledY = Mth.clamp(verticalDelta, -VERTICAL_SPEED, VERTICAL_SPEED);
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

    private boolean ownsMotion(Context context) {
        return this.mob.onClimbable()
                || isContinuingUpwardExit(context)
                || isContinuingDownwardExit(context)
                || isEnteringUpwardClimb(context);
    }

    private boolean isEnteringUpwardClimb(Context context) {
        return context.pathTargetsClimbable()
                && context.verticalDirection() > 0
                && context.climbableNode().y - this.mob.getY() >= 0.5D
                && isHorizontallyAlignedWithClimbable(context.climbableNode());
    }

    private boolean isApproachingClimbableExitNode(Context context) {
        return this.mob.onClimbable()
                && context.pathTargetsClimbable()
                && context.exitsClimbable()
                && !isHorizontallyAlignedWithClimbable(context.climbableNode())
                && !hasPassedClimbableTowardExit(context);
    }

    private boolean hasPassedClimbableTowardExit(Context context) {
        double climbX = context.climbableNode().x + 0.5D;
        double climbZ = context.climbableNode().z + 0.5D;
        double exitDx = context.targetNode().x + 0.5D - climbX;
        double exitDz = context.targetNode().z + 0.5D - climbZ;
        return (this.mob.getX() - climbX) * exitDx + (this.mob.getZ() - climbZ) * exitDz > 0.0D;
    }

    private boolean isAtExitHeight(Context context, double targetY) {
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

    private boolean hasCompletedClimbableExit(Context context) {
        if (context.targetNode().y == context.climbableNode().y) {
            double tolerance = context.verticalDirection() < 0
                    ? EXIT_CROSSING_TOLERANCE
                    : ASCENT_NODE_TOLERANCE;
            return hasReachedHeight(context.targetNode().y, context.verticalDirection(), tolerance);
        }

        if (context.verticalDirection() < 0
                && context.targetNode().y < context.climbableNode().y
                && hasClearedClimbableSupportTowardExit(context)) {
            return true;
        }

        return hasCrossedHeight(context.targetNode().y, context.verticalDirection());
    }

    private boolean hasClearedClimbableSupportTowardExit(Context context) {
        double climbX = context.climbableNode().x + 0.5D;
        double climbZ = context.climbableNode().z + 0.5D;
        double exitX = context.targetNode().x + 0.5D;
        double exitZ = context.targetNode().z + 0.5D;
        double dx = exitX - climbX;
        double dz = exitZ - climbZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance <= 1.0E-6D) {
            return false;
        }

        double progress = ((this.mob.getX() - climbX) * dx + (this.mob.getZ() - climbZ) * dz) / distance;
        double halfWidth = this.mob.getBbWidth() / 2.0D;
        return progress >= 0.5D + halfWidth;
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

    private boolean hasLeftClimbableUpward(Context context) {
        return context.exitsClimbable()
                && context.verticalDirection() > 0
                && context.targetNode().y > context.climbableNode().y
                && this.mob.getY() > context.climbableNode().y;
    }

    private boolean isContinuingUpwardExit(Context context) {
        return hasLeftClimbableUpward(context)
                && this.mob.getY() <= context.targetNode().y + EXIT_CROSSING_TOLERANCE;
    }

    private boolean isContinuingDownwardExit(Context context) {
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
        return Mth.clamp(delta * HORIZONTAL_GAIN, -HORIZONTAL_SPEED, HORIZONTAL_SPEED);
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
        return isClimbable(below) ? below : fallback;
    }

    private record Context(
            Node climbableNode,
            Node targetNode,
            boolean pathTargetsClimbable,
            boolean exitsClimbable,
            int verticalDirection
    ) {
    }
}
