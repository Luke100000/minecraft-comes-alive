package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.dialogue.DialogueEvent;
import net.conczin.mca.dialogue.DialogueEngine;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.InteractionDialogueNodeResponse;
import net.conczin.mca.network.s2c.InteractionDialogueSelectionRejectedResponse;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

public record InteractionDialogueChoiceMessage(long offerToken, String choiceId) implements HandleablePayload {
    public static final int MAX_CHOICE_ID_LENGTH = DialogueEvent.MAX_CHOICE_ID_LENGTH;
    private static final StreamCodec<io.netty.buffer.ByteBuf, String> CHOICE_ID_CODEC =
            ByteBufCodecs.stringUtf8(MAX_CHOICE_ID_LENGTH);

    public static final CustomPacketPayload.Type<InteractionDialogueChoiceMessage> TYPE =
            new CustomPacketPayload.Type<>(MCA.locate("interaction_dialogue_choice"));
    public static final StreamCodec<FriendlyByteBuf, InteractionDialogueChoiceMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, InteractionDialogueChoiceMessage::offerToken,
            CHOICE_ID_CODEC, InteractionDialogueChoiceMessage::choiceId,
            InteractionDialogueChoiceMessage::new
    );

    public InteractionDialogueChoiceMessage {
        Objects.requireNonNull(choiceId, "choiceId");
        if (choiceId.isEmpty() || choiceId.length() > MAX_CHOICE_ID_LENGTH) {
            throw new IllegalArgumentException("Dialogue choice id length is invalid");
        }
    }

    @Override
    public void handleServer(ServerPlayer player) {
        MCA.getDialogueEngine().ifPresent(engine -> sendTransition(player, offerToken, engine.choose(player, offerToken, choiceId)));
    }

    static void sendTransition(ServerPlayer player, long requestToken, DialogueEngine.TransitionResult result) {
        if (result.view().isPresent()) {
            Network.sendToPlayer(InteractionDialogueNodeResponse.active(result.view().orElseThrow()), player);
            return;
        }
        if (result.status() == DialogueEngine.TransitionStatus.REJECTED) {
            Network.sendToPlayer(new InteractionDialogueSelectionRejectedResponse(requestToken), player);
            return;
        }
        if (result.paused()) {
            result.sessionId().ifPresent(sessionId -> Network.sendToPlayer(
                    InteractionDialogueNodeResponse.paused(sessionId, requestToken),
                    player
            ));
            return;
        }
        if (result.ended() || result.completed()) {
            result.sessionId().ifPresent(sessionId -> Network.sendToPlayer(
                    InteractionDialogueNodeResponse.ended(sessionId, requestToken),
                    player
            ));
        }
    }

    @Override
    public Type<InteractionDialogueChoiceMessage> type() {
        return TYPE;
    }
}
