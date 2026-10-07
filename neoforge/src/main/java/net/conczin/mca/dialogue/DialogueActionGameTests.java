package net.conczin.mca.dialogue;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.resources.DialogueEvents;
import net.conczin.mca.server.world.data.DialogueEventHistory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@PrefixGameTestTemplate(false)
public final class DialogueActionGameTests {
    private DialogueActionGameTests() {
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void choiceQueuesEffectsUntilFinalContinueAndCommitsOnce(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = rewardingStory(ResourceLocation.parse("mca:test/action_commit"), false);
        DialogueEngine engine = engine(event);
        Memories memories = fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player());
        DialogueEventHistory history = DialogueEventHistory.get(fixture.player().serverLevel());
        memories.setHearts(49);
        int heartsBefore = memories.getHearts();
        fixture.villager().getVillagerBrain().modifyMoodValue(-fixture.villager().getVillagerBrain().getMoodValue());
        int moodBefore = fixture.villager().getVillagerBrain().getMoodValue();
        int fatigueBefore = memories.getInteractionFatigue();

        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView start = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        DialogueEngine.TransitionResult chosen = engine.choose(fixture.player(), start.offerToken(), "support");
        helper.assertTrue(chosen.accepted(), "valid rewarding choice should be accepted");
        DialogueEngine.DialogueNodeView terminal = chosen.view().orElseThrow();
        DialogueSession queued = Objects.requireNonNull(sessions(engine).get(fixture.player().getUUID()));
        Memories queuedMemories = fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player());

        helper.assertTrue(queued.pendingEffects().size() == 3, "accepted choice must queue all authored effects");
        helper.assertTrue(queuedMemories.getHearts() == heartsBefore,
                "accepting choice must not grant hearts before completion");
        helper.assertTrue(fixture.villager().getVillagerBrain().getMoodValue() == moodBefore,
                "accepting choice must not change mood before completion");
        helper.assertTrue(queuedMemories.getInteractionFatigue() == fatigueBefore,
                "accepting choice must not add interaction fatigue before completion");
        helper.assertTrue(!fixture.villager().getLongTermMemory().hasMemory(memoryId(fixture.player())),
                "accepting choice must not write long-term memory before completion");
        helper.assertTrue(!history.completed(fixture.player().getUUID(), fixture.villager().getUUID(), event.id()),
                "accepting choice must not write completion history");

