package net.mca.resources.data.skin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.mca.entity.ai.relationship.Gender;
import net.minecraft.resources.ResourceLocation;

public class BodySkin extends SkinListEntry {
    public static final Codec<Definition> DEFINITION_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GENDER_CODEC.optionalFieldOf("gender", Gender.NEUTRAL).forGetter(Definition::gender),
            Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(Definition::chance)
    ).apply(instance, Definition::new));
    public static final Codec<BodySkin> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(BodySkin::getIdentifierValue),
            GENDER_CODEC.optionalFieldOf("gender", Gender.NEUTRAL).forGetter(BodySkin::getGender),
            Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(BodySkin::getChance)
    ).apply(instance, (id, gender, chance) -> new BodySkin(id.toString(), gender, chance)));

    public BodySkin(String identifier, Gender gender, float chance) {
        super(identifier, gender, chance);
    }

    public record Definition(Gender gender, float chance) {
        public BodySkin create(String identifier, Gender fallbackGender) {
            return new BodySkin(identifier, resolveGender(gender, fallbackGender), chance);
        }
    }
}
