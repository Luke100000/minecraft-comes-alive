package net.conczin.mca.entity.ai.navigation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StairSpinDiagnosticsTest {
    @Test
    void repeatedTurningWithoutProgressOnStairsIsReported() {
        StairSpinDiagnostics diagnostics = new StairSpinDiagnostics();
        assertNull(diagnostics.sample(0, true, 0.5D, 0.5D, 0.0F, 0.0F));

        StairSpinDiagnostics.SpinEvent detected = null;
        for (int tick = 1; tick <= 12; tick++) {
            StairSpinDiagnostics.SpinEvent event = diagnostics.sample(tick, true, 0.5D, 0.5D,
                    tick * 40.0F, tick * 40.0F);
            if (event != null) detected = event;
        }

        assertNotNull(detected, "A full rotation in the same spot should be flagged");
        assertTrue(detected.rotationDegrees() >= 300.0D);
        assertTrue(detected.ticks() >= 8);
    }

    @Test
    void walkingAlongStairsDoesNotAccumulateRotationAcrossBlocks() {
        StairSpinDiagnostics diagnostics = new StairSpinDiagnostics();
        assertNull(diagnostics.sample(0, true, 0.0D, 0.0D, 0.0F, 0.0F));
        for (int tick = 1; tick <= 40; tick++) {
            assertNull(diagnostics.sample(tick, true, tick * 0.25D, 0.0D, tick * 80.0F, tick * 80.0F));
        }
    }

    @Test
    void wrappedYawAndOrdinaryTurnsDoNotLookLikeSpins() {
        StairSpinDiagnostics diagnostics = new StairSpinDiagnostics();
        assertNull(diagnostics.sample(0, true, 0.0D, 0.0D, 179.0F, 179.0F));
        for (int tick = 1; tick <= 20; tick++) {
            float yaw = tick % 2 == 0 ? 179.0F : -179.0F;
            assertNull(diagnostics.sample(tick, true, 0.0D, 0.0D, yaw, yaw));
        }
    }

    @Test
    void briefGapBetweenStairStepsDoesNotResetTheRotationWindow() {
        StairSpinDiagnostics diagnostics = new StairSpinDiagnostics();
        assertNull(diagnostics.sample(0, true, 0.0D, 0.0D, 0.0F, 0.0F));
        for (int tick = 1; tick <= 5; tick++) {
            assertNull(diagnostics.sample(tick, true, 0.0D, 0.0D, tick * 40.0F, tick * 40.0F));
        }
        assertNull(diagnostics.sample(6, false, 0.0D, 0.0D, 240.0F, 240.0F));
        StairSpinDiagnostics.SpinEvent event = null;
        for (int tick = 7; tick <= 12; tick++) {
            StairSpinDiagnostics.SpinEvent current = diagnostics.sample(tick, true, 0.0D, 0.0D,
                    tick * 40.0F, tick * 40.0F);
            if (current != null) event = current;
        }
        assertNotNull(event, "Moving between steps must not erase a genuine sustained spin");
    }

    @Test
    void leavingStairsLongEnoughClearsTheRotationWindow() {
        StairSpinDiagnostics diagnostics = new StairSpinDiagnostics();
        assertNull(diagnostics.sample(0, true, 0.0D, 0.0D, 0.0F, 0.0F));
        for (int tick = 1; tick <= 5; tick++) {
            assertNull(diagnostics.sample(tick, true, 0.0D, 0.0D, tick * 40.0F, tick * 40.0F));
        }
        for (int tick = 6; tick <= 18; tick++) {
            assertNull(diagnostics.sample(tick, false, 0.0D, 0.0D, 240.0F, 240.0F));
        }
        assertNull(diagnostics.sample(19, true, 0.0D, 0.0D, 240.0F, 240.0F));
        assertNull(diagnostics.sample(20, true, 0.0D, 0.0D, 280.0F, 280.0F));
    }

    @Test
    void visiblySpinningBodyIsReportedEvenIfMovementYawDoesNotChange() {
        StairSpinDiagnostics diagnostics = new StairSpinDiagnostics();
        assertNull(diagnostics.sample(0, true, 0.0D, 0.0D, 0.0F, 0.0F));
        StairSpinDiagnostics.SpinEvent detected = null;
        for (int tick = 1; tick <= 12; tick++) {
            StairSpinDiagnostics.SpinEvent event = diagnostics.sample(tick, true, 0.0D, 0.0D,
                    0.0F, tick * 40.0F);
            if (event != null) detected = event;
        }
        assertNotNull(detected, "Body rotation can be visible with a constant navigation yaw");
        assertEquals("body", detected.source());
        assertTrue(detected.rotationDegrees() >= 300.0D);
    }
}
