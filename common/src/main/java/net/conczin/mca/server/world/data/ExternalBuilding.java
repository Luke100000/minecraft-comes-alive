package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A grouped/open-air village site such as a Graveyard or Town Center. */
public final class ExternalBuilding extends Building {
    public ExternalBuilding(BlockPos pos) {
        super(pos);
    }

    public ExternalBuilding(CompoundTag tag) {
        super(tag);
    }

    @Override
    public boolean isFunctionalRoom() {
        return false;
    }

    @Override
    public boolean isStrictScan() {
        return false;
    }

    @Override
    public boolean containsPos(Vec3i pos) {
        return pos.closerThan(getCenter(), getBuildingType().getMargin());
    }

    public void validateBlocks(Level world) {
        setLastScan(world.getGameTime());
        for (Map.Entry<net.minecraft.resources.ResourceLocation, List<BlockPos>> positions : getBlocks().entrySet()) {
            positions.getValue().removeIf(pos -> !net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(world.getBlockState(pos).getBlock()).equals(positions.getKey()));
        }
    }

    public void addPOI(Level world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        removeBlock(block, pos);
        addBlock(block, pos);
        validateBlocks(world);

        List<BlockPos> positions = new ArrayList<>(getBlockPosStream().toList());
        if (positions.isEmpty()) {
            return;
        }
        BlockPos center = positions.stream().reduce(BlockPos.ZERO, BlockPos::offset);
        int n = positions.size();
        // External sites use their grouped POIs as display geometry; a one-cell footprint is enough.
        setGeometry(new BlockPos(center.getX() / n, center.getY() / n, center.getZ() / n),
                new BlockPos(center.getX() / n, center.getY() / n, center.getZ() / n), n, null);
    }
}
