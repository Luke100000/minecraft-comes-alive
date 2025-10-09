package net.conczin.mca.entity.ai;

import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

/**
 * The long term memory stored String keys for a given amount of time
 * While not powerful in terms of features it allows adding more intelligence to villager interactions
 */
public class LongTermMemory {
    final HashMap<String, Long> memories = new HashMap<>();

    private final VillagerEntityMCA entity;

    public LongTermMemory(VillagerEntityMCA entity) {
        this.entity = entity;
    }

    public static String parseId(JsonObject json, ServerPlayer player) {
        String id = json.get("id").getAsString();
        if (json.has("var")) {
            switch (json.get("var").getAsString()) {
                case "player" -> {
                    assert player != null;
                    id += "." + player.getUUID();
                }
            }
        }
        return id;
    }

    public void writeToNbt(CompoundTag nbt) {
        CompoundTag memory = new CompoundTag();
        for (Map.Entry<String, Long> entry : memories.entrySet()) {
            memory.putLong(entry.getKey(), entry.getValue());
        }
        nbt.put("longTermMemory", memory);
    }

    public void readFromNbt(CompoundTag nbt) {
        CompoundTag memory = nbt.getCompound("longTermMemory");
        memories.clear();
        for (String key : memory.getAllKeys()) {
            memories.put(key, memory.getLong(key));
        }
    }

    //remember forever
    public void remember(String id) {
        remember(id, Integer.MAX_VALUE);
    }

    public void remember(String id, long time) {
        long currentTime = entity.level().getGameTime();
        if (memories.containsKey(id)) {
            currentTime = Math.max(currentTime, memories.get(id));
        }
        memories.put(id, currentTime + time);
    }

    public long getMemory(String id) {
        if (memories.containsKey(id)) {
            if (entity.level().getGameTime() > memories.get(id)) {
                memories.remove(id);
            } else {
                return memories.get(id) - entity.level().getGameTime();
            }
        }
        return 0;
    }

    public boolean hasMemory(String id) {
        return getMemory(id) > 0;
    }
}
