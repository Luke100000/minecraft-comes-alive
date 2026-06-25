package net.conczin.mca.resources.data.skin;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;

public class BodySkin extends SkinListEntry {
    private static final String BUILT_IN_SKIN_NAMESPACE = "mca";
    private static final String BUILT_IN_SKIN_PATH_PREFIX = "skins/skin/";
    public static final Codec<Definition> DEFINITION_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GENDER_CODEC.optionalFieldOf("gender", Gender.NEUTRAL).forGetter(Definition::gender),
            Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(Definition::chance),
            Codec.BOOL.optionalFieldOf("tint", false).forGetter(Definition::tint)
    ).apply(instance, Definition::new));
    public static final Codec<BodySkin> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(BodySkin::getIdentifierValue),
            GENDER_CODEC.optionalFieldOf("gender", Gender.NEUTRAL).forGetter(BodySkin::getGender),
            Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(BodySkin::getChance),
            Codec.BOOL.optionalFieldOf("tint", false).forGetter(BodySkin::isTinted)
    ).apply(instance, (id, gender, chance, tint) -> new BodySkin(id.toString(), gender, chance, tint)));
    public static final StreamCodec<ByteBuf, BodySkin> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, BodySkin::getIdentifierValue,
            GENDER_STREAM_CODEC, BodySkin::getGender,
            ByteBufCodecs.FLOAT, BodySkin::getChance,
            ByteBufCodecs.BOOL, BodySkin::isTinted,
            (id, gender, chance, tint) -> new BodySkin(id.toString(), gender, chance, tint)
    );

    private final boolean tint;

    public BodySkin(String identifier, JsonObject object) {
        super(identifier, object);
        this.tint = GsonHelper.getAsBoolean(object, "tint", false);
    }

    public BodySkin(String identifier, Gender gender, float chance, boolean tint) {
        super(identifier, gender, chance);
        this.tint = tint;
    }

    public boolean isTinted() {
        return tint;
    }

    public static boolean isBuiltInTinted(String identifier) {
        Identifier id;
        try {
            id = Identifier.parse(identifier);
        } catch (Exception e) {
            return false;
        }
        return BUILT_IN_SKIN_NAMESPACE.equals(id.getNamespace())
                && id.getPath().startsWith(BUILT_IN_SKIN_PATH_PREFIX);
    }

    @Override
    public JsonObject toJson() {
        JsonObject json = super.toJson();
        json.addProperty("tint", tint);
        return json;
    }

    public record Definition(Gender gender, float chance, boolean tint) {
        public BodySkin create(String identifier, Gender fallbackGender) {
            return new BodySkin(identifier, resolveGender(gender, fallbackGender), chance, tint);
        }
    }
}
