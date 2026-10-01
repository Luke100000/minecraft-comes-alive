package net.conczin.mca;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigPathfindingTest {
    @Test
    void pathfindingDefaultsAndBoundsAreNativeConfigValues() {
        assertEquals(160, Config.SERVER.villagerPathfindingDistance.getDefault());
        assertEquals(48, Config.SERVER.villagerFollowRange.getDefault());

        assertTrue(Config.SERVER.villagerPathfindingDistance.getSpec().test(16));
        assertTrue(Config.SERVER.villagerPathfindingDistance.getSpec().test(256));
        assertFalse(Config.SERVER.villagerPathfindingDistance.getSpec().test(257));
        assertTrue(Config.SERVER.villagerFollowRange.getSpec().test(64));
        assertFalse(Config.SERVER.villagerFollowRange.getSpec().test(65));
    }
}
