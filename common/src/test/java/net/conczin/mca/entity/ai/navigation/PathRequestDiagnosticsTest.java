package net.conczin.mca.entity.ai.navigation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathRequestDiagnosticsTest {
    @Test
    void benchmarkOptOutOnlyDisablesDevelopmentDiagnosticsWhenRequested() {
        assertTrue(PathRequestDiagnostics.shouldCollect(true, false));
        assertFalse(PathRequestDiagnostics.shouldCollect(true, true));
        assertFalse(PathRequestDiagnostics.shouldCollect(false, false));
        assertFalse(PathRequestDiagnostics.shouldCollect(false, true));
    }
}
