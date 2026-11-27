package net.conczin.mca.server.world.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.CustomSkinsChangedMessage;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.Hair;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.SavedDataStorage;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

public class CustomClothingManager {
    static final Storage<Clothing> CLOTHING_DUMMY = new Storage<>();
    static final Storage<Hair> HAIR_DUMMY = new Storage<>();

    public static boolean canEdit(ServerPlayer player) {
        return player.permissions().hasPermission(new Permission.HasCommandLevel(
                PermissionLevel.byId(Config.getInstance().addContentGloballyPermissionLevel)
        )) || player.level().getServer().isSingleplayerOwner(player.nameAndId());
    }

    public static Storage<Clothing> getClothing() {
        Optional<MinecraftServer> server = MCA.getServer();
        return server.<Storage<Clothing>>map(minecraftServer -> {
            var world = minecraftServer.overworld();
            var storage = world.getDataStorage();
            return loadSafely(storage, WorldUtils.getSavedDataFolder(storage), world.registryAccess(),
                    "immersive_library_clothing", Clothing::new);
        }).orElse(CLOTHING_DUMMY);
    }

    public static Storage<Hair> getHair() {
        Optional<MinecraftServer> server = MCA.getServer();
        return server.<Storage<Hair>>map(minecraftServer -> {
            var world = minecraftServer.overworld();
            var storage = world.getDataStorage();
            return loadSafely(storage, WorldUtils.getSavedDataFolder(storage), world.registryAccess(),
                    "immersive_library_hair", Hair::new);
        }).orElse(HAIR_DUMMY);
    }

    static <T extends SkinListEntry> Storage<T> loadSafely(SavedDataStorage storage, Path dataFolder,
                                                              HolderLookup.Provider registries, String dataId,
                                                              BiFunction<String, JsonObject, T> entryFromNbt) {
        return WorldUtils.loadDataSafely(
                storage,
                dataFolder,
                registries,
                dataId,
                Storage::new,
                (nbt, provider) -> new Storage<>(nbt, entryFromNbt),
                (data, path) -> {
                    data.unreadableSavedData = true;
                    data.setDirty(false);
                    MCA.LOGGER.error("MCA custom skin data {} could not be loaded. Original save will not be overwritten.", path);
                }
        );
    }

    public static class Storage<T extends SkinListEntry> extends SavedData {
        final Map<String, T> entries = new HashMap<>();
        private boolean unreadableSavedData;

        public Storage() {
        }

        public Storage(CompoundTag nbt, BiFunction<String, JsonObject, T> entryFromNbt) {
            Gson gson = new Gson();
            for (String identifier : nbt.keySet()) {
                entries.put(identifier, entryFromNbt.apply(identifier, gson.fromJson(nbt.getString(identifier).orElse("{}"), JsonObject.class)));
            }
        }

        @Override
        public void setDirty(boolean dirty) {
            super.setDirty(dirty && !unreadableSavedData);
        }

        public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
            return toTag();
        }

        public CompoundTag toTag() {
            CompoundTag c = new CompoundTag();
            for (Map.Entry<String, T> entry : entries.entrySet()) {
                c.putString(entry.getKey(), entry.getValue().toJson().toString());
            }
            return c;
        }

        public Map<String, T> getEntries() {
            return entries;
        }

        public void addEntry(String id, T entry) {
            entries.put(id, entry);
            setDirty();
        }

        public void removeEntry(String id) {
            entries.remove(id);
            setDirty();
        }

        @Override
        public void setDirty() {
            super.setDirty();

            MCA.getServer().ifPresent(s -> {
                for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                    Network.sendToPlayer(new CustomSkinsChangedMessage(), player);
                }
            });
        }
    }
}

