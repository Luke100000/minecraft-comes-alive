package net.conczin.mca.server.world.data;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.conczin.mca.resources.data.skin.Hair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomClothingManagerSavePreservationTest {
    @TempDir Path dataFolder;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void unreadableClothingFileIsNotOverwrittenByLaterChanges() throws Exception {
        assertUnreadableFilePreserved("immersive_library_clothing");
    }

    @Test
    void unreadableHairFileIsNotOverwrittenByLaterChanges() throws Exception {
        assertUnreadableFilePreserved("immersive_library_hair");
    }

    private void assertUnreadableFilePreserved(String id) throws Exception {
        byte[] invalidNbt = {0x1f, (byte) 0x8b, 0x08, 0x00, 0x11};
        Path file = dataFolder.resolve("mca").resolve(id + ".dat");
        Files.createDirectories(file.getParent());
        Files.write(file, invalidNbt);
        try (SavedDataStorage storage = new SavedDataStorage(dataFolder, DataFixers.getDataFixer(), RegistryAccess.EMPTY)) {
            CustomClothingManager.Storage<?> entries = CustomClothingManager.loadSafely(
                    storage, dataFolder, RegistryAccess.EMPTY, id, (identifier, json) -> null);
            entries.setDirty();
            entries.setDirty(true);
            CustomClothingManager.Storage<Hair> healthy = CustomClothingManager.loadSafely(
                    storage, dataFolder, RegistryAccess.EMPTY, "healthy_sibling", Hair::new);
            healthy.addEntry("mca:test_hair", new Hair("mca:test_hair"));
            storage.saveAndJoin();
            assertTrue(Files.isRegularFile(dataFolder.resolve("mca/healthy_sibling.dat")));
        }
        assertArrayEquals(invalidNbt, Files.readAllBytes(file));
    }
}
