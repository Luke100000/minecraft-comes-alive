package net.mca.entity.ai.navigation;

import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.LadderBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** 1.20.1 equivalent of the newer MCA ground navigation implementation. */
public class MCAGroundPathNavigation extends MobNavigation {
    private static final double CLIMB_VERTICAL_SPEED = 0.16D;
    private static final double CLIMB_HORIZONTAL_SPEED = 0.12D;
    private static final double CLIMB_HORIZONTAL_GAIN = 0.35D;
    private static final double LADDER_ENTRY_OFFSET = 0.1D;
    private static final double ASCENT_NODE_TOLERANCE = 0.20D;
    private static final double DESCENT_NODE_TOLERANCE = 0.08D;
    private static final double EXIT_HEIGHT_TOLERANCE = 0.25D;
    private static final double EXIT_VERTICAL_BIAS = 0.08D;

    public MCAGroundPathNavigation(MobEntity mobEntity, World world) {
        super(mobEntity, world);
    }

    public boolean isControllingClimbable() {
        return getClimbContext() != null;
    }

    @Override
    protected PathNodeNavigator createPathNodeNavigator(int range) {
        nodeMaker = new MCAWalkNodeEvaluator();
        nodeMaker.setCanEnterOpenDoors(true);
        nodeMaker.setCanOpenDoors(true);
        return new PathNodeNavigator(nodeMaker, range);
    }

    @Override
    public boolean canJumpToNext(PathNodeType type) {
        return type != PathNodeType.DOOR_OPEN && super.canJumpToNext(type);
    }

    @Override
    protected Vec3d getPos() {
        return new Vec3d(entity.getX(), getWaterAwareSurfaceY(), entity.getZ());
    }

