package net.conczin.mca.dialogue;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.resources.DialogueEvents;
import net.conczin.mca.server.world.data.DialogueEventHistory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

        engine.pause(fixture.player());

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
        helper.assertTrue(sessions(engine).isEmpty(), "ended run must be discarded");
        helper.assertTrue(offers(engine).isEmpty(), "ending must not automatically open or chain another event");
        DialogueEngine.DialogueOptions refreshed = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(refreshed.containsEvent(event.id()), "Talk should refresh options only after an explicit new begin");
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
        DialogueAction queued = new DialogueAction.Defined(
                ResourceLocation.parse("mca:hearts"),
                JsonParser.parseString("{\"type\":\"mca:hearts\",\"amount\":1}").getAsJsonObject()
        );
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
