package net.conczin.mca.network.s2c;

import io.netty.buffer.ByteBuf;
import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.dialogue.DialogueEngine;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record InteractionDialogueNodeResponse(
        UUID sessionId,
        long offerToken,
        State state,
        Optional<Node> node
) implements HandleablePayload {
    public static final int MAX_CHOICES = 32;

    private static final StreamCodec<ByteBuf, Component> COMPONENT_CODEC =
            ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC;
    private static final StreamCodec<ByteBuf, String> CHOICE_ID_CODEC =
            ByteBufCodecs.stringUtf8(net.conczin.mca.network.c2s.InteractionDialogueChoiceMessage.MAX_CHOICE_ID_LENGTH);
    private static final StreamCodec<ByteBuf, Choice> CHOICE_CODEC = StreamCodec.composite(
            CHOICE_ID_CODEC, Choice::id,
            COMPONENT_CODEC, Choice::text,
            Choice::new
    );
    private static final StreamCodec<ByteBuf, List<Choice>> CHOICES_CODEC =
            ByteBufCodecs.collection(ArrayList::new, CHOICE_CODEC, MAX_CHOICES);
    private static final StreamCodec<ByteBuf, Node> NODE_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, Node::eventId,
            COMPONENT_CODEC, Node::line,
            ByteBufCodecs.BOOL, Node::silent,
            ByteBufCodecs.BOOL, Node::canContinue,
            CHOICES_CODEC, Node::choices,
            Node::new
    );
    private static final StreamCodec<ByteBuf, Optional<Node>> OPTIONAL_NODE_CODEC =
            NODE_CODEC.apply(ByteBufCodecs::optional);
    private static final StreamCodec<ByteBuf, State> STATE_CODEC =
            ByteBufCodecs.VAR_INT.map(InteractionDialogueNodeResponse::stateById, Enum::ordinal);

    public static final CustomPacketPayload.Type<InteractionDialogueNodeResponse> TYPE =
            new CustomPacketPayload.Type<>(MCA.locate("interaction_dialogue_node"));
    public static final StreamCodec<FriendlyByteBuf, InteractionDialogueNodeResponse> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, InteractionDialogueNodeResponse::sessionId,
            ByteBufCodecs.VAR_LONG, InteractionDialogueNodeResponse::offerToken,
            STATE_CODEC, InteractionDialogueNodeResponse::state,
            OPTIONAL_NODE_CODEC, InteractionDialogueNodeResponse::node,
            InteractionDialogueNodeResponse::new
    );

    public InteractionDialogueNodeResponse {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(state, "state");
        node = Objects.requireNonNull(node, "node");
        if ((state == State.ACTIVE) != node.isPresent()) {
            throw new IllegalArgumentException("Active dialogue response requires exactly one node view");
        }
    }

    public static InteractionDialogueNodeResponse active(DialogueEngine.DialogueNodeView view) {
        return new InteractionDialogueNodeResponse(
                view.sessionId(),
                view.offerToken(),
                State.ACTIVE,
                Optional.of(new Node(
                        view.eventId(),
                        view.line(),
                        view.silent(),
                        view.canContinue(),
                        view.choices().stream().map(choice -> new Choice(choice.id(), choice.text())).toList()
                ))
        );
    }

    public static InteractionDialogueNodeResponse ended(UUID sessionId, long offerToken) {
        return new InteractionDialogueNodeResponse(sessionId, offerToken, State.ENDED, Optional.empty());
    }

    public static InteractionDialogueNodeResponse paused(UUID sessionId, long offerToken) {
        return new InteractionDialogueNodeResponse(sessionId, offerToken, State.PAUSED, Optional.empty());
    }

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleDialogueNodeResponse(this);
    }

    private static State stateById(int id) {
        State[] values = State.values();
        if (id < 0 || id >= values.length) {
            throw new IllegalArgumentException("Unknown dialogue node state: " + id);
        }
        return values[id];
    }

    @Override
    public Type<InteractionDialogueNodeResponse> type() {
        return TYPE;
    }

    public enum State {
        ACTIVE,
        PAUSED,
        ENDED
    }

    public record Node(
            ResourceLocation eventId,
            Component line,
            boolean silent,
            boolean canContinue,
            List<Choice> choices
    ) {
        public Node {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(line, "line");
            choices = List.copyOf(Objects.requireNonNull(choices, "choices"));
            if (choices.size() > MAX_CHOICES) {
                throw new IllegalArgumentException("Dialogue node exceeds choice budget: " + choices.size());
            }
        }
    }

    public record Choice(String id, Component text) {
        public Choice {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(text, "text");
            if (id.isEmpty() || id.length() > net.conczin.mca.network.c2s.InteractionDialogueChoiceMessage.MAX_CHOICE_ID_LENGTH) {
                throw new IllegalArgumentException("Dialogue choice id length is invalid");
            }
        }
    }
}
