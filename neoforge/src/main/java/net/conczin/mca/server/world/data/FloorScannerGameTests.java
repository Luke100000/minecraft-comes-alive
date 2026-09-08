package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.stream.Collectors;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class FloorScannerGameTests {
    private FloorScannerGameTests() {
    }

    @GameTest(batch = "mca_floor_bed_footprint", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void bedsDoNotChangeRoomFootprint(GameTestHelper helper) {
        BlockPos roomMin = helper.absolutePos(new BlockPos(4, 2, 4));
        buildClosedRoom(helper, roomMin, 5, 4);
        BlockPos seed = roomMin.offset(2, 0, 2);

        SelectedFloorScanner.Result before = SelectedFloorScanner.scan(helper.getLevel(), seed, 128, 16);
        helper.assertTrue(before.result() == Building.validationResult.SUCCESS,
                "baseline room scan failed: " + before.result());
        Set<BlockPos> expected = before.floor().projection().cells();

        BlockPos bedFoot = roomMin.offset(1, 0, 1);
        placeBed(helper, bedFoot, Direction.EAST);

        SelectedFloorScanner.Result after = SelectedFloorScanner.scan(helper.getLevel(), seed, 128, 16);
        helper.assertTrue(after.result() == Building.validationResult.SUCCESS,
                "room scan with bed failed: " + after.result());
        Set<BlockPos> actual = after.floor().projection().cells();
        helper.assertTrue(expected.equals(actual),
                "bed changed room footprint: before=" + expected.size() + " after=" + actual.size());
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_bed_seed", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void scanFromBedTopUsesUnderlyingRoomFloor(GameTestHelper helper) {
        BlockPos roomMin = helper.absolutePos(new BlockPos(4, 2, 4));
        buildClosedRoom(helper, roomMin, 5, 4);
        BlockPos normalSeed = roomMin.offset(2, 0, 2);

        SelectedFloorScanner.Result before = SelectedFloorScanner.scan(helper.getLevel(), normalSeed, 128, 16);
        helper.assertTrue(before.result() == Building.validationResult.SUCCESS,
                "baseline room scan failed: " + before.result());
        Set<BlockPos> expected = before.floor().projection().cells();

        BlockPos bedFoot = roomMin.offset(1, 0, 1);
        placeBed(helper, bedFoot, Direction.EAST);

        SelectedFloorScanner.Result fromBed = SelectedFloorScanner.scan(
                helper.getLevel(), bedFoot.above(), 128, 16);
        helper.assertTrue(fromBed.result() == Building.validationResult.SUCCESS,
                "scan from bed top failed: " + fromBed.result());
        Set<BlockPos> actual = fromBed.floor().projection().cells();
        helper.assertTrue(expected.equals(actual),
                "bed-top seed changed room footprint: before=" + expected.size() + " after=" + actual.size());
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_full_block", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void fullBlockIsNotOwnedInteriorCell(GameTestHelper helper) {
        BlockPos roomMin = helper.absolutePos(new BlockPos(4, 2, 4));
        buildClosedRoom(helper, roomMin, 5, 4);
        BlockPos blocked = roomMin.offset(1, 0, 1);
        helper.getLevel().setBlock(blocked, Blocks.STONE.defaultBlockState(), 3);

        SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(
                helper.getLevel(), roomMin.offset(3, 0, 2), 128, 16);

        helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
                "room scan failed: " + scan.result());
        helper.assertTrue(scan.floor().cellAt(blocked).isEmpty(),
                "full cube became an owned Room cell");
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_carpet_footprint", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void carpetDoesNotChangeIntegerRoomMembership(GameTestHelper helper) {
        BlockPos roomMin = helper.absolutePos(new BlockPos(4, 2, 4));
        buildClosedRoom(helper, roomMin, 5, 4);
        BlockPos seed = roomMin.offset(2, 0, 2);
        BlockPos carpetCell = roomMin.offset(1, 0, 1);
        var level = helper.getLevel();

        Set<BlockPos> before = SelectedFloorScanner.scan(level, seed, 128, 16).floor()
                .cells().stream().map(FloorGeometry.Cell::feet).collect(Collectors.toSet());
        level.setBlock(carpetCell, Blocks.RED_CARPET.defaultBlockState(), 3);
        Set<BlockPos> after = SelectedFloorScanner.scan(level, seed, 128, 16).floor()
                .cells().stream().map(FloorGeometry.Cell::feet).collect(Collectors.toSet());

        helper.assertTrue(before.equals(after), "carpet changed integer Room membership");
        helper.succeed();
    }

    private static void buildClosedRoom(GameTestHelper helper, BlockPos min, int width, int depth) {
        for (int x = -1; x <= width; x++) {
            for (int z = -1; z <= depth; z++) {
                BlockPos column = min.offset(x, 0, z);
                helper.getLevel().setBlock(column.below(), Blocks.STONE.defaultBlockState(), 3);
                boolean wall = x == -1 || x == width || z == -1 || z == depth;
                if (wall) {
                    helper.getLevel().setBlock(column, Blocks.STONE.defaultBlockState(), 3);
                    helper.getLevel().setBlock(column.above(), Blocks.STONE.defaultBlockState(), 3);
                    helper.getLevel().setBlock(column.above(2), Blocks.STONE.defaultBlockState(), 3);
                } else {
                    helper.getLevel().setBlock(column, Blocks.AIR.defaultBlockState(), 3);
                    helper.getLevel().setBlock(column.above(), Blocks.AIR.defaultBlockState(), 3);
                    helper.getLevel().setBlock(column.above(2), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void placeBed(GameTestHelper helper, BlockPos foot, Direction facing) {
        BlockState footState = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, facing)
                .setValue(BedBlock.PART, BedPart.FOOT);
        BlockState headState = footState.setValue(BedBlock.PART, BedPart.HEAD);
        helper.getLevel().setBlock(foot, footState, 3);
        helper.getLevel().setBlock(foot.relative(facing), headState, 3);
    }
}
