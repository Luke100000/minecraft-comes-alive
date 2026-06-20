package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.CustomSkinListResponse;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.Hair;
import net.conczin.mca.server.world.data.CustomClothingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;

public record CustomSkinListRequest() implements HandleablePayload {
    public static final CustomPacketPayload.Type<CustomSkinListRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("custom_skin_list_request"));
    public static final StreamCodec<FriendlyByteBuf, CustomSkinListRequest> STREAM_CODEC = StreamCodec.unit(new CustomSkinListRequest());

    @Override
    public void handleServer(ServerPlayer player) {
        HashMap<String, Clothing> clothing = new HashMap<>(CustomClothingManager.getClothing().getEntries());
        HashMap<String, Hair> hair = new HashMap<>(CustomClothingManager.getHair().getEntries());
        Network.sendToPlayer(new CustomSkinListResponse(clothing, hair), player);
    }

    @Override
    public CustomPacketPayload.Type<CustomSkinListRequest> type() {
        return TYPE;
    }
}
