package net.conczin.mca.entity.ai.navigation;

import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.conczin.mca.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public class MCAWalkNodeEvaluator extends WalkNodeEvaluator {
    private static final double FLOOR_EPSILON = 0.001D;
    private final Long2BooleanMap clearanceCache = new Long2BooleanOpenHashMap();

    @Override
    public void done() {
        this.clearanceCache.clear();
        super.done();
    }

    @Nullable
    @Override
    protected Node findAcceptedNode(int x, int y, int z, int maxYStep, double currentFloor, Direction direction, PathType previousType) {
        Node node = super.findAcceptedNode(x, y, z, maxYStep, currentFloor, direction, previousType);
        if (node == null || node.costMalus < 0.0F) {
            return node;
        }

        return shouldCheckExactClearance(node.type) && !canOccupyNode(node) ? null : node;
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
            && !PathfindingBlacklist.overlapsSpecialCollisionBlock(this.currentContext.level(), box)) {
            return true;
        }

        long key = BlockPos.asLong(node.x, node.y, node.z);
        if (this.clearanceCache.containsKey(key)) {
            return this.clearanceCache.get(key);
        }

        boolean hasClearance = this.currentContext.level().noCollision(this.mob, box);
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
