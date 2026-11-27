package net.conczin.mca;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigListValidationTest {
    @Test
    void registryIdListsRejectMalformedResourceLocations() {
        assertTrue(Config.SERVER.villagerInteractionItemBlacklist.accepts(List.of("minecraft:bucket")));
        assertFalse(Config.SERVER.villagerInteractionItemBlacklist.accepts(List.of("not an id")));

        assertTrue(Config.SERVER.moddedVillagerWhitelist.accepts(List.of("example:custom_villager")));
        assertFalse(Config.SERVER.moddedVillagerWhitelist.accepts(List.of("example:bad id")));

        assertTrue(Config.SERVER.structuresInRumors.accepts(List.of("minecraft:village_plains")));
        assertFalse(Config.SERVER.structuresInRumors.accepts(List.of("minecraft:bad structure")));
    }

    @Test
    void registrySelectorListsRejectMalformedIdsAndTags() {
        assertTrue(Config.SERVER.validTreeSources.accepts(List.of("minecraft:dirt", "#minecraft:logs")));
        assertFalse(Config.SERVER.validTreeSources.accepts(List.of("#minecraft:bad tag")));

        assertTrue(Config.SERVER.destinySpawnLocations.accepts(List.of("somewhere", "#minecraft:village")));
        assertFalse(Config.SERVER.destinySpawnLocations.accepts(List.of("#bad tag")));
    }

    @Test
    void allowedSpawnReasonsRejectUnknownNames() {
        assertTrue(Config.SERVER.allowedSpawnReasons.accepts(List.of("natural", "structure")));
        assertFalse(Config.SERVER.allowedSpawnReasons.accepts(List.of("made_up")));
    }

    @Test
    void registryBackedMapKeysRejectMalformedSelectors() {
        assertTrue(Config.SERVER.maxTreeTicks.accepts(List.of("#minecraft:logs=60")));
        assertFalse(Config.SERVER.maxTreeTicks.accepts(List.of("#minecraft:bad tag=60")));

        assertTrue(Config.SERVER.taxesMap.accepts(List.of("minecraft:emerald=1.0")));
        assertFalse(Config.SERVER.taxesMap.accepts(List.of("minecraft:bad item=1.0")));
        assertTrue(Config.SERVER.taxesMap.accepts(List.of("minecraft:emerald=0.01")));
        for (String amount : List.of("0", "-1", "NaN", "Infinity", "-Infinity", "not_a_number")) {
            assertFalse(Config.SERVER.taxesMap.accepts(List.of("minecraft:emerald=" + amount)),
                    "Invalid tax value must never reach the village collection loop: " + amount);
        }
    }

    @Test
    void nativeValidationRejectsMissingVanillaRegistryEntriesButDefersModdedEntries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        assertTrue(Config.SERVER.villagerInteractionItemBlacklist.accepts(List.of("minecraft:bucket")));
        assertFalse(Config.SERVER.villagerInteractionItemBlacklist.accepts(List.of("minecraft:not_a_real_item")));
        assertTrue(Config.SERVER.villagerInteractionItemBlacklist.accepts(List.of("example:future_item")));

        assertTrue(Config.SERVER.moddedVillagerWhitelist.accepts(List.of("minecraft:villager")));
        assertFalse(Config.SERVER.moddedVillagerWhitelist.accepts(List.of("minecraft:not_a_real_entity")));
        assertTrue(Config.SERVER.moddedVillagerWhitelist.accepts(List.of("example:future_entity")));

        assertTrue(Config.SERVER.validTreeSources.accepts(List.of("minecraft:dirt")));
        assertFalse(Config.SERVER.validTreeSources.accepts(List.of("minecraft:not_a_real_block")));
        assertTrue(Config.SERVER.validTreeSources.accepts(List.of("example:future_block")));

        // Tag contents/existence are datapack/runtime-owned, so native load-time validation stays syntax-only.
        assertTrue(Config.SERVER.validTreeSources.accepts(List.of("#minecraft:not_a_real_tag")));

        assertFalse(Config.SERVER.taxesMap.accepts(List.of("minecraft:not_a_real_item=1.0")));
        assertTrue(Config.SERVER.taxesMap.accepts(List.of("example:future_item=1.0")));
    }

    @Test
    void professionConversionsValidateVanillaProfessionsWithoutRejectingFutureModdedOnes() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        assertTrue(Config.SERVER.professionConversionsMap.accepts(List.of("minecraft:farmer=minecraft:armorer")));
        assertTrue(Config.SERVER.professionConversionsMap.accepts(List.of("default=minecraft:farmer")));
        assertFalse(Config.SERVER.professionConversionsMap.accepts(List.of("minecraft:not_a_real_profession=minecraft:farmer")));
        assertFalse(Config.SERVER.professionConversionsMap.accepts(List.of("minecraft:farmer=minecraft:not_a_real_profession")));
        assertTrue(Config.SERVER.professionConversionsMap.accepts(List.of("example:future_profession=mca:guard")));
    }
}
