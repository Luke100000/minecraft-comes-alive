package net.conczin.mca.server.world.data;

import com.mojang.authlib.GameProfile;
import net.conczin.mca.Config;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.conczin.mca.network.c2s.ConfirmBuildingPolymorphMessage;
import net.conczin.mca.network.c2s.ReportBuildingMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@GameTestHolder("mca")
@PrefixGameTestTemplate(false)
public final class CopiedOpenHouseGameTests {
    private static final String TEMPLATE = "gametest/copied_open_house";
    private static final BlockPos LOWER_STOREY_SEED = new BlockPos(23, 5, 12);
    private static final BlockPos MAIN_STOREY_SEED = new BlockPos(15, 11, 12);
    private static final BlockPos MAIN_STOREY_STAIR_EXIT = new BlockPos(12, 10, 11);
    private static final BlockPos UPPER_STOREY_SEED = new BlockPos(11, 15, 13);
    private static final List<BlockPos> LOWER_STAIR_SEEDS = List.of(
            new BlockPos(10, 9, 13), new BlockPos(11, 9, 11), new BlockPos(10, 9, 12));

    // The 26.x GameTest bridge places template coordinates directly at helper-relative Y.
    private static final List<BlockPos> STOREY_SEEDS = List.of(
            LOWER_STOREY_SEED,
            MAIN_STOREY_SEED,
            UPPER_STOREY_SEED);

    private CopiedOpenHouseGameTests() {
    }

