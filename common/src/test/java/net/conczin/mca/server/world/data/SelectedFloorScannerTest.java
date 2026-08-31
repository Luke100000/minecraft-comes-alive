package net.conczin.mca.server.world.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectedFloorScannerTest {
    @Test
    void stepDecisionUsesVanillaJumpThreshold() {
        assertTrue(SelectedFloorScanner.canStep(64.0D, 65.0D));
        assertFalse(SelectedFloorScanner.canStep(64.0D, 65.25D));
    }

    @Test
    void selectedFloorBandDoesNotClimbIntoAnotherStorey() {
        assertTrue(SelectedFloorScanner.withinSelectedFloorBand(64, 66));
        assertFalse(SelectedFloorScanner.withinSelectedFloorBand(64, 67));
    }
}
