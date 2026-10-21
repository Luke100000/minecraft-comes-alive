package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.entity.ai.PathingBlockInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Read-only transition state for vanilla collision checking; the brain still owns all block mutation. */
final class PathingCollisionView implements CollisionGetter {
    private final CollisionGetter level;
    private final BlockPos first;
    private final BlockPos second;
    private final int bodyHeight;

    PathingCollisionView(CollisionGetter level, Node first, Node second, Mob mob) {
        this.level = level;
        this.first = first.asBlockPos();
        this.second = second.asBlockPos();
        this.bodyHeight = Mth.ceil(mob.getBbHeight());
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState state = this.level.getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.OPEN) || state.getBlock() instanceof TrapDoorBlock) return state;

        boolean operable;
        if (PathingBlockInteraction.canInteractWithDoor(state)) {
            BlockPos lower = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            operable = isDoorNode(lower, this.first) || isDoorNode(lower, this.second);
        } else if (PathingBlockInteraction.isDramaticDoorOpenable(state)) {
            operable = pos.equals(this.first) || pos.equals(this.second)
                    || isLinkedTallDoorPart(state, pos, this.first) || isLinkedTallDoorPart(state, pos, this.second);
        } else {
            operable = (isInBodyColumn(pos, this.first) || isInBodyColumn(pos, this.second))
                    && PathingBlockInteraction.canInteractWithFenceGate(state);
        }
        return operable ? state.setValue(BlockStateProperties.OPEN,
                PathingBlockInteraction.shouldBeOpenForMovement(state, this.first, this.second)) : state;
    }

    private static boolean isDoorNode(BlockPos lower, BlockPos node) {
        return lower.getX() == node.getX() && lower.getZ() == node.getZ()
                && (node.getY() == lower.getY() || node.getY() == lower.getY() + 1);
    }

    private boolean isInBodyColumn(BlockPos pos, BlockPos node) {
        return pos.getX() == node.getX() && pos.getZ() == node.getZ()
                && pos.getY() >= node.getY() && pos.getY() - node.getY() < this.bodyHeight;
    }

    private boolean isLinkedTallDoorPart(BlockState state, BlockPos pos, BlockPos node) {
        if (pos.getX() != node.getX() || pos.getZ() != node.getZ()) return false;
        int offset = tallDoorPartOffset(state);
        if (offset < 0) return false;
        BlockPos lower = pos.below(offset);
        // Dramatic Doors propagates OPEN between the lower/middle/upper thirds.
        // Project only linked parts of a door the interaction task can operate.
        int nodePart = node.getY() - lower.getY();
        if (nodePart < 0 || nodePart > 2) return false;
        BlockState linked = this.level.getBlockState(node);
        return linked.is(state.getBlock()) && tallDoorPartOffset(linked) == nodePart;
    }

    private static int tallDoorPartOffset(BlockState state) {
        Property<?> third = state.getBlock().getStateDefinition().getProperty("third");
        if (third == null || !(state.getValue(third) instanceof StringRepresentable part)) return -1;
        return switch (part.getSerializedName()) {
            case "lower" -> 0;
            case "middle" -> 1;
            case "upper" -> 2;
            default -> -1;
        };
    }

    @Override
    @Nullable
    public BlockGetter getChunkForCollisions(int x, int z) {
        // Keep vanilla's collision iteration, shape expansion, and region's loaded-chunk boundary.
        return this.level.getChunkForCollisions(x, z) == null ? null : this;
    }

    @Override
    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos) { return this.level.getBlockEntity(pos); }

    @Override
    public FluidState getFluidState(BlockPos pos) { return this.level.getFluidState(pos); }

    @Override
    public int getHeight() { return this.level.getHeight(); }

    @Override
    public int getMinY() { return this.level.getMinY(); }

    @Override
    public WorldBorder getWorldBorder() { return this.level.getWorldBorder(); }

    @Override
    public List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB box) {
        return this.level.getEntityCollisions(entity, box);
    }
}
