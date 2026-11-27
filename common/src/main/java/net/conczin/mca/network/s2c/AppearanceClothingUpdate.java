package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.resources.ClothingList;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.server.world.data.CustomClothingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

public record AppearanceClothingUpdate(String identifier, Optional<Clothing> effectiveClothing) implements HandleablePayload {
    public static final CustomPacketPayload.Type<AppearanceClothingUpdate> TYPE =
            new CustomPacketPayload.Type<>(MCA.locate("appearance_clothing_update"));
    public static final StreamCodec<FriendlyByteBuf, AppearanceClothingUpdate> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AppearanceClothingUpdate::identifier,
            ByteBufCodecs.optional(Clothing.STREAM_CODEC), AppearanceClothingUpdate::effectiveClothing,
            AppearanceClothingUpdate::new
    );

    public static Optional<Clothing> effective(Clothing custom, Clothing datapack) {
        return Optional.ofNullable(custom == null ? datapack : custom);
    }

    public static AppearanceClothingUpdate current(MinecraftServer server, String identifier) {
        Clothing custom = CustomClothingManager.getClothing(server).getEntries().get(identifier);
        ClothingList list = ClothingList.getInstance();
        Clothing datapack = list == null ? null : list.clothing.get(identifier);
        return new AppearanceClothingUpdate(identifier, effective(custom, datapack));
    }

    public static void broadcast(MinecraftServer server, String identifier) {
        AppearanceClothingUpdate packet = current(server, identifier);
        server.getPlayerList().getPlayers().forEach(player -> Network.sendToPlayer(packet, player));
    }

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleAppearanceClothingUpdate(this);
    }

    @Override
    public CustomPacketPayload.Type<AppearanceClothingUpdate> type() {
        return TYPE;
    }
}
