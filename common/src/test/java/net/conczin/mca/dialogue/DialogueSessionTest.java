package net.conczin.mca.dialogue;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueSessionTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VILLAGER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID OTHER_VILLAGER = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Test
    void pauseKeepsOneDeadlineAndResumePreservesProgressWithFreshToken() {
        DialogueEvent event = event();
        DialogueSession active = DialogueSession.start(PLAYER, VILLAGER, SESSION, 7L, 101L, event)
                .withProgress("reply", 1, List.of("comfort"), 102L)
                .withAcceptedChoice("ask_why")
                .withSelectedOutcome("ask_why", 1);

        DialogueSession paused = active.pause(3_400L);
        DialogueSession pausedAgain = paused.pause(9_999L);

        assertEquals(DialogueSession.Status.PAUSED, paused.status());
        assertEquals(3_400L, paused.pauseDeadline());
        assertSame(paused, pausedAgain, "browsing/duplicate pause must not extend the original deadline");
        assertEquals(true, paused.canResume(3_399L, 7L, VILLAGER));
        assertEquals(false, paused.canResume(3_400L, 7L, VILLAGER));
        assertEquals(false, paused.canResume(3_399L, 7L, OTHER_VILLAGER));
        assertEquals(false, paused.acceptsOffer(102L, 7L), "pre-pause active token must be stale");

        DialogueSession resumed = paused.resume(777L);
        assertEquals(DialogueSession.Status.ACTIVE, resumed.status());
        assertEquals(active.id(), resumed.id());
        assertEquals("reply", resumed.nodeId());
        assertEquals(1, resumed.lineIndex());
        assertEquals(List.of("comfort"), resumed.offeredChoices());
        assertEquals(Set.of("ask_why"), resumed.acceptedChoices());
        assertEquals(Map.of("ask_why", 1), resumed.selectedOutcomes());
        assertEquals(777L, resumed.offerToken());
        assertNotEquals(active.offerToken(), resumed.offerToken());
        assertEquals(false, resumed.acceptsOffer(102L, 7L));
        assertEquals(true, resumed.acceptsOffer(777L, 7L));
        assertEquals(false, resumed.acceptsOffer(777L, 8L), "reload generation must invalidate the active token");

        DialogueSession progressed = resumed.withProgress("reply", 1, List.of("comfort"), 778L);
        assertFalse(progressed.acceptsOffer(777L, 7L), "accepted progression must make the old token single-use");
        assertTrue(progressed.acceptsOffer(778L, 7L));
    }

    @Test
    void resumeRejectsReusingThePrePauseOfferToken() {
        DialogueSession paused = DialogueSession.start(PLAYER, VILLAGER, SESSION, 7L, 101L, event())
                .withProgress("reply", 1, List.of("comfort"), 102L)
                .pause(3_400L);

        assertFalse(paused.acceptsOffer(102L, 7L));
        assertThrows(IllegalArgumentException.class, () -> paused.resume(102L),
                "a token consumed before pause must never become active again on resume");
        assertThrows(IllegalArgumentException.class, () -> paused.resume(101L),
                "the preceding token must also remain stale after resume");
        assertThrows(IllegalArgumentException.class, () -> paused.resume(103L, 103L),
                "the menu token consumed by RESUME must not become the active token");
    }

    @Test
    void resumingAtLastAllowedTickKeepsTheExactPassageChoicesAndUncommittedEffects() {
        List<DialogueAction> effects = new ArrayList<>(List.of(new DialogueAction.Hearts(4)));
        DialogueSession active = DialogueSession.start(PLAYER, VILLAGER, SESSION, 7L, 101L, event())
                .withProgress("reply", 1, List.of("comfort"), 102L)
                .withAcceptedChoice("ask_why")
                .withSelectedOutcome("ask_why", 1)
                .withPendingEffects(effects);
        DialogueSession paused = active.pause(1_000L + DialogueEngine.PAUSE_TICKS);
        effects.clear();

        assertFalse(active.canResume(3_399L, 7L, VILLAGER));
        assertTrue(paused.canResume(3_399L, 7L, VILLAGER));
        assertFalse(paused.canResume(3_400L, 7L, VILLAGER));
        assertFalse(paused.canResume(3_399L, 8L, VILLAGER));
        assertFalse(paused.canResume(3_399L, 7L, OTHER_VILLAGER));

        DialogueSession resumed = paused.resume(103L, 201L);
        assertEquals(DialogueSession.Status.ACTIVE, resumed.status());
        assertEquals(0L, resumed.pauseDeadline());
        assertEquals(SESSION, resumed.id());
        assertEquals("reply", resumed.nodeId());
        assertEquals(1, resumed.lineIndex());
        assertEquals(List.of("comfort"), resumed.offeredChoices());
        assertEquals(Set.of("ask_why"), resumed.acceptedChoices());
        assertEquals(Map.of("ask_why", 1), resumed.selectedOutcomes());
        assertEquals(List.of(new DialogueAction.Hearts(4)), resumed.pendingEffects());
        assertEquals(201L, resumed.previousOfferToken());
        assertFalse(resumed.acceptsOffer(102L, 7L));
        assertTrue(resumed.acceptsOffer(103L, 7L));
        assertEquals(List.of(new DialogueAction.Hearts(4)), paused.pendingEffects(),
                "resuming must not consume the queued effects in the retained snapshot");
    }

    @Test
    void progressingRotatesOnlyTheImmediatelyPreviousScopedCloseToken() {
        DialogueSession selected = DialogueSession.start(PLAYER, VILLAGER, SESSION, 7L, 102L, 101L, event());
        assertEquals(101L, selected.previousOfferToken());

        DialogueSession sameOffer = selected.withProgress("start", 0, List.of(), 102L);
        assertEquals(101L, sameOffer.previousOfferToken(),
                "recomputing the same offer must not forget the pending menu close token");

        DialogueSession progressed = sameOffer.withProgress("start", 1, List.of(), 103L);
        assertEquals(102L, progressed.previousOfferToken());
        assertFalse(progressed.acceptsOffer(102L, 7L));
        assertTrue(progressed.acceptsOffer(103L, 7L));

        DialogueSession progressedAgain = progressed.withProgress("reply", 0, List.of(), 104L);
        assertEquals(103L, progressedAgain.previousOfferToken(),
                "a scoped close must not authorize arbitrarily old selections");
        assertFalse(progressedAgain.acceptsOffer(102L, 7L));
        assertFalse(progressedAgain.acceptsOffer(103L, 7L));
    }

    @Test
    void pauseAndResumeEnforceTheirStateAndDeadlineContracts() {
        DialogueSession active = DialogueSession.start(PLAYER, VILLAGER, SESSION, 7L, 101L, event());

        assertThrows(IllegalArgumentException.class, () -> active.pause(-1L));
        assertThrows(IllegalStateException.class, () -> active.resume(102L));

        DialogueSession paused = active.pause(3_400L);
        assertSame(paused, paused.pause(9_999L));
        assertFalse(paused.acceptsOffer(101L, 7L));
        assertEquals(3_400L, paused.pauseDeadline());
    }

    @Test
    void sessionCarriesImmutableEventDefinitionAndNeverADeadlineWhileActive() {
        DialogueEvent event = event();
        DialogueSession session = DialogueSession.start(PLAYER, VILLAGER, SESSION, 11L, 55L, event);

        assertSame(event, session.event());
        assertEquals(event.id(), session.eventId());
        assertEquals(event.start(), session.nodeId());
        assertEquals(0, session.lineIndex());
        assertEquals(0L, session.pauseDeadline());
        assertEquals(Set.of(), session.acceptedChoices());
        assertEquals(Map.of(), session.selectedOutcomes());
        assertEquals(List.of(), session.pendingEffects());
    }

    @Test
    void tickRetentionDropsOfflineExpiredAndReloadedSessions() {
        DialogueSession active = DialogueSession.start(PLAYER, VILLAGER, SESSION, 7L, 55L, event());
        DialogueSession paused = active.pause(3_400L);

        assertFalse(DialogueEngine.shouldDiscardSession(paused, 7L, 3_399L, true));
        assertTrue(DialogueEngine.shouldDiscardSession(paused, 7L, 3_400L, true));
        assertTrue(DialogueEngine.shouldDiscardSession(paused, 7L, 3_399L, false));
        assertTrue(DialogueEngine.shouldDiscardSession(active, 8L, 0L, true));
        assertFalse(DialogueEngine.shouldDiscardSession(active, 7L, 0L, true));
    }

    @Test
    void tickRetentionDropsOfflineAndReloadedMenuOffers() {
        DialogueEngine.DialogueOptions offer = new DialogueEngine.DialogueOptions(
                VILLAGER,
                7L,
                java.util.Optional.empty(),
                List.of(),
                false,
                java.util.Optional.of(Component.literal("continue")),
                99L,
                List.of(),
                java.util.Optional.of(SESSION)
        );

        assertFalse(DialogueEngine.shouldDiscardOffer(offer, 7L, true));
        assertTrue(DialogueEngine.shouldDiscardOffer(offer, 8L, true));
        assertTrue(DialogueEngine.shouldDiscardOffer(offer, 7L, false));
    }

    @Test
    void finalChoiceNodeWithNoOfferedChoicesIsA_safeExit() {
        DialogueSession blocked = DialogueSession.start(
                PLAYER, VILLAGER, SESSION, 7L, 55L, blockedChoiceEvent());

        assertTrue(DialogueEngine.hasNoValidContinuation(blocked));
        assertEquals(DialogueEngine.AdvanceKind.BACK_TO_TOPICS, DialogueEngine.advanceKind(blocked),
                "a zero-choice final line must still expose the safe final acknowledgement");
        assertFalse(DialogueEngine.hasNoValidContinuation(
                blocked.withProgress(blocked.nodeId(), 0, List.of("adult"), 56L)));
    }

    @Test
    void advanceKindDistinguishesAnotherPassageFromFinalAcknowledgement() {
        DialogueSession start = DialogueSession.start(PLAYER, VILLAGER, SESSION, 7L, 55L, event());
        assertEquals(DialogueEngine.AdvanceKind.NEXT, DialogueEngine.advanceKind(start));

        DialogueSession finalLineBeforeChoice = start.withProgress("reply", 1, List.of("comfort"), 56L);
        assertEquals(DialogueEngine.AdvanceKind.NONE, DialogueEngine.advanceKind(finalLineBeforeChoice));

        DialogueSession completing = start.withProgress("done", 0, List.of(), 57L);
        assertEquals(DialogueEngine.AdvanceKind.BACK_TO_TOPICS, DialogueEngine.advanceKind(completing));
    }

    private static DialogueEvent event() {
        return DialogueEvent.decode(ResourceLocation.parse("test:session"), JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{
                    "mode":"ask",
                    "prompt":"dialogue.session.prompt",
                    "resume_prompt":"dialogue.session.resume"
                  },
                  "repeat":{"type":"always"},
                  "start":"start",
                  "nodes":{
                    "start":{"lines":["dialogue.one","dialogue.two"],"next":"reply"},
                    "reply":{
                      "lines":["dialogue.reply.one","dialogue.reply.two"],
                      "choices":[
                        {"id":"comfort","text":"dialogue.comfort","next":"done"}
                      ]
                    },
                    "done":{"line":"dialogue.done","complete":true}
                  }
                }
                """).getAsJsonObject());
    }

    private static DialogueEvent blockedChoiceEvent() {
        return DialogueEvent.decode(ResourceLocation.parse("test:blocked_choice"), JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{
                    "mode":"ask",
                    "prompt":"dialogue.blocked.prompt",
                    "resume_prompt":"dialogue.blocked.resume"
                  },
                  "repeat":{"type":"always"},
                  "start":"start",
                  "nodes":{
                    "start":{
                      "line":"dialogue.blocked.line",
                      "choices":[
                        {
                          "id":"adult",
                          "text":"dialogue.adult",
                          "requirements":[{"type":"mca:age_group","value":"adult"}],
                          "next":"done"
                        }
                      ]
                    },
                    "done":{"line":"dialogue.done","complete":true}
                  }
                }
                """).getAsJsonObject());
    }
}
