package net.conczin.mca.network.c2s;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.ConfigResponse;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record ConfigRequest() implements HandleablePayload {
    public static final CustomPacketPayload.Type<ConfigRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("config_request"));
    public static final StreamCodec<FriendlyByteBuf, ConfigRequest> STREAM_CODEC = StreamCodec.unit(new ConfigRequest());

    @Override
    public void handleServer(ServerPlayer player) {
        Network.sendToPlayer(new ConfigResponse(Config.getInstance()), player);
    }

    @Override
    public CustomPacketPayload.Type<ConfigRequest> type() {
        return TYPE;
    }
}
