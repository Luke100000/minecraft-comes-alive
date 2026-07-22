package net.mca.entity.ai.navigation;

import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.mca.Config;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/**
 * Vanilla 1.20.1 land evaluation with MCA's targeted clearance checks.
 * The newer evaluator avoids replacing vanilla node classification wholesale;
 * only nodes that may actually need exact entity clearance are checked.
 */
public class MCAWalkNodeEvaluator extends LandPathNodeMaker {
    private final Long2BooleanMap clearanceCache = new Long2BooleanOpenHashMap();

    @Override
    public void clear() {
        clearanceCache.clear();
        super.clear();
    }

    @Override
    public PathNode getStart() {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int y = entity.getBlockY();
        FluidState fluid = cachedWorld.getFluidState(pos.set(entity.getX(), y, entity.getZ()));

        if (canSwim() && entity.isTouchingWater() && fluid.isIn(FluidTags.WATER)) {
            while (fluid.isIn(FluidTags.WATER)) {
                fluid = cachedWorld.getFluidState(pos.set(entity.getX(), ++y, entity.getZ()));
            }
            return getStartAtY(pos, y - 1);
        }

        return super.getStart();
    }

    private PathNode getStartAtY(BlockPos.Mutable pos, int y) {
        BlockPos entityPos = entity.getBlockPos();
        if (!canPathThrough(pos.set(entityPos.getX(), y, entityPos.getZ()))) {
            Box box = entity.getBoundingBox();
            if (canPathThrough(pos.set(box.minX, y, box.minZ))
                    || canPathThrough(pos.set(box.minX, y, box.maxZ))
                    || canPathThrough(pos.set(box.maxX, y, box.minZ))
                    || canPathThrough(pos.set(box.maxX, y, box.maxZ))) {
                return getStart(pos);
            }
        }

        return getStart(new BlockPos(entityPos.getX(), y, entityPos.getZ()));
    }

    @Override
    protected boolean isValidAdjacentSuccessor(PathNode node, PathNode previous) {
        return node != null
                && !node.visited
                && (node.penalty >= 0.0F || previous.penalty < 0.0F)
                && hasExactClearance(node);
    }

    @Override
    protected boolean isValidDiagonalSuccessor(PathNode node, PathNode xNode, PathNode zNode, PathNode diagonal) {
        if (diagonal == null || xNode == null || zNode == null || diagonal.visited) {
            return false;
        }
        if (xNode.y > node.y || zNode.y > node.y) {
            return false;
        }
        if (xNode.type == PathNodeType.WALKABLE_DOOR
                || zNode.type == PathNodeType.WALKABLE_DOOR
                || diagonal.type == PathNodeType.WALKABLE_DOOR) {
            return false;
        }
        boolean narrowFence = xNode.type == PathNodeType.FENCE
                && zNode.type == PathNodeType.FENCE
                && entity.getWidth() < 0.5F;
        return diagonal.penalty >= 0.0F
                && (xNode.y < node.y || xNode.penalty >= 0.0F || narrowFence)
                && (zNode.y < node.y || zNode.penalty >= 0.0F || narrowFence)
                && hasExactClearance(xNode)
                && hasExactClearance(zNode)
                && hasExactClearance(diagonal);
    }

    private boolean hasExactClearance(PathNode node) {
        if (!shouldCheckExactClearance(node.type)) {
            return true;
        }

        Box clearanceBox = getNodeClearanceBox(node);
        if (!Config.getInstance().villagerPathfindingCheckAllNodeCollisions
                && !PathfindingBlacklist.overlapsSpecialCollisionBlock(cachedWorld, clearanceBox)) {
            return true;
        }

        long key = BlockPos.asLong(node.x, node.y, node.z);
        if (clearanceCache.containsKey(key)) {
            return clearanceCache.get(key);
        }

        boolean hasClearance = !cachedWorld.getBlockCollisions(entity, clearanceBox).iterator().hasNext();
        clearanceCache.put(key, hasClearance);
        return hasClearance;
    }

    private static boolean shouldCheckExactClearance(PathNodeType type) {
        return type != PathNodeType.WALKABLE_DOOR
                && type != PathNodeType.DOOR_OPEN
                && type != PathNodeType.TRAPDOOR;
    }

    private static Box getNodeClearanceBox(PathNode node) {
        return new Box(
                node.x, node.y, node.z,
                node.x + 1.0D, node.y + 2.0D, node.z + 1.0D
        );
    }
}
