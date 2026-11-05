package net.conczin.mca.util.network.datasync;

import net.conczin.mca.MCA;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.UUID;

public final class MCAEntityDataSerializers {
    public static final Identifier COMPOUND_TAG_ID = MCA.locate("compound_tag");
    public static final Identifier OPTIONAL_UUID_ID = MCA.locate("optional_uuid");

    public static final EntityDataSerializer<CompoundTag> COMPOUND_TAG = new EntityDataSerializer<>() {
        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, CompoundTag> codec() {
            return ByteBufCodecs.COMPOUND_TAG;
        }

        @Override
        public CompoundTag copy(CompoundTag value) {
            return value.copy();
        }
    };
    public static final EntityDataSerializer<Optional<UUID>> OPTIONAL_UUID = EntityDataSerializer.forValueType(ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC));

    private MCAEntityDataSerializers() {
    }
}
