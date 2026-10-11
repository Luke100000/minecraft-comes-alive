package net.conczin.mca;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigDefaultMapUpgradeTest {
    @Test
    void upstreamMapDefaultsPreserveUserOverridesAndUnrelatedEntries() {
        Map<String, String> merged = Config.mergeDefaultMap(
                List.of("cpm=all", "mowziesmobs=all", "parcool=all"),
                List.of("cpm=arms", "custom=block_villager"),
                "playerRendererBlacklist");

        assertEquals(Map.of(
                "cpm", "arms",
                "mowziesmobs", "all",
                "parcool", "all",
                "custom", "block_villager"), merged);
        assertEquals(List.of("cpm=arms", "custom=block_villager",
                "mowziesmobs=all", "parcool=all"), Config.encodeMap(merged));
    }

    @Test
    void upstreamMapUpgradesHavePersistedVersionMarkersAndDefaults() {
        assertEquals(0, Config.SERVER.defaultMapsVersion.getDefault());
        assertEquals(0, Config.CLIENT.defaultMapsVersion.getDefault());
        assertTrue(Config.SERVER.defaultMapsVersion.getSpec().test(1));
        assertTrue(Config.CLIENT.defaultMapsVersion.getSpec().test(1));
        assertFalse(Config.SERVER.defaultMapsVersion.getSpec().test(2));
        assertEquals("minecraft:cleric", Config.decodeMap(
                Config.SERVER.professionConversionsMap.getDefault(),
                String.class, String.class, "professionConversionsMap").get("villagersplus:alchemist"));
        assertEquals("arms", Config.decodeMap(
                Config.CLIENT.playerRendererBlacklist.getDefault(),
                String.class, String.class, "playerRendererBlacklist").get("walkers"));
    }
}
