package net.conczin.mca.server.world.data;

import com.google.gson.JsonObject;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.Hair;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.BiFunction;

@PrefixGameTestTemplate(false)
public final class SavedDataDiskRecoveryGameTests {
    private SavedDataDiskRecoveryGameTests() {
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void existingPlayerDataReloadsFromFreshStorage(GameTestHelper helper) throws IOException {
        ServerLevel world = helper.getLevel().getServer().overworld();
        Path folder = Files.createTempDirectory("mca-player-reload-");
        UUID uuid = UUID.randomUUID();
        folder.toFile().deleteOnExit();
        folder.resolve("mca_player_" + uuid + ".dat").toFile().deleteOnExit();
        DimensionDataStorage first = new DimensionDataStorage(folder.toFile(), DataFixers.getDataFixer(), world.registryAccess());
        PlayerSaveData initial = PlayerSaveData.loadSafely(world, uuid, first, folder);
        initial.setChatAIPrompt("persisted-value");
        first.save();
        IOUtilities.waitUntilIOWorkerComplete();

        DimensionDataStorage second = new DimensionDataStorage(folder.toFile(), DataFixers.getDataFixer(), world.registryAccess());
        PlayerSaveData reloaded = PlayerSaveData.loadSafely(world, uuid, second, folder);
        helper.assertTrue("persisted-value".equals(reloaded.getChatAIPrompt()),
                "MCA player data was not loaded from disk with the current loader");
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void unreadablePlayerDataIsNeverOverwritten(GameTestHelper helper) throws IOException {
        ServerLevel world = helper.getLevel().getServer().overworld();
        Path folder = Files.createTempDirectory("mca-player-corrupt-");
        UUID uuid = UUID.randomUUID();
        Path file = folder.resolve("mca_player_" + uuid + ".dat");
        byte[] original = {0x1f, (byte) 0x8b, 0x08, 0x00, 0x11};
        folder.toFile().deleteOnExit();
        file.toFile().deleteOnExit();
        Files.write(file, original);
        DimensionDataStorage storage = new DimensionDataStorage(folder.toFile(), DataFixers.getDataFixer(), world.registryAccess());
        PlayerSaveData player = PlayerSaveData.loadSafely(world, uuid, storage, folder);
        player.setChatAIPrompt("do-not-replace-existing-save");
        storage.save();
        IOUtilities.waitUntilIOWorkerComplete();
        helper.assertTrue(Arrays.equals(original, Files.readAllBytes(file)),
                "Unreadable MCA player data must retain its original bytes");
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void existingVillageDataReloadsFromFreshStorage(GameTestHelper helper) throws IOException {
        ServerLevel world = helper.getLevel().getServer().overworld();
        Path folder = Files.createTempDirectory("mca-village-reload-");
        Path savedFile = folder.resolve("mca_villages.dat");
        folder.toFile().deleteOnExit();
        savedFile.toFile().deleteOnExit();
        DimensionDataStorage first = new DimensionDataStorage(folder.toFile(), DataFixers.getDataFixer(), world.registryAccess());
        CompoundTag initialData = new CompoundTag();
        initialData.putInt("lastVillageId", 37);
        initialData.putInt("lastBuildingId", 42);
        initialData.put("villages", new ListTag());
        VillageManager initial = new VillageManager(world, initialData);
        first.set("mca_villages", initial);
        initial.setDirty();
        first.save();
        IOUtilities.waitUntilIOWorkerComplete();
        helper.assertTrue(Files.isRegularFile(savedFile), "MCA village data was not written to disk");

        DimensionDataStorage second = new DimensionDataStorage(folder.toFile(), DataFixers.getDataFixer(), world.registryAccess());
        VillageManager reloaded = VillageManager.loadSafely(world, second, folder);
        helper.assertTrue(!reloaded.hasUnreadableSavedData(),
                "Saved MCA village data was unreadable with the current loader");
        CompoundTag loadedData = reloaded.save(new CompoundTag(), world.registryAccess());
        helper.assertTrue(loadedData.getInt("lastVillageId") == 37 && loadedData.getInt("lastBuildingId") == 42,
                "MCA village state did not round-trip through a fresh disk-backed storage");
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void villageSaveLocationShownToOperatorsIsWorldRelative(GameTestHelper helper) {
        String path = VillageManager.getRelativeSavePath(helper.getLevel().getServer().overworld());
        helper.assertTrue("data/mca_villages.dat".equals(path),
                "The village recovery message must use a world-relative path, not an absolute server path");
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void existingCustomClothingReloadsFromFreshStorage(GameTestHelper helper) throws IOException {
        assertCustomSkinReloads(helper, "immersive_library_clothing",
                new Clothing("mca:test_clothing", null, 0, false, Gender.NEUTRAL), Clothing::new);
        helper.succeed();
    }

    @GameTest(batch = "mca_saved_data_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void existingCustomHairReloadsFromFreshStorage(GameTestHelper helper) throws IOException {
        assertCustomSkinReloads(helper, "immersive_library_hair", new Hair("mca:test_hair"), Hair::new);
        helper.succeed();
    }

    private static <T extends SkinListEntry> void assertCustomSkinReloads(GameTestHelper helper,
            String dataId, T entry, BiFunction<String, JsonObject, T> parser) throws IOException {
        Path folder = Files.createTempDirectory("mca-skin-reload-");
        folder.toFile().deleteOnExit();
        folder.resolve(dataId + ".dat").toFile().deleteOnExit();
        DimensionDataStorage first = new DimensionDataStorage(folder.toFile(), DataFixers.getDataFixer(),
                helper.getLevel().registryAccess());
        CustomClothingManager.Storage<T> original = CustomClothingManager.loadSafely(first, folder, dataId, parser);
        original.addEntry(entry.getIdentifier(), entry);
        first.save();
        IOUtilities.waitUntilIOWorkerComplete();

        DimensionDataStorage second = new DimensionDataStorage(folder.toFile(), DataFixers.getDataFixer(),
                helper.getLevel().registryAccess());
        CustomClothingManager.Storage<T> reloaded = CustomClothingManager.loadSafely(second, folder, dataId, parser);
        helper.assertTrue(reloaded.getEntries().containsKey(entry.getIdentifier()),
                "MCA custom skin data " + dataId + " was not loaded from disk");
    }
}
