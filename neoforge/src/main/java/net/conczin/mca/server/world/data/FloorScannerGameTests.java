package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.HashSet;
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

    @GameTest(batch = "mca_floor_staircase_storey", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 100)
    public static void staircaseKeepsUpperRoomOutOfLowerStorey(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3));
        int floorY = origin.getY();
        buildTwoStoreyStaircase(helper, origin);

        SelectedFloorScanner.Result lower = SelectedFloorScanner.scan(
                helper.getLevel(), origin.offset(1, 0, 1), 256, 24);
        BlockPos topStair = origin.offset(7, 3, 1);
        BlockPos upperRoom = origin.offset(9, 3, 1);
        SelectedFloorScanner.Result upper = SelectedFloorScanner.scan(
                helper.getLevel(), upperRoom, 256, 24);

        helper.assertTrue(lower.result() == Building.validationResult.SUCCESS,
                "lower staircase scan failed: " + lower.result());
        helper.assertTrue(lower.floor().anchorY() == floorY,
                "lower staircase changed storey anchor to " + lower.floor().anchorY());
        helper.assertTrue(lower.floor().cellAt(topStair).isPresent(),
                "lower storey lost its top staircase transition");
        helper.assertTrue(lower.floor().cellAt(upperRoom).isEmpty(),
                "lower storey absorbed the upper room");
        helper.assertTrue(upper.result() == Building.validationResult.SUCCESS,
                "upper staircase scan failed: " + upper.result());
        helper.assertTrue(upper.floor().anchorY() == floorY + 3,
                "upper staircase changed storey anchor to " + upper.floor().anchorY());
        helper.assertTrue(upper.floor().cellAt(topStair).isEmpty(),
                "upper storey reclaimed the lower-owned top staircase transition");
        helper.assertTrue(lower.connectedFloors().stream()
                        .anyMatch(floor -> floor.anchorY() == floorY + 3),
                "stair attachment evidence was lost");
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_slab_stair_transition", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void slabAndStairUseTransientSurfaceEvidence(GameTestHelper helper) {
        BlockPos roomMin = helper.absolutePos(new BlockPos(4, 2, 4));
        buildClosedRoom(helper, roomMin, 5, 4);
        BlockPos slabCell = roomMin.offset(1, 0, 1);
        BlockPos stairCell = slabCell.east();
        var level = helper.getLevel();
        level.setBlock(slabCell.below(), Blocks.STONE_SLAB.defaultBlockState(), 3);
        level.setBlock(stairCell.below(), Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.EAST), 3);

        SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(level, roomMin.offset(3, 0, 2), 128, 16);

        helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
                "slab/stair room scan failed: " + scan.result());
        helper.assertTrue(scan.floor().cellAt(slabCell).isPresent(), "slab-supported integer cell was lost");
        helper.assertTrue(scan.floor().cellAt(stairCell).isPresent(), "stair-supported integer cell was lost");
        helper.assertTrue(scan.transitions().stream().anyMatch(edge -> edge.connects(slabCell, stairCell)),
                "slab/stair physical transition was not retained");
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_lower_exterior_storey", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 100)
    public static void lowerExteriorCheckDoesNotClimbOpenUpperStorey(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3));
        buildTwoStoreyStaircase(helper, origin);
        openUpperStorey(helper, origin);

        SelectedFloorScanner.Result lower = SelectedFloorScanner.scan(
                helper.getLevel(), origin.offset(1, 0, 1), 256, 24);

        helper.assertTrue(lower.result() == Building.validationResult.SUCCESS,
                "open upper storey leaked into lower exterior validation: " + lower.result());
        helper.assertTrue(lower.floor().anchorY() == origin.getY(),
                "open upper storey changed the lower anchor");
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_vertical_connector", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 100)
    public static void ladderTrapdoorAttachesFloorsWithoutMergingRooms(GameTestHelper helper) {
        BlockPos lowerMin = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos upperMin = lowerMin.above(4);
        buildClosedRoom(helper, lowerMin, 4, 4);
        buildClosedRoom(helper, upperMin, 4, 4);

        BlockPos connector = lowerMin.offset(0, 0, 1);
        var level = helper.getLevel();
        level.setBlock(connector, Blocks.LADDER.defaultBlockState(), 3);
        level.setBlock(connector.above(), Blocks.LADDER.defaultBlockState(), 3);
        level.setBlock(connector.above(2), Blocks.LADDER.defaultBlockState(), 3);
        level.setBlock(connector.above(3), Blocks.OAK_TRAPDOOR.defaultBlockState(), 3);

        SelectedFloorScanner.Result lower = SelectedFloorScanner.scan(level, lowerMin.offset(2, 0, 2), 256, 16);
        SelectedFloorScanner.Result upper = SelectedFloorScanner.scan(level, upperMin.offset(2, 0, 2), 256, 16);

        helper.assertTrue(lower.result() == Building.validationResult.SUCCESS,
                "lower ladder floor scan failed: " + lower.result());
        helper.assertTrue(upper.result() == Building.validationResult.SUCCESS,
                "upper ladder floor scan failed: " + upper.result());
        helper.assertTrue(lower.floor().connectorTypesByCell().containsValue(FloorConnector.Type.LADDER),
                "lower floor lost ladder connector metadata");
        helper.assertTrue(upper.floor().connectorTypesByCell().values().stream()
                        .anyMatch(type -> type == FloorConnector.Type.LADDER || type == FloorConnector.Type.TRAPDOOR),
                "upper floor lost vertical connector metadata");

        StructureFloor lowerFloor = new StructureFloor(0, 0, lower.floor());
        StructureFloor upperFloor = new StructureFloor(1, 1, upper.floor());
        helper.assertTrue(StructureConnector.connectsFloors(
                        java.util.List.of(connector, connector.above(), connector.above(2), connector.above(3)),
                        lowerFloor, upperFloor),
                "ladder/trapdoor column did not attach the two floors");

        Set<FloorGeometry.Cell> combinedCells = new HashSet<>(lower.floor().cells());
        combinedCells.addAll(upper.floor().cells());
        Set<SelectedFloorScanner.Transition> combinedTransitions = new HashSet<>(lower.transitions());
        combinedTransitions.addAll(upper.transitions());
        helper.assertTrue(RoomPartitioner.partition(new FloorGeometry(combinedCells, java.util.Map.of()),
                        combinedTransitions).size() == 2,
                "vertical connector merged lower and upper Room components");
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_uneven_cave", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 100)
    public static void unevenCaveRemainsOneStoreyAcrossThreeIntegerHeights(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(5, 2, 5));
        buildUnevenRoofedPassage(helper, origin);

        BlockPos low = origin;
        BlockPos middle = origin.offset(2, 1, 0);
        BlockPos high = origin.offset(4, 2, 0);
        SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(helper.getLevel(), low, 128, 16);

        helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
                "uneven cave scan failed: " + scan.result());
        helper.assertTrue(scan.floor().cellAt(low).isPresent(), "low cave cell was lost");
        helper.assertTrue(scan.floor().cellAt(middle).isPresent(), "middle cave cell was lost");
        helper.assertTrue(scan.floor().cellAt(high).isPresent(), "high cave cell was lost");
        helper.assertTrue(RoomPartitioner.partition(scan.floor(), scan.transitions()).size() == 1,
                "uneven cave split into multiple Room components");
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

    private static void buildTwoStoreyStaircase(GameTestHelper helper, BlockPos origin) {
        var level = helper.getLevel();
        int y = origin.getY();
        for (int x = -1; x <= 12; x++) {
            for (int z = -1; z <= 3; z++) {
                if (x == -1 || x == 12 || z == -1 || z == 3) {
                    for (int dy = 0; dy <= 5; dy++) {
                        level.setBlock(new BlockPos(origin.getX() + x, y + dy, origin.getZ() + z),
                                Blocks.STONE.defaultBlockState(), 3);
                    }
                }
                level.setBlock(new BlockPos(origin.getX() + x, y + 6, origin.getZ() + z),
                        Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int x = 0; x <= 3; x++) {
            for (int z = 0; z <= 2; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int x = 8; x <= 11; x++) {
            for (int z = 0; z <= 2; z++) {
                level.setBlock(origin.offset(x, 2, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int step = 0; step < 4; step++) {
            level.setBlock(origin.offset(4 + step, -1 + step, 1),
                    Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST), 3);
        }
    }

    private static void openUpperStorey(GameTestHelper helper, BlockPos origin) {
        var level = helper.getLevel();
        int y = origin.getY();
        for (int x = 8; x <= 12; x++) {
            for (int z = -1; z <= 3; z++) {
                for (int dy = 3; dy <= 6; dy++) {
                    level.setBlock(new BlockPos(origin.getX() + x, y + dy, origin.getZ() + z),
                            Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void buildUnevenRoofedPassage(GameTestHelper helper, BlockPos origin) {
        var level = helper.getLevel();
        int[] heights = {0, 0, 1, 1, 2, 2};
        for (int x = 0; x < heights.length; x++) {
            int dy = heights[x];
            BlockPos feet = origin.offset(x, dy, 0);
            level.setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(feet.above(2), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(feet.north(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(feet.north().above(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(feet.south(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(feet.south().above(), Blocks.STONE.defaultBlockState(), 3);
        }
        level.setBlock(origin.west(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(origin.west().above(), Blocks.STONE.defaultBlockState(), 3);
        BlockPos end = origin.offset(heights.length - 1, heights[heights.length - 1], 0);
        level.setBlock(end.east(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(end.east().above(), Blocks.STONE.defaultBlockState(), 3);
    }
}
