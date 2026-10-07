package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record InteractionDialogueLeaveMessage() implements HandleablePayload {
    public static final CustomPacketPayload.Type<InteractionDialogueLeaveMessage> TYPE =
            new CustomPacketPayload.Type<>(MCA.locate("interaction_dialogue_leave"));
    public static final StreamCodec<FriendlyByteBuf, InteractionDialogueLeaveMessage> STREAM_CODEC =
            StreamCodec.unit(new InteractionDialogueLeaveMessage());

    @Override
    public void handleServer(ServerPlayer player) {
        MCA.getDialogueEngine().ifPresent(engine -> engine.pause(player));
    }

    @Override
    public Type<InteractionDialogueLeaveMessage> type() {
        return TYPE;
    }
}
