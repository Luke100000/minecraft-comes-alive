package net.conczin.mca.server.world.data;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.conczin.mca.MCA;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@GameTestHolder("mca")
@PrefixGameTestTemplate(false)
public final class ReportedStairHouseGameTests {
    private ReportedStairHouseGameTests() {
    }

    @GameTest(batch = "mca_uneven_basement", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 120)
    public static void descendingBasementContinuationAddsRoomOnExistingFloor(GameTestHelper helper) {
        // Minimal stone/door geometry from (-200, 98, 134) in the reported New World.
        BlockPos origin = helper.absolutePos(new BlockPos(5, 2, 5));
        var level = helper.getLevel();
        VillageManager manager = createBasementVillage(helper, origin, false);
        Village village = manager.findNearestVillage(origin, Village.MERGE_MARGIN).orElseThrow();
        RoomWorkflow workflow = new RoomWorkflow(manager, level);
        BlockPos bottom = origin.offset(0, 0, 1);
        BlockPos nextStep = origin.offset(1, 1, 1);
        for (BlockPos source : List.of(nextStep, bottom, bottom.above(), origin.offset(0, 1, 2))) {
            var analysis = RoomScanPlanner.analyze(village, level, source);
            helper.assertTrue(analysis.plan().mode() == Village.RoomScanMode.ADD_ROOM
                            && analysis.plan().targetStructureId() == 69 && analysis.plan().targetFloorId() == 0,
                    "descending basement selected " + analysis.plan().mode() + " at " + source.subtract(origin)
                            + "; scanSeed=" + (analysis.observation() == null ? null
                            : analysis.observation().seed().subtract(origin))
                            + "; sourceCell=" + (analysis.observation() == null ? null
                            : analysis.observation().scan().floor().cellAt(source))
                            + "; cells=" + (analysis.observation() == null ? null
                            : analysis.observation().scan().floor().cells().stream()
                            .map(cell -> cell.feet().subtract(origin)).toList()));
            helper.assertTrue(analysis.observation() != null
                            && analysis.observation().scan().floor().anchorY() == origin.getY() + 3,
                    "descending basement changed its canonical anchor");
            BuildingScanResult addition = workflow.analyzeRoom(source);
            helper.assertTrue(addition.result() == Building.validationResult.SUCCESS,
                    "descending basement room addition failed: " + addition.result());
            StructureScanner.Result initial = StructureScanner.scanNewStructure(level, source, List.of());
            helper.assertTrue(initial.result() == Building.validationResult.SUCCESS
                            && initial.scannedFloor().anchorY() == origin.getY() + 3,
                    "initial registration from a basement step or edge failed: " + initial.result());
        }
        var grounded = StructureScanner.observeFloor(level, bottom, List.of()).orElseThrow();
        var airborne = StructureScanner.observeFloor(level, bottom.above(2), List.of()).orElse(null);
        helper.assertTrue(airborne != null && airborne.seed().equals(grounded.seed())
                        && airborne.scan().floor().sameExactGeometry(grounded.scan().floor()),
                "airborne basement selection did not resolve the same floor below");
        helper.assertTrue(StructureScanner.observeFloor(level, bottom.above(3), List.of()).isEmpty(),
                "standing normalization searched through a solid ceiling");
        var addition = workflow.analyzeRoom(bottom.above());
        var committed = workflow.commitAddition(addition,
                addition.isAmbiguous() ? addition.matchingTypes().getFirst() : null);
        helper.assertTrue(committed.status() == RoomWorkflow.Status.COMMITTED,
                "descending basement room did not commit: " + committed.result());
        Building newRoom = village.findInteractionRoomAt(bottom).orElseThrow();
        helper.assertTrue(newRoom.getStructureId() == 69 && newRoom.getFloorId() == 0
                        && newRoom.getId() != 70 && village.getStructures().size() == 2,
                "descending basement created a new floor instead of a room");
        Village reloaded = new Village(village.save(), level);
        helper.assertTrue(reloaded.getStructure(69).orElseThrow().getFloor(0).orElseThrow().floorNumber() == -1,
                "room addition changed the existing basement number");
        for (BlockPos source : List.of(nextStep, bottom, bottom.above(), origin.offset(0, 1, 2))) {
            RoomScanPlan plan = reloaded.getRoomScanPlan(null, source);
            helper.assertTrue(plan.mode() == Village.RoomScanMode.UPDATE_ROOM
                            && plan.currentRoom().orElseThrow().getId() == newRoom.getId(),
                    "descending basement lost its room identity after save/load");
        }
        helper.assertTrue(reloaded.findInteractionRoomAt(origin.offset(5, 3, 1)).orElseThrow().getId() == 70,
                "basement continuation replaced the registered room across the door");
        helper.succeed();
    }

