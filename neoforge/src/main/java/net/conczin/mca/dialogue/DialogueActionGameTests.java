package net.conczin.mca.dialogue;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.authlib.GameProfile;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.resources.DialogueEvents;
import net.conczin.mca.server.world.data.DialogueEventHistory;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@PrefixGameTestTemplate(false)
public final class DialogueActionGameTests {
    private static final ResourceLocation CUSTOM_TEST_ACTION = ResourceLocation.parse("dialogue_test:earned_points");
    private static final ResourceLocation REPLACE_INTERACTION_ACTION = ResourceLocation.parse("dialogue_test:replace_interaction");
    private static final AtomicInteger CUSTOM_POINTS = new AtomicInteger();

    static {
        DialogueAction.register(CUSTOM_TEST_ACTION, Codec.INT.fieldOf("points").codec(),
                (points, context) -> CUSTOM_POINTS.addAndGet(points));
        DialogueAction.register(REPLACE_INTERACTION_ACTION, Codec.BOOL.fieldOf("replace").codec(),
                (replace, context) -> {
                    if (replace) {
                        context.villager().getInteractions().interactAt(context.player(), Vec3.ZERO,
                                InteractionHand.MAIN_HAND);
                    }
                });
    }

    private DialogueActionGameTests() {
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void registeredAddonActionRunsOnlyOnFinalAcknowledgement(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        CUSTOM_POINTS.set(0);
        DialogueEvent event = DialogueEvent.decode(ResourceLocation.parse("dialogue_test:action_story"),
                JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{"mode":"ask","prompt":"test.prompt","resume_prompt":"test.resume"},
                  "repeat":{"type":"always"},"start":"intro",
                  "nodes":{
                    "intro":{"line":"test.intro","choices":[{"id":"yes","text":"test.yes",
                      "actions":[{"type":"dialogue_test:earned_points","points":7}],"next":"done"}]},
                    "done":{"line":"test.done","complete":true}
                  }
                }
                """).getAsJsonObject());
        DialogueEngine engine = engine(event);
        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(CUSTOM_POINTS.get() == 0, "offering a custom action must not execute it");
        DialogueEngine.DialogueNodeView intro = engine.select(fixture.player(), menu.token(),
                DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        DialogueEngine.DialogueNodeView done = engine.choose(fixture.player(), intro.offerToken(), "yes")
                .view().orElseThrow();
        helper.assertTrue(CUSTOM_POINTS.get() == 0, "accepted choice must only queue the registered effect");
        helper.assertTrue(engine.advance(fixture.player(), done.offerToken()).completed(),
                "acknowledging the final line must complete the registered action");
        helper.assertTrue(CUSTOM_POINTS.get() == 7, "the registered executor must receive the authored value");
        helper.assertTrue(engine.advance(fixture.player(), done.offerToken()).status()
                        == DialogueEngine.TransitionStatus.REJECTED,
                "the old token must not repeat an addon effect");
        helper.assertTrue(CUSTOM_POINTS.get() == 7, "duplicate packets must not replay the registered action");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void shippedDivorcePapersUsesCommandOwnerOnlyOnFinalAcknowledgement(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.villager().setProfession(VillagerProfession.CLERIC);
        ResourceLocation eventId = ResourceLocation.parse("mca:gameplay/divorce_papers");
        helper.assertTrue(DialogueEvents.INSTANCE.get(eventId).isPresent(), "shipped divorce-papers event must load");
        DialogueEngine engine = new DialogueEngine(DialogueEvents.INSTANCE, RandomSource.create(1L));
        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(menu.containsEvent(eventId), "cleric must offer the shipped divorce-papers topic");

        DialogueEngine.DialogueNodeView terminal = engine.select(fixture.player(), menu.token(),
                DialogueEngine.DialogueSelection.EVENT, eventId).orElseThrow();
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).pendingEffects().size() == 1,
                "shipped routing must queue exactly one gameplay command");
        helper.assertTrue(fixture.player().getInventory().countItem(ItemsMCA.DIVORCE_PAPERS) == 0,
                "selecting the topic must not deliver papers before the final acknowledgement");

        helper.assertTrue(engine.advance(fixture.player(), terminal.offerToken()).completed(),
                "shipped divorce-papers event must finish through its command owner");
        helper.assertTrue(fixture.player().getInventory().countItem(ItemsMCA.DIVORCE_PAPERS) == 1,
                "the shipped topic must deliver exactly one set of divorce papers");
        helper.assertTrue(engine.advance(fixture.player(), terminal.offerToken()).status()
                        == DialogueEngine.TransitionStatus.REJECTED,
                "replayed final acknowledgement must be rejected");
        helper.assertTrue(fixture.player().getInventory().countItem(ItemsMCA.DIVORCE_PAPERS) == 1,
                "replayed acknowledgement must not create extra divorce papers");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void shippedHireShortChargesOnceAfterFinalAcknowledgement(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.villager().setProfession(ProfessionsMCA.ADVENTURER);
        fixture.player().getInventory().add(new ItemStack(Items.EMERALD, 10));
        ResourceLocation eventId = ResourceLocation.parse("mca:gameplay/hire");
        helper.assertTrue(DialogueEvents.INSTANCE.get(eventId).isPresent(), "shipped hire event must load");
        DialogueEngine engine = new DialogueEngine(DialogueEvents.INSTANCE, RandomSource.create(1L));
        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(menu.containsEvent(eventId), "adventurer must offer the shipped hire topic");

        DialogueEngine.DialogueNodeView choices = engine.select(fixture.player(), menu.token(),
                DialogueEngine.DialogueSelection.EVENT, eventId).orElseThrow();
        helper.assertTrue(choices.choices().stream().anyMatch(choice -> choice.id().equals("short")),
                "shipped hire choice must be offered to the paying player");
        DialogueEngine.DialogueNodeView terminal = engine.choose(fixture.player(), choices.offerToken(), "short")
                .view().orElseThrow();
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).pendingEffects().size() == 1,
                "affordable shipped hire must queue exactly one gameplay command");
        helper.assertTrue(fixture.player().getInventory().countItem(Items.EMERALD) == 10,
                "accepting hire must not charge before final acknowledgement");

        helper.assertTrue(engine.advance(fixture.player(), terminal.offerToken()).completed(),
                "shipped hire must finish through the existing hire command owner");
        helper.assertTrue(fixture.player().getInventory().countItem(Items.EMERALD) == 5,
                "shipped short hire must charge five emeralds exactly once");
        helper.assertTrue(fixture.villager().getProfession() == ProfessionsMCA.MERCENARY,
                "the shipped hire must update the existing mercenary profession");
        helper.assertTrue(fixture.villager().getDespawnDelay() == 24000 * 3,
                "short hire must set the existing three-day mercenary contract");
        helper.assertTrue(fixture.villager().getInventory().countItem(Items.IRON_SWORD) == 1,
                "shipped hire must give the mercenary its equipment exactly once");
        helper.assertTrue(engine.advance(fixture.player(), terminal.offerToken()).status()
                        == DialogueEngine.TransitionStatus.REJECTED,
                "replayed shipped hire acknowledgement must be rejected");
        helper.assertTrue(fixture.player().getInventory().countItem(Items.EMERALD) == 5,
                "replayed acknowledgement must not charge another five emeralds");
        helper.assertTrue(fixture.villager().getDespawnDelay() == 24000 * 3
                        && fixture.villager().getInventory().countItem(Items.IRON_SWORD) == 1,
                "replayed acknowledgement must not extend the contract or duplicate equipment");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void shippedHireLongChargesTenEmeraldsForSevenDays(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.villager().setProfession(ProfessionsMCA.ADVENTURER);
        fixture.player().getInventory().add(new ItemStack(Items.EMERALD, 10));
        ResourceLocation eventId = ResourceLocation.parse("mca:gameplay/hire");
        DialogueEngine engine = new DialogueEngine(DialogueEvents.INSTANCE, RandomSource.create(1L));
        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(menu.containsEvent(eventId), "shipped hire must be available to an adventurer");

        DialogueEngine.DialogueNodeView choices = engine.select(fixture.player(), menu.token(),
                DialogueEngine.DialogueSelection.EVENT, eventId).orElseThrow();
        helper.assertTrue(choices.choices().stream().anyMatch(choice -> choice.id().equals("long")),
                "the shipped long hire option must be offered");
        DialogueEngine.DialogueNodeView terminal = engine.choose(fixture.player(), choices.offerToken(), "long")
                .view().orElseThrow();
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).pendingEffects().size() == 1,
                "affordable long hire must queue exactly one command");
        helper.assertTrue(fixture.player().getInventory().countItem(Items.EMERALD) == 10,
                "long hire must not charge until final acknowledgement");

        helper.assertTrue(engine.advance(fixture.player(), terminal.offerToken()).completed(),
                "the shipped long hire must complete through the command owner");
        helper.assertTrue(fixture.player().getInventory().countItem(Items.EMERALD) == 0,
                "long hire must charge ten emeralds");
        helper.assertTrue(fixture.villager().getProfession() == ProfessionsMCA.MERCENARY
                        && fixture.villager().getDespawnDelay() == 24000 * 7,
                "long hire must create a seven-day mercenary contract");
        helper.assertTrue(fixture.villager().getInventory().countItem(Items.IRON_SWORD) == 1,
                "long hire must equip exactly one sword");
        helper.assertTrue(engine.advance(fixture.player(), terminal.offerToken()).status()
                        == DialogueEngine.TransitionStatus.REJECTED,
                "replaying the final acknowledgement must be rejected");
        helper.assertTrue(fixture.player().getInventory().countItem(Items.EMERALD) == 0
                        && fixture.villager().getDespawnDelay() == 24000 * 7,
                "replaying the acknowledgement must not charge or extend the contract");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void shippedHireLongWithoutTenEmeraldsHasNoSideEffects(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.villager().setProfession(ProfessionsMCA.ADVENTURER);
        fixture.player().getInventory().add(new ItemStack(Items.EMERALD, 5));
        ResourceLocation eventId = ResourceLocation.parse("mca:gameplay/hire");
        DialogueEngine engine = new DialogueEngine(DialogueEvents.INSTANCE, RandomSource.create(1L));
        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView choices = engine.select(fixture.player(), menu.token(),
                DialogueEngine.DialogueSelection.EVENT, eventId).orElseThrow();
        DialogueEngine.DialogueNodeView noMoney = engine.choose(fixture.player(), choices.offerToken(), "long")
                .view().orElseThrow();

        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).pendingEffects().isEmpty(),
                "unaffordable shipped long hire must not queue a command");
        helper.assertTrue(engine.advance(fixture.player(), noMoney.offerToken()).completed(),
                "the authored no-money response must finish cleanly");
        helper.assertTrue(fixture.player().getInventory().countItem(Items.EMERALD) == 5,
                "unaffordable long hire must not charge the player");
        helper.assertTrue(fixture.villager().getProfession() == ProfessionsMCA.ADVENTURER
                        && fixture.villager().getDespawnDelay() != 24000 * 7,
                "unaffordable long hire must not create a mercenary contract");
        helper.succeed();
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
    public static void locationCommandReusesRumorsOwnerAndCompletes(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = locationCommandStory(ResourceLocation.parse("mca:test/location_command"));
        DialogueEngine engine = engine(event);
        DialogueEventHistory history = DialogueEventHistory.get(fixture.player().serverLevel());
        List<String> structures = Config.getInstance().structuresInRumors;
        Config.getInstance().structuresInRumors = List.of();
        try {
            DialogueEngine.DialogueNodeView terminal = startAndChoose(engine, fixture, event);
            DialogueEngine.TransitionResult completed = engine.advance(fixture.player(), terminal.offerToken());

            helper.assertTrue(completed.status() == DialogueEngine.TransitionStatus.COMPLETED,
                    "location command must complete through its gameplay owner even though that owner keeps the screen open");
            helper.assertTrue(history.completed(fixture.player().getUUID(), fixture.villager().getUUID(), event.id()),
                    "successful location command completion must commit dialogue history");
        } finally {
            Config.getInstance().structuresInRumors = structures;
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void typedSlapDialogueCompletesBeforeHitAndCommitsHeartsOnce(GameTestHelper helper) {
        Fixture fixture = fixture(helper, true);
        fixture.villager().setNoAi(true);
        fixture.villager().getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        disableFakePlayerSpawnProtection(fixture.player());
        fixture.player().setHealth(20.0f);
        fixture.villager().getVillagerBrain().setPersonality(Personality.FRIENDLY);
        Memories memories = fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player());
        memories.setHearts(50);
        DialogueEvent event = DialogueEvent.decode(ResourceLocation.parse("mca:test/typed_slap_with_hearts"),
                JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{"mode":"ask","prompt":"test.prompt","resume_prompt":"test.resume"},
                  "repeat":{"type":"once"},"start":"start",
                  "nodes":{
                    "start":{"line":"test.start","choices":[{"id":"support","text":"test.support",
                      "actions":[{"type":"mca:slap","amount":1.0},
                                 {"type":"mca:hearts","amount":-5}],"next":"done"}]},
                    "done":{"line":"test.done","complete":true}
                  }
                }
                """).getAsJsonObject());
        DialogueEngine engine = engine(event);
        DialogueEventHistory history = DialogueEventHistory.get(fixture.player().serverLevel());

        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView start = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        DialogueEngine.TransitionResult completed = engine.choose(fixture.player(), start.offerToken(), "support");