        DialogueEngine.TransitionResult completed = engine.advance(fixture.player(), terminal.offerToken());
        Memories committedMemories = fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player());
        helper.assertTrue(completed.status() == DialogueEngine.TransitionStatus.COMPLETED,
                "final Continue should commit completing event");
        helper.assertTrue(committedMemories.getHearts() == heartsBefore + 5, "mca:hearts should use the relationship owner");
        helper.assertTrue(committedMemories.getInteractionFatigue() == fatigueBefore + 1,
                "rewardHearts must preserve its interaction-fatigue side effect");
        helper.assertTrue(fixture.villager().getVillagerBrain().getMoodValue() == moodBefore + 7,
                "rewardHearts mood effect plus explicit mca:mood delta should each apply exactly once");
        helper.assertTrue(fixture.villager().getLongTermMemory().hasMemory(memoryId(fixture.player())),
                "mca:remember should commit through LongTermMemory");
        helper.assertTrue(history.completed(fixture.player().getUUID(), fixture.villager().getUUID(), event.id()),
                "successful completion must write story history");
        helper.assertTrue(history.chose(fixture.player().getUUID(), fixture.villager().getUUID(), event.id(), "support"),
                "successful completion must persist the stable accepted choice");
        long nextEligible = history.nextEligibleAt(fixture.player().getUUID(), fixture.villager().getUUID(), event.id());
        helper.assertTrue(nextEligible > helper.getLevel().getServer().overworld().getGameTime(),
                "successful completion must start the authored cooldown");

        helper.assertTrue(engine.advance(fixture.player(), terminal.offerToken()).status()
                        == DialogueEngine.TransitionStatus.REJECTED,
                "duplicate final Continue must be rejected");
        Memories afterDuplicate = fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player());
        helper.assertTrue(afterDuplicate.getHearts() == heartsBefore + 5, "duplicate Continue must not replay rewards");
        helper.assertTrue(afterDuplicate.getInteractionFatigue() == fatigueBefore + 1,
                "duplicate Continue must not replay owner side effects");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void weightedOutcomeQueuesOnlySelectedOutcomeEffects(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = outcomeReward(ResourceLocation.parse("mca:test/outcome_action"));
        DialogueEngine engine = engine(event);
        Memories memories = fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player());
        int heartsBefore = memories.getHearts();

        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView start = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        DialogueEngine.TransitionResult chosen = engine.choose(fixture.player(), start.offerToken(), "answer");
        DialogueSession queued = Objects.requireNonNull(sessions(engine).get(fixture.player().getUUID()));
        helper.assertTrue(queued.pendingEffects().size() == 1,
                "selected outcome should queue only its own authored actions");
        helper.assertTrue(queued.selectedOutcomes().get("answer") == 0,
                "single eligible outcome should be retained for resume stability");
        helper.assertTrue(fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player()).getHearts()
                        == heartsBefore,
                "outcome action must stay queued before completion");

        DialogueEngine.TransitionResult completed = engine.advance(
                fixture.player(), chosen.view().orElseThrow().offerToken());
        helper.assertTrue(completed.status() == DialogueEngine.TransitionStatus.COMPLETED,
                "outcome path should complete on final Continue");
        helper.assertTrue(fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player()).getHearts()
                        == heartsBefore + 4,
                "selected outcome reward should apply exactly once");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void pauseResumePreservesQueueWithoutReplay(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = heartsOnlyStory(ResourceLocation.parse("mca:test/action_resume"), 3);
        DialogueEngine engine = engine(event);
        Memories memories = fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player());
        int heartsBefore = memories.getHearts();

        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView start = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        DialogueEngine.DialogueNodeView terminal = engine.choose(
                fixture.player(), start.offerToken(), "support").view().orElseThrow();
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).pendingEffects().size() == 1,
                "reward should be queued before pause");
        engine.pause(fixture.player());

        DialogueEngine.DialogueOptions pausedMenu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView resumed = engine.select(
                fixture.player(), pausedMenu.token(), DialogueEngine.DialogueSelection.RESUME, null).orElseThrow();
        helper.assertTrue(fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player()).getHearts()
                        == heartsBefore,
                "resume must not execute queued effects");
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).pendingEffects().size() == 1,
                "resume must preserve the pending queue");
        helper.assertTrue(resumed.line().equals(terminal.line()), "resume should return the saved terminal line");

        engine.advance(fixture.player(), resumed.offerToken());
        helper.assertTrue(fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player()).getHearts()
                        == heartsBefore + 3,
                "queued reward should execute once after resumed final Continue");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void abandonmentTimeoutAndEndDiscardQueuedEffects(GameTestHelper helper) {
        Fixture fixture = presentFixture(helper);
        DialogueEvent rewarding = heartsOnlyStory(ResourceLocation.parse("mca:test/action_discard"), 6);
        DialogueEvent replacement = simpleComplete(ResourceLocation.parse("mca:test/action_replacement"));
        DialogueEvent ending = heartsEnd(ResourceLocation.parse("mca:test/action_end"), 8);
        DialogueEngine engine = engine(rewarding, replacement, ending);
        Memories memories = fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player());
        DialogueEventHistory history = DialogueEventHistory.get(fixture.player().serverLevel());
        int heartsBefore = memories.getHearts();

        DialogueEngine.DialogueNodeView rewardingTerminal = startAndChoose(engine, fixture, rewarding);
        engine.pause(fixture.player());
        DialogueEngine.DialogueOptions browse = engine.begin(fixture.player(), fixture.villager());
        engine.select(fixture.player(), browse.token(), DialogueEngine.DialogueSelection.EVENT, replacement.id()).orElseThrow();
        helper.assertTrue(fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player()).getHearts()
                        == heartsBefore,
                "replacing paused run must discard queued reward");
        helper.assertTrue(!history.completed(fixture.player().getUUID(), fixture.villager().getUUID(), rewarding.id()),
                "abandoned run must not write completion history");
        helper.assertTrue(engine.advance(fixture.player(), rewardingTerminal.offerToken()).status()
                        == DialogueEngine.TransitionStatus.REJECTED,
                "abandoned run token must stay stale");

        engine.end(fixture.player());
        DialogueEngine.DialogueNodeView timeoutTerminal = startAndChoose(engine, fixture, rewarding);
        engine.pause(fixture.player());
        DialogueSession paused = Objects.requireNonNull(sessions(engine).get(fixture.player().getUUID()));
        sessions(engine).put(fixture.player().getUUID(), withPauseDeadline(
                paused, helper.getLevel().getServer().overworld().getGameTime()));
        engine.tick(helper.getLevel().getServer());
        helper.assertTrue(sessions(engine).isEmpty(), "expired run must be discarded");
        helper.assertTrue(fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player()).getHearts()
                        == heartsBefore,
                "expired run must not apply queued reward");
        helper.assertTrue(!history.completed(fixture.player().getUUID(), fixture.villager().getUUID(), rewarding.id()),
                "expired run must not write completion history");
        helper.assertTrue(engine.advance(fixture.player(), timeoutTerminal.offerToken()).status()
                        == DialogueEngine.TransitionStatus.REJECTED,
                "expired run token must stay stale");

        DialogueEngine.DialogueNodeView endTerminal = startAndChoose(engine, fixture, ending);
        DialogueEngine.TransitionResult ended = engine.advance(fixture.player(), endTerminal.offerToken());
        helper.assertTrue(ended.status() == DialogueEngine.TransitionStatus.ENDED,
                "non-completing terminal should end the run");
        helper.assertTrue(fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player()).getHearts()
                        == heartsBefore,
                "non-completing end must discard queued reward");
        helper.assertTrue(!history.completed(fixture.player().getUUID(), fixture.villager().getUUID(), ending.id()),
                "non-completing end must not write history");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void schedulingHistoryCommitsOnlyCooldownTiming(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = schedulingStory(ResourceLocation.parse("mca:test/scheduling_action"));
        DialogueEngine engine = engine(event);
        DialogueEventHistory history = DialogueEventHistory.get(fixture.player().serverLevel());

        DialogueEngine.DialogueNodeView terminal = startAndChoose(engine, fixture, event);
        DialogueEngine.TransitionResult completed = engine.advance(fixture.player(), terminal.offerToken());
        long now = helper.getLevel().getServer().overworld().getGameTime();

        helper.assertTrue(completed.status() == DialogueEngine.TransitionStatus.COMPLETED,
                "scheduling-only event should still complete normally");
        helper.assertTrue(!history.completed(fixture.player().getUUID(), fixture.villager().getUUID(), event.id()),
                "scheduling-only history must not expose durable completion");
        helper.assertTrue(!history.chose(fixture.player().getUUID(), fixture.villager().getUUID(), event.id(), "support"),
                "scheduling-only history must not retain stable choices");
        helper.assertTrue(history.nextEligibleAt(fixture.player().getUUID(), fixture.villager().getUUID(), event.id()) > now,
                "scheduling-only completion must retain cooldown timing");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void failedCommandCannotGrantRewardsOrCompletion(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = failingCommandStory(ResourceLocation.parse("mca:test/command_failure"));
        DialogueEngine engine = engine(event);
        DialogueEventHistory history = DialogueEventHistory.get(fixture.player().serverLevel());
        Memories memories = fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player());
        int heartsBefore = memories.getHearts();

        DialogueEngine.DialogueNodeView terminal = startAndChoose(engine, fixture, event);
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).pendingEffects().size() == 2,
                "command and reward should both be queued before commit");
        DialogueEngine.TransitionResult result = engine.advance(fixture.player(), terminal.offerToken());

        helper.assertTrue(result.status() == DialogueEngine.TransitionStatus.ENDED,
                "failed command should fail closed and end the unfinished run");
        helper.assertTrue(fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player()).getHearts()
                        == heartsBefore,
                "failed command must prevent later story rewards from applying");
        helper.assertTrue(!history.completed(fixture.player().getUUID(), fixture.villager().getUUID(), event.id()),
                "failed command must prevent completion history");
        helper.assertTrue(sessions(engine).isEmpty(), "failed command should not leave an unfinishable retained run");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void villagerLineUsesMessengerDialogueFallbackKey(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = simpleComplete(ResourceLocation.parse("mca:test/localized_line"));
        DialogueEngine engine = engine(event);

        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView view = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();

        helper.assertTrue(view.line().getContents() instanceof TranslatableContents,
                "villager line should remain a translatable component");
        TranslatableContents contents = (TranslatableContents) view.line().getContents();
        helper.assertTrue(contents.getKey().endsWith(".dialogue.test.localized"),
                "Messenger must preserve the authored line key after fallback flags");
        helper.assertTrue(contents.getKey().contains("#G") && contents.getKey().contains("#E")
                        && contents.getKey().contains("#P") && contents.getKey().contains("#T"),
                "villager line must include gender/personality/profession/dialogue-type fallback flags");
        helper.assertTrue(contents.getArgs().length == 1,
                "Messenger should prepend exactly the implicit player-name argument when no authored params exist");
        helper.assertTrue(Objects.equals(contents.getArgs()[0], fixture.player().getName().getString()),
                "implicit translation argument should resolve to the player name");
        helper.succeed();
    }

    private static DialogueEngine.DialogueNodeView startAndChoose(
            DialogueEngine engine,
            Fixture fixture,
            DialogueEvent event
    ) {
        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView start = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        return engine.choose(fixture.player(), start.offerToken(), "support").view().orElseThrow();
    }

    private static DialogueEngine engine(DialogueEvent... definitions) {
        DialogueEvents events = new DialogueEvents();
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
            snapshotField.set(events, constructor.newInstance(Map.copyOf(loaded), 1L));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not seed dialogue events for GameTest", exception);
        }
        return new DialogueEngine(events, RandomSource.create(1L));
    }

    private static Fixture fixture(GameTestHelper helper) {
        BlockPos position = helper.absolutePos(BlockPos.ZERO);
        ServerPlayer player = new FakePlayer(
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "dialogue-actions")
        );
        player.setPos(position.getCenter());
        VillagerEntityMCA villager = Objects.requireNonNull(EntitiesMCA.MALE_VILLAGER.create(helper.getLevel()));
        villager.setAgeState(AgeState.ADULT);
        villager.setPos(player.getX() + 1.0D, player.getY(), player.getZ());
        helper.getLevel().addFreshEntity(villager);
        return new Fixture(player, villager);
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

    private static DialogueEvent rewardingStory(ResourceLocation id, boolean scheduling) {
        return DialogueEvent.decode(id, JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{"mode":"ask","prompt":"dialogue.test.prompt","resume_prompt":"dialogue.test.resume"},
                  "repeat":{"type":"cooldown","seconds":5},
                  "history":"%s",
                  "start":"start",
                  "nodes":{
                    "start":{"line":"dialogue.test.start","choices":[{
                      "id":"support","text":"dialogue.test.support",
                      "actions":[
                        {"type":"mca:hearts","amount":5},
                        {"type":"mca:mood","amount":2},
                        {"type":"mca:remember","id":"task6_memory","var":"player","time":40}
                      ],
                      "next":"done"
                    }]},
                    "done":{"line":"dialogue.test.done","complete":true}
                  }
                }
                """.formatted(scheduling ? "scheduling" : "story")).getAsJsonObject());
    }

    private static DialogueEvent heartsOnlyStory(ResourceLocation id, int amount) {
        return DialogueEvent.decode(id, JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{"mode":"ask","prompt":"dialogue.test.prompt","resume_prompt":"dialogue.test.resume"},
                  "repeat":{"type":"always"},
                  "start":"start",
                  "nodes":{
                    "start":{"line":"dialogue.test.start","choices":[{
                      "id":"support","text":"dialogue.test.support",
                      "actions":[{"type":"mca:hearts","amount":%d}],
                      "next":"done"
                    }]},
                    "done":{"line":"dialogue.test.done","complete":true}
                  }
                }
                """.formatted(amount)).getAsJsonObject());
    }

    private static DialogueEvent heartsEnd(ResourceLocation id, int amount) {
        return DialogueEvent.decode(id, JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{"mode":"ask","prompt":"dialogue.test.prompt","resume_prompt":"dialogue.test.resume"},
                  "repeat":{"type":"always"},
                  "start":"start",
                  "nodes":{
                    "start":{"line":"dialogue.test.start","choices":[{
                      "id":"support","text":"dialogue.test.support",
                      "actions":[{"type":"mca:hearts","amount":%d}],
                      "next":"done"
                    }]},
                    "done":{"line":"dialogue.test.done","end":true}
                  }
                }
                """.formatted(amount)).getAsJsonObject());
    }

    private static DialogueEvent schedulingStory(ResourceLocation id) {
        return DialogueEvent.decode(id, JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{"mode":"ask","prompt":"dialogue.test.prompt","resume_prompt":"dialogue.test.resume"},
                  "repeat":{"type":"cooldown","seconds":5},
                  "history":"scheduling",
                  "start":"start",
                  "nodes":{
                    "start":{"line":"dialogue.test.start","choices":[{
                      "id":"support","text":"dialogue.test.support","next":"done"
                    }]},
                    "done":{"line":"dialogue.test.done","complete":true}
                  }
                }
                """).getAsJsonObject());
    }

    private static DialogueEvent outcomeReward(ResourceLocation id) {
        return DialogueEvent.decode(id, JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{"mode":"ask","prompt":"dialogue.test.prompt","resume_prompt":"dialogue.test.resume"},
                  "repeat":{"type":"always"},
                  "start":"start",
                  "nodes":{
                    "start":{"line":"dialogue.test.start","choices":[{
                      "id":"answer","text":"dialogue.test.answer",
                      "outcomes":[{
                        "weight":1,"actions":[{"type":"mca:hearts","amount":4}],"next":"done"
                      }]
                    }]},
                    "done":{"line":"dialogue.test.done","complete":true}
                  }
                }
                """).getAsJsonObject());
    }

    private static DialogueEvent failingCommandStory(ResourceLocation id) {
        return DialogueEvent.decode(id, JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{"mode":"ask","prompt":"dialogue.test.prompt","resume_prompt":"dialogue.test.resume"},
                  "repeat":{"type":"always"},
                  "start":"start",
                  "nodes":{
                    "start":{"line":"dialogue.test.start","choices":[{
                      "id":"support","text":"dialogue.test.support",
                      "actions":[
                        {"type":"mca:command","command":"definitely_missing"},
                        {"type":"mca:hearts","amount":10}
                      ],
                      "next":"done"
                    }]},
                    "done":{"line":"dialogue.test.done","complete":true}
                  }
                }
                """).getAsJsonObject());
    }

    private static DialogueEvent simpleComplete(ResourceLocation id) {
        return DialogueEvent.decode(id, JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{"mode":"ask","prompt":"dialogue.test.prompt","resume_prompt":"dialogue.test.resume"},
                  "repeat":{"type":"always"},
                  "start":"start",
                  "nodes":{"start":{"line":"dialogue.test.localized","complete":true}}
                }
                """).getAsJsonObject());
    }

    private static String memoryId(ServerPlayer player) {
        return "task6_memory." + player.getUUID();
    }

    private record Fixture(ServerPlayer player, VillagerEntityMCA villager) {
    }

}
