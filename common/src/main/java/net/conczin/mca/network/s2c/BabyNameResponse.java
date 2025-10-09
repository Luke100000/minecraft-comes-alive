package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

public record BabyNameResponse(String name) implements HandleablePayload {
    public static final CustomPacketPayload.Type<BabyNameResponse> TYPE = new CustomPacketPayload.Type<>(MCA.locate("baby_name_response"));
    public static final StreamCodec<FriendlyByteBuf, BabyNameResponse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BabyNameResponse::name,
            BabyNameResponse::new
    );

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleBabyNameResponse(this);
    }

    @Override
    public CustomPacketPayload.Type<BabyNameResponse> type() {
        return TYPE;
    }
}
