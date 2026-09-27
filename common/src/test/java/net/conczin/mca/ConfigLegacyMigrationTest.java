package net.conczin.mca;

import net.neoforged.fml.config.NeoForgeTestConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigLegacyMigrationTest {
    @TempDir
    Path tempDirectory;

    @Test
    void legacyValuesMigrateWithoutBecomingNativeDefaults() throws Exception {
        Path configDirectory = Files.createDirectories(tempDirectory.resolve("config"));
        Files.writeString(configDirectory.resolve(Config.LEGACY_FILE_NAME), """
                {
                  "enableOnlineTTS": true,
                  "onlineTTSModel": "player2",
                  "enableVillagerChatAI": true,
                  "villagerChatAIToken": "legacy-token",
                  "inworldAIResourceNames": {
                    "4f6b6d8c-724c-4a8f-83b8-2f4ce9f22110": "characters/legacy"
                  },
                  "allowGrimReaper": false,
                  "archerArrowsIgnoreVillagers": false,
                  "useSquidwardModels": true,
                  "showNameTags": false,
                  "destinySpawnLocations": ["minecraft:village_desert"]
                }
                """, StandardCharsets.UTF_8);

        runProbe(MigrationProbe.class);
    }

    @Test
    void existingNativeTomlSuppressesLegacyImportAndLeavesJsonUntouched() throws Exception {
        Path configDirectory = Files.createDirectories(tempDirectory.resolve("config"));
        Path legacyPath = configDirectory.resolve(Config.LEGACY_FILE_NAME);
        String legacyJson = "{\"enableOnlineTTS\":true}";
        Files.writeString(legacyPath, legacyJson, StandardCharsets.UTF_8);
        Files.writeString(configDirectory.resolve(Config.CLIENT_FILE_NAME), "# existing native config", StandardCharsets.UTF_8);

        runProbe(ExistingTomlProbe.class);

        assertEquals(legacyJson, Files.readString(legacyPath, StandardCharsets.UTF_8));
    }

    private void runProbe(Class<?> probeClass) throws Exception {
        Process process = new ProcessBuilder(
                javaExecutable(),
                "-cp",
                absoluteClasspath(),
                probeClass.getName())
                .directory(tempDirectory.toFile())
                .redirectErrorStream(true)
                .start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private static String absoluteClasspath() {
        return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                .map(Path::of)
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
    }

    public static final class MigrationProbe {
        private MigrationProbe() {
        }

        public static void main(String[] args) throws Exception {
            NeoForgeTestConfigLoader.loadDefaults(Config.COMMON_SPEC);
            NeoForgeTestConfigLoader.loadDefaults(Config.SERVER_SPEC);
            NeoForgeTestConfigLoader.loadDefaults(Config.CLIENT_SPEC);

            requireEquals(false, Config.SERVER.enableVillagerChatAI.getDefault(), "SERVER AI default changed by legacy JSON");
            requireEquals(true, Config.SERVER.allowGrimReaper.getDefault(), "SERVER gameplay default changed by legacy JSON");
            requireEquals(true, Config.SERVER.archerArrowsIgnoreVillagers.getDefault(), "SERVER default changed by legacy JSON");
            requireEquals(false, Config.CLIENT.enableOnlineTTS.getDefault(), "CLIENT TTS default changed by legacy JSON");
            requireEquals("default", Config.CLIENT.onlineTTSModel.getDefault(), "CLIENT TTS model default changed by legacy JSON");
            requireEquals(false, Config.CLIENT.useSquidwardModels.getDefault(), "CLIENT model default changed by legacy JSON");
            requireEquals(true, Config.CLIENT.showNameTags.getDefault(), "CLIENT default changed by legacy JSON");

            Path commonPath = Path.of("config", Config.COMMON_FILE_NAME).toAbsolutePath().normalize();
            Path serverPath = Path.of("config", Config.SERVER_FILE_NAME).toAbsolutePath().normalize();
            Path clientPath = Path.of("config", Config.CLIENT_FILE_NAME).toAbsolutePath().normalize();
            Path worldServerOverride = Path.of("world", "serverconfig", Config.SERVER_FILE_NAME).toAbsolutePath().normalize();

            requireEquals(false, Config.migrateLegacy(Config.SERVER_SPEC, worldServerOverride), "world SERVER override did not suppress legacy import");
            requireEquals(true, Config.SERVER.archerArrowsIgnoreVillagers.get(), "suppressed SERVER migration changed the loaded value");

            requireEquals(true, Config.migrateLegacy(Config.COMMON_SPEC, commonPath), "COMMON legacy import was not applied");
            requireEquals(true, Config.migrateLegacy(Config.SERVER_SPEC, serverPath), "global SERVER legacy import was not applied");
            requireEquals(true, Config.migrateLegacy(Config.CLIENT_SPEC, clientPath), "CLIENT legacy import was not applied");

            requireEquals(true, Config.SERVER.enableVillagerChatAI.get(), "SERVER AI legacy value was not migrated");
            requireEquals("legacy-token", Config.COMMON.villagerChatAIToken.get(), "COMMON AI token legacy value was not migrated");
            requireEquals(
                    "characters/legacy",
                    Config.legacyInworldAIResourceNames().get(UUID.fromString("4f6b6d8c-724c-4a8f-83b8-2f4ce9f22110")),
                    "legacy Inworld villager assignment was not preserved for world-data migration");
            requireEquals(false, Config.SERVER.allowGrimReaper.get(), "SERVER gameplay legacy value was not migrated");
            requireEquals(false, Config.SERVER.archerArrowsIgnoreVillagers.get(), "SERVER legacy value was not migrated");
            requireEquals(true, Config.CLIENT.enableOnlineTTS.get(), "CLIENT TTS legacy value was not migrated");
            requireEquals("player2", Config.CLIENT.onlineTTSModel.get(), "CLIENT TTS model legacy value was not migrated");
            requireEquals(true, Config.CLIENT.useSquidwardModels.get(), "CLIENT model legacy value was not migrated");
            requireEquals(false, Config.CLIENT.showNameTags.get(), "CLIENT legacy value was not migrated");
            requireEquals(List.of("minecraft:village_desert"), Config.SERVER.destinySpawnLocations.get(), "SERVER list was not migrated");

            requireEquals(false, Config.SERVER.enableVillagerChatAI.getDefault(), "SERVER AI default changed after migration");
            requireEquals(true, Config.SERVER.allowGrimReaper.getDefault(), "SERVER gameplay default changed after migration");
            requireEquals(true, Config.SERVER.archerArrowsIgnoreVillagers.getDefault(), "SERVER default changed after migration");
            requireEquals(false, Config.CLIENT.enableOnlineTTS.getDefault(), "CLIENT TTS default changed after migration");
            requireEquals("default", Config.CLIENT.onlineTTSModel.getDefault(), "CLIENT TTS model default changed after migration");
            requireEquals(false, Config.CLIENT.useSquidwardModels.getDefault(), "CLIENT model default changed after migration");
            requireEquals(true, Config.CLIENT.showNameTags.getDefault(), "CLIENT default changed after migration");
            requireEquals(false, Config.migrateLegacy(Config.COMMON_SPEC, commonPath), "COMMON legacy migration was applied twice");
        }

        private static void requireEquals(Object expected, Object actual, String message) {
            if (!expected.equals(actual)) {
                throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
            }
        }
    }

    public static final class ExistingTomlProbe {
        private ExistingTomlProbe() {
        }

        public static void main(String[] args) {
            NeoForgeTestConfigLoader.loadDefaults(Config.CLIENT_SPEC);

            Path clientPath = Path.of("config", Config.CLIENT_FILE_NAME).toAbsolutePath().normalize();
            MigrationProbe.requireEquals(false, Config.migrateLegacy(Config.CLIENT_SPEC, clientPath), "existing CLIENT TOML did not suppress legacy import");
            MigrationProbe.requireEquals(false, Config.CLIENT.enableOnlineTTS.get(), "legacy JSON overwrote existing CLIENT TOML");
        }
    }
}
