package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public record OpenDestinyGuiRequest(int player, boolean allowTeleportation) implements HandleablePayload {
    public static final CustomPacketPayload.Type<OpenDestinyGuiRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("open_destiny_gui_request"));
    public static final StreamCodec<FriendlyByteBuf, OpenDestinyGuiRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, OpenDestinyGuiRequest::player,
            ByteBufCodecs.BOOL, OpenDestinyGuiRequest::allowTeleportation,
            OpenDestinyGuiRequest::new
    );

    public OpenDestinyGuiRequest(ServerPlayer player) {
        this(player.getId(), Config.getInstance().allowDestinyTeleportation);
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
