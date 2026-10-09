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
    void previousAndNextReviewSpeechWithoutReplayingRevealOrAllowingStaleChoices() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID session = UUID.randomUUID();
        assertTrue(presentation.acceptNode(active(session, 1L, Component.literal("First line"),
                DialogueEngine.AdvanceKind.NEXT, List.of()), 0L, Locale.ENGLISH));
        tickSteps(presentation, 0L, 10);
        assertTrue(presentation.acceptNode(active(session, 2L, Component.literal("Second line"),
                DialogueEngine.AdvanceKind.NEXT,
                List.of(new InteractionDialogueNodeResponse.Choice("yes", Component.literal("Yes")))), 450L, Locale.ENGLISH));
        presentation.tick(490L);
        assertEquals("S", presentation.visibleLine().getString());

        assertTrue(presentation.reviewPrevious());
        assertEquals("First line", presentation.visibleLine().getString(), "old speech is instantly visible");
        assertTrue(presentation.reviewingHistory());
        assertFalse(presentation.canAdvance(), "old speech cannot advance the server");
        assertTrue(presentation.visibleChoices().isEmpty(), "old choices must never be usable");
        presentation.tick(530L);
        assertEquals("First line", presentation.visibleLine().getString(), "history does not animate");
        assertTrue(presentation.reviewNext());
        assertFalse(presentation.reviewingHistory());
        assertEquals("Se", presentation.visibleLine().getString(), "live reveal progress resumes without restarting");
        assertFalse(presentation.reviewNext(), "Next on the live line is owned by server progression");
        assertFalse(presentation.canAdvance(), "live line is still revealing");
    }

    @Test
    void repeatedPacketsAndResumeKeepOneHistoryEntryButReplacementClearsIt() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID original = UUID.randomUUID();
        assertTrue(presentation.acceptNode(active(original, 1L, Component.literal("Earlier"),
                DialogueEngine.AdvanceKind.NEXT, List.of()), 0L, Locale.ENGLISH));
        assertTrue(presentation.acceptNode(active(original, 2L, Component.literal("Current"),
                DialogueEngine.AdvanceKind.NEXT, List.of()), 100L, Locale.ENGLISH));
        assertTrue(presentation.acceptNode(active(original, 2L, Component.literal("Current"),
                DialogueEngine.AdvanceKind.NEXT, List.of()), 120L, Locale.ENGLISH));

        presentation.pause();
        presentation.beginRequest();
        assertTrue(presentation.acceptOptions(new InteractionDialogueOptionsResponse(3L,
                Optional.of(Component.literal("Resume")), List.of(), false)));
        presentation.markSelectionRequested(DialogueEngine.DialogueSelection.RESUME);
        assertTrue(presentation.acceptNode(active(original, 4L, Component.literal("Current"),
                DialogueEngine.AdvanceKind.NEXT, List.of()), 300L, Locale.ENGLISH));
        assertTrue(presentation.reviewPrevious());
        assertEquals("Earlier", presentation.visibleLine().getString());
        assertFalse(presentation.reviewPrevious(), "resume and duplicate packets must not append fake history");

        presentation.beginRequest();
        assertTrue(presentation.acceptOptions(new InteractionDialogueOptionsResponse(5L,
                Optional.empty(), List.of(), true)));
        presentation.markSelectionRequested(DialogueEngine.DialogueSelection.AMBIENT);
        assertTrue(presentation.acceptNode(active(UUID.randomUUID(), 6L, Component.literal("New session"),
                DialogueEngine.AdvanceKind.NEXT, List.of()), 400L, Locale.ENGLISH));
        assertFalse(presentation.reviewPrevious(), "new session must not leak previous villager speech");
        presentation.clear();
        assertFalse(presentation.canReviewPrevious());
    }

    @Test
    void speechReviewHistoryHasBoundedCapacity() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID session = UUID.randomUUID();
        for (int i = 0; i < 70; i++) {
            assertTrue(presentation.acceptNode(active(session, i + 1L, Component.literal("Line " + i),
                    DialogueEngine.AdvanceKind.NEXT, List.of()), i * 100L, Locale.ENGLISH));
        }
        int previousCount = 0;
        while (presentation.reviewPrevious()) {
            previousCount++;
        }
        assertEquals(63, previousCount, "retain only the last 64 accepted speech lines");
        assertEquals("Line 6", presentation.visibleLine().getString());
        for (int i = 0; i < 63; i++) {
            assertTrue(presentation.reviewNext());
        }
        assertFalse(presentation.reviewNext());
    }

    @Test
    void ambientPreviewRevealsWhileMenuOptionsRemainAvailableAndTransitionsToActiveNode() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        InteractionDialogueNodeResponse.Node opening = new InteractionDialogueNodeResponse.Node(
                ResourceLocation.parse("test:ambient"), Component.literal("Hello"), false,
                DialogueEngine.AdvanceKind.NEXT, List.of());
        presentation.beginRequest();
        assertTrue(presentation.acceptOptions(new InteractionDialogueOptionsResponse(
                10L, Optional.empty(), List.of(), true, Optional.of(opening)),
                1_000L, Locale.ENGLISH, line -> line));
        assertTrue(presentation.options().isPresent(), "topics must remain available alongside the greeting");
        assertEquals("", presentation.visibleLine().getString());
        assertFalse(presentation.canAdvance());
        tickSteps(presentation, 1_000L, 5);
        assertEquals("Hello", presentation.visibleLine().getString());
        assertTrue(presentation.canAdvance());
        presentation.markSelectionRequested(DialogueEngine.DialogueSelection.AMBIENT);
        assertTrue(presentation.acceptNode(active(UUID.randomUUID(), 11L,
                Component.literal("Second"), DialogueEngine.AdvanceKind.BACK_TO_TOPICS,
                List.of()), 1_300L, Locale.ENGLISH));
        assertTrue(presentation.options().isEmpty());
        assertEquals("", presentation.visibleLine().getString(),
                "engagement must display the next line with a fresh reveal");
    }

    @Test
    void rejectedStaleSelectionCanRequestFreshMenuOnlyForItsPendingOffer() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        presentation.beginRequest();
        assertTrue(presentation.acceptOptions(new InteractionDialogueOptionsResponse(
                10L, Optional.empty(), List.of(), true)));
        presentation.markSelectionRequested(DialogueEngine.DialogueSelection.AMBIENT);

        assertFalse(presentation.acceptSelectionRejection(9L), "another offer must not trigger a refresh");
        assertTrue(presentation.acceptSelectionRejection(10L), "the rejected selection must unblock menu recovery");

        presentation.beginRequest();
        assertTrue(presentation.options().isEmpty(), "stale options cannot remain clickable during refresh");
        assertFalse(presentation.acceptSelectionRejection(10L), "duplicate rejection must not repeat the request");
        assertTrue(presentation.acceptOptions(new InteractionDialogueOptionsResponse(
                11L, Optional.empty(), List.of(), false)));
        assertEquals(11L, presentation.options().orElseThrow().offerToken());
        assertFalse(presentation.acceptSelectionRejection(10L), "late rejection cannot clear a newer server menu");
    }

    @Test
    void serverPushedReloadMenuSupersedesPendingSelectionRejection() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        presentation.beginRequest();
        assertTrue(presentation.acceptOptions(new InteractionDialogueOptionsResponse(
                20L, Optional.empty(), List.of(), true)));
        presentation.markSelectionRequested(DialogueEngine.DialogueSelection.AMBIENT);

        assertTrue(presentation.acceptOptions(new InteractionDialogueOptionsResponse(
                21L, Optional.empty(), List.of(), false)));
        assertFalse(presentation.acceptSelectionRejection(20L));
        assertEquals(21L, presentation.options().orElseThrow().offerToken());
    }

    @Test
    void staleSelectionAwaitingReplyAcceptsFreshMenuAfterReload() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        presentation.beginRequest();
        assertTrue(presentation.acceptOptions(new InteractionDialogueOptionsResponse(
                10L, Optional.empty(), List.of(), false)));
        presentation.markSelectionRequested(DialogueEngine.DialogueSelection.EVENT);

        assertTrue(presentation.acceptOptions(new InteractionDialogueOptionsResponse(
                11L, Optional.empty(), List.of(), false)),
                "new server menu must recover a pending selection invalidated by datapack reload");
        assertEquals(11L, presentation.options().orElseThrow().offerToken());
        assertFalse(presentation.acceptNode(active(UUID.randomUUID(), 12L, Component.literal("old"),
                DialogueEngine.AdvanceKind.NEXT, List.of()), 0L, Locale.ENGLISH),
                "old in-flight node response must not replace the newly refreshed menu");
    }

    @Test
    void revealsOneUnicodeGraphemeEveryFortyMillisecondsWithoutSkip() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID sessionId = UUID.randomUUID();
        InteractionDialogueNodeResponse response = active(
                sessionId,
                11L,
                Component.literal("A\u0301👋!"),
                DialogueEngine.AdvanceKind.BACK_TO_TOPICS,
                List.of(new InteractionDialogueNodeResponse.Choice("reply", Component.literal("Hi.")))
        );

        assertTrue(presentation.acceptNode(response, 1_000L, Locale.ENGLISH));
        assertEquals("", presentation.visibleLine().getString());
        assertFalse(presentation.canAdvance());
        assertTrue(presentation.visibleChoices().isEmpty());

        presentation.tick(1_039L);
        assertEquals("", presentation.visibleLine().getString());
        presentation.tick(1_040L);
        assertEquals("A\u0301", presentation.visibleLine().getString());
        assertFalse(presentation.canAdvance());
        assertTrue(presentation.visibleChoices().isEmpty());

        presentation.tick(1_079L);
        assertEquals("A\u0301", presentation.visibleLine().getString());
        presentation.tick(1_080L);
        assertEquals("A\u0301👋", presentation.visibleLine().getString());
        assertFalse(presentation.canAdvance());

        presentation.tick(1_120L);
        assertEquals("A\u0301👋!", presentation.visibleLine().getString());
        assertTrue(presentation.canAdvance(), "clicks during reveal must not queue an advance");
        assertEquals(DialogueEngine.AdvanceKind.BACK_TO_TOPICS, presentation.advanceKind().orElseThrow());
        assertEquals(List.of("reply"), presentation.visibleChoices().stream()
                .map(InteractionDialogueNodeResponse.Choice::id)
                .toList());
    }

    @Test
    void missingOptionalClientTranslationStillRevealsAndEnablesServerAuthoredControls() {
        String unknownLine = "optional_addon.dialogue.untranslated_server_line";
        String unknownReply = "optional_addon.dialogue.untranslated_reply";
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        InteractionDialogueNodeResponse response = active(
                UUID.randomUUID(), 99L, Component.translatable(unknownLine),
                DialogueEngine.AdvanceKind.NEXT,
                List.of(new InteractionDialogueNodeResponse.Choice("continue", Component.translatable(unknownReply)))
        );

        assertTrue(presentation.acceptNode(response, 0L, Locale.ENGLISH),
                "a server-owned node must not require the client to know its optional translation");
        assertFalse(presentation.canAdvance(), "missing localization must not bypass the normal reveal timer");
        tickSteps(presentation, 0L, unknownLine.length());
        assertEquals(unknownLine, presentation.visibleLine().getString(),
                "missing localization should remain visible as the key, not discard the node");
        assertTrue(presentation.canAdvance(), "Next must still be available after revealing an unknown key");
        assertEquals(List.of("continue"), presentation.visibleChoices().stream()
                .map(InteractionDialogueNodeResponse.Choice::id).toList(),
                "the server's stable choice ID must survive missing client localization");
        assertEquals(unknownReply, presentation.visibleChoices().getFirst().text().getString());
    }

    @Test
    void stalledFrameRevealsOnlyOneGraphemeInsteadOfBurstingToCatchUp() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        presentation.acceptNode(active(
                UUID.randomUUID(),
                12L,
                Component.literal("One"),
                DialogueEngine.AdvanceKind.NEXT,
                List.of()
        ), 1_000L, Locale.ENGLISH);

        presentation.tick(5_000L);
        assertEquals("O", presentation.visibleLine().getString());
        presentation.tick(5_039L);
        assertEquals("O", presentation.visibleLine().getString());
        presentation.tick(5_040L);
        assertEquals("On", presentation.visibleLine().getString());
    }

    @Test
    void slightlyLateFrameKeepsFortyMillisecondScheduleWithoutDrift() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        presentation.acceptNode(active(
                UUID.randomUUID(),
                121L,
                Component.literal("AB"),
                DialogueEngine.AdvanceKind.NEXT,
                List.of()
        ), 1_000L, Locale.ENGLISH);

        presentation.tick(1_050L);
        assertEquals("A", presentation.visibleLine().getString());
        presentation.tick(1_079L);
        assertEquals("A", presentation.visibleLine().getString());
        presentation.tick(1_080L);
        assertEquals("AB", presentation.visibleLine().getString(),
                "a slightly late render frame must not permanently shift the 40 ms reveal schedule");
    }

    @Test
    void punctuationOnlyLineStillRevealsBeforeControlsBecomeAvailable() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        presentation.acceptNode(active(
                UUID.randomUUID(),
                13L,
                Component.literal("..."),
                DialogueEngine.AdvanceKind.NEXT,
                List.of(new InteractionDialogueNodeResponse.Choice("wave", Component.literal("👋")))
        ), 0L, Locale.ENGLISH);

        assertEquals("", presentation.visibleLine().getString());
        assertFalse(presentation.canAdvance());
        assertTrue(presentation.visibleChoices().isEmpty());
        presentation.tick(40L);
        assertEquals(".", presentation.visibleLine().getString());
        assertFalse(presentation.canAdvance());
        presentation.tick(80L);
        assertEquals("..", presentation.visibleLine().getString());
        presentation.tick(120L);
        assertEquals("...", presentation.visibleLine().getString());
        assertTrue(presentation.canAdvance());
        assertEquals(List.of("wave"), presentation.visibleChoices().stream()
                .map(InteractionDialogueNodeResponse.Choice::id)
                .toList());
    }

    @Test
    void preservesUnicodePunctuationAndComponentStylesAtGraphemeBoundaries() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        MutableComponent line = Component.literal("Cafe\u0301 👋 ").withStyle(ChatFormatting.RED)
                .append(Component.literal("friend!").withStyle(ChatFormatting.BOLD));

        presentation.acceptNode(active(UUID.randomUUID(), 21L, line, DialogueEngine.AdvanceKind.NONE, List.of()),
                0L, Locale.ENGLISH);
        tickSteps(presentation, 0L, 7);

        assertEquals("Cafe\u0301 👋 ", presentation.visibleLine().getString());
        List<StyledPart> firstParts = styledParts(presentation.visibleLine());
        assertEquals(List.of(new StyledPart("Cafe\u0301 👋 ", ChatFormatting.RED.getColor())), firstParts);

        tickSteps(presentation, 280L, 7);
        assertEquals("Cafe\u0301 👋 friend!", presentation.visibleLine().getString());
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

        presentation.acceptNode(active(sessionId, 31L, source, DialogueEngine.AdvanceKind.NEXT, List.of()), 0L, Locale.ENGLISH);
        presentation.tick(40L);
        assertEquals("F", presentation.visibleLine().getString());

        presentation.pause();
        presentation.beginRequest();
        assertTrue(presentation.sessionId().isEmpty(),
                "closing while a resumed Talk menu is still loading must not send the old session ID");
        assertTrue(presentation.offerToken().isEmpty(),
                "closing while a resumed Talk menu is still loading must not send the old node token");
        presentation.acceptOptions(new InteractionDialogueOptionsResponse(
                40L,
                Optional.of(Component.literal("Want to finish what you were saying?")),
                List.of(),
                false
        ));
        presentation.markSelectionRequested(DialogueEngine.DialogueSelection.RESUME);
        assertTrue(presentation.acceptNode(active(
                sessionId, 41L, source, DialogueEngine.AdvanceKind.NEXT, List.of()), 10_000L, Locale.ENGLISH));

        assertEquals("F", presentation.visibleLine().getString(),
                "explicit resume must preserve the already resolved/revealed phrase");
        presentation.tick(10_039L);
        assertEquals("F", presentation.visibleLine().getString());
        presentation.tick(10_040L);
        assertEquals("Fi", presentation.visibleLine().getString());
    }

    @Test
    void staleTerminalOrDifferentSessionCannotOverwriteNewerActiveNode() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID current = UUID.randomUUID();
        UUID stale = UUID.randomUUID();

        assertTrue(presentation.acceptNode(
                active(current, 51L, Component.literal("Current line"), DialogueEngine.AdvanceKind.NEXT, List.of()),
                0L,
                Locale.ENGLISH
        ));
        assertFalse(presentation.acceptNode(new InteractionDialogueNodeResponse(
                stale, 50L, InteractionDialogueNodeResponse.State.ENDED, Optional.empty()), 0L, Locale.ENGLISH));
        assertFalse(presentation.acceptNode(
                active(stale, 52L, Component.literal("Stale replacement"), DialogueEngine.AdvanceKind.NEXT, List.of()),
                0L,
                Locale.ENGLISH
        ));

        assertEquals(current, presentation.sessionId().orElseThrow());
        tickSteps(presentation, 0L, 12);
        assertEquals("Current line", presentation.visibleLine().getString());
    }

    @Test
    void freshTalkRequestRejectsLateActiveResponseUntilASelectionIsMade() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID sessionId = UUID.randomUUID();
        assertTrue(presentation.acceptNode(
                active(sessionId, 51L, Component.literal("Old line"), DialogueEngine.AdvanceKind.NEXT, List.of()),
                0L,
                Locale.ENGLISH
        ));

        presentation.beginRequest();
        assertTrue(presentation.acceptNode(new InteractionDialogueNodeResponse(
                sessionId,
                51L,
                InteractionDialogueNodeResponse.State.PAUSED,
                Optional.empty()
        ), 5L, Locale.ENGLISH));
        assertFalse(presentation.nodeVisible(),
                "authoritative pause must clear stale node controls even while a fresh menu request is pending");
        assertFalse(presentation.acceptNode(
                active(sessionId, 52L, Component.literal("Late line"), DialogueEngine.AdvanceKind.NEXT, List.of()),
                10L,
                Locale.ENGLISH
        ));
        InteractionDialogueOptionsResponse menu = new InteractionDialogueOptionsResponse(
                53L,
                Optional.of(Component.literal("Resume?")),
                List.of(),
                false
        );
        assertTrue(presentation.acceptOptions(menu));
        assertTrue(presentation.sessionId().isEmpty(),
                "a fresh menu must not expose the old active session ID to the close packet");
        assertEquals(53L, presentation.offerToken().orElseThrow(),
                "closing the visible menu must use its newly issued offer token");
        presentation.markSelectionRequested(DialogueEngine.DialogueSelection.RESUME);
        assertTrue(presentation.acceptNode(
                active(sessionId, 54L, Component.literal("Old line"), DialogueEngine.AdvanceKind.NEXT, List.of()),
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
                DialogueEngine.AdvanceKind.NONE,
                List.of(new InteractionDialogueNodeResponse.Choice("old", Component.literal("Old")))
        ), 0L, Locale.ENGLISH);
        presentation.tick(40L);
        assertEquals("O", presentation.visibleLine().getString());

        assertTrue(presentation.acceptNode(active(
                sessionId,
                55L,
                line,
                DialogueEngine.AdvanceKind.NONE,
                List.of(new InteractionDialogueNodeResponse.Choice("new", Component.literal("New")))
        ), 300L, Locale.ENGLISH));
        assertEquals("O", presentation.visibleLine().getString());

        presentation.tick(340L);
        assertEquals("On", presentation.visibleLine().getString());
        tickSteps(presentation, 340L, 5);
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
                DialogueEngine.AdvanceKind.NEXT,
                List.of(new InteractionDialogueNodeResponse.Choice("x", Component.literal("X")))
        ), 0L, Locale.ENGLISH);
        presentation.tick(40L);

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
                true
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
            DialogueEngine.AdvanceKind advanceKind,
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
                        advanceKind,
                        choices
                ))
        );
    }

    private static void tickSteps(ClientHandlerImpl.DialoguePresentation presentation, long startMillis, int count) {
        for (int i = 1; i <= count; i++) {
            presentation.tick(startMillis + i * 40L);
        }
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
