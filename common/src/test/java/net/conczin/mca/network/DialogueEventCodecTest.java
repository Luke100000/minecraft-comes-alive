package net.conczin.mca.network;

import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.conczin.mca.dialogue.DialogueEngine;
import net.conczin.mca.network.c2s.InteractionDialogueAdvanceMessage;
import net.conczin.mca.network.c2s.InteractionDialogueBeginMessage;
import net.conczin.mca.network.c2s.InteractionDialogueChoiceMessage;
import net.conczin.mca.network.c2s.InteractionDialogueSelectMessage;
import net.conczin.mca.network.s2c.InteractionDialogueNodeResponse;
import net.conczin.mca.network.s2c.InteractionDialogueOptionsResponse;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DialogueEventCodecTest {
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void roundTripsAllClientRequests() {
        UUID villagerId = UUID.fromString("52de5f7b-29b7-4b80-9f92-68f7e054189a");
        ResourceLocation eventId = ResourceLocation.parse("addon:story/identity");

        assertEquals(
                new InteractionDialogueBeginMessage(villagerId),
                roundTrip(InteractionDialogueBeginMessage.STREAM_CODEC, new InteractionDialogueBeginMessage(villagerId))
        );
        assertEquals(
                new InteractionDialogueSelectMessage(
                        41L,
                        DialogueEngine.DialogueSelection.EVENT,
                        Optional.of(eventId)
                ),
                roundTrip(
                        InteractionDialogueSelectMessage.STREAM_CODEC,
                        new InteractionDialogueSelectMessage(
                                41L,
                                DialogueEngine.DialogueSelection.EVENT,
                                Optional.of(eventId)
                        )
                )
        );
        assertEquals(
                new InteractionDialogueSelectMessage(42L, DialogueEngine.DialogueSelection.RESUME, Optional.empty()),
                roundTrip(
                        InteractionDialogueSelectMessage.STREAM_CODEC,
                        new InteractionDialogueSelectMessage(42L, DialogueEngine.DialogueSelection.RESUME, Optional.empty())
                )
        );
        assertEquals(
                new InteractionDialogueChoiceMessage(43L, "ask_why"),
                roundTrip(
                        InteractionDialogueChoiceMessage.STREAM_CODEC,
                        new InteractionDialogueChoiceMessage(43L, "ask_why")
                )
        );
        assertEquals(
                new InteractionDialogueAdvanceMessage(44L),
                roundTrip(InteractionDialogueAdvanceMessage.STREAM_CODEC, new InteractionDialogueAdvanceMessage(44L))
        );
    }

    @Test
    void selectRequiresEventIdOnlyForEventSelection() {
        ResourceLocation eventId = ResourceLocation.parse("mca:test");

        assertThrows(IllegalArgumentException.class, () -> new InteractionDialogueSelectMessage(
                1L, DialogueEngine.DialogueSelection.EVENT, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new InteractionDialogueSelectMessage(
                1L, DialogueEngine.DialogueSelection.RESUME, Optional.of(eventId)));
        assertThrows(IllegalArgumentException.class, () -> new InteractionDialogueSelectMessage(
                1L, DialogueEngine.DialogueSelection.AMBIENT, Optional.of(eventId)));
        assertThrows(IllegalArgumentException.class, () -> new InteractionDialogueSelectMessage(
                1L, DialogueEngine.DialogueSelection.LEGACY, Optional.of(eventId)));
    }

    @Test
    void roundTripsOptionsWithContinuationAndServerComponents() {
        InteractionDialogueOptionsResponse response = new InteractionDialogueOptionsResponse(
                81L,
                Optional.of(Component.translatable("dialogue.resume", Component.literal("Robin"))),
                List.of(
                        new InteractionDialogueOptionsResponse.EventOption(
                                InteractionDialogueOptionsResponse.Mode.HIGHLIGHTED,
                                ResourceLocation.parse("mca:gloomy/mourning"),
                                Component.literal("You look upset.").withStyle(ChatFormatting.YELLOW)
                        ),
                        new InteractionDialogueOptionsResponse.EventOption(
                                InteractionDialogueOptionsResponse.Mode.ASK,
                                ResourceLocation.parse("addon:zombie/identity"),
                                Component.translatable("dialogue.addon.zombie.prompt")
                        )
                ),
                true,
                false
        );

        InteractionDialogueOptionsResponse decoded = roundTrip(InteractionDialogueOptionsResponse.STREAM_CODEC, response);
        assertEquals(response.offerToken(), decoded.offerToken());
        assertEquals(response.ambientAvailable(), decoded.ambientAvailable());
        assertEquals(response.legacyAvailable(), decoded.legacyAvailable());
        assertComponentEquivalent(response.continuation().orElseThrow(), decoded.continuation().orElseThrow());
        assertEquals(response.eventOptions().size(), decoded.eventOptions().size());
        for (int i = 0; i < response.eventOptions().size(); i++) {
            InteractionDialogueOptionsResponse.EventOption expected = response.eventOptions().get(i);
            InteractionDialogueOptionsResponse.EventOption actual = decoded.eventOptions().get(i);
            assertEquals(expected.mode(), actual.mode());
            assertEquals(expected.id(), actual.id());
            assertComponentEquivalent(expected.prompt(), actual.prompt());
        }
    }

    @Test
    void roundTripsActivePausedAndEndedNodeResponses() {
        UUID sessionId = UUID.fromString("271f7248-06b0-4bc0-aeaa-b076a747c804");
        InteractionDialogueNodeResponse.Node activeNode = new InteractionDialogueNodeResponse.Node(
                ResourceLocation.parse("mca:zombie/identity"),
                Component.literal("Do you think I'm still me?").withStyle(ChatFormatting.ITALIC),
                false,
                true,
                List.of(
                        new InteractionDialogueNodeResponse.Choice("yes", Component.literal("Of course.")),
                        new InteractionDialogueNodeResponse.Choice("unsure", Component.literal("I'm not sure."))
                )
        );

        InteractionDialogueNodeResponse active = new InteractionDialogueNodeResponse(
                sessionId, 91L, InteractionDialogueNodeResponse.State.ACTIVE, Optional.of(activeNode));
        InteractionDialogueNodeResponse paused = new InteractionDialogueNodeResponse(
                sessionId, 92L, InteractionDialogueNodeResponse.State.PAUSED, Optional.empty());
        InteractionDialogueNodeResponse ended = new InteractionDialogueNodeResponse(
                sessionId, 93L, InteractionDialogueNodeResponse.State.ENDED, Optional.empty());

        assertEquals(active, roundTrip(InteractionDialogueNodeResponse.STREAM_CODEC, active));
        assertEquals(paused, roundTrip(InteractionDialogueNodeResponse.STREAM_CODEC, paused));
        assertEquals(ended, roundTrip(InteractionDialogueNodeResponse.STREAM_CODEC, ended));
    }

    @Test
    void nodeStateRequiresViewOnlyWhileActive() {
        UUID sessionId = UUID.randomUUID();
        InteractionDialogueNodeResponse.Node node = new InteractionDialogueNodeResponse.Node(
                ResourceLocation.parse("mca:test"), Component.literal("line"), false, true, List.of());

        assertThrows(IllegalArgumentException.class, () -> new InteractionDialogueNodeResponse(
                sessionId, 1L, InteractionDialogueNodeResponse.State.ACTIVE, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new InteractionDialogueNodeResponse(
                sessionId, 1L, InteractionDialogueNodeResponse.State.ENDED, Optional.of(node)));
        assertThrows(IllegalArgumentException.class, () -> new InteractionDialogueNodeResponse(
                sessionId, 1L, InteractionDialogueNodeResponse.State.PAUSED, Optional.of(node)));
    }

    @Test
    void codecsAndConstructorsEnforceCollectionAndIdentifierBounds() {
        List<InteractionDialogueOptionsResponse.EventOption> tooManyOptions = new ArrayList<>();
        for (int i = 0; i <= InteractionDialogueOptionsResponse.MAX_EVENT_OPTIONS; i++) {
            tooManyOptions.add(new InteractionDialogueOptionsResponse.EventOption(
                    InteractionDialogueOptionsResponse.Mode.ASK,
                    ResourceLocation.fromNamespaceAndPath("mca", "test/" + i),
                    Component.literal("prompt")
            ));
        }
        assertThrows(IllegalArgumentException.class, () -> new InteractionDialogueOptionsResponse(
                1L, Optional.empty(), tooManyOptions, false, false));

        List<InteractionDialogueNodeResponse.Choice> tooManyChoices = new ArrayList<>();
        for (int i = 0; i <= InteractionDialogueNodeResponse.MAX_CHOICES; i++) {
            tooManyChoices.add(new InteractionDialogueNodeResponse.Choice("choice_" + i, Component.literal("reply")));
        }
        assertThrows(IllegalArgumentException.class, () -> new InteractionDialogueNodeResponse.Node(
                ResourceLocation.parse("mca:test"), Component.literal("line"), false, false, tooManyChoices));

        assertThrows(IllegalArgumentException.class, () -> new InteractionDialogueChoiceMessage(
                1L, "x".repeat(InteractionDialogueChoiceMessage.MAX_CHOICE_ID_LENGTH + 1)));
    }

    private static <T> T roundTrip(StreamCodec<? super FriendlyByteBuf, T> codec, T value) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buffer, value);
            return codec.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static void assertComponentEquivalent(Component expected, Component actual) {
        assertEquals(
                ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, expected).getOrThrow(),
                ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, actual).getOrThrow()
        );
    }
}
