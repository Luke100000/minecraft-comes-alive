package net.conczin.mca.network;

import net.conczin.mca.dialogue.DialogueEngine;
import net.conczin.mca.network.s2c.InteractionDialogueNodeResponse;
import net.conczin.mca.network.s2c.InteractionDialogueOptionsResponse;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialoguePresentationTest {
    @Test
    void revealsOneCompleteWordEveryQuarterSecondWithoutSkip() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID sessionId = UUID.randomUUID();
        InteractionDialogueNodeResponse response = active(
                sessionId,
                11L,
                Component.literal("Hello, world!  Again."),
                true,
                List.of(new InteractionDialogueNodeResponse.Choice("reply", Component.literal("Hi.")))
        );

        assertTrue(presentation.acceptNode(response, 1_000L, Locale.ENGLISH));
        assertEquals("", presentation.visibleLine().getString());
        assertFalse(presentation.canAdvance());
        assertTrue(presentation.visibleChoices().isEmpty());

        presentation.tick(1_249L);
        assertEquals("", presentation.visibleLine().getString());
        presentation.tick(1_250L);
        assertEquals("Hello, ", presentation.visibleLine().getString());
        assertFalse(presentation.canAdvance());
        assertTrue(presentation.visibleChoices().isEmpty());

        presentation.tick(1_499L);
        assertEquals("Hello, ", presentation.visibleLine().getString());
        presentation.tick(1_500L);
        assertEquals("Hello, world!  ", presentation.visibleLine().getString());
        assertFalse(presentation.canAdvance());

        presentation.tick(1_750L);
        assertEquals("Hello, world!  Again.", presentation.visibleLine().getString());
        assertTrue(presentation.canAdvance(), "clicks during reveal must not queue an advance");
        assertEquals(List.of("reply"), presentation.visibleChoices().stream()
                .map(InteractionDialogueNodeResponse.Choice::id)
                .toList());
    }

    @Test
    void stalledFrameRevealsOnlyOneWordInsteadOfBurstingToCatchUp() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        presentation.acceptNode(active(
                UUID.randomUUID(),
                12L,
                Component.literal("One two three four"),
                true,
                List.of()
        ), 1_000L, Locale.ENGLISH);

        presentation.tick(5_000L);
        assertEquals("One ", presentation.visibleLine().getString());
        presentation.tick(5_249L);
        assertEquals("One ", presentation.visibleLine().getString());
        presentation.tick(5_250L);
        assertEquals("One two ", presentation.visibleLine().getString());
    }

    @Test
    void punctuationOnlyLineStillRevealsBeforeControlsBecomeAvailable() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        presentation.acceptNode(active(
                UUID.randomUUID(),
                13L,
                Component.literal("... 👋 ?!"),
                true,
                List.of(new InteractionDialogueNodeResponse.Choice("wave", Component.literal("👋")))
        ), 0L, Locale.ENGLISH);

        assertEquals("", presentation.visibleLine().getString());
        assertFalse(presentation.canAdvance());
        assertTrue(presentation.visibleChoices().isEmpty());
        presentation.tick(250L);
        assertEquals("... 👋 ?!", presentation.visibleLine().getString());
        assertTrue(presentation.canAdvance());
        assertEquals(List.of("wave"), presentation.visibleChoices().stream()
                .map(InteractionDialogueNodeResponse.Choice::id)
                .toList());
    }

    @Test
    void preservesUnicodePunctuationAndComponentStylesAtWordBoundaries() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        MutableComponent line = Component.literal("Café, 👋 ").withStyle(ChatFormatting.RED)
                .append(Component.literal("friend!").withStyle(ChatFormatting.BOLD));

        presentation.acceptNode(active(UUID.randomUUID(), 21L, line, false, List.of()), 0L, Locale.ENGLISH);
        presentation.tick(250L);

        assertEquals("Café, 👋 ", presentation.visibleLine().getString());
        List<StyledPart> firstParts = styledParts(presentation.visibleLine());
        assertEquals(List.of(new StyledPart("Café, 👋 ", ChatFormatting.RED.getColor())), firstParts);

        presentation.tick(500L);
        assertEquals("Café, 👋 friend!", presentation.visibleLine().getString());
        List<StyledPart> allParts = styledParts(presentation.visibleLine());
        assertEquals(2, allParts.size());
        assertEquals("friend!", allParts.get(1).text());
        assertTrue(allParts.get(1).bold());
    }

    @Test
    void explicitResumePreservesResolvedLineAndRevealProgress() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID sessionId = UUID.randomUUID();
        Component source = Component.literal("First second third");

        presentation.acceptNode(active(sessionId, 31L, source, true, List.of()), 0L, Locale.ENGLISH);
        presentation.tick(250L);
        assertEquals("First ", presentation.visibleLine().getString());

        presentation.pause();
        presentation.beginRequest();
        presentation.acceptOptions(new InteractionDialogueOptionsResponse(
                40L,
                Optional.of(Component.literal("Want to finish what you were saying?")),
                List.of(),
                false,
                false
        ));
        presentation.markSelectionRequested(DialogueEngine.DialogueSelection.RESUME);
        assertTrue(presentation.acceptNode(active(sessionId, 41L, source, true, List.of()), 10_000L, Locale.ENGLISH));

        assertEquals("First ", presentation.visibleLine().getString(),
                "explicit resume must preserve the already resolved/revealed phrase");
        presentation.tick(10_249L);
        assertEquals("First ", presentation.visibleLine().getString());
        presentation.tick(10_250L);
        assertEquals("First second ", presentation.visibleLine().getString());
    }

    @Test
    void staleTerminalOrDifferentSessionCannotOverwriteNewerActiveNode() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID current = UUID.randomUUID();
        UUID stale = UUID.randomUUID();

        assertTrue(presentation.acceptNode(
                active(current, 51L, Component.literal("Current line"), true, List.of()),
                0L,
                Locale.ENGLISH
        ));
        assertFalse(presentation.acceptNode(new InteractionDialogueNodeResponse(
                stale, 50L, InteractionDialogueNodeResponse.State.ENDED, Optional.empty()), 0L, Locale.ENGLISH));
        assertFalse(presentation.acceptNode(
                active(stale, 52L, Component.literal("Stale replacement"), true, List.of()),
                0L,
                Locale.ENGLISH
        ));

        assertEquals(current, presentation.sessionId().orElseThrow());
        presentation.tick(250L);
        presentation.tick(500L);
        assertEquals("Current line", presentation.visibleLine().getString());
    }

    @Test
    void freshTalkRequestRejectsLateActiveResponseUntilASelectionIsMade() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID sessionId = UUID.randomUUID();
        assertTrue(presentation.acceptNode(
                active(sessionId, 51L, Component.literal("Old line"), true, List.of()),
                0L,
                Locale.ENGLISH
        ));

        presentation.beginRequest();
        assertFalse(presentation.acceptNode(new InteractionDialogueNodeResponse(
                sessionId,
                51L,
                InteractionDialogueNodeResponse.State.PAUSED,
                Optional.empty()
        ), 5L, Locale.ENGLISH));
        assertFalse(presentation.acceptNode(
                active(sessionId, 52L, Component.literal("Late line"), true, List.of()),
                10L,
                Locale.ENGLISH
        ));
        InteractionDialogueOptionsResponse menu = new InteractionDialogueOptionsResponse(
                53L,
                Optional.of(Component.literal("Resume?")),
                List.of(),
                false,
                false
        );
        assertTrue(presentation.acceptOptions(menu));
        presentation.markSelectionRequested(DialogueEngine.DialogueSelection.RESUME);
        assertTrue(presentation.acceptNode(
                active(sessionId, 54L, Component.literal("Old line"), true, List.of()),
                20L,
                Locale.ENGLISH
        ));
    }

    @Test
    void sameOfferRefreshUpdatesChoicesWithoutResettingReveal() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID sessionId = UUID.randomUUID();
        Component line = Component.literal("One two");

        presentation.acceptNode(active(
                sessionId,
                55L,
                line,
                false,
                List.of(new InteractionDialogueNodeResponse.Choice("old", Component.literal("Old")))
        ), 0L, Locale.ENGLISH);
        presentation.tick(250L);
        assertEquals("One ", presentation.visibleLine().getString());

        assertTrue(presentation.acceptNode(active(
                sessionId,
                55L,
                line,
                false,
                List.of(new InteractionDialogueNodeResponse.Choice("new", Component.literal("New")))
        ), 300L, Locale.ENGLISH));
        assertEquals("One ", presentation.visibleLine().getString());

        presentation.tick(500L);
        assertEquals("One two", presentation.visibleLine().getString());
        assertEquals(List.of("new"), presentation.visibleChoices().stream()
                .map(InteractionDialogueNodeResponse.Choice::id)
                .toList());
    }

    @Test
    void pausedAndEndedResponsesClearControlsButOnlyEndDropsSnapshot() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID sessionId = UUID.randomUUID();
        presentation.acceptNode(active(
                sessionId,
                61L,
                Component.literal("Keep this phrase"),
                true,
                List.of(new InteractionDialogueNodeResponse.Choice("x", Component.literal("X")))
        ), 0L, Locale.ENGLISH);
        presentation.tick(250L);

        assertTrue(presentation.acceptNode(new InteractionDialogueNodeResponse(
                sessionId, 61L, InteractionDialogueNodeResponse.State.PAUSED, Optional.empty()), 250L, Locale.ENGLISH));
        assertFalse(presentation.nodeVisible());
        assertTrue(presentation.visibleChoices().isEmpty());
        assertEquals(sessionId, presentation.sessionId().orElseThrow(), "pause keeps resumable snapshot identity");

        assertTrue(presentation.acceptNode(new InteractionDialogueNodeResponse(
                sessionId, 61L, InteractionDialogueNodeResponse.State.ENDED, Optional.empty()), 250L, Locale.ENGLISH));
        assertTrue(presentation.sessionId().isEmpty());
        assertEquals("", presentation.visibleLine().getString());
        assertFalse(presentation.canAdvance());
    }

    @Test
    void optionsAndClearRemainBoundedTransientState() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        InteractionDialogueOptionsResponse options = new InteractionDialogueOptionsResponse(
                71L,
                Optional.empty(),
                List.of(new InteractionDialogueOptionsResponse.EventOption(
                        InteractionDialogueOptionsResponse.Mode.ASK,
                        ResourceLocation.parse("mca:test"),
                        Component.literal("Ask")
                )),
                true,
                false
        );

        presentation.beginRequest();
        presentation.acceptOptions(options);
        assertEquals(options, presentation.options().orElseThrow());
        presentation.markSelectionRequested(DialogueEngine.DialogueSelection.AMBIENT);
        assertEquals(options, presentation.options().orElseThrow(),
                "a rejected selection must leave its server-issued menu recoverable");
        presentation.clear();
        assertTrue(presentation.options().isEmpty());
        assertTrue(presentation.sessionId().isEmpty());
        assertEquals("", presentation.visibleLine().getString());
    }

    private static InteractionDialogueNodeResponse active(
            UUID sessionId,
            long offerToken,
            Component line,
            boolean canContinue,
            List<InteractionDialogueNodeResponse.Choice> choices
    ) {
        return new InteractionDialogueNodeResponse(
                sessionId,
                offerToken,
                InteractionDialogueNodeResponse.State.ACTIVE,
                Optional.of(new InteractionDialogueNodeResponse.Node(
                        ResourceLocation.parse("mca:test/story"),
                        line,
                        false,
                        canContinue,
                        choices
                ))
        );
    }

    private static List<StyledPart> styledParts(Component component) {
        List<StyledPart> parts = new ArrayList<>();
        component.visit((style, text) -> {
            if (!text.isEmpty()) {
                parts.add(new StyledPart(
                        text,
                        style.getColor() == null ? null : style.getColor().getValue(),
                        style.isBold()
                ));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return parts;
    }

    private record StyledPart(String text, Integer color, boolean bold) {
        StyledPart(String text, Integer color) {
            this(text, color, false);
        }
    }
}
