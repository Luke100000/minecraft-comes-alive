package net.conczin.mca.entity.ai.navigation;

import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.conczin.mca.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public class MCAWalkNodeEvaluator extends WalkNodeEvaluator {
    private final Long2BooleanMap clearanceCache = new Long2BooleanOpenHashMap();

    @Override
    public void done() {
        this.clearanceCache.clear();
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
