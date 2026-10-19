package net.conczin.mca.server.world.data;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SelectedFloorScannerDoorwayTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void framedEntrancesKeepExactInteriorGeometry() {
        // Without frame cuts, broad outdoor terrain rejects the otherwise roofed room.
        for (boolean rotated : new boolean[]{false, true}) {
            for (int width : new int[]{1, 2}) {
                HouseBlocks world = house(width, rotated);
                Set<BlockPos> expected = new HashSet<>();
                for (int x = 1; x <= 4; x++) {
                    for (int z = 1; z <= 4; z++) expected.add(world.pos(x, 64, z));
                }
                for (int z = 2; z < 2 + width; z++) expected.add(world.pos(5, 64, z));
                for (BlockPos seed : new BlockPos[]{world.pos(2, 64, 2),
                        world.pos(5, 64, 2), world.pos(5, 64, 1 + width)}) {
                    var scan = SelectedFloorScanner.scan(world, seed, 512, 16);
                    assertEquals(Building.validationResult.SUCCESS, scan.result(),
                            "framed width=" + width + " rotated=" + rotated + " seed=" + seed);
                    assertEquals(expected, scan.floor().cells().stream()
                            .map(FloorGeometry.Cell::feet).collect(Collectors.toSet()));
                    assertTrue(scan.floor().connectorMarkers().isEmpty(), "empty arch invented a connector");
                    assertEquals(1, BuildingRoomScanner.components(world, scan).size(),
                            "empty arch partitioned the room");
                }
            }
        }
    }

    @Test
    void incompleteFramesDoNotSealExterior() {
        // Only complete collision faces can cut the unbounded terrain from this room.
        for (boolean rotated : new boolean[]{false, true}) {
            assertRejected(house(3, rotated), "three-wide breach");
            HouseBlocks noLintel = house(2, rotated);
            noLintel.put(5, 66, 2, Blocks.AIR.defaultBlockState());
            assertRejected(noLintel, "missing lintel");
            HouseBlocks noJamb = house(2, rotated);
            noJamb.put(5, 64, 1, Blocks.AIR.defaultBlockState());
            noJamb.put(5, 65, 1, Blocks.AIR.defaultBlockState());
            assertRejected(noJamb, "missing jamb");
            for (BlockState decoration : new BlockState[]{Blocks.OAK_FENCE.defaultBlockState(),
                    Blocks.OAK_LEAVES.defaultBlockState(), Blocks.STONE_SLAB.defaultBlockState(),
                    Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,
                            rotated ? Direction.WEST : Direction.NORTH)}) {
                HouseBlocks partial = house(2, rotated);
                partial.put(5, 64, 1, decoration);
                partial.put(5, 65, 1, decoration);
                assertRejected(partial, "incomplete jamb: " + decoration);
            }
            HouseBlocks topSlab = house(2, rotated);
            topSlab.put(5, 66, 2, Blocks.STONE_SLAB.defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.TOP));
            assertRejected(topSlab, "lintel has no bottom collision face");
            HouseBlocks roofless = house(2, rotated);
            for (int x = 0; x <= 4; x++) {
                for (int z = 0; z <= 5; z++) roofless.put(x, 67, z, Blocks.AIR.defaultBlockState());
            }
            assertRejected(roofless, "roofless frame");
        }
    }

    @Test
    void unknownFrameEvidenceIsNeverReadAsAir() {
        for (boolean rotated : new boolean[]{false, true}) {
            for (int[] column : new int[][]{{5, 1}, {4, 2}, {6, 2}, {5, 3}}) {
                HouseBlocks world = house(2, rotated);
                world.unknown.add(world.pos(column[0], 0, column[1]));
                assertRejected(world, "unknown required frame/adjacent column");
                assertNull(world.firstUnknownRead, "frame discovery read an unavailable column");
            }
        }
    }

    @Test
    void bothHalvesOfADoubleThresholdNeedSupport() {
        HouseBlocks world = house(2, false);
        world.put(5, 63, 3, Blocks.AIR.defaultBlockState());
        assertRejected(world, "unsupported second threshold must not seal broad exterior");
    }

    @Test
    void fullStairJambFacesAndBottomSlabLintelsRemainValidFrames() {
        for (boolean rotated : new boolean[]{false, true}) {
            HouseBlocks world = house(2, rotated);
            var jamb = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,
                    rotated ? Direction.EAST : Direction.SOUTH);
            world.put(5, 64, 1, jamb);
            world.put(5, 65, 1, jamb);
            world.put(5, 66, 2, Blocks.STONE_SLAB.defaultBlockState());
            world.put(5, 66, 3, Blocks.STONE_SLAB.defaultBlockState());
            var scan = SelectedFloorScanner.scan(world, world.pos(2, 64, 2), 512, 16);
            assertEquals(Building.validationResult.SUCCESS, scan.result(), "full collision faces were rejected by block type");
            assertTrue(scan.floor().cellAt(world.pos(5, 64, 2)).isPresent());
            assertTrue(scan.floor().cellAt(world.pos(6, 64, 2)).isEmpty());
        }
    }

    @Test
    void framedThresholdWithoutAnAcceptedSideIsNotARoom() {
        HouseBlocks world = house(1, false);
        for (BlockPos pos : BlockPos.betweenClosed(0, 64, 0, 4, 67, 5)) {
            world.put(pos.getX(), pos.getY(), pos.getZ(), Blocks.AIR.defaultBlockState());
        }
        assertEquals(Building.validationResult.NOT_IN_BUILDING,
                SelectedFloorScanner.scan(world, world.pos(5, 64, 2), 512, 16).result());
    }

    @Test
    void exteriorFrameBesideARealDoorDoesNotInventATinyRoom() {
        for (boolean rotated : new boolean[]{false, true}) {
            for (int width : new int[]{1, 2}) {
                HouseBlocks world = house(width, rotated);
                var door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,
                        rotated ? Direction.NORTH : Direction.WEST);
                for (int z = 2; z < 2 + width; z++) {
                    world.put(5, 64, z, door);
                    world.put(5, 65, z, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                    world.put(6, 66, z, Blocks.STONE.defaultBlockState());
                }
                for (int y = 64; y <= 66; y++) {
                    world.put(6, y, 1, Blocks.STONE.defaultBlockState());
                    world.put(6, y, 2 + width, Blocks.STONE.defaultBlockState());
                }
                var scan = SelectedFloorScanner.scan(world, world.pos(2, 64, 2), 512, 16);
                assertEquals(Building.validationResult.SUCCESS, scan.result());
                assertEquals(16 + width, scan.floor().cells().size(), "exterior-only threshold was retained");
                assertEquals(1, BuildingRoomScanner.components(world, scan).size());
            }
        }
    }

    @Test
    void aFramedPassageBetweenRealDoorsRetainsBothRooms() {
        HouseBlocks world = house(1, false);
        for (BlockPos pos : BlockPos.betweenClosed(7, 64, 0, 12, 67, 5)) {
            if (pos.getX() == 7 || pos.getX() == 12 || pos.getZ() == 0 || pos.getZ() == 5 || pos.getY() == 67) {
                world.put(pos.getX(), pos.getY(), pos.getZ(), Blocks.STONE.defaultBlockState());
            }
        }
        for (int y = 64; y <= 66; y++) {
            world.put(6, y, 1, Blocks.STONE.defaultBlockState());
            world.put(6, y, 3, Blocks.STONE.defaultBlockState());
        }
        world.put(6, 66, 2, Blocks.STONE.defaultBlockState());
        for (int x : new int[]{5, 7}) {
            var door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,
                    x == 5 ? Direction.WEST : Direction.EAST);
            world.put(x, 64, 2, door);
            world.put(x, 65, 2, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }
        var first = SelectedFloorScanner.scan(world, new BlockPos(2, 64, 2), 512, 16);
        var second = SelectedFloorScanner.scan(world, new BlockPos(9, 64, 2), 512, 16);
        assertEquals(Building.validationResult.SUCCESS, first.result());
        assertEquals(Building.validationResult.SUCCESS, second.result());
        assertEquals(35, first.floor().cells().size(), "enclosed passage disconnected the other Room");
        assertTrue(first.floor().sameExactGeometry(second.floor()));
        var components = BuildingRoomScanner.components(world, first);
        assertNotSame(RoomPartitioner.select(new BlockPos(2, 64, 2), first.floor(), components),
                RoomPartitioner.select(new BlockPos(9, 64, 2), first.floor(), components));
    }

    @Test
    void oneFramedExitDoesNotHideAnotherBreach() {
        HouseBlocks world = house(2, false);
        for (int z = 1; z <= 3; z++) {
            world.put(0, 64, z, Blocks.AIR.defaultBlockState());
            world.put(0, 65, z, Blocks.AIR.defaultBlockState());
        }
        assertRejected(world, "second unframed exit");
    }

    @Test
    void anEmptyArchConnectsTwoRoofedSidesWithoutPartitioningThem() {
        HouseBlocks world = house(2, false);
        for (BlockPos pos : BlockPos.betweenClosed(6, 64, 0, 10, 67, 5)) {
            if (pos.getX() == 10 || pos.getZ() == 0 || pos.getZ() == 5 || pos.getY() == 67) {
                world.put(pos.getX(), pos.getY(), pos.getZ(), Blocks.STONE.defaultBlockState());
            }
        }
        var left = SelectedFloorScanner.scan(world, new BlockPos(2, 64, 2), 512, 16);
        var right = SelectedFloorScanner.scan(world, new BlockPos(8, 64, 2), 512, 16);
        assertEquals(Building.validationResult.SUCCESS, left.result());
        assertEquals(Building.validationResult.SUCCESS, right.result());
        assertTrue(left.floor().sameExactGeometry(right.floor()));
        assertEquals(34, left.floor().cells().size());
        assertEquals(1, BuildingRoomScanner.components(world, left).size());
    }

    @Test
    void aNarrowRoofedPassageIsNotDiscardedAsOnlyDoorframes() {
        HouseBlocks world = new HouseBlocks(false);
        for (BlockPos pos : BlockPos.betweenClosed(0, 63, 0, 7, 66, 2)) {
            world.put(pos.getX(), pos.getY(), pos.getZ(), pos.getY() == 63 || pos.getY() == 66
                    || pos.getX() == 0 || pos.getX() == 7 || pos.getZ() == 0 || pos.getZ() == 2
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        var scan = SelectedFloorScanner.scan(world, new BlockPos(2, 64, 1), 512, 16);
        assertEquals(Building.validationResult.SUCCESS, scan.result());
        assertEquals(6, scan.floor().cells().size());
    }

    private static void assertRejected(HouseBlocks world, String reason) {
        var observation = new SelectedFloorScanner.Observation(world, 512, 16);
        var scan = observation.selected(world.pos(2, 64, 2));
        assertEquals(Building.validationResult.NOT_IN_BUILDING, scan.result(), () -> {
            var frames = new java.util.ArrayList<SelectedFloorScanner.FramedDoorway>();
            for (BlockPos cell : BlockPos.betweenClosed(0, 63, 0, 6, 68, 6)) {
                for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
                    observation.framedDoorway(cell, axis).ifPresent(frames::add);
                }
            }
            return reason + " frames=" + frames;
        });
    }

    private static HouseBlocks house(int width, boolean rotated) {
        HouseBlocks world = new HouseBlocks(rotated);
        for (BlockPos pos : BlockPos.betweenClosed(-20, 63, -20, 25, 63, 25)) {
            world.put(pos.getX(), pos.getY(), pos.getZ(), Blocks.STONE.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(0, 64, 0, 5, 67, 5)) {
            if (pos.getY() == 67 || pos.getX() == 0 || pos.getX() == 5
                    || pos.getZ() == 0 || pos.getZ() == 5) {
                world.put(pos.getX(), pos.getY(), pos.getZ(), Blocks.STONE.defaultBlockState());
            }
        }
        for (int z = 2; z < 2 + width; z++) {
            world.put(5, 64, z, Blocks.AIR.defaultBlockState());
            world.put(5, 65, z, Blocks.AIR.defaultBlockState());
        }
        return world;
    }

    private static final class HouseBlocks implements SelectedFloorScanner.LoadedColumns {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();
        private final Set<BlockPos> unknown = new HashSet<>();
        private final boolean rotated;
        private BlockPos firstUnknownRead;

        private HouseBlocks(boolean rotated) { this.rotated = rotated; }
        private BlockPos pos(int x, int y, int z) { return new BlockPos(rotated ? z : x, y, rotated ? x : z); }
        private void put(int x, int y, int z, BlockState state) { blocks.put(pos(x, y, z), state); }

        @Override
        public boolean hasColumn(BlockPos pos) { return !unknown.contains(new BlockPos(pos.getX(), 0, pos.getZ())); }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (!hasColumn(pos) && firstUnknownRead == null) firstUnknownRead = pos.immutable();
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
