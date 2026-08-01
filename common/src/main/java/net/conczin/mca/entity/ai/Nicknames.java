package net.conczin.mca.entity.ai;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Nicknames {
    public final HashMap<UUID, String> data = new HashMap<>();

    public void writeToNbt(CompoundTag nbt) {
        CompoundTag nickname = new CompoundTag();
        for (Map.Entry<UUID, String> entry : data.entrySet()) {
            nickname.putString(
                    entry.getKey().toString(),
                    entry.getValue()
            );
        }
        nbt.put("nicknames", nickname);
    }

    public void readFromNbt(CompoundTag nbt) {
        CompoundTag nickname = nbt.getCompound("nicknames");
        data.clear();
        for (String key : nickname.getAllKeys()) {
            data.put(
                    UUID.fromString(key),
                    nickname.getString(key)
            );
        }
    }
}
