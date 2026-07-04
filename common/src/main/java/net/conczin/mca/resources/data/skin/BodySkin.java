package net.conczin.mca.resources.data.skin;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

public class BodySkin extends SkinListEntry {
    public static final Codec<Definition> DEFINITION_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GENDER_CODEC.optionalFieldOf("gender", Gender.NEUTRAL).forGetter(Definition::gender),
            Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(Definition::chance)
    ).apply(instance, Definition::new));
    public static final Codec<BodySkin> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(BodySkin::getIdentifierValue),
            GENDER_CODEC.optionalFieldOf("gender", Gender.NEUTRAL).forGetter(BodySkin::getGender),
            Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(BodySkin::getChance)
    ).apply(instance, (id, gender, chance) -> new BodySkin(id.toString(), gender, chance)));
    public static final StreamCodec<ByteBuf, BodySkin> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, BodySkin::getIdentifierValue,
            GENDER_STREAM_CODEC, BodySkin::getGender,
            ByteBufCodecs.FLOAT, BodySkin::getChance,
            (id, gender, chance) -> new BodySkin(id.toString(), gender, chance)
    );

    public BodySkin(String identifier, Gender gender, float chance) {
        super(identifier, gender, chance);
    }

    @Override
    public JsonObject toJson() {
        return super.toJson();
    }

    public record Definition(Gender gender, float chance) {
        public BodySkin create(String identifier, Gender fallbackGender) {
            return new BodySkin(identifier, resolveGender(gender, fallbackGender), chance);
        }
    }
}
