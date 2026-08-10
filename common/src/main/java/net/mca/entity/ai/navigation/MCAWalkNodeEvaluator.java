package net.mca.entity.ai.navigation;

import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.mca.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;

/**
 * Vanilla 1.20.1 land evaluation with MCA's targeted clearance checks.
 * The newer evaluator avoids replacing vanilla node classification wholesale;
 * only nodes that may actually need exact entity clearance are checked.
 */
public class MCAWalkNodeEvaluator extends WalkNodeEvaluator {
    private static final int MAX_CLIMBABLE_VERTICAL_OFFSET = 2;
    private final Long2BooleanMap clearanceCache = new Long2BooleanOpenHashMap();
    private final Long2BooleanMap climbableCache = new Long2BooleanOpenHashMap();
    private final BlockPos.MutableBlockPos climbablePos = new BlockPos.MutableBlockPos();

    @Override
    public void done() {
        clearanceCache.clear();
        climbableCache.clear();
        super.done();
    }

    @Override
    public Node getStart() {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int y = mob.getBlockY();
        FluidState fluid = level.getFluidState(pos.set(mob.getX(), y, mob.getZ()));

        if (canFloat() && mob.isInWater() && fluid.is(FluidTags.WATER)) {
            while (fluid.is(FluidTags.WATER)) {
                fluid = level.getFluidState(pos.set(mob.getX(), ++y, mob.getZ()));
            }
            return getStartAtY(pos, y - 1);
        }

        BlockPos entityPos = mob.blockPosition();
        if (isClimbable(entityPos)) {
            return getClimbableNode(entityPos);
        }

        if (mob.onClimbable()) {
            for (int drop = 1; drop <= MAX_CLIMBABLE_VERTICAL_OFFSET; drop++) {
                BlockPos candidate = entityPos.below(drop);
                if (isClimbable(candidate)) {
                    return getClimbableNode(candidate);
                }
                if (!level.getBlockState(candidate).isPathfindable(level, candidate, net.minecraft.world.level.pathfinder.PathComputationType.LAND)) {
                    break;
                }
            }
        }

        return super.getStart();
    }

    @Override
    public int getNeighbors(Node[] successors, Node node) {
        int count = super.getNeighbors(successors, node);
        if (!isClimbable(node.x, node.y, node.z)) {
            return count;
        }

        count = removeLargeVerticalTransitions(successors, count, node);
        BlockPos origin = node.asBlockPos();
        BlockPos above = origin.above();
        if (isClimbable(above)) {
            count = addClimbableNode(successors, count, above);
        } else {
            count = addUpperFloorExits(successors, count, origin);
        }

        BlockPos below = origin.below();
        if (isClimbable(below)) {
            count = addClimbableNode(successors, count, below);
        }
        return count;
    }

    private int removeLargeVerticalTransitions(Node[] successors, int count, Node origin) {
        int writeIndex = 0;
        for (int i = 0; i < count; i++) {
            Node node = successors[i];
            if (Math.abs(node.y - origin.y) <= 1) {
                successors[writeIndex++] = node;
            }
        }
        for (int i = writeIndex; i < count; i++) {
            successors[i] = null;
        }
        return writeIndex;
    }

