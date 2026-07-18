package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;

public class MCAGroundPathNavigation extends GroundPathNavigation {
    private static final double LADDER_VERTICAL_SPEED = 0.14D;
    private static final double LADDER_VERTICAL_TOLERANCE = 0.25D;

    private long lastKeepPathDebugTick = Long.MIN_VALUE;

    private Path lastMotionDebugPath;
    private int lastMotionDebugNodeIndex = -1;
    private int lastMotionDebugDirection = Integer.MIN_VALUE;
    private boolean lastMotionDebugExiting;
    private boolean lastMotionDebugCentered;

    private int lastHandoffDebugPathId;
    private int lastHandoffDebugNodeIndex = -1;

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
    public Path createPath(BlockPos target, int reachRange) {
        Path currentPath = this.path;

        if (isLadderNavigationLocked()) {
            long gameTime = this.level.getGameTime();

            if (this.lastKeepPathDebugTick == Long.MIN_VALUE
                    || gameTime - this.lastKeepPathDebugTick >= 10L) {
                this.lastKeepPathDebugTick = gameTime;

                MCA.LOGGER.info(
                        "[LadderTrace] event=createPath-keep-current villager={} "
                                + "mobPos={} mobY={} target={} onClimbable={} velocity={} path={}",
                        this.mob.getUUID(),
                        this.mob.blockPosition(),
                        this.mob.getY(),
                        target,
                        this.mob.onClimbable(),
                        this.mob.getDeltaMovement(),
                        describePath(currentPath)
                );
            }

            return currentPath;
        }

        Path createdPath = super.createPath(target, reachRange);

        if (isNearClimbable()
                || containsClimbable(currentPath)
                || containsClimbable(createdPath)) {
            MCA.LOGGER.info(
                    "[LadderTrace] event=createPath-new villager={} "
                            + "mobPos={} mobY={} target={} onClimbable={} velocity={} "
                            + "previous={} created={}",
                    this.mob.getUUID(),
                    this.mob.blockPosition(),
                    this.mob.getY(),
                    target,
                    this.mob.onClimbable(),
                    this.mob.getDeltaMovement(),
                    describePath(currentPath),
                    describePath(createdPath)
            );
        }

        return createdPath;
    }

    @Override
    public boolean moveTo(Path requestedPath, double speedModifier) {
        Path previousPath = this.path;
        boolean differentRequest = requestedPath != previousPath;
        boolean result = super.moveTo(requestedPath, speedModifier);

        if (differentRequest
                && (isNearClimbable()
                || containsClimbable(previousPath)
                || containsClimbable(requestedPath)
                || containsClimbable(this.path))) {
            MCA.LOGGER.info(
                    "[LadderTrace] event=moveTo villager={} result={} speed={} "
                            + "mobPos={} mobY={} onClimbable={} velocity={} "
                            + "previous={} requested={} active={}",
                    this.mob.getUUID(),
                    result,
                    speedModifier,
                    this.mob.blockPosition(),
                    this.mob.getY(),
                    this.mob.onClimbable(),
                    this.mob.getDeltaMovement(),
                    describePath(previousPath),
                    describePath(requestedPath),
                    describePath(this.path)
            );
        }

        return result;
    }

    @Override
    protected boolean canUpdatePath() {
        return super.canUpdatePath() || this.mob.onClimbable();
    }

    @Override
    public void stop() {
        if (isLadderNavigationLocked()) {
            MCA.LOGGER.info(
                    "[LadderTrace] event=stop-suppressed villager={} "
                            + "mobPos={} mobY={} onClimbable={} velocity={} path={}",
                    this.mob.getUUID(),
                    this.mob.blockPosition(),
                    this.mob.getY(),
                    this.mob.onClimbable(),
                    this.mob.getDeltaMovement(),
                    describePath(this.path)
            );
            return;
        }

        if (isNearClimbable() || containsClimbable(this.path)) {
            MCA.LOGGER.info(
                    "[LadderTrace] event=stop-accepted villager={} "
                            + "mobPos={} mobY={} onClimbable={} velocity={} path={}",
                    this.mob.getUUID(),
                    this.mob.blockPosition(),
                    this.mob.getY(),
                    this.mob.onClimbable(),
                    this.mob.getDeltaMovement(),
                    describePath(this.path)
            );
        }

        super.stop();
    }

