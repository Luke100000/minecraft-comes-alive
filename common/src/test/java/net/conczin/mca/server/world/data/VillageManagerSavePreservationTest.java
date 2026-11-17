package net.conczin.mca.server.world.data;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VillageManagerSavePreservationTest {
    @TempDir Path dataFolder;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void unsupportedVillageVersionNeverOverwritesTheExistingSaveAfterLaterMutation() throws Exception {
        CompoundTag village = new CompoundTag();
        village.putInt("id", 42);
        village.putString("name", "existing-village");
        village.putInt("buildingDataVersion", 999);
        ListTag villages = new ListTag();
        villages.add(village);
        CompoundTag data = new CompoundTag();
        data.put("villages", villages);
        CompoundTag root = new CompoundTag();
        root.put("data", data);
        NbtUtils.addCurrentDataVersion(root);

        Path file = dataFolder.resolve("mca_villages.dat");
        NbtIo.writeCompressed(root, file);
        byte[] originalBytes = Files.readAllBytes(file);

        DimensionDataStorage storage = new DimensionDataStorage(dataFolder.toFile(), DataFixers.getDataFixer(), null);
        VillageManager manager = VillageManager.loadSafely(null, storage, dataFolder);

        assertNotNull(manager);
        assertTrue(manager.hasUnreadableSavedData());
        manager.reportBuilding(BlockPos.ZERO);
        assertTrue(manager.cache.isEmpty(), "Unreadable data must not queue new village work");
        assertEquals(Building.validationResult.TARGET_CHANGED, manager.processBuilding(BlockPos.ZERO));
        assertEquals(Building.validationResult.TARGET_CHANGED, manager.commitRoomAddition(null, null));
        assertEquals(Building.validationResult.TARGET_CHANGED, manager.commitRegisteredRoomUpdate(null, null));
        manager.setDirty();
        storage.save();
        assertArrayEquals(originalBytes, Files.readAllBytes(file),
                "Unsupported village data must not be replaced by an empty manager");
    }

    @Test
    void unreadableCompressedNbtNeverOverwritesTheExistingSaveAfterLaterMutation() throws Exception {
        Path file = dataFolder.resolve("mca_villages.dat");
        byte[] invalidNbt = new byte[]{0x1f, (byte) 0x8b, 0x08, 0x00, 0x11};
        Files.write(file, invalidNbt);

        DimensionDataStorage storage = new DimensionDataStorage(dataFolder.toFile(), DataFixers.getDataFixer(), null);
        VillageManager manager = VillageManager.loadSafely(null, storage, dataFolder);

        assertNotNull(manager);
        manager.setDirty();
        storage.save();
        assertArrayEquals(invalidNbt, Files.readAllBytes(file),
                "Failure decoding the NBT before the deserializer must not erase village data");
    }
}
