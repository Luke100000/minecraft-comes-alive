package net.conczin.mca.client.gui.immersive_library;

import net.conczin.mca.client.gui.immersive_library.types.Content;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

class SkinCacheVersionTest {
    @Test
    void olderCompletionCannotReplaceNewerCachedVersion() throws Exception {
        int id = uniqueTestId();
        try {
            SkinCache.store(content(id, 5));
            ResourceLocation newerTexture = ResourceLocation.fromNamespaceAndPath("mca", "version_test");
            SkinCache.textureIdentifiers.put(id, newerTexture);

            SkinCache.store(content(id, 4));

            assertVersionFive(id);
            assertEquals(newerTexture, SkinCache.textureIdentifiers.get(id));
            SkinCache.store(content(id, 5));
            assertVersionFive(id);
        } finally {
            cleanup(id);
        }
    }

    @Test
    void higherCompletionReplacesEarlierVersion() throws Exception {
        int id = uniqueTestId();
        try {
            SkinCache.store(content(id, 4));
            SkinCache.store(content(id, 5));
            assertVersionFive(id);
        } finally {
            cleanup(id);
        }
    }

    private static int uniqueTestId() {
        return ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, -1_000_000);
    }

    private static Content content(int id, int version) {
        return new Content(id, 1, "test", 0, Set.of(), "asset", version, false, Set.of(),
                "{\"version\":" + version + "}", Base64.getEncoder().encodeToString(new byte[]{(byte) version}));
    }

    private static void assertVersionFive(int id) throws Exception {
        Path directory = Path.of("immersive_library");
        assertEquals("5", Files.readString(directory.resolve(id + ".version")));
        assertEquals("{\"version\":5}", Files.readString(directory.resolve(id + ".json")));
        assertArrayEquals(new byte[]{5}, Files.readAllBytes(directory.resolve(id + ".png")));
        assertEquals(5, SkinCache.cachedVersions.get(id));
    }

    private static void cleanup(int id) throws Exception {
        SkinCache.cachedVersions.remove(id);
        SkinCache.textureIdentifiers.remove(id);
        SkinCache.loading.remove(id);
        SkinCache.metas.remove(id);
        SkinCache.requested.remove(id);
        Path directory = Path.of("immersive_library");
        Files.deleteIfExists(directory.resolve(id + ".png"));
        Files.deleteIfExists(directory.resolve(id + ".json"));
        Files.deleteIfExists(directory.resolve(id + ".version"));
    }
}
