package net.conczin.mca.item.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record BabyParentsComponent(
        UUID mother,
        UUID father,
        String motherName,
        String fatherName
) {
    public static final Codec<BabyParentsComponent> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                            UUIDUtil.CODEC.fieldOf("mother").forGetter(BabyParentsComponent::mother),
                            UUIDUtil.CODEC.fieldOf("father").forGetter(BabyParentsComponent::father),
                            Codec.STRING.fieldOf("motherName").forGetter(BabyParentsComponent::motherName),
                            Codec.STRING.fieldOf("fatherName").forGetter(BabyParentsComponent::fatherName)
                    )
                    .apply(instance, BabyParentsComponent::new)
    );

    public static final StreamCodec<ByteBuf, BabyParentsComponent> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, BabyParentsComponent::mother,
            UUIDUtil.STREAM_CODEC, BabyParentsComponent::father,
            ByteBufCodecs.STRING_UTF8, BabyParentsComponent::motherName,
            ByteBufCodecs.STRING_UTF8, BabyParentsComponent::fatherName,
            BabyParentsComponent::new
    );
}
