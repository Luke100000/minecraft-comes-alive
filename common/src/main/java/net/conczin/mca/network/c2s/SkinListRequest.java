package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.SkinListResponse;
import net.conczin.mca.resources.BodySkinList;
import net.conczin.mca.resources.ClothingList;
import net.conczin.mca.resources.HairList;
import net.conczin.mca.resources.HairStyleList;
import net.conczin.mca.resources.LayeredHairList;
import net.conczin.mca.resources.data.skin.BodySkin;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.Hair;
import net.conczin.mca.resources.data.skin.HairStyle;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.conczin.mca.server.world.data.CustomClothingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

public record SkinListRequest() implements HandleablePayload {
    public static final CustomPacketPayload.Type<SkinListRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("skin_list_request"));
    public static final StreamCodec<FriendlyByteBuf, SkinListRequest> STREAM_CODEC = StreamCodec.unit(new SkinListRequest());

    private static <T> HashMap<String, T> merge(Map<String, T> a, Map<String, T> b) {
        HashMap<String, T> map = new HashMap<>();
        map.putAll(a);
        map.putAll(b);
        return map;
    }

    @Override
    public void handleServer(ServerPlayer player) {
        Map<String, Clothing> clothing = CustomClothingManager.getClothing().getEntries();
        Map<String, Hair> hair = CustomClothingManager.getHair().getEntries();
        HashMap<String, Clothing> allClothing = merge(ClothingList.getInstance().clothing, clothing);
        HashMap<String, Hair> allHair = merge(HairList.getInstance().hair, hair);
        HashMap<String, BodySkin> bodySkins = BodySkinList.getInstance() == null ? new HashMap<>() : new HashMap<>(BodySkinList.getInstance().skins);
        HashMap<String, LayeredHair> layeredHair = LayeredHairList.getInstance() == null ? new HashMap<>() : new HashMap<>(LayeredHairList.getInstance().hair);
        HashMap<String, HairStyle> hairStyles = HairStyleList.getInstance() == null ? new HashMap<>() : HairStyleList.getInstance().getAllStyles(allHair);
        Network.sendToPlayer(new SkinListResponse(allClothing, allHair, bodySkins, layeredHair, hairStyles), player);
    }

    @Override
    public CustomPacketPayload.Type<SkinListRequest> type() {
        return TYPE;
    }
}
