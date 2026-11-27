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
        assertFalse(Config.SERVER.villagersInteractWithAnyDoor.getDefault());
    }

    @Test
    void gameplaySafetyOptionsAcceptServerOverrides() {
        assertTrue(Config.SERVER.archerArrowsIgnoreVillagers.accepts(false));
        assertTrue(Config.SERVER.villagersInteractWithFenceGates.accepts(false));
        assertTrue(Config.SERVER.villagersInteractWithAnyDoor.accepts(true));
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
        assertTrue(Config.SERVER.autoDiscoverDestinyLocations.accepts(false));
        assertTrue(Config.SERVER.destinySpawnLocationBlacklist.accepts(List.of("ctov:*", "othermod:*large*")));
        assertTrue(Config.SERVER.destinyOverworldOnly.accepts(true));
        assertTrue(Config.SERVER.destinyDimensionBlacklist.accepts(List.of("minecraft:the_nether", "some_mod:*")));
    }
}
