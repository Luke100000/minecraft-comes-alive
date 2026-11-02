package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/** Resolves and caches the nearest physical non-leaf ceiling above exact floor cells. */
final class FloorCeilingResolver {
    private static final int MAX_SCAN_HEIGHT = 128;
    private final BlockGetter world;
    private final Map<BlockPos, OptionalInt> cache = new HashMap<>();
    private final Map<BlockPos, Integer> inferred = new HashMap<>();
    private final Map<BlockPos, List<BlockPos>> roofGaps = new HashMap<>();

    FloorCeilingResolver(BlockGetter world) {
        this.world = world;
    }

    OptionalInt ceilingY(BlockPos feet) {
        return cache.computeIfAbsent(feet.immutable(), this::resolve);
    }

    /** Previously proved tiny gaps participate in floor geometry without changing physical rays. */
    OptionalInt knownCeilingY(BlockPos feet) {
        OptionalInt physical = ceilingY(feet);
        if (physical.isPresent()) return physical;
        Integer roof = inferred.get(feet);
        return roof == null ? OptionalInt.empty() : OptionalInt.of(roof);
    }

    /** Only the selected column may start extra rays to establish a missing ceiling hint. */
    OptionalInt selectedCeilingY(BlockPos feet) {
        if (!hasColumn(feet)) return OptionalInt.empty();
        OptionalInt known = knownCeilingY(feet);
        if (known.isPresent()) return known;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = feet.relative(direction);
            if (!hasColumn(adjacent)) continue;
            OptionalInt physical = ceilingY(adjacent);
            if (physical.isPresent()) {
                OptionalInt repaired = tolerantCeilingY(feet, physical.getAsInt());
                if (repaired.isPresent()) return repaired;
            }
        }
        return OptionalInt.empty();
    }

    /** A height hint bounds the proof to one roof plane, never another set of sky rays. */
    OptionalInt tolerantCeilingY(BlockPos feet, int knownRoofY) {
        if (!hasColumn(feet)) return OptionalInt.empty();
        OptionalInt known = knownCeilingY(feet);
        if (known.isPresent()) return known;
        if (knownRoofY <= feet.getY() + 1 || knownRoofY > feet.getY() + MAX_SCAN_HEIGHT
                || world.isOutsideBuildHeight(knownRoofY)) return OptionalInt.empty();
        List<BlockPos> gaps = roofGaps.computeIfAbsent(feet.atY(knownRoofY), this::resolveSmallRoofGap);
        if (gaps.isEmpty()) return OptionalInt.empty();
        for (BlockPos gap : gaps) inferred.put(gap.atY(feet.getY()), knownRoofY);
        return OptionalInt.of(knownRoofY);
    }

    private List<BlockPos> resolveSmallRoofGap(BlockPos roof) {
        List<BlockPos> gaps = new ArrayList<>();
        gaps.add(roof);
        for (int i = 0; i < gaps.size(); i++) {
            BlockPos gap = gaps.get(i);
            if (!hasColumn(gap) || !world.getBlockState(gap).isAir()) return List.of();
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos adjacent = gap.relative(direction);
                if (!hasColumn(adjacent)) return List.of();
                BlockState state = world.getBlockState(adjacent);
                // Only physical borders count; inferred gaps cannot chain across a missing roof.
                if (!(state.getBlock() instanceof LeavesBlock) && isRoofBlock(adjacent, state)) continue;
                if (!state.isAir()) return List.of();
                if (!gaps.contains(adjacent)) {
                    gaps.add(adjacent);
                    if (gaps.size() > 2) return List.of();
                }
            }
        }
        return List.copyOf(gaps);
    }

    @SuppressWarnings("deprecation")
    private boolean hasColumn(BlockPos pos) {
        if (world instanceof SelectedFloorScanner.LoadedColumns loaded) return loaded.hasColumn(pos);
        return !(world instanceof LevelReader reader) || reader.hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
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
