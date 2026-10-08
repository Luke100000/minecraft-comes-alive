package net.conczin.mca.dialogue;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.network.c2s.InteractionCloseRequest;
import net.conczin.mca.network.c2s.InteractionDialogueAdvanceMessage;
import net.conczin.mca.network.c2s.InteractionDialogueChoiceMessage;
import net.conczin.mca.network.c2s.InteractionDialogueLeaveMessage;
import net.conczin.mca.network.c2s.InteractionDialogueSelectMessage;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.resources.DialogueEvents;
import net.conczin.mca.server.world.data.DialogueEventHistory;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@PrefixGameTestTemplate(false)
public final class DialogueEngineGameTests {
    private DialogueEngineGameTests() {
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void closingTalkMenuInvalidatesItsOfferToken(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = simpleEvent(ResourceLocation.parse("mca:test/menu_close"));
        DialogueEngine engine = engine(event);

        DialogueEngine.DialogueOptions options = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(options.containsEvent(event.id()), "fixture event was not offered");

        helper.assertTrue(!engine.pause(fixture.player(), UUID.randomUUID(), Util.NIL_UUID, options.token()),
                "stale menu close must not authorize closing a newer interaction");
        helper.assertTrue(
                offers(engine).containsKey(fixture.player().getUUID()),
                "a stale close cannot consume an unrelated menu offer"
        );
        helper.assertTrue(engine.pause(fixture.player(), fixture.villager().getUUID(), Util.NIL_UUID, options.token()),
                "matching menu close must authorize normal cleanup");

        helper.assertTrue(
                engine.select(
                        fixture.player(),
                        options.token(),
                        DialogueEngine.DialogueSelection.EVENT,
                        event.id()
                ).isEmpty(),
                "closing Talk must invalidate the outstanding menu offer"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void staleScreenCloseCannotPauseNewSession(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = simpleEvent(ResourceLocation.parse("mca:test/scoped_pause"));
        DialogueEngine engine = engine(event);
        DialogueEngine.DialogueOptions options = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView active = engine.select(
                fixture.player(), options.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();

        helper.assertTrue(!engine.pause(fixture.player(), UUID.randomUUID(), active.sessionId(), active.offerToken()),
                "wrong villager cannot close active interaction");
        helper.assertTrue(!engine.pause(fixture.player(), fixture.villager().getUUID(), UUID.randomUUID(), active.offerToken()),
                "wrong session cannot close active interaction");
        helper.assertTrue(!engine.pause(fixture.player(), fixture.villager().getUUID(), active.sessionId(), active.offerToken() + 1),
                "stale token cannot close active interaction");
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).status() == DialogueSession.Status.ACTIVE,
                "stale or unrelated close packets must not affect the active dialogue");

        helper.assertTrue(engine.pause(fixture.player(), fixture.villager().getUUID(), active.sessionId(), active.offerToken()),
                "matching close should authorize normal cleanup");
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).status() == DialogueSession.Status.PAUSED,
                "a matching close packet must pause the active dialogue");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void closingDuringPendingProgressUsesPreviousToken(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = passageEvent(ResourceLocation.parse("mca:test/close_pending_progress"), false);
        DialogueEngine engine = engine(event);
        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView first = engine.select(fixture.player(), menu.token(),
                DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        helper.assertTrue(engine.pause(fixture.player(), fixture.villager().getUUID(), Util.NIL_UUID, menu.token()),
                "close while selecting a topic must match the just-consumed menu token");

        DialogueEngine.DialogueOptions resumeMenu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView resumed = engine.select(fixture.player(), resumeMenu.token(),
                DialogueEngine.DialogueSelection.RESUME, null).orElseThrow();
        DialogueEngine.DialogueNodeView advanced = engine.advance(fixture.player(), resumed.offerToken()).view().orElseThrow();
        helper.assertTrue(engine.pause(fixture.player(), fixture.villager().getUUID(), resumed.sessionId(), resumed.offerToken()),
                "close during an in-flight Next must accept the immediately preceding node token");
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).status() == DialogueSession.Status.PAUSED,
                "close during progress must not strand an active run");
        helper.assertTrue(engine.advance(fixture.player(), advanced.offerToken()).status() == DialogueEngine.TransitionStatus.REJECTED,
                "a successfully paused run cannot be advanced by its previous active token");
        helper.assertTrue(first.sessionId().equals(resumed.sessionId()), "resume must keep the same session identity");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void automaticRoutingDoesNotInvalidatePendingCloseToken(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = DialogueEvent.decode(ResourceLocation.parse("mca:test/pending_auto_close"),
                JsonParser.parseString("""
                        {
                          "trigger": "talk",
                          "presentation": {"mode": "ask", "prompt": "dialogue.test.prompt", "resume_prompt": "dialogue.test.resume"},
                          "repeat": {"type": "always"},
                          "start": "route_a",
                          "nodes": {
                            "route_a": {"outcomes": [{"weight": 1, "next": "route_b"}]},
                            "route_b": {"outcomes": [{"weight": 1, "next": "first"}]},
                            "first": {"line": "dialogue.test.line", "next": "route_c"},
                            "route_c": {"outcomes": [{"weight": 1, "next": "last"}]},
                            "last": {"line": "dialogue.test.line", "complete": true}
                          }
                        }
                        """).getAsJsonObject());
        DialogueEngine engine = engine(event);
        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView first = engine.select(fixture.player(), menu.token(),
                DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        helper.assertTrue(engine.pause(fixture.player(), fixture.villager().getUUID(), Util.NIL_UUID, menu.token()),
                "automatic entry routing must still recognize a close sent while the menu selection was pending");

        DialogueEngine.DialogueOptions continuation = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView resumed = engine.select(fixture.player(), continuation.token(),
                DialogueEngine.DialogueSelection.RESUME, null).orElseThrow();
        DialogueEngine.DialogueNodeView last = engine.advance(fixture.player(), resumed.offerToken()).view().orElseThrow();
        helper.assertTrue(last.offerToken() != resumed.offerToken(), "visible progression must rotate the offer token");
        helper.assertTrue(engine.pause(fixture.player(), fixture.villager().getUUID(), resumed.sessionId(), resumed.offerToken()),
                "automatic routing after Next must recognize the token of the last visible line");
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).status() == DialogueSession.Status.PAUSED,
                "closing after automatic routing must pause the run without executing its final effects");
        helper.assertTrue(first.sessionId().equals(resumed.sessionId()), "resumed run must keep its session binding");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void oldScreenCloseCannotCloseNewOrdinaryInteraction(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var interactions = fixture.villager().getInteractions();
        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID firstScreen = interactions.interactionId();
        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID secondScreen = interactions.interactionId();
        helper.assertTrue(!firstScreen.equals(secondScreen), "new interactions require distinct screen identities");

        new InteractionCloseRequest(fixture.villager().getUUID(), firstScreen, Util.NIL_UUID, 0L)
                .handleServer(fixture.player());
        helper.assertTrue(interactions.getInteractingPlayer().filter(fixture.player()::equals).isPresent(),
                "an old close packet must not dismiss a new ordinary interaction screen");
        new InteractionCloseRequest(fixture.villager().getUUID(), secondScreen, Util.NIL_UUID, 0L)
                .handleServer(fixture.player());
        helper.assertTrue(interactions.getInteractingPlayer().isEmpty(),
                "matching screen close must release the active interaction");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void oldPlayersCloseCannotDismissNewPlayersInteraction(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ServerPlayer other = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "dialogue-new-owner"));
        other.setPos(fixture.player().position());
        var interactions = fixture.villager().getInteractions();
        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID originalId = interactions.interactionId();
        interactions.interactAt(other, Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID otherId = interactions.interactionId();

        new InteractionCloseRequest(fixture.villager().getUUID(), originalId, Util.NIL_UUID, 0L)
                .handleServer(fixture.player());
        helper.assertTrue(interactions.matchesInteraction(other, otherId),
                "late close from a prior player must not dismiss the current player's screen");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void displacedPlayersClosePausesOwnStoryWithoutClosingNewOwnersScreen(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ServerPlayer other = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "dialogue-second-reader"));
        other.setPos(fixture.player().position());
        var interactions = fixture.villager().getInteractions();
        DialogueEngine engine = MCA.getDialogueEngine().orElseThrow();

        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID originalInteractionId = interactions.interactionId();
        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView active = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.AMBIENT, null).orElseThrow();

