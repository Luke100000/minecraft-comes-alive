package net.conczin.mca.entity.ai.navigation;

import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.conczin.mca.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public class MCAWalkNodeEvaluator extends WalkNodeEvaluator {
    private static final double FLOOR_EPSILON = 0.001D;
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
        BlockPos pos = this.mob.blockPosition();

        /*
         * While physically inside a ladder, start pathfinding from the
         * ladder block the mob currently occupies. Vanilla ground start
         * resolution may otherwise search downward for a floor and effectively
         * start us at the bottom of the ladder column.
         */
        if (isClimbable(pos)) {
            return getClimbableNode(pos);
        }

        /*
         * At the top edge of a ladder the mob's block position can already
         * be one block above the actual ladder while its bounding box is still
         * considered climbable.
         */
        if (this.mob.onClimbable()) {
            for (int drop = 1; drop <= MAX_LADDER_EDGE_OFFSET; drop++) {
                BlockPos candidate = pos.below(drop);

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

    @Override
    protected Node getStartNode(BlockPos pos) {
        return isClimbable(pos)
                ? getClimbableNode(pos)
                : super.getStartNode(pos);
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

        /*
         * Vanilla walking pathfinding may resolve an adjacent OPEN node by
         * falling until it finds ground. From a ladder this can create invalid
         * shortcuts such as:
         *
         * LADDER y63 -> WALKABLE y60
         *
         * Ladder traversal itself should remain one vertical block at a time.
         */
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

                /*
                 * The top ladder block may be one or two blocks below the
                 * walkable floor edge.
                 */
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

        /*
         * Clear stale entries after compacting the neighbor array.
         */
        for (int i = writeIndex; i < nodeCount; i++) {
            nodes[i] = null;
        }

        return writeIndex;
    }

    private int addUpperFloorExits(Node[] nodes, int nodeCount, BlockPos ladder) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int rise = 1; rise <= MAX_LADDER_EDGE_OFFSET; rise++) {
                BlockPos exit = ladder.relative(direction).above(rise);
                Node node = getStartNode(exit);

                if (node.type == PathType.OPEN
                        || node.costMalus < 0.0F
                        || !canOccupyNode(node)) {
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

        boolean climbable = this.currentContext
                .getBlockState(pos)
                .is(BlockTags.CLIMBABLE);

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
    protected Node findAcceptedNode(
            int x,
            int y,
            int z,
            int maxYStep,
            double currentFloor,
            Direction direction,
            PathType previousType
    ) {
        BlockPos pos = new BlockPos(x, y, z);

        if (isClimbable(pos)) {
            return getClimbableNode(pos);
        }

        /*
         * A climbable directly below this candidate must be entered through
         * the explicit ladder edges created in getNeighbors().
         */
        if (hasReachableClimbableBelow(pos)) {
            return null;
        }

        Node node = super.findAcceptedNode(
                x,
                y,
                z,
                maxYStep,
                currentFloor,
                direction,
                previousType
        );

        if (node == null || node.costMalus < 0.0F) {
            return node;
        }

        return shouldCheckExactClearance(node.type) && !canOccupyNode(node)
                ? null
                : node;
    }

    private static boolean shouldCheckExactClearance(PathType type) {
        return type != PathType.WALKABLE_DOOR
                && type != PathType.DOOR_OPEN
                && type != PathType.TRAPDOOR
                && type != PathType.DANGER_TRAPDOOR;
    }

    private boolean canOccupyNode(Node node) {
        AABB box = getMobBoxAt(node);

        if (!Config.getInstance().villagerPathfindingCheckAllNodeCollisions
                && !PathfindingBlacklist.overlapsSpecialCollisionBlock(
                this.currentContext.level(),
                box
        )) {
            return true;
        }

        long key = BlockPos.asLong(node.x, node.y, node.z);

        if (this.clearanceCache.containsKey(key)) {
            return this.clearanceCache.get(key);
        }

        boolean hasClearance = this.currentContext
                .level()
                .noCollision(this.mob, box);

        this.clearanceCache.put(key, hasClearance);
        return hasClearance;
    }

    private AABB getMobBoxAt(Node node) {
        AABB box = this.mob.getBoundingBox();
        BlockPos pos = new BlockPos(node.x, node.y, node.z);
        double floorY = this.getFloorLevel(pos);

        return box.move(
                node.x + 0.5D - this.mob.getX(),
                floorY + FLOOR_EPSILON - this.mob.getY(),
                node.z + 0.5D - this.mob.getZ()
        );
    }
}
