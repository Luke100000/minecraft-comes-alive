package net.conczin.mca.resources.data.skin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.conczin.mca.entity.ai.relationship.Gender;

public class HairStyle extends SkinListEntry {
    public static final Codec<Definition> DEFINITION_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GENDER_CODEC.optionalFieldOf("gender", Gender.NEUTRAL).forGetter(Definition::gender),
            Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(Definition::chance),
            Codec.STRING.optionalFieldOf("base", "").forGetter(Definition::base),
            Codec.STRING.optionalFieldOf("bangs", "").forGetter(Definition::bangs),
            Codec.STRING.optionalFieldOf("back", "").forGetter(Definition::back),
            Codec.STRING.optionalFieldOf("front", "").forGetter(Definition::front),
            Codec.STRING.optionalFieldOf("extra", "").forGetter(Definition::extra)
    ).apply(instance, Definition::new));
    public static final Codec<HairStyle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(HairStyle::getIdentifier),
            GENDER_CODEC.optionalFieldOf("gender", Gender.NEUTRAL).forGetter(HairStyle::getGender),
            Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(HairStyle::getChance),
            Codec.STRING.optionalFieldOf("base", "").forGetter(HairStyle::base),
            Codec.STRING.optionalFieldOf("bangs", "").forGetter(HairStyle::bangs),
            Codec.STRING.optionalFieldOf("back", "").forGetter(HairStyle::back),
            Codec.STRING.optionalFieldOf("front", "").forGetter(HairStyle::front),
            Codec.STRING.optionalFieldOf("extra", "").forGetter(HairStyle::extra)
    ).apply(instance, HairStyle::new));
    private final String base;
    private final String bangs;
    private final String back;
    private final String front;
    private final String extra;

    public HairStyle(String identifier, Gender gender, float chance, String base, String bangs, String back, String front, String extra) {
        super(identifier, gender, chance);
        this.base = base;
        this.bangs = bangs;
        this.back = back;
        this.front = front;
        this.extra = extra;
    }

    public static HairStyle singleLayer(String identifier, Gender gender, float chance) {
        return new HairStyle(identifier, gender, chance, identifier, "", "", "", "");
    }

    public static HairStyle fromHair(Hair hair) {
        return singleLayer(hair.getIdentifier(), hair.getGender(), hair.getChance());
    }

    public String layer(LayeredHair.Category category) {
        return switch (category) {
            case BASE -> base;
            case BANGS -> bangs;
            case BACK -> back;
            case FRONT -> front;
            case EXTRA -> extra;
        };
    }

    public String base() {
        return base;
    }

    public String bangs() {
        return bangs;
    }

    public String back() {
        return back;
    }

    public String front() {
        return front;
    }

    public String extra() {
        return extra;
    }

    public record Definition(Gender gender, float chance, String base, String bangs, String back, String front, String extra) {
        public HairStyle create(String identifier, Gender fallbackGender) {
            Gender resolvedGender = resolveGender(gender, inferLegacyGender(identifier, fallbackGender));
            return new HairStyle(identifier, resolvedGender, chance, base, bangs, back, front, extra);
        }

        private static Gender inferLegacyGender(String identifier, Gender fallbackGender) {
            if (identifier.startsWith("mca:skins/hair/female/")) {
                return Gender.FEMALE;
            }
            if (identifier.startsWith("mca:skins/hair/male/")) {
                return Gender.MALE;
            }
            return fallbackGender;
        }
    }
}
