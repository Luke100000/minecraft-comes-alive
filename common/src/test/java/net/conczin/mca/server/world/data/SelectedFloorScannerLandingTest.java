package net.conczin.mca.server.world.data;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
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
    void truncatedLandingProofDoesNotPoisonOtherSeeds() throws ReflectiveOperationException {
        SlabFlights world = new SlabFlights(40, 2);
        var ceilings = new FloorCeilingResolver(world);
        var end = SelectedFloorScanner.inspectSurfaceCell(world, new BlockPos(0, 64, 0), ceilings).orElseThrow();
        var middle = SelectedFloorScanner.inspectSurfaceCell(world, new BlockPos(20, 64, 0), ceilings).orElseThrow();
        Class<?> providerType = Class.forName(SelectedFloorScanner.class.getName() + "$StepProvider");
        var through = providerType.getDeclaredMethod("throughLanding", SelectedFloorScanner.SurfaceCell.class);
        through.setAccessible(true);
        var stable = providerType.getDeclaredMethod("stableLanding", SelectedFloorScanner.SurfaceCell.class);
        stable.setAccessible(true);
        var providerField = SelectedFloorScanner.Observation.class.getDeclaredField("provider");
        providerField.setAccessible(true);
        for (boolean endFirst : new boolean[]{true, false}) {
            Object provider = providerField.get(new SelectedFloorScanner.Observation(world, 2048, 32));
            if (endFirst) stable.invoke(provider, end);
            assertEquals(true, through.invoke(provider, middle),
                    "a seed-relative radius cutoff was cached as a physical landing boundary");
            assertEquals(false, stable.invoke(provider, middle),
                    "a complete through-flight proof retained a stale stable-landing result");
            assertEquals(false, stable.invoke(provider, end),
                    "a previous incomplete proof froze the end as another storey");
            assertEquals(true, through.invoke(provider, middle));
        }
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
