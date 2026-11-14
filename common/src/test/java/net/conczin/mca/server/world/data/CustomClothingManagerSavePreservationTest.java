package net.conczin.mca.server.world.data;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

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
        Path file = dataFolder.resolve(id + ".dat");
        Files.write(file, invalidNbt);
        DimensionDataStorage storage = new DimensionDataStorage(dataFolder.toFile(), DataFixers.getDataFixer(), null);
        CustomClothingManager.Storage<?> entries = CustomClothingManager.loadSafely(
                storage, dataFolder, id, (identifier, json) -> null);
        entries.setDirty();
        storage.save();
        assertArrayEquals(invalidNbt, Files.readAllBytes(file));
    }
}