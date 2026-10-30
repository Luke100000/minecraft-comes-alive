package net.conczin.mca.server.world.data;

import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/** Counts discovery reads without changing production code or its dependency limit. */
public final class ShelterLookupProbeGameTests {
    private ShelterLookupProbeGameTests() {
    }

    /** Use real datapack-loaded leaf tags, which the standalone unit bootstrap does not populate. */
    public static void verifyLeavesRespectCeilingBound(GameTestHelper helper) {
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
        helper.assertTrue(leaves.is(BlockTags.LEAVES), "runtime leaf tag was not loaded");
        Map<Integer, BlockState> states = new HashMap<>();
        states.put(100, leaves);
        states.put(192, Blocks.STONE.defaultBlockState());
        BlockGetter world = new BlockGetter() {
            @Override
            public BlockState getBlockState(BlockPos pos) {
                helper.assertTrue(pos.getY() >= 65 && pos.getY() <= 192,
                        "ceiling ray exceeded its 128-block budget: " + pos);
                return states.getOrDefault(pos.getY(), Blocks.AIR.defaultBlockState());
            }

            @Override
            public BlockEntity getBlockEntity(BlockPos pos) { return null; }

            @Override
            public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }

            @Override
            public int getHeight() { return 384; }

            @Override
            public int getMinBuildHeight() { return -64; }
        };
        BlockPos feet = new BlockPos(0, 64, 0);
        helper.assertTrue(new FloorCeilingResolver(world).ceilingY(feet).equals(OptionalInt.of(192)),
                "leaves became roof evidence or hid the roof at +128");
        states.remove(192);
        states.put(193, Blocks.STONE.defaultBlockState());
        helper.assertTrue(new FloorCeilingResolver(world).ceilingY(feet).isEmpty(),
                "skipping leaves extended the ceiling ray beyond +128");
        helper.succeed();
    }

    public static void log(IndoorRoomCache cache, BlockPos bed, String label) {
        try {
            Class<?> type = Class.forName(IndoorRoomCache.class.getName() + "$LoadedBlocks");
            Constructor<?> constructor = type.getDeclaredConstructor(IndoorRoomCache.class, boolean.class);
            constructor.setAccessible(true);
            var delegate = (SelectedFloorScanner.LoadedColumns) constructor.newInstance(cache, true);
            CountingBlocks blocks = new CountingBlocks(delegate);
            var scan = SelectedFloorScanner.scan(blocks, bed, 512, 16);
            if (scan.result() == Building.validationResult.SUCCESS) BuildingRoomScanner.components(blocks, scan);
            long air = blocks.reads.values().stream().filter(BlockState::isAir).count();
            long highAir = blocks.reads.entrySet().stream()
                    .filter(entry -> entry.getKey().getY() > bed.getY() + 16 && entry.getValue().isAir()).count();
            int highest = blocks.reads.keySet().stream().mapToInt(BlockPos::getY).max().orElse(bed.getY());
            MCA.LOGGER.info("[MCA Shelter Lookup] {} discoveryResult={} uniqueBlockReads={} airReads={} airReadsOver16AboveBed={} highestReadY={} bedY={}",
                    label, scan.result(), blocks.reads.size(), air, highAir, highest, bed.getY());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Room discovery diagnostic unavailable", e);
        }
    }

    private static final class CountingBlocks implements SelectedFloorScanner.LoadedColumns, FloorCeilingResolver.ColumnHeight {
        private final SelectedFloorScanner.LoadedColumns delegate;
        private final Map<BlockPos, BlockState> reads = new HashMap<>();

        private CountingBlocks(SelectedFloorScanner.LoadedColumns delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasColumn(BlockPos pos) { return delegate.hasColumn(pos); }

        @Override
        public int highestBlockY(BlockPos pos) {
            return ((FloorCeilingResolver.ColumnHeight) delegate).highestBlockY(pos);
        }

        @Override
        public void observeFrameState(BlockPos pos, Direction face) { delegate.observeFrameState(pos, face); }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState state = delegate.getBlockState(pos);
            if (!delegate.isOutsideBuildHeight(pos)) reads.putIfAbsent(pos.immutable(), state);
            return state;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) { return delegate.getBlockEntity(pos); }

        @Override
        public int getHeight() { return delegate.getHeight(); }

        @Override
        public int getMinBuildHeight() { return delegate.getMinBuildHeight(); }
    }
}