        interactions.interactAt(other, Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID otherInteractionId = interactions.interactionId();
        new InteractionCloseRequest(fixture.villager().getUUID(), originalInteractionId,
                UUID.randomUUID(), active.offerToken()).handleServer(fixture.player());
        new InteractionCloseRequest(fixture.villager().getUUID(), originalInteractionId,
                active.sessionId(), active.offerToken() + 1L).handleServer(fixture.player());
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).status() == DialogueSession.Status.ACTIVE,
                "displaced players cannot pause their own story using a forged session or token");
        helper.assertTrue(interactions.matchesInteraction(other, otherInteractionId),
                "forged displaced-player close packets must preserve the new owner's screen");
        new InteractionCloseRequest(fixture.villager().getUUID(), originalInteractionId,
                active.sessionId(), active.offerToken()).handleServer(fixture.player());

        helper.assertTrue(interactions.matchesInteraction(other, otherInteractionId),
                "closing an earlier player's screen must not dismiss another player's newer screen");
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).status() == DialogueSession.Status.PAUSED,
                "closing the earlier player's screen must still pause their own active conversation");

        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        DialogueEngine.DialogueOptions resumedMenu = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(resumedMenu.continuationSessionId().filter(active.sessionId()::equals).isPresent(),
                "reopening Talk must offer the displaced player's unfinished story");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void staleDialogueCloseCannotDismissReopenedConversation(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var interactions = fixture.villager().getInteractions();
        DialogueEngine engine = MCA.getDialogueEngine().orElseThrow();
        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID oldInteractionId = interactions.interactionId();

        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView first = engine.select(fixture.player(), menu.token(),
                DialogueEngine.DialogueSelection.AMBIENT, null).orElseThrow();
        InteractionCloseRequest oldClose = new InteractionCloseRequest(fixture.villager().getUUID(),
                oldInteractionId, first.sessionId(), first.offerToken());
        oldClose.handleServer(fixture.player());
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).status() == DialogueSession.Status.PAUSED,
                "initial close must leave the first conversation resumable");

        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID newInteractionId = interactions.interactionId();
        helper.assertTrue(!newInteractionId.equals(oldInteractionId), "reopening must establish a new interaction ID");
        DialogueEngine.DialogueOptions continuation = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView resumed = engine.select(fixture.player(), continuation.token(),
                DialogueEngine.DialogueSelection.RESUME, null).orElseThrow();
        helper.assertTrue(resumed.sessionId().equals(first.sessionId()),
                "resuming may reuse the dialogue session but not the screen interaction");

        oldClose.handleServer(fixture.player());
        helper.assertTrue(interactions.matchesInteraction(fixture.player(), newInteractionId),
                "replaying the old screen close must not dismiss a newly opened screen");
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).status() == DialogueSession.Status.ACTIVE,
                "the old screen close must not pause the resumed conversation");

        new InteractionCloseRequest(fixture.villager().getUUID(), newInteractionId,
                resumed.sessionId(), resumed.offerToken()).handleServer(fixture.player());
        helper.assertTrue(interactions.getInteractingPlayer().isEmpty(),
                "closing the new screen must release its own interaction");
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).status() == DialogueSession.Status.PAUSED,
                "closing the new screen must pause its resumed conversation");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void closeDuringNodeRolloverPausesThroughPacket(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var interactions = fixture.villager().getInteractions();
        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID interactionId = interactions.interactionId();

        DialogueEngine engine = MCA.getDialogueEngine().orElseThrow();
        DialogueEvent event = passageEvent(ResourceLocation.parse("mca:test/packet_close_rollover"), false);
        long previousToken = 71021L;
        UUID sessionId = UUID.randomUUID();
        sessions(engine).put(fixture.player().getUUID(), DialogueSession.start(
                fixture.player().getUUID(), fixture.villager().getUUID(), sessionId,
                DialogueEvents.INSTANCE.generation(), previousToken, event
        ));
        DialogueEngine.DialogueNodeView next = engine.advance(fixture.player(), previousToken)
                .view().orElseThrow();
        helper.assertTrue(next.offerToken() != previousToken,
                "server progression must rotate the token before the old close arrives");

        new InteractionCloseRequest(fixture.villager().getUUID(), interactionId,
                sessionId, previousToken).handleServer(fixture.player());
        helper.assertTrue(interactions.getInteractingPlayer().isEmpty(),
                "close sent from the previous node must close the original screen");
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).status() == DialogueSession.Status.PAUSED,
                "close sent during token rollover must pause the original session");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void completedDialogueCommandDoesNotCloseAnotherPlayersInteraction(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ServerPlayer other = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "dialogue-other-interactee"));
        other.setPos(fixture.player().position());
        var interactions = fixture.villager().getInteractions();
        interactions.interactAt(other, Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID otherInteractionId = interactions.interactionId();

        DialogueEvent event = simpleEvent(ResourceLocation.parse("mca:test/command_ownership"));
        DialogueEngine engine = engine(event);
        long token = 71022L;
        sessions(engine).put(fixture.player().getUUID(), DialogueSession.start(
                fixture.player().getUUID(), fixture.villager().getUUID(), UUID.randomUUID(),
                1L, token, event
        ).withPendingEffects(List.of(new DialogueAction.Command("divorcePapers"))));
        helper.assertTrue(engine.advance(fixture.player(), token).status() == DialogueEngine.TransitionStatus.COMPLETED,
                "the command must execute for the completing player");
        helper.assertTrue(fixture.player().getInventory().countItem(ItemsMCA.DIVORCE_PAPERS) == 1,
                "the completing player must receive the command's reward");
        helper.assertTrue(interactions.matchesInteraction(other, otherInteractionId),
                "another player's currently owned interaction must survive dialogue command completion");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void closingBeforeTalkOptionsArriveReleasesInteractionAndOffer(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var interactions = fixture.villager().getInteractions();
        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID interactionId = interactions.interactionId();
        DialogueEngine engine = MCA.getDialogueEngine().orElseThrow();
        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());

        new InteractionCloseRequest(fixture.villager().getUUID(), UUID.randomUUID(), Util.NIL_UUID, 0L)
                .handleServer(fixture.player());
        helper.assertTrue(offers(engine).containsKey(fixture.player().getUUID()),
                "a close with a wrong interaction ID cannot discard the menu");

        // The client has pressed Talk, but Escape is sent before the options response is received.
        new InteractionCloseRequest(fixture.villager().getUUID(), interactionId, Util.NIL_UUID, 0L)
                .handleServer(fixture.player());
        helper.assertTrue(interactions.getInteractingPlayer().isEmpty(),
                "closing before options arrive must release the current interaction");
        helper.assertTrue(!offers(engine).containsKey(fixture.player().getUUID()),
                "closing before options arrive must invalidate the unseen menu token");
        helper.assertTrue(engine.select(fixture.player(), menu.token(),
                        DialogueEngine.DialogueSelection.AMBIENT, null).isEmpty(),
                "the unseen menu cannot be replayed after its interaction closes");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void oldMenuTokenCannotCloseNewerMenuWithinSameInteraction(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var interactions = fixture.villager().getInteractions();
        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID interactionId = interactions.interactionId();
        DialogueEngine engine = MCA.getDialogueEngine().orElseThrow();

        DialogueEngine.DialogueOptions olderMenu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueOptions newerMenu = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(olderMenu.token() != newerMenu.token(), "a fresh menu must rotate its token");

        new InteractionCloseRequest(fixture.villager().getUUID(), interactionId,
                Util.NIL_UUID, olderMenu.token()).handleServer(fixture.player());
        helper.assertTrue(interactions.matchesInteraction(fixture.player(), interactionId),
                "an obsolete menu token must not close a newer menu's interaction");
        helper.assertTrue(offers(engine).get(fixture.player().getUUID()).token() == newerMenu.token(),
                "the newer server menu must survive an obsolete close token");

        new InteractionCloseRequest(fixture.villager().getUUID(), interactionId,
                Util.NIL_UUID, 0L).handleServer(fixture.player());
        helper.assertTrue(interactions.getInteractingPlayer().isEmpty(),
                "closing before the newer menu arrives must still release the interaction");
        helper.assertTrue(!offers(engine).containsKey(fixture.player().getUUID()),
                "the genuine early close must discard the outstanding menu");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void forgedPacketCloseCannotDismissActiveDialogueOrInteraction(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var interactions = fixture.villager().getInteractions();
        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID interactionId = interactions.interactionId();
        DialogueEngine engine = MCA.getDialogueEngine().orElseThrow();
        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView active = engine.select(fixture.player(), menu.token(),
                DialogueEngine.DialogueSelection.AMBIENT, null).orElseThrow();

        new InteractionCloseRequest(UUID.randomUUID(), interactionId, active.sessionId(), active.offerToken())
                .handleServer(fixture.player());
        new InteractionCloseRequest(fixture.villager().getUUID(), UUID.randomUUID(),
                active.sessionId(), active.offerToken()).handleServer(fixture.player());
        new InteractionCloseRequest(fixture.villager().getUUID(), interactionId,
                UUID.randomUUID(), active.offerToken()).handleServer(fixture.player());
        new InteractionCloseRequest(fixture.villager().getUUID(), interactionId,
                active.sessionId(), active.offerToken() + 1L).handleServer(fixture.player());

        helper.assertTrue(interactions.getInteractingPlayer().filter(fixture.player()::equals).isPresent(),
                "forged close packets must not close the currently bound villager interaction");
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).status() == DialogueSession.Status.ACTIVE,
                "forged close packets must not pause or replace the active server-owned dialogue");

        new InteractionCloseRequest(fixture.villager().getUUID(), interactionId,
                active.sessionId(), active.offerToken()).handleServer(fixture.player());
        helper.assertTrue(interactions.getInteractingPlayer().isEmpty(),
                "only the matching packet may close this concrete screen interaction");
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).status() == DialogueSession.Status.PAUSED,
                "the matching close must pause the real server dialogue session");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void choiceAndFinalAckPacketHandlersRejectReplaysAndCommitOnce(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEngine engine = MCA.getDialogueEngine().orElseThrow();
        DialogueEvent event = DialogueEvent.decode(ResourceLocation.parse("mca:test/packet_reward_replay"),
                JsonParser.parseString("""
                        {
                          "trigger":"talk",
                          "presentation":{"mode":"ask","prompt":"dialogue.test.prompt","resume_prompt":"dialogue.test.resume"},
                          "repeat":{"type":"always"},
                          "start":"start",
                          "nodes":{
                            "start":{"line":"dialogue.test.start","choices":[{
                              "id":"support","text":"dialogue.test.support",
                              "actions":[{"type":"mca:hearts","amount":5}],
                              "next":"done"
                            }]},
                            "done":{"line":"dialogue.test.done","complete":true}
                          }
                        }
                        """).getAsJsonObject());
        long initialToken = 42L;
        sessions(engine).put(fixture.player().getUUID(), DialogueSession.start(
                fixture.player().getUUID(), fixture.villager().getUUID(), UUID.randomUUID(),
                DialogueEvents.INSTANCE.generation(), initialToken, event
        ).withOfferedChoices(List.of("support")));
        int startingHearts = fixture.villager().getVillagerBrain()
                .getMemoriesForPlayer(fixture.player()).getHearts();

        new InteractionDialogueChoiceMessage(initialToken, "forged").handleServer(fixture.player());
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).nodeId().equals("start"),
                "the real choice handler must reject an unoffered choice ID");
        new InteractionDialogueChoiceMessage(initialToken, "support").handleServer(fixture.player());
        DialogueSession terminal = Objects.requireNonNull(sessions(engine).get(fixture.player().getUUID()));
        helper.assertTrue(terminal.nodeId().equals("done") && terminal.offerToken() != initialToken,
                "the real choice handler must advance and rotate the accepted offer token");
        new InteractionDialogueChoiceMessage(initialToken, "support").handleServer(fixture.player());
        new InteractionDialogueAdvanceMessage(initialToken).handleServer(fixture.player());
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).nodeId().equals("done"),
                "replaying consumed choice/advance packets must not move the active run");
        helper.assertTrue(fixture.villager().getVillagerBrain()
                        .getMemoriesForPlayer(fixture.player()).getHearts() == startingHearts,
                "packet choice and replay must not execute queued effects before final acknowledgement");

        new InteractionDialogueAdvanceMessage(terminal.offerToken()).handleServer(fixture.player());
        new InteractionDialogueAdvanceMessage(terminal.offerToken()).handleServer(fixture.player());
        helper.assertTrue(!sessions(engine).containsKey(fixture.player().getUUID()),
                "the final acknowledgement must consume and end the server-owned session");
        helper.assertTrue(fixture.villager().getVillagerBrain()
                        .getMemoriesForPlayer(fixture.player()).getHearts() == startingHearts + 5,
                "duplicate terminal packet must award the queued hearts exactly once");
        helper.assertTrue(DialogueEventHistory.get(fixture.player().serverLevel()).completed(
                        fixture.player().getUUID(), fixture.villager().getUUID(), event.id()),
                "only a valid terminal acknowledgement may commit the completion history");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void selectPacketHandlerRejectsForgedTopicsAndConsumedMenus(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEngine engine = MCA.getDialogueEngine().orElseThrow();
        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(offers(engine).containsKey(fixture.player().getUUID()),
                "starting a Talk menu must establish a server-owned offer");

        new InteractionDialogueSelectMessage(menu.token(), DialogueEngine.DialogueSelection.EVENT,
                Optional.of(ResourceLocation.parse("forged:nonexistent_topic"))).handleServer(fixture.player());
        helper.assertTrue(!sessions(engine).containsKey(fixture.player().getUUID()),
                "forged event IDs must not create a session through the C2S handler");
        helper.assertTrue(offers(engine).get(fixture.player().getUUID()).token() == menu.token(),
                "forged selections must not consume the legitimate pending menu");

        new InteractionDialogueSelectMessage(menu.token(), DialogueEngine.DialogueSelection.AMBIENT,
                Optional.empty()).handleServer(fixture.player());
        DialogueSession started = Objects.requireNonNull(sessions(engine).get(fixture.player().getUUID()));
        helper.assertTrue(!offers(engine).containsKey(fixture.player().getUUID()),
                "a valid ambient packet must consume the menu offer exactly once");

        new InteractionDialogueSelectMessage(menu.token(), DialogueEngine.DialogueSelection.AMBIENT,
                Optional.empty()).handleServer(fixture.player());
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).id().equals(started.id()),
                "replaying the consumed selection packet must not replace the active session");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void leavePacketHandlerRequiresBoundSessionAndFreshResumeToken(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEngine engine = MCA.getDialogueEngine().orElseThrow();
        DialogueEvent event = simpleEvent(ResourceLocation.parse("mca:test/leave_packet_replay"));
        UUID sessionId = UUID.randomUUID();
        long originalToken = 700L;
        sessions(engine).put(fixture.player().getUUID(), DialogueSession.start(
                fixture.player().getUUID(), fixture.villager().getUUID(), sessionId,
                DialogueEvents.INSTANCE.generation(), originalToken, event
        ).withPendingEffects(List.of(new DialogueAction.Hearts(4))));
        var memories = fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player());
        memories.setHearts(40);

        new InteractionDialogueLeaveMessage(fixture.villager().getUUID(), UUID.randomUUID(), originalToken)
                .handleServer(fixture.player());
        new InteractionDialogueLeaveMessage(fixture.villager().getUUID(), sessionId, originalToken + 1L)
                .handleServer(fixture.player());
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).status() == DialogueSession.Status.ACTIVE,
                "forged leave packets must not pause a different active session or token");

        new InteractionDialogueLeaveMessage(fixture.villager().getUUID(), sessionId, originalToken)
                .handleServer(fixture.player());
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).status() == DialogueSession.Status.PAUSED,
                "a properly bound leave packet must pause the unfinished session");
        new InteractionDialogueAdvanceMessage(originalToken).handleServer(fixture.player());
        helper.assertTrue(fixture.villager().getVillagerBrain()
                        .getMemoriesForPlayer(fixture.player()).getHearts() == 40,
                "an old final acknowledgement cannot commit rewards while the session is paused");

        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(menu.continuationSessionId().filter(sessionId::equals).isPresent(),
                "Talk must offer a continuation for the same paused run");
        DialogueEngine.DialogueNodeView resumed = engine.select(fixture.player(), menu.token(),
                DialogueEngine.DialogueSelection.RESUME, null).orElseThrow();
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).pendingEffects().size() == 1,
                "resuming must preserve the queued heart reward");
        helper.assertTrue(resumed.offerToken() != originalToken,
                "resume must create a fresh offer token instead of reactivating a paused packet token");
        new InteractionDialogueAdvanceMessage(originalToken).handleServer(fixture.player());
        helper.assertTrue(fixture.villager().getVillagerBrain()
                        .getMemoriesForPlayer(fixture.player()).getHearts() == 40,
                "a pre-resume token must remain unusable after the session resumes");
        new InteractionDialogueAdvanceMessage(resumed.offerToken()).handleServer(fixture.player());
        new InteractionDialogueAdvanceMessage(resumed.offerToken()).handleServer(fixture.player());
        helper.assertTrue(!sessions(engine).containsKey(fixture.player().getUUID()),
                "a valid resumed final acknowledgement must consume the active session");
        int observedHearts = fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player()).getHearts();
        boolean remembered = DialogueEventHistory.get(fixture.player().serverLevel()).completed(
                fixture.player().getUUID(), fixture.villager().getUUID(), event.id());
        helper.assertTrue(observedHearts == 44 && remembered,
                "only the valid resumed final acknowledgement may apply the queued reward once; actual="
                        + observedHearts + ", completed=" + remembered);
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void twoPlayersRememberDifferentChoicesAndCooldownsForOneVillager(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ServerPlayer second = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "dialogue-second"));
        second.setPos(fixture.player().position());
        DialogueEvent event = DialogueEvent.decode(ResourceLocation.parse("mca:test/two_player_history"),
                JsonParser.parseString("""
                        {
                          "trigger":"talk",
                          "presentation":{"mode":"ask","prompt":"dialogue.test.prompt","resume_prompt":"dialogue.test.resume"},
                          "repeat":{"type":"cooldown","seconds":5},
                          "start":"start",
                          "nodes":{
                            "start":{"line":"dialogue.test.line","choices":[
                              {"id":"agree","text":"dialogue.test.agree","next":"done"},
                              {"id":"disagree","text":"dialogue.test.disagree","next":"done"}
                            ]},
                            "done":{"line":"dialogue.test.done","complete":true}
                          }
                        }
                        """).getAsJsonObject());
        DialogueEngine engine = engine(event);
        DialogueEventHistory history = DialogueEventHistory.get(fixture.player().serverLevel());

        DialogueEngine.DialogueOptions firstMenu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueOptions secondMenu = engine.begin(second, fixture.villager());
        helper.assertTrue(firstMenu.containsEvent(event.id()) && secondMenu.containsEvent(event.id()),
                "each player must independently see a fresh topic on the same villager");
        DialogueEngine.DialogueNodeView first = engine.select(fixture.player(), firstMenu.token(),
                DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        DialogueEngine.DialogueNodeView other = engine.select(second, secondMenu.token(),
                DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        helper.assertTrue(!first.sessionId().equals(other.sessionId()),
                "simultaneous players must never share a dialogue session");

        DialogueEngine.DialogueNodeView firstDone = engine.choose(fixture.player(), first.offerToken(), "agree")
                .view().orElseThrow();
        helper.assertTrue(engine.advance(fixture.player(), firstDone.offerToken()).completed(),
                "first player must complete only their own run");
        helper.assertTrue(!engine.begin(fixture.player(), fixture.villager()).containsEvent(event.id()),
                "first player's completed story must be cooling down");
        helper.assertTrue(sessions(engine).get(second.getUUID()).id().equals(other.sessionId()),
                "first player's completion must not invalidate the second player's active run");

        DialogueEngine.DialogueNodeView secondDone = engine.choose(second, other.offerToken(), "disagree")
                .view().orElseThrow();
        helper.assertTrue(engine.advance(second, secondDone.offerToken()).completed(),
                "second player must complete independently");
        UUID villagerId = fixture.villager().getUUID();
        helper.assertTrue(history.chose(fixture.player().getUUID(), villagerId, event.id(), "agree"),
                "first player's answer must persist only under the first pair");
        helper.assertTrue(!history.chose(fixture.player().getUUID(), villagerId, event.id(), "disagree"),
                "second player's answer must not overwrite the first player's history");
        helper.assertTrue(history.chose(second.getUUID(), villagerId, event.id(), "disagree"),
                "second player's different answer must persist under the second pair");
        helper.assertTrue(!engine.begin(second, fixture.villager()).containsEvent(event.id()),
                "second player's own completion must independently start their cooldown");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void lostChoiceEligibilityReturnsRefreshedNodeView(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.villager().setAgeState(AgeState.ADULT);
        DialogueEvent event = ageGatedChoiceEvent();
        DialogueEngine engine = engine(event);

        DialogueEngine.DialogueOptions options = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView initial = engine.select(
                fixture.player(), options.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        helper.assertTrue(
                initial.choices().stream().map(DialogueEngine.ChoiceView::id).toList()
                        .equals(List.of("adult", "fallback")),
                "fixture should initially offer both choices"
        );

        fixture.villager().setAgeState(AgeState.CHILD);
        DialogueEngine.TransitionResult rejected = engine.choose(
                fixture.player(), initial.offerToken(), "adult");

        helper.assertTrue(!rejected.accepted(), "a newly ineligible choice must remain rejected");
        helper.assertTrue(rejected.view().isPresent(), "rejection must return the refreshed authoritative node");
        DialogueEngine.DialogueNodeView refreshed = rejected.view().orElseThrow();
        helper.assertTrue(
                refreshed.choices().stream().map(DialogueEngine.ChoiceView::id).toList()
                        .equals(List.of("fallback")),
                "refreshed node must remove the choice that no longer passes its requirements"
        );

        DialogueEngine.TransitionResult fallback = engine.choose(
                fixture.player(), refreshed.offerToken(), "fallback");
        helper.assertTrue(fallback.accepted(), "remaining refreshed choice should still be selectable");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void pausedExpiryInvalidatesContinuationMenuOffer(GameTestHelper helper) {
        Fixture fixture = presentFixture(helper);
        DialogueEvent pausedEvent = simpleEvent(ResourceLocation.parse("mca:test/paused"));
        DialogueEvent otherEvent = simpleEvent(ResourceLocation.parse("mca:test/other"));
        DialogueEngine engine = engine(pausedEvent, otherEvent);

        DialogueEngine.DialogueOptions initial = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView active = engine.select(
                fixture.player(), initial.token(), DialogueEngine.DialogueSelection.EVENT, pausedEvent.id()).orElseThrow();
        engine.pause(fixture.player());

        DialogueEngine.DialogueOptions browsing = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(browsing.continuationPrompt().isPresent(), "paused run should be offered for continuation");
        helper.assertTrue(browsing.containsEvent(otherEvent.id()), "other valid topic should remain in the menu");

        Map<UUID, DialogueSession> sessions = sessions(engine);
        DialogueSession paused = Objects.requireNonNull(sessions.get(fixture.player().getUUID()));
        long now = helper.getLevel().getServer().overworld().getGameTime();
        sessions.put(fixture.player().getUUID(), withPauseDeadline(paused, now));

        engine.tick(helper.getLevel().getServer());

        helper.assertTrue(
                engine.select(
                        fixture.player(),
                        browsing.token(),
                        DialogueEngine.DialogueSelection.EVENT,
                        otherEvent.id()
                ).isEmpty(),
                "expiry must invalidate the menu token that advertised the expired continuation"
        );
        helper.assertTrue(
                engine.advance(fixture.player(), active.offerToken()).status() == DialogueEngine.TransitionStatus.REJECTED,
                "expired run must not accept its stale pre-pause token"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void ambientSelectionUsesOnlyHighestPriorityCandidates(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent low = ambientEvent(ResourceLocation.parse("mca:test/ambient_low"), 10, 100.0D);
        DialogueEvent highA = ambientEvent(ResourceLocation.parse("mca:test/ambient_high_a"), 20, 1.0D);
        DialogueEvent highB = ambientEvent(ResourceLocation.parse("mca:test/ambient_high_b"), 20, 4.0D);
        DialogueEngine engine = engine(low, highA, highB);

        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(menu.ambientAvailable(), "highest-priority ambient candidates should expose ambient selection");
        helper.assertTrue(
                menu.ambientCandidates().equals(List.of(highA.id(), highB.id())),
                "ambient menu should retain only the canonical highest-priority tier"
        );

        DialogueEngine.DialogueNodeView selected = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.AMBIENT, null).orElseThrow();
        DialogueSession session = sessions(engine).get(fixture.player().getUUID());
        helper.assertTrue(session.id().equals(selected.sessionId()), "ambient selection should create the retained run");
        helper.assertTrue(session.eventId().equals(selected.eventId()),
                "active node view must expose the selected event identity");
        helper.assertTrue(
                session.eventId().equals(highA.id()) || session.eventId().equals(highB.id()),
                "ambient selection must not choose a lower-priority candidate"
        );
        helper.assertTrue(offers(engine).isEmpty(), "accepted ambient selection must consume the menu offer");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void persistedCooldownExcludesEventFromTalkOptions(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = cooldownEvent(ResourceLocation.parse("mca:test/cooldown_integration"));
        DialogueEngine engine = engine(event);
        long now = helper.getLevel().getServer().overworld().getGameTime();
        DialogueEventHistory history = DialogueEventHistory.get(fixture.player().serverLevel());
        history.complete(
                fixture.player().getUUID(),
                fixture.villager().getUUID(),
                event,
                Set.of(),
                now,
                RandomSource.create(2L)
        );
        helper.assertTrue(
                history.nextEligibleAt(fixture.player().getUUID(), fixture.villager().getUUID(), event.id()) > now,
                "fixture must persist a future cooldown deadline"
        );

        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(!menu.containsEvent(event.id()), "cooling-down event must be omitted from Talk options");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void resumeBoundaryOffersBeforeDeadlineAndRejectsAtDeadline(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = passageEvent(ResourceLocation.parse("mca:test/resume_boundary"), false);
        DialogueEvent other = simpleEvent(ResourceLocation.parse("mca:test/resume_boundary_other"));
        DialogueEngine engine = engine(event, other);

        DialogueEngine.DialogueOptions initial = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView active = engine.select(
                fixture.player(), initial.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        DialogueEngine.DialogueNodeView savedLine = engine.advance(
                fixture.player(), active.offerToken()).view().orElseThrow();
        engine.pause(fixture.player());

        long now = helper.getLevel().getServer().overworld().getGameTime();
        Map<UUID, DialogueSession> sessions = sessions(engine);
        DialogueSession paused = Objects.requireNonNull(sessions.get(fixture.player().getUUID()));
        sessions.put(fixture.player().getUUID(), withPauseDeadline(paused, now + 1L));

        DialogueEngine.DialogueOptions browsing = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(browsing.continuationPrompt().isPresent(), "one tick before expiry must still offer continuation");
        helper.assertTrue(browsing.containsEvent(other.id()), "continuation should remain alongside other valid topics");
        helper.assertTrue(
                sessions.get(fixture.player().getUUID()).lineIndex() == paused.lineIndex(),
                "opening Talk must not resume or move the saved line"
        );

        sessions.put(
                fixture.player().getUUID(),
                withPauseDeadline(Objects.requireNonNull(sessions.get(fixture.player().getUUID())), now)
        );
        helper.assertTrue(
                engine.select(fixture.player(), browsing.token(), DialogueEngine.DialogueSelection.RESUME, null).isEmpty(),
                "resume at the exact deadline must be rejected"
        );
        helper.assertTrue(sessions(engine).isEmpty(), "expired resume must discard the unfinished run without restarting it");
        helper.assertTrue(
                engine.advance(fixture.player(), savedLine.offerToken()).status() == DialogueEngine.TransitionStatus.REJECTED,
                "pre-pause token must remain stale after expiry"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void continueTokensAreSingleUseAndTerminalNeedsFinalAck(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = passageEvent(ResourceLocation.parse("mca:test/passage"), false);
        DialogueEngine engine = engine(event);

        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView first = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        DialogueEngine.TransitionResult secondResult = engine.advance(fixture.player(), first.offerToken());
        helper.assertTrue(secondResult.status() == DialogueEngine.TransitionStatus.ADVANCED,
                "first Continue should advance to the second passage line");
        DialogueEngine.DialogueNodeView second = secondResult.view().orElseThrow();

        helper.assertTrue(
                engine.advance(fixture.player(), first.offerToken()).status() == DialogueEngine.TransitionStatus.REJECTED,
                "accepted Continue token must be single-use"
        );

        DialogueEngine.TransitionResult terminalResult = engine.advance(fixture.player(), second.offerToken());
        helper.assertTrue(terminalResult.status() == DialogueEngine.TransitionStatus.ADVANCED,
                "entering a completing terminal must only send its final line");
        DialogueEngine.DialogueNodeView terminal = terminalResult.view().orElseThrow();
        helper.assertTrue(!sessions(engine).isEmpty(), "terminal line must retain the run until final Continue");

        DialogueEngine.TransitionResult completed = engine.advance(fixture.player(), terminal.offerToken());
        helper.assertTrue(completed.status() == DialogueEngine.TransitionStatus.COMPLETED,
                "final Continue should complete the run");
        helper.assertTrue(completed.sessionId().orElseThrow().equals(terminal.sessionId()),
                "completion must retain the terminated session identity for stale-response filtering");
        helper.assertTrue(sessions(engine).isEmpty(), "completed run must be discarded");
        helper.assertTrue(offers(engine).isEmpty(), "completion must not auto-open or auto-start another event");
        helper.assertTrue(
                engine.advance(fixture.player(), terminal.offerToken()).status() == DialogueEngine.TransitionStatus.REJECTED,
                "final Continue token must be stale after completion"
        );

        DialogueEngine.DialogueOptions refreshed = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(refreshed.containsEvent(event.id()), "Talk options should refresh only when explicitly requested");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void choiceTokensRejectForgedAndDuplicateSelections(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = ageGatedChoiceEvent();
        DialogueEngine engine = engine(event);

        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView node = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();

        helper.assertTrue(
                engine.choose(fixture.player(), node.offerToken(), "forged").status()
                        == DialogueEngine.TransitionStatus.REJECTED,
                "unoffered choice id must be rejected"
        );
        DialogueEngine.TransitionResult accepted = engine.choose(fixture.player(), node.offerToken(), "fallback");
        helper.assertTrue(accepted.status() == DialogueEngine.TransitionStatus.ADVANCED,
                "offered choice should advance");
        helper.assertTrue(
                engine.choose(fixture.player(), node.offerToken(), "fallback").status()
                        == DialogueEngine.TransitionStatus.REJECTED,
                "accepted choice token must be single-use"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void zeroEligibleChoicesExitSafely(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.villager().setAgeState(AgeState.CHILD);
        DialogueEvent event = adultOnlyChoiceEvent();
        DialogueEngine engine = engine(event);

        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView node = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        helper.assertTrue(node.choices().isEmpty(), "ineligible choice must not be offered");
        helper.assertTrue(node.canContinue(), "zero-choice node must expose safe Continue exit");

        DialogueEngine.TransitionResult ended = engine.advance(fixture.player(), node.offerToken());
        helper.assertTrue(ended.status() == DialogueEngine.TransitionStatus.ENDED,
                "zero-choice Continue should end without completing");
        helper.assertTrue(ended.sessionId().orElseThrow().equals(node.sessionId()),
                "ended transition must retain the terminated session identity for stale-response filtering");
        helper.assertTrue(sessions(engine).isEmpty(), "ended run must be discarded");
        helper.assertTrue(offers(engine).isEmpty(), "ending must not automatically open or chain another event");
        DialogueEngine.DialogueOptions refreshed = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(refreshed.containsEvent(event.id()), "Talk should refresh options only after an explicit new begin");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void newlyEligibleChoiceRefreshesInsteadOfLeavingContinuePending(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.villager().setAgeState(AgeState.CHILD);
        DialogueEvent event = adultOnlyChoiceEvent();
        DialogueEngine engine = engine(event);

        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView node = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        helper.assertTrue(node.choices().isEmpty(), "fixture must initially expose the safe Continue exit");

        fixture.villager().setAgeState(AgeState.ADULT);
        DialogueEngine.TransitionResult refreshedResult = engine.advance(fixture.player(), node.offerToken());
        helper.assertTrue(refreshedResult.status() == DialogueEngine.TransitionStatus.REJECTED,
                "Continue must not bypass a choice that became eligible");
        DialogueEngine.DialogueNodeView refreshed = refreshedResult.view().orElseThrow();
        helper.assertTrue(refreshed.offerToken() == node.offerToken(),
                "choice refresh must retain the current single-use offer token");
        helper.assertTrue(
                refreshed.choices().stream().map(DialogueEngine.ChoiceView::id).toList().equals(List.of("adult")),
                "client must receive the newly authoritative choice instead of waiting forever"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void activeRunCannotBeReplacedByForgedBeginSelection(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent activeEvent = passageEvent(ResourceLocation.parse("mca:test/active_begin"), false);
        DialogueEvent replacementEvent = simpleEvent(ResourceLocation.parse("mca:test/active_begin_replacement"));
        DialogueEngine engine = engine(activeEvent, replacementEvent);

        DialogueEngine.DialogueOptions initial = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView active = engine.select(
                fixture.player(), initial.token(), DialogueEngine.DialogueSelection.EVENT, activeEvent.id()).orElseThrow();

        DialogueEngine.DialogueOptions forgedMenu = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(forgedMenu.containsEvent(replacementEvent.id()),
                "fixture must prove the forged Begin produced a selectable-looking menu");
        helper.assertTrue(engine.select(
                fixture.player(), forgedMenu.token(), DialogueEngine.DialogueSelection.EVENT, replacementEvent.id()).isEmpty(),
                "a second Begin must not replace an ACTIVE run"
        );
        DialogueSession retained = sessions(engine).get(fixture.player().getUUID());
        helper.assertTrue(retained != null && retained.id().equals(active.sessionId()),
                "the original active session must remain authoritative");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void interactionLossReturnsPausedTransition(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = passageEvent(ResourceLocation.parse("mca:test/pause_response"), false);
        DialogueEngine engine = engine(event);

        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView node = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        fixture.player().setPos(fixture.player().getX() + 100.0D, fixture.player().getY(), fixture.player().getZ());

        DialogueEngine.TransitionResult pausedResult = engine.advance(fixture.player(), node.offerToken());
        helper.assertTrue(pausedResult.status() == DialogueEngine.TransitionStatus.PAUSED,
                "losing interaction range must report the server-side pause to the client");
        helper.assertTrue(!pausedResult.accepted(), "pausing on interaction loss must not count as accepted progression");
        helper.assertTrue(pausedResult.sessionId().orElseThrow().equals(node.sessionId()),
                "paused transition must retain the resumable session identity");
        DialogueSession paused = sessions(engine).get(fixture.player().getUUID());
        helper.assertTrue(paused != null && paused.status() == DialogueSession.Status.PAUSED,
                "interaction loss must retain a paused run rather than discard it");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void menuRefreshPreservesPauseDeadlineAndOnlyResumeRestoresProgress(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = passageEvent(ResourceLocation.parse("mca:test/resume"), false);
        DialogueEvent other = simpleEvent(ResourceLocation.parse("mca:test/resume_other"));
        DialogueEngine engine = engine(event, other);

        DialogueEngine.DialogueOptions initialMenu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView first = engine.select(
                fixture.player(), initialMenu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        DialogueEngine.DialogueNodeView second = engine.advance(fixture.player(), first.offerToken()).view().orElseThrow();
        engine.pause(fixture.player());

        DialogueSession paused = sessions(engine).get(fixture.player().getUUID());
        long deadline = Objects.requireNonNull(paused).pauseDeadline();
        UUID sessionId = paused.id();
        DialogueEngine.DialogueOptions firstBrowse = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueOptions secondBrowse = engine.begin(fixture.player(), fixture.villager());

        helper.assertTrue(firstBrowse.continuationPrompt().isPresent(), "Talk must expose continuation while paused");
        helper.assertTrue(firstBrowse.containsEvent(other.id()), "continuation must not hide other topics");
        helper.assertTrue(!firstBrowse.containsEvent(event.id()), "paused event must not be offered as a fresh start");
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).pauseDeadline() == deadline,
                "menu refresh must not extend pause deadline");
        helper.assertTrue(
                engine.select(fixture.player(), firstBrowse.token(), DialogueEngine.DialogueSelection.RESUME, null).isEmpty(),
                "older menu token must be invalidated by a newer Talk menu"
        );

        DialogueEngine.DialogueNodeView resumed = engine.select(
                fixture.player(), secondBrowse.token(), DialogueEngine.DialogueSelection.RESUME, null).orElseThrow();
        helper.assertTrue(resumed.sessionId().equals(sessionId), "resume must retain the same run identity");
        helper.assertTrue(resumed.line().equals(second.line()), "resume must restore the saved passage line");
        helper.assertTrue(resumed.offerToken() != second.offerToken(), "resume must issue a fresh active-run token");
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).status() == DialogueSession.Status.ACTIVE,
                "explicit RESUME should reactivate the paused run");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void browsingAnotherVillagerPreservesPauseUntilValidReplacement(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        VillagerEntityMCA bob = villagerNear(helper, fixture.player(), -1.0D);
        DialogueEvent pausedEvent = simpleChoiceEvent(ResourceLocation.parse("mca:test/alice_story"));
        DialogueEvent otherEvent = simpleEvent(ResourceLocation.parse("mca:test/other_topic"));
        DialogueEngine engine = engine(pausedEvent, otherEvent);

        DialogueEngine.DialogueOptions aliceMenu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView aliceStart = engine.select(
                fixture.player(), aliceMenu.token(), DialogueEngine.DialogueSelection.EVENT, pausedEvent.id()).orElseThrow();
        engine.choose(fixture.player(), aliceStart.offerToken(), "remember_me");
        engine.pause(fixture.player());
        UUID pausedId = sessions(engine).get(fixture.player().getUUID()).id();
        helper.assertTrue(
                sessions(engine).get(fixture.player().getUUID()).acceptedChoices().contains("remember_me"),
                "fixture should retain a temporary accepted choice before replacement"
        );

        DialogueEngine.DialogueOptions bobBrowse = engine.begin(fixture.player(), bob);
        helper.assertTrue(bobBrowse.continuationPrompt().isEmpty(), "Alice's run must not appear as Bob's continuation");
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).id().equals(pausedId),
                "browsing Bob must preserve Alice's paused run");
        helper.assertTrue(
                engine.select(
                        fixture.player(),
                        aliceMenu.token(),
                        DialogueEngine.DialogueSelection.EVENT,
                        pausedEvent.id()
                ).isEmpty(),
                "opening Bob's menu must invalidate the older Alice-bound menu token"
        );
        helper.assertTrue(
                engine.select(
                        fixture.player(),
                        bobBrowse.token(),
                        DialogueEngine.DialogueSelection.EVENT,
                        ResourceLocation.parse("mca:test/forged")
                ).isEmpty(),
                "failed selection must not replace the paused run"
        );
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).id().equals(pausedId),
                "failed selection must leave Alice resumable");

        DialogueEngine.DialogueOptions aliceBrowse = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(aliceBrowse.continuationPrompt().isPresent(), "Alice should still be resumable after Bob browsing");
        DialogueEngine.DialogueOptions bobSelect = engine.begin(fixture.player(), bob);
        DialogueEngine.DialogueNodeView replacement = engine.select(
                fixture.player(), bobSelect.token(), DialogueEngine.DialogueSelection.EVENT, otherEvent.id()).orElseThrow();

        DialogueSession retained = sessions(engine).get(fixture.player().getUUID());
        helper.assertTrue(!retained.id().equals(pausedId), "valid Bob topic must replace Alice's paused run");
        helper.assertTrue(retained.villagerId().equals(bob.getUUID()), "replacement run must bind Bob");
        helper.assertTrue(retained.id().equals(replacement.sessionId()), "only the replacement run should remain");
        helper.assertTrue(retained.acceptedChoices().isEmpty(), "replacement must discard Alice's temporary choices");
        helper.assertTrue(sessions(engine).size() == 1, "engine must retain at most one run per player");
        helper.assertTrue(offers(engine).isEmpty(), "accepted selection must consume the one current menu offer");

        engine.pause(fixture.player());
        DialogueEngine.DialogueOptions aliceAfterReplacement = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(aliceAfterReplacement.continuationPrompt().isEmpty(),
                "discarded Alice run must not reappear after Bob replacement");
        helper.assertTrue(offers(engine).size() == 1, "engine must retain at most one menu offer per player");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void sameVillagerReplacementClearsTransientStateWithoutWritingHistory(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent pausedEvent = simpleChoiceEvent(ResourceLocation.parse("mca:test/same_villager_story"));
        DialogueEvent replacementEvent = simpleEvent(ResourceLocation.parse("mca:test/same_villager_other"));
        DialogueEngine engine = engine(pausedEvent, replacementEvent);
        DialogueEventHistory history = DialogueEventHistory.get(fixture.player().serverLevel());

        DialogueEngine.DialogueOptions initial = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView start = engine.select(
                fixture.player(), initial.token(), DialogueEngine.DialogueSelection.EVENT, pausedEvent.id()).orElseThrow();
        DialogueEngine.TransitionResult chosen = engine.choose(fixture.player(), start.offerToken(), "remember_me");
        DialogueEngine.DialogueNodeView unfinished = chosen.view().orElseThrow();
        engine.pause(fixture.player());

        UUID playerId = fixture.player().getUUID();
        UUID villagerId = fixture.villager().getUUID();
        DialogueSession paused = Objects.requireNonNull(sessions(engine).get(playerId));
        DialogueAction queued = new DialogueAction.Hearts(1);
        sessions(engine).put(playerId, paused.withPendingEffects(List.of(queued)));
        UUID pausedId = paused.id();
        helper.assertTrue(paused.acceptedChoices().contains("remember_me"), "fixture must carry an accepted temporary choice");
        helper.assertTrue(!history.completed(playerId, villagerId, pausedEvent.id()), "unfinished story must not be durable history");
        helper.assertTrue(!history.chose(playerId, villagerId, pausedEvent.id(), "remember_me"),
                "temporary choice must not be persisted before completion");

        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(menu.continuationPrompt().isPresent(), "same villager should still offer the paused continuation");
        helper.assertTrue(menu.containsEvent(replacementEvent.id()), "same villager should also offer a different valid topic");
        DialogueEngine.DialogueNodeView replacement = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, replacementEvent.id()).orElseThrow();

        DialogueSession retained = Objects.requireNonNull(sessions(engine).get(playerId));
        helper.assertTrue(!retained.id().equals(pausedId), "different same-villager topic must replace the paused run");
        helper.assertTrue(retained.id().equals(replacement.sessionId()), "only replacement session should remain");
        helper.assertTrue(retained.acceptedChoices().isEmpty(), "replacement must clear unfinished accepted choices");
        helper.assertTrue(retained.selectedOutcomes().isEmpty(), "replacement must clear unfinished selected outcomes");
        helper.assertTrue(retained.pendingEffects().isEmpty(), "replacement must clear queued unfinished effects");
        helper.assertTrue(
                engine.advance(fixture.player(), unfinished.offerToken()).status() == DialogueEngine.TransitionStatus.REJECTED,
                "replaced run token must be invalid"
        );
        helper.assertTrue(!history.completed(playerId, villagerId, pausedEvent.id()),
                "abandoning a run must not mark it completed in durable history");
        helper.assertTrue(!history.chose(playerId, villagerId, pausedEvent.id(), "remember_me"),
                "abandoning a run must not persist its temporary choice");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void reloadGenerationInvalidatesActiveRunAndToken(GameTestHelper helper) {
        Fixture fixture = presentFixture(helper);
        DialogueEvent event = passageEvent(ResourceLocation.parse("mca:test/reload"), false);
        DialogueEngine engine = engine(event);

        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView active = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        seedEvents(events(engine), 2L, event);

        helper.assertTrue(
                engine.advance(fixture.player(), active.offerToken()).status() == DialogueEngine.TransitionStatus.REJECTED,
                "reload generation change must reject the active token"
        );
        helper.assertTrue(sessions(engine).isEmpty(), "reload mismatch must discard the transient run");

        DialogueEngine pausedEngine = engine(event);
        DialogueEngine.DialogueOptions pausedMenu = pausedEngine.begin(fixture.player(), fixture.villager());
        pausedEngine.select(
                fixture.player(), pausedMenu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        pausedEngine.pause(fixture.player());
        DialogueEngine.DialogueOptions continuation = pausedEngine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(continuation.continuationPrompt().isPresent(), "fixture must expose paused continuation");
        seedEvents(events(pausedEngine), 2L, event);
        pausedEngine.tick(helper.getLevel().getServer());
        helper.assertTrue(sessions(pausedEngine).isEmpty(), "reload tick must discard paused transient state");
        helper.assertTrue(offers(pausedEngine).isEmpty(), "reload tick must discard its continuation menu offer");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_reload", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 300)
    public static void realServerResourceReloadDiscardsActiveAndPausedPendingEffects(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ServerPlayer other = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "dialogue-reload"));
        other.setPos(fixture.player().position());
        helper.assertTrue(DialogueEvents.INSTANCE.get(MCA.locate("ambient/baseline")).isPresent(),
                "the shipped baseline must be loaded before a real reload");

        DialogueEngine activeEngine = new DialogueEngine(DialogueEvents.INSTANCE);
        DialogueEngine pausedEngine = new DialogueEngine(DialogueEvents.INSTANCE);
        DialogueEngine.DialogueOptions activeMenu = activeEngine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView active = activeEngine.select(fixture.player(), activeMenu.token(),
                DialogueEngine.DialogueSelection.AMBIENT, null).orElseThrow();
        DialogueSession inFlight = sessions(activeEngine).get(fixture.player().getUUID());
        sessions(activeEngine).put(fixture.player().getUUID(),
                inFlight.withPendingEffects(List.of(new DialogueAction.Hearts(7))));
        int heartsBefore = fixture.villager().getVillagerBrain()
                .getMemoriesForPlayer(fixture.player()).getHearts();

        DialogueEngine.DialogueOptions otherMenu = pausedEngine.begin(other, fixture.villager());
        DialogueEngine.DialogueNodeView paused = pausedEngine.select(other, otherMenu.token(),
                DialogueEngine.DialogueSelection.AMBIENT, null).orElseThrow();
        sessions(pausedEngine).put(other.getUUID(),
                sessions(pausedEngine).get(other.getUUID()).withPendingEffects(List.of(new DialogueAction.Hearts(9))));
        pausedEngine.pause(other);
        helper.assertTrue(sessions(pausedEngine).get(other.getUUID()).status() == DialogueSession.Status.PAUSED,
                "second player must have an unfinished paused run before reload");

        var server = helper.getLevel().getServer();
        long oldGeneration = DialogueEvents.INSTANCE.generation();
        Set<ResourceLocation> validBeforeReload = DialogueEvents.INSTANCE.all().stream()
                .map(DialogueEvent::id).collect(java.util.stream.Collectors.toSet());
        server.reloadResources(server.getPackRepository().getSelectedIds());

        helper.succeedWhen(() -> {
            helper.assertTrue(DialogueEvents.INSTANCE.generation() > oldGeneration,
                    "server resource reload must publish a new DialogueEvents generation");
            helper.assertTrue(validBeforeReload.stream().allMatch(id -> DialogueEvents.INSTANCE.get(id).isPresent()),
                    "a real reload must preserve unrelated valid dialogue events, including any installed addon events");
            helper.assertTrue(activeEngine.advance(fixture.player(), active.offerToken()).status()
                            == DialogueEngine.TransitionStatus.REJECTED,
                    "a pre-reload active offer must be rejected, never committed");
            helper.assertTrue(sessions(activeEngine).isEmpty(),
                    "a real reload must clear the active transient session");
            helper.assertTrue(pausedEngine.begin(other, fixture.villager()).continuationPrompt().isEmpty(),
                    "a real reload must not offer the previously paused continuation");
            helper.assertTrue(sessions(pausedEngine).isEmpty(),
                    "a real reload must clear the paused transient session");
            helper.assertTrue(fixture.villager().getVillagerBrain()
                            .getMemoriesForPlayer(fixture.player()).getHearts() == heartsBefore,
                    "the queued active-session reward must not execute after reload");
            helper.assertTrue(!paused.sessionId().equals(active.sessionId()),
                    "the two pre-reload sessions must have been distinct");
        });
    }

    @GameTest(batch = "mca_dialogue_reload_pack", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 500)
    public static void installingThenDisablingRealAddonPackInvalidatesQueuedDialogue(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var server = helper.getLevel().getServer();
        var repository = server.getPackRepository();
        Set<String> originalPacks = new LinkedHashSet<>(repository.getSelectedIds());
        Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("dialogue-mca-live-reload-test");
        Path events = pack.resolve("data/dialogue_reload_test/dialogue_events/verification");
        ResourceLocation validId = ResourceLocation.parse("dialogue_reload_test:verification/valid");
        ResourceLocation invalidId = ResourceLocation.parse("dialogue_reload_test:verification/invalid");
        try {
            Files.createDirectories(events);
            Files.writeString(pack.resolve("pack.mcmeta"), """
                    {"pack":{"pack_format":48,"description":"Temporary dialogue reload acceptance fixture"}}
                    """);
            Files.writeString(events.resolve("valid.json"), """
                    {
                      "trigger":"talk",
                      "presentation":{"mode":"ask","prompt":"dialogue_reload_test.untranslated",
                                      "resume_prompt":"dialogue_reload_test.resume"},
                      "priority":1000,
                      "repeat":{"type":"always"},
                      "start":"main",
                      "nodes":{"main":{"line":"dialogue_reload_test.no_client_translation","complete":true}}
                    }
                    """);
            Files.writeString(events.resolve("invalid.json"), """
                    {
                      "trigger":"talk",
                      "presentation":{"mode":"ask","prompt":"dialogue_reload_test.invalid",
                                      "resume_prompt":"dialogue_reload_test.resume"},
                      "requirements":[{"type":"mca:deliberately_invalid_test_condition"}],
                      "repeat":{"type":"always"},
                      "start":"main",
                      "nodes":{"main":{"line":"dialogue_reload_test.invalid","complete":true}}
                    }
                    """);
        } catch (IOException exception) {
            throw new AssertionError("Could not install temporary server datapack for reload GameTest", exception);
        }

        repository.reload();
        String packId = repository.getAvailableIds().stream()
                .filter(id -> id.endsWith("dialogue-mca-live-reload-test"))
                .findFirst().orElseThrow(() -> new AssertionError("Test datapack was not discovered"));
        Set<String> enabledPacks = new LinkedHashSet<>(originalPacks);
        enabledPacks.add(packId);
        long generationBefore = DialogueEvents.INSTANCE.generation();
        AtomicInteger phase = new AtomicInteger();
        AtomicLong generationWithPack = new AtomicLong();
        AtomicLong activeToken = new AtomicLong();
        AtomicInteger initialHearts = new AtomicInteger();
        AtomicInteger pausedPlayerHearts = new AtomicInteger();
        AtomicReference<DialogueEngine> activeEngine = new AtomicReference<>();
        AtomicReference<DialogueEngine> pausedEngine = new AtomicReference<>();
        AtomicReference<Throwable> reloadFailure = new AtomicReference<>();
        ServerPlayer other = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "dialogue-pack-remove"));
        other.setPos(fixture.player().position());
        server.reloadResources(enabledPacks).whenComplete((ignored, error) -> {
            if (error != null) {
                reloadFailure.set(error);
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(reloadFailure.get() == null,
                    "real datapack enable/disable reload failed: " + reloadFailure.get());
            if (phase.get() == 0) {
                helper.assertTrue(DialogueEvents.INSTANCE.generation() > generationBefore,
                        "enabling the test pack must trigger a real dialogue registry reload");
                helper.assertTrue(DialogueEvents.INSTANCE.get(validId).isPresent(),
                        "a newly enabled valid namespaced addon event must be loaded");
                helper.assertTrue(DialogueEvents.INSTANCE.get(invalidId).isEmpty(),
                        "a malformed event in the same pack must be rejected independently");
                helper.assertTrue(DialogueEvents.INSTANCE.get(MCA.locate("ambient/baseline")).isPresent(),
                        "an invalid addon event must not remove a shipped event");

                DialogueEngine active = new DialogueEngine(DialogueEvents.INSTANCE);
                DialogueEngine paused = new DialogueEngine(DialogueEvents.INSTANCE);
                DialogueEngine.DialogueOptions activeMenu = active.begin(fixture.player(), fixture.villager());
                helper.assertTrue(activeMenu.containsEvent(validId),
                        "server must offer the newly installed addon topic without client translations");
                DialogueEngine.DialogueNodeView node = active.select(fixture.player(), activeMenu.token(),
                        DialogueEngine.DialogueSelection.EVENT, validId).orElseThrow();
                sessions(active).put(fixture.player().getUUID(),
                        sessions(active).get(fixture.player().getUUID())
                                .withPendingEffects(List.of(new DialogueAction.Hearts(7))));
                initialHearts.set(fixture.villager().getVillagerBrain()
                        .getMemoriesForPlayer(fixture.player()).getHearts());

                DialogueEngine.DialogueOptions otherMenu = paused.begin(other, fixture.villager());
                helper.assertTrue(otherMenu.containsEvent(validId),
                        "second player must independently see the installed addon topic");
                paused.select(other, otherMenu.token(), DialogueEngine.DialogueSelection.EVENT, validId).orElseThrow();
                sessions(paused).put(other.getUUID(),
                        sessions(paused).get(other.getUUID())
                                .withPendingEffects(List.of(new DialogueAction.Hearts(9))));
                pausedPlayerHearts.set(fixture.villager().getVillagerBrain()
                        .getMemoriesForPlayer(other).getHearts());
                paused.pause(other);
                helper.assertTrue(paused.begin(other, fixture.villager()).continuationPrompt().isPresent(),
                        "the addon session must offer a paused continuation before removal");

                activeEngine.set(active);
                pausedEngine.set(paused);
                activeToken.set(node.offerToken());
                generationWithPack.set(DialogueEvents.INSTANCE.generation());
                phase.set(1);
                server.reloadResources(originalPacks).whenComplete((ignored, error) -> {
                    if (error != null) {
                        reloadFailure.set(error);
                    }
                });
            }
            helper.assertTrue(phase.get() == 1 &&
                            DialogueEvents.INSTANCE.generation() > generationWithPack.get(),
                    "disabling the pack must complete a second real server resource reload");
            helper.assertTrue(DialogueEvents.INSTANCE.get(validId).isEmpty(),
                    "the removed addon event must disappear from server authoritative registry");
            helper.assertTrue(DialogueEvents.INSTANCE.get(invalidId).isEmpty(),
                    "malformed addon event must never be registered");
            helper.assertTrue(DialogueEvents.INSTANCE.get(MCA.locate("ambient/baseline")).isPresent(),
                    "disabling an addon must preserve shipped events");
            helper.assertTrue(activeEngine.get().advance(fixture.player(), activeToken.get()).status()
                            == DialogueEngine.TransitionStatus.REJECTED,
                    "a removed event's pre-reload token must not complete its queued reward");
            helper.assertTrue(sessions(activeEngine.get()).isEmpty(),
                    "removing the addon must discard its active session");
            helper.assertTrue(pausedEngine.get().begin(other, fixture.villager()).continuationPrompt().isEmpty(),
                    "a paused session from a removed addon must not be resumable");
            helper.assertTrue(sessions(pausedEngine.get()).isEmpty(),
                    "removing the addon must discard the paused pending actions");
            helper.assertTrue(fixture.villager().getVillagerBrain()
                            .getMemoriesForPlayer(fixture.player()).getHearts() == initialHearts.get(),
                    "removing an addon must never execute a queued heart reward");
            helper.assertTrue(fixture.villager().getVillagerBrain()
                            .getMemoriesForPlayer(other).getHearts() == pausedPlayerHearts.get(),
                    "removing an addon must never execute the paused player's queued reward");
            try {
                Files.delete(events.resolve("valid.json"));
                Files.delete(events.resolve("invalid.json"));
                Files.delete(events);
                Files.delete(events.getParent());
                Files.delete(events.getParent().getParent());
                Files.delete(events.getParent().getParent().getParent());
                Files.delete(pack.resolve("pack.mcmeta"));
                Files.delete(pack);
            } catch (IOException exception) {
                throw new AssertionError("Could not clean up temporary server datapack", exception);
            }
            repository.reload();
        });
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void reloadReissuesCurrentlyVisibleTalkMenu(GameTestHelper helper) {
        Fixture fixture = presentFixture(helper);
        DialogueEvent event = simpleEvent(ResourceLocation.parse("mca:test/reloaded_menu"));
        DialogueEngine engine = engine(event);
        try {
            Field interacting = net.conczin.mca.entity.interaction.EntityCommandHandler.class
                    .getDeclaredField("interactingPlayer");
            interacting.setAccessible(true);
            interacting.set(fixture.villager().getInteractions(), fixture.player());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not simulate an open villager interaction", exception);
        }

        DialogueEngine.DialogueOptions stale = engine.begin(fixture.player(), fixture.villager());
        seedEvents(events(engine), 2L, event);
        engine.tick(helper.getLevel().getServer());

        DialogueEngine.DialogueOptions current = offers(engine).get(fixture.player().getUUID());
        helper.assertTrue(current != null && current.generation() == 2L && current.token() != stale.token(),
                "datapack reload must replace an open Talk menu with fresh server-authoritative options");
        helper.assertTrue(engine.select(fixture.player(), stale.token(),
                        DialogueEngine.DialogueSelection.EVENT, event.id()).isEmpty(),
                "old menu offer must not be usable after reload");
        helper.assertTrue(engine.select(fixture.player(), current.token(),
                        DialogueEngine.DialogueSelection.EVENT, event.id()).isPresent(),
                "the refreshed menu must remain selectable");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void startedAndResumedRunIgnoresOriginalEntryRequirementDrift(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.villager().getVillagerBrain().modifyMoodValue(-100);
        helper.getLevel().setDayTime(18_000L);
        helper.getLevel().updateSkyBrightness();
        helper.getLevel().setWeatherParameters(0, 6_000, true, true);
        helper.getLevel().setRainLevel(1.0F);
        helper.getLevel().setThunderLevel(1.0F);
        DialogueEvent event = contextualEntryEvent();
        DialogueEngine engine = engine(event);

        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView first = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        helper.getLevel().setDayTime(6_000L);
        helper.getLevel().updateSkyBrightness();
        helper.getLevel().setWeatherParameters(6_000, 0, false, false);
        helper.getLevel().setRainLevel(0.0F);
        helper.getLevel().setThunderLevel(0.0F);
        fixture.villager().getVillagerBrain().modifyMoodValue(200);

        DialogueEngine.TransitionResult advanced = engine.advance(fixture.player(), first.offerToken());
        helper.assertTrue(advanced.status() == DialogueEngine.TransitionStatus.ADVANCED,
                "started run must not recheck original time/weather/mood requirements");
        DialogueEngine.DialogueNodeView second = advanced.view().orElseThrow();
        engine.pause(fixture.player());
        DialogueEngine.DialogueOptions pausedMenu = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(pausedMenu.continuationPrompt().isPresent(),
                "continuation should remain available after original time/weather/mood requirements change");

        DialogueEngine.DialogueNodeView resumed = engine.select(
                fixture.player(), pausedMenu.token(), DialogueEngine.DialogueSelection.RESUME, null).orElseThrow();
        helper.assertTrue(resumed.line().equals(second.line()), "resume must restore progress without rechecking entry gates");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void selectedOutcomeSurvivesPauseAndResume(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = outcomeEvent();
        DialogueEngine engine = engine(event);

        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView start = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        DialogueEngine.TransitionResult chosen = engine.choose(fixture.player(), start.offerToken(), "weighted");
        helper.assertTrue(chosen.status() == DialogueEngine.TransitionStatus.ADVANCED, "weighted choice should advance");
        Map<String, Integer> selected = sessions(engine).get(fixture.player().getUUID()).selectedOutcomes();
        helper.assertTrue(selected.containsKey("weighted"), "server must retain the selected outcome index");

        engine.pause(fixture.player());
        DialogueEngine.DialogueOptions pausedMenu = engine.begin(fixture.player(), fixture.villager());
        engine.select(fixture.player(), pausedMenu.token(), DialogueEngine.DialogueSelection.RESUME, null).orElseThrow();
        helper.assertTrue(
                sessions(engine).get(fixture.player().getUUID()).selectedOutcomes().equals(selected),
                "resume must not reroll or lose the selected outcome"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void villagerUnloadPausesButDeathAndConversionDiscard(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = passageEvent(ResourceLocation.parse("mca:test/villager_leave"), false);
        DialogueEngine engine = engine(event);

        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        engine.select(fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        engine.onVillagerLeave(fixture.villager());
        DialogueSession paused = sessions(engine).get(fixture.player().getUUID());
        helper.assertTrue(paused.status() == DialogueSession.Status.PAUSED,
                "temporary villager unload should pause rather than discard the run");

        fixture.villager().discard();
        engine.onVillagerLeave(fixture.villager());
        helper.assertTrue(sessions(engine).isEmpty(), "conversion-style discard must remove unfinished state");
        helper.assertTrue(offers(engine).isEmpty(), "conversion-style discard must invalidate menu offers");

        VillagerEntityMCA killed = villagerNear(helper, fixture.player(), -1.0D);
        DialogueEngine.DialogueOptions killedMenu = engine.begin(fixture.player(), killed);
        engine.select(fixture.player(), killedMenu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        killed.remove(Entity.RemovalReason.KILLED);
        engine.onVillagerLeave(killed);
        helper.assertTrue(sessions(engine).isEmpty(), "villager death must discard unfinished state");
        helper.assertTrue(offers(engine).isEmpty(), "villager death must invalidate menu offers");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void expiredUnloadedRunIsDroppedWithoutReloadingVillager(GameTestHelper helper) {
        Fixture fixture = presentFixture(helper);
        DialogueEvent event = passageEvent(ResourceLocation.parse("mca:test/unloaded_expiry"), false);
        DialogueEngine engine = engine(event);

        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        engine.select(fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        fixture.villager().remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        engine.onVillagerLeave(fixture.villager());
        helper.assertTrue(
                sessions(engine).get(fixture.player().getUUID()).status() == DialogueSession.Status.PAUSED,
                "temporary unload should retain only paused transient state"
        );
        helper.assertTrue(
                helper.getLevel().getEntity(fixture.villager().getUUID()) == null,
                "fixture villager must actually be unloaded before expiry"
        );

        long now = helper.getLevel().getServer().overworld().getGameTime();
        DialogueSession paused = Objects.requireNonNull(sessions(engine).get(fixture.player().getUUID()));
        sessions(engine).put(fixture.player().getUUID(), withPauseDeadline(paused, now));
        engine.tick(helper.getLevel().getServer());

        helper.assertTrue(sessions(engine).isEmpty(), "expired unloaded run must be discarded");
        helper.assertTrue(
                helper.getLevel().getEntity(fixture.villager().getUUID()) == null,
                "expiry lookup must not reload the unloaded villager"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void offlineTickAndServerClearDiscardTransientState(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = simpleEvent(ResourceLocation.parse("mca:test/lifecycle"));
        DialogueEngine engine = engine(event);

        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        engine.select(fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        engine.tick(helper.getLevel().getServer());
        helper.assertTrue(sessions(engine).isEmpty(), "offline player tick must discard its transient run");
        helper.assertTrue(offers(engine).isEmpty(), "offline player tick must discard its menu offer");

        DialogueEngine.DialogueOptions restartedMenu = engine.begin(fixture.player(), fixture.villager());
        engine.select(
                fixture.player(), restartedMenu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        engine.begin(fixture.player(), fixture.villager());
        engine.clear(helper.getLevel().getServer());
        helper.assertTrue(sessions(engine).isEmpty(), "server clear must discard all runs");
        helper.assertTrue(offers(engine).isEmpty(), "server clear must discard all menu offers");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void serverStartReplacesTransientDialogueEngineState(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEngine first = MCA.getDialogueEngine().orElseThrow();
        DialogueEvent event = simpleEvent(ResourceLocation.parse("mca:test/server_lifecycle"));
        long generation = DialogueEvents.INSTANCE.generation();
        UUID playerId = fixture.player().getUUID();

        sessions(first).put(playerId, DialogueSession.start(
                playerId,
                fixture.villager().getUUID(),
                UUID.randomUUID(),
                generation,
                1L,
                event
        ));
        offers(first).put(playerId, new DialogueEngine.DialogueOptions(
                fixture.villager().getUUID(),
                generation,
                Optional.empty(),
                List.of(),
                false,
                Optional.empty(),
                2L,
                List.of(),
                Optional.empty()
        ));
        helper.assertTrue(!sessions(first).isEmpty() && !offers(first).isEmpty(),
                "fixture must seed transient state into the current server engine");

        MCA.startServer(helper.getLevel().getServer());
        DialogueEngine second = MCA.getDialogueEngine().orElseThrow();
        helper.assertTrue(second != first, "a fresh server start must install a fresh dialogue engine");
        helper.assertTrue(sessions(second).isEmpty(), "new server engine must not inherit prior sessions");
        helper.assertTrue(offers(second).isEmpty(), "new server engine must not inherit prior menu offers");
        helper.succeed();
    }

    private static DialogueEngine engine(DialogueEvent... definitions) {
        DialogueEvents events = new DialogueEvents();
        seedEvents(events, 1L, definitions);
        return new DialogueEngine(events, RandomSource.create(1L));
    }

    private static void seedEvents(DialogueEvents events, long generation, DialogueEvent... definitions) {
        try {
            Field snapshotField = DialogueEvents.class.getDeclaredField("snapshot");
            snapshotField.setAccessible(true);
            Class<?> snapshotType = snapshotField.getType();
            Constructor<?> constructor = snapshotType.getDeclaredConstructor(Map.class, long.class);
            constructor.setAccessible(true);
            Map<ResourceLocation, DialogueEvent> loaded = new LinkedHashMap<>();
            for (DialogueEvent definition : definitions) {
                loaded.put(definition.id(), definition);
            }
            snapshotField.set(events, constructor.newInstance(Map.copyOf(loaded), generation));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not seed dialogue events for GameTest", exception);
        }
    }

    private static DialogueEvents events(DialogueEngine engine) {
        try {
            Field eventsField = DialogueEngine.class.getDeclaredField("events");
            eventsField.setAccessible(true);
            return (DialogueEvents) eventsField.get(engine);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not inspect dialogue event registry for GameTest", exception);
        }
    }

    private static Fixture fixture(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(BlockPos.ZERO);
        ServerPlayer player = new FakePlayer(
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "dialogue-engine")
        );
        player.setPos(position.getCenter());
        VillagerEntityMCA villager = Objects.requireNonNull(EntitiesMCA.MALE_VILLAGER.create(helper.getLevel()));
        villager.setAgeState(AgeState.ADULT);
        villager.setPos(player.getX() + 1.0D, player.getY(), player.getZ());
        helper.getLevel().addFreshEntity(villager);
        return new Fixture(player, villager);
    }

    private static VillagerEntityMCA villagerNear(GameTestHelper helper, ServerPlayer player, double offset) {
        VillagerEntityMCA villager = Objects.requireNonNull(EntitiesMCA.MALE_VILLAGER.create(helper.getLevel()));
        villager.setAgeState(AgeState.ADULT);
        villager.setPos(player.getX() + offset, player.getY(), player.getZ());
        helper.getLevel().addFreshEntity(villager);
        return villager;
    }

    @SuppressWarnings("unchecked")
    private static Fixture presentFixture(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        try {
            Object playerList = helper.getLevel().getServer().getPlayerList();
            Field playersByUuid = playerList.getClass().getSuperclass().getDeclaredField("playersByUUID");
            playersByUuid.setAccessible(true);
            ((Map<UUID, ServerPlayer>) playersByUuid.get(playerList))
                    .put(fixture.player().getUUID(), fixture.player());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not mark FakePlayer present for DialogueEngine.tick", exception);
        }
        return fixture;
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, DialogueSession> sessions(DialogueEngine engine) {
        try {
            Field sessionsField = DialogueEngine.class.getDeclaredField("sessions");
            sessionsField.setAccessible(true);
            return (Map<UUID, DialogueSession>) sessionsField.get(engine);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not inspect dialogue sessions for GameTest", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, DialogueEngine.DialogueOptions> offers(DialogueEngine engine) {
        try {
            Field offersField = DialogueEngine.class.getDeclaredField("offers");
            offersField.setAccessible(true);
            return (Map<UUID, DialogueEngine.DialogueOptions>) offersField.get(engine);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not inspect dialogue menu offers for GameTest", exception);
        }
    }

    private static DialogueSession withPauseDeadline(DialogueSession session, long deadline) {
        return new DialogueSession(
                session.playerId(),
                session.villagerId(),
                session.id(),
                session.generation(),
                session.offerToken(),
                session.status(),
                deadline,
                session.event(),
                session.nodeId(),
                session.lineIndex(),
                session.offeredChoices(),
                session.acceptedChoices(),
                session.selectedOutcomes(),
                session.pendingEffects()
        );
    }

    private static DialogueEvent simpleEvent(ResourceLocation id) {
        return DialogueEvent.decode(id, JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{
                    "mode":"ask",
                    "prompt":"dialogue.test.prompt",
                    "resume_prompt":"dialogue.test.resume"
                  },
                  "repeat":{"type":"always"},
                  "start":"start",
                  "nodes":{"start":{"line":"dialogue.test.line","complete":true}}
                }
                """).getAsJsonObject());
    }

    private static DialogueEvent ambientEvent(ResourceLocation id, int priority, double weight) {
        return DialogueEvent.decode(id, JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{"mode":"ambient","resume_prompt":"dialogue.test.resume"},
                  "priority":%d,
                  "weight":%s,
                  "repeat":{"type":"always"},
                  "start":"start",
                  "nodes":{"start":{"line":"dialogue.test.line","complete":true}}
                }
                """.formatted(priority, weight)).getAsJsonObject());
    }

    private static DialogueEvent cooldownEvent(ResourceLocation id) {
        return DialogueEvent.decode(id, JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{
                    "mode":"ask",
                    "prompt":"dialogue.test.prompt",
                    "resume_prompt":"dialogue.test.resume"
                  },
                  "repeat":{"type":"cooldown","seconds":5},
                  "start":"start",
                  "nodes":{"start":{"line":"dialogue.test.line","complete":true}}
                }
                """).getAsJsonObject());
    }

    private static DialogueEvent simpleChoiceEvent(ResourceLocation id) {
        return DialogueEvent.decode(id, JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{
                    "mode":"ask",
                    "prompt":"dialogue.test.prompt",
                    "resume_prompt":"dialogue.test.resume"
                  },
                  "repeat":{"type":"always"},
                  "start":"start",
                  "nodes":{
                    "start":{
                      "line":"dialogue.test.line",
                      "choices":[{
                        "id":"remember_me",
                        "text":"dialogue.test.remember_me",
                        "next":"done"
                      }]
                    },
                    "done":{"line":"dialogue.test.done","complete":true}
                  }
                }
                """).getAsJsonObject());
    }

    private static DialogueEvent passageEvent(ResourceLocation id, boolean adultEntryRequirement) {
        String requirements = adultEntryRequirement
                ? "\"requirements\":[{\"type\":\"mca:age_group\",\"value\":\"adult\"}],"
                : "";
        return DialogueEvent.decode(id, JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{
                    "mode":"ask",
                    "prompt":"dialogue.test.prompt",
                    "resume_prompt":"dialogue.test.resume"
                  },
                  %s
                  "repeat":{"type":"always"},
                  "start":"start",
                  "nodes":{
                    "start":{"lines":["dialogue.test.one","dialogue.test.two"],"next":"done"},
                    "done":{"line":"dialogue.test.done","complete":true}
                  }
                }
                """.formatted(requirements)).getAsJsonObject());
    }

    private static DialogueEvent contextualEntryEvent() {
        return DialogueEvent.decode(ResourceLocation.parse("mca:test/context_drift"), JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{
                    "mode":"ask",
                    "prompt":"dialogue.test.prompt",
                    "resume_prompt":"dialogue.test.resume"
                  },
                  "requirements":[
                    {"type":"mca:time","value":"night"},
                    {"type":"mca:weather","value":"thunder"},
                    {"type":"mca:mood","value":"depressed"}
                  ],
                  "repeat":{"type":"always"},
                  "start":"start",
                  "nodes":{
                    "start":{"lines":["dialogue.test.one","dialogue.test.two"],"next":"done"},
                    "done":{"line":"dialogue.test.done","complete":true}
                  }
                }
                """).getAsJsonObject());
    }

    private static DialogueEvent ageGatedChoiceEvent() {
        return DialogueEvent.decode(ResourceLocation.parse("mca:test/choice_refresh"), JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{
                    "mode":"ask",
                    "prompt":"dialogue.test.prompt",
                    "resume_prompt":"dialogue.test.resume"
                  },
                  "repeat":{"type":"always"},
                  "start":"start",
                  "nodes":{
                    "start":{
                      "line":"dialogue.test.line",
                      "choices":[
                        {
                          "id":"adult",
                          "text":"dialogue.test.adult",
                          "requirements":[{"type":"mca:age_group","value":"adult"}],
                          "next":"done"
                        },
                        {
                          "id":"fallback",
                          "text":"dialogue.test.fallback",
                          "next":"done"
                        }
                      ]
                    },
                    "done":{"line":"dialogue.test.done","complete":true}
                  }
                }
                """).getAsJsonObject());
    }

    private static DialogueEvent adultOnlyChoiceEvent() {
        return DialogueEvent.decode(ResourceLocation.parse("mca:test/adult_only_choice"), JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{
                    "mode":"ask",
                    "prompt":"dialogue.test.prompt",
                    "resume_prompt":"dialogue.test.resume"
                  },
                  "repeat":{"type":"always"},
                  "start":"start",
                  "nodes":{
                    "start":{
                      "line":"dialogue.test.line",
                      "choices":[{
                        "id":"adult",
                        "text":"dialogue.test.adult",
                        "requirements":[{"type":"mca:age_group","value":"adult"}],
                        "next":"done"
                      }]
                    },
                    "done":{"line":"dialogue.test.done","complete":true}
                  }
                }
                """).getAsJsonObject());
    }

    private static DialogueEvent outcomeEvent() {
        return DialogueEvent.decode(ResourceLocation.parse("mca:test/outcome_resume"), JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{
                    "mode":"ask",
                    "prompt":"dialogue.test.prompt",
                    "resume_prompt":"dialogue.test.resume"
                  },
                  "repeat":{"type":"always"},
                  "start":"start",
                  "nodes":{
                    "start":{
                      "line":"dialogue.test.line",
                      "choices":[{
                        "id":"weighted",
                        "text":"dialogue.test.weighted",
                        "outcomes":[
                          {
                            "requirements":[{"type":"mca:age_group","value":"adult"}],
                            "weight":1,
                            "next":"done"
                          },
                          {
                            "requirements":[{"type":"mca:age_group","value":"adult"}],
                            "weight":4,
                            "next":"done"
                          }
                        ]
                      }]
                    },
                    "done":{"line":"dialogue.test.done","complete":true}
                  }
                }
                """).getAsJsonObject());
    }

    private record Fixture(ServerPlayer player, VillagerEntityMCA villager) {
    }
}
