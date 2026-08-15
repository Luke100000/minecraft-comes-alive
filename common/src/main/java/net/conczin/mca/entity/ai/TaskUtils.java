package net.conczin.mca.entity.ai;

import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public interface TaskUtils {
    /**
     * Finds a y position given an x,y,z coordinate that is assumed to be the world's "ground".
     *
     * @param world The world in which blocks will be tested
     * @param x     X coordinate
     * @param y     Y coordinate, used as the starting height for finding ground.
     * @param z     Z coordinate
     * @return Integer representing the air block above the first non-air block given the provided ordered triples.
     */
    static int getSpawnSafeTopLevel(Level world, int x, int y, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, Math.min(y, world.getMaxBuildHeight()), z);
        while (world.isEmptyBlock(pos.move(Direction.DOWN)) && pos.getY() > world.getMinBuildHeight()) {}

        return pos.getY() + 1;
    }

    static List<BlockPos> getNearbyBlocks(BlockPos origin, Level world, @Nullable Predicate<BlockState> filter, int xzDist, int yDist) {
        return BlockPos.withinManhattanStream(origin, xzDist, yDist, xzDist)
                .filter(pos -> !origin.equals(pos) && (filter == null || filter.test(world.getBlockState(pos))))
                .map(BlockPos::immutable)
                .toList();
    }

    @Nullable
    static BlockPos getNearestPoint(BlockPos origin, List<BlockPos> blocks) {
        return blocks.stream().min(Comparator.comparing(origin::distSqr)).orElse(null);
    }
}