    private int addUpperFloorExits(Node[] successors, int count, BlockPos climbable) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int rise = 1; rise <= MAX_CLIMBABLE_VERTICAL_OFFSET; rise++) {
                BlockPos candidate = climbable.relative(direction).above(rise);
                Node node = getStartNode(candidate);
                if (node.type == BlockPathTypes.OPEN || node.costMalus < 0.0F || !hasExactClearance(node)) {
                    continue;
                }
                if (!node.closed && count < successors.length) {
                    successors[count++] = node;
                }
                break;
            }
        }
        return count;
    }

    private int addClimbableNode(Node[] successors, int count, BlockPos pos) {
        Node node = getClimbableNode(pos);
        if (!node.closed && count < successors.length) {
            successors[count++] = node;
        }
        return count;
    }

    private Node getClimbableNode(BlockPos pos) {
        Node node = getNode(pos);
        node.type = BlockPathTypes.WALKABLE;
        node.costMalus = Math.max(node.costMalus, 0.0F);
        return node;
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockGetter world, int x, int y, int z, Mob entity) {
        return world.getBlockState(climbablePos.set(x, y, z)).is(BlockTags.CLIMBABLE)
                ? BlockPathTypes.WALKABLE
                : super.getBlockPathType(world, x, y, z, entity);
    }

    private boolean isClimbable(BlockPos pos) {
        return isClimbable(pos.getX(), pos.getY(), pos.getZ());
    }

    private boolean isClimbable(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        if (climbableCache.containsKey(key)) {
            return climbableCache.get(key);
        }
        boolean climbable = level.getBlockState(climbablePos.set(x, y, z)).is(BlockTags.CLIMBABLE);
        climbableCache.put(key, climbable);
        return climbable;
    }

    private Node getStartAtY(BlockPos.MutableBlockPos pos, int y) {
        BlockPos entityPos = mob.blockPosition();
        if (!canStartAt(pos.set(entityPos.getX(), y, entityPos.getZ()))) {
            AABB box = mob.getBoundingBox();
            if (canStartAt(pos.set(box.minX, y, box.minZ))
                    || canStartAt(pos.set(box.minX, y, box.maxZ))
                    || canStartAt(pos.set(box.maxX, y, box.minZ))
                    || canStartAt(pos.set(box.maxX, y, box.maxZ))) {
                return getStartNode(pos);
            }
        }

        return getStartNode(new BlockPos(entityPos.getX(), y, entityPos.getZ()));
    }

    @Override
    protected boolean isNeighborValid(Node node, Node previous) {
        return node != null
                && !node.closed
                && (node.costMalus >= 0.0F || previous.costMalus < 0.0F)
                && hasExactClearance(node);
    }

    @Override
    protected boolean isDiagonalValid(Node node, Node xNode, Node zNode, Node diagonal) {
        if (diagonal == null || xNode == null || zNode == null || diagonal.closed) {
            return false;
        }
        if (xNode.y > node.y || zNode.y > node.y) {
            return false;
        }
        if (xNode.type == BlockPathTypes.WALKABLE_DOOR
                || zNode.type == BlockPathTypes.WALKABLE_DOOR
                || diagonal.type == BlockPathTypes.WALKABLE_DOOR) {
            return false;
        }
        boolean narrowFence = xNode.type == BlockPathTypes.FENCE
                && zNode.type == BlockPathTypes.FENCE
                && mob.getBbWidth() < 0.5F;
        return diagonal.costMalus >= 0.0F
                && (xNode.y < node.y || xNode.costMalus >= 0.0F || narrowFence)
                && (zNode.y < node.y || zNode.costMalus >= 0.0F || narrowFence)
                && hasExactClearance(xNode)
                && hasExactClearance(zNode)
                && hasExactClearance(diagonal);
    }

    private boolean hasExactClearance(Node node) {
        if (!shouldCheckExactClearance(node.type)) {
            return true;
        }

        AABB clearanceBox = getNodeClearanceBox(node);
        if (!Config.getInstance().villagerPathfindingCheckAllNodeCollisions
                && !PathfindingBlacklist.overlapsSpecialCollisionBlock(level, clearanceBox)) {
            return true;
        }

        long key = BlockPos.asLong(node.x, node.y, node.z);
        if (clearanceCache.containsKey(key)) {
            return clearanceCache.get(key);
        }

        boolean hasClearance = !level.getBlockCollisions(mob, clearanceBox).iterator().hasNext();
        clearanceCache.put(key, hasClearance);
        return hasClearance;
    }

    private static boolean shouldCheckExactClearance(BlockPathTypes type) {
        return type != BlockPathTypes.WALKABLE_DOOR
                && type != BlockPathTypes.DOOR_OPEN
                && type != BlockPathTypes.TRAPDOOR;
    }

    private static AABB getNodeClearanceBox(Node node) {
        return new AABB(
                node.x, node.y, node.z,
                node.x + 1.0D, node.y + 2.0D, node.z + 1.0D
        );
    }
}
