package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.destiny.DestinyDestination;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.DestinyLocationResolver;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public record OpenDestinyGuiRequest(boolean allowTeleportation, List<DestinyDestination> destinations) implements HandleablePayload {
    public static final CustomPacketPayload.Type<OpenDestinyGuiRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("open_destiny_gui_request"));
    public static final StreamCodec<FriendlyByteBuf, OpenDestinyGuiRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, OpenDestinyGuiRequest::allowTeleportation,
            DestinyDestination.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenDestinyGuiRequest::destinations,
            OpenDestinyGuiRequest::new
    );

    public OpenDestinyGuiRequest(ServerPlayer player) {
        this(
                Config.getInstance().allowDestinyTeleportation,
                DestinyLocationResolver.resolve(player.level().getServer(), Config.getInstance())
        );
    }

    public OpenDestinyGuiRequest {
        destinations = List.copyOf(destinations);
    }

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleDestinyGuiRequest(this);
    }

    @Override
    public CustomPacketPayload.Type<OpenDestinyGuiRequest> type() {
        return TYPE;
    }
}
