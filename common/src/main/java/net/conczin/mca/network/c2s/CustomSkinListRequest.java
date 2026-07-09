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
import java.util.function.BiPredicate;

public record CustomSkinListRequest() implements HandleablePayload {
    public static final CustomPacketPayload.Type<CustomSkinListRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("custom_skin_list_request"));
    public static final StreamCodec<FriendlyByteBuf, CustomSkinListRequest> STREAM_CODEC = StreamCodec.unit(new CustomSkinListRequest());

    @Override
    public void handleServer(ServerPlayer player) {
        BuiltInSkinCatalog.Catalog builtIn = BuiltInSkinCatalog.get();
        HashMap<String, Clothing> clothing = delta(ClothingList.getInstance() == null ? Map.of() : ClothingList.getInstance().clothing, builtIn.clothing(), CustomSkinListRequest::sameClothing);
        HashMap<String, BodySkin> bodySkins = delta(BodySkinList.getInstance() == null ? Map.of() : BodySkinList.getInstance().skins, builtIn.bodySkins(), CustomSkinListRequest::sameBodySkin);
        HashMap<String, LayeredHair> layeredHair = delta(LayeredHairList.getInstance() == null ? Map.of() : LayeredHairList.getInstance().hair, builtIn.layeredHair(), CustomSkinListRequest::sameLayeredHair);
        HashMap<String, HairStyle> hairStyles = delta(HairStyleList.getInstance() == null ? Map.of() : HairStyleList.getInstance().styles, builtIn.hairStyles(), CustomSkinListRequest::sameHairStyle);
        HashMap<String, Hair> hair = new HashMap<>(CustomClothingManager.getHair().getEntries());

        clothing.putAll(CustomClothingManager.getClothing().getEntries());
        Network.sendToPlayer(new CustomSkinListResponse(clothing, bodySkins, layeredHair, hairStyles, hair), player);
    }

    @Override
    public CustomPacketPayload.Type<CustomSkinListRequest> type() {
        return TYPE;
    }

    private static <T> HashMap<String, T> delta(Map<String, T> effective, Map<String, T> builtIn, BiPredicate<T, T> matches) {
        HashMap<String, T> result = new HashMap<>();
        effective.forEach((key, value) -> {
            T builtInValue = builtIn.get(key);
            if (builtInValue == null || !matches.test(value, builtInValue)) {
                result.put(key, value);
            }
        });
        return result;
    }

    private static boolean sameClothing(Clothing left, Clothing right) {
        return left.getGender() == right.getGender()
                && Objects.equals(left.profession, right.profession)
                && left.exclude == right.exclude
                && left.temperature == right.temperature;
    }

    private static boolean sameBodySkin(BodySkin left, BodySkin right) {
        return left.getGender() == right.getGender()
                && Float.compare(left.getChance(), right.getChance()) == 0;
    }

    private static boolean sameLayeredHair(LayeredHair left, LayeredHair right) {
        return left.getGender() == right.getGender()
                && left.getCategory() == right.getCategory()
                && Float.compare(left.getChance(), right.getChance()) == 0;
    }

    private static boolean sameHairStyle(HairStyle left, HairStyle right) {
        return left.getGender() == right.getGender()
                && Float.compare(left.getChance(), right.getChance()) == 0
                && Objects.equals(left.base(), right.base())
                && Objects.equals(left.bangs(), right.bangs())
                && Objects.equals(left.back(), right.back())
                && Objects.equals(left.front(), right.front())
                && Objects.equals(left.extra(), right.extra());
    }
}