    @Override
    protected boolean isAtValidPosition() {
        return super.isAtValidPosition() || entity.isClimbing();
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
    protected void continueFollowingPath() {
        if (currentPath == null || currentPath.isFinished()) {
            return;
        }

        ClimbContext context = getClimbContext();
        if (context == null) {
            super.continueFollowingPath();
            return;
        }

        Vec3d position = getPos();
        if (context.pathTargetsClimbable() && entity.isClimbing()) {
            double tolerance = context.verticalDirection() < 0
                    ? DESCENT_NODE_TOLERANCE
                    : ASCENT_NODE_TOLERANCE;
            if (hasReachedHeight(context.targetNode().y, context.verticalDirection(), tolerance)) {
                currentPath.next();
            }
        }

        checkTimeouts(position);
    }

    private int getWaterAwareSurfaceY() {
        if (entity.isTouchingWater() && canSwim()) {
            int surfaceY = entity.getBlockY();
            BlockPos.Mutable pos = new BlockPos.Mutable(entity.getX(), surfaceY, entity.getZ());
            int steps = 0;

            while (world.getFluidState(pos).isIn(FluidTags.WATER)) {
                pos.setY(++surfaceY);
                if (++steps > 16) {
                    return entity.getBlockY();
                }
            }

            return surfaceY;
        }

        return MathHelper.floor(entity.getY() + 0.5D);
    }

    private ClimbContext getClimbContext() {
        Path path = currentPath;
        if (path == null || path.isFinished()) {
            return null;
        }

        int nextNodeIndex = path.getCurrentNodeIndex();
        if (nextNodeIndex < 0 || nextNodeIndex >= path.getLength()) {
            return null;
        }

        PathNode nextNode = path.getNode(nextNodeIndex);
        if (isClimbable(nextNode.getBlockPos())) {
            PathNode followingNode = nextNodeIndex + 1 < path.getLength()
                    ? path.getNode(nextNodeIndex + 1)
                    : null;
            boolean exitsClimbable = followingNode != null && !isClimbable(followingNode.getBlockPos());
            return new ClimbContext(
                    nextNode,
                    exitsClimbable ? followingNode : nextNode,
                    true,
                    exitsClimbable,
                    getVerticalDirection(path, nextNodeIndex)
            );
        }

        if (entity.isClimbing() && nextNodeIndex > 0) {
            PathNode previousNode = path.getNode(nextNodeIndex - 1);
            if (isClimbable(previousNode.getBlockPos())) {
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

    private static int getVerticalDirection(Path path, int climbableNodeIndex) {
        PathNode climbableNode = path.getNode(climbableNodeIndex);
        if (climbableNodeIndex + 1 < path.getLength()) {
            PathNode followingNode = path.getNode(climbableNodeIndex + 1);
            int direction = Integer.compare(followingNode.y, climbableNode.y);
            if (direction != 0) {
                return direction;
            }
        }

        if (climbableNodeIndex > 0) {
            PathNode previousNode = path.getNode(climbableNodeIndex - 1);
            return Integer.compare(climbableNode.y, previousNode.y);
        }

        return 0;
    }

    private void applyClimbableMotion(ClimbContext context) {
        BlockPos climbablePos = findAttachedClimbable(context.climbableNode().getBlockPos());
        Vec3d anchor = getClimbableAnchor(climbablePos);

        if (!entity.isClimbing()) {
            entity.getMoveControl().moveTo(anchor.x, entity.getY(), anchor.z, speed);
            Vec3d movement = entity.getVelocity();
            entity.setVelocity(
                    horizontalVelocity(anchor.x - entity.getX()),
                    movement.y,
                    horizontalVelocity(anchor.z - entity.getZ())
            );
            return;
        }

        double targetY = context.targetNode().y;
        boolean atExitHeight = context.exitsClimbable() && isAtExitHeight(context, targetY);
        double targetX = anchor.x;
        double targetZ = anchor.z;
        if (context.exitsClimbable() && (!context.pathTargetsClimbable() || atExitHeight)) {
            targetX = context.targetNode().x + 0.5D;
            targetZ = context.targetNode().z + 0.5D;
        }

        entity.getMoveControl().moveTo(targetX, targetY, targetZ, speed);

        double verticalDelta = targetY - entity.getY();
        double controlledY;
        if (context.verticalDirection() > 0) {
            controlledY = MathHelper.clamp(verticalDelta, 0.0D, CLIMB_VERTICAL_SPEED);
        } else if (context.verticalDirection() < 0) {
            controlledY = MathHelper.clamp(verticalDelta, -CLIMB_VERTICAL_SPEED, 0.0D);
        } else {
            controlledY = MathHelper.clamp(verticalDelta, -CLIMB_VERTICAL_SPEED, CLIMB_VERTICAL_SPEED);
        }

        if (atExitHeight && context.verticalDirection() != 0) {
            controlledY = context.verticalDirection() > 0
                    ? Math.max(controlledY, EXIT_VERTICAL_BIAS)
                    : Math.min(controlledY, -EXIT_VERTICAL_BIAS);
        }

        entity.sidewaysSpeed = 0.0F;
        entity.forwardSpeed = 0.0F;
        entity.setVelocity(
                horizontalVelocity(targetX - entity.getX()),
                controlledY,
                horizontalVelocity(targetZ - entity.getZ())
        );
    }

    private boolean isAtExitHeight(ClimbContext context, double targetY) {
        return hasReachedHeight(targetY, context.verticalDirection(), EXIT_HEIGHT_TOLERANCE);
    }

    private boolean hasReachedHeight(double targetY, int verticalDirection, double tolerance) {
        if (verticalDirection > 0) {
            return entity.getY() >= targetY - tolerance;
        }
        if (verticalDirection < 0) {
            return entity.getY() <= targetY + tolerance;
        }
        return Math.abs(targetY - entity.getY()) <= tolerance;
    }

    private Vec3d getClimbableAnchor(BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        Vec3d center = Vec3d.ofCenter(pos);
        if (state.getBlock() instanceof LadderBlock) {
            Direction facing = state.get(LadderBlock.FACING);
            return center.add(
                    facing.getOffsetX() * LADDER_ENTRY_OFFSET,
                    0.0D,
                    facing.getOffsetZ() * LADDER_ENTRY_OFFSET
            );
        }
        return center;
    }

    private static double horizontalVelocity(double delta) {
        return MathHelper.clamp(
                delta * CLIMB_HORIZONTAL_GAIN,
                -CLIMB_HORIZONTAL_SPEED,
                CLIMB_HORIZONTAL_SPEED
        );
    }

    private BlockPos findAttachedClimbable(BlockPos fallback) {
        BlockPos mobPos = entity.getBlockPos();
        if (isClimbable(mobPos)) {
            return mobPos;
        }
        if (isClimbable(mobPos.up())) {
            return mobPos.up();
        }
        if (isClimbable(mobPos.down())) {
            return mobPos.down();
        }
        return fallback;
    }

    private boolean isClimbable(BlockPos pos) {
        return world.getBlockState(pos).isIn(BlockTags.CLIMBABLE);
    }

    private record ClimbContext(
            PathNode climbableNode,
            PathNode targetNode,
            boolean pathTargetsClimbable,
            boolean exitsClimbable,
            int verticalDirection
    ) {
    }
}
