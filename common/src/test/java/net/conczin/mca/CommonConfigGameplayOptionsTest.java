package net.conczin.mca;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonConfigGameplayOptionsTest {
    private static final Gson GSON = new Gson();

    @Test
    void gameplaySafetyOptionsDefaultEnabled() {
        CommonConfig config = new CommonConfig();

        assertTrue(config.archerArrowsIgnoreVillagers);
        assertTrue(config.villagersInteractWithFenceGates);
    }

    @Test
    void gameplaySafetyOptionsCanBeDisabledFromServerConfig() {
        CommonConfig config = GSON.fromJson("""
                {
                  "archerArrowsIgnoreVillagers": false,
                  "villagersInteractWithFenceGates": false
                }
                """, CommonConfig.class);

        assertFalse(config.archerArrowsIgnoreVillagers);
        assertFalse(config.villagersInteractWithFenceGates);
    }

    @Test
    void destinyDiscoveryDefaultsToAutomaticWithNoBlacklist() {
        CommonConfig config = new CommonConfig();

        assertTrue(config.autoDiscoverDestinyLocations);
        assertEquals(java.util.List.of(), config.destinySpawnLocationBlacklist);
    }

    @Test
    void destinyDiscoveryAndBlacklistCanBeConfigured() {
        CommonConfig config = GSON.fromJson("""
                {
                  "autoDiscoverDestinyLocations": false,
                  "destinySpawnLocationBlacklist": ["ctov:*", "othermod:*large*"]
                }
                """, CommonConfig.class);

        assertFalse(config.autoDiscoverDestinyLocations);
        assertEquals(
                java.util.List.of("ctov:*", "othermod:*large*"),
                config.destinySpawnLocationBlacklist
        );
    }
}
