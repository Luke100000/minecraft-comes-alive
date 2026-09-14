package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
    private static final BlockPos UPPER_STOREY_SEED = new BlockPos(11, 16, 13);

    // The structure block itself sits one block below the template in 1.21.1,
    // so helper-relative Y is template-NBT Y + 1.
    private static final List<BlockPos> STOREY_SEEDS = List.of(
            LOWER_STOREY_SEED,
            MAIN_STOREY_SEED,
            UPPER_STOREY_SEED);

    private CopiedOpenHouseGameTests() {
    }

    @GameTest(batch = "mca_copied_open_house_rooms", templateNamespace = "mca",
            template = TEMPLATE, timeoutTicks = 240)
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
            template = TEMPLATE, timeoutTicks = 280)
    public static void initialRegistrationPreservesFloorButRegistersOnlySelectedRoom(GameTestHelper helper) {
        Config config = Config.getInstance();
        BlockPos seed = helper.absolutePos(MAIN_STOREY_SEED);
        SelectedFloorScanner.Result floorScan = SelectedFloorScanner.scan(
                helper.getLevel(), seed, config.maxBuildingSize, config.maxBuildingRadius);
        helper.assertTrue(floorScan.result() == Building.validationResult.SUCCESS,
                "copied main floor scan failed: " + floorScan.result());

        List<RoomPartitioner.Component> components = BuildingRoomScanner.components(helper.getLevel(), floorScan);
        helper.assertTrue(components.size() > 1,
                "copied main Floor no longer reproduces multiple Rooms: " + components.size());
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
        helper.assertTrue(!rooms.getFirst().getFloorCells().equals(floorCells),
                "initial registration incorrectly claimed every Room component on the Floor");
        helper.succeed();
    }

    @GameTest(batch = "mca_copied_open_house_add_room", templateNamespace = "mca",
            template = TEMPLATE, timeoutTicks = 280)
    public static void registeredMainRoomLeavesSiblingAsAddRoom(GameTestHelper helper) {
        Config config = Config.getInstance();
        BlockPos mainSeed = helper.absolutePos(MAIN_STOREY_SEED);
        SelectedFloorScanner.Result floorScan = SelectedFloorScanner.scan(
                helper.getLevel(), mainSeed, config.maxBuildingSize, config.maxBuildingRadius);
        helper.assertTrue(floorScan.result() == Building.validationResult.SUCCESS,
                "copied main-floor scan failed: " + floorScan.result());

        List<RoomPartitioner.Component> components = BuildingRoomScanner.components(helper.getLevel(), floorScan);
        RoomPartitioner.Component selected = RoomPartitioner.select(mainSeed, floorScan.floor(), components);
        helper.assertTrue(selected != null, "copied main-floor seed did not select a Room component");
        RoomPartitioner.Component sibling = components.stream()
                .filter(component -> component != selected)
                .findFirst().orElseThrow();
        BlockPos siblingSeed = sibling.nearestCell(mainSeed);

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
        RoomScanPlan siblingPlan = village.getRoomScanPlan(helper.getLevel(), siblingSeed);
        helper.assertTrue(siblingPlan.mode() == Village.RoomScanMode.ADD_ROOM,
                "unregistered copied sibling should offer Add Room, got " + siblingPlan.mode());
        helper.assertTrue(siblingPlan.targetStructureId() == structure.getId(),
                "Add Room targeted a different Structure");
        helper.assertTrue(siblingPlan.targetFloorId() == mainRoom.getFloorId(),
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

    @GameTest(batch = "mca_copied_open_house_storeys", templateNamespace = "mca",
            template = TEMPLATE, timeoutTicks = 280)
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
}
