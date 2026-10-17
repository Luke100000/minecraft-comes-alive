package net.conczin.mca;

import net.conczin.mca.config.ConfigSchema;
import net.conczin.mca.config.ConfigScope;
import net.conczin.mca.config.ConfigValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLayoutTest {
    @Test
    void nativeSectionsKeepTranslationsDeclaredBeforePush() {
        assertEquals("mca.configuration.section.villager_behavior",
                Config.COMMON_SPEC.section(List.of("villager_behavior")).translation());
        assertEquals("mca.configuration.section.ai",
                Config.COMMON_SPEC.section(List.of("ai")).translation());
        assertEquals("mca.configuration.section.village_behavior",
                Config.SERVER_SPEC.section(List.of("village_behavior")).translation());

        var nativeSpec = NativeConfigDefaultsExtension.spec(ConfigScope.COMMON);
        assertEquals("mca.configuration.section.ai", nativeSpec.getLevelTranslationKey(List.of("ai")));
    }

    @Test
    void nativeConfigValuesAreLoadedForCommonUnitTests() {
        assertEquals(Config.SERVER.enableAutoScanByDefault.getDefault(), Config.SERVER.enableAutoScanByDefault.get());
        assertEquals(Config.SERVER.archerArrowsIgnoreVillagers.getDefault(), Config.SERVER.archerArrowsIgnoreVillagers.get());
        assertEquals(Config.CLIENT.showNameTags.getDefault(), Config.CLIENT.showNameTags.get());
    }

    @Test
    void exposesNativeCommonServerAndClientSpecs() {
        assertTrue(Config.COMMON_SPEC.getValues().contains(List.of("villager_behavior", "useSquidwardModels")));
        assertTrue(Config.COMMON_SPEC.getValues().contains(List.of("ai", "villagerChatAIToken")));
        assertTrue(Config.SERVER_SPEC.getValues().contains(List.of("village_behavior", "archerArrowsIgnoreVillagers")));
        assertTrue(Config.SERVER_SPEC.getValues().contains(List.of("destiny", "destinySpawnLocations")));
        assertTrue(Config.SERVER_SPEC.getValues().contains(List.of("destiny", "launchIntoDestiny")));
        assertTrue(Config.SERVER_SPEC.getValues().contains(List.of("mod_features", "overwriteOriginalVillagers")));
        assertTrue(Config.CLIENT_SPEC.getValues().contains(List.of("villager_behavior", "showNameTags")));
    }

    @Test
    void keepsServerSyncedSettingsInServerConfig() {
        assertEquals(true, Config.SERVER.archerArrowsIgnoreVillagers.getDefault());
        assertEquals(24000, Config.SERVER.babyItemGrowUpTime.getDefault());
        assertFalse(Config.SERVER.villagerMaxAgeTime.getSpec().test(3));
        assertTrue(Config.SERVER.villagerMaxAgeTime.getSpec().test(4));
        assertEquals(3, Config.SERVER.addContentGloballyPermissionLevel.getDefault());
        assertTrue(Config.SERVER_SPEC.getValues().contains(List.of("villager_behavior", "enabledTraits")));
        assertTrue(Config.SERVER_SPEC.getValues().contains(List.of("villager_behavior", "babyItemGrowUpTime")));
        assertTrue(Config.SERVER_SPEC.getValues().contains(List.of("player_customization", "addContentGloballyPermissionLevel")));
        assertTrue(Config.SERVER_SPEC.getValues().contains(List.of("player_customization", "allowPlayerSizeAdjustment")));
        assertTrue(Config.SERVER_SPEC.getValues().contains(List.of("player_customization", "scalePlayerHitboxWithSizeAndWidth")));
        assertTrue(Config.SERVER_SPEC.getValues().contains(List.of("destiny", "allowBodyCustomizationInDestiny")));
        assertFalse(Config.SERVER_SPEC.getValues().contains(List.of("player_customization", "launchIntoDestiny")));
        for (String key : List.of(
                "allowBodyCustomizationInDestiny",
                "allowTraitCustomizationInDestiny",
                "destinySpawnLocations",
                "autoDiscoverDestinyLocations",
                "destinySpawnLocationBlacklist",
                "destinyOverworldOnly",
                "destinyDimensionBlacklist",
                "destinyLocationsToTranslationMap",
                "launchIntoDestiny",
                "allowDestinyCommandOnce",
                "allowDestinyCommandMoreThanOnce",
                "allowDestinyTeleportation")) {
            assertTrue(Config.SERVER_SPEC.getValues().contains(List.of("destiny", key)), key);
        }
        assertTrue(Config.SERVER_SPEC.getValues().contains(List.of("villager_behavior", "villagerMaxHealth")));
        assertTrue(Config.SERVER_SPEC.getValues().contains(List.of("village_behavior", "taxesFactor")));
        assertFalse(Config.COMMON_SPEC.getValues().contains(List.of("mod_features", "overwriteOriginalVillagers")));
    }

    @Test
    void keepsClientOnlySettingsOutOfCommonConfig() {
        assertEquals(false, Config.COMMON.useSquidwardModels.getDefault());
        assertEquals(true, Config.CLIENT.showNameTags.getDefault());
        assertEquals(5.0, Config.CLIENT.nameTagDistance.getDefault());
        assertFalse(Config.CLIENT_SPEC.getValues().contains(List.of("villager_behavior", "useSquidwardModels")));
        assertTrue(Config.CLIENT_SPEC.getValues().contains(List.of("tts", "enableOnlineTTS")));
        assertTrue(Config.CLIENT_SPEC.getValues().contains(List.of("tts", "onlineTTSModel")));
        assertTrue(Config.COMMON_SPEC.getValues().contains(List.of("villager_behavior", "useSquidwardModels")));
        assertFalse(Config.COMMON_SPEC.getValues().contains(List.of("ai", "enableOnlineTTS")));
        assertFalse(Config.COMMON_SPEC.getValues().contains(List.of("ai", "onlineTTSModel")));
        assertFalse(Config.COMMON_SPEC.getValues().contains(List.of("villager_behavior", "showNameTags")));
        assertFalse(Config.COMMON_SPEC.getValues().contains(List.of("player_customization", "enablePlayerShaders")));
    }

    @Test
    void serverPolicyUsesSyncedServerConfigWhilePrivateAiInfrastructureStaysCommon() {
        for (String key : List.of(
                "enableVillagerChatAI",
                "villagerChatAIModel",
                "villagerChatAIUseTools",
                "villagerChatAIContextPermissionLevel")) {
            assertTrue(Config.SERVER_SPEC.getValues().contains(List.of("ai", key)), key);
            assertFalse(Config.COMMON_SPEC.getValues().contains(List.of("ai", key)), key);
        }

        for (String key : List.of(
                "villagerChatAIEndpoint",
                "villagerChatAIToken",
                "villagerChatAISystemPrompt",
                "villagerChatAIFuseSystemPrompt",
                "villagerChatAIUseLongTermMemory",
                "villagerChatAIUseSharedLongTermMemory",
                "villagerChatAIIncludeSessionInformation",
                "inworldAIToken")) {
            assertTrue(Config.COMMON_SPEC.getValues().contains(List.of("ai", key)), key);
            assertFalse(Config.SERVER_SPEC.getValues().contains(List.of("ai", key)), key);
        }

        assertFalse(Config.COMMON_SPEC.getValues().contains(List.of("ai", "inworldAIResourceNames")));
    }

    @Test
    void configSectionsGroupValuesByTheirActualDomain() {
        assertTrue(Config.SERVER_SPEC.getValues().contains(List.of("player_customization", "bypassTraitRestrictions")));
        assertFalse(Config.CLIENT_SPEC.getValues().contains(List.of("villager_behavior", "bypassTraitRestrictions")));

        assertTrue(Config.SERVER_SPEC.getValues().contains(List.of("villager_behavior", "useModernUSANamesOnly")));
        assertTrue(Config.SERVER_SPEC.getValues().contains(List.of("villager_behavior", "structuresInRumors")));
        assertTrue(Config.SERVER_SPEC.getValues().contains(List.of("villager_behavior", "professionConversionsMap")));
        assertTrue(Config.SERVER_SPEC.getValues().contains(List.of("village_behavior", "guardsTargetEntities")));
        assertTrue(Config.SERVER_SPEC.getValues().contains(List.of("village_behavior", "taxesMap")));

        for (String key : List.of(
                "useModernUSANamesOnly",
                "guardsTargetEntities",
                "structuresInRumors",
                "professionConversionsMap",
                "taxesMap")) {
            assertFalse(Config.SERVER_SPEC.getValues().contains(List.of("player_customization", key)), key);
        }

        assertTrue(Config.CLIENT_SPEC.getValues().contains(List.of("player_customization", "immersiveLibraryUrl")));
        assertFalse(Config.CLIENT_SPEC.getValues().contains(List.of("village_behavior", "immersiveLibraryUrl")));
    }

    @Test
    void removedHistoricalPathfindingBlacklistDoesNotReturn() {
        assertFalse(Config.COMMON_SPEC.getValues().contains(List.of("village_behavior", "villagerPathfindingBlacklist")));
        assertFalse(Config.SERVER_SPEC.getValues().contains(List.of("village_behavior", "villagerPathfindingBlacklist")));
    }

    @Test
    void villagerCollisionsAreServerConfigAndEnabledByDefault() {
        ConfigValue<?> value = Config.SERVER_SPEC.entry("villager_behavior.enableVillagerCollisions").value();

        assertTrue(value != null);
        assertEquals(true, value.getDefault());
    }

    @Test
    void restartMetadataMatchesActualLifecycleBoundaries() {
        for (ConfigValue<?> value : List.of(
                Config.COMMON.useSquidwardModels,
                Config.CLIENT.enableBoobs,
                Config.CLIENT.onlineTTSServer,
                Config.CLIENT.player2Url,
                Config.CLIENT.enableVillagerPlayerModel,
                Config.CLIENT.playerRendererBlacklist)) {
            assertEquals(ConfigSchema.Restart.GAME, Config.schema(scope(value)).entry(String.join(".", value.getPath())).restart());
        }

        for (ConfigValue<?> value : List.of(
                Config.SERVER.nightOwlChance,
                Config.SERVER.allowAnyNightOwl,
                Config.SERVER.maleVillagerHeightFactor,
                Config.SERVER.femaleVillagerHeightFactor,
                Config.SERVER.maleVillagerWidthFactor,
                Config.SERVER.femaleVillagerWidthFactor,
                Config.SERVER.scalePlayerHitboxWithSizeAndWidth)) {
            assertEquals(ConfigSchema.Restart.WORLD, Config.schema(scope(value)).entry(String.join(".", value.getPath())).restart());
        }

        for (ConfigValue<?> value : List.of(
                Config.SERVER.guardEquipment,
                Config.SERVER.archerEquipment)) {
            assertEquals(ConfigSchema.Restart.NONE, Config.schema(scope(value)).entry(String.join(".", value.getPath())).restart());
        }
    }

    private static net.conczin.mca.config.ConfigScope scope(ConfigValue<?> value) {
        for (net.conczin.mca.config.ConfigScope scope : net.conczin.mca.config.ConfigScope.values()) {
            if (Config.schema(scope).entry(String.join(".", value.getPath())) != null
                    && Config.schema(scope).entry(String.join(".", value.getPath())).value() == value) return scope;
        }
        throw new IllegalArgumentException("Missing schema for " + value.getPath());
    }
}
