package net.conczin.mca.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;

public class MCAWalkNodeEvaluator extends WalkNodeEvaluator {
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
}
