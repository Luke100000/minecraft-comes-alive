package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Geometry from the September 19 diagnose traces, without saved entities or inventories. */
@GameTestHolder("mca")
@PrefixGameTestTemplate(false)
public final class ReportedFloorInteractionGameTests {
    private static final String TEMPLATE = "gametest/reported_floor_transitions";

    private ReportedFloorInteractionGameTests() {
    }

    @GameTest(batch = "mca_reported_stair_interaction", templateNamespace = "mca",
            template = TEMPLATE, timeoutTicks = 280, skyAccess = true)
    public static void reportedUpperStairResolvesRegisteredRoomThroughReload(GameTestHelper helper) {
        BlockPos main = reportedPosition(helper, 14, 131, 11);
        BlockPos upper = reportedPosition(helper, 9, 135, 14);
        BlockPos stair = reportedPosition(helper, 10, 135, 15);
        VillageManager manager = new VillageManager(helper.getLevel());
        RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());
        commit(helper, workflow, workflow.analyzeBuildingAddition(main));
        Village village = manager.findNearestVillage(main, Village.MERGE_MARGIN).orElseThrow();
        RoomScanPlan addition = village.getRoomScanPlan(helper.getLevel(), upper);
        commit(helper, workflow, workflow.analyzeAttachedRoom(upper, addition.mode(), addition.targetBuildingId()));
        Building room = village.findInteractionRoomAt(upper).orElseThrow();
        StructureFloor floor = village.getStructureFor(room).orElseThrow()
                .getFloor(room.getFloorId()).orElseThrow();

        helper.assertTrue(helper.getLevel().getBlockState(stair.below()).is(Blocks.OAK_STAIRS),
                "reported upper stair is missing");
        helper.assertTrue(floor.geometry().cellAt(stair).isPresent(),
                "upper Floor omitted the supported stair cell and left a geometry hole");
        assertRegisteredInteraction(helper, village, workflow, room, stair);
        assertRegisteredInteraction(helper, village, workflow, room, stair.below());
        assertRegisteredInteraction(helper, new Village(village.save(), helper.getLevel()), workflow, room, stair);
        helper.succeed();
    }

    @GameTest(batch = "mca_reported_hatch_interaction", templateNamespace = "mca",
            template = TEMPLATE, timeoutTicks = 280, skyAccess = true)
    public static void reportedHatchLookupAgreesWithRoomPlanOpenClosedAndReloaded(GameTestHelper helper) {
        BlockPos main = reportedPosition(helper, 14, 131, 11);
        BlockPos landing = reportedPosition(helper, 15, 127, 15);
        BlockPos exit = reportedPosition(helper, 16, 127, 15);
        BlockPos hatch = exit.below();
        VillageManager manager = new VillageManager(helper.getLevel());
        RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());
        commit(helper, workflow, workflow.analyzeBuildingAddition(main));
        Village village = manager.findNearestVillage(main, Village.MERGE_MARGIN).orElseThrow();
        RoomScanPlan addition = village.getRoomScanPlan(helper.getLevel(), landing);
        helper.assertTrue(addition.mode() == Village.RoomScanMode.ADD_BASEMENT,
                "reported basement selected " + addition.mode());
        commit(helper, workflow,
                workflow.analyzeAttachedRoom(landing, addition.mode(), addition.targetBuildingId()));
        Building room = village.findInteractionRoomAt(landing).orElseThrow();
        StructureFloor floor = village.getStructureFor(room).orElseThrow()
                .getFloor(room.getFloorId()).orElseThrow();
        helper.assertTrue(helper.getLevel().getBlockState(hatch).is(Blocks.OAK_TRAPDOOR),
                "reported ladder hatch is missing");
        helper.assertTrue(floor.geometry().cellAt(exit).isPresent(),
                "basement Floor omitted the supported trapdoor exit cell and left a geometry hole");
        for (boolean open : List.of(false, true)) {
            helper.getLevel().setBlock(hatch,
                    helper.getLevel().getBlockState(hatch).setValue(TrapDoorBlock.OPEN, open), 3);
            for (BlockPos source : List.of(hatch, exit)) {
                assertRegisteredInteraction(helper, village, workflow, room, source);
                assertRegisteredInteraction(helper, new Village(village.save(), helper.getLevel()),
                        workflow, room, source);
            }
        }
        helper.succeed();
    }

    private static void assertRegisteredInteraction(GameTestHelper helper, Village village,
                                                     RoomWorkflow workflow, Building expected, BlockPos source) {
        RoomScanPlanner.Analysis analysis = RoomScanPlanner.analyze(village, helper.getLevel(), source);
        RoomScanPlan plan = analysis.plan();
        helper.assertTrue(plan.mode() == Village.RoomScanMode.UPDATE_ROOM,
                "reported interaction " + source + " selected " + plan.mode() + " seed=" + plan.scanSeed()
                        + " adjacent=" + (analysis.observation() == null ? "none"
                        : analysis.observation().scan().adjacentFloorSeeds()));
        helper.assertTrue(plan.currentRoom().orElseThrow().getId() == expected.getId(),
                "interaction selected a different Room");
        helper.assertTrue(village.findInteractionRoomAt(source).map(Building::getId).orElse(-1) == expected.getId(),
                "interaction lookup disagrees with UPDATE_ROOM at " + source);
        helper.assertTrue(village.getInteractionStructureAt(source).map(Structure::getId).orElse(-1)
                        == expected.getStructureId(),
                "interaction Structure lookup disagrees with UPDATE_ROOM at " + source);
        Structure structure = village.getStructureFor(expected).orElseThrow();
        StructureFloor persisted = structure.getFloor(expected.getFloorId()).orElseThrow();
        StructureScanner.Result fresh = StructureScanner.scanExistingFloor(
                helper.getLevel(), structure, persisted, source, village.getStructures().values());
        helper.assertTrue(fresh.result() == Building.validationResult.SUCCESS,
                "fresh registered Floor scan failed at " + source + ": " + fresh.result());
        helper.assertTrue(fresh.scannedFloor().sameCellPositions(persisted.geometry()),
                "fresh registered Floor geometry changed at " + source
                        + ": persistedCells=" + persisted.geometry().cells().size()
                        + " freshCells=" + fresh.scannedFloor().cells().size());
        RegisteredRoomUpdate update = workflow.analyzeRegisteredRoomUpdate(village, expected.getId(), source);
        helper.assertTrue(update.result() == Building.validationResult.SUCCESS,
                "reported interaction update failed: " + update.result());
    }

    private static BlockPos reportedPosition(GameTestHelper helper, int x, int y, int z) {
        // The saved template starts at world Y=120; GameTest's structure block is below its origin.
        return helper.absolutePos(new BlockPos(x, y - 120 + 1, z));
    }

    private static void commit(GameTestHelper helper, RoomWorkflow workflow, BuildingScanResult scan) {
        helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
                "reported house registration failed: " + scan.result());
        String type = scan.isAmbiguous() ? scan.matchingTypes().getFirst() : null;
        helper.assertTrue(workflow.commitAddition(scan, type).status() == RoomWorkflow.Status.COMMITTED,
                "reported house registration did not commit");
    }
}
