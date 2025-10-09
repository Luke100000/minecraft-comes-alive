package net.conczin.mca.resources.data.skin;

import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

public class Clothing extends SkinListEntry {
    public static final StreamCodec<ByteBuf, Clothing> STREAM_CODEC = StreamCodec.of(
            (out, value) -> {
                JsonObject json = value.toJson();
                json.addProperty("id", value.identifier);
                ByteBufCodecs.STRING_UTF8.encode(out, json.toString());
            },
            (in) -> {
                String json = ByteBufCodecs.STRING_UTF8.decode(in);
                JsonObject parsed = GsonHelper.parse(json);
                return new Clothing(parsed.get("id").getAsString(), parsed);
            }
    );

    @Nullable
    public final String profession;
    public final int temperature;
    public final boolean exclude;

    public Clothing(String identifier, @Nullable String profession, int temperature, boolean exclude, Gender gender) {
        super(identifier, gender, 1.0f);
        this.profession = profession;
        this.temperature = temperature;
        this.exclude = exclude;
    }

    public Clothing(String identifier, JsonObject object) {
        super(identifier, object);

        this.profession = object.get("profession").isJsonNull() ? null : GsonHelper.getAsString(object, "profession", null);
        this.exclude = GsonHelper.getAsBoolean(object, "exclude", false);
        this.temperature = GsonHelper.getAsInt(object, "temperature", 0);
    }

    @Override
    public JsonObject toJson() {
        JsonObject j = super.toJson();
        j.addProperty("profession", profession);
        j.addProperty("exclude", exclude);
        j.addProperty("temperature", temperature);
        return j;
    }
}