    @Override
    public void recomputePath() {
        if (isLadderNavigationLocked()) {
            MCA.LOGGER.info(
                    "[LadderTrace] event=recompute-suppressed villager={} "
                            + "mobPos={} mobY={} velocity={} path={}",
                    this.mob.getUUID(),
                    this.mob.blockPosition(),
                    this.mob.getY(),
                    this.mob.getDeltaMovement(),
                    describePath(this.path)
            );
            return;
        }

        if (isNearClimbable() || containsClimbable(this.path)) {
            MCA.LOGGER.info(
                    "[LadderTrace] event=recompute-allowed villager={} "
                            + "mobPos={} mobY={} onClimbable={} velocity={} path={}",
                    this.mob.getUUID(),
                    this.mob.blockPosition(),
                    this.mob.getY(),
                    this.mob.onClimbable(),
                    this.mob.getDeltaMovement(),
                    describePath(this.path)
            );
        }

        super.recomputePath();
    }

    private boolean isLadderNavigationLocked() {
        Path path = this.path;
        if (path == null || path.isDone() || !this.mob.onClimbable()) {
            return false;
        }

        int nextNodeIndex = path.getNextNodeIndex();
        if (nextNodeIndex >= path.getNodeCount()) {
            return false;
        }

        if (isClimbable(path.getNode(nextNodeIndex).asBlockPos())) {
            return true;
        }

        return nextNodeIndex > 0
                && isClimbable(path.getNode(nextNodeIndex - 1).asBlockPos());
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
        BlockPos nextPos = nextNode.asBlockPos();

        if (!isClimbable(nextPos)) {
            int nextNodeIndex = this.path.getNextNodeIndex();

            if (this.mob.onClimbable()
                    || isPreviousNodeClimbable(this.path, nextNodeIndex)) {
                traceVanillaHandoff(this.path, nextNodeIndex, nextNode);
            }

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

        boolean reachedTargetY =
                Math.abs(this.mob.getY() - targetY) <= LADDER_VERTICAL_TOLERANCE;

        /*
         * While traversing the ladder, require the mob to remain centred.
         *
         * Once the final ladder node has reached the adjacent floor's Y,
         * however, release it immediately. Requiring ladder centring here
         * leaves the mob targeting the ladder block and vanilla climbable
         * collision can push it back upward instead of letting it step off.
         */
        boolean canAdvance = reachedTargetY
                && (exitingLadder || this.mob.onClimbable());

        if (canAdvance) {
            if (exitingLadder) {
                stopVerticalMotion();
            }

            MCA.LOGGER.info(
                    "[LadderTrace] event=ladder-node-advance villager={} "
                            + "mobPos={} mobY={} onClimbable={} index={} "
                            + "next={} following={} targetY={} velocity={} pathId={}",
                    this.mob.getUUID(),
                    this.mob.blockPosition(),
                    this.mob.getY(),
                    this.mob.onClimbable(),
                    nextNodeIndex,
                    describeNode(nextNode),
                    describeNode(followingNode),
                    targetY,
                    this.mob.getDeltaMovement(),
                    pathId(this.path)
            );
            this.path.advance();
        }

        this.doStuckDetection(position);
    }

    @Override
    protected double getGroundY(Vec3 position) {
        BlockPos targetPos = BlockPos.containing(position);

        if (this.mob.onClimbable() || isClimbable(targetPos)) {
            return this.mob.getY();
        }

        return super.getGroundY(position);
    }

    public double getControlledLadderVelocity() {
        Path path = this.path;
        if (path == null || path.isDone()) {
            return Double.NaN;
        }

        int nextNodeIndex = path.getNextNodeIndex();
        if (nextNodeIndex >= path.getNodeCount()) {
            return Double.NaN;
        }

        Node nextNode = path.getNode(nextNodeIndex);
        BlockPos nextPos = nextNode.asBlockPos();

        if (!isClimbable(nextPos)) {
            if (this.mob.onClimbable()
                    && nextNodeIndex > 0
                    && isClimbable(path.getNode(nextNodeIndex - 1).asBlockPos())) {
                double yDelta = nextNode.y - this.mob.getY();

                if (Math.abs(yDelta) <= 0.01D) {
                    return 0.0D;
                }

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

        if (Math.abs(yDelta) <= LADDER_VERTICAL_TOLERANCE) {
            return 0.0D;
        }

        return Math.copySign(LADDER_VERTICAL_SPEED, yDelta);
    }

    private void applyPathLadderMotion() {
        double controlledY = getControlledLadderVelocity();
        if (!Double.isNaN(controlledY)) {
            Vec3 movement = this.mob.getDeltaMovement();
            this.mob.setDeltaMovement(movement.x(), controlledY, movement.z());
            return;
        }

        Path path = this.path;

        if (path == null || path.isDone()) {
            resetMotionTrace();
            return;
        }

        int nextNodeIndex = path.getNextNodeIndex();

        if (nextNodeIndex >= path.getNodeCount()) {
            resetMotionTrace();
            return;
        }

        Node nextNode = path.getNode(nextNodeIndex);
        BlockPos nextPos = nextNode.asBlockPos();

        /*
         * The next waypoint is still part of the ladder.
         *
         * Move vertically toward that exact waypoint. Do not infer the
         * direction from the final path target: that can reverse the ladder
         * impulse while the path is being advanced or recomputed.
         */
        if (isClimbable(nextPos)) {
            Node followingNode = nextNodeIndex + 1 < path.getNodeCount()
                    ? path.getNode(nextNodeIndex + 1)
                    : null;

            boolean exitingLadder = followingNode != null
                    && !isClimbable(followingNode.asBlockPos());

            boolean centered = isCenteredOnLadder(nextPos);

            double targetY = exitingLadder
                    ? followingNode.y
                    : nextNode.y;
            double yDelta = targetY - this.mob.getY();
            int direction = Math.abs(yDelta) <= LADDER_VERTICAL_TOLERANCE
                    ? 0
                    : yDelta > 0.0D ? 1 : -1;

            traceMotionState(
                    path,
                    nextNodeIndex,
                    nextNode,
                    followingNode,
                    exitingLadder,
                    centered,
                    targetY,
                    yDelta,
                    direction
            );

            /*
             * The final ladder node owns the complete vertical transition to
             * the exit floor.
             *
             * Do not require centring during this final transition. If
             * vanilla ladder physics has already nudged the mob above or
             * below the exit, explicitly move it back toward the exit Y.
             * followThePath() will release the ladder node as soon as that Y
             * is reached, allowing normal navigation to move horizontally
             * onto the adjacent floor.
             */
            if (exitingLadder) {
                if (Math.abs(yDelta) <= LADDER_VERTICAL_TOLERANCE) {
                    stopVerticalMotion();
                    return;
                }

                applyLadderVerticalMotion(yDelta > 0.0D ? 1 : -1);
                return;
            }

            if (!centered) {
                return;
            }

            if (!this.mob.onClimbable() && !exitingLadder) {
                return;
            }

            if (Math.abs(yDelta) <= LADDER_VERTICAL_TOLERANCE) {
                if (exitingLadder) {
                    stopVerticalMotion();
                }
                return;
            }

            applyLadderVerticalMotion(yDelta > 0.0D ? 1 : -1);
            return;
        }

        resetMotionTrace();
    }

    private void applyLadderVerticalMotion(int direction) {
        Vec3 movement = this.mob.getDeltaMovement().multiply(0.1D, 1.0D, 0.1D);
        this.mob.setDeltaMovement(
                movement.x(),
                direction * LADDER_VERTICAL_SPEED,
                movement.z()
        );
    }

    private void stopVerticalMotion() {
        Vec3 movement = this.mob.getDeltaMovement();
        this.mob.setDeltaMovement(
                movement.x(),
                0.0D,
                movement.z()
        );
    }

    private boolean isClimbable(BlockPos pos) {
        return this.level.getBlockState(pos).is(BlockTags.CLIMBABLE);
    }

    private boolean isCenteredOnLadder(BlockPos ladder) {
        BlockState state = this.level.getBlockState(ladder);
        if (state.getBlock() instanceof LadderBlock) {
            Direction facing = state.getValue(LadderBlock.FACING);
            double alignedCoordinate = facing.getAxis() == Direction.Axis.Z
                    ? this.mob.getX()
                    : this.mob.getZ();
            double ladderCenter = facing.getAxis() == Direction.Axis.Z
                    ? ladder.getX() + 0.5D
                    : ladder.getZ() + 0.5D;
            return Math.abs(alignedCoordinate - ladderCenter) < 0.1D;
        }

        return ladder.closerToCenterThan(this.mob.position(), 0.15D);
    }

    private void traceVanillaHandoff(Path path, int nodeIndex, Node nextNode) {
        int id = System.identityHashCode(path);

        if (this.lastHandoffDebugPathId == id
                && this.lastHandoffDebugNodeIndex == nodeIndex) {
            return;
        }

        this.lastHandoffDebugPathId = id;
        this.lastHandoffDebugNodeIndex = nodeIndex;

        Node previousNode = nodeIndex > 0
                ? path.getNode(nodeIndex - 1)
                : null;

        MCA.LOGGER.info(
                "[LadderTrace] event=vanilla-handoff villager={} "
                        + "mobPos={} mobY={} onClimbable={} index={} "
                        + "previous={} next={} velocity={} path={}",
                this.mob.getUUID(),
                this.mob.blockPosition(),
                this.mob.getY(),
                this.mob.onClimbable(),
                nodeIndex,
                describeNode(previousNode),
                describeNode(nextNode),
                this.mob.getDeltaMovement(),
                describePath(path)
        );
    }

    private void traceMotionState(
            Path path,
            int nodeIndex,
            Node nextNode,
            Node followingNode,
            boolean exitingLadder,
            boolean centered,
            double targetY,
            double yDelta,
            int direction
    ) {
        if (this.lastMotionDebugPath == path
                && this.lastMotionDebugNodeIndex == nodeIndex
                && this.lastMotionDebugDirection == direction
                && this.lastMotionDebugExiting == exitingLadder
                && this.lastMotionDebugCentered == centered) {
            return;
        }

        this.lastMotionDebugPath = path;
        this.lastMotionDebugNodeIndex = nodeIndex;
        this.lastMotionDebugDirection = direction;
        this.lastMotionDebugExiting = exitingLadder;
        this.lastMotionDebugCentered = centered;

        MCA.LOGGER.info(
                "[LadderTrace] event=motion-state villager={} "
                        + "mobPos={} mobY={} onClimbable={} index={} "
                        + "next={} following={} exiting={} centered={} "
                        + "targetY={} yDelta={} direction={} velocity={} pathId={}",
                this.mob.getUUID(),
                this.mob.blockPosition(),
                this.mob.getY(),
                this.mob.onClimbable(),
                nodeIndex,
                describeNode(nextNode),
                describeNode(followingNode),
                exitingLadder,
                centered,
                targetY,
                yDelta,
                direction,
                this.mob.getDeltaMovement(),
                pathId(path)
        );
    }

    private void resetMotionTrace() {
        this.lastMotionDebugPath = null;
        this.lastMotionDebugNodeIndex = -1;
        this.lastMotionDebugDirection = Integer.MIN_VALUE;
    }

    private boolean isPreviousNodeClimbable(Path path, int nodeIndex) {
        return nodeIndex > 0
                && isClimbable(path.getNode(nodeIndex - 1).asBlockPos());
    }

    private int getNextNodeIndex(Path path) {
        return path == null ? -1 : path.getNextNodeIndex();
    }

    private boolean containsClimbable(Path path) {
        if (path == null) {
            return false;
        }

        for (int i = 0; i < path.getNodeCount(); i++) {
            if (isClimbable(path.getNode(i).asBlockPos())) {
                return true;
            }
        }

        return false;
    }

    private boolean isNearClimbable() {
        BlockPos pos = this.mob.blockPosition();

        if (isClimbable(pos)
                || isClimbable(pos.above())
                || isClimbable(pos.below())) {
            return true;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = pos.relative(direction);

            if (isClimbable(adjacent)
                    || isClimbable(adjacent.above())
                    || isClimbable(adjacent.below())) {
                return true;
            }
        }

        return false;
    }

    private String describePath(Path path) {
        if (path == null) {
            return "null";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("{id=")
                .append(pathId(path))
                .append(",index=")
                .append(path.getNextNodeIndex())
                .append('/')
                .append(path.getNodeCount())
                .append(",done=")
                .append(path.isDone())
                .append(",canReach=")
                .append(path.canReach())
                .append(",target=")
                .append(path.getTarget())
                .append(",nodes=[");

        for (int i = 0; i < path.getNodeCount(); i++) {
            if (i > 0) {
                builder.append(" -> ");
            }

            if (i == path.getNextNodeIndex()) {
                builder.append('*');
            }

            builder.append(describeNode(path.getNode(i)));
        }

        return builder.append("]}").toString();
    }

    private String describeNode(Node node) {
        if (node == null) {
            return "null";
        }

        BlockPos pos = node.asBlockPos();

        return pos
                + (isClimbable(pos) ? "[LADDER]" : "")
                + '{' + String.valueOf(node.type) + '}';
    }

    private static String pathId(Path path) {
        return path == null
                ? "null"
                : Integer.toHexString(System.identityHashCode(path));
    }
}
