package net.conczin.mca.fabric.config;

import net.conczin.mca.Config;
import net.conczin.mca.config.ConfigScope;
import net.conczin.mca.config.nativeapi.NeoForgeConfigBackend;
import com.electronwill.nightconfig.toml.TomlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FabricTomlConfigBackendTest {
    @TempDir Path root;

    @Test
    void firstLaunchCreatesOnlyGlobalFilesAndWritesOriginalDefaults() throws Exception {
        FabricTomlConfigBackend backend = new FabricTomlConfigBackend(root.resolve("config"));
        backend.loadGlobal();
        assertTrue(Files.isRegularFile(root.resolve("config/mca-common.toml")));
        assertTrue(Files.isRegularFile(root.resolve("config/mca-server.toml")));
        assertFalse(Files.exists(root.resolve("config/mca-client.toml")), "dedicated servers must not touch CLIENT");
        assertEquals(24000, Config.SERVER.babyItemGrowUpTime.get());
        assertEquals(true, Config.SERVER.enableVillagerCollisions.get());
        Config.COMMON.useSquidwardModels.set(true);
        Config.SERVER.babyItemGrowUpTime.set(34567);
        backend.loadClient();
        assertTrue(Files.isRegularFile(root.resolve("config/mca-client.toml")));
        assertTrue(Config.COMMON.useSquidwardModels.get(), "client initialization must not reload COMMON");
        assertEquals(34567, Config.SERVER.babyItemGrowUpTime.get(), "client initialization must not reload SERVER");
    }

    @Test
    void freshFilesIncludeNativeDefaultAndRangeComments() throws Exception {
        FabricTomlConfigBackend backend = new FabricTomlConfigBackend(root.resolve("config"));
        backend.loadGlobal();
        String toml = Files.readString(root.resolve("config/mca-server.toml"));
        assertTrue(toml.contains("Default: 24000"));
        assertTrue(toml.contains("Range:"));
        assertTrue(toml.contains("#"));
    }

    @Test
    void standaloneGeneratedFilesPassActualNativeSpecCorrectionCheck() throws Exception {
        Path dir = root.resolve("config");
        FabricTomlConfigBackend backend = new FabricTomlConfigBackend(dir);
        backend.loadGlobal();
        backend.loadClient();
        try {
            var nativeBackend = new NeoForgeConfigBackend();
            for (ConfigScope scope : ConfigScope.values()) {
                Path file = dir.resolve(switch (scope) {
                    case COMMON -> Config.COMMON_FILE_NAME;
                    case SERVER -> Config.SERVER_FILE_NAME;
                    case CLIENT -> Config.CLIENT_FILE_NAME;
                });
                try (var reader = Files.newBufferedReader(file)) {
                    assertTrue(nativeBackend.spec(scope).isCorrect(new TomlParser().parse(reader)),
                            "FCP would rewrite freshly-created " + file);
                }
            }
        } finally {
            // The native adapter binds shared handles; restore the Fabric test backend afterward.
            FabricTomlConfigBackend restored = new FabricTomlConfigBackend(dir);
            restored.loadGlobal();
            restored.loadClient();
        }
    }

    @Test
    void validOverridesRoundTripWithCorrectScopes() throws Exception {
        Path dir = root.resolve("config");
        FabricTomlConfigBackend backend = new FabricTomlConfigBackend(dir);
        backend.loadGlobal();
        backend.loadClient();
        Config.SERVER.babyItemGrowUpTime.set(42000);
        Config.CLIENT.showNameTags.set(false);
        backend.save(ConfigScope.SERVER);
        backend.save(ConfigScope.CLIENT);

        FabricTomlConfigBackend reloaded = new FabricTomlConfigBackend(dir);
        reloaded.loadGlobal();
        reloaded.loadClient();
        assertEquals(42000, Config.SERVER.babyItemGrowUpTime.get());
        assertFalse(Config.CLIENT.showNameTags.get());
    }

    @Test
    void invalidValueFallsBackWithoutRewritingOriginalFile() throws Exception {
        Path dir = root.resolve("config");
        Files.createDirectories(dir);
        Path file = dir.resolve("mca-server.toml");
        byte[] content = "[villager_behavior]\nbabyItemGrowUpTime = -234\n# custom comment\nunknownKey = 7\n".getBytes(StandardCharsets.UTF_8);
        Files.write(file, content);
        new FabricTomlConfigBackend(dir).loadGlobal();
        assertEquals(Config.SERVER.babyItemGrowUpTime.getDefault(), Config.SERVER.babyItemGrowUpTime.get());
        assertArrayEquals(content, Files.readAllBytes(file));
    }

    @Test
    void savePreservesUnknownKeysAndUserComments() throws Exception {
        Path dir = root.resolve("config");
        Files.createDirectories(dir);
        Path file = dir.resolve("mca-server.toml");
        Files.writeString(file, """
                [villager_behavior]
                # A user note that must survive a save
                babyItemGrowUpTime = 12345
                customUnusedKey = "keep ©"
                """, StandardCharsets.UTF_8);

        FabricTomlConfigBackend backend = new FabricTomlConfigBackend(dir);
        backend.loadGlobal();
        assertEquals(12345, Config.SERVER.babyItemGrowUpTime.get());
        backend.save(ConfigScope.SERVER);
        String updated = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(updated.contains("A user note that must survive a save"));
        assertTrue(updated.contains("customUnusedKey"));
        assertTrue(updated.contains("keep ©"));
    }

    @Test
    void malformedFileSurvivesWithoutSilentOverwrite() throws Exception {
        Path dir = root.resolve("config");
        Files.createDirectories(dir);
        Path file = dir.resolve("mca-server.toml");
        byte[] content = "[broken TOML\nfoo = 1\n".getBytes(StandardCharsets.UTF_8);
        Files.write(file, content);
        new FabricTomlConfigBackend(dir).loadGlobal();
        assertArrayEquals(content, Files.readAllBytes(file));
    }

    @Test
    void worldOverridesAreLocalAndRestoreGlobalValues() throws Exception {
        Path dir = root.resolve("config");
        FabricTomlConfigBackend backend = new FabricTomlConfigBackend(dir);
        backend.loadGlobal();
        Path worldConfig = root.resolve("worldA/serverconfig");
        Files.createDirectories(worldConfig);
        Path file = worldConfig.resolve("mca-server.toml");
        Files.writeString(file, "[villager_behavior]\nbabyItemGrowUpTime = 31111\n", StandardCharsets.UTF_8);

        backend.startWorld(worldConfig);
        assertEquals(31111, Config.SERVER.babyItemGrowUpTime.get());
        backend.stopWorld();
        assertEquals(24000, Config.SERVER.babyItemGrowUpTime.get());
        Path noOverride = root.resolve("worldB/serverconfig");
        backend.startWorld(noOverride);
        assertFalse(Files.exists(noOverride.resolve("mca-server.toml")));
        assertEquals(24000, Config.SERVER.babyItemGrowUpTime.get());
    }

    @Test
    void standaloneWorldMigratesLegacyOverrideBeforeLoading() throws Exception {
        Path dir = root.resolve("config");
        FabricTomlConfigBackend backend = new FabricTomlConfigBackend(dir);
        backend.loadGlobal();
        Path global = dir.resolve(Config.SERVER_FILE_NAME);
        byte[] originalGlobal = Files.readAllBytes(global);
        Path worldConfig = root.resolve("legacyWorld/serverconfig");
        Files.createDirectories(worldConfig);
        Path file = worldConfig.resolve(Config.SERVER_FILE_NAME);
        byte[] original = "babyItemGrowUpTime = 34567\n".getBytes(StandardCharsets.UTF_8);
        Files.write(file, original);

        backend.startWorld(worldConfig);
        assertEquals(34567, Config.SERVER.babyItemGrowUpTime.get());
        try (var reader = Files.newBufferedReader(file)) {
            var migrated = new TomlParser().parse(reader);
            assertEquals(34567, ((Number) migrated.getRaw("villager_behavior.babyItemGrowUpTime")).intValue());
            assertNull(migrated.getRaw("babyItemGrowUpTime"));
        }
        assertArrayEquals(original, Files.readAllBytes(file.resolveSibling(file.getFileName() + ".pre-mca-migration.bak")));
        backend.stopWorld();
        assertEquals(24000, Config.SERVER.babyItemGrowUpTime.get());
        assertArrayEquals(originalGlobal, Files.readAllBytes(global));
    }

    @Test
    void malformedWorldOverrideDoesNotReusePreviousWorldSettings() throws Exception {
        Path dir = root.resolve("config");
        FabricTomlConfigBackend backend = new FabricTomlConfigBackend(dir);
        backend.loadGlobal();
        Path worldA = root.resolve("worldA/serverconfig");
        Files.createDirectories(worldA);
        Files.writeString(worldA.resolve("mca-server.toml"),
                "[villager_behavior]\nbabyItemGrowUpTime = 32100\n");
        Path worldB = root.resolve("worldB/serverconfig");
        Files.createDirectories(worldB);
        byte[] invalid = "[invalid TOML\nbabyItemGrowUpTime = 500\n".getBytes(StandardCharsets.UTF_8);
        Files.write(worldB.resolve("mca-server.toml"), invalid);

        backend.startWorld(worldA);
        assertEquals(32100, Config.SERVER.babyItemGrowUpTime.get());
        backend.startWorld(worldB);
        assertEquals(24000, Config.SERVER.babyItemGrowUpTime.get(),
                "bad world B must fall back to global SERVER, never world A");
        assertArrayEquals(invalid, Files.readAllBytes(worldB.resolve("mca-server.toml")));
        backend.startWorld(root.resolve("worldC/serverconfig"));
        assertEquals(24000, Config.SERVER.babyItemGrowUpTime.get());
    }

    @Test
    void existingTomlWithOldMapVersionReceivesDefaultUpgrade() throws Exception {
        Path dir = root.resolve("config");
        Files.createDirectories(dir);
        Path file = dir.resolve("mca-server.toml");
        Files.writeString(file, "[villager_behavior]\ndefaultMapsVersion = 0\nprofessionConversionsMap = []\n");
        new FabricTomlConfigBackend(dir).loadGlobal();
        assertEquals(1, Config.SERVER.defaultMapsVersion.get());
        assertFalse(Config.SERVER.professionConversionsMap.get().isEmpty());
        assertTrue(Files.readString(file).contains("defaultMapsVersion = 1"));
    }

    @Test
    void existingWorldOverridesUpgradeDefaultMapsOnce() throws Exception {
        Path dir = root.resolve("config");
        FabricTomlConfigBackend backend = new FabricTomlConfigBackend(dir);
        backend.loadGlobal();
        Path global = dir.resolve(Config.SERVER_FILE_NAME);
        byte[] originalGlobal = Files.readAllBytes(global);
        Path worldConfig = root.resolve("world/serverconfig");
        Files.createDirectories(worldConfig);
        Path file = worldConfig.resolve(Config.SERVER_FILE_NAME);
        Files.writeString(file, """
                [villager_behavior]
                defaultMapsVersion = 0
                professionConversionsMap = ["villagersplus:alchemist=minecraft:farmer"]
                """);

        backend.startWorld(worldConfig);
        assertEquals(1, Config.SERVER.defaultMapsVersion.get());
        assertTrue(Config.SERVER.professionConversionsMap.get().contains("villagersplus:alchemist=minecraft:farmer"));
        assertTrue(Config.SERVER.professionConversionsMap.get().contains("villagersplus:horticulturist=minecraft:farmer"));
        assertFalse(Config.SERVER.professionConversionsMap.get().contains("villagersplus:alchemist=minecraft:cleric"));
        try (var reader = Files.newBufferedReader(file)) {
            var upgraded = new TomlParser().parse(reader);
            assertEquals(1, ((Number) upgraded.getRaw("villager_behavior.defaultMapsVersion")).intValue());
            assertTrue(upgraded.<java.util.List<String>>get("villager_behavior.professionConversionsMap")
                    .contains("villagersplus:horticulturist=minecraft:farmer"));
        }
        Config.SERVER.professionConversionsMap.set(java.util.List.of("villagersplus:alchemist=minecraft:farmer"));
        backend.save(ConfigScope.SERVER);
        backend.stopWorld();
        assertArrayEquals(originalGlobal, Files.readAllBytes(global));

        byte[] edited = Files.readAllBytes(file);
        backend.startWorld(worldConfig);
        assertEquals(java.util.List.of("villagersplus:alchemist=minecraft:farmer"), Config.SERVER.professionConversionsMap.get());
        assertArrayEquals(edited, Files.readAllBytes(file), "subsequent starts must preserve intentional deletions");
    }

    @Test
    void malformedWorldUsesUpgradedGlobalServerSnapshot() throws Exception {
        Path dir = root.resolve("config");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("mca-server.toml"), """
                [villager_behavior]
                defaultMapsVersion = 0
                professionConversionsMap = []
                """);
        FabricTomlConfigBackend backend = new FabricTomlConfigBackend(dir);
        backend.loadGlobal();
        assertEquals(1, Config.SERVER.defaultMapsVersion.get());

        Path world = root.resolve("worldBroken/serverconfig");
        Files.createDirectories(world);
        Files.writeString(world.resolve("mca-server.toml"), "[broken TOML\n");
        backend.startWorld(world);
        assertEquals(1, Config.SERVER.defaultMapsVersion.get(),
                "world parse failure must reuse the *upgraded* global snapshot");
        assertFalse(Config.SERVER.professionConversionsMap.get().isEmpty());
    }

    @Test
    void dedicatedServerMigrationNeverTouchesClientToml() throws Exception {
        Path dir = root.resolve("config");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("mca-server.toml"), "babyItemGrowUpTime = 34600\n");
        Path clientFile = dir.resolve("mca-client.toml");
        byte[] invalid = "[broken TOML\n".getBytes(StandardCharsets.UTF_8);
        Files.write(clientFile, invalid);
        FabricLegacyTomlMigration.migrate(dir, false);
        assertArrayEquals(invalid, Files.readAllBytes(clientFile));
        assertFalse(Files.exists(dir.resolve("mca-client.toml.pre-mca-migration.bak")));
        assertTrue(Files.readString(dir.resolve("mca-server.toml")).contains("[villager_behavior]"),
                "an unrelated CLIENT parse failure must not block SERVER migration");
    }

    @Test
    void immutableListValuesCanBeReusedWithoutLeakingMutableState() throws Exception {
        FabricTomlConfigBackend backend = new FabricTomlConfigBackend(root.resolve("config"));
        backend.loadGlobal();
        var first = Config.SERVER.professionConversionsMap.get();
        assertSame(first, Config.SERVER.professionConversionsMap.get());
        assertThrows(UnsupportedOperationException.class, () -> first.clear());

        java.util.ArrayList<String> mutable = new java.util.ArrayList<>(java.util.List.of("minecraft:farmer=minecraft:butcher"));
        Config.SERVER.professionConversionsMap.set(mutable);
        mutable.clear();
        assertEquals(1, Config.SERVER.professionConversionsMap.get().size());
        assertSame(Config.SERVER.professionConversionsMap.get(), Config.SERVER.professionConversionsMap.get());
    }

    @Test
    void migratedFlatAndCrossScopeTomlRetainsKnownUserValuesAndBackups() throws Exception {
        Path dir = root.resolve("config");
        Files.createDirectories(dir);
        Path oldCommon = dir.resolve("mca-common.toml");
        Path oldServer = dir.resolve("mca-server.toml");
        Files.writeString(oldCommon, "[ai]\nvillagerChatAIModel = \"my-custom-model\"\nvillagerChatAIToken = \"local-only\"\n");
        Files.writeString(oldServer, "babyItemGrowUpTime = 34567\n");
        FabricLegacyTomlMigration.migrate(dir, true);

        FabricTomlConfigBackend backend = new FabricTomlConfigBackend(dir);
        backend.loadGlobal();
        assertEquals(34567, Config.SERVER.babyItemGrowUpTime.get());
        assertEquals("my-custom-model", Config.SERVER.villagerChatAIModel.get());
        assertEquals("local-only", Config.COMMON.villagerChatAIToken.get());
        assertTrue(Files.exists(dir.resolve("mca-common.toml.pre-mca-migration.bak")));
        assertTrue(Files.exists(dir.resolve("mca-server.toml.pre-mca-migration.bak")));

        byte[] after = Files.readAllBytes(oldServer);
        FabricLegacyTomlMigration.migrate(dir, true);
        assertArrayEquals(after, Files.readAllBytes(oldServer), "migration must be idempotent");
    }

    @Test
    void failedCrossScopeMigrationRetainsLegacyOverridesForRetry() throws Exception {
        Path dir = root.resolve("config");
        Files.createDirectories(dir);
        Path common = dir.resolve(Config.COMMON_FILE_NAME);
        Path server = dir.resolve(Config.SERVER_FILE_NAME);
        Path client = dir.resolve(Config.CLIENT_FILE_NAME);
        byte[] original = """
                [ai]
                villagerChatAIModel = "legacy-model"
                [tts]
                onlineTTSModel = "legacy-voice"
                """.getBytes(StandardCharsets.UTF_8);
        Files.write(common, original);
        Files.createDirectory(client);
        Files.writeString(client.resolve("block-replacement"), "keep");

        assertAll(
                () -> assertThrows(java.io.UncheckedIOException.class, () -> FabricLegacyTomlMigration.migrate(dir, true),
                        "config loading must stop before native correction can discard the alias"),
                () -> assertArrayEquals(original, Files.readAllBytes(common),
                        "the legacy source must survive a failed destination write"));
        assertArrayEquals(original, Files.readAllBytes(common.resolveSibling(common.getFileName() + ".pre-mca-migration.bak")));
        assertEquals("keep", Files.readString(client.resolve("block-replacement")));

        // The successful destination may be edited before retry; its canonical value must win.
        Files.writeString(server, "[ai]\nvillagerChatAIModel = \"canonical-model\"\n");
        Files.delete(client.resolve("block-replacement"));
        Files.delete(client);
        FabricLegacyTomlMigration.migrate(dir, true);
        try (var reader = Files.newBufferedReader(common)) {
            var parsed = new TomlParser().parse(reader);
            assertNull(parsed.getRaw("ai.villagerChatAIModel"));
            assertNull(parsed.getRaw("tts.onlineTTSModel"));
        }
        FabricTomlConfigBackend backend = new FabricTomlConfigBackend(dir);
        backend.loadGlobal();
        backend.loadClient();
        assertEquals("canonical-model", Config.SERVER.villagerChatAIModel.get());
        assertEquals("legacy-voice", Config.CLIENT.onlineTTSModel.get());
    }

    @Test
    void migrationPrefersCanonicalValueAndLeavesUnrecognizedSectionKeysUntouched() throws Exception {
        Path dir = root.resolve("config");
        Files.createDirectories(dir);
        Path file = dir.resolve(Config.SERVER_FILE_NAME);
        Files.writeString(file, """
                babyItemGrowUpTime = 9999
                [villager_behavior]
                babyItemGrowUpTime = 33333
                [custom_user_section]
                villagerMaxAgeTime = 777777
                """);

        FabricLegacyTomlMigration.migrate(dir, true);
        String migrated = Files.readString(file);
        assertTrue(migrated.contains("babyItemGrowUpTime = 33333"));
        assertFalse(migrated.contains("babyItemGrowUpTime = 9999"));
        assertTrue(migrated.contains("villagerMaxAgeTime = 777777"),
                "an unrelated user-defined section must not be reinterpreted as a legacy config path");
        try (var reader = Files.newBufferedReader(file)) {
            var parsed = new TomlParser().parse(reader);
            assertEquals(777777, ((Number) parsed.getRaw("custom_user_section.villagerMaxAgeTime")).intValue());
            assertNull(parsed.getRaw("villager_behavior.villagerMaxAgeTime"));
        }
        byte[] migratedBytes = Files.readAllBytes(file);
        FabricLegacyTomlMigration.migrate(dir, true);
        assertArrayEquals(migratedBytes, Files.readAllBytes(file));
        assertTrue(Files.readString(dir.resolve("mca-server.toml.pre-mca-migration.bak"))
                .contains("babyItemGrowUpTime = 9999"));
    }
}
