package net.conczin.mca.server.world.data;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectedFloorScannerBoundaryTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void smallHouseBesideUnevenCoveredPorchDoesNotReadUnloadedNeighbor() {
        ChunkBlocks world = houseWithPorch(16, 4, 7);
        BlockPos inside = new BlockPos(3, 64, 6);

        var scan = SelectedFloorScanner.scan(world, inside, 512, 16);

        assertEquals(Building.validationResult.SUCCESS, scan.result());
        assertTrue(scan.floor().cellAt(inside).isPresent());
        assertFalse(scan.floor().cellAt(new BlockPos(6, 64, 6)).isPresent(), "exterior porch became indoor floor");
        assertNull(world.firstMissingBlock, "a stable porch triggered downhill exploration into an unloaded chunk");
    }

    @Test
    void narrowSteppedPorchDoesNotReadUnloadedNeighbor() {
        ChunkBlocks world = houseWithPorch(16, 5, 6);
        BlockPos inside = new BlockPos(3, 64, 6);

        var scan = SelectedFloorScanner.scan(world, inside, 512, 16);

        assertEquals(Building.validationResult.SUCCESS, scan.result());
        assertTrue(scan.floor().cellAt(inside).isPresent());
        assertFalse(scan.floor().cellAt(new BlockPos(6, 64, 6)).isPresent(), "exterior porch became indoor floor");
        assertNull(world.firstMissingBlock, "downhill proof followed the narrow porch into an unloaded chunk");
    }

    @Test
    void extendingNarrowPorchDoesNotExpandHouseDiscoveryBeyondItsRadius() {
        BlockPos inside = new BlockPos(3, 64, 6);
        ChunkBlocks nearby = houseWithPorch(16, 5, 6);
        ChunkBlocks extended = houseWithPorch(256, 5, 6);

        var local = SelectedFloorScanner.scan(nearby, inside, 512, 16);
        var distant = SelectedFloorScanner.scan(extended, inside, 512, 16);

        assertEquals(Building.validationResult.SUCCESS, local.result());
        assertEquals(Building.validationResult.SUCCESS, distant.result());
        assertTrue(local.floor().sameExactGeometry(distant.floor()), "distant exterior ground changed the indoor floor");
        assertTrue(extended.maxReadX <= inside.getX() + 16,
                "radius-16 discovery read distant porch blocks at x=" + extended.maxReadX);
    }

    private static ChunkBlocks houseWithPorch(int length, int minPorchZ, int maxPorchZ) {
        ChunkBlocks world = new ChunkBlocks(length);
        for (BlockPos pos : BlockPos.betweenClosed(0, 63, 0, length - 1, 63, 15)) {
            world.put(pos, Blocks.STONE.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(0, 64, 4, 5, 67, 9)) {
            if (pos.getY() == 67 || pos.getX() == 0 || pos.getX() == 5 || pos.getZ() == 4 || pos.getZ() == 9) {
                world.put(pos, Blocks.STONE.defaultBlockState());
            }
        }
        var door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        world.put(new BlockPos(5, 64, 6), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        world.put(new BlockPos(5, 65, 6), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        for (int x = 6; x < length; x++) {
            for (int z = minPorchZ; z <= maxPorchZ; z++) world.put(new BlockPos(x, 67, z), Blocks.STONE.defaultBlockState());
            world.put(new BlockPos(x, 63, minPorchZ), Blocks.AIR.defaultBlockState());
            world.put(new BlockPos(x, 62, minPorchZ), Blocks.STONE.defaultBlockState());
        }
        return world;
    }

    private static final class ChunkBlocks implements BlockGetter {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private final int loadedWidth;
        private BlockPos firstMissingBlock;
        private int maxReadX = Integer.MIN_VALUE;

        private ChunkBlocks(int loadedWidth) { this.loadedWidth = loadedWidth; }

        private void put(BlockPos pos, BlockState state) { blocks.put(pos.immutable(), state); }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            maxReadX = Math.max(maxReadX, pos.getX());
            if (firstMissingBlock == null && (pos.getX() < 0 || pos.getX() >= loadedWidth || pos.getZ() < 0 || pos.getZ() >= 16)) {
                firstMissingBlock = pos.immutable();
            }
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }

        @Override
        public int getHeight() { return 128; }

        @Override
        public int getMinY() { return 0; }
    }
}
