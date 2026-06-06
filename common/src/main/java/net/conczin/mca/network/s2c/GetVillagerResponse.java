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

public record GetVillagerResponse(CompoundTag data) implements HandleablePayload {
    public static final CustomPacketPayload.Type<GetVillagerResponse> TYPE = new CustomPacketPayload.Type<>(MCA.locate("get_villager_response"));
    public static final StreamCodec<FriendlyByteBuf, GetVillagerResponse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, GetVillagerResponse::data,
            GetVillagerResponse::new
    );

    public GetVillagerResponse {
        data = data.copy();
    }

    public CompoundTag data() {
        return data.copy();
    }

    public CompoundTag getData() {
        return data();
    }

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleVillagerDataResponse(this);
    }

    @Override
    public Type<GetVillagerResponse> type() {
        return TYPE;
    }
}
