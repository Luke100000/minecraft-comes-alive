package net.conczin.mca.server.world.data;

import com.google.gson.JsonObject;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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

        SavedDataStorage storage = storage();
        VillageManager manager = VillageManager.loadSafely(null, storage, dataFolder);

        assertNotNull(manager);
        assertTrue(manager.hasUnreadableSavedData());
        manager.reportBuilding(BlockPos.ZERO);
        assertTrue(manager.cache.isEmpty(), "Unreadable data must not queue new village work");
        assertEquals(Building.validationResult.TARGET_CHANGED, manager.processBuilding(BlockPos.ZERO));
        assertEquals(Building.validationResult.TARGET_CHANGED, manager.commitRoomAddition(null, null));
        assertEquals(Building.validationResult.TARGET_CHANGED, manager.commitRegisteredRoomUpdate(null, null));
        manager.setDirty();
        manager.setDirty(true);
        storage.saveAndJoin();
        assertFalse(Files.exists(dataFolder.resolve("mca/mca_villages.dat")));
        assertArrayEquals(originalBytes, Files.readAllBytes(file),
                "Unsupported village data must not be replaced by an empty manager");
    }

    @Test
    void partialLoadKeepsHealthyVillagesReadableAndPreservesTheOriginalFile() throws Exception {
        Village healthy = new Village(42, null);
        BlockPos floorCell = new BlockPos(0, 64, 0);
        var geometry = new FloorGeometry(List.of(new FloorGeometry.Cell(floorCell, 67)), List.of());
        var floor = new StructureFloor(0, 0, 64, geometry);
        Building room = new Building(floorCell);
        room.setId(10);
        room.setStructureId(20);
        room.setFloorId(0);
        room.setType("house");
        room.setGeometry(floorCell, floorCell.above(2), List.of(floorCell));
        healthy.registerStructure(new Structure(20, floorCell, List.of(floor)), room);

        CompoundTag malformed = new Village(43, null).save();
        ListTag badStructures = new ListTag();
        badStructures.add(new CompoundTag());
        malformed.put("structures", badStructures);

        ListTag villages = new ListTag();
        villages.add(healthy.save());
        villages.add(malformed);
        CompoundTag data = new CompoundTag();
        data.put("villages", villages);
        CompoundTag root = new CompoundTag();
        root.put("data", data);
        NbtUtils.addCurrentDataVersion(root);

        Path file = dataFolder.resolve("mca_villages.dat");
        NbtIo.writeCompressed(root, file);
        byte[] originalBytes = Files.readAllBytes(file);
        SavedDataStorage storage = storage();
        VillageManager manager = VillageManager.loadSafely(null, storage, dataFolder);

        assertTrue(manager.getOrEmpty(42).isPresent(), "Healthy villages must remain available");
        assertTrue(manager.getOrEmpty(43).isEmpty(), "Malformed villages must be isolated");
        manager.setDirty();
        storage.saveAndJoin();
        assertFalse(Files.exists(dataFolder.resolve("mca/mca_villages.dat")));
        assertArrayEquals(originalBytes, Files.readAllBytes(file), "Partial loading must preserve the original save");
    }

    @Test
    void quarantinedRoomKeepsHealthyRoomsReadableWithoutOverwritingOriginalSave() throws Exception {
        CompoundTag villageTag = healthyVillageTag();
        ListTag rooms = villageTag.getList("buildings").orElseThrow();
        CompoundTag invalidRoom = rooms.getCompound(0).orElseThrow().copy();
        invalidRoom.putInt("id", 11);
        invalidRoom.remove("floorCellPositions");
        invalidRoom.remove("floorCells");
        rooms.add(invalidRoom);

        assertQuarantinedSavePreserved(villageTag, false);
    }

    @Test
    void orphanedStructureReconciliationPreservesOriginalSave() throws Exception {
        CompoundTag village = healthyVillageTag();
        ListTag structures = village.getList("structures").orElseThrow();
        CompoundTag orphan = structures.getCompound(0).orElseThrow().copy();
        orphan.putInt("id", 21);
        orphan.putInt("buildingId", 99);
        structures.add(orphan);

        assertReconciledSavePreserved(village);
    }

    @Test
    void emptyLogicalBuildingReconciliationPreservesOriginalSave() throws Exception {
        CompoundTag village = healthyVillageTag();
        village.getList("logicalBuildings").orElseThrow()
                .add(new LogicalBuilding(99, -1, false).save());

        assertReconciledSavePreserved(village);
    }

    @Test
    void missingCustomRoomTypeSurvivesSaveAndWorksWhenRestored() throws Exception {
        BuildingTypes types = BuildingTypes.getInstance();
        Map<String, BuildingType> previousTypes = types.getBuildingTypes();
        BuildingType generic = new BuildingType("building", new JsonObject());
        BuildingType custom = new BuildingType("test_custom_room", new JsonObject());
        try {
            types.setBuildingTypes(Map.of(generic.name(), generic));
            CompoundTag village = healthyVillageTag();
            CompoundTag room = village.getList("buildings").orElseThrow().getCompound(0).orElseThrow();
            room.putString("type", custom.name());
            room.putBoolean("isTypeForced", true);
            writeVillage(village);

            SavedDataStorage storage = storage();
            VillageManager manager = VillageManager.loadSafely(null, storage, dataFolder);
            Village loaded = manager.getOrEmpty(42).orElseThrow();
            assertEquals("building", loaded.getEffectiveBuildingType(loaded.getBuilding(10).orElseThrow()).name());
            loaded.setName("updated");
            manager.setDirty();
            storage.saveAndJoin();

            types.setBuildingTypes(Map.of(generic.name(), generic, custom.name(), custom));
            Village reloaded = VillageManager.loadSafely(null, storage(), dataFolder).getOrEmpty(42).orElseThrow();
            Building reloadedRoom = reloaded.getBuilding(10).orElseThrow();
            assertEquals("updated", reloaded.getName(), "Missing room types must not block unrelated saves");
            assertTrue(reloadedRoom.isTypeForced());
            assertEquals(custom.name(), reloaded.getEffectiveBuildingType(reloadedRoom).name());
        } finally {
            types.setBuildingTypes(previousTypes);
        }
    }

    private void assertReconciledSavePreserved(CompoundTag village) throws IOException {
        Path file = writeVillage(village);
        byte[] originalBytes = Files.readAllBytes(file);
        SavedDataStorage storage = storage();
        VillageManager manager = VillageManager.loadSafely(null, storage, dataFolder);
        Village loaded = manager.getOrEmpty(42).orElseThrow();
        assertTrue(loaded.getBuilding(10).isPresent(), "The healthy room should remain usable");
        loaded.setName("updated");
        manager.setDirty();
        storage.saveAndJoin();
        assertFalse(Files.exists(dataFolder.resolve("mca/mca_villages.dat")));
        assertArrayEquals(originalBytes, Files.readAllBytes(file),
                "Load-time reconciliation must preserve the source save when it removes building data");
    }

    private Path writeVillage(CompoundTag village) throws IOException {
        ListTag villages = new ListTag();
        villages.add(village);
        CompoundTag data = new CompoundTag();
        data.put("villages", villages);
        CompoundTag root = new CompoundTag();
        root.put("data", data);
        NbtUtils.addCurrentDataVersion(root);
        Path file = dataFolder.resolve("mca_villages.dat");
        NbtIo.writeCompressed(root, file);
        return file;
    }

    @Test
    void quarantinedExternalBuildingKeepsHealthyRoomsReadableWithoutOverwritingOriginalSave() throws Exception {
        BuildingTypes types = BuildingTypes.getInstance();
        Map<String, BuildingType> previousTypes = types.getBuildingTypes();
        try {
            types.setBuildingTypes(Map.of(
                    "house", new BuildingType("house", new JsonObject()),
                    "building", new BuildingType("building", new JsonObject())));
            CompoundTag villageTag = healthyVillageTag();
            ExternalBuilding invalid = new ExternalBuilding(new BlockPos(20, 64, 20));
            invalid.setId(30);
            invalid.setType("house");
            villageTag.getList("externalBuildings").orElseThrow().add(invalid.save());

            assertQuarantinedSavePreserved(villageTag, true);
        } finally {
            types.setBuildingTypes(previousTypes);
        }
    }

    private void assertQuarantinedSavePreserved(CompoundTag villageTag, boolean externalQuarantine) throws Exception {
        ListTag villages = new ListTag();
        villages.add(villageTag);
        CompoundTag data = new CompoundTag();
        data.put("villages", villages);
        CompoundTag root = new CompoundTag();
        root.put("data", data);
        NbtUtils.addCurrentDataVersion(root);

        Path file = dataFolder.resolve("mca_villages.dat");
        NbtIo.writeCompressed(root, file);
        byte[] originalBytes = Files.readAllBytes(file);
        SavedDataStorage storage = storage();
        VillageManager manager = VillageManager.loadSafely(null, storage, dataFolder);

        Village loadedVillage = manager.getOrEmpty(42).orElseThrow();
        assertEquals(1, loadedVillage.getBuildings().size(), "The healthy room must remain usable");
        assertTrue(loadedVillage.getBuildings().containsKey(10));
        if (externalQuarantine) {
            assertTrue(loadedVillage.getExternalBuildingMap().isEmpty(), "Invalid external site must be quarantined");
        } else {
            assertFalse(loadedVillage.getBuildings().containsKey(11), "Invalid room must be quarantined");
        }
        assertTrue(manager.hasUnreadableSavedData(), "Quarantined persisted data must protect the original save");
        manager.setDirty();
        storage.saveAndJoin();
        assertFalse(Files.exists(dataFolder.resolve("mca/mca_villages.dat")));
        assertArrayEquals(originalBytes, Files.readAllBytes(file));
    }

    private static CompoundTag healthyVillageTag() {
        Village village = new Village(42, null);
        BlockPos floorCell = new BlockPos(0, 64, 0);
        var geometry = new FloorGeometry(List.of(new FloorGeometry.Cell(floorCell, 67)), List.of());
        var floor = new StructureFloor(0, 0, 64, geometry);
        Building room = new Building(floorCell);
        room.setId(10);
        room.setStructureId(20);
        room.setFloorId(0);
        room.setType("house");
        room.setGeometry(floorCell, floorCell.above(2), List.of(floorCell));
        village.registerStructure(new Structure(20, floorCell, List.of(floor)), room);
        return village.save();
    }

    @Test
    void unreadableCompressedNbtNeverOverwritesTheExistingSaveAfterLaterMutation() throws Exception {
        Path file = dataFolder.resolve("mca_villages.dat");
        byte[] invalidNbt = new byte[]{0x1f, (byte) 0x8b, 0x08, 0x00, 0x11};
        Files.write(file, invalidNbt);

        SavedDataStorage storage = storage();
        VillageManager manager = VillageManager.loadSafely(null, storage, dataFolder);

        assertNotNull(manager);
        manager.setDirty();
        storage.saveAndJoin();
        assertFalse(Files.exists(dataFolder.resolve("mca/mca_villages.dat")));
        assertArrayEquals(invalidNbt, Files.readAllBytes(file),
                "Failure decoding the NBT before the deserializer must not erase village data");
    }

    private SavedDataStorage storage() {
        return new SavedDataStorage(dataFolder, DataFixers.getDataFixer(), RegistryAccess.EMPTY);
    }
}
