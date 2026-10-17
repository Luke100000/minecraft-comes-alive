package net.conczin.mca;

import net.conczin.mca.config.ConfigSchema;
import net.conczin.mca.config.ConfigScope;
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
        ServerConfig server = new ServerConfig(ConfigSchema.builder(ConfigScope.SERVER));
        Map<String, Integer> original = server.guardsTargetEntities();

        reloadGuards(server, "minecraft:zombie=4");
        Map<String, Integer> firstServer = server.guardsTargetEntities();
        assertEquals(Map.of("minecraft:zombie", 4), firstServer);
        assertNotSame(original, firstServer);

        reloadGuards(server, "minecraft:zombie=9");
        Map<String, Integer> secondServer = server.guardsTargetEntities();
        assertEquals(Map.of("minecraft:zombie", 9), secondServer);
        assertNotSame(firstServer, secondServer);

        reloadGuards(server, "minecraft:zombie=9");
        assertSame(secondServer, server.guardsTargetEntities(),
                "identical server values need not be decoded again");

        server.guardsTargetEntities.set(server.guardsTargetEntities.getDefault());
        assertEquals(original, server.guardsTargetEntities(),
                "returning to the initial server must restore its values");
    }

    private static void reloadGuards(ServerConfig server, String entry) {
        server.guardsTargetEntities.set(List.of(entry));
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
