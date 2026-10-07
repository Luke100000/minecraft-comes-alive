package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record InteractionDialogueAdvanceMessage(long offerToken) implements HandleablePayload {
    public static final CustomPacketPayload.Type<InteractionDialogueAdvanceMessage> TYPE =
            new CustomPacketPayload.Type<>(MCA.locate("interaction_dialogue_advance"));
    public static final StreamCodec<FriendlyByteBuf, InteractionDialogueAdvanceMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, InteractionDialogueAdvanceMessage::offerToken,
            InteractionDialogueAdvanceMessage::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        MCA.getDialogueEngine().ifPresent(engine -> InteractionDialogueChoiceMessage.sendTransition(
                player,
                offerToken,
                engine.advance(player, offerToken)
        ));
    }

    @Override
    public Type<InteractionDialogueAdvanceMessage> type() {
        return TYPE;
    }
}
