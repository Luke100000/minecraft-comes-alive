package net.conczin.mca.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

public class MCAGroundPathNavigation extends GroundPathNavigation {
    private static final double LADDER_VERTICAL_SPEED = 0.14D;
    private static final double LADDER_VERTICAL_TOLERANCE = 0.25D;
    private static final double LADDER_DESCENT_TOLERANCE = 0.01D;

    public MCAGroundPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new MCAWalkNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        this.nodeEvaluator.setCanOpenDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    @Override
    protected boolean canUpdatePath() {
        return super.canUpdatePath() || this.mob.onClimbable();
    }

    @Override
    public void tick() {
        super.tick();
        applyPathLadderMotion();
    }

    @Override
    protected void followThePath() {
        if (this.path == null || this.path.isDone()) {
            return;
        }

        Node nextNode = this.path.getNextNode();
        if (!isClimbable(nextNode.asBlockPos())) {
            super.followThePath();
            return;
        }

        Vec3 position = this.getTempMobPos();
        int nextNodeIndex = this.path.getNextNodeIndex();
        Node followingNode = nextNodeIndex + 1 < this.path.getNodeCount()
                ? this.path.getNode(nextNodeIndex + 1)
                : null;
        boolean exitingLadder = followingNode != null
                && !isClimbable(followingNode.asBlockPos());
        double targetY = exitingLadder
                ? followingNode.y
                : nextNode.y;

        double yDelta = targetY - this.mob.getY();
        boolean readyForExitHandoff = exitingLadder
                && yDelta < 0.0D
                && Math.abs(yDelta) <= LADDER_VERTICAL_TOLERANCE;
        if ((readyForExitHandoff
                || hasReachedLadderTarget(followingNode, exitingLadder, yDelta))
                && (exitingLadder || this.mob.onClimbable())) {
            this.path.advance();
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

    private double getLadderVerticalVelocity() {
        Path path = this.path;
        if (path == null || path.isDone()) {
            return Double.NaN;
        }

        int nextNodeIndex = path.getNextNodeIndex();
        if (nextNodeIndex >= path.getNodeCount()) {
            return Double.NaN;
        }

        Node nextNode = path.getNode(nextNodeIndex);
        if (!isClimbable(nextNode.asBlockPos())) {
            if (this.mob.onClimbable()
                    && nextNodeIndex > 0
                    && isClimbable(path.getNode(nextNodeIndex - 1).asBlockPos())) {
                double yDelta = nextNode.y - this.mob.getY();
                return Math.copySign(
                        Math.min(LADDER_VERTICAL_SPEED, Math.abs(yDelta)),
                        yDelta
                );
            }
            return Double.NaN;
        }

        Node followingNode = nextNodeIndex + 1 < path.getNodeCount()
                ? path.getNode(nextNodeIndex + 1)
                : null;
        boolean exitingLadder = followingNode != null
                && !isClimbable(followingNode.asBlockPos());

        if (!this.mob.onClimbable() && !exitingLadder) {
            return Double.NaN;
        }

        double targetY = exitingLadder
                ? followingNode.y
                : nextNode.y;
        double yDelta = targetY - this.mob.getY();
        if (hasReachedLadderTarget(followingNode, exitingLadder, yDelta)) {
            return 0.0D;
        }

        return Math.copySign(
                Math.min(LADDER_VERTICAL_SPEED, Math.abs(yDelta)),
                yDelta
        );
    }

    private static boolean hasReachedLadderTarget(
            Node followingNode,
            boolean exitingLadder,
            double yDelta
    ) {
        boolean finishingDescent = yDelta < 0.0D
                && (followingNode == null || exitingLadder);
        double tolerance = finishingDescent
                ? LADDER_DESCENT_TOLERANCE
                : LADDER_VERTICAL_TOLERANCE;
        return Math.abs(yDelta) <= tolerance;
    }

    private void applyPathLadderMotion() {
        double controlledY = getLadderVerticalVelocity();
        if (!Double.isNaN(controlledY)) {
            Vec3 movement = this.mob.getDeltaMovement();
            this.mob.setDeltaMovement(movement.x(), controlledY, movement.z());
            return;
        }

        if (this.path == null || this.path.isDone()) {
            stopStaleLadderUpwardMotion();
        }
    }

    private void stopStaleLadderUpwardMotion() {
        if (!this.mob.onClimbable()) {
            return;
        }

        Vec3 movement = this.mob.getDeltaMovement();
        if (movement.y() > 0.0D) {
            this.mob.setDeltaMovement(movement.x(), 0.0D, movement.z());
        }
    }

    private boolean isClimbable(BlockPos pos) {
        return this.level.getBlockState(pos).is(BlockTags.CLIMBABLE);
    }
}
