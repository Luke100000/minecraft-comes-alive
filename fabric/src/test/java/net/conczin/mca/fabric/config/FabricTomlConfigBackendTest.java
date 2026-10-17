package net.conczin.mca.fabric.config;

import net.conczin.mca.Config;
import net.conczin.mca.config.ConfigScope;
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
        backend.loadGlobal(false);
        assertTrue(Files.isRegularFile(root.resolve("config/mca-common.toml")));
        assertTrue(Files.isRegularFile(root.resolve("config/mca-server.toml")));
        assertFalse(Files.exists(root.resolve("config/mca-client.toml")), "dedicated servers must not touch CLIENT");
        assertEquals(24000, Config.SERVER.babyItemGrowUpTime.get());
        assertEquals(true, Config.SERVER.enableVillagerCollisions.get());
        backend.loadGlobal(true);
        assertTrue(Files.isRegularFile(root.resolve("config/mca-client.toml")));
    }

    @Test
    void validOverridesRoundTripWithCorrectScopes() throws Exception {
        Path dir = root.resolve("config");
        FabricTomlConfigBackend backend = new FabricTomlConfigBackend(dir);
        backend.loadGlobal(true);
        Config.SERVER.babyItemGrowUpTime.set(42000);
        Config.CLIENT.showNameTags.set(false);
        backend.save(ConfigScope.SERVER);
        backend.save(ConfigScope.CLIENT);

        new FabricTomlConfigBackend(dir).loadGlobal(true);
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
        new FabricTomlConfigBackend(dir).loadGlobal(false);
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
        backend.loadGlobal(false);
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
        new FabricTomlConfigBackend(dir).loadGlobal(false);
        assertArrayEquals(content, Files.readAllBytes(file));
    }

    @Test
    void worldOverridesAreLocalAndRestoreGlobalValues() throws Exception {
        Path dir = root.resolve("config");
        FabricTomlConfigBackend backend = new FabricTomlConfigBackend(dir);
        backend.loadGlobal(false);
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
}
