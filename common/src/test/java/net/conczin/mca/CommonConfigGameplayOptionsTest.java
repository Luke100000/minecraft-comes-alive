package net.conczin.mca;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonConfigGameplayOptionsTest {
    @Test
    void gameplaySafetyOptionsDefaultEnabled() {
        assertTrue(Config.SERVER.archerArrowsIgnoreVillagers.getDefault());
        assertTrue(Config.SERVER.villagersInteractWithFenceGates.getDefault());
    }

    @Test
    void gameplaySafetyOptionsAcceptServerOverrides() {
        assertTrue(Config.SERVER.archerArrowsIgnoreVillagers.getSpec().test(false));
        assertTrue(Config.SERVER.villagersInteractWithFenceGates.getSpec().test(false));
    }

    @Test
    void destinyDiscoveryDefaultsToAutomaticWithNoBlacklist() {
        assertTrue(Config.SERVER.autoDiscoverDestinyLocations.getDefault());
        assertEquals(List.of(), Config.SERVER.destinySpawnLocationBlacklist.getDefault());
        assertFalse(Config.SERVER.destinyOverworldOnly.getDefault());
        assertEquals(List.of(), Config.SERVER.destinyDimensionBlacklist.getDefault());
    }

    @Test
    void destinyDiscoveryAndBlacklistsAcceptServerOverrides() {
        assertTrue(Config.SERVER.autoDiscoverDestinyLocations.getSpec().test(false));
        assertTrue(Config.SERVER.destinySpawnLocationBlacklist.getSpec().test(List.of("ctov:*", "othermod:*large*")));
        assertTrue(Config.SERVER.destinyOverworldOnly.getSpec().test(true));
        assertTrue(Config.SERVER.destinyDimensionBlacklist.getSpec().test(List.of("minecraft:the_nether", "some_mod:*")));
    }
}