    @GameTest(batch = "mca_copied_open_house_wall_cavity", templateNamespace = "mca",
            template = TEMPLATE, timeoutTicks = 240, skyAccess = true)
    public static void carvedWallCavityStopsAtSolidColumn(GameTestHelper helper) {
        // Reproduce the live MCA Floor NBT Test edits from the diagnose trace without changing the fixture.
        // GameTest relative Y is template-NBT Y + 1.
        var level = helper.getLevel();
        level.setBlock(helper.absolutePos(new BlockPos(34, 6, 6)), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(helper.absolutePos(new BlockPos(35, 6, 6)), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(helper.absolutePos(new BlockPos(35, 7, 6)), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(helper.absolutePos(new BlockPos(36, 5, 6)), Blocks.STRIPPED_OAK_WOOD.defaultBlockState(), 3);
        level.setBlock(helper.absolutePos(new BlockPos(36, 6, 6)), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(helper.absolutePos(new BlockPos(36, 7, 6)), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(helper.absolutePos(new BlockPos(36, 7, 7)), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(helper.absolutePos(new BlockPos(37, 6, 7)), Blocks.AIR.defaultBlockState(), 3);

        BlockPos seed = helper.absolutePos(new BlockPos(20, 7, 13));
        BlockPos openCavity = helper.absolutePos(new BlockPos(36, 6, 6));
        BlockPos headBlockedCavity = helper.absolutePos(new BlockPos(37, 6, 7));
        SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(
                level, seed, Config.getInstance().maxBuildingSize, Config.getInstance().maxBuildingRadius);

        helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
                "reported lower-floor scan failed: " + scan.result());
        helper.assertTrue(scan.floor().cellAt(openCavity).isPresent(),
                "carved two-block passage was not reflected in live Floor geometry");
        helper.assertTrue(scan.floor().cellAt(headBlockedCavity).isEmpty(),
                "solid wall continuation became structural Floor geometry");
        helper.succeed();
    }

    @GameTest(batch = "mca_copied_open_house_rooms", templateNamespace = "mca",
            template = TEMPLATE, timeoutTicks = 240, skyAccess = true)
    public static void everyCopiedHouseRoomComponentValidates(GameTestHelper helper) {
        Config config = Config.getInstance();
        List<String> failures = new ArrayList<>();

        for (BlockPos relativeSeed : STOREY_SEEDS) {
            BlockPos seed = helper.absolutePos(relativeSeed);
            SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(
                    helper.getLevel(), seed, config.maxBuildingSize, config.maxBuildingRadius);
            if (scan.result() != Building.validationResult.SUCCESS) {
                failures.add(relativeSeed + "=" + scan.result());
                continue;
            }
            List<RoomPartitioner.Component> components = BuildingRoomScanner.components(helper.getLevel(), scan);
            helper.assertTrue(!components.isEmpty(),
                    "copied house scan from " + relativeSeed + " produced no Room components");

            List<BuildingRoomScanner.Result> rooms = BuildingRoomScanner.partition(
                    helper.getLevel(), seed, config.maxBuildingSize, scan);
            helper.assertTrue(rooms.size() == components.size(),
                    "copied house materialization changed component count at " + relativeSeed
                            + ": components=" + components.size() + " rooms=" + rooms.size());
            helper.assertTrue(rooms.stream().allMatch(room -> room.status() == Building.validationResult.SUCCESS),
                    "copied house has an invalid Room component at " + relativeSeed + ": "
                            + rooms.stream().map(BuildingRoomScanner.Result::status).toList());

            Set<BlockPos> expected = scan.floor().cells().stream()
                    .map(FloorGeometry.Cell::feet)
                    .collect(Collectors.toSet());
            Set<BlockPos> owned = new HashSet<>();
            for (BuildingRoomScanner.Result room : rooms) {
                for (BlockPos cell : room.floorCells()) {
                    helper.assertTrue(owned.add(cell),
                            "copied house duplicated Floor ownership at " + cell + " from " + relativeSeed);
                }
            }
            helper.assertTrue(owned.equals(expected),
                    "copied house Room partition did not exactly own the selected Floor at " + relativeSeed
                            + ": floor=" + expected.size() + " owned=" + owned.size());
        }

        helper.assertTrue(failures.isEmpty(), "copied house storey scan failures: " + failures);
        helper.succeed();
    }

    @GameTest(batch = "mca_copied_open_house_registration", templateNamespace = "mca",
            template = TEMPLATE, timeoutTicks = 280, skyAccess = true)
    public static void initialRegistrationPreservesFloorButRegistersOnlySelectedRoom(GameTestHelper helper) {
        Config config = Config.getInstance();
        BlockPos seed = helper.absolutePos(MAIN_STOREY_SEED);
        SelectedFloorScanner.Result floorScan = SelectedFloorScanner.scan(
                helper.getLevel(), seed, config.maxBuildingSize, config.maxBuildingRadius);
        helper.assertTrue(floorScan.result() == Building.validationResult.SUCCESS,
                "copied main floor scan failed: " + floorScan.result());

        List<RoomPartitioner.Component> components = BuildingRoomScanner.components(helper.getLevel(), floorScan);
        helper.assertTrue(components.size() == 1,
                "open main Floor should contain one Room under its actual roof: " + components.size());
        RoomPartitioner.Component selected = RoomPartitioner.select(seed, floorScan.floor(), components);
        helper.assertTrue(selected != null, "copied main-floor seed did not select a Room component");

        VillageManager manager = new VillageManager(helper.getLevel());
        RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());
        BuildingScanResult addition = workflow.analyzeBuildingAddition(seed);
        helper.assertTrue(addition.result() == Building.validationResult.SUCCESS,
                "copied house initial building scan failed: " + addition.result());
        String selectedType = addition.isAmbiguous() ? addition.matchingTypes().getFirst() : null;
        RoomWorkflow.Outcome outcome = workflow.commitAddition(addition, selectedType);
        helper.assertTrue(outcome.status() == RoomWorkflow.Status.COMMITTED,
                "copied house initial building commit failed: " + outcome.result());

        Village village = manager.findNearestVillage(seed, Village.MERGE_MARGIN).orElseThrow();
        Structure structure = village.getStructures().values().stream().findFirst().orElseThrow();
        StructureFloor floor = structure.getFloors().getFirst();
        List<Building> rooms = village.getRooms()
                .filter(room -> room.getStructureId() == structure.getId())
                .filter(room -> room.getFloorId() == floor.id())
                .toList();
        helper.assertTrue(rooms.size() == 1,
                "initial registration should persist only the selected Room, got " + rooms.size());

        Set<BlockPos> floorCells = floor.geometry().cells().stream()
                .map(FloorGeometry.Cell::feet)
                .collect(Collectors.toSet());
        Set<BlockPos> scannedFloorCells = floorScan.floor().cells().stream()
                .map(FloorGeometry.Cell::feet)
                .collect(Collectors.toSet());
        helper.assertTrue(floorCells.equals(scannedFloorCells),
                "initial registration did not preserve the complete scanned Floor");
        helper.assertTrue(rooms.getFirst().getFloorCells().equals(selected.floorCells()),
                "initial registration persisted a different Room component than the selected one");
        helper.succeed();
    }

    @GameTest(batch = "mca_copied_open_house_add_room", templateNamespace = "mca",
            template = TEMPLATE, timeoutTicks = 280, skyAccess = true)
    public static void registeredLowerRoomLeavesSiblingAsAddRoom(GameTestHelper helper) {
        Config config = Config.getInstance();
        BlockPos lowerSeed = helper.absolutePos(LOWER_STOREY_SEED);
        SelectedFloorScanner.Result floorScan = SelectedFloorScanner.scan(
                helper.getLevel(), lowerSeed, config.maxBuildingSize, config.maxBuildingRadius);
        helper.assertTrue(floorScan.result() == Building.validationResult.SUCCESS,
                "copied lower-floor scan failed: " + floorScan.result());

        List<RoomPartitioner.Component> components = BuildingRoomScanner.components(helper.getLevel(), floorScan);
        RoomPartitioner.Component selected = RoomPartitioner.select(lowerSeed, floorScan.floor(), components);
        helper.assertTrue(selected != null, "copied lower-floor seed did not select a Room component");
        RoomPartitioner.Component sibling = components.stream()
                .filter(component -> component != selected)
                .findFirst().orElseThrow();
        BlockPos siblingSeed = sibling.nearestCell(lowerSeed);

        VillageManager manager = new VillageManager(helper.getLevel());
        RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());
        BuildingScanResult initialScan = workflow.analyzeBuildingAddition(lowerSeed);
        helper.assertTrue(initialScan.result() == Building.validationResult.SUCCESS,
                "copied lower Room could not be registered: " + initialScan.result());
        String initialType = initialScan.isAmbiguous() ? initialScan.matchingTypes().getFirst() : null;
        RoomWorkflow.Outcome initial = workflow.commitAddition(initialScan, initialType);
        helper.assertTrue(initial.status() == RoomWorkflow.Status.COMMITTED,
                "copied lower Room registration failed: " + initial.result());

        Village village = manager.findNearestVillage(lowerSeed, Village.MERGE_MARGIN).orElseThrow();
        Building lowerRoom = village.findInteractionRoomAt(lowerSeed).orElseThrow();
        Structure structure = village.getStructureFor(lowerRoom).orElseThrow();
        RoomScanPlan siblingPlan = village.getRoomScanPlan(helper.getLevel(), siblingSeed);
        helper.assertTrue(siblingPlan.mode() == Village.RoomScanMode.ADD_ROOM,
                "unregistered copied sibling should offer Add Room, got " + siblingPlan.mode());
        helper.assertTrue(siblingPlan.targetStructureId() == structure.getId(),
                "Add Room targeted a different Structure");
        helper.assertTrue(siblingPlan.targetFloorId() == lowerRoom.getFloorId(),
                "Add Room targeted a different Floor");

        BuildingScanResult siblingScan = workflow.analyzeRoom(siblingSeed);
        helper.assertTrue(siblingScan.result() == Building.validationResult.SUCCESS,
                "copied sibling Add Room scan failed: " + siblingScan.result());
        String siblingType = siblingScan.isAmbiguous() ? siblingScan.matchingTypes().getFirst() : null;
        RoomWorkflow.Outcome added = workflow.commitAddition(siblingScan, siblingType);
        helper.assertTrue(added.status() == RoomWorkflow.Status.COMMITTED,
                "copied sibling Add Room commit failed: " + added.result());
        Building addedRoom = village.findInteractionRoomAt(siblingSeed).orElseThrow();
        helper.assertTrue(addedRoom.getStructureId() == structure.getId(),
                "Add Room created a separate Structure instead of extending the house");
        helper.succeed();
    }

    @GameTest(batch = "mca_copied_open_house_lifecycle", templateNamespace = "mca",
            template = TEMPLATE, timeoutTicks = 280, skyAccess = true)
    public static void stairRoomsRegisterUnderOneHouseAndSurviveReload(GameTestHelper helper) {
        BlockPos main = helper.absolutePos(MAIN_STOREY_SEED);
        VillageManager manager = new VillageManager(helper.getLevel());
        RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());
        commit(helper, workflow, workflow.analyzeBuildingAddition(main));
        Village village = manager.findNearestVillage(main, Village.MERGE_MARGIN).orElseThrow();
        Building mainRoom = village.findInteractionRoomAt(main).orElseThrow();
        int buildingId = village.getStructureFor(mainRoom).orElseThrow().getLogicalBuildingId();

        for (BlockPos relative : LOWER_STAIR_SEEDS) {
            RoomScanPlan plan = village.getRoomScanPlan(helper.getLevel(), helper.absolutePos(relative));
            helper.assertTrue(plan.mode() == Village.RoomScanMode.ADD_ATTACHMENT,
                    "lower staircase " + relative + " offered " + plan.mode());
            helper.assertTrue(plan.targetBuildingId() == buildingId,
                    "lower staircase selected another house at " + relative);
        }

        List<Integer> roomIds = new ArrayList<>();
        roomIds.add(mainRoom.getId());
        for (BlockPos relative : List.of(LOWER_STOREY_SEED, UPPER_STOREY_SEED)) {
            BlockPos source = helper.absolutePos(relative);
            commit(helper, workflow, workflow.analyzeAttachedRoom(source, buildingId));
            Building room = village.findInteractionRoomAt(source).orElseThrow();
            helper.assertTrue(village.getStructureFor(room).orElseThrow().getLogicalBuildingId() == buildingId,
                    "attachment created another house at " + relative);
            roomIds.add(room.getId());
        }
        List<BlockPos> registeredSeeds = List.of(MAIN_STOREY_SEED, LOWER_STOREY_SEED, UPPER_STOREY_SEED);
        for (int i = 0; i < registeredSeeds.size(); i++) {
            BlockPos source = helper.absolutePos(registeredSeeds.get(i));
            RegisteredRoomUpdate update = workflow.analyzeRegisteredRoomUpdate(village, roomIds.get(i), source);
            helper.assertTrue(update.result() == Building.validationResult.SUCCESS,
                    "refresh rejected attached Room at " + registeredSeeds.get(i) + ": " + update.result());
            String type = update.requiresTypeSelection() ? update.matchingTypes().getFirst() : null;
            helper.assertTrue(manager.commitRegisteredRoomUpdate(update, type) == Building.validationResult.SUCCESS,
                    "refresh could not commit attached Room at " + registeredSeeds.get(i));
        }
        Village reloaded = new Village(village.save(), helper.getLevel());
        for (int i = 0; i < registeredSeeds.size(); i++) {
            BlockPos source = helper.absolutePos(registeredSeeds.get(i));
            Building room = reloaded.findInteractionRoomAt(source).orElseThrow();
            helper.assertTrue(room.getId() == roomIds.get(i), "reload changed Room identity at " + source);
            helper.assertTrue(reloaded.getStructureFor(room).orElseThrow().getLogicalBuildingId() == buildingId,
                    "reload changed the house at " + source);
            helper.assertTrue(reloaded.getRoomScanPlan(helper.getLevel(), source).mode()
                    == Village.RoomScanMode.UPDATE_ROOM, "registered Room is no longer selectable at " + source);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_copied_open_house_stair_exit", templateNamespace = "mca",
            template = TEMPLATE, timeoutTicks = 280, skyAccess = true)
    public static void topStairExitSelectsRegisteredMainRoom(GameTestHelper helper) {
        BlockPos main = helper.absolutePos(MAIN_STOREY_SEED);
        VillageManager manager = new VillageManager(helper.getLevel());
        RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());
        commit(helper, workflow, workflow.analyzeBuildingAddition(main));

        Village village = manager.findNearestVillage(main, Village.MERGE_MARGIN).orElseThrow();
        Building mainRoom = village.findInteractionRoomAt(main).orElseThrow();
        BlockPos stairExit = helper.absolutePos(MAIN_STOREY_STAIR_EXIT);
        RoomScanPlan plan = village.getRoomScanPlan(helper.getLevel(), stairExit);

        helper.assertTrue(plan.mode() == Village.RoomScanMode.UPDATE_ROOM,
                "top stair exit selected " + plan.mode() + " instead of the registered main Room");
        helper.assertTrue(plan.currentRoom().orElse(null) == mainRoom,
                "top stair exit lost the registered main Room identity");
        helper.succeed();
    }

    @GameTest(batch = "mca_copied_open_house_lower_chain", templateNamespace = "mca",
            template = TEMPLATE, timeoutTicks = 320, skyAccess = true)
    public static void successiveLowerStairRoomsKeepBoundedStoreysAndDistinctGeometry(GameTestHelper helper) {
        BlockPos main = helper.absolutePos(MAIN_STOREY_SEED);
        VillageManager manager = new VillageManager(helper.getLevel());
        RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());
        commit(helper, workflow, workflow.analyzeBuildingAddition(main));

        Village village = manager.findNearestVillage(main, Village.MERGE_MARGIN).orElseThrow();
        Building mainRoom = village.findInteractionRoomAt(main).orElseThrow();
        int buildingId = village.getStructureFor(mainRoom).orElseThrow().getLogicalBuildingId();
        List<BlockPos> lowerRooms = List.of(
                new BlockPos(9, 8, 11),
                new BlockPos(15, 6, 16),
                new BlockPos(22, 4, 9));
        List<FloorGeometry> attached = new ArrayList<>();
        List<Integer> expectedNumbers = List.of(-1, -1, -2);
        List<Integer> roomIds = new ArrayList<>();

        for (int index = 0; index < lowerRooms.size(); index++) {
            BlockPos source = helper.absolutePos(lowerRooms.get(index));
            RoomScanPlanner.Analysis analysis = RoomScanPlanner.analyze(village, helper.getLevel(), source);
            RoomScanPlan plan = analysis.plan();
            helper.assertTrue(plan.mode() == Village.RoomScanMode.ADD_ATTACHMENT,
                    "lower room " + lowerRooms.get(index) + " offered " + plan.mode());
            helper.assertTrue(plan.targetBuildingId() == buildingId,
                    "lower room " + lowerRooms.get(index) + " targeted another house");
            helper.assertTrue(plan.prospectiveFloorNumber() == expectedNumbers.get(index),
                    "lower room " + lowerRooms.get(index) + " has an unexpected bounded storey: floor="
                            + plan.prospectiveFloorNumber());
            helper.assertTrue(analysis.observation() != null,
                    "lower room " + lowerRooms.get(index) + " has no selected Floor");
            FloorGeometry selectedFloor = analysis.observation().scan().floor();
            helper.assertTrue(attached.stream().noneMatch(previous ->
                            previous.sameCellPositions(selectedFloor)),
                    "lower room " + lowerRooms.get(index) + " reused an earlier stair-delimited Floor");
            for (int later = index + 1; later < lowerRooms.size(); later++) {
                BlockPos laterSource = helper.absolutePos(lowerRooms.get(later));
                helper.assertTrue(selectedFloor.interactionCellAt(
                                laterSource.getX(), laterSource.getY(), laterSource.getZ()).isEmpty(),
                        "lower room " + lowerRooms.get(index) + " Floor already owns later room "
                                + lowerRooms.get(later) + "; anchor=" + analysis.observation().scan().anchorY()
                                + " cellYs=" + selectedFloor.cells().stream()
                                .collect(Collectors.groupingBy(cell -> cell.feet().getY(), Collectors.counting())));
            }

            attached.add(selectedFloor);
            BuildingScanResult addition = workflow.analyzeAttachedRoom(source, buildingId);
            commit(helper, workflow, addition);
            Building room = village.getRooms().filter(candidate -> candidate.getFloorCells()
                    .equals(addition.building().getFloorCells())).findFirst().orElseThrow();
            roomIds.add(room.getId());
        }

        Village reloaded = new Village(village.save(), helper.getLevel());
        for (int index = 0; index < roomIds.size(); index++) {
            Building room = reloaded.getBuilding(roomIds.get(index)).orElseThrow();
            Structure structure = reloaded.getStructureFor(room).orElseThrow();
            StructureFloor floor = structure.getFloor(room.getFloorId()).orElseThrow();
            helper.assertTrue(floor.floorNumber() == expectedNumbers.get(index),
                    "reload changed the lower room's storey");
            helper.assertTrue(floor.geometry().sameExactGeometry(attached.get(index)),
                    "reload changed the lower room's physical Floor");
        }
        helper.succeed();
    }

    private static void commit(GameTestHelper helper, RoomWorkflow workflow, BuildingScanResult scan) {
        helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
                "room analysis failed: " + scan.result());
        String type = scan.isAmbiguous() ? scan.matchingTypes().getFirst() : null;
        RoomWorkflow.Outcome outcome = workflow.commitAddition(scan, type);
        helper.assertTrue(outcome.status() == RoomWorkflow.Status.COMMITTED,
                "room commit failed: " + outcome.result());
    }

