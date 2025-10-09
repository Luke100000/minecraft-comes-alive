package net.conczin.mca.resources.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record SerializablePair<L, R>(L left, R right) {
    public static <L, R> Codec<SerializablePair<L, R>> codec(Codec<L> leftCodec, Codec<R> rightCodec) {
        return RecordCodecBuilder.create(instance -> instance.group(
                leftCodec.fieldOf("left").forGetter(SerializablePair::left),
                rightCodec.fieldOf("right").forGetter(SerializablePair::right)
        ).apply(instance, SerializablePair::new));
    }

    public static <L, R> StreamCodec<ByteBuf, SerializablePair<L, R>> streamCodec(
            StreamCodec<ByteBuf, L> leftCodec,
            StreamCodec<ByteBuf, R> rightCodec
    ) {
        return StreamCodec.composite(
                leftCodec, SerializablePair::left,
                rightCodec, SerializablePair::right,
                SerializablePair::new
        );
    }
}
