package net.mca.client.resources;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;

public class PresetCodec {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static String toJsonString(CompoundTag tag) {
        JsonElement element = CompoundTag.CODEC.encodeStart(JsonOps.INSTANCE, tag)
                .getOrThrow(false, msg -> { throw new RuntimeException("Failed to encode preset NBT: " + msg); });
        return GSON.toJson(element);
    }

    public static CompoundTag fromJsonString(String json) {
        JsonElement element = JsonParser.parseString(json);
        return CompoundTag.CODEC.parse(JsonOps.INSTANCE, element)
                .getOrThrow(false, msg -> { throw new RuntimeException("Failed to decode preset JSON: " + msg); });
    }
}
