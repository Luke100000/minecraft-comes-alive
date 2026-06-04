package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.OpenGuiRequest;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public record OpenPlayerInteractionRequest(UUID targetUUID) implements HandleablePayload {
    public static final CustomPacketPayload.Type<OpenPlayerInteractionRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("open_player_interaction_request"));
    public static final StreamCodec<FriendlyByteBuf, OpenPlayerInteractionRequest> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, OpenPlayerInteractionRequest::targetUUID,
            OpenPlayerInteractionRequest::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        Entity target = player.level().getPlayerByUUID(targetUUID);
        if (target instanceof ServerPlayer targetPlayer && targetPlayer != player && player.distanceToSqr(targetPlayer) <= 36.0D) {
            Network.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.PLAYER_INTERACT, targetPlayer), player);
        }
    }

    @Override
    public CustomPacketPayload.Type<OpenPlayerInteractionRequest> type() {
        return TYPE;
    }
}
