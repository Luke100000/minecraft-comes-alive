package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

public record GetFamilyResponse(CompoundTag nbt) implements HandleablePayload {
    public static final CustomPacketPayload.Type<GetFamilyResponse> TYPE = new CustomPacketPayload.Type<>(MCA.locate("get_family_response"));
    public static final StreamCodec<FriendlyByteBuf, GetFamilyResponse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, GetFamilyResponse::nbt,
            GetFamilyResponse::new
    );

    public CompoundTag getData() {
        return nbt;
    }

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleFamilyDataResponse(this);
    }

    @Override
    public CustomPacketPayload.Type<GetFamilyResponse> type() {
        return TYPE;
    }
}
