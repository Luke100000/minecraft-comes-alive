package net.conczin.mca.server.world.data;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
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

class SelectedFloorScannerRoofGapTest {
    private static final BlockPos SOURCE = new BlockPos(3, 64, 3);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void selectedCellUnderSingleSkylightRetainsTheWholeRoom() {
        Room world = room();
        world.blocks.remove(SOURCE.atY(68));
        assertWholeRoom(world);
    }

    @Test
    void twoCellSkylightRetainsBothCellsAndTheWholeRoom() {
        Room world = room();
        world.blocks.remove(SOURCE.atY(68));
        world.blocks.remove(SOURCE.east().atY(68));
        assertWholeRoom(world);
    }

    @Test
    void selectionUnderWideMissingRoofStillRequiresCover() {
        Room world = room();
        for (BlockPos pos : BlockPos.betweenClosed(2, 68, 2, 4, 68, 4)) world.blocks.remove(pos);
        assertEquals(Building.validationResult.NOT_IN_BUILDING,
                SelectedFloorScanner.scan(world, SOURCE, 512, 16).result());
    }

    @Test
    void inferredCeilingsDoNotChangePhysicalRaysAndReuseTheCompletedProof() {
        Room world = room();
        world.blocks.remove(SOURCE.atY(68));
        world.blocks.remove(SOURCE.east().atY(68));
        FloorCeilingResolver resolver = new FloorCeilingResolver(world);

        assertEquals(OptionalInt.empty(), resolver.ceilingY(SOURCE));
        assertEquals(OptionalInt.of(68), resolver.selectedCeilingY(SOURCE));
        assertEquals(OptionalInt.empty(), resolver.ceilingY(SOURCE));
        assertEquals(OptionalInt.of(68), resolver.knownCeilingY(SOURCE.east()));
        int reads = world.reads;
        assertEquals(OptionalInt.of(68), resolver.knownCeilingY(SOURCE.east()));
        assertEquals(OptionalInt.of(68), resolver.tolerantCeilingY(SOURCE, 68));
        assertEquals(reads, world.reads, "a completed gap proof must not repeat its block reads");
    }

    @Test
    void repairDoesNotHideAnExistingPhysicalCeilingInTheOtherGapColumn() {
        Room world = room();
        world.blocks.remove(SOURCE.atY(68));
        world.blocks.remove(SOURCE.east().atY(68));
        world.blocks.put(SOURCE.east().atY(66), Blocks.STONE.defaultBlockState());
        FloorCeilingResolver resolver = new FloorCeilingResolver(world);
        assertEquals(OptionalInt.of(68), resolver.tolerantCeilingY(SOURCE, 68));
        assertEquals(OptionalInt.of(66), resolver.knownCeilingY(SOURCE.east()));
    }

    @Test
    void hintedGapCannotChainIntoThreeMissingRoofCells() {
        Room world = room();
        for (int x = 2; x <= 4; x++) world.blocks.remove(new BlockPos(x, 68, 3));
        FloorCeilingResolver resolver = new FloorCeilingResolver(world);
        assertEquals(OptionalInt.empty(), resolver.tolerantCeilingY(SOURCE, 68));
        assertEquals(OptionalInt.empty(), resolver.tolerantCeilingY(SOURCE.east(), 68));
        assertEquals(OptionalInt.empty(), resolver.knownCeilingY(SOURCE));
    }

    @Test
    void leafFluidAndWaterloggedBordersDoNotProveRoofCover() {
        for (BlockState border : new BlockState[]{Blocks.OAK_LEAVES.defaultBlockState(),
                Blocks.WATER.defaultBlockState(), Blocks.OAK_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true)}) {
            Room world = room();
            world.blocks.remove(SOURCE.atY(68));
            world.blocks.put(SOURCE.west().atY(68), border);
            assertEquals(OptionalInt.empty(), new FloorCeilingResolver(world).tolerantCeilingY(SOURCE, 68));
        }
    }

    @Test
    void tinyGapProofDoesNotReadAnUnloadedBorderColumn() {
        Room world = room();
        world.blocks.remove(SOURCE.atY(68));
        world.missingColumn = SOURCE.west();
        assertEquals(OptionalInt.empty(), new FloorCeilingResolver(world).tolerantCeilingY(SOURCE, 68));
    }

    @Test
    void hintedRoofKeepsThe128BlockAndWorldHeightBounds() {
        for (int roofY : new int[]{192, 193, 320}) {
            Room world = new Room();
            for (BlockPos pos : BlockPos.betweenClosed(2, roofY, 2, 4, roofY, 4)) {
                if (!pos.equals(SOURCE.atY(roofY))) world.blocks.put(pos.immutable(), Blocks.STONE.defaultBlockState());
            }
            assertEquals(roofY == 192 ? OptionalInt.of(192) : OptionalInt.empty(),
                    new FloorCeilingResolver(world).tolerantCeilingY(SOURCE, roofY));
        }
    }

    @Test
    void heightHintInOpenSkyDoesNotSynthesizeARoof() {
        Room world = new Room();
        assertEquals(OptionalInt.empty(), new FloorCeilingResolver(world).tolerantCeilingY(SOURCE, 68));
        assertTrue(world.reads <= 132, "open sky expanded beyond one vertical ray and a tiny plane proof");
    }

    private static void assertWholeRoom(Room world) {
        for (BlockPos source : new BlockPos[]{SOURCE, new BlockPos(1, 64, 1)}) {
            var result = SelectedFloorScanner.scan(world, source, 512, 16);
            assertEquals(Building.validationResult.SUCCESS, result.result());
            assertEquals(25, result.floor().cells().size(), "roof holes removed supported indoor floor");
            assertTrue(result.floor().cellAt(SOURCE).isPresent());
            assertTrue(result.floor().cells().stream().allMatch(cell -> cell.ceilingY() == 68));
        }
    }

    private static Room room() {
        Room world = new Room();
        for (BlockPos pos : BlockPos.betweenClosed(0, 63, 0, 6, 68, 6)) {
            if (pos.getY() == 63 || pos.getY() == 68 || pos.getX() == 0 || pos.getX() == 6
                    || pos.getZ() == 0 || pos.getZ() == 6) {
                world.blocks.put(pos.immutable(), Blocks.STONE.defaultBlockState());
            }
        }
        return world;
    }

    private static final class Room implements SelectedFloorScanner.LoadedColumns {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private BlockPos missingColumn;
        private int reads;

        @Override
        public boolean hasColumn(BlockPos pos) {
            return missingColumn == null || pos.getX() != missingColumn.getX() || pos.getZ() != missingColumn.getZ();
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            assertTrue(hasColumn(pos), "tiny roof proof read an unloaded column: " + pos);
            assertTrue(!isOutsideBuildHeight(pos), "tiny roof proof read outside world height: " + pos);
            reads++;
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }

        @Override
        public int getHeight() { return 384; }

        @Override
        public int getMinBuildHeight() { return -64; }
    }
}
