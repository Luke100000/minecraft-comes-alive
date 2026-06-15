package net.conczin.mca.resources.data.skin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

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
    public static final StreamCodec<ByteBuf, HairStyle> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, HairStyle::getIdentifier,
            GENDER_STREAM_CODEC, HairStyle::getGender,
            ByteBufCodecs.FLOAT, HairStyle::getChance,
            ByteBufCodecs.STRING_UTF8, HairStyle::base,
            ByteBufCodecs.STRING_UTF8, HairStyle::bangs,
            ByteBufCodecs.STRING_UTF8, HairStyle::back,
            ByteBufCodecs.STRING_UTF8, HairStyle::front,
            ByteBufCodecs.STRING_UTF8, HairStyle::extra,
            HairStyle::new
    );

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
            return new HairStyle(identifier, resolveGender(gender, fallbackGender), chance, base, bangs, back, front, extra);
        }
    }
}
