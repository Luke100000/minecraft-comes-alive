package net.conczin.mca.server.world.data;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
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
    void selectionInAnUnknownColumnDoesNotReadItAsAir() {
        ChunkBlocks world = houseWithPorch(16, 5, 6);
        BlockPos unknown = new BlockPos(16, 64, 6);
        assertEquals(Building.validationResult.NOT_IN_BUILDING,
                SelectedFloorScanner.scan(world, unknown, 512, 16).result());
        assertNull(world.firstMissingBlock, "unknown seed was still examined as air during adjacent fallback");
        assertTrue(new SelectedFloorScanner.Observation(world, 512, 16).surfaceCell(unknown).isEmpty());
        assertNull(world.firstMissingBlock, "surface inspection read an unknown column");
    }

    @Test
    void loadedOnlyBlockViewUsesTheSameEnclosureProofAsLevelReader() {
        for (int minPorchZ : new int[]{4, 5}) {
            ChunkBlocks blocks = houseWithPorch(16, minPorchZ, minPorchZ == 4 ? 7 : 6);
            BlockPos source = new BlockPos(3, 64, 6);
            var realLevel = SelectedFloorScanner.scan(blocks.loadedView(), source, 512, 16);
            var loadedOnly = SelectedFloorScanner.scan(blocks, source, 512, 16);

            assertEquals(Building.validationResult.SUCCESS, loadedOnly.result());
            assertTrue(realLevel.floor().sameExactGeometry(loadedOnly.floor()));
            assertNull(blocks.firstMissingBlock, "loaded-only view read unknown blocks as air");
        }
    }

    @Test
    void smallHouseBesideUnevenCoveredPorchDoesNotReadUnloadedNeighbor() {
        ChunkBlocks world = houseWithPorch(16, 4, 7);
        BlockPos inside = new BlockPos(3, 64, 6);

        var scan = SelectedFloorScanner.scan(world.loadedView(), inside, 512, 16);

        assertEquals(Building.validationResult.SUCCESS, scan.result());
        assertTrue(scan.floor().cellAt(inside).isPresent());
        assertFalse(scan.floor().cellAt(new BlockPos(6, 64, 6)).isPresent(), "exterior porch became indoor floor");
        assertNull(world.firstMissingBlock, "a stable porch triggered downhill exploration into an unloaded chunk");
    }

    @Test
    void narrowSteppedPorchDoesNotReadUnloadedNeighbor() {
        ChunkBlocks world = houseWithPorch(16, 5, 6);
        BlockPos inside = new BlockPos(3, 64, 6);

        var scan = SelectedFloorScanner.scan(world.loadedView(), inside, 512, 16);

        assertEquals(Building.validationResult.SUCCESS, scan.result());
        assertTrue(scan.floor().cellAt(inside).isPresent());
        assertFalse(scan.floor().cellAt(new BlockPos(6, 64, 6)).isPresent(), "exterior porch became indoor floor");
        assertNull(world.firstMissingBlock, "downhill proof followed the narrow porch into an unloaded chunk");
    }

    @Test
    void unknownExteriorChunkIsNotAProvenFiniteEdge() {
        ChunkBlocks unknown = new ChunkBlocks(16);
        for (BlockPos pos : BlockPos.betweenClosed(10, 63, 4, 15, 67, 9)) {
            if (pos.getY() == 63 || pos.getY() == 67 || pos.getX() == 10 || pos.getX() == 15
                    || pos.getZ() == 4 || pos.getZ() == 9) {
                unknown.put(pos, Blocks.STONE.defaultBlockState());
            }
        }
        unknown.put(new BlockPos(15, 64, 6), Blocks.AIR.defaultBlockState());
        unknown.put(new BlockPos(15, 65, 6), Blocks.AIR.defaultBlockState());
        BlockPos source = new BlockPos(13, 64, 6);
        assertEquals(Building.validationResult.NOT_IN_BUILDING,
                SelectedFloorScanner.scan(unknown.loadedView(), source, 512, 16).result());
        assertEquals(Building.validationResult.NOT_IN_BUILDING,
                SelectedFloorScanner.scan(unknown, source, 512, 16).result());
        assertNull(unknown.firstMissingBlock, "finite proof loaded an unknown exterior chunk");

        ChunkBlocks loaded = new ChunkBlocks(32);
        loaded.blocks.putAll(unknown.blocks);
        loaded.put(new BlockPos(16, 63, 6), Blocks.STONE.defaultBlockState());
        assertEquals(Building.validationResult.SUCCESS,
                SelectedFloorScanner.scan(loaded.loadedView(), source, 512, 16).result());
        assertNull(loaded.firstMissingBlock, "loaded finite balcony needed a missing chunk");
    }

    @Test
    void extendingNarrowPorchDoesNotExpandHouseDiscoveryBeyondItsRadius() {
        BlockPos inside = new BlockPos(3, 64, 6);
        ChunkBlocks nearby = houseWithPorch(16, 5, 6);
        ChunkBlocks extended = houseWithPorch(256, 5, 6);

        var local = SelectedFloorScanner.scan(nearby.loadedView(), inside, 512, 16);
        var distant = SelectedFloorScanner.scan(extended.loadedView(), inside, 512, 16);

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

    private static final class ChunkBlocks implements SelectedFloorScanner.LoadedColumns {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private final int loadedWidth;
        private BlockPos firstMissingBlock;
        private int maxReadX = Integer.MIN_VALUE;

        private ChunkBlocks(int loadedWidth) { this.loadedWidth = loadedWidth; }

        private void put(BlockPos pos, BlockState state) { blocks.put(pos.immutable(), state); }

        @Override
        public boolean hasColumn(BlockPos pos) {
            return pos.getX() >= 0 && pos.getX() < loadedWidth && pos.getZ() >= 0 && pos.getZ() < 16;
        }

        // Expose the real LevelReader availability contract as well as block reads.
        // Missing columns must reject the finite proof without being treated as air.
        private LevelReader loadedView() {
            return (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(),
                    new Class<?>[]{LevelReader.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "hasChunk" -> (int) args[0] >= 0
                                && ((int) args[0] << 4) < loadedWidth && (int) args[1] == 0;
                        case "getBlockState" -> getBlockState((BlockPos) args[0]);
                        case "getFluidState" -> getFluidState((BlockPos) args[0]);
                        case "getBlockEntity" -> null;
                        case "getHeight" -> 128;
                        case "getMinBuildHeight" -> 0;
                        default -> {
                            if (method.isDefault()) yield InvocationHandler.invokeDefault(proxy, method, args);
                            throw new UnsupportedOperationException(method.toString());
                        }
                    });
        }

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
        public int getMinBuildHeight() { return 0; }
    }
}
