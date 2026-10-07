package net.conczin.mca.network.s2c;

import io.netty.buffer.ByteBuf;
import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.dialogue.DialogueEngine;
import net.conczin.mca.network.HandleablePayload;
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

public record InteractionDialogueOptionsResponse(
        long offerToken,
        Optional<Component> continuation,
        List<EventOption> eventOptions,
        boolean ambientAvailable,
        boolean legacyAvailable
) implements HandleablePayload {
    public static final int MAX_EVENT_OPTIONS = DialogueEngine.MAX_EVENT_OPTIONS;

    private static final StreamCodec<ByteBuf, Mode> MODE_CODEC =
            ByteBufCodecs.VAR_INT.map(InteractionDialogueOptionsResponse::modeById, Enum::ordinal);
    private static final StreamCodec<ByteBuf, Component> COMPONENT_CODEC =
            ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC;
    private static final StreamCodec<ByteBuf, Optional<Component>> OPTIONAL_COMPONENT_CODEC =
            COMPONENT_CODEC.apply(ByteBufCodecs::optional);
    private static final StreamCodec<ByteBuf, EventOption> EVENT_OPTION_CODEC = StreamCodec.composite(
            MODE_CODEC, EventOption::mode,
            ResourceLocation.STREAM_CODEC, EventOption::id,
            COMPONENT_CODEC, EventOption::prompt,
            EventOption::new
    );
    private static final StreamCodec<ByteBuf, List<EventOption>> EVENT_OPTIONS_CODEC =
            ByteBufCodecs.collection(ArrayList::new, EVENT_OPTION_CODEC, MAX_EVENT_OPTIONS);

    public static final CustomPacketPayload.Type<InteractionDialogueOptionsResponse> TYPE =
            new CustomPacketPayload.Type<>(MCA.locate("interaction_dialogue_options"));
    public static final StreamCodec<FriendlyByteBuf, InteractionDialogueOptionsResponse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, InteractionDialogueOptionsResponse::offerToken,
            OPTIONAL_COMPONENT_CODEC, InteractionDialogueOptionsResponse::continuation,
            EVENT_OPTIONS_CODEC, InteractionDialogueOptionsResponse::eventOptions,
            ByteBufCodecs.BOOL, InteractionDialogueOptionsResponse::ambientAvailable,
            ByteBufCodecs.BOOL, InteractionDialogueOptionsResponse::legacyAvailable,
            InteractionDialogueOptionsResponse::new
    );

    public InteractionDialogueOptionsResponse {
        continuation = Objects.requireNonNull(continuation, "continuation");
        eventOptions = List.copyOf(Objects.requireNonNull(eventOptions, "eventOptions"));
        if (eventOptions.size() > MAX_EVENT_OPTIONS) {
            throw new IllegalArgumentException("Dialogue menu exceeds event option budget: " + eventOptions.size());
        }
    }

    public static InteractionDialogueOptionsResponse from(DialogueEngine.DialogueOptions options) {
        List<EventOption> entries = new ArrayList<>();
        options.highlighted().ifPresent(option -> entries.add(new EventOption(Mode.HIGHLIGHTED, option.id(), option.prompt())));
        options.ask().forEach(option -> entries.add(new EventOption(Mode.ASK, option.id(), option.prompt())));
        return new InteractionDialogueOptionsResponse(
                options.token(),
                options.continuationPrompt(),
                entries,
                options.ambientAvailable(),
                false
        );
    }

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleDialogueOptionsResponse(this);
    }

    private static Mode modeById(int id) {
        Mode[] values = Mode.values();
        if (id < 0 || id >= values.length) {
            throw new IllegalArgumentException("Unknown dialogue option mode: " + id);
        }
        return values[id];
    }

    @Override
    public Type<InteractionDialogueOptionsResponse> type() {
        return TYPE;
    }

    public enum Mode {
        HIGHLIGHTED,
        ASK
    }

    public record EventOption(Mode mode, ResourceLocation id, Component prompt) {
        public EventOption {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(prompt, "prompt");
        }
    }
}
