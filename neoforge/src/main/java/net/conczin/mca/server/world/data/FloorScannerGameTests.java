package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class FloorScannerGameTests {
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

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

    @GameTest(batch = "mca_floor_doorway_cell", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void doorwayCellIsCanonicalRoomBoundary(GameTestHelper helper) {
        BlockPos roomMin = helper.absolutePos(new BlockPos(4, 2, 4));
        buildClosedRoom(helper, roomMin, 5, 3);
        var level = helper.getLevel();

        for (int z = 0; z < 3; z++) {
            BlockPos wall = roomMin.offset(2, 0, z);
            level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(wall.above(), Blocks.STONE.defaultBlockState(), 3);
        }

        BlockPos doorway = roomMin.offset(2, 0, 1);
        placeDoor(helper, doorway, Direction.WEST);
        setDoorOpen(helper, doorway, true);

        BlockPos left = doorway.west();
        BlockPos right = doorway.east();
        SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(level, left, 128, 16);

        helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
                "doorway room scan failed: " + scan.result());
        helper.assertTrue(scan.floor().cellAt(doorway).isPresent(),
                "scanner did not retain the exact doorway Floor cell");
        helper.assertTrue(scan.floor().connectorTypesByCell().get(doorway) == FloorConnector.Type.DOOR,
                "doorway Floor cell lost its connector marker");
        helper.assertTrue(scan.transitions().stream().anyMatch(edge -> edge.connects(doorway, right)),
                "doorway lost physical connectivity to the non-owner side");
        helper.assertTrue(scan.transitions().stream().anyMatch(edge -> edge.connects(left, doorway)),
                "doorway lost physical connectivity to its FACING owner side");

        List<RoomPartitioner.Component> rooms = RoomPartitioner.partition(
                scan.floor(), scan.transitions(), StructureConnector.doorOwnerSides(level, scan.floor()));
        helper.assertTrue(rooms.size() == 2, "doorway merged both Room components");
        RoomPartitioner.Component owner = rooms.stream()
                .filter(room -> room.contains(doorway))
                .findFirst().orElseThrow();
        helper.assertTrue(owner.contains(left),
                "WEST-facing door did not assign its cell to the WEST Room");
        helper.assertTrue(!owner.contains(right),
                "WEST-facing door assigned its cell to the EAST Room");
        helper.assertTrue(rooms.stream().mapToInt(RoomPartitioner.Component::area).sum() == scan.floor().cells().size(),
                "Room partition lost or duplicated Floor cells at the doorway");
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_lower_doorway", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void oneBlockLowerDoorwayRemainsInSelectedStorey(GameTestHelper helper) {
        BlockPos roomMin = helper.absolutePos(new BlockPos(4, 3, 4));
        buildClosedRoom(helper, roomMin, 5, 3);
        var level = helper.getLevel();

        for (int z = 0; z < 3; z++) {
            BlockPos wall = roomMin.offset(2, 0, z);
            level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(wall.above(), Blocks.STONE.defaultBlockState(), 3);
        }

        BlockPos doorway = roomMin.offset(2, -1, 1);
        level.setBlock(doorway.below(), Blocks.STONE.defaultBlockState(), 3);
        placeDoor(helper, doorway, Direction.WEST);
        setDoorOpen(helper, doorway, true);

        BlockPos left = roomMin.offset(1, 0, 1);
        BlockPos right = roomMin.offset(3, 0, 1);
        SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(level, left, 128, 16);

        helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
                "lower-doorway scan failed: " + scan.result());
        helper.assertTrue(scan.floor().cellAt(doorway).isPresent(),
                "reachable one-block-lower doorway was split into another storey");
        helper.assertTrue(scan.floor().cellAt(right).isPresent(),
                "selected storey stopped at the one-block-lower doorway");
        List<RoomPartitioner.Component> rooms = RoomPartitioner.partition(
                scan.floor(), scan.transitions(), StructureConnector.doorOwnerSides(level, scan.floor()));
        RoomPartitioner.Component owner = rooms.stream()
                .filter(room -> room.contains(doorway))
                .findFirst().orElseThrow();
        helper.assertTrue(owner.contains(left),
                "one-block-lower WEST-facing door did not belong to its WEST owner side");
        helper.assertTrue(!owner.contains(right),
                "one-block-lower WEST-facing door was assigned to the EAST Room");
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_upper_doorway", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void oneBlockHigherDoorwayKeepsFacingOwner(GameTestHelper helper) {
        BlockPos roomMin = helper.absolutePos(new BlockPos(4, 3, 4));
        buildClosedRoom(helper, roomMin, 5, 3);
        var level = helper.getLevel();

        for (int z = 0; z < 3; z++) {
            BlockPos wall = roomMin.offset(2, 0, z);
            level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(wall.above(), Blocks.STONE.defaultBlockState(), 3);
        }

        BlockPos doorway = roomMin.offset(2, 1, 1);
        placeDoor(helper, doorway, Direction.WEST);
        BlockPos left = roomMin.offset(1, 0, 1);
        BlockPos right = roomMin.offset(3, 0, 1);
        SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(level, left, 128, 16);

        helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
                "upper-doorway scan failed: " + scan.result());
        helper.assertTrue(scan.floor().cellAt(doorway).isPresent(),
                "reachable one-block-higher doorway was split from the selected storey");
        List<RoomPartitioner.Component> rooms = RoomPartitioner.partition(
                scan.floor(), scan.transitions(), StructureConnector.doorOwnerSides(level, scan.floor()));
        RoomPartitioner.Component owner = rooms.stream()
                .filter(room -> room.contains(doorway))
                .findFirst().orElseThrow();
        helper.assertTrue(owner.contains(left),
                "one-block-higher WEST-facing door did not belong to its WEST owner side");
        helper.assertTrue(!owner.contains(right),
                "one-block-higher WEST-facing door was assigned to the EAST Room");
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_doorway_expansion", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 120)
    public static void addRoomPersistsNewBoundaryDoorCell(GameTestHelper helper) {
        BlockPos firstMin = helper.absolutePos(new BlockPos(5, 2, 5));
        buildClosedRoom(helper, firstMin, 4, 4);
        BlockPos firstSeed = firstMin.offset(1, 0, 1);
        VillageManager manager = new VillageManager(helper.getLevel());
        RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());

        BuildingScanResult initialScan = workflow.analyzeBuildingAddition(firstSeed);
        String initialType = initialScan.isAmbiguous() ? initialScan.matchingTypes().getFirst() : null;
        RoomWorkflow.Outcome initial = workflow.commitAddition(initialScan, initialType);
        helper.assertTrue(initial.status() == RoomWorkflow.Status.COMMITTED,
                "initial room was not committed: " + initial.result());

        BlockPos secondMin = firstMin.offset(5, 0, 0);
        buildClosedRoom(helper, secondMin, 4, 4);
        BlockPos doorway = firstMin.offset(4, 0, 1);
        placeDoor(helper, doorway, Direction.EAST);

        BlockPos secondSeed = doorway.east();
        BuildingScanResult additionScan = workflow.analyzeRoom(secondSeed);
        String addedType = additionScan.isAmbiguous() ? additionScan.matchingTypes().getFirst() : null;
        RoomWorkflow.Outcome added = workflow.commitAddition(additionScan, addedType);
        helper.assertTrue(added.status() == RoomWorkflow.Status.COMMITTED,
                "adjacent room was not committed: " + added.result());

        Village village = manager.findNearestVillage(secondSeed, Village.MERGE_MARGIN).orElseThrow();
        Structure structure = village.getStructures().values().stream().findFirst().orElseThrow();
        StructureFloor floor = structure.getFloors().getFirst();
        helper.assertTrue(floor.geometry().cellAt(doorway).isPresent(),
                "expanded Floor lost the newly added doorway cell");
        long doorwayOwners = village.getRooms()
                .filter(room -> room.getStructureId() == structure.getId())
                .filter(room -> room.getFloorId() == floor.id())
                .filter(room -> room.getFloorCells().contains(doorway))
                .count();
        helper.assertTrue(doorwayOwners == 1,
                "new doorway Floor cell belongs to " + doorwayOwners + " persisted Rooms");
        Set<BlockPos> persistedRoomCells = village.getRooms()
                .filter(room -> room.getStructureId() == structure.getId())
                .filter(room -> room.getFloorId() == floor.id())
                .flatMap(room -> room.getFloorCells().stream())
                .collect(Collectors.toSet());
        Set<BlockPos> floorCells = floor.geometry().cells().stream()
                .map(FloorGeometry.Cell::feet)
                .collect(Collectors.toSet());
        helper.assertTrue(persistedRoomCells.equals(floorCells),
                "expanded Floor contains cells that no persisted Room owns");
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_unregistered_door_side", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 140)
    public static void unregisteredRoomAcrossDoorCanBeAddedWithoutOverlap(GameTestHelper helper) {
        BlockPos firstMin = helper.absolutePos(new BlockPos(5, 2, 5));
        BlockPos secondMin = firstMin.offset(3, 0, 0);
        buildClosedRoom(helper, firstMin, 2, 5);
        buildClosedRoom(helper, secondMin, 3, 5);
        BlockPos doorway = firstMin.offset(2, 0, 2);
        // Room A is west of the door, so face west to keep the doorway owned by Room A.
        placeDoor(helper, doorway, Direction.WEST);

        VillageManager manager = new VillageManager(helper.getLevel());
        RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());
        BlockPos firstSeed = firstMin.offset(1, 0, 1);
        BuildingScanResult initialScan = workflow.analyzeBuildingAddition(firstSeed);
        String initialType = initialScan.isAmbiguous() ? initialScan.matchingTypes().getFirst() : null;
        RoomWorkflow.Outcome initial = workflow.commitAddition(initialScan, initialType);
        helper.assertTrue(initial.status() == RoomWorkflow.Status.COMMITTED,
                "initial room was not committed: " + initial.result());

        Village village = manager.findNearestVillage(firstSeed, Village.MERGE_MARGIN).orElseThrow();
        StructureFloor floor = village.getStructures().values().stream()
                .findFirst().orElseThrow().getFloors().getFirst();
        Building existing = village.getRooms().findFirst().orElseThrow();
        helper.assertTrue(floor.geometry().cells().size() == 26,
                "fixture Floor area was " + floor.geometry().cells().size() + " instead of 26");
        helper.assertTrue(existing.getFloorCells().size() == 11,
                "fixture registered Room area was " + existing.getFloorCells().size() + " instead of 11");
        helper.assertTrue(existing.getFloorCells().contains(doorway),
                "only registered Room did not retain the doorway cell");

        BlockPos secondSeed = secondMin.offset(1, 0, 1);
        BuildingScanResult additionScan = workflow.analyzeRoom(secondSeed);

        helper.assertTrue(additionScan.result() == Building.validationResult.SUCCESS,
                "unregistered side of doorway was rejected as " + additionScan.result());
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_initial_door_owner", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 140)
    public static void initialBuildingOwnsDoorOnFacingSide(GameTestHelper helper) {
        BlockPos firstMin = helper.absolutePos(new BlockPos(5, 2, 5));
        BlockPos secondMin = firstMin.offset(3, 0, 0);
        buildClosedRoom(helper, firstMin, 2, 5);
        buildClosedRoom(helper, secondMin, 3, 5);
        BlockPos doorway = firstMin.offset(2, 0, 2);
        placeDoor(helper, doorway, Direction.WEST);

        VillageManager manager = new VillageManager(helper.getLevel());
        RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());
        BlockPos firstSeed = firstMin.offset(1, 0, 1);
        BuildingScanResult initialScan = workflow.analyzeBuildingAddition(firstSeed);
        String initialType = initialScan.isAmbiguous() ? initialScan.matchingTypes().getFirst() : null;
        RoomWorkflow.Outcome initial = workflow.commitAddition(initialScan, initialType);
        helper.assertTrue(initial.status() == RoomWorkflow.Status.COMMITTED,
                "initial room was not committed: " + initial.result());

        Village village = manager.findNearestVillage(firstSeed, Village.MERGE_MARGIN).orElseThrow();
        Structure structure = village.getStructures().values().stream().findFirst().orElseThrow();
        StructureFloor floor = structure.getFloors().getFirst();
        Building existing = village.getRooms().findFirst().orElseThrow();

        helper.assertTrue(floor.geometry().cellAt(doorway).isPresent(),
                "doorway was not retained as an exact Floor cell");
        helper.assertTrue(floor.geometry().connectorTypesByCell().get(doorway) == FloorConnector.Type.DOOR,
                "doorway Floor cell lost its DOOR connector metadata");
        helper.assertTrue(existing.getFloorCells().contains(doorway),
                "initial Room on the door-facing side did not own the doorway cell");

        RoomScanPlan doorPlan = RoomScanPlanner.plan(village, helper.getLevel(), doorway);
        helper.assertTrue(doorPlan.mode() == Village.RoomScanMode.UPDATE_ROOM,
                "interacting with the owned doorway produced " + doorPlan.mode());
        helper.assertTrue(doorPlan.currentRoom().orElse(null) == existing,
                "doorway interaction did not resolve to the initially registered Room");
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_stale_door_owner", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 140)
    public static void stalePersistedDoorOwnershipUsesFreshPartitionBeforeAddingRoom(GameTestHelper helper) {
        BlockPos firstMin = helper.absolutePos(new BlockPos(5, 2, 5));
        BlockPos secondMin = firstMin.offset(3, 0, 0);
        buildClosedRoom(helper, firstMin, 2, 5);
        buildClosedRoom(helper, secondMin, 3, 5);
        BlockPos doorway = firstMin.offset(2, 0, 2);
        placeDoor(helper, doorway, Direction.WEST);

        VillageManager manager = new VillageManager(helper.getLevel());
        RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());
        BlockPos firstSeed = firstMin.offset(1, 0, 1);
        BuildingScanResult initialScan = workflow.analyzeBuildingAddition(firstSeed);
        String initialType = initialScan.isAmbiguous() ? initialScan.matchingTypes().getFirst() : null;
        RoomWorkflow.Outcome initial = workflow.commitAddition(initialScan, initialType);
        helper.assertTrue(initial.status() == RoomWorkflow.Status.COMMITTED,
                "initial room was not committed: " + initial.result());

        Village village = manager.findNearestVillage(firstSeed, Village.MERGE_MARGIN).orElseThrow();
        Building existing = village.getRooms().findFirst().orElseThrow();
        helper.assertTrue(existing.getFloorCells().contains(doorway),
                "fixture did not initially assign the doorway to the registered Room");

        Set<BlockPos> staleCells = new HashSet<>(existing.getFloorCells());
        staleCells.remove(doorway);
        existing.setGeometry(existing.getRawPos0(), existing.getRawPos1(), staleCells);
        helper.assertTrue(village.resolveInteractionPosition(doorway).orElseThrow().position().room() == null,
                "fixture did not reproduce an unowned persisted doorway cell");

        RoomScanPlan plan = RoomScanPlanner.plan(village, helper.getLevel(), doorway);

        helper.assertTrue(plan.mode() == Village.RoomScanMode.UPDATE_ROOM,
                "fresh doorway ownership was ignored and produced " + plan.mode());
        helper.assertTrue(plan.currentRoom().orElse(null) == existing,
                "fresh doorway partition did not recover the registered Room identity");
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_lower_doorway_expansion", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 120)
    public static void addRoomThroughOneBlockLowerDoorwaySucceeds(GameTestHelper helper) {
        BlockPos firstMin = helper.absolutePos(new BlockPos(5, 3, 5));
        buildClosedRoom(helper, firstMin, 4, 4);
        BlockPos firstSeed = firstMin.offset(1, 0, 1);
        VillageManager manager = new VillageManager(helper.getLevel());
        RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());

        BuildingScanResult initialScan = workflow.analyzeBuildingAddition(firstSeed);
        String initialType = initialScan.isAmbiguous() ? initialScan.matchingTypes().getFirst() : null;
        RoomWorkflow.Outcome initial = workflow.commitAddition(initialScan, initialType);
        helper.assertTrue(initial.status() == RoomWorkflow.Status.COMMITTED,
                "initial upper room was not committed: " + initial.result());

        BlockPos secondMin = firstMin.offset(5, -1, 0);
        buildClosedRoom(helper, secondMin, 4, 4);
        BlockPos doorway = firstMin.offset(4, -1, 1);
        helper.getLevel().setBlock(doorway.below(), Blocks.STONE.defaultBlockState(), 3);
        placeDoor(helper, doorway, Direction.EAST);

        BlockPos secondSeed = secondMin.offset(1, 0, 1);
        BuildingScanResult additionScan = workflow.analyzeRoom(secondSeed);
        helper.assertTrue(additionScan.result() == Building.validationResult.SUCCESS,
                "one-block-lower adjacent Room analysis failed: " + additionScan.result());
        String addedType = additionScan.isAmbiguous() ? additionScan.matchingTypes().getFirst() : null;
        RoomWorkflow.Outcome added = workflow.commitAddition(additionScan, addedType);
        helper.assertTrue(added.status() == RoomWorkflow.Status.COMMITTED,
                "one-block-lower adjacent room was not committed: " + added.result());

        Village village = manager.findNearestVillage(secondSeed, Village.MERGE_MARGIN).orElseThrow();
        helper.assertTrue(village.getRooms().count() == 2,
                "lower doorway expansion did not persist exactly two Rooms");
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_doorway_owner_stable", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 140)
    public static void doorOwnershipIgnoresAdjacentRoomSizeAndOpenState(GameTestHelper helper) {
        BlockPos firstMin = helper.absolutePos(new BlockPos(5, 2, 5));
        BlockPos secondMin = firstMin.offset(5, 0, 0);
        buildClosedRoom(helper, firstMin, 4, 4);
        buildClosedRoom(helper, secondMin, 2, 2);
        BlockPos doorway = firstMin.offset(4, 0, 1);
        placeDoor(helper, doorway, Direction.WEST);

        VillageManager manager = new VillageManager(helper.getLevel());
        RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());
        BlockPos firstSeed = firstMin.offset(1, 0, 1);
        BuildingScanResult initialScan = workflow.analyzeBuildingAddition(firstSeed);
        String initialType = initialScan.isAmbiguous() ? initialScan.matchingTypes().getFirst() : null;
        RoomWorkflow.Outcome initial = workflow.commitAddition(initialScan, initialType);
        helper.assertTrue(initial.status() == RoomWorkflow.Status.COMMITTED,
                "initial doorway room was not committed: " + initial.result());

        Village village = manager.findNearestVillage(firstSeed, Village.MERGE_MARGIN).orElseThrow();
        Building existingRoom = village.getRooms().findFirst().orElseThrow();
        int existingRoomId = existingRoom.getId();
        helper.assertTrue(existingRoom.getFloorCells().contains(doorway),
                "initial smaller-side partition did not assign the doorway to the existing Room");

        buildClosedRoom(helper, secondMin, 6, 4);
        placeDoor(helper, doorway, Direction.WEST);
        setDoorOpen(helper, doorway, true);
        BlockPos secondSeed = secondMin.offset(3, 0, 2);
        BuildingScanResult additionScan = workflow.analyzeRoom(secondSeed);
        String addedType = additionScan.isAmbiguous() ? additionScan.matchingTypes().getFirst() : null;
        RoomWorkflow.Outcome added = workflow.commitAddition(additionScan, addedType);
        helper.assertTrue(added.status() == RoomWorkflow.Status.COMMITTED,
                "larger adjacent Room failed after doorway refresh: " + added.result());

        Building refreshedExisting = village.getBuilding(existingRoomId).orElseThrow();
        helper.assertTrue(refreshedExisting.getFloorCells().contains(doorway),
                "doorway moved away from the FACING owner side");
        long doorwayOwners = village.getRooms()
                .filter(room -> room.getFloorCells().contains(doorway))
                .count();
        helper.assertTrue(doorwayOwners == 1,
                "doorway belongs to " + doorwayOwners + " persisted Rooms");
        helper.assertTrue(village.getRooms()
                .filter(room -> room.getId() != existingRoomId)
                .noneMatch(room -> room.getFloorCells().contains(doorway)),
                "larger Room stole the doorway from its FACING owner side");
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

    @GameTest(batch = "mca_floor_narrow_upper_storey", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 100)
    public static void narrowUpperCorridorDoesNotCollapseIntoLowerStorey(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3));
        buildTwoStoreyStaircase(helper, origin);
        narrowUpperStoreyToCorridor(helper, origin);

        BlockPos lowerRoom = origin.offset(1, 0, 1);
        BlockPos upperRoom = origin.offset(9, 3, 1);
        SelectedFloorScanner.Result upper = SelectedFloorScanner.scan(
                helper.getLevel(), upperRoom, 256, 24);

        helper.assertTrue(upper.result() == Building.validationResult.SUCCESS,
                "narrow upper-storey scan failed: " + upper.result());
        helper.assertTrue(upper.floor().anchorY() == upperRoom.getY(),
                "narrow upper corridor collapsed to anchor " + upper.floor().anchorY());
        helper.assertTrue(upper.floor().cellAt(lowerRoom).isEmpty(),
                "narrow upper corridor absorbed the lower Room");
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_persisted_interaction_truth", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 100)
    public static void exactPersistedInteractionIsNotOverriddenByFreshAnchor(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3));
        buildTwoStoreyStaircase(helper, origin);
        BlockPos low = origin.offset(1, 0, 1);
        BlockPos source = origin.offset(9, 3, 1);
        Set<BlockPos> ownedCells = Set.of(low, source);
        FloorGeometry persistedGeometry = new FloorGeometry(Set.of(
                new FloorGeometry.Cell(low, low.getY() + 3),
                new FloorGeometry.Cell(source, source.getY() + 3)), Map.of());
        StructureFloor floor = new StructureFloor(0, 0, persistedGeometry);
        Structure structure = new Structure(20, low, List.of(floor));
        structure.setLogicalBuildingId(20);
        Building room = new Building(low);
        room.setId(100);
        room.setStructureId(20);
        room.setFloorId(0);
        room.setGeometry(low, new BlockPos(source.getX(), source.getY() + 2, source.getZ()), ownedCells);
        Village village = new Village(1, helper.getLevel());
        village.registerStructure(structure, room);

        RoomScanPlan plan = RoomScanPlanner.plan(village, helper.getLevel(), source);

        helper.assertTrue(plan.mode() == Village.RoomScanMode.UPDATE_ROOM,
                "exact persisted Room was overridden by fresh scan action " + plan.mode());
        helper.assertTrue(plan.currentRoom().orElse(null) == room,
                "exact persisted Room identity was not preserved");
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

    @GameTest(batch = "mca_floor_external_basement", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 100)
    public static void externalBasementDoorAttachesToOverlappingBuilding(GameTestHelper helper) {
        BlockPos basementMin = helper.absolutePos(new BlockPos(6, 2, 6));
        BlockPos groundMin = basementMin.above(4);
        buildClosedRoom(helper, basementMin, 4, 4);
        buildClosedRoom(helper, groundMin, 4, 4);

        BlockPos door = basementMin.west().offset(0, 0, 1);
        placeDoor(helper, door, Direction.WEST);

        BlockPos source = door.west();
        helper.getLevel().setBlock(source.below(), Blocks.STONE.defaultBlockState(), 3);

        BlockPos groundSeed = groundMin.offset(1, 0, 1);
        SelectedFloorScanner.Result groundScan = SelectedFloorScanner.scan(
                helper.getLevel(), groundSeed, 256, 16);
        helper.assertTrue(groundScan.result() == Building.validationResult.SUCCESS,
                "ground floor scan failed: " + groundScan.result());

        StructureFloor groundFloor = new StructureFloor(0, 0, groundScan.floor());
        Structure groundStructure = new Structure(10, groundSeed, List.of(groundFloor));
        Building groundRoom = new Building(groundSeed);
        groundRoom.setId(100);
        groundRoom.setStructureId(10);
        groundRoom.setFloorId(0);
        groundRoom.setGeometry(groundSeed, groundSeed, Set.of(groundSeed));

        Village village = new Village(1, helper.getLevel());
        village.registerStructure(groundStructure, groundRoom);

        RoomScanPlan plan = village.getRoomScanPlan(helper.getLevel(), source);
        helper.assertTrue(plan.mode() == Village.RoomScanMode.ADD_BASEMENT,
                "external basement planned as " + plan.mode());
        helper.assertTrue(plan.targetBuildingId() == 10,
                "external basement targeted building " + plan.targetBuildingId());
        helper.assertTrue(plan.prospectiveFloorNumber() == -1,
                "external basement floor number was " + plan.prospectiveFloorNumber());
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

    @GameTest(batch = "mca_floor_uneven_source_independent", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 100)
    public static void unevenCaveScanDoesNotDependOnSelectedHeight(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(5, 2, 5));
        buildUnevenRoofedPassage(helper, origin);

        BlockPos low = origin;
        BlockPos middle = origin.offset(2, 1, 0);
        BlockPos high = origin.offset(5, 2, 0);
        SelectedFloorScanner.Result lowScan = SelectedFloorScanner.scan(helper.getLevel(), low, 128, 16);
        SelectedFloorScanner.Result middleScan = SelectedFloorScanner.scan(helper.getLevel(), middle, 128, 16);
        SelectedFloorScanner.Result highScan = SelectedFloorScanner.scan(helper.getLevel(), high, 128, 16);

        helper.assertTrue(lowScan.result() == Building.validationResult.SUCCESS,
                "low uneven scan failed: " + lowScan.result());
        helper.assertTrue(middleScan.result() == Building.validationResult.SUCCESS,
                "middle uneven scan failed: " + middleScan.result());
        helper.assertTrue(highScan.result() == Building.validationResult.SUCCESS,
                "high uneven scan failed: " + highScan.result());

        Set<BlockPos> expected = Set.of(
                origin,
                origin.offset(1, 0, 0),
                origin.offset(2, 1, 0),
                origin.offset(3, 1, 0),
                origin.offset(4, 2, 0),
                origin.offset(5, 2, 0));
        Set<BlockPos> lowCells = lowScan.floor().cells().stream()
                .map(FloorGeometry.Cell::feet).collect(Collectors.toSet());
        Set<BlockPos> middleCells = middleScan.floor().cells().stream()
                .map(FloorGeometry.Cell::feet).collect(Collectors.toSet());
        Set<BlockPos> highCells = highScan.floor().cells().stream()
                .map(FloorGeometry.Cell::feet).collect(Collectors.toSet());
        helper.assertTrue(lowCells.equals(expected),
                "low source discovered wrong uneven Floor membership: expected " + expected + " but got " + lowCells);
        helper.assertTrue(middleCells.equals(expected),
                "middle source changed uneven Floor membership: expected " + expected + " but got " + middleCells);
        helper.assertTrue(highCells.equals(expected),
                "high source changed uneven Floor membership: expected " + expected + " but got " + highCells);
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_uneven_plateau_width", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 100)
    public static void widerHighPlateauDoesNotBecomeAnotherStorey(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(5, 2, 5));
        buildUnevenRoofedPassage(helper, origin, new int[]{0, 0, 1, 1, 2, 2, 2});

        BlockPos low = origin;
        BlockPos high = origin.offset(6, 2, 0);
        SelectedFloorScanner.Result lowScan = SelectedFloorScanner.scan(helper.getLevel(), low, 128, 16);
        SelectedFloorScanner.Result highScan = SelectedFloorScanner.scan(helper.getLevel(), high, 128, 16);

        helper.assertTrue(lowScan.result() == Building.validationResult.SUCCESS,
                "wide-plateau low scan failed: " + lowScan.result());
        helper.assertTrue(highScan.result() == Building.validationResult.SUCCESS,
                "wide-plateau high scan failed: " + highScan.result());
        Set<BlockPos> expected = Set.of(
                origin,
                origin.offset(1, 0, 0),
                origin.offset(2, 1, 0),
                origin.offset(3, 1, 0),
                origin.offset(4, 2, 0),
                origin.offset(5, 2, 0),
                origin.offset(6, 2, 0));
        Set<BlockPos> lowCells = lowScan.floor().cells().stream()
                .map(FloorGeometry.Cell::feet).collect(Collectors.toSet());
        Set<BlockPos> highCells = highScan.floor().cells().stream()
                .map(FloorGeometry.Cell::feet).collect(Collectors.toSet());
        helper.assertTrue(lowCells.equals(expected),
                "wide-plateau low source discovered wrong Floor membership");
        helper.assertTrue(highCells.equals(expected),
                "high plateau source discovered wrong Floor membership");
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_uneven_exterior", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 100)
    public static void highSourceStillFindsExteriorThroughUnevenDescent(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(5, 2, 5));
        buildUnevenRoofedPassage(helper, origin);
        BlockPos exterior = origin.west();
        helper.getLevel().setBlock(exterior.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(exterior, Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(exterior.above(), Blocks.AIR.defaultBlockState(), 3);

        BlockPos high = origin.offset(5, 2, 0);
        SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(helper.getLevel(), high, 128, 16);

        helper.assertTrue(scan.result() == Building.validationResult.NOT_IN_BUILDING,
                "high source ignored exterior reachable through uneven descent: " + scan.result());
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_single_sided_door", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 100)
    public static void singleSidedDoorCellBelongsToItsOnlyRoom(GameTestHelper helper) {
        BlockPos roomMin = helper.absolutePos(new BlockPos(5, 2, 5));
        buildClosedRoom(helper, roomMin, 4, 4);
        BlockPos doorway = roomMin.offset(4, 0, 1);
        placeDoor(helper, doorway, Direction.WEST);

        BlockPos interior = doorway.west();
        SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(helper.getLevel(), interior, 128, 16);
        helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
                "single-sided doorway scan failed: " + scan.result());
        List<RoomPartitioner.Component> rooms = RoomPartitioner.partition(
                scan.floor(), scan.transitions(),
                StructureConnector.doorOwnerSides(helper.getLevel(), scan.floor()));
        helper.assertTrue(rooms.size() == 1,
                "single-sided doorway created a separate Room component");
        helper.assertTrue(rooms.getFirst().contains(doorway),
                "single-sided doorway cell did not join its only adjacent Room");
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_three_arm_source_independent", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 100)
    public static void threeArmRoomIsSourceIndependent(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(6, 2, 6));
        Set<BlockPos> relative = Set.of(
                new BlockPos(2, 0, 2),
                new BlockPos(1, 0, 2), new BlockPos(0, 0, 2),
                new BlockPos(3, 0, 2), new BlockPos(4, 0, 2),
                new BlockPos(2, 0, 3), new BlockPos(2, 0, 4));
        Set<BlockPos> expected = buildRoofedFootprint(helper, origin, relative);

        assertExactFloorFromSources(helper, expected,
                origin.offset(0, 0, 2),
                origin.offset(4, 0, 2),
                origin.offset(2, 0, 4),
                origin.offset(2, 0, 2));
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_uneven_three_arm_source_independent", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 100)
    public static void unevenThreeArmRoomIsSourceIndependent(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(6, 2, 6));
        Set<BlockPos> relative = Set.of(
                new BlockPos(2, 1, 2),
                new BlockPos(1, 0, 2), new BlockPos(0, 0, 2),
                new BlockPos(3, 2, 2), new BlockPos(4, 2, 2),
                new BlockPos(2, 1, 3), new BlockPos(2, 0, 4));
        Set<BlockPos> expected = buildRoofedFootprint(helper, origin, relative);

        assertExactFloorFromSources(helper, expected,
                origin.offset(0, 0, 2),
                origin.offset(4, 2, 2),
                origin.offset(2, 0, 4),
                origin.offset(2, 1, 2));
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_three_rooms_two_doors", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 120)
    public static void twoDoorsPartitionLShapedFloorIntoThreeRooms(GameTestHelper helper) {
        BlockPos firstMin = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos secondMin = firstMin.offset(5, 0, 0);
        BlockPos thirdMin = secondMin.offset(0, 0, 5);
        buildClosedRoom(helper, firstMin, 4, 4);
        buildClosedRoom(helper, secondMin, 4, 4);
        buildClosedRoom(helper, thirdMin, 4, 4);

        BlockPos firstDoor = firstMin.offset(4, 0, 1);
        BlockPos secondDoor = secondMin.offset(1, 0, 4);
        placeDoor(helper, firstDoor, Direction.EAST);
        placeDoor(helper, secondDoor, Direction.SOUTH);

        BlockPos firstSeed = firstMin.offset(1, 0, 1);
        BlockPos secondSeed = secondMin.offset(2, 0, 2);
        BlockPos thirdSeed = thirdMin.offset(2, 0, 2);
        SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(helper.getLevel(), firstSeed, 256, 24);
        helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
                "three-room floor scan failed: " + scan.result());

        List<RoomPartitioner.Component> rooms = RoomPartitioner.partition(scan.floor(), scan.transitions());
        helper.assertTrue(rooms.size() == 3, "two doors produced " + rooms.size() + " Rooms instead of 3");
        helper.assertTrue(RoomPartitioner.select(firstSeed, scan.floor(), rooms)
                        != RoomPartitioner.select(secondSeed, scan.floor(), rooms),
                "first and second room seeds resolved to the same component");
        helper.assertTrue(RoomPartitioner.select(secondSeed, scan.floor(), rooms)
                        != RoomPartitioner.select(thirdSeed, scan.floor(), rooms),
                "second and third room seeds resolved to the same component");
        helper.assertTrue(rooms.stream().filter(room -> room.contains(firstDoor)).count() == 1,
                "first door was not owned exactly once");
        helper.assertTrue(rooms.stream().filter(room -> room.contains(secondDoor)).count() == 1,
                "second door was not owned exactly once");
        helper.assertTrue(rooms.stream().mapToInt(RoomPartitioner.Component::area).sum()
                        == scan.floor().cells().size(),
                "three-room partition lost or duplicated Floor cells");
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_three_door_add_room", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 160)
    public static void threeDoorFloorCanAddOneUnregisteredRoomWithoutOverlap(GameTestHelper helper) {
        BlockPos firstMin = helper.absolutePos(new BlockPos(3, 2, 5));
        BlockPos secondMin = firstMin.offset(5, 0, 0);
        BlockPos thirdMin = secondMin.offset(5, 0, 0);
        BlockPos fourthMin = thirdMin.offset(5, 0, 0);
        buildClosedRoom(helper, firstMin, 4, 4);
        buildClosedRoom(helper, secondMin, 4, 4);
        buildClosedRoom(helper, thirdMin, 4, 4);
        buildClosedRoom(helper, fourthMin, 4, 4);

        BlockPos firstDoor = firstMin.offset(4, 0, 1);
        BlockPos secondDoor = secondMin.offset(4, 0, 1);
        BlockPos thirdDoor = thirdMin.offset(4, 0, 1);
        placeDoor(helper, firstDoor, Direction.EAST);
        placeDoor(helper, secondDoor, Direction.EAST);
        placeDoor(helper, thirdDoor, Direction.EAST);

        VillageManager manager = new VillageManager(helper.getLevel());
        RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());
        BlockPos firstSeed = firstMin.offset(2, 0, 2);
        BuildingScanResult initialScan = workflow.analyzeBuildingAddition(firstSeed);
        String initialType = initialScan.isAmbiguous() ? initialScan.matchingTypes().getFirst() : null;
        RoomWorkflow.Outcome initial = workflow.commitAddition(initialScan, initialType);
        helper.assertTrue(initial.status() == RoomWorkflow.Status.COMMITTED,
                "initial room in three-door Floor was not committed: " + initial.result());

        Village village = manager.findNearestVillage(firstSeed, Village.MERGE_MARGIN).orElseThrow();
        StructureFloor floor = village.getStructures().values().stream()
                .findFirst().orElseThrow().getFloors().getFirst();
        helper.assertTrue(floor.connectors().stream()
                        .filter(marker -> marker.type() == FloorConnector.Type.DOOR).count() == 3,
                "three-door fixture did not retain all three connector cells");
        helper.assertTrue(village.getRooms().count() == 1,
                "initial registration unexpectedly persisted unselected Rooms");

        BlockPos secondSeed = secondMin.offset(2, 0, 2);
        BuildingScanResult additionScan = workflow.analyzeRoom(secondSeed);
        helper.assertTrue(additionScan.result() == Building.validationResult.SUCCESS,
                "three-door Floor rejected the selected unregistered Room as " + additionScan.result());
        String addedType = additionScan.isAmbiguous() ? additionScan.matchingTypes().getFirst() : null;
        RoomWorkflow.Outcome added = workflow.commitAddition(additionScan, addedType);
        helper.assertTrue(added.status() == RoomWorkflow.Status.COMMITTED,
                "three-door Floor failed to commit selected Room: " + added.result());
        helper.assertTrue(village.getRooms().count() == 2,
                "three-door Floor did not persist exactly the selected two Rooms");
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_stacked_multi_room", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 140)
    public static void stackedFloorsWithMultipleRoomsStayIndependent(GameTestHelper helper) {
        BlockPos lowerLeft = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos lowerRight = lowerLeft.offset(5, 0, 0);
        BlockPos upperLeft = lowerLeft.above(4);
        BlockPos upperRight = lowerRight.above(4);
        buildClosedRoom(helper, lowerLeft, 4, 4);
        buildClosedRoom(helper, lowerRight, 4, 4);
        buildClosedRoom(helper, upperLeft, 4, 4);
        buildClosedRoom(helper, upperRight, 4, 4);

        BlockPos lowerDoor = lowerLeft.offset(4, 0, 1);
        BlockPos upperDoor = upperLeft.offset(4, 0, 2);
        placeDoor(helper, lowerDoor, Direction.EAST);
        placeDoor(helper, upperDoor, Direction.EAST);

        BlockPos verticalConnector = lowerLeft.offset(0, 0, 3);
        var level = helper.getLevel();
        level.setBlock(verticalConnector, Blocks.LADDER.defaultBlockState(), 3);
        level.setBlock(verticalConnector.above(), Blocks.LADDER.defaultBlockState(), 3);
        level.setBlock(verticalConnector.above(2), Blocks.LADDER.defaultBlockState(), 3);
        level.setBlock(verticalConnector.above(3), Blocks.OAK_TRAPDOOR.defaultBlockState(), 3);

        SelectedFloorScanner.Result lower = SelectedFloorScanner.scan(
                level, lowerLeft.offset(2, 0, 2), 256, 24);
        SelectedFloorScanner.Result upper = SelectedFloorScanner.scan(
                level, upperLeft.offset(2, 0, 2), 256, 24);
        helper.assertTrue(lower.result() == Building.validationResult.SUCCESS,
                "lower multi-room floor scan failed: " + lower.result());
        helper.assertTrue(upper.result() == Building.validationResult.SUCCESS,
                "upper multi-room floor scan failed: " + upper.result());
        helper.assertTrue(lower.floor().cellAt(upperLeft.offset(2, 0, 2)).isEmpty(),
                "lower Floor absorbed an upper Room cell");
        helper.assertTrue(upper.floor().cellAt(lowerLeft.offset(2, 0, 2)).isEmpty(),
                "upper Floor absorbed a lower Room cell");
        helper.assertTrue(RoomPartitioner.partition(lower.floor(), lower.transitions()).size() == 2,
                "lower Floor did not retain two Rooms");
        helper.assertTrue(RoomPartitioner.partition(upper.floor(), upper.transitions()).size() == 2,
                "upper Floor did not retain two Rooms");
        helper.assertTrue(lower.floor().connectorTypesByCell().containsValue(FloorConnector.Type.DOOR),
                "lower Floor lost its door connector");
        helper.assertTrue(upper.floor().connectorTypesByCell().containsValue(FloorConnector.Type.DOOR),
                "upper Floor lost its door connector");

        StructureFloor lowerFloor = new StructureFloor(0, 0, lower.floor());
        StructureFloor upperFloor = new StructureFloor(1, 1, upper.floor());
        helper.assertTrue(StructureConnector.connectsFloors(
                        List.of(verticalConnector, verticalConnector.above(),
                                verticalConnector.above(2), verticalConnector.above(3)),
                        lowerFloor, upperFloor),
                "vertical connector did not link the two multi-room Floors");

        BlockPos lowerLeftSeed = lowerLeft.offset(2, 0, 2);
        BlockPos lowerRightSeed = lowerRight.offset(2, 0, 2);
        BlockPos upperLeftSeed = upperLeft.offset(2, 0, 2);
        BlockPos upperRightSeed = upperRight.offset(2, 0, 2);
        List<BuildingRoomScanner.Result> lowerRooms = BuildingRoomScanner.partition(
                level, lowerLeftSeed, 256, 0, lower.floor(), lower.transitions());
        List<BuildingRoomScanner.Result> upperRooms = BuildingRoomScanner.partition(
                level, upperLeftSeed, 256, 1, upper.floor(), upper.transitions());
        helper.assertTrue(lowerRooms.size() == 2 && upperRooms.size() == 2,
                "multi-floor room materialization did not preserve two Rooms per Floor");

        Building lowerLeftRoom = materializedRoom(100, 10, componentAt(lowerRooms, lowerLeftSeed));
        Building lowerRightRoom = materializedRoom(101, 10, componentAt(lowerRooms, lowerRightSeed));
        Building upperLeftRoom = materializedRoom(102, 10, componentAt(upperRooms, upperLeftSeed));
        Building upperRightRoom = materializedRoom(103, 10, componentAt(upperRooms, upperRightSeed));
        Structure structure = new Structure(10, lowerLeftSeed, List.of(lowerFloor, upperFloor));
        Village village = new Village(1, level);
        village.registerStructure(structure, lowerLeftRoom);
        village.registerRoom(lowerRightRoom);
        village.registerRoom(upperLeftRoom);
        village.registerRoom(upperRightRoom);

        assertResolvedRoom(helper, village, lowerLeftSeed, 0, lowerLeftRoom);
        assertResolvedRoom(helper, village, lowerRightSeed, 0, lowerRightRoom);
        assertResolvedRoom(helper, village, upperLeftSeed, 1, upperLeftRoom);
        assertResolvedRoom(helper, village, upperRightSeed, 1, upperRightRoom);
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

    private static Set<BlockPos> buildRoofedFootprint(
            GameTestHelper helper, BlockPos origin, Set<BlockPos> relativeCells) {
        Set<BlockPos> cells = relativeCells.stream()
                .map(relative -> origin.offset(relative.getX(), relative.getY(), relative.getZ()))
                .collect(Collectors.toSet());
        Set<Long> columns = cells.stream()
                .map(cell -> FloorGeometry.columnKey(cell.getX(), cell.getZ()))
                .collect(Collectors.toSet());
        int minY = cells.stream().mapToInt(BlockPos::getY).min().orElse(origin.getY());
        int maxY = cells.stream().mapToInt(BlockPos::getY).max().orElse(origin.getY());
        var level = helper.getLevel();

        for (BlockPos cell : cells) {
            level.setBlock(cell.below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(cell.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(cell.above(2), Blocks.STONE.defaultBlockState(), 3);
        }
        for (BlockPos cell : cells) {
            for (Direction direction : HORIZONTAL) {
                BlockPos neighbor = cell.relative(direction);
                if (columns.contains(FloorGeometry.columnKey(neighbor.getX(), neighbor.getZ()))) continue;
                for (int y = minY - 1; y <= maxY + 2; y++) {
                    level.setBlock(new BlockPos(neighbor.getX(), y, neighbor.getZ()),
                            Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        return Set.copyOf(cells);
    }

    private static void assertExactFloorFromSources(
            GameTestHelper helper, Set<BlockPos> expected, BlockPos... sources) {
        for (BlockPos source : sources) {
            SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(helper.getLevel(), source, 256, 24);
            helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
                    "scan from " + source + " failed: " + scan.result());
            Set<BlockPos> actual = scan.floor().cells().stream()
                    .map(FloorGeometry.Cell::feet)
                    .collect(Collectors.toSet());
            helper.assertTrue(actual.equals(expected),
                    "source " + source + " changed exact Floor membership: expected=" + expected
                            + " actual=" + actual);
            helper.assertTrue(RoomPartitioner.partition(scan.floor(), scan.transitions()).size() == 1,
                    "source " + source + " split one irregular Room into multiple components");
        }
    }

    private static BuildingRoomScanner.Result componentAt(
            List<BuildingRoomScanner.Result> rooms, BlockPos cell) {
        return rooms.stream()
                .filter(room -> room.status() == Building.validationResult.SUCCESS)
                .filter(room -> room.floorCells().contains(cell))
                .findFirst()
                .orElseThrow();
    }

    private static Building materializedRoom(
            int id, int structureId, BuildingRoomScanner.Result scan) {
        Building room = new Building(scan.seed());
        room.setId(id);
        room.setStructureId(structureId);
        room.setFloorId(scan.floorId());
        room.setGeometry(scan.min(), scan.max(), scan.floorCells());
        return room;
    }

    private static void assertResolvedRoom(GameTestHelper helper,
                                           Village village,
                                           BlockPos source,
                                           int expectedFloorId,
                                           Building expectedRoom) {
        Village.ResolvedInteraction resolved = village.resolveInteractionPosition(source).orElseThrow();
        helper.assertTrue(resolved.position().floor().id() == expectedFloorId,
                "source " + source + " resolved Floor " + resolved.position().floor().id()
                        + " instead of " + expectedFloorId);
        helper.assertTrue(resolved.position().room() == expectedRoom,
                "source " + source + " resolved the wrong persisted Room");
    }

    private static void placeBed(GameTestHelper helper, BlockPos foot, Direction facing) {
        BlockState footState = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, facing)
                .setValue(BedBlock.PART, BedPart.FOOT);
        BlockState headState = footState.setValue(BedBlock.PART, BedPart.HEAD);
        helper.getLevel().setBlock(foot, footState, 3);
        helper.getLevel().setBlock(foot.relative(facing), headState, 3);
    }

    private static void placeDoor(GameTestHelper helper, BlockPos lower, Direction facing) {
        BlockState lowerDoor = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        helper.getLevel().setBlock(lower, lowerDoor, 3);
        helper.getLevel().setBlock(lower.above(),
                lowerDoor.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
    }

    private static void setDoorOpen(GameTestHelper helper, BlockPos lower, boolean open) {
        helper.getLevel().setBlock(lower,
                helper.getLevel().getBlockState(lower).setValue(DoorBlock.OPEN, open), 3);
        helper.getLevel().setBlock(lower.above(),
                helper.getLevel().getBlockState(lower.above()).setValue(DoorBlock.OPEN, open), 3);
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

    private static void narrowUpperStoreyToCorridor(GameTestHelper helper, BlockPos origin) {
        var level = helper.getLevel();
        for (int x = 8; x <= 11; x++) {
            for (int z : new int[]{0, 2}) {
                for (int dy = 3; dy <= 5; dy++) {
                    level.setBlock(origin.offset(x, dy, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void buildUnevenRoofedPassage(GameTestHelper helper, BlockPos origin) {
        buildUnevenRoofedPassage(helper, origin, new int[]{0, 0, 1, 1, 2, 2});
    }

    private static void buildUnevenRoofedPassage(GameTestHelper helper, BlockPos origin, int[] heights) {
        var level = helper.getLevel();
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
