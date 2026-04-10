package net.conczin.mca.server.world.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

public class CustomClothingManager {
    static final Storage<Clothing> CLOTHING_DUMMY = new Storage<>();
    static final Storage<Hair> HAIR_DUMMY = new Storage<>();

    public static Storage<Clothing> getClothing() {
        Optional<MinecraftServer> server = MCA.getServer();
        return server.<Storage<Clothing>>map(minecraftServer -> minecraftServer.overworld().getDataStorage()
                .computeIfAbsent(new SavedDataType<>(
                        "immersive_library_clothing",
                        Storage::new,
                        CompoundTag.CODEC.xmap(nbt -> new Storage<>(nbt, Clothing::new), data -> data.save(new CompoundTag(), minecraftServer.registryAccess())),
                        DataFixTypes.LEVEL
                ))).orElse(CLOTHING_DUMMY);
    }

    public static Storage<Hair> getHair() {
        Optional<MinecraftServer> server = MCA.getServer();
        return server.<Storage<Hair>>map(minecraftServer -> minecraftServer.overworld().getDataStorage()
                .computeIfAbsent(new SavedDataType<>(
                        "immersive_library_hair",
                        Storage::new,
                        CompoundTag.CODEC.xmap(nbt -> new Storage<>(nbt, Hair::new), data -> data.save(new CompoundTag(), minecraftServer.registryAccess())),
                        DataFixTypes.LEVEL
                ))).orElse(HAIR_DUMMY);
    }

    public static class Storage<T extends SkinListEntry> extends SavedData implements WorldUtils.NbtSavedData {
        final Map<String, T> entries = new HashMap<>();

        public Storage() {
        }

        public Storage(CompoundTag nbt, BiFunction<String, JsonObject, T> entryFromNbt) {
            Gson gson = new Gson();
            for (String identifier : nbt.keySet()) {
                entries.put(identifier, entryFromNbt.apply(identifier, gson.fromJson(nbt.getString(identifier).orElse("{}"), JsonObject.class)));
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

            MCA.getServer().ifPresent(s -> {
                for (ServerPlayer player : s.getPlayerList().getPlayers()) {
                    Network.sendToPlayer(new CustomSkinsChangedMessage(), player);
                }
            });
        }
    }
}
