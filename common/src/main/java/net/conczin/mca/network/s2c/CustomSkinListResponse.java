package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.Hair;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;

public record CustomSkinListResponse(
        HashMap<String, Clothing> clothing,
        HashMap<String, Hair> hair
) implements HandleablePayload {
    public static final CustomPacketPayload.Type<CustomSkinListResponse> TYPE = new CustomPacketPayload.Type<>(MCA.locate("custom_skin_list_response"));
    public static final StreamCodec<FriendlyByteBuf, CustomSkinListResponse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, Clothing.STREAM_CODEC), CustomSkinListResponse::clothing,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, Hair.STREAM_CODEC), CustomSkinListResponse::hair,
            CustomSkinListResponse::new
    );

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleCustomSkinListResponse(this);
    }

    @Override
    public CustomPacketPayload.Type<CustomSkinListResponse> type() {
        return TYPE;
    }
}
