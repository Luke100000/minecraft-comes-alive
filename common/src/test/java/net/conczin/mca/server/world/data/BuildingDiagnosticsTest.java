package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BuildingDiagnosticsTest {
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
