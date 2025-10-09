package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

public record VillagerNameResponse(String name) implements HandleablePayload {
    public static final CustomPacketPayload.Type<VillagerNameResponse> TYPE = new CustomPacketPayload.Type<>(MCA.locate("villager_name_response"));
    public static final StreamCodec<FriendlyByteBuf, VillagerNameResponse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, VillagerNameResponse::name,
            VillagerNameResponse::new
    );

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleVillagerNameResponse(this);
    }

    @Override
    public CustomPacketPayload.Type<VillagerNameResponse> type() {
        return TYPE;
    }
}
