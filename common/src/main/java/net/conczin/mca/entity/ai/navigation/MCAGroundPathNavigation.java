package net.conczin.mca.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MCAGroundPathNavigation extends GroundPathNavigation {
    private static final double CLIMB_VERTICAL_SPEED = 0.16D;
    private static final double CLIMB_HORIZONTAL_SPEED = 0.12D;
    private static final double CLIMB_HORIZONTAL_GAIN = 0.35D;
    private static final double ASCENT_NODE_TOLERANCE = 0.20D;
    private static final double DESCENT_NODE_TOLERANCE = 0.08D;
    private static final double EXIT_HEIGHT_TOLERANCE = 0.25D;
    private static final double EXIT_VERTICAL_BIAS = 0.08D;

    public MCAGroundPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    public boolean isControllingClimbable() {
        return getClimbContext() != null;
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new MCAWalkNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        this.nodeEvaluator.setCanOpenDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
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

        Vec3 position = this.getTempMobPos();
        if (context.pathTargetsClimbable()) {
            double yDelta = context.targetNode().y - this.mob.getY();
            double tolerance = yDelta < 0.0D
                    ? DESCENT_NODE_TOLERANCE
                    : ASCENT_NODE_TOLERANCE;
            if (this.mob.onClimbable() && Math.abs(yDelta) <= tolerance) {
                this.path.advance();
            }
        }

        this.doStuckDetection(position);
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
            boolean exitsClimbable = followingNode != null
                    && !isClimbable(followingNode.asBlockPos());
            return new ClimbContext(
                    nextNode,
                    exitsClimbable ? followingNode : nextNode,
                    true,
                    exitsClimbable
            );
        }

        if (this.mob.onClimbable() && nextNodeIndex > 0) {
            Node previousNode = path.getNode(nextNodeIndex - 1);
            if (isClimbable(previousNode.asBlockPos())) {
                return new ClimbContext(previousNode, nextNode, false, true);
            }
        }

        return null;
    }

    private void applyClimbableMotion(ClimbContext context) {
        BlockPos climbablePos = findAttachedClimbable(context.climbableNode().asBlockPos());
        Vec3 anchor = getClimbableAnchor(climbablePos);

        if (!this.mob.onClimbable()) {
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

        double targetY = context.targetNode().y;
        boolean atExitHeight = context.exitsClimbable() && isAtExitHeight(context, targetY);
        double targetX = anchor.x();
        double targetZ = anchor.z();
        if (context.exitsClimbable() && (!context.pathTargetsClimbable() || atExitHeight)) {
            targetX = context.targetNode().x + 0.5D;
            targetZ = context.targetNode().z + 0.5D;
        }

        this.mob.getMoveControl().setWantedPosition(targetX, targetY, targetZ, this.speedModifier);

        double controlledY = Mth.clamp(
                targetY - this.mob.getY(),
                -CLIMB_VERTICAL_SPEED,
                CLIMB_VERTICAL_SPEED
        );
        if (atExitHeight) {
            int exitDirection = Integer.compare(context.targetNode().y, context.climbableNode().y);
            if (exitDirection != 0) {
                controlledY = exitDirection > 0
                        ? Math.max(controlledY, EXIT_VERTICAL_BIAS)
                        : Math.min(controlledY, -EXIT_VERTICAL_BIAS);
            }
        }

        this.mob.setXxa(0.0F);
        this.mob.setZza(0.0F);
        this.mob.setDeltaMovement(
                horizontalVelocity(targetX - this.mob.getX()),
                controlledY,
                horizontalVelocity(targetZ - this.mob.getZ())
        );
    }

    private boolean isAtExitHeight(ClimbContext context, double targetY) {
        int exitDirection = Integer.compare(context.targetNode().y, context.climbableNode().y);
        if (exitDirection > 0) {
            return this.mob.getY() >= targetY - EXIT_HEIGHT_TOLERANCE;
        }
        if (exitDirection < 0) {
            return this.mob.getY() <= targetY + EXIT_HEIGHT_TOLERANCE;
        }
        return Math.abs(targetY - this.mob.getY()) <= EXIT_HEIGHT_TOLERANCE;
    }

    private Vec3 getClimbableAnchor(BlockPos pos) {
        BlockState state = this.level.getBlockState(pos);
        VoxelShape shape = state.getShape(this.level, pos);
        if (shape.isEmpty()) {
            return Vec3.atCenterOf(pos);
        }

        AABB bounds = shape.bounds();
        return new Vec3(
                pos.getX() + (bounds.minX + bounds.maxX) * 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + (bounds.minZ + bounds.maxZ) * 0.5D
        );
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
            boolean exitsClimbable
    ) {
    }
}
