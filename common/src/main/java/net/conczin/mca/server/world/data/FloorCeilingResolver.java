package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/** Resolves and caches the nearest physical non-leaf ceiling above exact floor cells. */
final class FloorCeilingResolver {
    private final Level world;
    private final Map<BlockPos, OptionalInt> cache = new HashMap<>();

    FloorCeilingResolver(Level world) {
        this.world = world;
    }

    OptionalInt ceilingY(BlockPos feet) {
        return cache.computeIfAbsent(feet.immutable(), this::resolve);
    }

    private OptionalInt resolve(BlockPos feet) {
        for (int y = feet.getY() + 1; y < world.getMaxBuildHeight(); y++) {
            BlockPos probe = new BlockPos(feet.getX(), y, feet.getZ());
            BlockState state = world.getBlockState(probe);
            if (isRoofBlock(probe, state)) return OptionalInt.of(y);
        }
        return OptionalInt.empty();
    }

    private boolean isRoofBlock(BlockPos pos, BlockState state) {
        return state.getFluidState().isEmpty()
                && !state.is(BlockTags.LEAVES)
                && !state.getCollisionShape(world, pos).isEmpty();
    }
}