    @GameTest(batch = "mca_basement_ladder_update", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 120)
    public static void basementLadderKeepsFloorCellAndUpdatesItsRoomAfterExpansion(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(5, 2, 5));
        var level = helper.getLevel();
        VillageManager manager = createBasementVillage(helper, origin, true);
        Village village = manager.findNearestVillage(origin, Village.MERGE_MARGIN).orElseThrow();
        RoomWorkflow workflow = new RoomWorkflow(manager, level);
        BuildingScanResult addition = workflow.analyzeRoom(origin.offset(0, 0, 1));
        helper.assertTrue(addition.result() == Building.validationResult.SUCCESS,
                "expanded basement did not scan: " + addition.result());
        helper.assertTrue(workflow.commitAddition(addition,
                        addition.isAmbiguous() ? addition.matchingTypes().getFirst() : null).status()
                        == RoomWorkflow.Status.COMMITTED,
                "expanded basement did not register");
        BlockPos ladder = origin.offset(4, 3, 3);
        var floor = village.getStructure(69).orElseThrow().getFloor(0).orElseThrow();
        var lowerScan = SelectedFloorScanner.scan(level, origin.offset(0, 0, 1), 2048, 32);
        var upperScan = SelectedFloorScanner.scan(level, origin.offset(5, 3, 1), 2048, 32);
        helper.assertTrue(lowerScan.result() == Building.validationResult.SUCCESS
                        && upperScan.result() == Building.validationResult.SUCCESS
                        && lowerScan.floor().sameExactGeometry(upperScan.floor()),
                "fresh scans from both basement Rooms disagree about their physical Floor");
        helper.assertTrue(floor.geometry().cellAt(ladder).isPresent(),
                "supported basement ladder entry is not a floor cell after expansion");
        helper.assertTrue(floor.geometry().cellAt(ladder.above()).isEmpty(),
                "unsupported intermediate ladder rung became a floor cell");
        RoomScanPlan ladderPlan = village.getRoomScanPlan(level, ladder);
        helper.assertTrue(ladderPlan.mode() == Village.RoomScanMode.UPDATE_ROOM
                        && ladderPlan.currentRoom().orElseThrow().getId() == 70,
                "newly observed ladder entry lost its persisted connector room");
        var fresh = StructureScanner.scanExistingFloor(level, village.getStructure(69).orElseThrow(),
                floor, ladder, village.getStructures().values());
        helper.assertTrue(fresh.result() == Building.validationResult.SUCCESS
                        && floor.geometry().cells().stream().allMatch(cell ->
                        fresh.scannedFloor().cellAt(cell.feet()).isPresent()),
                "ladder refresh dropped unchanged cells from the lower room");
        var updated = workflow.scanRoom(ladder, 70, null);
        helper.assertTrue(updated.status() == RoomWorkflow.Status.COMMITTED,
                "basement ladder update returned " + updated.result());
        helper.assertTrue(village.getStructure(69).orElseThrow().getFloor(0).orElseThrow()
                        .geometry().cellAt(ladder).isPresent(),
                "supported basement ladder entry is not a floor cell");
        helper.assertTrue(village.findInteractionRoomAt(ladder).orElseThrow().getId() == 70,
                "basement ladder changed its registered room");
        Village reloaded = new Village(village.save(), level);
        RoomScanPlan reloadedPlan = reloaded.getRoomScanPlan(null, ladder);
        helper.assertTrue(reloadedPlan.mode() == Village.RoomScanMode.UPDATE_ROOM
                        && reloadedPlan.currentRoom().orElseThrow().getId() == 70,
                "basement ladder lost room lookup on reload");
        helper.succeed();
    }

    private static VillageManager createBasementVillage(GameTestHelper helper, BlockPos origin, boolean expanded) {
        var level = helper.getLevel();
        for (BlockPos relative : BlockPos.betweenClosed(-2, -1, -1, 6, 8, 4)) {
            level.setBlock(origin.offset(relative), Blocks.STONE.defaultBlockState(), 2);
        }
        int[][] air = {
                {0, 0, 1}, {-1, 1, 1}, {0, 1, 1}, {1, 1, 0}, {1, 1, 1}, {0, 1, 2}, {0, 2, 2},
                {-1, 2, 1}, {0, 2, 1}, {1, 2, 0}, {1, 2, 1}, {2, 2, 0}, {2, 2, 1}, {1, 2, 2},
                {0, 3, 0}, {1, 3, 0}, {1, 3, 1}, {2, 3, 0}, {2, 3, 1},
                {2, 4, 0}, {1, 4, 1}, {2, 4, 1},
                {4, 3, 0}, {4, 3, 1}, {4, 3, 2}, {5, 3, 0}, {5, 3, 1}, {5, 3, 2}, {5, 3, 3},
                {4, 4, 0}, {4, 4, 1}, {4, 4, 2}, {5, 4, 0}, {5, 4, 1}, {5, 4, 2}, {5, 4, 3},
                {4, 6, 0}, {4, 6, 1}, {5, 6, 0}, {5, 6, 1},
                {4, 7, 0}, {4, 7, 1}, {5, 7, 0}, {5, 7, 1}
        };
        for (int[] pos : air) {
            level.setBlock(origin.offset(pos[0], pos[1], pos[2]), Blocks.AIR.defaultBlockState(), 2);
        }
        if (expanded) {
            for (BlockPos feet : List.of(new BlockPos(-1, 1, 2), new BlockPos(1, 1, 2),
                    new BlockPos(0, 1, 3), new BlockPos(1, 1, 3))) {
                level.setBlock(origin.offset(feet), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(origin.offset(feet).above(), Blocks.AIR.defaultBlockState(), 2);
            }
            var ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
            level.setBlock(origin.offset(4, 3, 3), ladder, 2);
            level.setBlock(origin.offset(4, 4, 3), ladder, 2);
            level.setBlock(origin.offset(4, 5, 3), Blocks.AIR.defaultBlockState(), 2);
        }
        BlockPos doorPos = origin.offset(3, 3, 0);
        var door = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.WEST)
                .setValue(DoorBlock.OPEN, true)
                .setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT);
        level.setBlock(doorPos, door, 2);
        level.setBlock(doorPos.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);

        Village village = new Village(0, level);
        List<FloorGeometry.Cell> groundCells = List.of(new BlockPos(4, 6, 0), new BlockPos(4, 6, 1),
                        new BlockPos(5, 6, 0), new BlockPos(5, 6, 1)).stream()
                .map(pos -> new FloorGeometry.Cell(origin.offset(pos), origin.getY() + 8)).toList();
        registerBasementFixtureFloor(village, 67, 68, 0, new FloorGeometry(groundCells, List.of()), groundCells);
        int[][] saved = {
                {1, 1, 0, 4}, {1, 1, 1, 5}, {2, 2, 0, 5}, {2, 2, 1, 5}, {3, 3, 0, 4},
                {4, 3, 0, 5}, {4, 3, 1, 5}, {4, 3, 2, 5}, {5, 3, 0, 5}, {5, 3, 1, 5}, {5, 3, 2, 5}, {5, 3, 3, 5}
        };
        List<FloorGeometry.Cell> cells = Arrays.stream(saved)
                .map(pos -> new FloorGeometry.Cell(origin.offset(pos[0], pos[1], pos[2]), origin.getY() + pos[3]))
                .toList();
        var markers = new java.util.ArrayList<FloorConnector.Marker>();
        markers.add(new FloorConnector.Marker(doorPos, FloorConnector.Type.DOOR, doorPos, Direction.EAST));
        if (expanded) {
            markers.add(new FloorConnector.Marker(origin.offset(4, 3, 3),
                    FloorConnector.Type.LADDER, origin.offset(5, 3, 3)));
        }
        FloorGeometry basement = new FloorGeometry(cells, markers);
        List<FloorGeometry.Cell> existingRoom = cells.stream().filter(cell ->
                cell.feet().getX() >= origin.getX() + 4 && cell.feet().getY() == origin.getY() + 3).toList();
        registerBasementFixtureFloor(village, 69, 70, -1, basement, existingRoom);
        var villages = new ListTag();
        villages.add(village.save());
        var managerTag = new CompoundTag();
        managerTag.put("villages", villages);
        managerTag.putInt("lastBuildingId", 100);
        return new VillageManager(level, managerTag);
    }

    private static void registerBasementFixtureFloor(Village village, int structureId, int roomId, int number,
                                                     FloorGeometry geometry, List<FloorGeometry.Cell> roomCells) {
        BlockPos source = roomCells.getFirst().feet();
        Structure structure = new Structure(structureId, source, List.of(new StructureFloor(0, number, geometry)));
        structure.setLogicalBuildingId(67);
        Building room = new Building(source);
        room.setId(roomId);
        room.setStructureId(structureId);
        room.setFloorId(0);
        room.setType("house");
        room.setGeometry(structure.getRawPos0(), structure.getRawPos1(),
                roomCells.stream().map(FloorGeometry.Cell::feet).toList());
        village.registerStructure(structure, room);
    }

    @GameTest(batch = "mca_reported_stair_initial_registration", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 120)
    public static void savedMiddleStairCanRegisterItsSelectedFloor(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        BlockPos origin = placeSavedHouse(helper);
        BlockPos stair = origin.offset(4, 4, 2);
        for (BlockPos source : List.of(stair, stair.above())) {
            VillageManager manager = new VillageManager(helper.getLevel());
            RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());
            BuildingScanResult scan = workflow.analyzeBuildingAddition(source);
            helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
                    "initial stair registration failed at " + source.subtract(origin) + ": " + scan.result());
            var committed = workflow.commitAddition(scan,
                    scan.isAmbiguous() ? scan.matchingTypes().getFirst() : null);
            helper.assertTrue(committed.status() == RoomWorkflow.Status.COMMITTED,
                    "initial stair registration did not commit: " + committed.result());
            Village village = manager.findNearestVillage(source, Village.MERGE_MARGIN).orElseThrow();
            Building room = village.findInteractionRoomAt(origin.offset(3, 6, 2)).orElseThrow();
            RoomScanPlan plan = village.getRoomScanPlan(helper.getLevel(), source);
            helper.assertTrue(plan.mode() == Village.RoomScanMode.UPDATE_ROOM
                            && plan.currentRoom().orElseThrow().getId() == room.getId(),
                    "initial stair registration did not resolve back to its selected Room");
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_reported_stair_house", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 120)
    public static void savedStaircaseUpdatesTheLowerRoomAndAddsTheUpperFloor(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        BlockPos origin = placeSavedHouse(helper);
        var level = helper.getLevel();

        VillageManager manager = new VillageManager(level);
        RoomWorkflow workflow = new RoomWorkflow(manager, level);
        BlockPos lowerSeed = origin.offset(7, 2, 4);
        BuildingScanResult lowerScan = workflow.analyzeBuildingAddition(lowerSeed);
        helper.assertTrue(lowerScan.result() == Building.validationResult.SUCCESS,
                "saved lower room did not scan: " + lowerScan.result());
        var outcome = workflow.commitAddition(lowerScan,
                lowerScan.isAmbiguous() ? lowerScan.matchingTypes().getFirst() : null);
        helper.assertTrue(outcome.status() == RoomWorkflow.Status.COMMITTED,
                "saved lower room did not register: " + outcome.result());
        Village village = manager.findNearestVillage(lowerSeed, Village.MERGE_MARGIN).orElseThrow();
        Building lowerRoom = village.findInteractionRoomAt(lowerSeed).orElseThrow();
        BlockPos source = origin.offset(4, 4, 2);
        BlockPos lowerStair = origin.offset(5, 4, 2);
        for (BlockPos position : List.of(origin.offset(6, 3, 2), lowerStair, lowerStair.below())) {
            RoomScanPlan plan = village.getRoomScanPlan(level, position);
            helper.assertTrue(plan.mode() == Village.RoomScanMode.UPDATE_ROOM,
                    "lower stair " + position.subtract(origin) + " selected " + plan.mode());
            helper.assertTrue(plan.currentRoom().orElseThrow().getId() == lowerRoom.getId(),
                    "lower stair lost its Room identity");
        }
        var update = workflow.scanRoom(lowerStair, lowerRoom.getId(), null);
        helper.assertTrue(update.status() == RoomWorkflow.Status.COMMITTED,
                "saved lower stair update failed: " + update.result());

        BlockPos upperLanding = origin.offset(3, 6, 2);
        SelectedFloorScanner.Result lowerGeometry = SelectedFloorScanner.scan(level, lowerSeed, 256, 24);
        SelectedFloorScanner.Result upperGeometry = SelectedFloorScanner.scan(level, upperLanding, 256, 24);
        helper.assertTrue(lowerGeometry.result() == Building.validationResult.SUCCESS
                        && upperGeometry.result() == Building.validationResult.SUCCESS,
                "saved staircase floor ownership scans failed");
        for (BlockPos feet : List.of(origin.offset(6, 3, 2), lowerStair)) {
            helper.assertTrue(lowerGeometry.floor().cellAt(feet).isPresent()
                            && upperGeometry.floor().cellAt(feet).isEmpty(),
                    "saved lower half was not exclusively owned downstairs");
        }
        for (BlockPos feet : List.of(source.above(), upperLanding)) {
            helper.assertTrue(upperGeometry.floor().cellAt(feet).isPresent()
                            && lowerGeometry.floor().cellAt(feet).isEmpty(),
                    "saved upper half was not exclusively owned upstairs");
        }
        RoomScanPlan additionPlan = village.getRoomScanPlan(level, upperLanding);
        for (BlockPos position : List.of(source, source.above(), upperLanding, origin.offset(2, 6, 2))) {
            RoomScanPlan plan = village.getRoomScanPlan(level, position);
            helper.assertTrue(plan.mode() == Village.RoomScanMode.ADD_ATTACHMENT,
                    "upper landing selected " + plan.mode() + " instead of Add Floor");
            helper.assertTrue(plan.targetBuildingId() == village.getLogicalBuildingId(lowerRoom.getStructureId()),
                    "upper landing targeted another building");
            helper.assertTrue(plan.prospectiveFloorNumber() == 1,
                    "upper landing did not select floor 1");
        }
        BuildingScanResult addition = workflow.analyzeAttachedRoom(upperLanding, additionPlan.targetBuildingId());
        helper.assertTrue(addition.result() == Building.validationResult.SUCCESS,
                "saved upper floor attachment failed: " + addition.result());
        var added = workflow.commitAddition(addition,
                addition.isAmbiguous() ? addition.matchingTypes().getFirst() : null);
        helper.assertTrue(added.status() == RoomWorkflow.Status.COMMITTED,
                "saved upper floor did not commit: " + added.result());
        Building upperRoom = village.findInteractionRoomAt(upperLanding).orElseThrow();
        for (Village persisted : List.of(village, new Village(village.save(), level))) {
            for (BlockPos position : List.of(origin.offset(6, 3, 2), lowerStair)) {
                Building owner = persisted.findInteractionRoomAt(position).orElseThrow();
                helper.assertTrue(owner.getId() == lowerRoom.getId(),
                        "registered lower stair was reassigned to the upper Room");
            }
            for (BlockPos position : List.of(source, source.above(), upperLanding, origin.offset(2, 6, 2))) {
                RoomScanPlan plan = persisted.getRoomScanPlan(level, position);
                helper.assertTrue(plan.mode() == Village.RoomScanMode.UPDATE_ROOM,
                        "registered upper staircase selected " + plan.mode());
                helper.assertTrue(plan.currentRoom().orElseThrow().getId() == upperRoom.getId(),
                        "registered upper staircase lost its Room identity");
            }
        }
        int logicalBuildingId = village.getLogicalBuildingId(upperRoom.getStructureId());
        int upperStructureId = upperRoom.getStructureId();
        int upperFloorId = upperRoom.getFloorId();
        var landingState = level.getBlockState(upperLanding.below());
        helper.assertTrue(manager.removeFloor(upperLanding, 1, logicalBuildingId, upperStructureId, upperFloorId)
                        == VillageManager.BuildingEditResult.NO_FLOOR,
                "occupied upper floor should reject removal");
        helper.assertTrue(manager.removeRoom(upperLanding, upperRoom.getId())
                        == VillageManager.BuildingEditResult.SUCCESS,
                "upper Room could not be removed before removing its Floor");
        RoomScanPlan emptyFloorPlan = village.getRoomScanPlan(level, upperLanding);
        helper.assertTrue(emptyFloorPlan.mode() == Village.RoomScanMode.ADD_ROOM
                        && emptyFloorPlan.targetStructureId() == upperStructureId
                        && emptyFloorPlan.targetFloorId() == upperFloorId,
                "empty upper landing lost its persisted Floor identity");
        helper.assertTrue(manager.removeFloor(upperLanding, 1, logicalBuildingId, upperStructureId, upperFloorId)
                        == VillageManager.BuildingEditResult.SUCCESS,
                "empty upper Floor could not be removed without a Room");
        helper.assertTrue(village.getStructure(upperStructureId)
                        .flatMap(structure -> structure.getFloor(upperFloorId)).isEmpty(),
                "removed upper Floor is still registered");
        helper.assertTrue(village.getBuilding(lowerRoom.getId()).isPresent(),
                "removing upper Floor removed the lower Room");
        helper.assertTrue(level.getBlockState(upperLanding.below()).equals(landingState),
                "floor registration removal changed staircase blocks");
        helper.succeed();
    }

    @GameTest(batch = "mca_reported_short_ladder_hole", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 120)
    public static void savedHouseShortLadderHoleUpdatesItsExistingRoom(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        BlockPos origin = placeSavedHouse(helper);
        var level = helper.getLevel();
        // Saved-world opening (-196, 104, 136), interaction (-196, 103, 136), top ladder Y=102.
        BlockPos opening = origin.offset(2, 2, 4);
        level.setBlock(opening.below(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(opening.below(2), Blocks.AIR.defaultBlockState(), 3);
        VillageManager manager = new VillageManager(level);
        RoomWorkflow workflow = new RoomWorkflow(manager, level);
        BlockPos roomSeed = origin.offset(7, 2, 4);
        BuildingScanResult original = workflow.analyzeBuildingAddition(roomSeed);
        helper.assertTrue(original.result() == Building.validationResult.SUCCESS,
                "saved house with an ordinary air hole did not scan: " + original.result());
        var committed = workflow.commitAddition(original,
                original.isAmbiguous() ? original.matchingTypes().getFirst() : null);
        helper.assertTrue(committed.status() == RoomWorkflow.Status.COMMITTED,
                "saved house with an ordinary air hole did not register");
        Village village = manager.findNearestVillage(roomSeed, Village.MERGE_MARGIN).orElseThrow();
        Building room = village.findInteractionRoomAt(roomSeed).orElseThrow();
        helper.assertTrue(!room.ownsFloorCell(opening),
                "ordinary air hole acquired Room ownership without a ladder");

        var ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
        for (int depth = 2; depth <= 3; depth++) {
            level.setBlock(opening.below(depth).west(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(opening.below(depth), ladder, 3);
        }
        for (BlockPos source : List.of(opening.below(), opening)) {
            RoomScanPlan plan = village.getRoomScanPlan(level, source);
            helper.assertTrue(plan.mode() == Village.RoomScanMode.UPDATE_ROOM,
                    "saved short ladder hole selected " + plan.mode() + " instead of Update Room");
            helper.assertTrue(plan.currentRoom().orElseThrow().getId() == room.getId(),
                    "saved short ladder hole selected another Room");
        }
        var updated = workflow.scanRoom(opening.below(), room.getId(), null);
        helper.assertTrue(updated.status() == RoomWorkflow.Status.COMMITTED,
                "saved short ladder hole update failed: " + updated.result());
        helper.assertTrue(village.getBuilding(room.getId()).orElseThrow().ownsFloorCell(opening),
                "saved short ladder hole was not persisted in its Room");
        Village reloaded = new Village(village.save(), level);
        RoomScanPlan persistedPlan = reloaded.getRoomScanPlan(null, opening.below());
        helper.assertTrue(persistedPlan.mode() == Village.RoomScanMode.UPDATE_ROOM
                        && persistedPlan.currentRoom().orElseThrow().getId() == room.getId(),
                "saved short ladder hole lost its Room identity after save/load");
        helper.succeed();
    }

    private static BlockPos placeSavedHouse(GameTestHelper helper) throws IOException, CommandSyntaxException {
        // Saved world origin (-198, 102, 132); the failing interaction was (-194, 106, 134).
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 4));
        var level = helper.getLevel();
        var resource = level.getServer().getResourceManager()
                .getResourceOrThrow(MCA.locate("gametest/reported_stair_house.snbt"));
        StructureTemplate template = new StructureTemplate();
        try (var input = resource.open()) {
            template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK),
                    TagParser.parseCompoundFully(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
        template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
        return origin;
    }
}
