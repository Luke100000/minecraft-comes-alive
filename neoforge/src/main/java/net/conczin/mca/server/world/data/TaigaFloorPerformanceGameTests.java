package net.conczin.mca.server.world.data;

import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Replays the real vanilla Taiga Temple through MCA's registration path. */
@PrefixGameTestTemplate(false)
public final class TaigaFloorPerformanceGameTests {
    private TaigaFloorPerformanceGameTests() {
    }

    @GameTest(batch = "mca_taiga_floor_registration", templateNamespace = "minecraft",
            template = "village/taiga/houses/taiga_temple_1", timeoutTicks = 200, skyAccess = true)
    public static void taigaTempleRegistersSelectedRoomAndMeasuresScanTime(GameTestHelper helper) {
        var world = helper.getLevel();
        // Builder's template-relative source is (6, 1, 5); GameTest adds one Y to template blocks.
        BlockPos source = helper.absolutePos(new BlockPos(6, 2, 5));
        VillageManager manager = new VillageManager(world);
        RoomWorkflow workflow = new RoomWorkflow(manager, world);

        long coldStart = System.nanoTime();
        BuildingScanResult cold = workflow.analyzeReportedBuildingAddition(source);
        long coldNanos = System.nanoTime() - coldStart;
        helper.assertTrue(cold.result() == Building.validationResult.SUCCESS,
                "Taiga Temple registration scan failed at " + source + ": " + cold.result());

        long warmStart = System.nanoTime();
        BuildingScanResult warm = workflow.analyzeReportedBuildingAddition(source);
        long warmNanos = System.nanoTime() - warmStart;
        helper.assertTrue(warm.result() == Building.validationResult.SUCCESS,
                "Repeated Taiga Temple scan failed: " + warm.result());
        helper.assertTrue(warm.building().getFloorCells().equals(cold.building().getFloorCells()),
                "Taiga Temple room ownership changed between identical scans");

        long commitStart = System.nanoTime();
        String type = warm.isAmbiguous() ? warm.matchingTypes().getFirst() : null;
        RoomWorkflow.Outcome outcome = workflow.commitAddition(warm, type);
        long commitNanos = System.nanoTime() - commitStart;
        helper.assertTrue(outcome.status() == RoomWorkflow.Status.COMMITTED,
                "Taiga Temple registration commit failed: " + outcome.result());
        Village village = manager.findNearestVillage(source, Village.MERGE_MARGIN).orElseThrow();
        List<Building> rooms = village.getRooms().toList();
        helper.assertTrue(rooms.size() == 1,
                "Taiga Temple registration should create one selected room, got " + rooms.size());
        helper.assertTrue(rooms.getFirst().getFloorCells().equals(warm.building().getFloorCells()),
                "Taiga Temple registration did not preserve the selected room geometry");

        MCA.LOGGER.info("[MCA Taiga floor registration] coldScanMs={} warmScanMs={} commitMs={} floorCells={}",
                coldNanos / 1_000_000.0, warmNanos / 1_000_000.0,
                commitNanos / 1_000_000.0, warm.building().getFloorCells().size());
        helper.succeed();
    }
}
