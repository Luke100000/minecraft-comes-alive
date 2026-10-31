package net.conczin.mca.server.world.data;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectedFloorScannerLandingTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void twoBlockRightAngleStairTurnDoesNotBecomeAnotherStorey() {
        BlockGetter world = new TurningStairs();
        var lower = SelectedFloorScanner.scan(world, new BlockPos(1, 60, -4), 2048, 64);
        var upper = SelectedFloorScanner.scan(world, new BlockPos(7, 65, 0), 2048, 64);
        assertEquals(Building.validationResult.SUCCESS, lower.result());
        assertEquals(Building.validationResult.SUCCESS, upper.result());
        assertEquals(60, lower.anchorY());
        assertEquals(65, upper.anchorY());
        assertFalse(lower.floor().sameCellPositions(upper.floor()));
        for (BlockPos turn : new BlockPos[]{new BlockPos(1, 62, 0), new BlockPos(2, 62, 0)}) {
            var selected = SelectedFloorScanner.scan(world, turn, 2048, 64);
            assertEquals(Building.validationResult.SUCCESS, selected.result());
            assertEquals(60, selected.anchorY(), "the two-block turn invented an intermediate storey at " + turn);
            assertTrue(lower.floor().sameExactGeometry(selected.floor()),
                    "standing on either corner must select the same lower flight half");
        }
    }

    @Test
    void turnOwnershipDoesNotDependOnObservationQueryOrder() {
        BlockGetter world = new TurningStairs();
        BlockPos[] seeds = {
                new BlockPos(1, 60, -4), new BlockPos(1, 62, 0),
                new BlockPos(2, 62, 0), new BlockPos(7, 65, 0)
        };
        for (boolean lowerFirst : new boolean[]{true, false}) {
            var observation = new SelectedFloorScanner.Observation(world, 2048, 64);
            var selected = new SelectedFloorScanner.Result[seeds.length];
            for (int query = 0; query < seeds.length; query++) {
                int index = lowerFirst ? query : seeds.length - 1 - query;
                selected[index] = observation.selected(seeds[index]);
                assertEquals(Building.validationResult.SUCCESS, selected[index].result());
            }
            assertEquals(60, selected[0].anchorY());
            assertEquals(60, selected[1].anchorY());
            assertEquals(60, selected[2].anchorY());
            assertEquals(65, selected[3].anchorY());
            assertTrue(selected[0].floor().sameExactGeometry(selected[1].floor()),
                    "the first turn must retain the exact lower geometry in either query order");
            assertTrue(selected[0].floor().sameExactGeometry(selected[2].floor()),
                    "the second turn must retain the exact lower geometry in either query order");
            assertFalse(selected[0].floor().sameCellPositions(selected[3].floor()),
                    "the upper deck must retain its distinct floor in either query order");
        }
    }

    @Test
    void narrowSlabRoomBetweenFlightsKeepsItsOwnStorey() {
        SlabFlights world = new SlabFlights(2, 4);
        var lower = SelectedFloorScanner.scan(world, new BlockPos(0, 60, -9), 2048, 64);
        var middle = SelectedFloorScanner.scan(world, new BlockPos(0, 64, 1), 2048, 64);
        var upper = SelectedFloorScanner.scan(world, new BlockPos(0, 68, 12), 2048, 64);
        assertEquals(Building.validationResult.SUCCESS, lower.result());
        assertEquals(Building.validationResult.SUCCESS, middle.result());
        assertEquals(Building.validationResult.SUCCESS, upper.result());
        assertEquals(64, middle.anchorY(), "the usable 2x4 slab deck was swallowed by the stair flight");
        assertTrue(middle.floor().cellAt(new BlockPos(1, 64, 2)).isPresent());
        assertFalse(middle.floor().sameCellPositions(lower.floor()));
        assertFalse(middle.floor().sameCellPositions(upper.floor()));
        var otherEnd = SelectedFloorScanner.scan(world, new BlockPos(1, 64, 2), 2048, 64);
        assertTrue(otherEnd.floor().sameExactGeometry(middle.floor()));
    }

    @Test
    void truncatedLandingProofDoesNotPoisonOtherSeeds() {
        SlabFlights world = new SlabFlights(40, 2);
        var ceilings = new FloorCeilingResolver(world);
        var end = SelectedFloorScanner.inspectSurfaceCell(world, new BlockPos(0, 64, 0), ceilings).orElseThrow();
        var middle = SelectedFloorScanner.inspectSurfaceCell(world, new BlockPos(20, 64, 0), ceilings).orElseThrow();
        for (boolean endFirst : new boolean[]{true, false}) {
            var observation = new SelectedFloorScanner.Observation(world, 2048, 32);
            if (endFirst) observation.stableLanding(end);
            assertEquals(true, observation.throughLanding(middle),
                    "a seed-relative radius cutoff was cached as a physical landing boundary");
            assertEquals(false, observation.stableLanding(middle),
                    "a complete through-flight proof retained a stale stable-landing result");
            assertEquals(false, observation.stableLanding(end),
                    "a previous incomplete proof froze the end as another storey");
            assertEquals(true, observation.throughLanding(middle));
        }
    }

    /** Two full-block flights joined by two flat blocks and a ninety-degree turn. */
    private static final class TurningStairs implements BlockGetter {
        @Override
        public BlockState getBlockState(BlockPos pos) {
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            if (x < -1 || x > 9 || z < -6 || z > 2 || y < 59 || y > 75) {
                return Blocks.AIR.defaultBlockState();
            }
            int feet = x >= 0 && x <= 2 && z >= -5 && z <= -3 ? 60
                    : x == 1 && z == -2 ? 61
                    : x == 1 && z == -1 || (x == 1 || x == 2) && z == 0 ? 62
                    : x >= 3 && x <= 5 && z == 0 ? 60 + x
                    : x >= 6 && x <= 8 && z >= -1 && z <= 1 ? 65 : -1;
            if (feet < 0 || y < feet - 1 || y == 75) return Blocks.STONE.defaultBlockState();
            if (y >= feet) return Blocks.AIR.defaultBlockState();
            if (x == 1 && (z == -2 || z == -1) || x >= 3 && x <= 5 && z == 0) {
                return Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,
                        x == 1 ? Direction.SOUTH : Direction.EAST);
            }
            return Blocks.STONE.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }

        @Override
        public int getHeight() { return 128; }

        @Override
        public int getMinBuildHeight() { return 0; }
    }

    /** Two genuine end decks, half-slab flights and a configurable flat middle patch. */
    private record SlabFlights(int width, int middleLength) implements BlockGetter {
        @Override
        public BlockState getBlockState(BlockPos pos) {
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            if (x < -1 || x > width || z < -11 || z > middleLength + 10) return Blocks.AIR.defaultBlockState();
            if (y == 75 || y >= 59 && y < 75
                    && (x == -1 || x == width || z == -11 || z == middleLength + 10)) {
                return Blocks.STONE.defaultBlockState();
            }
            if (x < 0 || x >= width) return Blocks.AIR.defaultBlockState();
            if (z >= -10 && z <= -8 && y == 59
                    || z >= middleLength + 7 && z <= middleLength + 9 && y == 67) {
                return Blocks.STONE.defaultBlockState();
            }
            if (z >= 0 && z < middleLength && y == 63) {
                return Blocks.COBBLESTONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
            }
            int row = z < 0 ? z + 7 : z - middleLength;
            int baseY = z < 0 ? 60 : 64;
            if (row >= 0 && row < 7 && y == baseY + row / 2) {
                return Blocks.COBBLESTONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE,
                        row % 2 == 0 ? SlabType.BOTTOM : SlabType.TOP);
            }
            return Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }

        @Override
        public int getHeight() { return 128; }

        @Override
        public int getMinBuildHeight() { return 0; }
    }
}
