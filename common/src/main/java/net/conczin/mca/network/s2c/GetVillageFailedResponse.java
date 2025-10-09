package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

public record GetVillageFailedResponse() implements HandleablePayload {
    public static final CustomPacketPayload.Type<GetVillageFailedResponse> TYPE = new CustomPacketPayload.Type<>(MCA.locate("get_village_failed_response"));
    public static final StreamCodec<FriendlyByteBuf, GetVillageFailedResponse> STREAM_CODEC = StreamCodec.unit(new GetVillageFailedResponse());

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleVillageDataFailedResponse(this);
    }

    @Override
    public CustomPacketPayload.Type<GetVillageFailedResponse> type() {
        return TYPE;
    }
}
