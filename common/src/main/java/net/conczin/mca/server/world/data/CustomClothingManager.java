package net.conczin.mca.server.world.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.conczin.mca.MCA;
import net.conczin.mca.network.s2c.AppearanceCatalogSync;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.Hair;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

public class CustomClothingManager {
    static final Storage<Clothing> CLOTHING_DUMMY = new Storage<>();
    static final Storage<Hair> HAIR_DUMMY = new Storage<>();

    public static Storage<Clothing> getClothing() {
        Optional<MinecraftServer> server = MCA.getServer();
        return server.map(CustomClothingManager::getClothing).orElse(CLOTHING_DUMMY);
    }

    public static Storage<Clothing> getClothing(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(Storage::new, (nbt, provider) -> new Storage<>(nbt, Clothing::new), null), "immersive_library_clothing");
    }

    public static Storage<Hair> getHair() {
        Optional<MinecraftServer> server = MCA.getServer();
        return server.map(CustomClothingManager::getHair).orElse(HAIR_DUMMY);
    }

    public static Storage<Hair> getHair(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(Storage::new, (nbt, provider) -> new Storage<>(nbt, Hair::new), null), "immersive_library_hair");
    }

    public static class Storage<T extends SkinListEntry> extends SavedData {
        final Map<String, T> entries = new HashMap<>();

        public Storage() {
        }

        public Storage(CompoundTag nbt, BiFunction<String, JsonObject, T> entryFromNbt) {
            Gson gson = new Gson();
            for (String identifier : nbt.getAllKeys()) {
                entries.put(identifier, entryFromNbt.apply(identifier, gson.fromJson(nbt.getString(identifier), JsonObject.class)));
            }
        }

        @Override
        public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
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
            MCA.getServer().ifPresent(AppearanceCatalogSync::sendToAll);
        }
    }
}
