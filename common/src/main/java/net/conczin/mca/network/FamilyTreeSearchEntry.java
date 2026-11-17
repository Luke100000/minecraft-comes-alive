package net.conczin.mca.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record FamilyTreeSearchEntry(
        UUID uuid,
        String name,
        boolean fatherRecorded,
        String father,
        boolean motherRecorded,
        String mother
) {
    public static final StreamCodec<ByteBuf, FamilyTreeSearchEntry> STREAM_CODEC = StreamCodec.of(
            (buffer, entry) -> {
                UUIDUtil.STREAM_CODEC.encode(buffer, entry.uuid());
                ByteBufCodecs.STRING_UTF8.encode(buffer, entry.name());
                ByteBufCodecs.BOOL.encode(buffer, entry.fatherRecorded());
                ByteBufCodecs.STRING_UTF8.encode(buffer, entry.father());
                ByteBufCodecs.BOOL.encode(buffer, entry.motherRecorded());
                ByteBufCodecs.STRING_UTF8.encode(buffer, entry.mother());
            },
            buffer -> new FamilyTreeSearchEntry(
                    UUIDUtil.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer)
            )
    );
}
