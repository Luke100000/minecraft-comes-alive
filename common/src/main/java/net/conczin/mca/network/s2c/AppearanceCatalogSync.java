package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.resources.*;
import net.conczin.mca.resources.data.skin.*;
import net.conczin.mca.server.world.data.CustomClothingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Full server-authoritative appearance catalog sent after datapack synchronization
 * and whenever server-managed custom appearance content changes.
 */
public record AppearanceCatalogSync(
        HashMap<String, Clothing> clothing,
        HashMap<String, BodySkin> bodySkins,
        HashMap<String, LayeredHair> layeredHair,
        HashMap<String, HairStyle> hairStyles,
        HashMap<String, Hair> hair,
        HashMap<ResourceLocation, EyeDefinition> eyes
) implements HandleablePayload {
    public static final CustomPacketPayload.Type<AppearanceCatalogSync> TYPE =
            new CustomPacketPayload.Type<>(MCA.locate("appearance_catalog_sync"));
    public static final StreamCodec<FriendlyByteBuf, AppearanceCatalogSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, Clothing.STREAM_CODEC), AppearanceCatalogSync::clothing,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, BodySkin.STREAM_CODEC), AppearanceCatalogSync::bodySkins,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, LayeredHair.STREAM_CODEC), AppearanceCatalogSync::layeredHair,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, HairStyle.STREAM_CODEC), AppearanceCatalogSync::hairStyles,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, Hair.STREAM_CODEC), AppearanceCatalogSync::hair,
            ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, EyeDefinition.STREAM_CODEC), AppearanceCatalogSync::eyes,
            AppearanceCatalogSync::new
    );

    public static AppearanceCatalogSync current(MinecraftServer server) {
        ClothingList clothingList = ClothingList.getInstance();
        BodySkinList bodySkinList = BodySkinList.getInstance();
        LayeredHairList layeredHairList = LayeredHairList.getInstance();
        EyeCatalog eyeCatalog = EyeCatalog.getInstance();

        HashMap<String, Clothing> clothing = copy(clothingList == null ? Map.of() : clothingList.clothing);
        clothing.putAll(CustomClothingManager.getClothing(server).getEntries());

        HashMap<String, Hair> hair = copy(CustomClothingManager.getHair(server).getEntries());
        HairStyleList hairStyleList = HairStyleList.getInstance();
        HashMap<String, HairStyle> hairStyles = hairStyleList == null
                ? new HashMap<>()
                : hairStyleList.getAllStyles(hair);

        HashMap<String, BodySkin> bodySkins = copy(bodySkinList == null ? Map.of() : bodySkinList.skins);
        HashMap<String, LayeredHair> layeredHair = copy(layeredHairList == null ? Map.of() : layeredHairList.hair);
        HashMap<ResourceLocation, EyeDefinition> eyes = copy(eyeCatalog == null ? Map.of() : eyeCatalog.effectiveDefinitions());

        return new AppearanceCatalogSync(clothing, bodySkins, layeredHair, hairStyles, hair, eyes);
    }

    public static void send(ServerPlayer player) {
        Network.sendToPlayer(current(player.serverLevel().getServer()), player);
    }

    public static void sendToAll(MinecraftServer server) {
        AppearanceCatalogSync sync = current(server);
        server.getPlayerList().getPlayers().forEach(player -> Network.sendToPlayer(sync, player));
    }

    private static <K, V> HashMap<K, V> copy(Map<K, V> source) {
        return new HashMap<>(source);
    }

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleAppearanceCatalogSync(this);
    }

    @Override
    public CustomPacketPayload.Type<AppearanceCatalogSync> type() {
        return TYPE;
    }
}
