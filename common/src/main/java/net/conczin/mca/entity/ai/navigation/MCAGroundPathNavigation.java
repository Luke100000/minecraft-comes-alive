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
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

/** 1.20.1 equivalent of the newer MCA ground navigation implementation. */
public class MCAGroundPathNavigation extends GroundPathNavigation {
    private static final double CLIMB_VERTICAL_SPEED = 0.16D;
    private static final double CLIMB_HORIZONTAL_SPEED = 0.12D;
    private static final double CLIMB_HORIZONTAL_GAIN = 0.35D;
    private static final double LADDER_ENTRY_OFFSET = 0.1D;
    private static final double ASCENT_NODE_TOLERANCE = 0.20D;
    private static final double DESCENT_NODE_TOLERANCE = 0.08D;
    private static final double EXIT_HEIGHT_TOLERANCE = 0.25D;
    private static final double EXIT_VERTICAL_BIAS = 0.08D;

    public MCAGroundPathNavigation(Mob mobEntity, Level world) {
        super(mobEntity, world);
    }

    public boolean isControllingClimbable() {
        return getClimbContext() != null;
    }

    @Override
    protected PathFinder createPathFinder(int range) {
        nodeEvaluator = new MCAWalkNodeEvaluator();
        nodeEvaluator.setCanPassDoors(true);
        nodeEvaluator.setCanOpenDoors(true);
        return new PathFinder(nodeEvaluator, range);
    }

    @Override
    public boolean canCutCorner(BlockPathTypes type) {
        return type != BlockPathTypes.DOOR_OPEN && super.canCutCorner(type);
    }

    @Override
    protected Vec3 getTempMobPos() {
        return new Vec3(mob.getX(), getWaterAwareSurfaceY(), mob.getZ());
    }

    @Override
    protected boolean canUpdatePath() {
        return super.canUpdatePath() || mob.onClimbable();
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
        if (path == null || path.isDone()) {
            return;
        }

        ClimbContext context = getClimbContext();
        if (context == null) {
            super.followThePath();
            return;
        }

        Vec3 position = getTempMobPos();
        if (context.pathTargetsClimbable() && mob.onClimbable()) {
            double tolerance = context.verticalDirection() < 0
                    ? DESCENT_NODE_TOLERANCE
                    : ASCENT_NODE_TOLERANCE;
            if (hasReachedHeight(context.targetNode().y, context.verticalDirection(), tolerance)) {
                path.advance();
            }
        }

        doStuckDetection(position);
    }

    private int getWaterAwareSurfaceY() {
        if (mob.isInWater() && canFloat()) {
            int surfaceY = mob.getBlockY();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(mob.getX(), surfaceY, mob.getZ());
            int steps = 0;

            while (level.getFluidState(pos).is(FluidTags.WATER)) {
                pos.setY(++surfaceY);
                if (++steps > 16) {
                    return mob.getBlockY();
                }
            }

            return surfaceY;
        }

        return Mth.floor(mob.getY() + 0.5D);
    }

    private ClimbContext getClimbContext() {
        Path path = this.path;
        if (path == null || path.isDone()) {
            return null;
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
            boolean exitsClimbable = followingNode != null && !isClimbable(followingNode.asBlockPos());
            return new ClimbContext(
                    nextNode,
                    exitsClimbable ? followingNode : nextNode,
                    true,
                    exitsClimbable,
                    getVerticalDirection(path, nextNodeIndex)
            );
        }

        if (mob.onClimbable() && nextNodeIndex > 0) {
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
            return Integer.compare(climbableNode.y, previousNode.y);
        }

        return 0;
    }

    private void applyClimbableMotion(ClimbContext context) {
        BlockPos climbablePos = findAttachedClimbable(context.climbableNode().asBlockPos());
        Vec3 anchor = getClimbableAnchor(climbablePos);

        if (!mob.onClimbable()) {
            mob.getMoveControl().setWantedPosition(anchor.x, mob.getY(), anchor.z, speedModifier);
            Vec3 movement = mob.getDeltaMovement();
            mob.setDeltaMovement(
                    horizontalVelocity(anchor.x - mob.getX()),
                    movement.y,
                    horizontalVelocity(anchor.z - mob.getZ())
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

        mob.getMoveControl().setWantedPosition(targetX, targetY, targetZ, speedModifier);

        double verticalDelta = targetY - mob.getY();
        double controlledY;
        if (context.verticalDirection() > 0) {
            controlledY = Mth.clamp(verticalDelta, 0.0D, CLIMB_VERTICAL_SPEED);
        } else if (context.verticalDirection() < 0) {
            controlledY = Mth.clamp(verticalDelta, -CLIMB_VERTICAL_SPEED, 0.0D);
        } else {
            controlledY = Mth.clamp(verticalDelta, -CLIMB_VERTICAL_SPEED, CLIMB_VERTICAL_SPEED);
        }

        if (atExitHeight && context.verticalDirection() != 0) {
            controlledY = context.verticalDirection() > 0
                    ? Math.max(controlledY, EXIT_VERTICAL_BIAS)
                    : Math.min(controlledY, -EXIT_VERTICAL_BIAS);
        }

        mob.xxa = 0.0F;
        mob.zza = 0.0F;
        mob.setDeltaMovement(
                horizontalVelocity(targetX - mob.getX()),
                controlledY,
                horizontalVelocity(targetZ - mob.getZ())
        );
    }

    private boolean isAtExitHeight(ClimbContext context, double targetY) {
        return hasReachedHeight(targetY, context.verticalDirection(), EXIT_HEIGHT_TOLERANCE);
    }

    private boolean hasReachedHeight(double targetY, int verticalDirection, double tolerance) {
        if (verticalDirection > 0) {
            return mob.getY() >= targetY - tolerance;
        }
        if (verticalDirection < 0) {
            return mob.getY() <= targetY + tolerance;
        }
        return Math.abs(targetY - mob.getY()) <= tolerance;
    }

    private Vec3 getClimbableAnchor(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
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
        BlockPos mobPos = mob.blockPosition();
        if (isClimbable(mobPos)) {
            return mobPos;
        }
        if (isClimbable(mobPos.above())) {
            return mobPos.above();
        }
        if (isClimbable(mobPos.below())) {
            return mobPos.below();
        }
        return fallback;
    }

    private boolean isClimbable(BlockPos pos) {
        return level.getBlockState(pos).is(BlockTags.CLIMBABLE);
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