    @GameTest(batch = "mca_copied_open_house_stair_registration", templateNamespace = "mca",
            template = TEMPLATE, timeoutTicks = 280, skyAccess = true)
    public static void eachLowerStaircaseRegistersThePlannedRoomUnderTheHouse(GameTestHelper helper) {
        BlockPos main = helper.absolutePos(MAIN_STOREY_SEED);
        for (BlockPos relative : LOWER_STAIR_SEEDS) {
            VillageManager manager = new VillageManager(helper.getLevel());
            RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());
            commit(helper, workflow, workflow.analyzeBuildingAddition(main));
            Village village = manager.findNearestVillage(main, Village.MERGE_MARGIN).orElseThrow();
            BlockPos source = helper.absolutePos(relative);
            RoomScanPlanner.Analysis analysis = RoomScanPlanner.analyze(village, helper.getLevel(), source);
            RoomScanPlan plan = analysis.plan();
            helper.assertTrue(plan.mode() == Village.RoomScanMode.ADD_ATTACHMENT,
                    "staircase did not select the lower Floor at " + relative);
            BuildingScanResult scan = workflow.analyzeAttachedRoom(source, plan.targetBuildingId());
            helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
                    "staircase attachment failed at " + relative + ": " + scan.result());
            Set<BlockPos> selectedCells = scan.building().getFloorCells();
            commit(helper, workflow, scan);
            Building registered = village.getRooms()
                    .filter(room -> room.getFloorCells().equals(selectedCells)).findFirst().orElseThrow();
            Structure structure = village.getStructureFor(registered).orElseThrow();
            helper.assertTrue(structure.getLogicalBuildingId() == plan.targetBuildingId(),
                    "staircase registered a separate house at " + relative);
            helper.assertTrue(structure.getFloor(registered.getFloorId()).orElseThrow().geometry()
                            .sameCellPositions(analysis.observation().scan().floor()),
                    "staircase commit changed the selected Floor at " + relative);
            Village.RoomScanMode next = village.getRoomScanPlan(helper.getLevel(), source).mode();
            helper.assertTrue(next == Village.RoomScanMode.UPDATE_ROOM || next == Village.RoomScanMode.ADD_ROOM,
                    "registered staircase still offers a new building or attachment at " + relative + ": " + next);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_copied_open_house_confirmation", templateNamespace = "mca",
            template = TEMPLATE, timeoutTicks = 280, skyAccess = true)
    public static void attachmentConfirmationHandlerRejectsBlockedSource(GameTestHelper helper) {
        BlockPos main = helper.absolutePos(MAIN_STOREY_SEED);
        BlockPos lower = helper.absolutePos(LOWER_STOREY_SEED);
        VillageManager manager = VillageManager.get(helper.getLevel());
        RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());
        commit(helper, workflow, workflow.analyzeBuildingAddition(main));
        Village village = manager.findNearestVillage(main, Village.MERGE_MARGIN).orElseThrow();
        BlockState original = helper.getLevel().getBlockState(lower);
        try {
            ConfirmBuildingPolymorphMessage confirmation = attachmentConfirmation(helper, workflow, village, lower);
            // Use the loader's disconnected player fixture, not a replacement handler or manager.
            ServerPlayer player = new FakePlayer(helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "blueprint-confirmation"));
            player.setPos(lower.getX() + 0.5, lower.getY(), lower.getZ() + 0.5);
            int structuresBefore = village.getStructures().size();
            long roomsBefore = village.getRooms().count();

            helper.getLevel().setBlockAndUpdate(lower, Blocks.STONE.defaultBlockState());
            confirmation.handleServer(player);
            helper.assertTrue(village.getStructures().size() == structuresBefore
                            && village.getRooms().count() == roomsBefore,
                    "confirmation handler registered an attachment from a blocked source");

            helper.getLevel().setBlockAndUpdate(lower, original);
            confirmation.handleServer(player);
            helper.assertTrue(village.getStructures().size() == structuresBefore + 1
                            && village.getRooms().count() == roomsBefore + 1,
                    "confirmation handler did not register the valid attachment");
            Building registered = village.findInteractionRoomAt(lower).orElseThrow();
            helper.assertTrue(village.getStructureFor(registered).orElseThrow().getLogicalBuildingId()
                            == confirmation.expectedTargetId(),
                    "confirmation handler attached the Room to a different house");
        } finally {
            helper.getLevel().setBlockAndUpdate(lower, original);
            manager.removeVillage(village.getId());
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_copied_open_house_duplicate_confirmation", templateNamespace = "mca",
            template = TEMPLATE, timeoutTicks = 280, skyAccess = true)
    public static void attachmentConfirmationHandlerRejectsDuplicateRegistration(GameTestHelper helper) {
        BlockPos main = helper.absolutePos(MAIN_STOREY_SEED);
        BlockPos lower = helper.absolutePos(LOWER_STOREY_SEED);
        VillageManager manager = VillageManager.get(helper.getLevel());
        RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());
        commit(helper, workflow, workflow.analyzeBuildingAddition(main));
        Village village = manager.findNearestVillage(main, Village.MERGE_MARGIN).orElseThrow();
        try {
            ConfirmBuildingPolymorphMessage confirmation = attachmentConfirmation(helper, workflow, village, lower);
            ServerPlayer player = new FakePlayer(helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "blueprint-confirmation"));
            player.setPos(lower.getX() + 0.5, lower.getY(), lower.getZ() + 0.5);
            int structuresBefore = village.getStructures().size();
            long roomsBefore = village.getRooms().count();

            confirmation.handleServer(player);
            helper.assertTrue(village.getStructures().size() == structuresBefore + 1
                            && village.getRooms().count() == roomsBefore + 1,
                    "confirmation handler did not register the initial attachment");

            confirmation.handleServer(player);
            helper.assertTrue(village.getStructures().size() == structuresBefore + 1
                            && village.getRooms().count() == roomsBefore + 1,
                    "confirmation handler registered a duplicate attachment");
        } finally {
            manager.removeVillage(village.getId());
        }
        helper.succeed();
    }

    private static ConfirmBuildingPolymorphMessage attachmentConfirmation(
            GameTestHelper helper, RoomWorkflow workflow, Village village, BlockPos source) {
        RoomScanPlan plan = village.getRoomScanPlan(helper.getLevel(), source);
        helper.assertTrue(plan.mode() == Village.RoomScanMode.ADD_ATTACHMENT,
                "lower Room did not offer an attachment");
        BuildingScanResult scan = workflow.analyzeAttachedRoom(source, plan.targetBuildingId());
        helper.assertTrue(scan.result() == Building.validationResult.SUCCESS
                        && !scan.matchingTypes().isEmpty(),
                "lower Room must offer a valid type for confirmation");
        return new ConfirmBuildingPolymorphMessage(source, ReportBuildingMessage.Action.ADD_ATTACHMENT,
                plan.targetBuildingId(), scan.matchingTypes().getFirst());
    }

    @GameTest(batch = "mca_copied_open_house_storeys", templateNamespace = "mca",
            template = TEMPLATE, timeoutTicks = 280, skyAccess = true)
    public static void registeredMainRoomOffersUpperAndLowerStoreyAttachments(GameTestHelper helper) {
        BlockPos mainSeed = helper.absolutePos(MAIN_STOREY_SEED);
        VillageManager manager = new VillageManager(helper.getLevel());
        RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());
        BuildingScanResult initialScan = workflow.analyzeBuildingAddition(mainSeed);
        helper.assertTrue(initialScan.result() == Building.validationResult.SUCCESS,
                "copied main Room could not be registered: " + initialScan.result());
        String initialType = initialScan.isAmbiguous() ? initialScan.matchingTypes().getFirst() : null;
        RoomWorkflow.Outcome initial = workflow.commitAddition(initialScan, initialType);
        helper.assertTrue(initial.status() == RoomWorkflow.Status.COMMITTED,
                "copied main Room registration failed: " + initial.result());

        Village village = manager.findNearestVillage(mainSeed, Village.MERGE_MARGIN).orElseThrow();
        Building mainRoom = village.findInteractionRoomAt(mainSeed).orElseThrow();
        Structure structure = village.getStructureFor(mainRoom).orElseThrow();
        int logicalBuildingId = structure.getLogicalBuildingId();
        BlockPos upperSeed = helper.absolutePos(UPPER_STOREY_SEED);
        var mainCellsAtUpperColumn = structure.getFloors().stream()
                .flatMap(floor -> floor.geometry().cellsAtColumn(upperSeed.getX(), upperSeed.getZ()).stream())
                .toList();
        var mainColumnStates = mainCellsAtUpperColumn.stream()
                .flatMap(cell -> java.util.stream.IntStream.rangeClosed(cell.feet().getY(), upperSeed.getY())
                        .mapToObj(y -> y + "=" + helper.getLevel().getBlockState(
                                new BlockPos(upperSeed.getX(), y, upperSeed.getZ())).getBlock()))
                .toList();

        RoomScanPlan lowerPlan = village.getRoomScanPlan(
                helper.getLevel(), helper.absolutePos(LOWER_STOREY_SEED));
        helper.assertTrue(lowerPlan.mode() == Village.RoomScanMode.ADD_ATTACHMENT,
                "copied lower storey should attach as basement, got " + lowerPlan.mode());
        helper.assertTrue(lowerPlan.targetBuildingId() == logicalBuildingId,
                "copied lower storey targeted a different logical building");

        RoomScanPlan upperPlan = village.getRoomScanPlan(
                helper.getLevel(), upperSeed);
        helper.assertTrue(upperPlan.mode() == Village.RoomScanMode.ADD_ATTACHMENT,
                "copied upper storey should attach as floor, got " + upperPlan.mode()
                        + " upperSeed=" + upperSeed
                        + " mainCellsAtUpperColumn=" + mainCellsAtUpperColumn
                        + " mainColumnStates=" + mainColumnStates
                        + " resolvedRoom=" + village.findInteractionRoomAt(upperSeed).map(Building::getId));
        helper.assertTrue(upperPlan.targetBuildingId() == logicalBuildingId,
                "copied upper storey targeted a different logical building");
        helper.succeed();
    }

    @GameTest(batch = "mca_copied_open_house_basement_preserves_upper", templateNamespace = "mca",
            template = TEMPLATE, timeoutTicks = 320, skyAccess = true)
    public static void addingBasementPreservesRegisteredUpperStorey(GameTestHelper helper) {
        BlockPos main = helper.absolutePos(MAIN_STOREY_SEED);
        BlockPos upper = helper.absolutePos(UPPER_STOREY_SEED);
        BlockPos lower = helper.absolutePos(LOWER_STAIR_SEEDS.getFirst());
        VillageManager manager = new VillageManager(helper.getLevel());
        RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());

        commit(helper, workflow, workflow.analyzeBuildingAddition(main));
        Village village = manager.findNearestVillage(main, Village.MERGE_MARGIN).orElseThrow();
        Building mainRoom = village.findInteractionRoomAt(main).orElseThrow();
        int buildingId = village.getStructureFor(mainRoom).orElseThrow().getLogicalBuildingId();

        BuildingScanResult upperAddition = workflow.analyzeAttachedRoom(upper, buildingId);
        helper.assertTrue(upperAddition.result() == Building.validationResult.SUCCESS,
                "upper registration before basement failed: " + upperAddition.result());
        commit(helper, workflow, upperAddition);
        helper.assertTrue(village.getRoomScanPlan(helper.getLevel(), upper).mode()
                        == Village.RoomScanMode.UPDATE_ROOM,
                "registered upper storey was not selectable before adding the basement");

        RoomScanPlan lowerPlan = village.getRoomScanPlan(helper.getLevel(), lower);
        helper.assertTrue(lowerPlan.mode() == Village.RoomScanMode.ADD_ATTACHMENT,
                "upper registration changed basement plan to " + lowerPlan.mode());
        helper.assertTrue(lowerPlan.targetBuildingId() == buildingId,
                "upper registration changed basement target to " + lowerPlan.targetBuildingId());

        BuildingScanResult basementAddition = workflow.analyzeAttachedRoom(lower, buildingId);
        helper.assertTrue(basementAddition.result() == Building.validationResult.SUCCESS,
                "basement registration after upper failed: " + basementAddition.result());
        commit(helper, workflow, basementAddition);

        Building upperRoom = village.findInteractionRoomAt(upper).orElse(null);
        helper.assertTrue(upperRoom != null,
                "adding the basement removed the registered upper storey interaction");
        helper.assertTrue(village.getRoomScanPlan(helper.getLevel(), upper).mode()
                        == Village.RoomScanMode.UPDATE_ROOM,
                "adding the basement changed the registered upper storey action");
        SelectedFloorScanner.Result upperScan = SelectedFloorScanner.scan(
                helper.getLevel(), upper, Config.getInstance().maxBuildingSize,
                Config.getInstance().maxBuildingRadius);
        helper.assertTrue(upperScan.result() == Building.validationResult.SUCCESS,
                "adding the basement broke the upper fresh Floor scan: " + upperScan.result());
        helper.succeed();
    }

}
