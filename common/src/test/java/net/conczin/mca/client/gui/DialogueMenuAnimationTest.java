package net.conczin.mca.client.gui;

import net.conczin.mca.dialogue.DialogueEngine;
import net.conczin.mca.network.ClientHandlerImpl;
import net.conczin.mca.network.s2c.InteractionDialogueNodeResponse;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DialogueMenuAnimationTest {
    @Test
    void movingViewportCannotSnapNativeScrollerDuringDrag() {
        DialogueMenuAnimation animation = new DialogueMenuAnimation();
        assertEquals(90, animation.update(90, 0, false));
        animation.update(180, 10, false);
        int displayed = animation.update(180, 50, false);
        assertTrue(displayed > 90 && displayed < 180);
        animation.hold(55);
        DialogueMenuScrollWidget scroller = new DialogueMenuScrollWidget();
        int viewportY = 300 - displayed;
        scroller.layout(50, viewportY, 100, displayed, 500);
        scroller.mouseScrolled(55, viewportY + 5, 0, -3);
        int offset = scroller.offset();
        scroller.mouseClicked(155, viewportY + 5, 0);
        scroller.setFocused(true);
        int heldHeight = animation.update(40, 60, true);
        assertEquals(displayed, heldHeight);
        scroller.layout(50, 300 - heldHeight, 100, heldHeight, 500);
        scroller.mouseDragged(155, viewportY + 5, 0, 0, 0);
        assertEquals(offset, scroller.offset(), "stationary pointer must not snap to the new viewport top");
        assertEquals(displayed, animation.update(40, 1000, true));
        assertEquals(displayed, animation.update(40, 1001, false));
        assertEquals(40, animation.update(40, 1201, false));
    }

    @Test
    void wheelGestureKeepsRowsStillThenResumesHeightTween() {
        DialogueMenuAnimation animation = new DialogueMenuAnimation();
        animation.update(100, 0, false);
        animation.hold(20);
        assertEquals(100, animation.update(150, 50, false));
        assertEquals(100, animation.update(150, 199, false));
        assertEquals(100, animation.update(150, 201, false));
        assertEquals(150, animation.update(150, 400, false));
    }

    @Test
    void typingOnlyNodeHasOnePlaceholderUntilComplete() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        assertTrue(presentation.acceptNode(node(UUID.randomUUID(), 1L, "Still speaking",
                DialogueEngine.AdvanceKind.BACK_TO_TOPICS, List.of()), 0L, Locale.ENGLISH));

        InteractScreen.ReplyIndicators typing = InteractScreen.replyIndicators(false, true, false, false);
        List<InteractScreen.DialogueRow> during = InteractScreen.replyRows(presentation, typing);
        assertEquals(1, during.size());
        assertTrue(during.getFirst().placeholder());
        assertNull(during.getFirst().click(), "the grey row must never create a hit target");
        assertFalse(typing.progress(), "character reveal alone is not a server search");

        assertTrue(presentation.revealFully());
        List<InteractScreen.DialogueRow> after = InteractScreen.replyRows(presentation,
                InteractScreen.replyIndicators(false, false, false, false));
        assertEquals(1, after.size(), "Finish replaces the placeholder in one update");
        assertFalse(after.getFirst().placeholder());
        assertNotNull(after.getFirst().click());
    }

    @Test
    void pendingAsyncShowsBothIndicatorsWithoutAnySelectableRows() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        InteractScreen.ReplyIndicators pending = InteractScreen.replyIndicators(false, false, true, false);

        assertTrue(pending.progress());
        List<InteractScreen.DialogueRow> rows = InteractScreen.replyRows(presentation, pending);
        assertEquals(1, rows.size());
        assertTrue(rows.getFirst().placeholder());
        assertNull(rows.getFirst().click());
        assertFalse(InteractScreen.replyIndicators(false, false, false, false).progress(),
                "ordinary requests may not simulate server-side progress");
    }

    @Test
    void requestAwaitingResponseHidesPreviousChoicesWithoutShowingAsyncProgress() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        assertTrue(presentation.acceptNode(node(UUID.randomUUID(), 1L, "Ready",
                DialogueEngine.AdvanceKind.NONE,
                List.of(new InteractionDialogueNodeResponse.Choice("ok", Component.literal("OK")))),
                0L, Locale.ENGLISH));
        presentation.revealFully();
        InteractScreen.ReplyIndicators waiting = InteractScreen.replyIndicators(true, false, false, false);
        List<InteractScreen.DialogueRow> rows = InteractScreen.replyRows(presentation, waiting);
        assertFalse(waiting.progress());
        assertEquals(1, rows.size());
        assertTrue(rows.getFirst().placeholder());
        assertNull(rows.getFirst().click(), "the old OK button cannot remain a hit target");
    }

    @Test
    void readyAfterTypingShowsAllChoicesTogether() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        assertTrue(presentation.acceptNode(node(UUID.randomUUID(), 1L, "Choose",
                DialogueEngine.AdvanceKind.NONE, List.of(
                        new InteractionDialogueNodeResponse.Choice("yes", Component.literal("Yes")),
                        new InteractionDialogueNodeResponse.Choice("no", Component.literal("No")))),
                0L, Locale.ENGLISH));

        assertEquals(1, InteractScreen.replyRows(presentation,
                InteractScreen.replyIndicators(false, true, false, false)).size());
        presentation.revealFully();
        List<InteractScreen.DialogueRow> ready = InteractScreen.replyRows(presentation,
                InteractScreen.replyIndicators(false, false, false, false));
        assertEquals(List.of("Yes", "No"), ready.stream().map(row -> row.text().getString()).toList());
        assertTrue(ready.stream().allMatch(row -> row.click() != null && !row.placeholder()));
    }

    @Test
    void historyReviewHasNoActivePlaceholderOrHitTargets() {
        ClientHandlerImpl.DialoguePresentation presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID session = UUID.randomUUID();
        assertTrue(presentation.acceptNode(node(session, 1L, "Earlier",
                DialogueEngine.AdvanceKind.NONE, List.of(
                        new InteractionDialogueNodeResponse.Choice("one", Component.literal("First reply")),
                        new InteractionDialogueNodeResponse.Choice("two", Component.literal("Second reply")))),
                0L, Locale.ENGLISH));
        presentation.markChoiceRequested(1L, "two");
        assertTrue(presentation.acceptNode(node(session, 2L, "Current",
                DialogueEngine.AdvanceKind.BACK_TO_TOPICS,
                List.of(new InteractionDialogueNodeResponse.Choice("wrong", Component.literal("Current reply")))),
                40L, Locale.ENGLISH));
        assertTrue(presentation.reviewPrevious());

        InteractScreen.ReplyIndicators history = InteractScreen.replyIndicators(false, true, false, true);
        List<InteractScreen.DialogueRow> rows = InteractScreen.replyRows(presentation, history);
        assertFalse(history.progress());
        assertEquals(List.of("First reply", "✓ Second reply"), rows.stream()
                .map(row -> row.text().getString()).toList());
        assertTrue(rows.get(1).selected());
        assertFalse(rows.get(0).selected());
        assertTrue(rows.stream().noneMatch(InteractScreen.DialogueRow::placeholder));
        assertTrue(rows.stream().allMatch(row -> row.click() == null), "history replies are read-only");
    }

    @Test
    void loadingSquaresHopInSequenceAndStopWithReducedMotion() {
        assertEquals(2, InteractScreen.loadingSquareRise(0L, 0, true));
        assertEquals(0, InteractScreen.loadingSquareRise(0L, 1, true));
        assertEquals(2, InteractScreen.loadingSquareRise(150L, 1, true));
        for (int index = 0; index < 3; index++) {
            assertEquals(0, InteractScreen.loadingSquareRise(150L, index, false));
        }
    }

    @Test
    void loadingSquaresMoveContinuouslyBetweenFrames() {
        assertEquals(1.0, InteractScreen.loadingSquareRise(75L, 0, true), 0.001,
                "the leading square should ease down between its peak and rest");
        assertEquals(1.0, InteractScreen.loadingSquareRise(75L, 1, true), 0.001,
                "the following square should ease up before its peak");
        assertTrue(InteractScreen.loadingSquareRise(25L, 0, true)
                > InteractScreen.loadingSquareRise(50L, 0, true),
                "successive rendered frames must have intermediate heights");
    }

    private static InteractionDialogueNodeResponse node(UUID session, long token, String line,
                                                        DialogueEngine.AdvanceKind advance,
                                                        List<InteractionDialogueNodeResponse.Choice> choices) {
        return new InteractionDialogueNodeResponse(session, token, InteractionDialogueNodeResponse.State.ACTIVE,
                Optional.of(new InteractionDialogueNodeResponse.Node(ResourceLocation.parse("mca:test/line"),
                        Component.literal(line), false, advance, choices)));
    }
}
