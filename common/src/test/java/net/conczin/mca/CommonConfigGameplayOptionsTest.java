package net.conczin.mca;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
