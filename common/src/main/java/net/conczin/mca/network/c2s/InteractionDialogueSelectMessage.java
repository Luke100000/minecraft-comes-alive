package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.dialogue.DialogueEngine;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.InteractionDialogueNodeResponse;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Optional;

public record InteractionDialogueSelectMessage(
        long offerToken,
        DialogueEngine.DialogueSelection selection,
        Optional<ResourceLocation> eventId
) implements HandleablePayload {
    private static final StreamCodec<io.netty.buffer.ByteBuf, DialogueEngine.DialogueSelection> SELECTION_CODEC =
            ByteBufCodecs.VAR_INT.map(InteractionDialogueSelectMessage::selectionById, Enum::ordinal);
    private static final StreamCodec<io.netty.buffer.ByteBuf, Optional<ResourceLocation>> OPTIONAL_EVENT_CODEC =
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs::optional);

    public static final CustomPacketPayload.Type<InteractionDialogueSelectMessage> TYPE =
            new CustomPacketPayload.Type<>(MCA.locate("interaction_dialogue_select"));
    public static final StreamCodec<FriendlyByteBuf, InteractionDialogueSelectMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, InteractionDialogueSelectMessage::offerToken,
            SELECTION_CODEC, InteractionDialogueSelectMessage::selection,
            OPTIONAL_EVENT_CODEC, InteractionDialogueSelectMessage::eventId,
            InteractionDialogueSelectMessage::new
    );

    public InteractionDialogueSelectMessage {
        Objects.requireNonNull(selection, "selection");
        eventId = Objects.requireNonNull(eventId, "eventId");
        if ((selection == DialogueEngine.DialogueSelection.EVENT) != eventId.isPresent()) {
            throw new IllegalArgumentException("Event selection requires exactly one event id");
        }
    }

    @Override
    public void handleServer(ServerPlayer player) {
        MCA.getDialogueEngine().ifPresent(engine -> engine.select(
                player,
                offerToken,
                selection,
                eventId.orElse(null)
        ).ifPresent(view -> Network.sendToPlayer(InteractionDialogueNodeResponse.active(view), player)));
    }

    private static DialogueEngine.DialogueSelection selectionById(int id) {
        DialogueEngine.DialogueSelection[] values = DialogueEngine.DialogueSelection.values();
        if (id < 0 || id >= values.length) {
            throw new IllegalArgumentException("Unknown dialogue selection id: " + id);
        }
        return values[id];
    }

    @Override
    public Type<InteractionDialogueSelectMessage> type() {
        return TYPE;
    }
}
