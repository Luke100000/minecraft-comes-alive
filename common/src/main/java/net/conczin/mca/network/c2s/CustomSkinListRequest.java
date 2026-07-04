package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.CustomSkinListResponse;
import net.conczin.mca.resources.BodySkinList;
import net.conczin.mca.resources.BuiltInSkinCatalog;
import net.conczin.mca.resources.ClothingList;
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
import java.util.Objects;
import java.util.function.Function;

public record CustomSkinListRequest() implements HandleablePayload {
    public static final CustomPacketPayload.Type<CustomSkinListRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("custom_skin_list_request"));
    public static final StreamCodec<FriendlyByteBuf, CustomSkinListRequest> STREAM_CODEC = StreamCodec.unit(new CustomSkinListRequest());

    @Override
    public void handleServer(ServerPlayer player) {
        BuiltInSkinCatalog.Catalog builtIn = BuiltInSkinCatalog.get();
        HashMap<String, Clothing> clothing = delta(ClothingList.getInstance() == null ? Map.of() : ClothingList.getInstance().clothing, builtIn.clothing(), CustomSkinListRequest::clothingSignature);
        HashMap<String, BodySkin> bodySkins = delta(BodySkinList.getInstance() == null ? Map.of() : BodySkinList.getInstance().skins, builtIn.bodySkins(), CustomSkinListRequest::bodySkinSignature);
        HashMap<String, LayeredHair> layeredHair = delta(LayeredHairList.getInstance() == null ? Map.of() : LayeredHairList.getInstance().hair, builtIn.layeredHair(), CustomSkinListRequest::layeredHairSignature);
        HashMap<String, HairStyle> hairStyles = delta(HairStyleList.getInstance() == null ? Map.of() : HairStyleList.getInstance().styles, builtIn.hairStyles(), CustomSkinListRequest::hairStyleSignature);
        HashMap<String, Hair> hair = new HashMap<>(CustomClothingManager.getHair().getEntries());

        clothing.putAll(CustomClothingManager.getClothing().getEntries());
        Network.sendToPlayer(new CustomSkinListResponse(clothing, bodySkins, layeredHair, hairStyles, hair), player);
    }

    @Override
    public CustomPacketPayload.Type<CustomSkinListRequest> type() {
        return TYPE;
    }

    private static <T> HashMap<String, T> delta(Map<String, T> effective, Map<String, T> builtIn, Function<T, String> signature) {
        HashMap<String, T> result = new HashMap<>();
        effective.forEach((key, value) -> {
            T builtInValue = builtIn.get(key);
            if (builtInValue == null || !Objects.equals(signature.apply(value), signature.apply(builtInValue))) {
                result.put(key, value);
            }
        });
        return result;
    }

    private static String clothingSignature(Clothing clothing) {
        return clothing.getIdentifier() + "|" + clothing.toJson();
    }

    private static String bodySkinSignature(BodySkin bodySkin) {
        return bodySkin.getIdentifier() + "|" + bodySkin.getGender().getDataName() + "|" + bodySkin.getChance();
    }

    private static String layeredHairSignature(LayeredHair hair) {
        return hair.getIdentifier() + "|" + hair.toJson();
    }

    private static String hairStyleSignature(HairStyle style) {
        return style.getIdentifier()
                + "|" + style.getGender().getDataName()
                + "|" + style.getChance()
                + "|" + style.base()
                + "|" + style.bangs()
                + "|" + style.back()
                + "|" + style.front()
                + "|" + style.extra();
    }
}
