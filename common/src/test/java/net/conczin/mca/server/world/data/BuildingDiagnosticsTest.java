package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.conczin.mca.server.world.data.BuildingDiagnostics.StructuralPosition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BuildingDiagnosticsTest {
    @Test
    void refreshedPersistedFloorAnchorDoesNotProveASeparateUpperFloor() throws Exception {
        BlockPos source = new BlockPos(0, 76, 0);
        FloorGeometry geometry = new FloorGeometry(Set.of(new FloorGeometry.Cell(source.above(), 88)), List.of());
        Structure structure = new Structure(80, source, List.of(new StructureFloor(0, 0, 76, geometry)));
        Building room = new Building(source);
        room.setFloorId(0);
        var verdict = BuildingDiagnostics.class.getDeclaredMethod("verdict", StructuralPosition.class,
                String.class, Building.validationResult.class, Structure.class, java.util.Collection.class,
                Building.class, FloorGeometry.class, BlockPos.class, net.minecraft.server.level.ServerLevel.class);
        verdict.setAccessible(true);

        String message = (String) verdict.invoke(null, StructuralPosition.REGISTERED_ROOM, "UPDATE_ROOM",
                Building.validationResult.SUCCESS, structure, List.of(room), room, geometry, source, null);

        assertFalse(message.contains("SEPARATES_UPPER_FLOOR"), "refresh of the same persisted floor claimed a new storey: " + message);
        assertFalse(message.contains("resolves player"), "persisted-floor refresh was presented as player-floor discovery");
    }

    @Test
    void persistedConnectorLookupUsesPhysicalMarkerPosition() {
        BlockPos floorCell = new BlockPos(10, 64, 20);
        BlockPos ladder = floorCell.east();
        StructureFloor floor = new StructureFloor(3, 0, new FloorGeometry(
                Set.of(new FloorGeometry.Cell(floorCell, 67)),
                List.of(new FloorConnector.Marker(ladder, FloorConnector.Type.LADDER, floorCell))));

        assertEquals(FloorConnector.Type.LADDER,
                BuildingDiagnostics.persistedConnectorType(floor, ladder));
        assertNull(BuildingDiagnostics.persistedConnectorType(floor, floorCell));
    }

    @Test
    void roomPlanProbeCapturesScannerFailureForDiagnostics() {
        IllegalArgumentException failure = new IllegalArgumentException("duplicate floor column");

        BuildingDiagnostics.PlanAttempt attempt = BuildingDiagnostics.planAttempt(() -> {
            throw failure;
        });

        assertNull(attempt.analysis());
        assertEquals(failure, attempt.failure());
    }

    @Test
    void roomPlanProbeReturnsSuccessfulAnalysisUnchanged() {
        RoomScanPlanner.Analysis expected = new RoomScanPlanner.Analysis(
                RoomScanPlan.addBuilding(new BlockPos(1, 64, 2)));

        BuildingDiagnostics.PlanAttempt attempt = BuildingDiagnostics.planAttempt(() -> expected);

        assertEquals(expected, attempt.analysis());
        assertNull(attempt.failure());
    }
}
