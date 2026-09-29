package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BuildingDiagnosticsTest {
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

        assertNull(attempt.plan());
        assertEquals(failure, attempt.failure());
    }

    @Test
    void roomPlanProbeReturnsSuccessfulPlanUnchanged() {
        RoomScanPlan expected = RoomScanPlan.addBuilding(new BlockPos(1, 64, 2));

        BuildingDiagnostics.PlanAttempt attempt = BuildingDiagnostics.planAttempt(() -> expected);

        assertEquals(expected, attempt.plan());
        assertNull(attempt.failure());
    }
}
