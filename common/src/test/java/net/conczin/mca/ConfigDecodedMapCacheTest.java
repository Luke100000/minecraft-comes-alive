package net.conczin.mca;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.neoforged.fml.config.NeoForgeTestConfigLoader;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class ConfigDecodedMapCacheTest {
    @Test
    void reloadingServerConfigReplacesPriorServerMapWithoutRetainingStaleValues() {
        Pair<ServerConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(ServerConfig::new);
        ServerConfig server = pair.getLeft();
        ModConfigSpec spec = pair.getRight();

        CommentedConfig initial = CommentedConfig.inMemory();
        NeoForgeTestConfigLoader.loadConfig(spec, initial);
        Map<String, Integer> original = server.guardsTargetEntities();

        reloadGuards(spec, server, "minecraft:zombie=4");
        Map<String, Integer> firstServer = server.guardsTargetEntities();
        assertEquals(Map.of("minecraft:zombie", 4), firstServer);
        assertNotSame(original, firstServer);

        reloadGuards(spec, server, "minecraft:zombie=9");
        Map<String, Integer> secondServer = server.guardsTargetEntities();
        assertEquals(Map.of("minecraft:zombie", 9), secondServer);
        assertNotSame(firstServer, secondServer);

        reloadGuards(spec, server, "minecraft:zombie=9");
        assertSame(secondServer, server.guardsTargetEntities(),
                "identical server values need not be decoded again");

        NeoForgeTestConfigLoader.loadConfig(spec, initial);
        assertEquals(original, server.guardsTargetEntities(),
                "returning to the initial server must restore its values");
    }

    private static void reloadGuards(ModConfigSpec spec, ServerConfig server, String entry) {
        CommentedConfig config = CommentedConfig.inMemory();
        spec.correct(config);
        config.set(server.guardsTargetEntities.getPath(), List.of(entry));
        NeoForgeTestConfigLoader.loadConfig(spec, config);
    }

    @Test
    void cacheDetectsInPlaceChangesAndEquivalentReloadedLists() {
        Config.DecodedMapCache<String, Integer> cache =
                new Config.DecodedMapCache<>(String.class, Integer.class, "guardsTargetEntities");
        ArrayList<String> values = new ArrayList<>(List.of("minecraft:zombie=1"));
        Map<String, Integer> initial = cache.get(values);

        values.set(0, "minecraft:zombie=8");
        Map<String, Integer> edited = cache.get(values);
        assertEquals(Map.of("minecraft:zombie", 8), edited,
                "a mutable source list can change without changing its identity");
        assertNotSame(initial, edited);
        assertSame(edited, cache.get(List.of("minecraft:zombie=8")),
                "reloading identical config content should retain the decoded map");

        Map<String, Integer> reloaded = cache.get(List.of("minecraft:zombie=3"));
        assertEquals(Map.of("minecraft:zombie", 3), reloaded);
        assertNotSame(edited, reloaded);
        assertSame(reloaded, cache.get(List.of("minecraft:zombie=3")));
    }

    @Test
    void guardPrioritiesReuseDecodedMapUntilEncodedConfigChanges() {
        List<? extends String> original = List.copyOf(Config.SERVER.guardsTargetEntities.get());
        try {
            Map<String, Integer> first = Config.SERVER.guardsTargetEntities();
            assertSame(first, Config.SERVER.guardsTargetEntities());

            Config.SERVER.guardsTargetEntities.set(List.of("minecraft:zombie=7"));
            Map<String, Integer> changed = Config.SERVER.guardsTargetEntities();

            assertNotSame(first, changed);
            assertEquals(Map.of("minecraft:zombie", 7), changed);
            assertSame(changed, Config.SERVER.guardsTargetEntities());
        } finally {
            Config.SERVER.guardsTargetEntities.set(original);
            Config.SERVER.guardsTargetEntities();
        }
    }

    @Test
    void shaderLocationsReuseDecodedMapUntilEncodedConfigChanges() {
        List<? extends String> original = List.copyOf(Config.CLIENT.shaderLocationsMap.get());
        try {
            Map<String, String> first = Config.CLIENT.shaderLocationsMap();
            assertSame(first, Config.CLIENT.shaderLocationsMap());

            Config.CLIENT.shaderLocationsMap.set(List.of("test_trait=mca:shaders/post/test.json"));
            Map<String, String> changed = Config.CLIENT.shaderLocationsMap();

            assertNotSame(first, changed);
            assertEquals(Map.of("test_trait", "mca:shaders/post/test.json"), changed);
            assertSame(changed, Config.CLIENT.shaderLocationsMap());
        } finally {
            Config.CLIENT.shaderLocationsMap.set(original);
            Config.CLIENT.shaderLocationsMap();
        }
    }
}
