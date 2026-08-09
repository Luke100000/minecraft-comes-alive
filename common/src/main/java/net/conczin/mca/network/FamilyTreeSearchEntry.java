package net.conczin.mca.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record FamilyTreeSearchEntry(UUID uuid, String name, String father, String mother) {
    public static final StreamCodec<ByteBuf, FamilyTreeSearchEntry> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, FamilyTreeSearchEntry::uuid,
            ByteBufCodecs.STRING_UTF8, FamilyTreeSearchEntry::name,
            ByteBufCodecs.STRING_UTF8, FamilyTreeSearchEntry::father,
            ByteBufCodecs.STRING_UTF8, FamilyTreeSearchEntry::mother,
            FamilyTreeSearchEntry::new
    );
}
