package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.resources.data.skin.BodySkin;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.Hair;
import net.conczin.mca.resources.data.skin.HairStyle;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;

public record SkinListResponse(
        HashMap<String, Clothing> clothing,
        HashMap<String, Hair> hair,
        HashMap<String, BodySkin> bodySkins,
        HashMap<String, LayeredHair> layeredHair,
        HashMap<String, HairStyle> hairStyles
) implements HandleablePayload {
    public static final CustomPacketPayload.Type<SkinListResponse> TYPE = new CustomPacketPayload.Type<>(MCA.locate("skin_list_response"));
    public static final StreamCodec<FriendlyByteBuf, SkinListResponse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, Clothing.STREAM_CODEC), SkinListResponse::clothing,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, Hair.STREAM_CODEC), SkinListResponse::hair,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, BodySkin.STREAM_CODEC), SkinListResponse::bodySkins,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, LayeredHair.STREAM_CODEC), SkinListResponse::layeredHair,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, HairStyle.STREAM_CODEC), SkinListResponse::hairStyles,
            SkinListResponse::new
    );

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleSkinListResponse(this);
    }

    @Override
    public CustomPacketPayload.Type<SkinListResponse> type() {
        return TYPE;
    }
}
