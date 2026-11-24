package net.conczin.mca.server.world.data;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloorCeilingResolverTest {
    private static final BlockPos FEET = new BlockPos(0, 64, 0);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void roofAt128IsIncludedButRoofAt129IsNot() {
        CountingWorld included = new CountingWorld();
        included.put(192, Blocks.STONE.defaultBlockState());
        assertEquals(OptionalInt.of(192), new FloorCeilingResolver(included).ceilingY(FEET));

        CountingWorld excluded = new CountingWorld();
        excluded.put(193, Blocks.STONE.defaultBlockState());
        assertEquals(OptionalInt.empty(), new FloorCeilingResolver(excluded).ceilingY(FEET));
        assertEquals(128, excluded.reads);
        assertEquals(192, excluded.highestRead);
    }

    @Test
    void openSkyDoesNotReadBeyond128Blocks() {
        CountingWorld world = new CountingWorld();
        assertEquals(OptionalInt.empty(), new FloorCeilingResolver(world).ceilingY(FEET));
        assertEquals(128, world.reads);
        assertEquals(192, world.highestRead);
    }

    @Test
    void nearestRoofWinsWithinTheBound() {
        CountingWorld world = new CountingWorld();
        world.put(104, Blocks.STONE.defaultBlockState());
        world.put(192, Blocks.STONE.defaultBlockState());
        assertEquals(OptionalInt.of(104), new FloorCeilingResolver(world).ceilingY(FEET));
        assertEquals(40, world.reads);
    }

    @Test
    void fluidAndWaterloggedShapesDoNotStopOrExtendTheCeilingRay() {
        CountingWorld world = new CountingWorld();
        world.put(70, Blocks.WATER.defaultBlockState());
        world.put(80, Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true));
        world.put(104, Blocks.STONE.defaultBlockState());
        assertEquals(OptionalInt.of(104), new FloorCeilingResolver(world).ceilingY(FEET));

        world.blocks.remove(104);
        world.put(193, Blocks.STONE.defaultBlockState());
        world.resetReads();
        assertEquals(OptionalInt.empty(), new FloorCeilingResolver(world).ceilingY(FEET));
        assertEquals(128, world.reads, "skipped fluids do not grant additional ray distance");
        assertEquals(192, world.highestRead);
    }

    @Test
    void rayStopsAtExclusiveWorldHeight() {
        CountingWorld world = new CountingWorld(0, 100);
        BlockPos feet = new BlockPos(0, 96, 0);
        world.put(99, Blocks.STONE.defaultBlockState());
        assertEquals(OptionalInt.of(99), new FloorCeilingResolver(world).ceilingY(feet));
        world.blocks.clear();
        world.resetReads();
        assertEquals(OptionalInt.empty(), new FloorCeilingResolver(world).ceilingY(feet));
        assertEquals(3, world.reads);
        assertEquals(99, world.highestRead);
        world.resetReads();
        assertEquals(OptionalInt.empty(), new FloorCeilingResolver(world).ceilingY(new BlockPos(0, 99, 0)));
        assertEquals(0, world.reads);
    }

    @Test
    void repeatedSuccessfulAndEmptyQueriesReuseTheSameResolverCache() {
        for (boolean roof : new boolean[]{true, false}) {
            CountingWorld world = new CountingWorld();
            if (roof) world.put(104, Blocks.STONE.defaultBlockState());
            FloorCeilingResolver resolver = new FloorCeilingResolver(world);
            OptionalInt expected = roof ? OptionalInt.of(104) : OptionalInt.empty();
            assertEquals(expected, resolver.ceilingY(FEET));
            int firstReads = world.reads;
            for (int i = 0; i < 10; i++) assertEquals(expected, resolver.ceilingY(FEET));
            assertEquals(firstReads, world.reads);
        }
    }

    @Test
    void newResolverObservesAnEditedRoof() {
        CountingWorld world = new CountingWorld();
        world.put(104, Blocks.STONE.defaultBlockState());
        assertEquals(OptionalInt.of(104), new FloorCeilingResolver(world).ceilingY(FEET));
        world.blocks.remove(104);
        world.put(108, Blocks.STONE.defaultBlockState());
        assertEquals(OptionalInt.of(108), new FloorCeilingResolver(world).ceilingY(FEET));
    }

    @Test
    void provenColumnTopAvoidsReadingTheEmptySky() {
        CountingWorld world = new CountingWorld();
        assertEquals(OptionalInt.empty(), new FloorCeilingResolver(new HeightBoundWorld(world, 64)).ceilingY(FEET));
        assertEquals(0, world.reads);
    }

    @Test
    void provenColumnTopDoesNotReplaceTheNearestPhysicalRoof() {
        CountingWorld world = new CountingWorld();
        world.put(104, Blocks.STONE.defaultBlockState());
        world.put(180, Blocks.STONE.defaultBlockState());
        assertEquals(OptionalInt.of(104), new FloorCeilingResolver(new HeightBoundWorld(world, 180)).ceilingY(FEET));
        assertEquals(40, world.reads);
    }

    @Test
    void provenColumnTopRetainsFluidExclusionAnd128BlockLimit() {
        CountingWorld world = new CountingWorld();
        world.put(70, Blocks.WATER.defaultBlockState());
        assertEquals(OptionalInt.empty(), new FloorCeilingResolver(new HeightBoundWorld(world, 70)).ceilingY(FEET));
        assertEquals(6, world.reads, "a highest fluid block is a bound, not roof evidence");
        world.put(193, Blocks.STONE.defaultBlockState());
        world.resetReads();
        assertEquals(OptionalInt.empty(), new FloorCeilingResolver(new HeightBoundWorld(world, 193)).ceilingY(FEET));
        assertEquals(128, world.reads);
        assertEquals(192, world.highestRead);
    }

    private record HeightBoundWorld(CountingWorld delegate, int highestBlockY)
            implements BlockGetter, FloorCeilingResolver.ColumnHeight {
        @Override
        public int highestBlockY(BlockPos column) { return highestBlockY; }

        @Override
        public BlockState getBlockState(BlockPos pos) { return delegate.getBlockState(pos); }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) { return delegate.getBlockEntity(pos); }

        @Override
        public FluidState getFluidState(BlockPos pos) { return delegate.getFluidState(pos); }

        @Override
        public int getHeight() { return delegate.getHeight(); }

        @Override
        public int getMinBuildHeight() { return delegate.getMinBuildHeight(); }
    }

    private static final class CountingWorld implements BlockGetter {
        private final int minimum;
        private final int height;
        private final Map<Integer, BlockState> blocks = new HashMap<>();
        private int reads;
        private int highestRead = Integer.MIN_VALUE;

        private CountingWorld() {
            this(-64, 384);
        }

        private CountingWorld(int minimum, int height) {
            this.minimum = minimum;
            this.height = height;
        }

        private void put(int y, BlockState state) {
            blocks.put(y, state);
        }

        private void resetReads() {
            reads = 0;
            highestRead = Integer.MIN_VALUE;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            assertTrue(pos.getY() >= minimum && pos.getY() < minimum + height,
                    "ceiling ray read outside the world's build height: " + pos);
            reads++;
            highestRead = Math.max(highestRead, pos.getY());
            return blocks.getOrDefault(pos.getY(), Blocks.AIR.defaultBlockState());
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }

        @Override
        public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }

        @Override
        public int getHeight() { return height; }

        @Override
        public int getMinBuildHeight() { return minimum; }
    }
}
