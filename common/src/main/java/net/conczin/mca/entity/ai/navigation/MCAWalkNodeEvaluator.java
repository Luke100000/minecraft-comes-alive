package net.conczin.mca.entity.ai.navigation;

import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.conczin.mca.entity.ai.PathingBlockInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.pathfinder.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MCAWalkNodeEvaluator extends WalkNodeEvaluator {
    // Matches Direction.Plane.HORIZONTAL's iteration order exactly; avoid
    // Guava Iterators$ArrayItr allocations on every path node expansion.
    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final int MAX_CLIMBABLE_VERTICAL_OFFSET = 2;
    private static final double FLOOR_EPSILON = PathNodeGeometry.FLOOR_EPSILON;
    private static final double BOX_EPSILON = 1.0E-7D;
    private static final double RAISED_START_EPSILON = 1.0E-3D;
    private static final byte DESCENDING_CONTINUE = 1;
    private static final byte DESCENDING_STOP = 2;
    private static final byte TRAPDOOR_CLOSED_OR_ABSENT = 1;
    private static final byte TRAPDOOR_OPEN = 2;
    private final Long2BooleanMap clearanceCache = new Long2BooleanOpenHashMap();
    private double liveOverhangDepartureFloor = Double.NEGATIVE_INFINITY;
    private final Long2BooleanMap climbableCache = new Long2BooleanOpenHashMap();
    private final Long2ByteMap descendingProbeCache = new Long2ByteOpenHashMap();
    private final Long2ByteMap openTrapdoorCache = new Long2ByteOpenHashMap();
    private final BlockPos.MutableBlockPos climbablePos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos collisionPos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos descendingPos = new BlockPos.MutableBlockPos();
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
        this.descendingProbeCache.clear();
        this.openTrapdoorCache.clear();
        this.startNode = null;
        super.done();
    }

    @Override
    public Node getStart() {
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
        if (this.mob.isInWater() && selectedStart.y < mobPos.getY()
                && this.currentContext.getBlockState(mobPos).getFluidState().isEmpty()) {
            // Water brushing the body must not put a grounded start inside its solid support.
            return this.getStartNode(mobPos);
        }
        AABB startBox = this.mob.getBoundingBox();
        if (selectedStart.asBlockPos().equals(mobPos)
                && startBox.minY > mobPos.getY() + RAISED_START_EPSILON
                && !hasBlockClearance(selectedStart)) {
            // A blocked start can be on a partial surface or inside a hollow shape.
            // Normalize to its top only when the live body can rise there; movement
            // already uses the same surface height and vanilla's collision-triggered jump.
            Node supportedStart = this.getStartNode(mobPos.above());
            AABB supportedBox = getMobBoxAt(supportedStart);
            double riseToSurface = supportedBox.minY - FLOOR_EPSILON - startBox.minY;
            if (riseToSurface >= -RAISED_START_EPSILON && riseToSurface <= PathNodeGeometry.jumpHeight(this.mob)
                    && supportedStart.costMalus >= 0.0F && hasBlockClearance(supportedStart)
                    && canSweepTo(startBox, supportedBox, this.currentContext.level())) {
                return supportedStart;
            }
        }
        double modeledFloor = this.getFloorLevel(mobPos);
        boolean unreachedClimbableStart = selectedStart.x == mobPos.getX()
            && selectedStart.z == mobPos.getZ()
            && selectedStart.y > mobPos.getY()
            && startBox.minY < selectedStart.y - RAISED_START_EPSILON
            && isClimbable(selectedStart.asBlockPos());
        if (!selectedStart.asBlockPos().equals(mobPos)
            && startBox.minY > modeledFloor + RAISED_START_EPSILON
            && (selectedStart.type == PathType.BLOCKED
                || unreachedClimbableStart
                || getMobBoxAt(selectedStart).minY - startBox.minY > this.mob.maxUpStep()
                || !canSweepTo(startBox, getMobBoxAt(selectedStart), this.currentContext.level()))) {
            // A raised start must be reachable from the real box without assuming a jump already happened.
            // A rounded BLOCKED start can also overstate the occupied height above a partial floor.
            // Start from the mob cell so A* evaluates the actual exits from partial blocks.
            return this.getStartNode(mobPos);
        }
        return selectedStart;
    }

    private Node captureStartNode(Node selectedStart) {
        this.startNode = selectedStart;
        return selectedStart;
    }

    @Override
    public int getNeighbors(Node[] nodes, Node origin) {
        if (this.countExpandedNodes) {
            this.expandedNodes++;
        }
        int nodeCount = collectGroundNeighbors(nodes, origin);
        if (!isClimbable(origin.x, origin.y, origin.z)) {
            nodeCount = addDescendingClimbableEntries(nodes, nodeCount, origin.asBlockPos());
            return addClosedHatchDescent(nodes, nodeCount, origin);
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

    private int collectGroundNeighbors(Node[] nodes, Node origin) {
        if (origin == this.startNode && this.mob.onGround() && !this.mob.onClimbable()
                && !this.mob.isInWater() && !this.mob.isInLava()) {
            BlockPos feet = this.mob.blockPosition();
            AABB body = this.mob.getBoundingBox();
            if (feet.equals(origin.asBlockPos())
                    && body.minY > this.getFloorLevel(feet) + RAISED_START_EPSILON) {
                AABB footSlice = new AABB(body.minX, body.minY - FLOOR_EPSILON, body.minZ,
                        body.maxX, body.minY, body.maxZ);
                if (this.currentContext.level().findSupportingBlock(this.mob, footSlice).isPresent()) {
                    // Actual footing is higher than the floor modeled beneath the body's center.
                    // Correct vanilla's departure height for this expansion before checking the live sweep.
                    this.liveOverhangDepartureFloor = body.minY;
                    try {
                        return rejectBlockedTransitions(nodes, super.getNeighbors(nodes, origin), origin, true);
                    } finally {
                        this.liveOverhangDepartureFloor = Double.NEGATIVE_INFINITY;
                    }
                }
            }
        }
        return rejectBlockedTransitions(nodes, super.getNeighbors(nodes, origin), origin, false);
    }

    /** A closed hatch beneath the feet is a valid route only to safe ground in its own column. */
    private int addClosedHatchDescent(Node[] nodes, int nodeCount, Node origin) {
        if (nodeCount >= nodes.length) return nodeCount;

        BlockPos hatch = origin.asBlockPos().below();
        BlockState state = this.currentContext.getBlockState(hatch);
        if (!PathingBlockInteraction.isHandOpenableTrapDoor(state)
                || state.getValue(TrapDoorBlock.OPEN)
                || state.getValue(TrapDoorBlock.POWERED)) return nodeCount;

        BlockPos landing = hatch.below();
        Node destination = this.getStartNode(landing);
        if (destination.closed || (destination.type != PathType.WALKABLE && destination.type != PathType.TRAPDOOR)
                || destination.costMalus < 0.0F)
            return nodeCount;

        // Validate the destination with the hatch hypothetically opened, not
        // with the currently closed panel that caused the deadlock. The actual
        // toggle remains entirely the Brain task's responsibility.
        CollisionGetter projection = new PathingCollisionView(
                this.currentContext.level(), origin, destination, this.mob);
        AABB from = getDepartureBox(origin);
        AABB to = getMobBoxAt(destination);
        if (from.minY < to.minY || from.minY - to.minY > this.mob.getMaxFallDistance()
                || !projection.noBlockCollision(this.mob, to)) return nodeCount;

        for (int step = 1; step <= 16; step++) {
            AABB alongDrop = from.move(0.0D, (to.minY - from.minY) * step / 16.0D, 0.0D);
            if (!projection.noBlockCollision(this.mob, alongDrop)) return nodeCount;
        }
        nodes[nodeCount++] = destination;
        return nodeCount;
    }

    int addDescendingClimbableEntries(Node[] nodes, int nodeCount, BlockPos origin) {
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            for (int drop = 1; drop <= MAX_CLIMBABLE_VERTICAL_OFFSET; drop++) {
                BlockPos candidate = this.descendingPos.set(
                        origin.getX() + direction.getStepX(),
                        origin.getY() - drop,
                        origin.getZ() + direction.getStepZ());
                long key = candidate.asLong();
                if (this.climbableCache.get(key)) {
                    nodeCount = addDescendingClimbableNode(nodes, nodeCount, origin, candidate);
                    break;
                }

                byte probe = this.descendingProbeCache.get(key);
                if (probe == 0) {
                    // A non-climbable result alone cannot distinguish an open trapdoor
                    // from solid ground. Cache that distinction for subsequent edges.
                    BlockState state = this.currentContext.getBlockState(candidate);
                    boolean climbable = state.is(BlockTags.CLIMBABLE);
                    this.climbableCache.put(key, climbable);
                    if (climbable) {
                        nodeCount = addDescendingClimbableNode(nodes, nodeCount, origin, candidate);
                        break;
                    }
                    probe = PathingBlockInteraction.isHandOpenableTrapDoor(state)
                            || state.isPathfindable(PathComputationType.LAND)
                            ? DESCENDING_CONTINUE : DESCENDING_STOP;
                    this.descendingProbeCache.put(key, probe);
                }
                if (probe == DESCENDING_STOP) {
                    break;
                }
            }
        }
        return nodeCount;
    }

    private int addDescendingClimbableNode(Node[] nodes, int nodeCount, BlockPos origin, BlockPos target) {
        Node departure = this.getNode(origin);
        Node destination = getClimbableNode(target);
        AABB from = getDepartureBox(departure);
        AABB to = PathNodeGeometry.bodyAt(this.mob, PathNodeGeometry.climbingPosition(target))
                .move(0.0D, FLOOR_EPSILON, 0.0D);
        CollisionGetter collisions = new PathingCollisionView(
                this.currentContext.level(), departure, destination, this.mob);
        if (!canSweepTo(from, to, collisions)
                || !hasScaffoldingClimbClearance(destination,
                        to.expandTowards(0.0D, Math.max(0.0D, from.minY - to.minY), 0.0D), collisions)) {
            return nodeCount;
        }
        return addClimbableNode(nodes, nodeCount, target);
    }

    private boolean requiresRaisedSurfaceSweep(Node origin) {
        if (origin == this.startNode && (!this.mob.onGround() || this.mob.onClimbable())) {
            return false;
        }

        BlockPos originPos = origin.asBlockPos();
        double modeledFloor = this.getFloorLevel(originPos);
        double standingFloor = origin == this.startNode ? this.mob.getBoundingBox().minY
                : PathNodeGeometry.walkingFloorY(this.currentContext.level(), originPos, this.mob, modeledFloor);
        return standingFloor > modeledFloor + RAISED_START_EPSILON
                || isBarrierSupport(this.currentContext.getBlockState(originPos.below()));
    }

    private AABB getDepartureBox(Node origin) {
        // Only the actual first edge has a live pose; later edges start from their modeled node.
        return origin == this.startNode && this.mob.onGround() ? this.mob.getBoundingBox() : getMobBoxAt(origin);
    }

    private int rejectBlockedTransitions(Node[] nodes, int nodeCount, Node origin, boolean overhangingDeparture) {
        boolean raisedSurface = requiresRaisedSurfaceSweep(origin);
        AABB originBox = null;
        int writeIndex = 0;
        for (int readIndex = 0; readIndex < nodeCount; readIndex++) {
            Node candidate = nodes[readIndex];
            if (candidate == null) {
                continue;
            }
            if (requiresGroundJumpFromFlowingWater(origin, candidate)) {
                continue;
            }

            AABB destinationBox = null;
            if (raisedSurface) {
                if (originBox == null) originBox = getDepartureBox(origin);
                destinationBox = getMobBoxAt(candidate);
                var collisions = new PathingCollisionView(this.currentContext.level(), origin, candidate, this.mob);
                if (!canSweepTo(originBox, destinationBox, collisions, overhangingDeparture)) {
                    continue;
                }
                // The native guard is live-body-only and sees actual closed blocks.
                // Operable nodes use the projected sweep, as in findAcceptedNode.
                boolean liveJump = origin == this.startNode
                        && destinationBox.minY - FLOOR_EPSILON - originBox.minY > this.mob.maxUpStep();
                if (liveJump && shouldCheckBlockClearance(candidate.type) && !super.canReachWithoutCollision(candidate)) {
                    continue;
                }
            }

            if (candidate.y > origin.y && candidate.x != origin.x && candidate.z != origin.z) {
                if (originBox == null) {
                    originBox = getDepartureBox(origin);
                }
                if (destinationBox == null) destinationBox = getMobBoxAt(candidate);
                BlockPos originPos = this.collisionPos.set(origin.x, origin.y, origin.z);
                if (destinationBox.minY - originBox.minY > this.mob.maxUpStep()
                        && this.currentContext.getBlockState(originPos)
                                .getCollisionShape(this.currentContext.level(), originPos).isEmpty()
                        && !canApproachDiagonalJump(origin, candidate, originBox)) {
                    continue;
                }
            }

            if (!raisedSurface) {
                BlockState support = this.currentContext.getBlockState(
                        this.collisionPos.set(candidate.x, candidate.y - 1, candidate.z));
                if (isBarrierSupport(support)) {
                    if (originBox == null) originBox = getDepartureBox(origin);
                    if (destinationBox == null) destinationBox = getMobBoxAt(candidate);
                    boolean raisedBarrierTop = destinationBox.minY > originBox.minY + RAISED_START_EPSILON;
                    if (raisedBarrierTop && !canSweepTo(originBox, destinationBox,
                            new PathingCollisionView(this.currentContext.level(), origin, candidate, this.mob))) {
                        continue;
                    }
                }
            }

            if (hasPassableTrapdoorEdge(origin, candidate)
                    && hasSupportedFenceDiagonal(origin, candidate)) {
                nodes[writeIndex++] = candidate;
            }
        }

        for (int index = writeIndex; index < nodeCount; index++) {
            nodes[index] = null;
        }
        return writeIndex;
    }

    private boolean requiresGroundJumpFromFlowingWater(Node origin, Node candidate) {
        if (candidate.y <= origin.y) {
            return false;
        }
        BlockPos departurePos = this.collisionPos.set(origin.x, origin.y, origin.z);
        BlockState departure = this.currentContext.getBlockState(departurePos);
        if (!(departure.getBlock() instanceof LiquidBlock)
                || !departure.getFluidState().is(FluidTags.WATER)
                || departure.getFluidState().isSource()
                || this.currentContext.getBlockState(this.collisionPos.set(candidate.x, candidate.y, candidate.z))
                        .is(BlockTags.CLIMBABLE)) {
            return false;
        }
        // The floating evaluator's extra water floor height is not solid support for a jump.
        double departureFloor = WalkNodeEvaluator.getFloorLevel(this.currentContext.level(),
                this.collisionPos.set(origin.x, origin.y, origin.z));
        double immersion = origin.y + departure.getFluidState().getHeight(this.currentContext.level(),
                this.collisionPos) - departureFloor;
        if (departureFloor > origin.y - 1.0D
                && immersion <= this.mob.getFluidJumpThreshold()) {
            // Shallow water over real support still uses LivingEntity's grounded jump.
            return false;
        }
        return getMobBoxAt(candidate).minY - departureFloor > this.mob.maxUpStep();
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
        // Sliding past a low corner cannot clear an overhead block before the jump begins.
        for (int y = origin.y + 1; y < Mth.ceil(approachBox.maxY); y++) {
            if (blocksJumpApproach(candidate.x, y, origin.z, approachBox)
                    || blocksJumpApproach(origin.x, y, candidate.z, approachBox)) {
                return false;
            }
        }
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

    private boolean hasPassableTrapdoorEdge(Node origin, Node candidate) {
        int dx = Math.abs(origin.x - candidate.x);
        int dz = Math.abs(origin.z - candidate.z);
        if (candidate.y > origin.y || dx > 1 || dz > 1 || dx + dz == 0) {
            return true;
        }

        boolean crossesTrapdoor = isOpenTrapdoor(origin.x, origin.y, origin.z)
                || isOpenTrapdoor(candidate.x, candidate.y, candidate.z)
                || (dx == 1 && dz == 1
                    && (isOpenTrapdoor(origin.x, origin.y, candidate.z)
                        || isOpenTrapdoor(candidate.x, origin.y, origin.z)));
        if (!crossesTrapdoor) {
            return true;
        }
        double stepUp = uprightDoorwayStepHeight(origin, candidate);

        AABB departure = getDepartureBox(origin);
        AABB destination = getMobBoxAt(candidate);
        CollisionGetter collisions = new PathingCollisionView(this.currentContext.level(), origin, candidate, this.mob);
        // A descent must clear the departure panel before the body can fall to the next floor.
        double travelX = destination.minX - departure.minX;
        double travelZ = destination.minZ - departure.minZ;
        int steps = Mth.ceil(Math.max(Math.abs(travelX), Math.abs(travelZ)) * 16.0D);
        for (int step = 1; step <= steps; step++) {
            double fraction = (double) step / steps;
            AABB footprint = departure.move(travelX * fraction, 0.0D, travelZ * fraction);
            if (!collisions.noBlockCollision(this.mob, footprint)
                    && (stepUp <= 0.0D || stepUp > this.mob.maxUpStep()
                    || !collisions.noBlockCollision(this.mob, footprint.move(0.0D, stepUp + FLOOR_EPSILON, 0.0D)))) {
                return false;
            }
        }
        return true;
    }

    private double uprightDoorwayStepHeight(Node origin, Node candidate) {
        BlockPos previous = origin.asBlockPos();
        BlockPos next = candidate.asBlockPos();
        BlockState first = this.currentContext.getBlockState(previous);
        BlockState second = this.currentContext.getBlockState(next);
        BlockState panel = PathingBlockInteraction.canFoldUprightTrapdoor(first, previous, next) ? first
                : PathingBlockInteraction.canFoldUprightTrapdoor(second, next, previous) ? second : null;
        if (panel == null) return 0.0D;
        return panel.getValue(TrapDoorBlock.HALF) == Half.TOP
                ? 1.0D : 0.1875D;
    }

    boolean isOpenTrapdoor(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        byte cached = this.openTrapdoorCache.get(key);
        if (cached != 0) {
            return cached == TRAPDOOR_OPEN;
        }
        BlockState state = this.currentContext.getBlockState(this.collisionPos.set(x, y, z));
        boolean open = state.getBlock() instanceof TrapDoorBlock && state.getValue(TrapDoorBlock.OPEN);
        this.openTrapdoorCache.put(key, open ? TRAPDOOR_OPEN : TRAPDOOR_CLOSED_OR_ABSENT);
        return open;
    }

    private boolean hasSupportedFenceDiagonal(Node origin, Node candidate) {
        if (origin.y != candidate.y || Math.abs(origin.x - candidate.x) != 1
                || Math.abs(origin.z - candidate.z) != 1
                || (!this.currentContext.getBlockState(this.collisionPos.set(origin.x, origin.y - 1, origin.z))
                        .is(BlockTags.FENCES)
                && !this.currentContext.getBlockState(this.collisionPos.set(candidate.x, candidate.y - 1, candidate.z))
                        .is(BlockTags.FENCES))) {
            return true;
        }

        AABB departure = getDepartureBox(origin);
        AABB destination = getMobBoxAt(candidate);
        // Clearance offsets belong to collision boxes, not the physical support heights.
        double departureFloor = departure.minY
                - (origin == this.startNode && this.mob.onGround() ? 0.0D : FLOOR_EPSILON);
        double destinationFloor = destination.minY - FLOOR_EPSILON;
        if (Math.abs(departureFloor - destinationFloor) > 1.0E-4D) {
            return true;
        }

        // The body cannot safely cut a fence-top corner if it loses its narrow support.
        AABB feet = new AABB(departure.minX, departure.minY - 0.05D, departure.minZ,
                departure.maxX, departure.minY, departure.maxZ);
        double dx = destination.minX - departure.minX;
        double dz = destination.minZ - departure.minZ;
        for (int step = 1; step < 16; step++) {
            double fraction = step / 16.0D;
            if (this.currentContext.level().noBlockCollision(this.mob,
                    feet.move(dx * fraction, 0.0D, dz * fraction))) {
                return false;
            }
        }
        return true;
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
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
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
        // Unlike a walking node, a suspended climb node has no supporting floor:
        // the mob's feet must actually reach this node's Y, not the last ground
        // surface reported by getFloorLevel below it.
        if (!node.closed && nodeCount < nodes.length && hasClimbableBodyClearance(node)) {
            AABB body = PathNodeGeometry.bodyAt(this.mob, PathNodeGeometry.climbingPosition(pos));
            if (bodyTouchesFire(body)) {
                float malus = this.mob.getPathfindingMalus(PathType.FIRE);
                if (malus < 0.0F) return nodeCount;
                node.type = PathType.FIRE;
                node.costMalus = Math.max(node.costMalus, malus);
            }
            nodes[nodeCount++] = node;
        }
        return nodeCount;
    }

    private boolean bodyTouchesFire(AABB body) {
        // Read raw occupied cells: walking path types also inspect supporting and
        // neighboring blocks, which can be safe below a suspended climber.
        for (int x = floorMin(body.minX); x <= floorMax(body.maxX); x++) {
            for (int y = floorMin(body.minY); y <= floorMax(body.maxY); y++) {
                for (int z = floorMin(body.minZ); z <= floorMax(body.maxZ); z++) {
                    if (this.currentContext.getPathTypeFromState(x, y, z) == PathType.FIRE) return true;
                }
            }
        }
        return false;
    }

    private boolean hasClimbableBodyClearance(Node node) {
        AABB box = PathNodeGeometry.bodyAt(this.mob, PathNodeGeometry.climbingPosition(node.asBlockPos()))
                .move(0.0D, FLOOR_EPSILON, 0.0D);
        if (this.currentContext.getBlockState(this.collisionPos.set(node.x, node.y, node.z))
                .is(Blocks.SCAFFOLDING)) {
            return hasScaffoldingClimbClearance(node, box, this.currentContext.level());
        }
        return hasExactBlockClearance(box);
    }

    private boolean hasScaffoldingClimbClearance(Node node, AABB box, CollisionGetter collisions) {
        // Scaffold platforms and frames may be passed while climbing/descending.
        // Ignore only this column's scaffolding, not a roof or another obstacle.
        for (int x = floorMin(box.minX); x <= floorMax(box.maxX); x++) {
            for (int y = floorMin(box.minY); y <= floorMax(box.maxY); y++) {
                for (int z = floorMin(box.minZ); z <= floorMax(box.maxZ); z++) {
                    BlockPos pos = this.collisionPos.set(x, y, z);
                    BlockState state = collisions.getBlockState(pos);
                    if (state.isAir() || state.is(Blocks.SCAFFOLDING)
                            && x == node.x && z == node.z) {
                        continue;
                    }
                    VoxelShape shape = state.getCollisionShape(
                            collisions, pos, CollisionContext.of(this.mob));
                    if (!shape.isEmpty() && Shapes.joinIsNotEmpty(
                            Shapes.create(box.move(-x, -y, -z)), shape, BooleanOp.AND)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private Node getClimbableNode(BlockPos pos) {
        Node node = this.getNode(pos);
        node.type = PathType.WALKABLE;
        node.costMalus = Math.max(node.costMalus, 0.0F);
        return node;
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        return getPathType(context, x, y, z, this.mob);
    }

    @Override
    public PathType getPathType(Mob mob, BlockPos pos) {
        return getPathType(new PathfindingContext(mob.level(), mob),
                pos.getX(), pos.getY(), pos.getZ(), mob);
    }

    private PathType getPathType(PathfindingContext context, int x, int y, int z, @Nullable Mob mob) {
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

        PathType type = super.getPathType(context, x, y, z);
        if (type == PathType.OPEN && mob != null && y > mob.level().getMinY()) {
            BlockPos supportPos = this.collisionPos.set(x, y - 1, z);
            BlockState support = context.getBlockState(supportPos);
            // Some modded top plates inherit Block's LAND pathfindability despite
            // having a full-footprint walking surface. Vanilla then calls the
            // air above them OPEN rather than WALKABLE, breaking a level crossing.
            // Check actual support geometry; narrow rails and walls are not floors.
            if (!support.isAir() && support.isPathfindable(PathComputationType.LAND)
                    && PathNodeGeometry.walkingFloorY(context.level(), supportPos, mob,
                            supportPos.getY()) >= y - FLOOR_EPSILON) {
                return WalkNodeEvaluator.checkNeighbourBlocks(context, x, y, z, PathType.WALKABLE);
            }
        }
        return type;
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
    protected Node findAcceptedNode(int x, int y, int z, int jumpSize, double nodeHeight, Direction travelDirection, PathType blockPathTypeCurrent) {
        double departureFloor = Math.max(nodeHeight, this.liveOverhangDepartureFloor);
        Node node = super.findAcceptedNode(x, y, z, jumpSize, departureFloor, travelDirection, blockPathTypeCurrent);
        if (node == null || node.costMalus < 0.0F) {
            return node;
        }

        return shouldCheckBlockClearance(node.type) && !hasBlockClearance(node) ? null : node;
    }

    private static boolean shouldCheckBlockClearance(PathType type) {
        return type != PathType.WALKABLE_DOOR
                && type != PathType.DOOR_OPEN
                && type != PathType.TRAPDOOR
                && type != PathType.ON_TOP_OF_TRAPDOOR;
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
        BlockPos pos = this.collisionPos.set(node.x, node.y, node.z);
        Vec3 standing = PathNodeGeometry.walkingPosition(this.currentContext.level(), pos, this.mob, this.getFloorLevel(pos));
        return PathNodeGeometry.bodyAt(this.mob, standing).move(0.0D, FLOOR_EPSILON, 0.0D);
    }

    private static final int MAX_JUMP_SWEEP_TICKS = 20;

    private boolean canSweepTo(AABB startBox, AABB destinationBox, CollisionGetter collisions) {
        return canSweepTo(startBox, destinationBox, collisions, false);
    }

    private boolean canSweepTo(AABB startBox, AABB destinationBox, CollisionGetter collisions,
                               boolean overhangingDeparture) {
        double rise = destinationBox.minY - startBox.minY;
        Vec3 travel = new Vec3(
                destinationBox.minX - startBox.minX,
                0.0D,
                destinationBox.minZ - startBox.minZ
        );
        int steps = Mth.ceil(travel.length() / startBox.getSize());
        if (steps <= 0) {
            return rise <= RAISED_START_EPSILON
                    || collisions.noBlockCollision(this.mob, startBox.expandTowards(0.0D, rise, 0.0D));
        }
        if (rise > this.mob.maxUpStep()) {
            if (canStepUpTo(startBox, destinationBox, travel, collisions, overhangingDeparture)) {
                return true;
            }
            if (!collisions.noBlockCollision(this.mob, startBox.expandTowards(0.0D, rise, 0.0D))) {
                return false;
            }
            return canJumpSweepTo(startBox, rise, travel, steps, collisions);
        }

        if (overhangingDeparture && rise > RAISED_START_EPSILON) {
            // MoveControl does not jump a step within maxUpStep. Carrying the body
            // at the destination height would admit a gap the villager falls into.
            return canStepUpTo(startBox, destinationBox, travel, collisions, true);
        }

        AABB sweepBox = startBox;
        if (rise > RAISED_START_EPSILON) {
            if (!collisions.noBlockCollision(this.mob, startBox.expandTowards(0.0D, rise, 0.0D))) {
                return false;
            }
            sweepBox = startBox.move(0.0D, rise, 0.0D);
        }
        return canSweepHorizontally(sweepBox, travel, steps, collisions);
    }

    /**
     * Multi-tread obstacles such as stairs can exceed {@code maxUpStep()} overall while remaining
     * walkable via consecutive step-ups without jumping.
     */
    private boolean canStepUpTo(AABB startBox, AABB destinationBox, Vec3 travel, CollisionGetter collisions,
                                boolean needsContinuousFooting) {
        double maxStep = this.mob.maxUpStep();
        if (maxStep <= 0.0D) {
            return false;
        }

        int stepCount = Math.max(4, Mth.ceil(travel.length() * 4.0D));
        double stepX = travel.x / stepCount;
        double stepZ = travel.z / stepCount;
        AABB current = startBox;
        for (int index = 0; index < stepCount; index++) {
            AABB next = current.move(stepX, 0.0D, stepZ);
            if (collisions.noBlockCollision(this.mob, next)) {
                if (needsContinuousFooting && collisions.noBlockCollision(this.mob,
                        new AABB(next.minX, next.minY - FLOOR_EPSILON, next.minZ, next.maxX, next.minY, next.maxZ))) {
                    return false;
                }
                current = next;
                continue;
            }

            double bestStepUp = Double.POSITIVE_INFINITY;
            for (VoxelShape shape : collisions.getBlockCollisions(this.mob, next)) {
                for (double yCoord : shape.getCoords(Direction.Axis.Y)) {
                    double stepUp = yCoord - current.minY;
                    if (stepUp <= BOX_EPSILON || stepUp > maxStep + BOX_EPSILON || stepUp >= bestStepUp) {
                        continue;
                    }
                    if (collisions.noBlockCollision(this.mob, current.expandTowards(0.0D, stepUp, 0.0D))
                            && collisions.noBlockCollision(this.mob, next.move(0.0D, stepUp, 0.0D))) {
                        bestStepUp = stepUp;
                    }
                }
            }
            if (!Double.isFinite(bestStepUp)) {
                return false;
            }
            current = next.move(0.0D, bestStepUp, 0.0D);
        }
        return current.minY >= destinationBox.minY - FLOOR_EPSILON - BOX_EPSILON;
    }

    /**
     * A sheer step taller than {@code maxUpStep()} crosses only on discrete vanilla jump ticks.
     * Raised partial floors can overshoot a tight ceiling window that continuous min-rise sweeps admit.
     */
    private boolean canJumpSweepTo(
            AABB startBox,
            double rise,
            Vec3 travel,
            int steps,
            CollisionGetter collisions
    ) {
        double jumpPower = getJumpPowerAt(startBox, collisions);
        if (jumpPower <= 1.0E-5F) {
            return false;
        }
        double gravity = this.mob.getGravity();
        if (gravity <= 0.0D) {
            return canSweepHorizontally(startBox.move(0.0D, rise, 0.0D), travel, steps, collisions);
        }

        double yOffset = 0.0D;
        double velocityY = jumpPower;
        for (int tick = 0; tick < MAX_JUMP_SWEEP_TICKS; tick++) {
            AABB current = startBox.move(0.0D, yOffset, 0.0D);
            double dy = velocityY;
            double collidedDy = Shapes.collide(
                    Direction.Axis.Y,
                    current,
                    collisions.getBlockCollisions(this.mob, current.expandTowards(0.0D, dy, 0.0D)),
                    dy
            );
            yOffset += collidedDy;
            boolean verticalCollision = Math.abs(collidedDy - dy) > BOX_EPSILON;
            if (verticalCollision) {
                if (dy > 0.0D) {
                    velocityY = 0.0D;
                } else {
                    break;
                }
            }
            if (yOffset >= rise - FLOOR_EPSILON
                    && canSweepHorizontally(startBox.move(0.0D, yOffset, 0.0D), travel, steps, collisions)) {
                return true;
            }
            velocityY = (velocityY - gravity) * 0.98D;
            if (yOffset <= 0.0D && velocityY <= 0.0D) {
                break;
            }
        }
        return false;
    }

    private double getJumpPowerAt(AABB startBox, CollisionGetter collisions) {
        int x = Mth.floor((startBox.minX + startBox.maxX) * 0.5D);
        int z = Mth.floor((startBox.minZ + startBox.maxZ) * 0.5D);
        float inBlockFactor = collisions.getBlockState(this.collisionPos.set(x, Mth.floor(startBox.minY), z))
                .getBlock().getJumpFactor();
        float jumpFactor = inBlockFactor != 1.0F ? inBlockFactor
                : collisions.getBlockState(this.collisionPos.set(x, Mth.floor(startBox.minY - 0.500001F), z))
                        .getBlock().getJumpFactor();
        return (float) this.mob.getAttributeValue(Attributes.JUMP_STRENGTH) * jumpFactor
                + this.mob.getJumpBoostPower();
    }

    private boolean canSweepHorizontally(AABB sweepBox, Vec3 travel, int steps, CollisionGetter collisions) {
        Vec3 step = travel.scale(1.0D / steps);
        for (int index = 0; index < steps; index++) {
            sweepBox = sweepBox.move(step);
            if (!collisions.noBlockCollision(this.mob, sweepBox)) {
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
