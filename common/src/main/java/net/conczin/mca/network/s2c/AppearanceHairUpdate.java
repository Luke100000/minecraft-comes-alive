package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.resources.HairStyleList;
import net.conczin.mca.resources.data.skin.Hair;
import net.conczin.mca.resources.data.skin.HairStyle;
import net.conczin.mca.server.world.data.CustomClothingManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

public record AppearanceHairUpdate(String identifier, Optional<Hair> customHair, Optional<HairStyle> effectiveStyle) implements HandleablePayload {
    public static final CustomPacketPayload.Type<AppearanceHairUpdate> TYPE =
            new CustomPacketPayload.Type<>(MCA.locate("appearance_hair_update"));
    public static final StreamCodec<FriendlyByteBuf, AppearanceHairUpdate> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AppearanceHairUpdate::identifier,
            ByteBufCodecs.optional(Hair.STREAM_CODEC), AppearanceHairUpdate::customHair,
            ByteBufCodecs.optional(HairStyle.STREAM_CODEC), AppearanceHairUpdate::effectiveStyle,
            AppearanceHairUpdate::new
    );

    public static Optional<HairStyle> effectiveStyle(Hair hair, HairStyle explicit) {
        return Optional.ofNullable(explicit == null ? (hair == null ? null : HairStyle.fromHair(hair)) : explicit);
    }

    public static AppearanceHairUpdate current(MinecraftServer server, String identifier) {
        Hair custom = CustomClothingManager.getHair(server).getEntries().get(identifier);
        HairStyleList list = HairStyleList.getInstance();
        HairStyle explicit = list == null ? null : list.styles.get(identifier);
        return new AppearanceHairUpdate(identifier, Optional.ofNullable(custom), effectiveStyle(custom, explicit));
    }

    public static void broadcast(MinecraftServer server, String identifier) {
        AppearanceHairUpdate packet = current(server, identifier);
        server.getPlayerList().getPlayers().forEach(player -> Network.sendToPlayer(packet, player));
    }

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleAppearanceHairUpdate(this);
    }

    @Override
    public CustomPacketPayload.Type<AppearanceHairUpdate> type() {
        return TYPE;
    }
}
