package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/** Resolves and caches the nearest physical non-leaf ceiling above exact floor cells. */
final class FloorCeilingResolver {
    private static final int MAX_SCAN_HEIGHT = 128;
    private final BlockGetter world;
    private final Map<BlockPos, OptionalInt> cache = new HashMap<>();

    FloorCeilingResolver(BlockGetter world) {
        this.world = world;
    }

    OptionalInt ceilingY(BlockPos feet) {
        return cache.computeIfAbsent(feet.immutable(), this::resolve);
    }

    /** Optional loaded-column proof that blocks above this height are air, never a roof result. */
    interface ColumnHeight {
        int highestBlockY(BlockPos column);
    }

    private OptionalInt resolve(BlockPos feet) {
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        int maximumY = Math.min(feet.getY() + MAX_SCAN_HEIGHT, world.getMaxBuildHeight() - 1);
        if (world instanceof ColumnHeight columns) maximumY = Math.min(maximumY, columns.highestBlockY(feet));
        for (int y = feet.getY() + 1; y <= maximumY; y++) {
            probe.set(feet.getX(), y, feet.getZ());
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
