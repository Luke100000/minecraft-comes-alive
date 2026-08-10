package net.mca.server.world.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.mca.Config;
import net.mca.MCA;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.s2c.CustomSkinsChangedMessage;
import net.mca.resources.data.skin.Clothing;
import net.mca.resources.data.skin.Hair;
import net.mca.resources.data.skin.SkinListEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

public class CustomClothingManager {
    static final Storage<Clothing> CLOTHING_DUMMY = new Storage<>();
    static final Storage<Hair> HAIR_DUMMY = new Storage<>();

    public static boolean canModifyGlobalContent(ServerPlayer player) {
        return Config.getServerConfig().allowEveryoneToAddContentGlobally || player.hasPermissions(4);
    }

    public static Storage<Clothing> getClothing() {
        Optional<MinecraftServer> server = MCA.getServer();
        if (server.isPresent()) {
            return server.get().overworld().getDataStorage()
                    .computeIfAbsent(nbt -> new Storage<>(nbt, Clothing::new), Storage::new, "immersive_library_clothing");
        } else {
            return CLOTHING_DUMMY;
        }
    }

    public static Storage<Hair> getHair() {
        Optional<MinecraftServer> server = MCA.getServer();
        if (server.isPresent()) {
            return server.get().overworld().getDataStorage()
                    .computeIfAbsent(nbt -> new Storage<>(nbt, Hair::new), Storage::new, "immersive_library_hair");
        } else {
            return HAIR_DUMMY;
        }
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
        public CompoundTag save(CompoundTag nbt) {
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
                    NetworkHandler.sendToPlayer(new CustomSkinsChangedMessage(), player);
                }
            });
        }
    }
}
