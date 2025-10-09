package net.conczin.mca.resources.data.skin;

import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.GsonHelper;

public class Hair extends SkinListEntry {
    public static final StreamCodec<ByteBuf, Hair> STREAM_CODEC = StreamCodec.of(
            (out, value) -> {
                JsonObject json = value.toJson();
                json.addProperty("id", value.identifier);
                ByteBufCodecs.STRING_UTF8.encode(out, json.toString());
            },
            (in) -> {
                String json = ByteBufCodecs.STRING_UTF8.decode(in);
                JsonObject parsed = GsonHelper.parse(json);
                return new Hair(parsed.get("id").getAsString(), parsed);
            }
    );

    public Hair(String identifier) {
        super(identifier);
    }

    public Hair(String identifier, JsonObject object) {
        super(identifier, object);
    }

    public Hair(String identifier, Gender gender, float chance) {
        super(identifier, gender, chance);
    }
}
