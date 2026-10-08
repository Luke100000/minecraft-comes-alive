package net.conczin.mca.entity.ai.navigation;

import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.conczin.mca.entity.ai.PathingBlockInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class MCAWalkNodeEvaluator extends WalkNodeEvaluator {
    private static final int MAX_CLIMBABLE_VERTICAL_OFFSET = 2;
    private static final double FLOOR_EPSILON = 1.0E-3D;
    private static final double BOX_EPSILON = 1.0E-7D;
    private static final double RAISED_START_EPSILON = 1.0E-3D;
    private final Long2BooleanMap clearanceCache = new Long2BooleanOpenHashMap();
    private final Long2BooleanMap climbableCache = new Long2BooleanOpenHashMap();
    private final BlockPos.MutableBlockPos climbablePos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos collisionPos = new BlockPos.MutableBlockPos();
    private Node startNode;
    private boolean countExpandedNodes;
    private int expandedNodes;

    @Override
    public void prepare(PathNavigationRegion region, Mob mob) {
        this.expandedNodes = 0;
        this.countExpandedNodes = PathRequestDiagnostics.enabled();
        super.prepare(region, mob);
    }

    /** Neighbor expansions, not heap pops: reached goals and range-cutoff nodes are excluded. */
    int expandedNodes() {
        return this.expandedNodes;
    }

    @Override
    public void done() {
        this.clearanceCache.clear();
        this.climbableCache.clear();
        this.startNode = null;
        super.done();
    }

    @Override
    public Node getStart() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int y = this.mob.getBlockY();
        BlockState state = this.currentContext.getBlockState(pos.set(this.mob.getX(), y, this.mob.getZ()));

        if (!this.mob.canStandOnFluid(state.getFluidState())
            && this.canFloat()
            && this.mob.isInWater()
            && state.getFluidState().is(FluidTags.WATER)) {
            while (state.getFluidState().is(FluidTags.WATER)) {
                state = this.currentContext.getBlockState(pos.set(this.mob.getX(), ++y, this.mob.getZ()));
            }
            return captureStartNode(correctRaisedStartNode(this.getStartNodeAtY(pos, y - 1)));
        }

        BlockPos mobPos = this.mob.blockPosition();
        if (isClimbable(mobPos)) {
            return captureStartNode(correctRaisedStartNode(getClimbableNode(mobPos)));
        }

        if (this.mob.onClimbable()) {
            for (int drop = 1; drop <= MAX_CLIMBABLE_VERTICAL_OFFSET; drop++) {
                BlockPos candidate = mobPos.below(drop);

                if (isClimbable(candidate)) {
                    return captureStartNode(correctRaisedStartNode(getClimbableNode(candidate)));
                }

                if (!this.currentContext.getBlockState(candidate)
                        .isPathfindable(PathComputationType.LAND)) {
                    break;
                }
            }
        }

        return captureStartNode(correctRaisedStartNode(super.getStart()));
    }

    private Node correctRaisedStartNode(Node selectedStart) {
        if (selectedStart == null || !this.mob.onGround() || this.mob.onClimbable()) {
            return selectedStart;
        }

        BlockPos mobPos = this.mob.blockPosition();
        AABB startBox = this.mob.getBoundingBox();
        double modeledFloor = this.getFloorLevel(mobPos);
        if (!selectedStart.asBlockPos().equals(mobPos)
            && startBox.minY > modeledFloor + RAISED_START_EPSILON
            && (getMobBoxAt(selectedStart).minY - startBox.minY > this.mob.maxUpStep()
                || !canSweepBoxTo(selectedStart, startBox))) {
            // A raised start must be reachable from the real box without assuming a jump already happened.
            // Start from the mob cell so A* evaluates the actual exits from partial blocks.
            return this.getStartNode(mobPos);
        }
        return selectedStart;
    }

    private Node captureStartNode(Node selectedStart) {
        this.startNode = selectedStart;
        return selectedStart;
    }

    private Node getStartNodeAtY(BlockPos.MutableBlockPos pos, int y) {
        BlockPos mobPos = this.mob.blockPosition();
        if (!this.canStartAt(pos.set(mobPos.getX(), y, mobPos.getZ()))) {
            AABB box = this.mob.getBoundingBox();
            if (this.canStartAt(pos.set(box.minX, y, box.minZ))
                || this.canStartAt(pos.set(box.minX, y, box.maxZ))
                || this.canStartAt(pos.set(box.maxX, y, box.minZ))
                || this.canStartAt(pos.set(box.maxX, y, box.maxZ))) {
                return this.getStartNode(pos);
            }
        }

        return this.getStartNode(new BlockPos(mobPos.getX(), y, mobPos.getZ()));
    }

    @Override
    public int getNeighbors(Node[] nodes, Node origin) {
        if (this.countExpandedNodes) {
            this.expandedNodes++;
        }
        int nodeCount = super.getNeighbors(nodes, origin);
        nodeCount = rejectBlockedRaisedStartTransitions(nodes, nodeCount, origin);
        nodeCount = rejectBlockedRaisedTransitions(nodes, nodeCount, origin);
        if (!isClimbable(origin.x, origin.y, origin.z)) {
            return addDescendingClimbableEntries(nodes, nodeCount, origin.asBlockPos());
        }

        nodeCount = removeLargeVerticalTransitions(nodes, nodeCount, origin);
        BlockPos originPos = origin.asBlockPos();
        BlockPos above = originPos.above();
        boolean continuesUp = isClimbable(above);
        if (continuesUp) {
            nodeCount = addClimbableNode(nodes, nodeCount, above);
        } else {
            nodeCount = addUpperFloorExits(nodes, nodeCount, originPos);
        }

        BlockPos below = originPos.below();
        if (isClimbable(below)) {
            nodeCount = addClimbableNode(nodes, nodeCount, below);
        }

        return nodeCount;
    }

    int addDescendingClimbableEntries(Node[] nodes, int nodeCount, BlockPos origin) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos edge = origin.relative(direction);
            for (int drop = 1; drop <= MAX_CLIMBABLE_VERTICAL_OFFSET; drop++) {
                BlockPos candidate = edge.below(drop);
                long key = candidate.asLong();
                if (this.climbableCache.get(key)) {
                    nodeCount = addClimbableNode(nodes, nodeCount, candidate);
                    break;
                }

                // Non-climbable candidates still need their state for trapdoors
                // and solid-floor termination. Read it once rather than asking
                // isClimbable() to fetch it and immediately fetching it again.
                BlockState state = this.currentContext.getBlockState(candidate);
                boolean climbable = state.is(BlockTags.CLIMBABLE);
                this.climbableCache.put(key, climbable);
                if (climbable) {
                    nodeCount = addClimbableNode(nodes, nodeCount, candidate);
                    break;
                }
                if (PathingBlockInteraction.isHandOpenableTrapDoor(state)) {
                    continue;
                }
                if (!state.isPathfindable(PathComputationType.LAND)) {
                    break;
                }
            }
        }
        return nodeCount;
    }

    private int rejectBlockedRaisedStartTransitions(Node[] nodes, int nodeCount, Node origin) {
        if (origin != this.startNode || !this.mob.onGround() || this.mob.onClimbable()) {
            return nodeCount;
        }

        AABB startBox = this.mob.getBoundingBox();
        double modeledFloor = this.getFloorLevel(origin.asBlockPos());
        if (startBox.minY <= modeledFloor + RAISED_START_EPSILON) {
            return nodeCount;
        }

        int writeIndex = 0;
        for (int readIndex = 0; readIndex < nodeCount; readIndex++) {
            Node candidate = nodes[readIndex];
            if (candidate != null && canSweepBoxTo(candidate, startBox)) {
                nodes[writeIndex++] = candidate;
            }
        }

        for (int index = writeIndex; index < nodeCount; index++) {
            nodes[index] = null;
        }
        return writeIndex;
    }

    private int rejectBlockedRaisedTransitions(Node[] nodes, int nodeCount, Node origin) {
        AABB originBox = null;
        int writeIndex = 0;
        for (int readIndex = 0; readIndex < nodeCount; readIndex++) {
            Node candidate = nodes[readIndex];
            if (candidate == null) {
                continue;
            }

            if (candidate.y > origin.y && candidate.x != origin.x && candidate.z != origin.z) {
                if (originBox == null) {
                    originBox = getMobBoxAt(origin);
                }
                AABB destinationBox = getMobBoxAt(candidate);
                BlockPos originPos = this.collisionPos.set(origin.x, origin.y, origin.z);
                if (destinationBox.minY - originBox.minY > this.mob.maxUpStep()
                        && this.currentContext.getBlockState(originPos)
                                .getCollisionShape(this.currentContext.level(), originPos).isEmpty()
                        && !canApproachDiagonalJump(origin, candidate, originBox)) {
                    continue;
                }
            }

            BlockState support = this.currentContext.getBlockState(
                    this.collisionPos.set(candidate.x, candidate.y - 1, candidate.z)
            );
            if (!isBarrierSupport(support)) {
                nodes[writeIndex++] = candidate;
                continue;
            }

            if (originBox == null) {
                originBox = getMobBoxAt(origin);
            }
            AABB destinationBox = getMobBoxAt(candidate);
            boolean raisedBarrierTop = destinationBox.minY > originBox.minY + RAISED_START_EPSILON;
            if (!raisedBarrierTop || canSweepBoxTo(candidate, originBox)) {
                nodes[writeIndex++] = candidate;
            }
        }

        for (int index = writeIndex; index < nodeCount; index++) {
            nodes[index] = null;
        }
        return writeIndex;
    }

    private boolean canApproachDiagonalJump(Node origin, Node candidate, AABB originBox) {
        double dx = candidate.x + 0.5D - (originBox.minX + originBox.maxX) * 0.5D;
        double dz = candidate.z + 0.5D - (originBox.minZ + originBox.maxZ) * 0.5D;
        double jumpRangeSqr = Math.max(1.0F, this.mob.getBbWidth());
        double approachFraction = 1.0D - Math.sqrt(jumpRangeSqr / (dx * dx + dz * dz));
        if (approachFraction <= 0.0D) {
            return true;
        }
        // Entity collision can step over low edges; one tall edge also permits sliding into
        // MoveControl's jump range. Only tall edging on both sides blocks the diagonal approach.
        AABB approachBox = originBox.move(dx * approachFraction, this.mob.maxUpStep(), dz * approachFraction);
        return !blocksJumpApproach(candidate.x, origin.y, origin.z, approachBox)
                || !blocksJumpApproach(origin.x, origin.y, candidate.z, approachBox);
    }

    private boolean blocksJumpApproach(int x, int y, int z, AABB approachBox) {
        BlockPos pos = this.collisionPos.set(x, y, z);
        VoxelShape shape = this.currentContext.getBlockState(pos).getCollisionShape(this.currentContext.level(), pos);
        return !shape.isEmpty() && Shapes.joinIsNotEmpty(
                Shapes.create(approachBox.move(-x, -y, -z)), shape, BooleanOp.AND);
    }

    private static boolean isBarrierSupport(BlockState state) {
        if (PathingBlockInteraction.isFenceGate(state)) {
            return !state.getValue(BlockStateProperties.OPEN);
        }
        return state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS);
    }

    private int removeLargeVerticalTransitions(Node[] nodes, int nodeCount, Node origin) {
        int writeIndex = 0;
        for (int i = 0; i < nodeCount; i++) {
            Node node = nodes[i];
            if (Math.abs(node.y - origin.y) <= 1) {
                nodes[writeIndex++] = node;
            }
        }

        for (int i = writeIndex; i < nodeCount; i++) {
            nodes[i] = null;
        }
        return writeIndex;
    }

    private int addUpperFloorExits(Node[] nodes, int nodeCount, BlockPos climbable) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int rise = 1; rise <= MAX_CLIMBABLE_VERTICAL_OFFSET; rise++) {
                Node node = getStartNode(climbable.relative(direction).above(rise));
                if (node.type == PathType.OPEN || node.costMalus < 0.0F || !hasBlockClearance(node)) {
                    continue;
                }

                if (!node.closed && nodeCount < nodes.length) {
                    nodes[nodeCount++] = node;
                }
                break;
            }
        }
        return nodeCount;
    }

    private int addClimbableNode(Node[] nodes, int nodeCount, BlockPos pos) {
        Node node = getClimbableNode(pos);
        if (!node.closed && nodeCount < nodes.length) {
            nodes[nodeCount++] = node;
        }
        return nodeCount;
    }

    private Node getClimbableNode(BlockPos pos) {
        Node node = this.getNode(pos);
        node.type = PathType.WALKABLE;
        node.costMalus = Math.max(node.costMalus, 0.0F);
        return node;
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        // The same state also determines hand-openable gate handling. Reuse it
        // for climbable classification instead of fetching this block twice.
        BlockState state = context.getBlockState(this.climbablePos.set(x, y, z));
        boolean climbable = state.is(BlockTags.CLIMBABLE);
        if (context == this.currentContext) {
            this.climbableCache.put(BlockPos.asLong(x, y, z), climbable);
        }
        if (state.is(Blocks.SCAFFOLDING)) {
            return super.getPathType(context, x, y, z);
        }
        if (climbable) {
            return PathType.WALKABLE;
        }

        if (state.getBlock() instanceof DoorBlock
                && !state.getValue(BlockStateProperties.OPEN)) {
            // Pathfinding and SmarterOpenDoorsTask must agree on the same door policy.
            // Vanilla's evaluator otherwise accepts every hand-openable DoorBlock even
            // when vanilla's door AI would refuse to operate it.
            return PathingBlockInteraction.canInteractWithDoor(state)
                    ? PathType.WALKABLE_DOOR
                    : PathType.DOOR_IRON_CLOSED;
        }

        if (PathingBlockInteraction.isDramaticDoorOpenable(state)
                && !state.getValue(BlockStateProperties.OPEN)) {
            return PathType.WALKABLE_DOOR;
        }

        if (PathingBlockInteraction.canInteractWithFenceGate(state)
                && !state.getValue(BlockStateProperties.OPEN)) {
            // Vanilla treats closed fence gates as FENCE, so a path can never contain
            // the gate node for SmarterOpenDoorsTask to open. Treat hand-operated gates
            // like walkable doors: path through them, then let the brain toggle them.
            return PathType.WALKABLE_DOOR;
        }

        return super.getPathType(context, x, y, z);
    }

    private boolean isClimbable(BlockPos pos) {
        return isClimbable(this.currentContext, pos.getX(), pos.getY(), pos.getZ());
    }

    private boolean isClimbable(int x, int y, int z) {
        return isClimbable(this.currentContext, x, y, z);
    }

    private boolean isClimbable(PathfindingContext context, int x, int y, int z) {
        if (context != this.currentContext) {
            return context.getBlockState(this.climbablePos.set(x, y, z)).is(BlockTags.CLIMBABLE);
        }

        long key = BlockPos.asLong(x, y, z);
        if (this.climbableCache.containsKey(key)) {
            return this.climbableCache.get(key);
        }

        boolean climbable = context.getBlockState(this.climbablePos.set(x, y, z)).is(BlockTags.CLIMBABLE);
        this.climbableCache.put(key, climbable);
        return climbable;
    }

    @Nullable
    @Override
    protected Node findAcceptedNode(int x, int y, int z, int maxYStep, double currentFloor, Direction direction, PathType previousType) {
        Node node = super.findAcceptedNode(x, y, z, maxYStep, currentFloor, direction, previousType);
        if (node == null || node.costMalus < 0.0F) {
            return node;
        }

        return shouldCheckBlockClearance(node.type) && !hasBlockClearance(node) ? null : node;
    }

    private static boolean shouldCheckBlockClearance(PathType type) {
        return type != PathType.WALKABLE_DOOR
               && type != PathType.DOOR_OPEN
               && type != PathType.TRAPDOOR
               && type != PathType.DANGER_TRAPDOOR;
    }

    private boolean hasBlockClearance(Node node) {
        long key = BlockPos.asLong(node.x, node.y, node.z);
        if (this.clearanceCache.containsKey(key)) {
            return this.clearanceCache.get(key);
        }

        boolean hasClearance = hasExactBlockClearance(getMobBoxAt(node));
        this.clearanceCache.put(key, hasClearance);
        return hasClearance;
    }

    private boolean hasExactBlockClearance(AABB clearanceBox) {
        int minX = floorMin(clearanceBox.minX);
        int maxX = floorMax(clearanceBox.maxX);
        int minY = floorMin(clearanceBox.minY);
        int maxY = floorMax(clearanceBox.maxY);
        int minZ = floorMin(clearanceBox.minZ);
        int maxZ = floorMax(clearanceBox.maxZ);
        boolean hasPartialCollision = false;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockState state = this.currentContext.getBlockState(this.collisionPos.set(x, y, z));
                    if (state.isAir()) {
                        continue;
                    }

                    if (state.isCollisionShapeFullBlock(this.currentContext.level(), this.collisionPos)) {
                        return false;
                    }

                    if (!state.getCollisionShape(this.currentContext.level(), this.collisionPos).isEmpty()) {
                        hasPartialCollision = true;
                    }
                }
            }
        }

        return !hasPartialCollision
               || this.currentContext.level().noBlockCollision(this.mob, clearanceBox);
    }

    private AABB getMobBoxAt(Node node) {
        AABB box = this.mob.getBoundingBox();
        double floorY = getStandingFloorY(node, box);
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        return box.move(
                node.x + 0.5D - centerX,
                floorY + FLOOR_EPSILON - box.minY,
                node.z + 0.5D - centerZ
        );
    }

    private double getStandingFloorY(Node node, AABB mobBox) {
        BlockPos pos = this.collisionPos.set(node.x, node.y, node.z);
        double floorY = this.getFloorLevel(pos);
        BlockState state = this.currentContext.getBlockState(pos);
        if (state.isAir() || !state.isPathfindable(PathComputationType.LAND)) {
            return floorY;
        }

        VoxelShape collisionShape = state.getCollisionShape(this.currentContext.level(), pos);
        if (collisionShape.isEmpty()) {
            return floorY;
        }

        double surfaceY = collisionShape.max(Direction.Axis.Y);
        if (surfaceY > 1.0D) {
            return floorY;
        }

        double halfWidth = mobBox.getXsize() * 0.5D;
        double halfDepth = mobBox.getZsize() * 0.5D;
        VoxelShape footprint = Shapes.box(
                0.5D - halfWidth,
                Math.max(0.0D, surfaceY - BOX_EPSILON),
                0.5D - halfDepth,
                0.5D + halfWidth,
                surfaceY,
                0.5D + halfDepth
        );
        if (Shapes.joinIsNotEmpty(footprint, collisionShape, BooleanOp.ONLY_FIRST)) {
            return floorY;
        }

        return Math.max(floorY, node.y + surfaceY);
    }

    private boolean canSweepBoxTo(Node candidate, AABB startBox) {
        AABB destinationBox = getMobBoxAt(candidate);
        AABB sweepBox = startBox;
        double rise = destinationBox.minY - startBox.minY;
        if (rise > RAISED_START_EPSILON) {
            AABB verticalSweep = startBox.expandTowards(0.0D, rise, 0.0D);
            if (!this.currentContext.level().noBlockCollision(this.mob, verticalSweep)) {
                return false;
            }
            sweepBox = startBox.move(0.0D, rise, 0.0D);
        }

        Vec3 travel = new Vec3(
                destinationBox.minX - startBox.minX,
                0.0D,
                destinationBox.minZ - startBox.minZ
        );
        int steps = Mth.ceil(travel.length() / startBox.getSize());
        if (steps <= 0) {
            return true;
        }

        Vec3 step = travel.scale(1.0D / steps);
        for (int index = 0; index < steps; index++) {
            sweepBox = sweepBox.move(step);
            if (!this.currentContext.level().noBlockCollision(this.mob, sweepBox)) {
                return false;
            }
        }
        return true;
    }

    private static int floorMin(double value) {
        return (int)Math.floor(value);
    }

    private static int floorMax(double value) {
        return (int)Math.floor(value - BOX_EPSILON);
    }

}