        helper.assertTrue(completed.status() == DialogueEngine.TransitionStatus.COMPLETED,
                "typed slap dialogue must complete on the provoking choice");
        helper.assertTrue(history.completed(fixture.player().getUUID(), fixture.villager().getUUID(), event.id()),
                "typed slap completion must commit dialogue history");
        helper.assertTrue(fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player()).getHearts() == 45,
                "other authored effects must commit before the hit");
        helper.assertTrue(fixture.player().getHealth() == 20.0f, "typed slap must wait before damaging");
        helper.assertTrue(engine.choose(fixture.player(), start.offerToken(), "support").status()
                        == DialogueEngine.TransitionStatus.REJECTED,
                "replaying the typed slap choice must be rejected");

        helper.runAfterDelay(3, () -> {
            engine.tick(helper.getLevel().getServer());
            helper.assertTrue(fixture.player().getHealth() == 19.0f,
                    "typed slap must apply the authored one-damage hit");
            helper.assertTrue(fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player()).getHearts() == 45,
                    "heart effects must never be replayed");
            engine.tick(helper.getLevel().getServer());
            helper.assertTrue(fixture.player().getHealth() == 19.0f, "typed slap must never repeat");
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void slapRequiresCurrentInteractionOwnerBeforeCommittingEffects(GameTestHelper helper) {
        Fixture fixture = fixture(helper, true);
        fixture.villager().setNoAi(true);
        fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player()).setHearts(50);
        DialogueEvent event = DialogueEvent.decode(ResourceLocation.parse("mca:test/slap_owner_required"),
                JsonParser.parseString("""
                {
                  "trigger":"talk", "presentation":{"mode":"ask","prompt":"test.prompt","resume_prompt":"test.resume"},
                  "repeat":{"type":"once"}, "start":"start", "nodes":{
                    "start":{"line":"test.start","choices":[{"id":"support","text":"test.support",
                      "actions":[{"type":"mca:slap","amount":1.0},{"type":"mca:hearts","amount":-5}],
                      "next":"done"}]},
                    "done":{"line":"test.done","complete":true}
                  }
                }
                """).getAsJsonObject());
        DialogueEngine engine = engine(event);
        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView start = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        helper.assertTrue(engine.choose(fixture.player(), start.offerToken(), "support").status()
                        == DialogueEngine.TransitionStatus.REJECTED,
                "a terminal slap token without a concrete owned interaction must be rejected");
        helper.assertTrue(fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player()).getHearts() == 50,
                "unauthorized slap choice must not commit associated heart effects");
        helper.assertTrue(!DialogueEventHistory.get(fixture.player().serverLevel()).completed(
                        fixture.player().getUUID(), fixture.villager().getUUID(), event.id()),
                "unauthorized slap choice must not commit completion history");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void slapAdditionalActionMustNotCloseReplacementInteraction(GameTestHelper helper) {
        Fixture fixture = fixture(helper, true);
        fixture.villager().setNoAi(true);
        fixture.villager().getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID originalId = fixture.villager().getInteractions().interactionId();
        DialogueEvent event = DialogueEvent.decode(ResourceLocation.parse("mca:test/slap_replacement_owner"),
                JsonParser.parseString("""
                {
                  "trigger":"talk", "presentation":{"mode":"ask","prompt":"test.prompt","resume_prompt":"test.resume"},
                  "repeat":{"type":"once"}, "start":"start", "nodes":{
                    "start":{"line":"test.start","choices":[{"id":"support","text":"test.support",
                      "actions":[{"type":"mca:slap","amount":1.0},
                                 {"type":"dialogue_test:replace_interaction","replace":true}],
                      "next":"done"}]},
                    "done":{"line":"test.done","complete":true}
                  }
                }
                """).getAsJsonObject());
        DialogueEngine engine = engine(event);
        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView start = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        helper.assertTrue(engine.choose(fixture.player(), start.offerToken(), "support").completed(),
                "slap should still complete when a registered action replaces its interaction");
        UUID replacementId = fixture.villager().getInteractions().interactionId();
        helper.assertTrue(!originalId.equals(replacementId),
                "registered action must have opened a different interaction ID");
        helper.assertTrue(fixture.villager().getInteractions().matchesInteraction(fixture.player(), replacementId),
                "completion must not close the replacement interaction opened during effect commit");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void slapActionUsesEmptyHandAndVillagerDamageOnce(GameTestHelper helper) {
        Fixture fixture = fixture(helper, true);
        // This tests dialogue ownership rather than natural AI threat interruption.
        // Other GameTests may cause panic, legitimately closing the newer screen.
        fixture.villager().setNoAi(true);
        disableFakePlayerSpawnProtection(fixture.player());
        fixture.player().setHealth(20.0f);
        fixture.villager().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        DialogueEvent event = DialogueEvent.decode(ResourceLocation.parse("mca:test/slap_action"),
                JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{"mode":"ask","prompt":"test.prompt","resume_prompt":"test.resume"},
                  "repeat":{"type":"once"},"start":"start",
                  "nodes":{
                    "start":{"line":"test.start","choices":[{
                      "id":"support","text":"test.support",
                      "actions":[{"type":"mca:slap","amount":3.0}],"next":"done"}]},
                    "done":{"line":"test.done","complete":true}
                  }
                }
                """).getAsJsonObject());
        DialogueEngine engine = engine(event);
        DialogueEventHistory history = DialogueEventHistory.get(fixture.player().serverLevel());
        fixture.villager().getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID originalInteraction = fixture.villager().getInteractions().interactionId();
        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView start = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        DialogueEngine.TransitionResult completion = engine.choose(fixture.player(), start.offerToken(), "support");
        helper.assertTrue(completion.status() == DialogueEngine.TransitionStatus.COMPLETED,
                "provoking a terminal slap must finish immediately without a final acknowledgement");
        helper.assertTrue(history.completed(fixture.player().getUUID(), fixture.villager().getUUID(), event.id()),
                "slap dialogue history must be committed exactly when the choice is accepted");
        helper.assertTrue(fixture.player().getHealth() == 20.0f,
                "the accepted slap reply must not damage before the world screen closes");
        helper.assertTrue(!fixture.villager().getInteractions().matchesInteraction(
                        fixture.player(), originalInteraction),
                "slap must release the original interaction owner before the hit");
        helper.assertTrue(engine.choose(fixture.player(), start.offerToken(), "support").status()
                        == DialogueEngine.TransitionStatus.REJECTED,
                "replaying an accepted slap choice must be rejected");
        FakePlayer nextPlayer = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "next-slap-conversation"));
        nextPlayer.setPos(fixture.player().position());
        fixture.villager().getInteractions().interactAt(nextPlayer, Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID nextInteraction = fixture.villager().getInteractions().interactionId();
        helper.assertTrue(fixture.villager().getInteractions().matchesInteraction(nextPlayer, nextInteraction),
                "another player must be able to interact before the delayed hit");

        helper.runAfterDelay(2, () -> {
            engine.tick(helper.getLevel().getServer());
            helper.assertTrue(fixture.player().getHealth() == 20.0f,
                    "the deferred slap must not damage during its two-tick wait");
        });
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(fixture.villager().getInteractions().matchesInteraction(nextPlayer, nextInteraction),
                    "newer owner must still be present immediately before processing the pending slap"
                            + " (danger=" + fixture.villager().isConversationDangerous()
                            + ", distanceSq=" + fixture.villager().distanceToSqr(nextPlayer)
                            + ", nextAlive=" + nextPlayer.isAlive() + ")");
            // Reproduce vanilla's first-half swing gate regardless of unrelated AI animation timing.
            fixture.villager().swinging = true;
            fixture.villager().swingTime = 0;
            fixture.villager().swingingArm = InteractionHand.MAIN_HAND;
            helper.assertTrue(fixture.villager().getMainHandItem().is(Items.IRON_SWORD),
                    "the villager must still hold its sword when selecting the empty-hand slap");
            engine.tick(helper.getLevel().getServer());
            helper.assertTrue(fixture.player().getHealth() == 17.0f,
                    "authored slap must deal 3 damage once after the three-tick delay");
            helper.assertTrue(fixture.player().getLastDamageSource() != null
                            && fixture.player().getLastDamageSource().getEntity() == fixture.villager(),
                    "damage must be attributed to the slapping villager");
            helper.assertTrue(fixture.villager().swinging
                            && fixture.villager().swingingArm == InteractionHand.OFF_HAND,
                    "an occupied main hand must use the empty off hand for vanilla swing animation"
                            + " (swinging=" + fixture.villager().swinging
                            + ", arm=" + fixture.villager().swingingArm
                            + ", main=" + fixture.villager().getMainHandItem()
                            + ", off=" + fixture.villager().getOffhandItem() + ")");
            helper.assertTrue(fixture.villager().getMainHandItem().is(Items.IRON_SWORD)
                            && fixture.villager().getOffhandItem().isEmpty(),
                    "slapping must not disturb villager equipment");
            engine.tick(helper.getLevel().getServer());
            helper.assertTrue(fixture.player().getHealth() == 17.0f,
                    "a completed slap must never fire a second time");
            helper.assertTrue(fixture.villager().getInteractions().matchesInteraction(nextPlayer, nextInteraction),
                    "the delayed hit must not close a newer interaction owner");
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void disappearingSlapActorCancelsPendingDamage(GameTestHelper helper) {
        Fixture fixture = fixture(helper, true);
        fixture.villager().setNoAi(true);
        fixture.villager().getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        disableFakePlayerSpawnProtection(fixture.player());
        fixture.player().setHealth(20.0f);
        DialogueEvent event = slapStory(ResourceLocation.parse("mca:test/disappearing_slap"));
        DialogueEngine engine = engine(event);
        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView start = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();

        helper.assertTrue(engine.choose(fixture.player(), start.offerToken(), "support").completed(),
                "slap must be pending before the actor disappears");
        fixture.villager().discard();
        helper.runAfterDelay(3, () -> {
            engine.tick(helper.getLevel().getServer());
            helper.assertTrue(fixture.player().getHealth() == 20.0f,
                    "disappeared villager must never damage the player after cancellation");
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void disconnectedSlapPlayerCancelsPendingDamage(GameTestHelper helper) {
        Fixture fixture = fixture(helper, true);
        fixture.villager().setNoAi(true);
        fixture.villager().getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        disableFakePlayerSpawnProtection(fixture.player());
        fixture.player().setHealth(20.0f);
        DialogueEvent event = slapStory(ResourceLocation.parse("mca:test/disconnected_slap"));
        DialogueEngine engine = engine(event);
        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView start = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();

        helper.assertTrue(engine.choose(fixture.player(), start.offerToken(), "support").completed(),
                "slap must be queued before disconnect");
        fixture.player().disconnect();
        helper.runAfterDelay(3, () -> {
            engine.tick(helper.getLevel().getServer());
            helper.assertTrue(fixture.player().getHealth() == 20.0f,
                    "a disconnected player must not receive queued damage");
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void legacySlapCommandIsRejectedWithoutDamage(GameTestHelper helper) {
        Fixture fixture = fixture(helper, true);
        disableFakePlayerSpawnProtection(fixture.player());
        fixture.player().setHealth(20.0f);
        helper.assertTrue(!fixture.villager().getInteractions().handleDialogue(fixture.player(), "slap").accepted(),
                "removed legacy slap command must be rejected");
        helper.assertTrue(fixture.player().getHealth() == 20.0f,
                "removed legacy slap command must not deal damage");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void legacySlapDialogueCannotCommit(GameTestHelper helper) {
        Fixture fixture = fixture(helper, true);
        disableFakePlayerSpawnProtection(fixture.player());
        fixture.player().setHealth(20.0f);
        DialogueEvent event = commandStory(ResourceLocation.parse("mca:test/removed_legacy_slap"), "slap");
        DialogueEngine engine = engine(event);
        DialogueEventHistory history = DialogueEventHistory.get(fixture.player().serverLevel());
        DialogueEngine.DialogueNodeView terminal = startAndChoose(engine, fixture, event);

        helper.assertTrue(engine.advance(fixture.player(), terminal.offerToken()).status()
                        == DialogueEngine.TransitionStatus.ENDED,
                "legacy slap dialogue must fail closed at completion");
        helper.assertTrue(!history.completed(fixture.player().getUUID(), fixture.villager().getUUID(), event.id()),
                "rejected legacy slap must not write completion history");
        helper.runAfterDelay(3, () -> {
            engine.tick(helper.getLevel().getServer());
            helper.assertTrue(fixture.player().getHealth() == 20.0f,
                    "rejected legacy slap must not queue delayed damage");
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void rejectedProcreateCommandFailsClosed(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = commandStory(ResourceLocation.parse("mca:test/procreate_rejected"), "procreate");
        DialogueEngine engine = engine(event);
        DialogueEventHistory history = DialogueEventHistory.get(fixture.player().serverLevel());
        fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player()).setHearts(0);

        DialogueEngine.DialogueNodeView terminal = startAndChoose(engine, fixture, event);
        DialogueEngine.TransitionResult result = engine.advance(fixture.player(), terminal.offerToken());

        helper.assertTrue(result.status() == DialogueEngine.TransitionStatus.ENDED,
                "rejected procreate command must fail closed instead of committing dialogue completion");
        helper.assertTrue(!history.completed(fixture.player().getUUID(), fixture.villager().getUUID(), event.id()),
                "rejected procreate command must not write completion history");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void hireCommandRevalidatesEmeraldPaymentAtCommit(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = commandStory(ResourceLocation.parse("mca:test/hire_rejected"), "hire_short");
        DialogueEngine engine = engine(event);
        DialogueEventHistory history = DialogueEventHistory.get(fixture.player().serverLevel());

        DialogueEngine.DialogueNodeView terminal = startAndChoose(engine, fixture, event);
        DialogueEngine.TransitionResult result = engine.advance(fixture.player(), terminal.offerToken());

        helper.assertTrue(result.status() == DialogueEngine.TransitionStatus.ENDED,
                "hire must fail closed when the required emerald payment is unavailable at commit");
        helper.assertTrue(!history.completed(fixture.player().getUUID(), fixture.villager().getUUID(), event.id()),
                "unpaid hire must not write completion history");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void successfulHireChargesExactlyOnce(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.villager().setProfession(ProfessionsMCA.ADVENTURER);
        DialogueEvent event = commandStory(ResourceLocation.parse("mca:test/hire_success"), "hire_short");
        DialogueEngine engine = engine(event);
        fixture.player().getInventory().add(new ItemStack(Items.EMERALD, 10));

        DialogueEngine.DialogueNodeView terminal = startAndChoose(engine, fixture, event);
        DialogueEngine.TransitionResult completed = engine.advance(fixture.player(), terminal.offerToken());

        helper.assertTrue(completed.status() == DialogueEngine.TransitionStatus.COMPLETED,
                "paid hire must complete through the gameplay command owner");
        helper.assertTrue(fixture.player().getInventory().countItem(Items.EMERALD) == 5,
                "hire_short must charge exactly five emeralds once");
        helper.assertTrue(engine.advance(fixture.player(), terminal.offerToken()).status()
                        == DialogueEngine.TransitionStatus.REJECTED,
                "consumed hire token must reject replay");
        helper.assertTrue(fixture.player().getInventory().countItem(Items.EMERALD) == 5,
                "replayed hire token must not charge the owner twice");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void changedMarriageRejectsProcreationBeforeFinalCommit(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.villager().getRelationships().marry(fixture.player());
        fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player()).setHearts(150);
        DialogueEvent event = commandStory(ResourceLocation.parse("mca:test/procreate_lost_marriage"), "procreate");
        DialogueEngine engine = engine(event);
        DialogueEngine.DialogueNodeView terminal = startAndChoose(engine, fixture, event);
        FakePlayer other = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "new-spouse"));
        fixture.villager().getRelationships().marry(other);
        helper.assertTrue(fixture.villager().getRelationships().isMarried()
                        && !fixture.villager().getRelationships().isMarriedTo(fixture.player().getUUID()),
                "the villager must be married to someone other than the initiating player");

        helper.assertTrue(engine.advance(fixture.player(), terminal.offerToken()).status()
                        == DialogueEngine.TransitionStatus.ENDED,
                "procreation must reject when the initiating player is no longer the spouse");
        helper.assertTrue(!DialogueEventHistory.get(fixture.player().serverLevel()).completed(
                        fixture.player().getUUID(), fixture.villager().getUUID(), event.id()),
                "rejected procreation must leave completion history untouched");
        helper.assertTrue(!fixture.villager().getRelationships().isProcreating(),
                "a stale procreation acknowledgement must not start pregnancy behavior");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void competingHireRejectsChangedProfessionBeforeCharging(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.villager().setProfession(ProfessionsMCA.ADVENTURER);
        fixture.player().getInventory().add(new ItemStack(Items.EMERALD, 10));
        DialogueEvent event = commandStory(ResourceLocation.parse("mca:test/competing_hire"), "hire_short");
        DialogueEngine engine = engine(event);
        DialogueEngine.DialogueNodeView terminal = startAndChoose(engine, fixture, event);
        FakePlayer other = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "competing-hirer"));
        other.getInventory().add(new ItemStack(Items.EMERALD, 10));
        helper.assertTrue(fixture.villager().getInteractions().handleDialogue(other, "hire_long").accepted(),
                "another player's paid hire must succeed before the first player acknowledges");
        helper.assertTrue(other.getInventory().countItem(Items.EMERALD) == 0,
                "the competing player must pay for the seven-day hire");
        int swordCount = fixture.villager().getInventory().countItem(Items.IRON_SWORD);
        int despawnDelay = fixture.villager().getDespawnDelay();

        helper.assertTrue(engine.advance(fixture.player(), terminal.offerToken()).status()
                        == DialogueEngine.TransitionStatus.ENDED,
                "a stale hiring decision cannot rehire a mercenary");
        helper.assertTrue(fixture.player().getInventory().countItem(Items.EMERALD) == 10,
                "a rejected hire must not charge the player");
        helper.assertTrue(fixture.villager().getProfession() == ProfessionsMCA.MERCENARY,
                "the successful competing hire must remain in effect");
        helper.assertTrue(swordCount == 1 && fixture.villager().getInventory().countItem(Items.IRON_SWORD) == 1,
                "rejecting the stale hire must not duplicate mercenary equipment");
        helper.assertTrue(despawnDelay == 24000 * 7 && fixture.villager().getDespawnDelay() == despawnDelay,
                "rejecting the stale hire must not change the competing contract");
        helper.assertTrue(!DialogueEventHistory.get(fixture.player().serverLevel()).completed(
                        fixture.player().getUUID(), fixture.villager().getUUID(), event.id()),
                "rejected competing hire must not write completion history");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void fullInventoryRejectsDivorcePapersCompletion(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        for (int slot = 0; slot < fixture.player().getInventory().getContainerSize(); slot++) {
            fixture.player().getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        DialogueEvent event = commandStory(ResourceLocation.parse("mca:test/full_divorce_papers"), "divorcePapers");
        DialogueEngine engine = engine(event);
        DialogueEngine.DialogueNodeView terminal = startAndChoose(engine, fixture, event);

        helper.assertTrue(engine.advance(fixture.player(), terminal.offerToken()).status()
                        == DialogueEngine.TransitionStatus.ENDED,
                "no room for divorce papers must not complete the command");
        helper.assertTrue(!fixture.player().getInventory().contains(ItemsMCA.DIVORCE_PAPERS.getDefaultInstance()),
                "full inventory must remain free of undelivered divorce papers");
        helper.assertTrue(!DialogueEventHistory.get(fixture.player().serverLevel()).completed(
                        fixture.player().getUUID(), fixture.villager().getUUID(), event.id()),
                "undelivered papers must not write completion history");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void creativeFullInventoryRejectsUndeliveredDivorcePapers(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        for (int slot = 0; slot < fixture.player().getInventory().getContainerSize(); slot++) {
            fixture.player().getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        // Vanilla Inventory.add() reports success for creative players even when no slot accepted the item.
        fixture.player().getAbilities().instabuild = true;
        DialogueEvent event = commandStory(ResourceLocation.parse("mca:test/creative_full_divorce_papers"), "divorcePapers");
        DialogueEngine engine = engine(event);
        DialogueEngine.DialogueNodeView terminal = startAndChoose(engine, fixture, event);

        helper.assertTrue(engine.advance(fixture.player(), terminal.offerToken()).status()
                        == DialogueEngine.TransitionStatus.ENDED,
                "creative inventory insertion must not count as delivery if no slot accepted the papers");
        helper.assertTrue(fixture.player().getInventory().countItem(ItemsMCA.DIVORCE_PAPERS) == 0,
                "failed creative delivery must not generate divorce papers");
        helper.assertTrue(!DialogueEventHistory.get(fixture.player().serverLevel()).completed(
                        fixture.player().getUUID(), fixture.villager().getUUID(), event.id()),
                "creative insertion without actual delivery must not commit dialogue history");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void availableInventorySlotDeliversDivorcePapersExactlyOnce(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        // Slot 0 is ordinary inventory space. The final container slots include
        // armor/offhand, which Inventory.add() does not use for generic items.
        for (int slot = 1; slot < fixture.player().getInventory().getContainerSize(); slot++) {
            fixture.player().getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        DialogueEvent event = commandStory(ResourceLocation.parse("mca:test/delivered_divorce_papers"), "divorcePapers");
        DialogueEngine engine = engine(event);
        DialogueEngine.DialogueNodeView terminal = startAndChoose(engine, fixture, event);

        helper.assertTrue(engine.advance(fixture.player(), terminal.offerToken()).status()
                        == DialogueEngine.TransitionStatus.COMPLETED,
                "divorce papers must complete if the player has a free inventory slot");
        helper.assertTrue(fixture.player().getInventory().countItem(ItemsMCA.DIVORCE_PAPERS) == 1,
                "the command must deliver exactly one divorce-paper item");
        helper.assertTrue(engine.advance(fixture.player(), terminal.offerToken()).status()
                        == DialogueEngine.TransitionStatus.REJECTED,
                "a consumed completion token cannot replay the item delivery");
        helper.assertTrue(fixture.player().getInventory().countItem(ItemsMCA.DIVORCE_PAPERS) == 1,
                "a stale acknowledgement must not grant duplicate papers");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void completingCommandCannotCloseAnotherPlayersInteraction(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = commandStory(ResourceLocation.parse("mca:test/multiplayer_command_close"), "stay_in_village");
        DialogueEngine engine = engine(event);
        DialogueEngine.DialogueNodeView terminal = startAndChoose(engine, fixture, event);
        FakePlayer other = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "other-command-player"));
        fixture.villager().getInteractions().interactAt(other, Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID otherInteraction = fixture.villager().getInteractions().interactionId();

        helper.assertTrue(engine.advance(fixture.player(), terminal.offerToken()).status()
                        == DialogueEngine.TransitionStatus.COMPLETED,
                "the initiating player's valid command should complete");
        helper.assertTrue(fixture.villager().getInteractions().matchesInteraction(other, otherInteraction),
                "one player's completion must not close a different player's active interaction");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void adoptionRejectsWhenAnotherParentAdoptsBeforeAcknowledgement(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent event = commandStory(ResourceLocation.parse("mca:test/adopt_changed"), "adopt");
        DialogueEngine engine = engine(event);
        DialogueEngine.DialogueNodeView terminal = startAndChoose(engine, fixture, event);

        FakePlayer other = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "other-adopter"));
        FamilyTree family = FamilyTree.get(helper.getLevel());
        FamilyTreeNode otherParent = family.getOrCreate(other);
        FamilyTreeNode child = fixture.villager().getRelationships().getFamilyEntry();
        child.replaceParents(otherParent, Optional.empty());
        helper.assertTrue(!family.isOrphan(child), "fixture must have a living adoptive parent");

        DialogueEngine.TransitionResult result = engine.advance(fixture.player(), terminal.offerToken());
        helper.assertTrue(result.status() == DialogueEngine.TransitionStatus.ENDED,
                "adoption must be rejected when the child gains a living parent before commit");
        helper.assertTrue(child.streamParents().anyMatch(otherParent.id()::equals),
                "rejected adoption must preserve the existing parent");
        helper.assertTrue(!DialogueEventHistory.get(fixture.player().serverLevel()).completed(
                        fixture.player().getUUID(), fixture.villager().getUUID(), event.id()),
                "rejected adoption cannot create completion history");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_actions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void divorceRejectsWhenMarriageEndsBeforeAcknowledgement(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.villager().getRelationships().marry(fixture.player());
        DialogueEvent event = commandStory(ResourceLocation.parse("mca:test/divorce_changed"), "divorceConfirm");
        DialogueEngine engine = engine(event);
        DialogueEngine.DialogueNodeView terminal = startAndChoose(engine, fixture, event);

        fixture.villager().getRelationships().endRelationShip(RelationshipState.SINGLE);
        Memories memories = fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player());
        int heartsBefore = memories.getHearts();
        DialogueEngine.TransitionResult result = engine.advance(fixture.player(), terminal.offerToken());
        helper.assertTrue(result.status() == DialogueEngine.TransitionStatus.ENDED,
                "divorce must be rejected if this player is no longer the spouse at commit");
        helper.assertTrue(memories.getHearts() == heartsBefore,
                "rejected divorce must not deduct relationship hearts");
        helper.assertTrue(!DialogueEventHistory.get(fixture.player().serverLevel()).completed(
                        fixture.player().getUUID(), fixture.villager().getUUID(), event.id()),
                "rejected divorce cannot create completion history");
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
        return fixture(helper, false);
    }

    private static Fixture fixture(GameTestHelper helper, boolean damageablePlayer) {
        BlockPos position = helper.absolutePos(BlockPos.ZERO);
        GameProfile profile = new GameProfile(UUID.randomUUID(), "dialogue-actions");
        ServerPlayer player = damageablePlayer ? new FakePlayer(helper.getLevel(), profile) {
            @Override
            public boolean isInvulnerableTo(DamageSource source) {
                // NeoForge FakePlayer is permanently invulnerable by default.
                return false;
            }
        } : new FakePlayer(helper.getLevel(), profile);
        player.setPos(position.getCenter());
        VillagerEntityMCA villager = Objects.requireNonNull(EntitiesMCA.MALE_VILLAGER.create(helper.getLevel()));
        villager.setAgeState(AgeState.ADULT);
        villager.setPos(player.getX() + 1.0D, player.getY(), player.getZ());
        helper.getLevel().addFreshEntity(villager);
        return new Fixture(player, villager);
    }

    private static void disableFakePlayerSpawnProtection(ServerPlayer player) {
        // FakePlayers used in dialogue fixtures are not ticked; their vanilla 60-tick
        // spawn invulnerability would otherwise suppress every normal mob attack.
        try {
            Field field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            field.setAccessible(true);
            field.setInt(player, 0);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not disable FakePlayer spawn protection", exception);
        }
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

    private static DialogueEvent locationCommandStory(ResourceLocation id) {
        return DialogueEvent.decode(id, JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{"mode":"ask","prompt":"dialogue.test.prompt","resume_prompt":"dialogue.test.resume"},
                  "repeat":{"type":"once"},
                  "start":"start",
                  "nodes":{
                    "start":{"line":"dialogue.test.start","choices":[{
                      "id":"support","text":"dialogue.test.support",
                      "actions":[{"type":"mca:command","command":"location"}],
                      "next":"done"
                    }]},
                    "done":{"line":"dialogue.test.done","complete":true}
                  }
                }
                """).getAsJsonObject());
    }

    private static DialogueEvent commandStory(ResourceLocation id, String command) {
        return DialogueEvent.decode(id, JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{"mode":"ask","prompt":"dialogue.test.prompt","resume_prompt":"dialogue.test.resume"},
                  "repeat":{"type":"once"},
                  "start":"start",
                  "nodes":{
                    "start":{"line":"dialogue.test.start","choices":[{
                      "id":"support","text":"dialogue.test.support",
                      "actions":[{"type":"mca:command","command":"%s"}],
                      "next":"done"
                    }]},
                    "done":{"line":"dialogue.test.done","complete":true}
                  }
                }
                """.formatted(command)).getAsJsonObject());
    }

    private static DialogueEvent slapStory(ResourceLocation id) {
        return DialogueEvent.decode(id, JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{"mode":"ask","prompt":"dialogue.test.prompt","resume_prompt":"dialogue.test.resume"},
                  "repeat":{"type":"once"},
                  "start":"start",
                  "nodes":{
                    "start":{"line":"dialogue.test.start","choices":[{
                      "id":"support","text":"dialogue.test.support",
                      "actions":[{"type":"mca:slap","amount":1.0}],
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
