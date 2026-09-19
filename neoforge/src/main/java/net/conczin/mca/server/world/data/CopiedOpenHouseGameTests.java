package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@GameTestHolder("mca")
@PrefixGameTestTemplate(false)
public final class CopiedOpenHouseGameTests {
    private static final String TEMPLATE = "gametest/copied_open_house";
    private static final BlockPos LOWER_STOREY_SEED = new BlockPos(23, 6, 12);
    private static final BlockPos MAIN_STOREY_SEED = new BlockPos(15, 12, 12);
    private static final BlockPos MAIN_STOREY_STAIR_EXIT = new BlockPos(12, 11, 11);
    private static final BlockPos UPPER_STOREY_SEED = new BlockPos(11, 16, 13);
    private static final List<BlockPos> LOWER_STAIR_SEEDS = List.of(
            new BlockPos(10, 10, 13), new BlockPos(11, 10, 11), new BlockPos(10, 10, 12));

    // The structure block itself sits one block below the template in 1.21.1,
    // so helper-relative Y is template-NBT Y + 1.
    private static final List<BlockPos> STOREY_SEEDS = List.of(
            LOWER_STOREY_SEED,
            MAIN_STOREY_SEED,
            UPPER_STOREY_SEED);

    private CopiedOpenHouseGameTests() {
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
                    helper.getLevel(), seed, config.maxBuildingSize, 0, scan);
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
            helper.assertTrue(plan.mode() == Village.RoomScanMode.ADD_BASEMENT,
                    "lower staircase " + relative + " offered " + plan.mode());
            helper.assertTrue(plan.targetBuildingId() == buildingId,
                    "lower staircase selected another house at " + relative);
        }

        List<Integer> roomIds = new ArrayList<>();
        roomIds.add(mainRoom.getId());
        for (BlockPos relative : List.of(LOWER_STOREY_SEED, UPPER_STOREY_SEED)) {
            BlockPos source = helper.absolutePos(relative);
            Village.RoomScanMode mode = relative.equals(LOWER_STOREY_SEED)
                    ? Village.RoomScanMode.ADD_BASEMENT : Village.RoomScanMode.ADD_FLOOR;
            commit(helper, workflow, workflow.analyzeAttachedRoom(source, mode, buildingId));
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
    public static void successiveLowerStairRoomsAttachOneLevelAtATime(GameTestHelper helper) {
        BlockPos main = helper.absolutePos(MAIN_STOREY_SEED);
        VillageManager manager = new VillageManager(helper.getLevel());
        RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());
        commit(helper, workflow, workflow.analyzeBuildingAddition(main));

        Village village = manager.findNearestVillage(main, Village.MERGE_MARGIN).orElseThrow();
        Building mainRoom = village.findInteractionRoomAt(main).orElseThrow();
        int buildingId = village.getStructureFor(mainRoom).orElseThrow().getLogicalBuildingId();
        List<BlockPos> lowerRooms = List.of(
                new BlockPos(9, 9, 11),
                new BlockPos(15, 7, 16),
                new BlockPos(22, 5, 9));
        List<FloorGeometry> attached = new ArrayList<>();

        for (int index = 0; index < lowerRooms.size(); index++) {
            BlockPos source = helper.absolutePos(lowerRooms.get(index));
            RoomScanPlan plan = village.getRoomScanPlan(helper.getLevel(), source);
            helper.assertTrue(plan.mode() == Village.RoomScanMode.ADD_BASEMENT,
                    "lower room " + lowerRooms.get(index) + " offered " + plan.mode());
            helper.assertTrue(plan.targetBuildingId() == buildingId,
                    "lower room " + lowerRooms.get(index) + " targeted another house");
            helper.assertTrue(plan.prospectiveFloorNumber() == -(index + 1),
                    "lower room " + lowerRooms.get(index) + " skipped a stair-delimited level: floor="
                            + plan.prospectiveFloorNumber());
            helper.assertTrue(plan.selectedAttachmentFloor() != null,
                    "lower room " + lowerRooms.get(index) + " has no selected Floor");
            helper.assertTrue(attached.stream().noneMatch(previous ->
                            previous.sameCellPositions(plan.selectedAttachmentFloor().geometry())),
                    "lower room " + lowerRooms.get(index) + " reused an earlier stair-delimited Floor");
            for (int later = index + 1; later < lowerRooms.size(); later++) {
                BlockPos laterSource = helper.absolutePos(lowerRooms.get(later));
                helper.assertTrue(plan.selectedAttachmentFloor().geometry().interactionCellAt(
                                laterSource.getX(), laterSource.getY(), laterSource.getZ()).isEmpty(),
                        "lower room " + lowerRooms.get(index) + " Floor already owns later room "
                                + lowerRooms.get(later) + "; anchor=" + plan.selectedAttachmentFloor().anchorY()
                                + " cellYs=" + plan.selectedAttachmentFloor().geometry().cells().stream()
                                .collect(Collectors.groupingBy(cell -> cell.feet().getY(), Collectors.counting())));
            }

            attached.add(plan.selectedAttachmentFloor().geometry());
            commit(helper, workflow, workflow.analyzeAttachedRoom(
                    source, Village.RoomScanMode.ADD_BASEMENT, buildingId));
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
            RoomScanPlan plan = village.getRoomScanPlan(helper.getLevel(), source);
            helper.assertTrue(plan.mode() == Village.RoomScanMode.ADD_BASEMENT,
                    "staircase did not select the lower Floor at " + relative);
            BuildingScanResult scan = workflow.analyzeAttachedRoom(source, plan.mode(), plan.targetBuildingId());
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
                            .sameCellPositions(plan.selectedAttachmentFloor().geometry()),
                    "staircase commit changed the selected Floor at " + relative);
            Village.RoomScanMode next = village.getRoomScanPlan(helper.getLevel(), source).mode();
            helper.assertTrue(next == Village.RoomScanMode.UPDATE_ROOM || next == Village.RoomScanMode.ADD_ROOM,
                    "registered staircase still offers a new building or attachment at " + relative + ": " + next);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_copied_open_house_confirmation", templateNamespace = "mca",
            template = TEMPLATE, timeoutTicks = 280, skyAccess = true)
    public static void attachmentConfirmationRejectsChangedGeometryAndDuplicateRegistration(GameTestHelper helper) {
        BlockPos main = helper.absolutePos(MAIN_STOREY_SEED);
        BlockPos lower = helper.absolutePos(LOWER_STOREY_SEED);
        VillageManager manager = new VillageManager(helper.getLevel());
        RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());
        commit(helper, workflow, workflow.analyzeBuildingAddition(main));
        Village village = manager.findNearestVillage(main, Village.MERGE_MARGIN).orElseThrow();
        RoomScanPlan expected = village.getRoomScanPlan(helper.getLevel(), lower);
        helper.assertTrue(expected.mode() == Village.RoomScanMode.ADD_BASEMENT,
                "lower Room did not offer an attachment");
        int before = village.getStructures().size();
        BlockState original = helper.getLevel().getBlockState(lower);
        helper.getLevel().setBlockAndUpdate(lower, Blocks.STONE.defaultBlockState());
        BuildingScanResult changed = workflow.analyzeAttachedRoom(
                village, expected, expected.mode(), expected.targetBuildingId());
        helper.assertTrue(changed.result() != Building.validationResult.SUCCESS,
                "confirmation accepted changed Floor geometry");
        helper.assertTrue(village.getStructures().size() == before,
                "rejected confirmation mutated the house");
        helper.getLevel().setBlockAndUpdate(lower, original);
        commit(helper, workflow, workflow.analyzeAttachedRoom(
                village, expected, expected.mode(), expected.targetBuildingId()));
        BuildingScanResult duplicate = workflow.analyzeAttachedRoom(
                village, expected, expected.mode(), expected.targetBuildingId());
        helper.assertTrue(duplicate.result() != Building.validationResult.SUCCESS,
                "stale confirmation accepted an already registered attachment");
        helper.assertTrue(village.getStructures().size() == before + 1,
                "confirmation created a duplicate Structure");
        helper.succeed();
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

        RoomScanPlan lowerPlan = village.getRoomScanPlan(
                helper.getLevel(), helper.absolutePos(LOWER_STOREY_SEED));
        helper.assertTrue(lowerPlan.mode() == Village.RoomScanMode.ADD_BASEMENT,
                "copied lower storey should attach as basement, got " + lowerPlan.mode());
        helper.assertTrue(lowerPlan.targetBuildingId() == logicalBuildingId,
                "copied lower storey targeted a different logical building");

        RoomScanPlan upperPlan = village.getRoomScanPlan(
                helper.getLevel(), helper.absolutePos(UPPER_STOREY_SEED));
        helper.assertTrue(upperPlan.mode() == Village.RoomScanMode.ADD_FLOOR,
                "copied upper storey should attach as floor, got " + upperPlan.mode());
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

        BuildingScanResult upperAddition = workflow.analyzeAttachedRoom(
                upper, Village.RoomScanMode.ADD_FLOOR, buildingId);
        helper.assertTrue(upperAddition.result() == Building.validationResult.SUCCESS,
                "upper registration before basement failed: " + upperAddition.result());
        commit(helper, workflow, upperAddition);
        helper.assertTrue(village.getRoomScanPlan(helper.getLevel(), upper).mode()
                        == Village.RoomScanMode.UPDATE_ROOM,
                "registered upper storey was not selectable before adding the basement");

        RoomScanPlan lowerPlan = village.getRoomScanPlan(helper.getLevel(), lower);
        helper.assertTrue(lowerPlan.mode() == Village.RoomScanMode.ADD_BASEMENT,
                "upper registration changed basement plan to " + lowerPlan.mode());
        helper.assertTrue(lowerPlan.targetBuildingId() == buildingId,
                "upper registration changed basement target to " + lowerPlan.targetBuildingId());

        BuildingScanResult basementAddition = workflow.analyzeAttachedRoom(
                lower, Village.RoomScanMode.ADD_BASEMENT, buildingId);
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
