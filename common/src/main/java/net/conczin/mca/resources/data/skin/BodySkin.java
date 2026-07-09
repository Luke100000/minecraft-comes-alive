package net.conczin.mca.resources.data.skin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

public class BodySkin extends SkinListEntry {
    public static final Codec<BodySkin> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(BodySkin::getIdentifierValue),
            GENDER_CODEC.optionalFieldOf("gender", Gender.NEUTRAL).forGetter(BodySkin::getGender),
            Codec.FLOAT.optionalFieldOf("chance", 1.0f).forGetter(BodySkin::getChance)
    ).apply(instance, (id, gender, chance) -> new BodySkin(id.toString(), gender, chance)));
    public static final StreamCodec<ByteBuf, BodySkin> STREAM_CODEC = StreamCodec.of(
            (out, value) -> {
                String json = CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, value)
                        .getOrThrow()
                        .toString();
                ByteBufCodecs.STRING_UTF8.encode(out, json);
            },
            in -> {
                String json = ByteBufCodecs.STRING_UTF8.decode(in);
                return CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, GsonHelper.parse(json)).getOrThrow();
            }
    );

    public BodySkin(String identifier, Gender gender, float chance) {
        super(identifier, gender, chance);
    }

}
