package net.conczin.mca;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.config.NeoForgeTestConfigLoader;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ConfigLegacyMigrationTest {
    @Test
    void pathlessServerDefaultsDoNotAttemptLegacyMigration() {
        var config = NeoForgeTestConfigLoader.pathlessServerConfig(Config.SERVER_SPEC);
        assertThrows(IllegalStateException.class, config::getFullPath,
                "The native loader's in-memory SERVER defaults have no filesystem path");
        assertDoesNotThrow(() -> Config.migrateLegacy(config));
    }

    @TempDir
    Path tempDirectory;

    @Test
    void realDev1211RunConfigMigratesWithoutModifyingTheOriginal() throws Exception {
        String sourcePath = System.getenv("MCA_DEV_1211_JSON");
        assumeTrue(sourcePath != null && Files.isRegularFile(Path.of(sourcePath)),
                "Set MCA_DEV_1211_JSON to dev/1.21.1/neoforge/run/config/mca.json");

        Path source = Path.of(sourcePath);
        byte[] original = Files.readAllBytes(source);
        Path configDirectory = Files.createDirectories(tempDirectory.resolve("config"));
        Files.copy(source, configDirectory.resolve(Config.LEGACY_FILE_NAME), StandardCopyOption.REPLACE_EXISTING);

        runProbe(RealDev1211Probe.class);
        runProbe(RealDev1211SecondLaunchProbe.class);
        assertArrayEquals(original, Files.readAllBytes(source),
                "The original development run config must remain untouched");
    }

    @Test
    void legacyValuesMigrateWithoutBecomingNativeDefaults() throws Exception {
        Path configDirectory = Files.createDirectories(tempDirectory.resolve("config"));
        Files.writeString(configDirectory.resolve(Config.LEGACY_FILE_NAME), """
                {
                  "version": 2,
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
        String legacyJson = "{\"version\":2,\"enableOnlineTTS\":true}";
        Files.writeString(legacyPath, legacyJson, StandardCharsets.UTF_8);
        Files.writeString(configDirectory.resolve(Config.CLIENT_FILE_NAME), "# existing native config", StandardCharsets.UTF_8);

        runProbe(ExistingTomlProbe.class);

        assertEquals(legacyJson, Files.readString(legacyPath, StandardCharsets.UTF_8));
    }

    @Test
    void nonDev121LegacySchemaIsNotMigratedOrRewritten() throws Exception {
        Path configDirectory = Files.createDirectories(tempDirectory.resolve("config"));
        Path legacyPath = configDirectory.resolve(Config.LEGACY_FILE_NAME);
        String legacyJson = "{\"version\":1,\"enableOnlineTTS\":true}";
        Files.writeString(legacyPath, legacyJson, StandardCharsets.UTF_8);

        runProbe(UnsupportedLegacyVersionProbe.class);

        assertEquals(legacyJson, Files.readString(legacyPath, StandardCharsets.UTF_8));
    }

    @Test
    void loaderConfigDirectoryIsUsedForLegacyMigration() throws Exception {
        Path configDirectory = Files.createDirectories(tempDirectory.resolve("custom-config"));
        Files.writeString(configDirectory.resolve(Config.LEGACY_FILE_NAME), """
                {
                  "version": 2,
                  "enableOnlineTTS": true
                }
                """, StandardCharsets.UTF_8);

        runProbe(CustomConfigDirectoryProbe.class, configDirectory.toString());
    }

    private void runProbe(Class<?> probeClass, String... args) throws Exception {
        List<String> command = new java.util.ArrayList<>(List.of(
                javaExecutable(),
                "-cp",
                absoluteClasspath(),
                probeClass.getName()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command)
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

    public static final class UnsupportedLegacyVersionProbe {
        private UnsupportedLegacyVersionProbe() {
        }

        public static void main(String[] args) {
            NeoForgeTestConfigLoader.loadDefaults(Config.CLIENT_SPEC);

            Path clientPath = Path.of("config", Config.CLIENT_FILE_NAME).toAbsolutePath().normalize();
            MigrationProbe.requireEquals(false, Config.migrateLegacy(Config.CLIENT_SPEC, clientPath), "non-dev/1.21.1 legacy schema was imported");
            MigrationProbe.requireEquals(false, Config.CLIENT.enableOnlineTTS.get(), "non-dev/1.21.1 legacy value changed CLIENT config");
        }
    }

    public static final class CustomConfigDirectoryProbe {
        private CustomConfigDirectoryProbe() {
        }

        public static void main(String[] args) {
            Path configDirectory = Path.of(args[0]).toAbsolutePath().normalize();
            MCA.platformHelper = new PlatformHelper() {
                @Override
                public Path getConfigDirectory() {
                    return configDirectory;
                }
            };

            NeoForgeTestConfigLoader.loadDefaults(Config.CLIENT_SPEC);

            Path clientPath = configDirectory.resolve(Config.CLIENT_FILE_NAME);
            MigrationProbe.requireEquals(true, Config.migrateLegacy(Config.CLIENT_SPEC, clientPath), "legacy JSON was not read from the loader config directory");
            MigrationProbe.requireEquals(true, Config.CLIENT.enableOnlineTTS.get(), "legacy value from the loader config directory was not migrated");
        }
    }

    /** Checks every matching native value against an isolated copy of the actual dev/1.21.1 JSON. */
    public static final class RealDev1211Probe {
        private static final Gson GSON = new Gson();

        private RealDev1211Probe() {
        }

        public static void main(String[] args) throws Exception {
            Path configDir = Path.of("config").toAbsolutePath().normalize();
            JsonObject legacy;
            try (var reader = Files.newBufferedReader(configDir.resolve(Config.LEGACY_FILE_NAME))) {
                legacy = JsonParser.parseReader(reader).getAsJsonObject();
            }
            if (legacy.get("version").getAsInt() != 2) {
                throw new AssertionError("The real fixture must be dev/1.21.1 schema v2");
            }

            CommentedConfig common = CommentedConfig.inMemory();
            CommentedConfig server = CommentedConfig.inMemory();
            CommentedConfig client = CommentedConfig.inMemory();
            NeoForgeTestConfigLoader.loadConfig(Config.COMMON_SPEC, common);
            NeoForgeTestConfigLoader.loadConfig(Config.SERVER_SPEC, server);
            NeoForgeTestConfigLoader.loadConfig(Config.CLIENT_SPEC, client);
            Path serverPath = configDir.resolve(Config.SERVER_FILE_NAME);
            if (Config.migrateLegacy(Config.SERVER_SPEC, configDir.resolve("world/serverconfig/mca-server.toml"))) {
                throw new AssertionError("A world SERVER override imported the legacy global config");
            }
            if (!Config.migrateLegacy(Config.COMMON_SPEC, configDir.resolve(Config.COMMON_FILE_NAME))
                    || !Config.migrateLegacy(Config.SERVER_SPEC, serverPath)
                    || !Config.migrateLegacy(Config.CLIENT_SPEC, configDir.resolve(Config.CLIENT_FILE_NAME))) {
                throw new AssertionError("One or more first-launch native configs did not import");
            }

            int covered = countPreservedValues(Config.COMMON_SPEC.getValues(), legacy)
                    + countPreservedValues(Config.SERVER_SPEC.getValues(), legacy)
                    + countPreservedValues(Config.CLIENT_SPEC.getValues(), legacy);
            if (covered < 100) {
                throw new AssertionError("Only " + covered + " real legacy fields were checked");
            }
            if (Config.migrateLegacy(Config.SERVER_SPEC, serverPath)) {
                throw new AssertionError("SERVER legacy import ran twice");
            }
            saveToml(common, configDir.resolve(Config.COMMON_FILE_NAME));
            saveToml(server, serverPath);
            saveToml(client, configDir.resolve(Config.CLIENT_FILE_NAME));
            System.out.println("Verified " + covered + " real dev/1.21.1 legacy fields");
        }

        private static void saveToml(CommentedConfig config, Path path) throws Exception {
            try (var writer = Files.newBufferedWriter(path)) {
                new TomlWriter().write(config, writer);
            }
        }

        private static int countPreservedValues(UnmodifiableConfig section, JsonObject legacy) {
            int checked = 0;
            for (UnmodifiableConfig.Entry entry : section.entrySet()) {
                if (entry.getRawValue() instanceof UnmodifiableConfig nested) {
                    checked += countPreservedValues(nested, legacy);
                } else if (entry.getRawValue() instanceof ModConfigSpec.ConfigValue<?> value
                        && legacy.has(entry.getKey()) && !legacy.get(entry.getKey()).isJsonNull()) {
                    Object expected = legacyValue(legacy.get(entry.getKey()), value.getDefault());
                    if (!value.getSpec().test(expected)) {
                        throw new AssertionError("Actual dev/1.21.1 field rejected by the native spec: " + entry.getKey());
                    }
                    if (!java.util.Objects.equals(expected, value.get())) {
                        throw new AssertionError("Legacy field was not preserved: " + entry.getKey());
                    }
                    checked++;
                }
            }
            return checked;
        }

        private static Object legacyValue(JsonElement element, Object defaultValue) {
            if (defaultValue instanceof Boolean) return element.getAsBoolean();
            if (defaultValue instanceof Integer) return element.getAsInt();
            if (defaultValue instanceof Double) return element.getAsDouble();
            if (defaultValue instanceof String) return element.getAsString();
            if (defaultValue instanceof List<?>) {
                if (element.isJsonArray()) {
                    List<String> values = new ArrayList<>();
                    element.getAsJsonArray().forEach(item -> values.add(item.getAsString()));
                    return values;
                }
                if (element.isJsonObject()) {
                    return element.getAsJsonObject().entrySet().stream()
                            .sorted(java.util.Map.Entry.comparingByKey())
                            .map(entry -> entry.getKey() + "=" + (entry.getValue().isJsonPrimitive()
                                    && entry.getValue().getAsJsonPrimitive().isString()
                                    ? entry.getValue().getAsString() : GSON.toJson(entry.getValue())))
                            .toList();
                }
            }
            throw new AssertionError("Unexpected legacy field shape for " + defaultValue.getClass().getSimpleName());
        }
    }

    /** A separate JVM proves an existing TOML wins over the still-present legacy JSON. */
    public static final class RealDev1211SecondLaunchProbe {
        private RealDev1211SecondLaunchProbe() {
        }

        public static void main(String[] args) throws Exception {
            Path configDir = Path.of("config").toAbsolutePath().normalize();
            JsonObject legacy;
            try (var reader = Files.newBufferedReader(configDir.resolve(Config.LEGACY_FILE_NAME))) {
                legacy = JsonParser.parseReader(reader).getAsJsonObject();
            }
            loadToml(Config.COMMON_SPEC, configDir.resolve(Config.COMMON_FILE_NAME));
            loadToml(Config.SERVER_SPEC, configDir.resolve(Config.SERVER_FILE_NAME));
            loadToml(Config.CLIENT_SPEC, configDir.resolve(Config.CLIENT_FILE_NAME));
            if (Config.migrateLegacy(Config.COMMON_SPEC, configDir.resolve(Config.COMMON_FILE_NAME))
                    || Config.migrateLegacy(Config.SERVER_SPEC, configDir.resolve(Config.SERVER_FILE_NAME))
                    || Config.migrateLegacy(Config.CLIENT_SPEC, configDir.resolve(Config.CLIENT_FILE_NAME))) {
                throw new AssertionError("Second launch imported legacy JSON over existing native TOML");
            }
            int covered = RealDev1211Probe.countPreservedValues(Config.COMMON_SPEC.getValues(), legacy)
                    + RealDev1211Probe.countPreservedValues(Config.SERVER_SPEC.getValues(), legacy)
                    + RealDev1211Probe.countPreservedValues(Config.CLIENT_SPEC.getValues(), legacy);
            if (covered < 100) {
                throw new AssertionError("Second launch lost real legacy values");
            }
        }

        private static void loadToml(ModConfigSpec spec, Path path) throws Exception {
            try (var reader = Files.newBufferedReader(path)) {
                NeoForgeTestConfigLoader.loadConfig(spec, new TomlParser().parse(reader));
            }
        }
    }
}
