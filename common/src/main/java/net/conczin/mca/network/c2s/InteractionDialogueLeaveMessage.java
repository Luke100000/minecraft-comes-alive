package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public record InteractionDialogueLeaveMessage(UUID villagerUUID, UUID sessionId, long offerToken) implements HandleablePayload {
    public static final CustomPacketPayload.Type<InteractionDialogueLeaveMessage> TYPE =
            new CustomPacketPayload.Type<>(MCA.locate("interaction_dialogue_leave"));
    public static final StreamCodec<FriendlyByteBuf, InteractionDialogueLeaveMessage> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, InteractionDialogueLeaveMessage::villagerUUID,
            UUIDUtil.STREAM_CODEC, InteractionDialogueLeaveMessage::sessionId,
            ByteBufCodecs.VAR_LONG, InteractionDialogueLeaveMessage::offerToken,
            InteractionDialogueLeaveMessage::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        MCA.getDialogueEngine().ifPresent(engine -> engine.pause(player, villagerUUID, sessionId, offerToken));
    }

    @Override
    public Type<InteractionDialogueLeaveMessage> type() {
        return TYPE;
    }
}
