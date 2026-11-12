package net.conczin.mca.network;

import net.conczin.mca.dialogue.DialogueEngine;
import net.conczin.mca.client.gui.DialogueRichText;
import net.conczin.mca.network.s2c.InteractionDialogueNodeResponse;
import net.conczin.mca.network.s2c.InteractionDialogueOptionsResponse;
import net.conczin.mca.network.s2c.InteractionDialoguePendingResponse;
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
    void pendingToReadyMatchesTokenAndRevealsResolvedLine() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID session = UUID.randomUUID();
        presentation.beginRequest();
        assertTrue(presentation.acceptOptions(new InteractionDialogueOptionsResponse(
                10L, Optional.empty(), List.of(), true)));
        presentation.markSelectionRequested(DialogueEngine.DialogueSelection.AMBIENT);

        assertTrue(presentation.acceptPending(new InteractionDialoguePendingResponse(
                session, 11L, Component.literal("Searching for ruins…"))));
        assertTrue(presentation.pendingAsync());
        assertEquals("Searching for ruins…", presentation.pendingLine().getString());
        assertTrue(presentation.awaitingReply());
        assertFalse(presentation.canAdvance());
        assertTrue(presentation.visibleChoices().isEmpty());

        assertTrue(presentation.acceptNode(active(session, 11L, Component.literal("At 9,70,12"),
                DialogueEngine.AdvanceKind.BACK_TO_TOPICS, List.of()), 100L, Locale.ENGLISH));
        assertFalse(presentation.pendingAsync());
        assertTrue(presentation.revealingLine());
        assertEquals("", presentation.visibleLine().getString());
        assertFalse(presentation.canAdvance());
        tickSteps(presentation, 100L, 10);
        assertFalse(presentation.revealingLine());
        assertTrue(presentation.canAdvance());
        assertFalse(presentation.acceptPending(new InteractionDialoguePendingResponse(
                session, 11L, Component.literal("Stale lookup"))));
    }

    @Test
    void stalePendingCannotReplaceNewerNodeAndLeavingClearsState() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID session = UUID.randomUUID();
        assertTrue(presentation.acceptNode(active(session, 30L, Component.literal("Current"),
                DialogueEngine.AdvanceKind.NEXT, List.of()), 0L, Locale.ENGLISH));
        assertFalse(presentation.acceptPending(new InteractionDialoguePendingResponse(
                session, 29L, Component.literal("Late"))));
        assertFalse(presentation.pendingAsync());

        assertTrue(presentation.acceptPending(new InteractionDialoguePendingResponse(
                session, 31L, Component.literal("Searching"))));
        assertFalse(presentation.acceptPending(new InteractionDialoguePendingResponse(
                session, 31L, Component.literal("Duplicate"))));
        assertTrue(presentation.pendingAsync());
        assertFalse(presentation.acceptSelectionRejection(31L),
                "an async resolver still running must not be replaced by a duplicate reply rejection");
        presentation.pause();
        assertFalse(presentation.pendingAsync());
        presentation.beginRequest();
        assertEquals("", presentation.pendingLine().getString());
        presentation.clear();
        assertFalse(presentation.awaitingReply());
    }

    @Test
    void ordinaryTypingDoesNotClaimAsyncProgress() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID session = UUID.randomUUID();
        assertTrue(presentation.acceptNode(active(session, 1L, Component.literal("Hello"),
                DialogueEngine.AdvanceKind.NEXT, List.of()), 0L, Locale.ENGLISH));
        assertTrue(presentation.revealingLine());
        assertFalse(presentation.pendingAsync());
        tickSteps(presentation, 0L, 5);
        assertFalse(presentation.revealingLine());
        assertFalse(presentation.pendingAsync());
    }

    @Test
    void paperHasNonStaleSpeechDuringUnpreviewedTopicsAndRequests() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        assertEquals("...", presentation.paperFullLine().getString());
        assertEquals("...", presentation.paperVisibleLine().getString());

        presentation.beginRequest();
        assertTrue(presentation.acceptOptions(new InteractionDialogueOptionsResponse(
                1L, Optional.empty(), List.of(), true)));
        assertEquals("...", presentation.paperFullLine().getString(),
                "a menu with no preview must still have a speech bubble");
        assertEquals("...", presentation.paperVisibleLine().getString());

        presentation.markSelectionRequested(DialogueEngine.DialogueSelection.AMBIENT);
        assertTrue(presentation.acceptNode(active(UUID.randomUUID(), 2L,
                Component.literal("Hello"), DialogueEngine.AdvanceKind.NEXT, List.of()),
                0L, Locale.ENGLISH));
        assertEquals("Hello", presentation.paperFullLine().getString());
        assertEquals("", presentation.paperVisibleLine().getString(),
                "the actual node must still use character reveal");

        presentation.beginRequest();
        assertEquals("...", presentation.paperFullLine().getString(),
                "returning to topics must not show stale node speech");
        assertEquals("...", presentation.paperVisibleLine().getString());
    }

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
    void reviewedLineRetainsOriginalChoicesAndAcknowledgedAnswer() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID session = UUID.randomUUID();
        List<InteractionDialogueNodeResponse.Choice> original = List.of(
                new InteractionDialogueNodeResponse.Choice("stay", Component.literal("Stay here")),
                new InteractionDialogueNodeResponse.Choice("leave", Component.literal("Leave now")),
                new InteractionDialogueNodeResponse.Choice("ask", Component.literal("Ask another question")));
        assertTrue(presentation.acceptNode(active(session, 10L, Component.literal("What will you do?"),
                DialogueEngine.AdvanceKind.NONE, original), 0L, Locale.ENGLISH));
        presentation.markChoiceRequested(10L, "leave");
        assertTrue(presentation.acceptNode(active(session, 11L, Component.literal("Good luck"),
                DialogueEngine.AdvanceKind.BACK_TO_TOPICS, List.of()), 100L, Locale.ENGLISH));
        assertTrue(presentation.reviewPrevious());

        var reviewed = presentation.reviewedEntry().orElseThrow();
        assertEquals("What will you do?", reviewed.line().getString());
        assertEquals(List.of("stay", "leave", "ask"), reviewed.choices().stream()
                .map(InteractionDialogueNodeResponse.Choice::id).toList());
        assertEquals(List.of("Stay here", "Leave now", "Ask another question"), reviewed.choices().stream()
                .map(choice -> choice.text().getString()).toList());
        assertEquals(Optional.of("leave"), reviewed.selectedChoiceId());
        assertTrue(presentation.visibleChoices().isEmpty());
        assertFalse(presentation.canAdvance());
        assertTrue(presentation.reviewLatest());
        assertTrue(presentation.reviewedEntry().isEmpty());
    }

    @Test
    void pendingAcknowledgesChoiceButRejectionAndPauseDoNot() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID session = UUID.randomUUID();
        var choice = new InteractionDialogueNodeResponse.Choice("yes", Component.literal("Yes"));
        assertTrue(presentation.acceptNode(active(session, 20L, Component.literal("Question"),
                DialogueEngine.AdvanceKind.NONE, List.of(choice)), 0L, Locale.ENGLISH));

        presentation.markChoiceRequested(19L, "yes");
        presentation.markChoiceRequested(20L, "unknown");
        assertTrue(presentation.acceptNode(active(session, 20L, Component.literal("Question"),
                DialogueEngine.AdvanceKind.NONE, List.of(choice)), 40L, Locale.ENGLISH));
        assertTrue(presentation.acceptPending(new InteractionDialoguePendingResponse(
                session, 21L, Component.literal("Checking"))));
        assertTrue(presentation.acceptNode(active(session, 21L, Component.literal("Answer"),
                DialogueEngine.AdvanceKind.NEXT, List.of()), 80L, Locale.ENGLISH));
        assertTrue(presentation.reviewPrevious());
        assertTrue(presentation.reviewedEntry().orElseThrow().selectedChoiceId().isEmpty(),
                "a same-offer refresh or invalid request must not gain a checkmark");
        presentation.reviewLatest();

        presentation.markChoiceRequested(21L, "yes");
        assertTrue(presentation.acceptNode(active(session, 22L, Component.literal("Another question"),
                DialogueEngine.AdvanceKind.NONE, List.of(choice)), 120L, Locale.ENGLISH));
        presentation.markChoiceRequested(22L, "yes");
        assertTrue(presentation.acceptPending(new InteractionDialoguePendingResponse(
                session, 23L, Component.literal("Searching"))));
        assertTrue(presentation.acceptNode(active(session, 23L, Component.literal("Result"),
                DialogueEngine.AdvanceKind.NONE, List.of()), 160L, Locale.ENGLISH));
        assertTrue(presentation.reviewPrevious());
        assertEquals(Optional.of("yes"), presentation.reviewedEntry().orElseThrow().selectedChoiceId());
        presentation.reviewLatest();

        assertTrue(presentation.acceptNode(active(session, 24L, Component.literal("Last question"),
                DialogueEngine.AdvanceKind.NONE, List.of(choice)), 180L, Locale.ENGLISH));
        presentation.markChoiceRequested(24L, "yes");
        assertTrue(presentation.acceptNode(new InteractionDialogueNodeResponse(
                session, 24L, InteractionDialogueNodeResponse.State.PAUSED, Optional.empty()),
                200L, Locale.ENGLISH));
        presentation.beginRequest();
        assertTrue(presentation.acceptOptions(new InteractionDialogueOptionsResponse(
                25L, Optional.of(Component.literal("Resume")), List.of(), false)));
        presentation.markSelectionRequested(DialogueEngine.DialogueSelection.RESUME);
        assertTrue(presentation.acceptNode(active(session, 26L, Component.literal("Last question"),
                DialogueEngine.AdvanceKind.NONE, List.of(choice)), 220L, Locale.ENGLISH));
        assertTrue(presentation.acceptNode(active(session, 27L, Component.literal("Continued"),
                DialogueEngine.AdvanceKind.NEXT, List.of()), 260L, Locale.ENGLISH));
        assertTrue(presentation.reviewPrevious());
        assertTrue(presentation.reviewedEntry().orElseThrow().selectedChoiceId().isEmpty());
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
    void resumeRefreshesHistoricalChoicesWithoutRestartingSpeech() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID session = UUID.randomUUID();
        var a = new InteractionDialogueNodeResponse.Choice("a", Component.literal("Old answer"));
        var b = new InteractionDialogueNodeResponse.Choice("b", Component.literal("Still available"));
        var c = new InteractionDialogueNodeResponse.Choice("c", Component.literal("New answer"));
        assertTrue(presentation.acceptNode(active(session, 10L, Component.literal("Question"),
                DialogueEngine.AdvanceKind.NONE, List.of(a, b)), 0L, Locale.ENGLISH));
        presentation.tick(40L);
        assertEquals("Q", presentation.visibleLine().getString());

        presentation.pause();
        presentation.beginRequest();
        assertTrue(presentation.acceptOptions(new InteractionDialogueOptionsResponse(11L,
                Optional.of(Component.literal("Resume")), List.of(), false)));
        presentation.markSelectionRequested(DialogueEngine.DialogueSelection.RESUME);
        assertTrue(presentation.acceptNode(active(session, 12L, Component.literal("Question"),
                DialogueEngine.AdvanceKind.NONE, List.of(b, c)), 100L, Locale.ENGLISH));
        assertEquals("Q", presentation.visibleLine().getString(), "resume preserves reveal progress");

        presentation.markChoiceRequested(12L, "c");
        assertTrue(presentation.acceptNode(active(session, 13L, Component.literal("Reply"),
                DialogueEngine.AdvanceKind.BACK_TO_TOPICS, List.of()), 150L, Locale.ENGLISH));
        assertTrue(presentation.reviewPrevious());
        var previous = presentation.reviewedEntry().orElseThrow();
        assertEquals("Question", previous.line().getString());
        assertEquals(List.of("b", "c"), previous.choices().stream()
                .map(InteractionDialogueNodeResponse.Choice::id).toList());
        assertEquals(Optional.of("c"), previous.selectedChoiceId());
        assertFalse(presentation.reviewPrevious(), "resume does not add another history entry");
    }

    @Test
    void latestLineExitsReviewWithoutAdvancingConversation() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID session = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            assertTrue(presentation.acceptNode(active(session, i + 1, Component.literal("Line " + i),
                    DialogueEngine.AdvanceKind.BACK_TO_TOPICS, List.of()), i * 100L, Locale.ENGLISH));
        }
        assertTrue(presentation.reviewPrevious());
        assertTrue(presentation.reviewPrevious());
        assertFalse(presentation.canAdvance());
        assertTrue(presentation.reviewLatest());
        assertFalse(presentation.reviewingHistory());
        assertEquals("Line 2", presentation.fullLine().getString());
        assertFalse(presentation.reviewLatest(), "latest is a read-only review action, not an advance");
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
    void rejectedActiveReplyRefreshesOnlyItsCurrentNodeOffer() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID session = UUID.randomUUID();
        assertTrue(presentation.acceptNode(active(session, 30L, Component.literal("Question"),
                DialogueEngine.AdvanceKind.NEXT, List.of()), 0L, Locale.ENGLISH));

        assertFalse(presentation.acceptSelectionRejection(29L), "a late rejection cannot refresh another node");
        assertTrue(presentation.acceptSelectionRejection(30L), "the current node's rejected reply must recover");

        presentation.pause();
        assertFalse(presentation.acceptSelectionRejection(30L), "closing Talk must ignore an in-flight rejection");
        presentation.beginRequest();
        assertFalse(presentation.acceptSelectionRejection(30L), "refresh must invalidate the rejected offer");
        assertTrue(presentation.acceptOptions(new InteractionDialogueOptionsResponse(
                31L, Optional.empty(), List.of(), false)));
        assertFalse(presentation.acceptSelectionRejection(30L), "a late rejection cannot discard newer topics");
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
    void styledRichTextSurvivesRevealAndSpeechHistory() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID session = UUID.randomUUID();
        presentation.acceptNode(active(session, 21L, Component.literal("<gold><bold>Hello!</bold></gold>"),
                DialogueEngine.AdvanceKind.NEXT, List.of()), 0L, Locale.ENGLISH, DialogueRichText::parse);
        tickSteps(presentation, 0L, 6);
        assertEquals("Hello!", presentation.visibleLine().getString());
        assertTrue(presentation.visibleLine().getSiblings().getFirst().getStyle().isBold());
        presentation.acceptNode(active(session, 22L, Component.literal("Next"),
                DialogueEngine.AdvanceKind.NEXT, List.of()), 320L, Locale.ENGLISH);
        assertTrue(presentation.reviewPrevious());
        assertEquals("Hello!", presentation.visibleLine().getString());
        assertTrue(presentation.visibleLine().getSiblings().getFirst().getStyle().isBold());
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
        assertTrue(presentation.acceptNode(active(sessionId, 56L, Component.literal("Next line"),
                DialogueEngine.AdvanceKind.NEXT, List.of()), 600L, Locale.ENGLISH));
        assertTrue(presentation.reviewPrevious());
        assertEquals(List.of("new"), presentation.reviewedEntry().orElseThrow().choices().stream()
                .map(InteractionDialogueNodeResponse.Choice::id).toList(),
                "a refreshed offer must replace obsolete choices on the same historical line");
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
                        Component.literal("Ask"),
                        "other"
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
