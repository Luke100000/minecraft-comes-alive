package net.conczin.mca.entity.ai.navigation;

import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.conczin.mca.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public class MCAWalkNodeEvaluator extends WalkNodeEvaluator {
    private static final int MAX_LADDER_EDGE_OFFSET = 2;
    private final Long2BooleanMap clearanceCache = new Long2BooleanOpenHashMap();
    private final Long2BooleanMap climbableCache = new Long2BooleanOpenHashMap();

    @Override
    public void done() {
        this.clearanceCache.clear();
        this.climbableCache.clear();
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
            return this.getStartNodeAtY(pos, y - 1);
        }

        BlockPos mobPos = this.mob.blockPosition();
        if (isClimbable(mobPos)) {
            return getClimbableNode(mobPos);
        }

        if (this.mob.onClimbable()) {
            for (int drop = 1; drop <= MAX_LADDER_EDGE_OFFSET; drop++) {
                BlockPos candidate = mobPos.below(drop);

                if (isClimbable(candidate)) {
                    return getClimbableNode(candidate);
                }

                if (!this.currentContext.getBlockState(candidate)
                        .isPathfindable(PathComputationType.LAND)) {
                    break;
                }
            }
        }

        return super.getStart();
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
    protected Node getStartNode(BlockPos pos) {
        return isClimbable(pos) ? getClimbableNode(pos) : super.getStartNode(pos);
    }

    @Override
    protected boolean canStartAt(BlockPos pos) {
        return isClimbable(pos) || super.canStartAt(pos);
    }

    @Override
    public int getNeighbors(Node[] nodes, Node origin) {
        BlockPos originPos = origin.asBlockPos();
        boolean originClimbable = isClimbable(originPos);
        int nodeCount = super.getNeighbors(nodes, origin);

        if (originClimbable) {
            nodeCount = removeLargeVerticalTransitions(nodes, nodeCount, origin);
            if (!isClimbable(originPos.above())) {
                nodeCount = addUpperFloorExits(nodes, nodeCount, originPos);
            }
        }

        BlockPos above = originPos.above();
        if (isClimbable(above)) {
            nodeCount = addClimbableNode(nodes, nodeCount, above);
        }

        BlockPos below = originPos.below();
        if (isClimbable(below)) {
            nodeCount = addClimbableNode(nodes, nodeCount, below);
        }

        if (!originClimbable) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos edgePos = originPos.relative(direction);
                for (int drop = 1; drop <= MAX_LADDER_EDGE_OFFSET; drop++) {
                    BlockPos ladderEntry = edgePos.below(drop);

                    if (isClimbable(ladderEntry)) {
                        nodeCount = addClimbableNode(nodes, nodeCount, ladderEntry);
                        break;
                    }

                    if (!this.currentContext.getBlockState(ladderEntry)
                            .isPathfindable(PathComputationType.LAND)) {
                        break;
                    }
                }
            }
        }

        return nodeCount;
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

    private int addUpperFloorExits(Node[] nodes, int nodeCount, BlockPos ladder) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int rise = 1; rise <= MAX_LADDER_EDGE_OFFSET; rise++) {
                Node node = getStartNode(ladder.relative(direction).above(rise));
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

    private boolean isClimbable(BlockPos pos) {
        long key = pos.asLong();
        if (this.climbableCache.containsKey(key)) {
            return this.climbableCache.get(key);
        }

        boolean climbable = this.currentContext.getBlockState(pos).is(BlockTags.CLIMBABLE);
        this.climbableCache.put(key, climbable);
        return climbable;
    }

    private boolean hasReachableClimbableBelow(BlockPos pos) {
        for (int drop = 1; drop <= MAX_LADDER_EDGE_OFFSET; drop++) {
            BlockPos candidate = pos.below(drop);
            if (isClimbable(candidate)) {
                return true;
            }

            if (!this.currentContext.getBlockState(candidate)
                    .isPathfindable(PathComputationType.LAND)) {
                return false;
            }
        }
        return false;
    }

    @Nullable
    @Override
    protected Node findAcceptedNode(int x, int y, int z, int maxYStep, double currentFloor, Direction direction, PathType previousType) {
        BlockPos pos = new BlockPos(x, y, z);
        if (isClimbable(pos)) {
            return getClimbableNode(pos);
        }
        if (hasReachableClimbableBelow(pos)) {
            return null;
        }

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
        AABB clearanceBox = getNodeClearanceBox(node);
        if (!Config.getInstance().villagerPathfindingCheckAllNodeCollisions
            && !PathfindingBlacklist.overlapsSpecialCollisionBlock(this.currentContext.level(), clearanceBox)) {
            return true;
        }

        long key = BlockPos.asLong(node.x, node.y, node.z);
        if (this.clearanceCache.containsKey(key)) {
            return this.clearanceCache.get(key);
        }

        boolean hasClearance = this.currentContext.level().noBlockCollision(this.mob, clearanceBox);
        this.clearanceCache.put(key, hasClearance);
        return hasClearance;
    }

    private static AABB getNodeClearanceBox(Node node) {
        return new AABB(
                node.x, node.y, node.z,
                node.x + 1.0D, node.y + 2.0D, node.z + 1.0D
        );
    }

}
