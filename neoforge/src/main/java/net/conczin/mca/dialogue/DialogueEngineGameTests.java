package net.conczin.mca.dialogue;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import io.netty.buffer.Unpooled;
import com.mojang.authlib.GameProfile;
import net.conczin.mca.MCA;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.conczin.mca.entity.interaction.InteractionMenuCommandAccess;
import net.conczin.mca.entity.ai.ActivitiesMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.network.c2s.InteractionCloseRequest;
import net.conczin.mca.network.c2s.InteractionDialogueAdvanceMessage;
import net.conczin.mca.network.c2s.InteractionDialogueChoiceMessage;
import net.conczin.mca.network.c2s.InteractionDialogueLeaveMessage;
import net.conczin.mca.network.c2s.InteractionDialogueSelectMessage;
import net.conczin.mca.network.c2s.InteractionVillagerMessage;
import net.conczin.mca.network.c2s.GetInteractDataRequest;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.GetInteractDataResponse;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.registry.ItemsMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.resources.DialogueEvents;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.DialogueEventHistory;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@PrefixGameTestTemplate(false)
public final class DialogueEngineGameTests {
    private static final Set<Entity> AI_ACTORS = new LinkedHashSet<>();
    private static final Set<ChunkPos> AI_FORCED_CHUNKS = new LinkedHashSet<>();

    @AfterBatch(batch = "mca_dialogue_ai")
    public static void cleanUpDialogueAi(ServerLevel level) {
        AI_ACTORS.forEach(Entity::discard);
        AI_ACTORS.clear();
        AI_FORCED_CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
        AI_FORCED_CHUNKS.clear();
    }

    private DialogueEngineGameTests() {
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void browsingTopicsDoesNotReplayAmbientIntroduction(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent opening = DialogueEvent.decode(ResourceLocation.parse("mca:test/intro_then_topics"),
                JsonParser.parseString("""
                        {
                          "trigger":"talk",
                          "presentation":{"mode":"ambient","resume_prompt":"dialogue.test.resume"},
                          "repeat":{"type":"always"},
                          "start":"intro",
                          "nodes":{
                            "intro":{"lines":["dialogue.test.one","dialogue.test.two"],"complete":true}
                          }
                        }
                        """).getAsJsonObject());
        DialogueEngine engine = engine(opening);
        DialogueEngine.DialogueOptions initial = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(initial.preview().isPresent(), "initial Talk should include an ambient introduction");

        DialogueEngine.DialogueOptions topics = engine.begin(fixture.player(), fixture.villager(), false);
        helper.assertTrue(topics.preview().isEmpty(), "Topics must not immediately start another ambient line");
        helper.assertTrue(topics.ambientAvailable(), "manual ambient conversations should remain selectable");
        helper.assertTrue(topics.ambientCandidates().contains(opening.id()),
                "disabling automatic greetings must not hide the ambient event itself");
        helper.assertTrue(engine.advance(fixture.player(), initial.token()).status()
                        == DialogueEngine.TransitionStatus.REJECTED,
                "opening Topics must invalidate the prior preview token");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void clickingSleepingVillagerWakesBeforeDialogue(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent chat = simpleEvent(ResourceLocation.parse("mca:test/sleeping_chat"));
        DialogueEngine engine = engine(chat);
        fixture.villager().startSleeping(fixture.villager().blockPosition());
        helper.assertTrue(fixture.villager().isSleeping(), "fixture villager must be sleeping before click");
        fixture.villager().getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        helper.assertTrue(!fixture.villager().isSleeping(), "an accepted right-click must wake the villager immediately");

        DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager(), false);
        helper.assertTrue(menu.containsEvent(chat.id()), "an awake villager should offer Chat");
        helper.assertTrue(engine.select(fixture.player(), menu.token(),
                        DialogueEngine.DialogueSelection.EVENT, chat.id()).isPresent(),
                "a newly woken villager should accept the offered Chat topic");
        fixture.villager().discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void openingInventoryReleasesWholeInteractMenuOwner(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        VillagerEntityMCA villager = fixture.villager();
        var interactions = villager.getInteractions();
        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID menuId = interactions.interactionId();
        helper.assertTrue(interactions.matchesInteraction(fixture.player(), menuId),
                "the Interact menu must be owned before switching to inventory");
        helper.assertTrue(villager.getBrain().isActive(ActivitiesMCA.INTERACT),
                "the full Interact menu must own the villager's brain activity");

        interactions.handle(fixture.player(), "inventory");
        helper.assertTrue(interactions.getInteractingPlayer().isEmpty(),
                "replacing the whole Interact screen with inventory must release its owner");
        helper.assertTrue(!villager.getBrain().isActive(ActivitiesMCA.INTERACT),
                "inventory screen must not leave the villager in conversation activity");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 150)
    public static void activeDialogueSuspendsChoreForEightyTicks(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        VillagerEntityMCA villager = fixture.villager();
        // This air-only template has no floor. Keep the owner nearby so the
        // test measures chore preemption, not legitimate out-of-range cleanup.
        villager.setNoGravity(true);
        villager.getVillagerBrain().assignJob(Chore.PROSPECT, fixture.player());
        villager.getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == Chore.PROSPECT,
                "talking must not discard an assigned chore");

        helper.runAfterDelay(80, () -> {
            helper.assertTrue(villager.getInteractions().getInteractingPlayer().filter(fixture.player()::equals).isPresent(),
                    "dialogue owner must remain valid throughout normal AI ticks; villager=" + villager.position()
                            + ", player=" + fixture.player().position()
                            + ", distance=" + villager.distanceToSqr(fixture.player())
                            + ", villagerAlive=" + villager.isAlive()
                            + ", villagerRemoved=" + villager.isRemoved()
                            + ", playerAlive=" + fixture.player().isAlive()
                            + ", playerRemoved=" + fixture.player().isRemoved()
                            + ", panic=" + villager.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.PANIC)
                            + ", attackTarget=" + villager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                            + ", activity=" + villager.getBrain().getActiveNonCoreActivity());
            helper.assertTrue(!villager.getBrain().isActive(ActivitiesMCA.CHORE),
                    "an active conversation must preempt the chore activity without deleting its assignment");
            helper.assertTrue(villager.getBrain().isActive(ActivitiesMCA.INTERACT),
                    "the dedicated conversation activity must remain active across normal AI ticks");
            helper.assertTrue(!villager.isSleeping(), "an actively talking villager cannot start sleeping");
            helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == Chore.PROSPECT,
                    "the assigned chore must survive an active conversation");
            villager.discard();
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_dialogue_ai", templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 460)
    public static void dialogueMaintainsEyeContactAcrossLookTaskTimeouts(GameTestHelper helper) {
        Fixture fixture = dialogueAiFixture(helper);
        VillagerEntityMCA villager = fixture.villager();
        villager.getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        Vec3 playerStart = fixture.player().position();
        helper.runAfterDelay(140, () -> fixture.player().setPos(playerStart.add(-1.0D, 0, 0)));
        helper.runAfterDelay(280, () -> fixture.player().setPos(playerStart.add(1.0D, 0, 0)));
        float[] previousHead = {villager.yHeadRot};
        for (int tick = 40; tick <= 420; tick++) {
            int observedTick = tick;
            helper.runAfterDelay(tick, () -> {
                helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                                .map(visible -> visible.contains(fixture.player())).orElse(false),
                        "the gaze fixture must expose its real player to native visibility sensing");
                double dx = fixture.player().getX() - villager.getX();
                double dz = fixture.player().getZ() - villager.getZ();
                float bearing = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
                float error = Math.abs(Mth.wrapDegrees(villager.yHeadRot - bearing));
                boolean turning = observedTick >= 140 && observedTick < 160
                        || observedTick >= 280 && observedTick < 300;
                helper.assertTrue(turning || error < 15.0F,
                        "conversation lost actual eye contact at tick " + observedTick + "; error=" + error
                                + "; activity=" + villager.getBrain().getActiveNonCoreActivity());
                if (observedTick > 40) {
                    helper.assertTrue(Math.abs(Mth.wrapDegrees(villager.yHeadRot - previousHead[0]))
                                    <= villager.getHeadRotSpeed() + 0.01F,
                            "conversation head movement must respect native turn speed at tick " + observedTick);
                }
                previousHead[0] = villager.yHeadRot;
                if (observedTick >= 80 && !(observedTick >= 140 && observedTick < 180)
                        && !(observedTick >= 280 && observedTick < 320)) {
                    helper.assertTrue(Math.abs(Mth.wrapDegrees(villager.yBodyRot - bearing)) < 15.0F,
                            "stationary body must settle toward the player after native head tracking");
                }
                helper.assertTrue(villager.getBrain().isActive(ActivitiesMCA.INTERACT),
                        "conversation must keep its activity throughout native look-task timeouts");
                if (observedTick == 420) helper.succeed();
            });
        }
    }

    @GameTest(batch = "mca_dialogue_ai", templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 100)
    public static void brainRefreshPreservesActiveConversation(GameTestHelper helper) {
        Fixture fixture = dialogueAiFixture(helper);
        VillagerEntityMCA villager = fixture.villager();
        villager.getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID interactionId = villager.getInteractions().interactionId();
        helper.runAfterDelay(30, () -> {
            villager.refreshBrain(helper.getLevel());
            helper.assertTrue(villager.getInteractions().matchesInteraction(fixture.player(), interactionId),
                    "brain refresh must preserve the concrete screen owner");
            helper.assertTrue(villager.getBrain().isActive(ActivitiesMCA.INTERACT),
                    "profession/age brain refresh must restore conversation ownership before scheduled AI runs");
            helper.runAfterDelay(30, () -> {
                helper.assertTrue(villager.getBrain().isActive(ActivitiesMCA.INTERACT),
                        "refreshed conversation must remain active across further AI ticks");
                helper.succeed();
            });
        });
    }

    private static Fixture dialogueAiFixture(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(new BlockPos(16, 1, 16));
        ChunkPos min = new ChunkPos(base.offset(-5, 0, -5));
        ChunkPos max = new ChunkPos(base.offset(5, 0, 5));
        for (int x = min.x; x <= max.x; x++) {
            for (int z = min.z; z <= max.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!helper.getLevel().getForcedChunks().contains(chunk.toLong())) AI_FORCED_CHUNKS.add(chunk);
            }
        }
        net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea(helper, base, 5, 4);
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "dialogue-ai"));
        player.setPos(base.getX() + 0.5D, base.getY(), base.getZ() + 0.5D);
        player.setYRot(0.0F);
        helper.getLevel().addNewPlayer(player);
        AI_ACTORS.add(player);
        VillagerEntityMCA villager = Objects.requireNonNull(EntitiesMCA.MALE_VILLAGER.create(helper.getLevel()));
        villager.setAgeState(AgeState.ADULT);
        villager.setPos(player.getX(), player.getY(), player.getZ() + 2.0D);
        helper.getLevel().addFreshEntity(villager);
        AI_ACTORS.add(villager);
        return new Fixture(player, villager);
    }

    @GameTest(batch = "mca_dialogue_ai", templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void dialogueWalksToFrontOfPlayersView(GameTestHelper helper) {
        Fixture fixture = dialogueAiFixture(helper);
        VillagerEntityMCA villager = fixture.villager();
        Vec3 playerPos = fixture.player().position();
        fixture.player().setYRot(0.0F); // Faces +Z; the target is in front, not at the player's feet.
        fixture.player().setXRot(0.0F);
        villager.setPos(playerPos.add(4.0D, 0, 0));
        villager.getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);

        helper.runAfterDelay(110, () -> {
            Vec3 fromPlayer = villager.position().subtract(playerPos);
            helper.assertTrue(fromPlayer.z >= 1.35D && fromPlayer.z <= 3.85D,
                    "dialogue approach must reach 2-3 blocks along the player's look direction, got " + fromPlayer
                            + "; owner=" + villager.getInteractions().getInteractingPlayer()
                            + "; activity=" + villager.getBrain().getActiveNonCoreActivity()
                            + "; walk=" + villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                            + "; path=" + villager.getNavigation().getPath()
                            + "; navDone=" + villager.getNavigation().isDone()
                            + "; look=" + fixture.player().getLookAngle()
                            + "; danger=" + villager.isConversationDangerous());
            helper.assertTrue(Math.abs(fromPlayer.x) < 1.45D,
                    "the villager must approach the view vector instead of standing beside the player: " + fromPlayer);
            helper.assertTrue(villager.getBrain().isActive(ActivitiesMCA.INTERACT),
                    "arrival must keep the dialogue activity active");
            float bearing = (float) Math.toDegrees(Math.atan2(-fromPlayer.z, -fromPlayer.x)) - 90.0F;
            helper.assertTrue(Math.abs(Mth.wrapDegrees(villager.yHeadRot - bearing)) < 15.0F,
                    "the villager must face the player after finishing its approach");
            villager.discard();
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void attackTargetImmediatelyReleasesDialogueOwner(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        VillagerEntityMCA villager = fixture.villager();
        villager.getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        helper.assertTrue(villager.getBrain().isActive(ActivitiesMCA.INTERACT),
                "the interaction activity must be active before combat starts");
        villager.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, fixture.player());
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(villager.getInteractions().getInteractingPlayer().isEmpty(),
                    "the first defensive AI tick must release dialogue ownership");
            helper.assertTrue(!villager.getBrain().isActive(ActivitiesMCA.INTERACT),
                    "combat must not leave dialogue as the active Brain activity");
            villager.discard();
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void closingDialogueRestoresAssignedChoreImmediately(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        VillagerEntityMCA villager = fixture.villager();
        villager.getVillagerBrain().assignJob(Chore.PROSPECT, fixture.player());
        villager.getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        helper.assertTrue(villager.getBrain().isActive(ActivitiesMCA.INTERACT), "opening must activate dialogue");
        villager.getInteractions().stopInteracting(fixture.player(), villager.getInteractions().interactionId());
        helper.assertTrue(villager.getBrain().isActive(ActivitiesMCA.CHORE),
                "normal close must restore the assigned chore without waiting for the next schedule tick");
        helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == Chore.PROSPECT,
                "closing dialogue must preserve assigned chore identity");
        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void walkingOutOfDialogueRangeClosesOwner(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var interactions = fixture.villager().getInteractions();
        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        fixture.player().setPos(fixture.player().position().add(10.0D, 0, 0));
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(interactions.getInteractingPlayer().isEmpty(),
                    "moving out of usable dialogue range must release server ownership");
            helper.assertTrue(!fixture.villager().getBrain().isActive(ActivitiesMCA.INTERACT),
                    "out-of-range dialogue must not retain the conversation activity");
            fixture.villager().discard();
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void discardedVillagerReleasesDialogueOwner(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var interactions = fixture.villager().getInteractions();
        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        fixture.villager().discard();
        helper.assertTrue(interactions.getInteractingPlayer().isEmpty(),
                "removing a villager must release its dialogue owner without waiting for another AI tick");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void automaticAmbientOpeningEngagesOnceAndPreservesPausedRun(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.villager().getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        DialogueEvent event = DialogueEvent.decode(ResourceLocation.parse("mca:test/preview_passages"),
                JsonParser.parseString("""
                        {
                          "trigger":"talk",
                          "presentation":{"mode":"ambient","resume_prompt":"dialogue.test.resume"},
                          "repeat":{"type":"always"},
                          "start":"intro",
                          "nodes":{
                            "intro":{"lines":["dialogue.test.one","dialogue.test.two"],"next":"done"},
                            "done":{"line":"dialogue.test.done","complete":true}
                          }
                        }
                        """).getAsJsonObject());
        DialogueEngine engine = engine(event, ambientEvent(
                ResourceLocation.parse("mca:test/preview_other_topic"), -10, 1.0D));
        UUID playerId = fixture.player().getUUID();
        DialogueEngine.DialogueOptions offer = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(offer.preview().map(p -> p.eventId().equals(event.id())).orElse(false),
                "Talk should pin the eligible ambient opening");
        helper.assertTrue(offer.ambientAvailable(),
                "another eligible lower-priority ambient topic must remain selectable");
        helper.assertTrue(!sessions(engine).containsKey(playerId),
                "showing the first line must not start a story session");
        helper.assertTrue(engine.advance(fixture.player(), offer.token() + 1).status()
                        == DialogueEngine.TransitionStatus.REJECTED,
                "forged preview token cannot advance a story");

        DialogueEngine.TransitionResult firstAdvance = engine.advance(fixture.player(), offer.token());
        helper.assertTrue(firstAdvance.status() == DialogueEngine.TransitionStatus.ADVANCED,
                "a valid Next should start the pinned story at its second line");
        DialogueEngine.DialogueNodeView second = firstAdvance.view().orElseThrow();
        helper.assertTrue(sessions(engine).get(playerId).lineIndex() == 1,
                "the first passage must not be repeated");
        helper.assertTrue(engine.advance(fixture.player(), offer.token()).status()
                        == DialogueEngine.TransitionStatus.REJECTED,
                "consumed menu tokens must not advance again");

        DialogueEngine.DialogueOptions topics = engine.begin(fixture.player(), fixture.villager());
        DialogueSession paused = sessions(engine).get(playerId);
        helper.assertTrue(paused.status() == DialogueSession.Status.PAUSED,
                "opening Topics must pause the active story");
        helper.assertTrue(topics.continuationSessionId().filter(second.sessionId()::equals).isPresent(),
                "Topics must preserve the event-specific continuation");
        long originalDeadline = paused.pauseDeadline();
        DialogueEngine.DialogueOptions reopened = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(sessions(engine).get(playerId).pauseDeadline() == originalDeadline,
                "browsing Topics may not extend the existing pause deadline");
        DialogueEngine.DialogueNodeView resumed = engine.select(fixture.player(), reopened.token(),
                DialogueEngine.DialogueSelection.RESUME, null).orElseThrow();
        helper.assertTrue(resumed.sessionId().equals(second.sessionId())
                        && sessions(engine).get(playerId).lineIndex() == 1,
                "resuming must restore the existing session and second line");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void fatigueConfigControlsRealHeartChangeAccumulation(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var brain = fixture.villager().getVillagerBrain();
        var memories = brain.getMemoriesForPlayer(fixture.player());
        Config config = Config.getInstance();
        boolean enabled = config.interactionFatigueEnabled;
        float multiplier = config.interactionChanceFatigue;
        try {
            memories.setHearts(0);
            memories.setInteractionFatigue(0);
            config.interactionFatigueEnabled = false;
            brain.rewardHearts(fixture.player(), 3);
            memories = brain.getMemoriesForPlayer(fixture.player());
            helper.assertTrue(memories.getHearts() == 3 && memories.getInteractionFatigue() == 0,
                    "disabled fatigue must not suppress ordinary heart changes or add fatigue");

            memories.setInteractionFatigue(6);
            brain.rewardHearts(fixture.player(), -1);
            memories = brain.getMemoriesForPlayer(fixture.player());
            helper.assertTrue(memories.getInteractionFatigue() == 6,
                    "disabling fatigue must leave existing saved fatigue intact");

            config.interactionFatigueEnabled = true;
            config.interactionChanceFatigue = 0.0f;
            brain.rewardHearts(fixture.player(), 1);
            memories = brain.getMemoriesForPlayer(fixture.player());
            helper.assertTrue(memories.getInteractionFatigue() == 7,
                    "zero weighting multiplier must still permit fatigue accumulation");
            config.interactionChanceFatigue = 1.0f;
            brain.rewardHearts(fixture.player(), 1);
            memories = brain.getMemoriesForPlayer(fixture.player());
            helper.assertTrue(memories.getInteractionFatigue() == 8,
                    "re-enabling fatigue must resume accumulation from the saved value");
        } finally {
            config.interactionFatigueEnabled = enabled;
            config.interactionChanceFatigue = multiplier;
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_fatigue_isolated", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void disabledFatigueStillDecaysAndReenablesWithRemainingPairWeight(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ServerPlayer other = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "dialogue-fatigue-other"));
        other.setPos(fixture.player().position());
        VillagerEntityMCA otherVillager = villagerNear(helper, other, -1.0D);
        var brain = fixture.villager().getVillagerBrain();
        brain.getMemoriesForPlayer(fixture.player()).setInteractionFatigue(6);
        otherVillager.getVillagerBrain().getMemoriesForPlayer(other).setInteractionFatigue(6);

        DialogueEvent goaway = DialogueEvent.decode(ResourceLocation.parse("mca:test/fatigue_decay_goaway"),
                JsonParser.parseString("""
                        {
                          "trigger":"talk",
                          "presentation":{"mode":"ambient","resume_prompt":"dialogue.test.resume"},
                          "weight":-5,"fatigue":1,
                          "history":"scheduling",
                          "repeat":{"type":"cooldown","seconds":0},
                          "start":"main",
                          "nodes":{"main":{"line":"dialogue.goaway","complete":true}}
                        }
                        """).getAsJsonObject());
        DialogueEvent.Outcome tired = new DialogueEvent.Outcome(
                List.of(), -5.0, List.of(), "done", 0, 1.0);
        Config config = Config.getInstance();
        boolean originallyEnabled = config.interactionFatigueEnabled;
        float originalMultiplier = config.interactionChanceFatigue;
        int originalCooldown = config.interactionFatigueCooldown;
        try {
            config.interactionFatigueEnabled = true;
            config.interactionChanceFatigue = 1.0f;
            config.interactionFatigueCooldown = 20;
            helper.assertTrue(DialogueEngine.pickPreview(List.of(goaway), null, RandomSource.create(1L),
                    6, config.interactionChanceFatigue, config.interactionFatigueEnabled).isPresent(),
                    "fatigue six must permit the go-away opening");
            helper.assertTrue(DialogueEngine.pickOutcome(List.of(tired), RandomSource.create(1L),
                    6, config.interactionChanceFatigue, config.interactionFatigueEnabled).isPresent(),
                    "fatigue six must permit the marked outcome");
            config.interactionFatigueEnabled = false;
            helper.assertTrue(DialogueEngine.pickPreview(List.of(goaway), null, RandomSource.create(1L),
                    6, config.interactionChanceFatigue, config.interactionFatigueEnabled).isEmpty()
                    && DialogueEngine.pickOutcome(List.of(tired), RandomSource.create(1L),
                    6, config.interactionChanceFatigue, config.interactionFatigueEnabled).isEmpty(),
                    "disabling fatigue must suppress both weight paths despite saved fatigue");

            // Cross a single cooldown boundary through the real villager tick pipeline.
            fixture.villager().tickCount = 19;
            otherVillager.tickCount = 5;
            helper.runAfterDelay(3, () -> {
                try {
                    int remaining = brain.getMemoriesForPlayer(fixture.player()).getInteractionFatigue();
                    int otherFatigue = otherVillager.getVillagerBrain()
                            .getMemoriesForPlayer(other).getInteractionFatigue();
                    helper.assertTrue(remaining == 5 && otherFatigue == 6,
                            "disabled fatigue must decay once without changing the independent pair");
                    config.interactionFatigueEnabled = true;
                    helper.assertTrue(DialogueEngine.pickPreview(List.of(goaway), null, RandomSource.create(1L),
                            remaining, config.interactionChanceFatigue, config.interactionFatigueEnabled).isEmpty()
                            && DialogueEngine.pickOutcome(List.of(tired), RandomSource.create(1L),
                            remaining, config.interactionChanceFatigue, config.interactionFatigueEnabled).isEmpty(),
                            "re-enabling must use the reduced fatigue for both weight paths");
                    helper.assertTrue(DialogueEngine.pickPreview(List.of(goaway), null, RandomSource.create(1L),
                            otherFatigue, config.interactionChanceFatigue, config.interactionFatigueEnabled).isPresent()
                            && DialogueEngine.pickOutcome(List.of(tired), RandomSource.create(1L),
                            otherFatigue, config.interactionChanceFatigue, config.interactionFatigueEnabled).isPresent(),
                            "the independent pair must retain its own higher-fatigue options");
                    helper.succeed();
                } finally {
                    config.interactionFatigueEnabled = originallyEnabled;
                    config.interactionChanceFatigue = originalMultiplier;
                    config.interactionFatigueCooldown = originalCooldown;
                }
            });
        } catch (RuntimeException | Error e) {
            config.interactionFatigueEnabled = originallyEnabled;
            config.interactionChanceFatigue = originalMultiplier;
            config.interactionFatigueCooldown = originalCooldown;
            throw e;
        }
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void fatiguedRefusalCannotBeBypassedAndClosesOnlyItsInteraction(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var interactions = fixture.villager().getInteractions();
        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID originalInteraction = interactions.interactionId();
        var memories = fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player());
        memories.setInteractionFatigue(6);
        int heartsBefore = memories.getHearts();
        DialogueEvent refusal = DialogueEvent.decode(ResourceLocation.parse("mca:test/refusal"),
                JsonParser.parseString("""
                        {
                          "trigger":"talk",
                          "presentation":{"mode":"ambient","resume_prompt":"dialogue.test.resume"},
                          "weight":-3,"fatigue":1,
                          "history":"scheduling",
                          "repeat":{"type":"cooldown","seconds":0},
                          "start":"main",
                          "nodes":{"main":{"line":"dialogue.busy","complete":true,"close_interaction":true}}
                        }
                        """).getAsJsonObject());
        DialogueEvent topic = simpleEvent(ResourceLocation.parse("mca:test/another_topic"));
        DialogueEngine engine = engine(refusal, topic);
        DialogueEventHistory history = DialogueEventHistory.get(fixture.player().serverLevel());

        DialogueEngine.DialogueOptions offer = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(offer.preview().map(p -> p.eventId().equals(refusal.id())
                        && p.advanceKind() == DialogueEngine.AdvanceKind.BACK_TO_TOPICS).orElse(false),
                "fatigue should expose the complete refusal with a Finish acknowledgement");
        helper.assertTrue(offer.ask().isEmpty() && !offer.ambientAvailable()
                        && offer.continuationSessionId().isEmpty(),
                "refusal must not offer a topic or continuation bypass");
        helper.assertTrue(engine.select(fixture.player(), offer.token(),
                DialogueEngine.DialogueSelection.EVENT, topic.id()).isEmpty(),
                "forged topic selection must be rejected while refusing");
        helper.assertTrue(engine.select(fixture.player(), offer.token(),
                DialogueEngine.DialogueSelection.AMBIENT, null).isEmpty(),
                "forged ambient selection must be rejected while refusing");
        helper.assertTrue(interactions.matchesInteraction(fixture.player(), originalInteraction),
                "refusal must be readable before it releases the interaction");
        helper.assertTrue(!history.completed(fixture.player().getUUID(), fixture.villager().getUUID(), refusal.id()),
                "preview must not record a completion before acknowledgement");

        DialogueEngine.DialogueOptions refreshed = engine.begin(fixture.player(), fixture.villager(), false);
        helper.assertTrue(refreshed.token() != offer.token()
                        && refreshed.preview().map(p -> p.eventId().equals(refusal.id())
                            && p.advanceKind() == DialogueEngine.AdvanceKind.BACK_TO_TOPICS).orElse(false)
                        && refreshed.ask().isEmpty() && !refreshed.ambientAvailable()
                        && refreshed.continuationSessionId().isEmpty(),
                "reopening Topics must preserve the refusal without exposing another route");
        helper.assertTrue(engine.advance(fixture.player(), offer.token()).status()
                        == DialogueEngine.TransitionStatus.REJECTED,
                "the replaced preview's acknowledgement must be invalidated");
        helper.assertTrue(engine.advance(fixture.player(), refreshed.token()).completed(),
                "acknowledging refusal must complete its terminal node");
        helper.assertTrue(!interactions.matchesInteraction(fixture.player(), originalInteraction),
                "acknowledgement must release the original owner-scoped interaction");
        helper.assertTrue(!sessions(engine).containsKey(fixture.player().getUUID())
                        && !offers(engine).containsKey(fixture.player().getUUID()),
                "acknowledgement must clear session and offer state");
        helper.assertTrue(memories.getInteractionFatigue() == 6 && memories.getHearts() == heartsBefore,
                "pure refusal speech must not change saved relationship fatigue or hearts");

        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID replacement = interactions.interactionId();
        helper.assertTrue(!replacement.equals(originalInteraction),
                "reopened interaction must have a distinct owner ID");
        helper.assertTrue(engine.advance(fixture.player(), offer.token()).status()
                        == DialogueEngine.TransitionStatus.REJECTED
                        && interactions.matchesInteraction(fixture.player(), replacement),
                "replayed acknowledgement must not release the newer interaction");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void resumedTerminalCloseReleasesReopenedInteraction(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var interactions = fixture.villager().getInteractions();
        DialogueEvent closing = DialogueEvent.decode(ResourceLocation.parse("mca:test/resumed_terminal_close"),
                JsonParser.parseString("""
                        {
                          "trigger":"talk",
                          "presentation":{"mode":"ask","prompt":"dialogue.test.prompt",
                                          "resume_prompt":"dialogue.test.resume"},
                          "repeat":{"type":"always"},
                          "start":"end",
                          "nodes":{"end":{"line":"dialogue.test.line",
                                          "complete":true,"close_interaction":true}}
                        }
                        """).getAsJsonObject());
        DialogueEngine engine = engine(closing);
        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID originalId = interactions.interactionId();
        var offer = engine.begin(fixture.player(), fixture.villager(), false);
        var first = engine.select(fixture.player(), offer.token(),
                DialogueEngine.DialogueSelection.EVENT, closing.id()).orElseThrow();
        engine.pause(fixture.player());
        interactions.stopInteracting(fixture.player(), originalId);

        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID reopenedId = interactions.interactionId();
        helper.assertTrue(!originalId.equals(reopenedId),
                "resuming should use a fresh interaction owner");
        var continuation = engine.begin(fixture.player(), fixture.villager(), false);
        helper.assertTrue(continuation.continuationSessionId().filter(first.sessionId()::equals).isPresent(),
                "the unfinished terminal must remain resumable");
        var resumed = engine.select(fixture.player(), continuation.token(),
                DialogueEngine.DialogueSelection.RESUME, null).orElseThrow();
        helper.assertTrue(engine.advance(fixture.player(), resumed.offerToken()).completed(),
                "Finish should complete the resumed terminal node");
        helper.assertTrue(!interactions.matchesInteraction(fixture.player(), reopenedId),
                "Finish must release the reopened interaction owner");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void ambientOpeningReplyDoesNotCommitStoryUntilFinalAcknowledgement(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.villager().getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        DialogueEvent event = DialogueEvent.decode(ResourceLocation.parse("mca:test/preview_reply"),
                JsonParser.parseString("""
                        {
                          "trigger":"talk",
                          "presentation":{"mode":"ambient","resume_prompt":"dialogue.test.resume"},
                          "repeat":{"type":"once"},
                          "start":"intro",
                          "nodes":{
                            "intro":{"line":"dialogue.test.line","choices":[
                              {"id":"ask_why","text":"dialogue.test.question","next":"done"}
                            ]},
                            "done":{"line":"dialogue.test.done","complete":true}
                          }
                        }
                        """).getAsJsonObject());
        DialogueEngine engine = engine(event);
        DialogueEventHistory history = DialogueEventHistory.get(fixture.player().serverLevel());
        DialogueEngine.DialogueOptions offer = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(offer.preview().map(p -> p.choices().stream()
                        .anyMatch(choice -> choice.id().equals("ask_why"))).orElse(false),
                "initial natural reply must be present in the offer");
        helper.assertTrue(!history.completed(fixture.player().getUUID(), fixture.villager().getUUID(), event.id()),
                "displaying the greeting must not complete a story");
        helper.assertTrue(engine.choose(fixture.player(), offer.token(), "forged").status()
                        == DialogueEngine.TransitionStatus.REJECTED,
                "unoffered opening replies must not change the run");
        DialogueEngine.TransitionResult accepted = engine.choose(fixture.player(), offer.token(), "ask_why");
        helper.assertTrue(accepted.status() == DialogueEngine.TransitionStatus.ADVANCED,
                "an offered opening reply should start the normal story");
        helper.assertTrue(engine.choose(fixture.player(), offer.token(), "ask_why").status()
                        == DialogueEngine.TransitionStatus.REJECTED,
                "replayed initial choices must not be accepted");
        helper.assertTrue(!history.completed(fixture.player().getUUID(), fixture.villager().getUUID(), event.id()),
                "accepting a reply must not imply story completion");
        DialogueEngine.DialogueNodeView end = accepted.view().orElseThrow();
        helper.assertTrue(engine.advance(fixture.player(), end.offerToken()).completed(),
                "only final acknowledgement should complete the story");
        helper.assertTrue(history.completed(fixture.player().getUUID(), fixture.villager().getUUID(), event.id()),
                "completion must be persisted under the original player/villager pair");
        helper.succeed();
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
        DialogueSession displacedSession = sessions(engine).get(fixture.player().getUUID());
        helper.assertTrue(displacedSession.status() == DialogueSession.Status.PAUSED,
                "superseding a player's screen must pause their own unfinished story");
        long displacedPauseDeadline = displacedSession.pauseDeadline();
        new InteractionCloseRequest(fixture.villager().getUUID(), originalInteractionId,
                UUID.randomUUID(), active.offerToken()).handleServer(fixture.player());
        new InteractionCloseRequest(fixture.villager().getUUID(), originalInteractionId,
                active.sessionId(), active.offerToken() + 1L).handleServer(fixture.player());
        DialogueSession afterForgedClose = sessions(engine).get(fixture.player().getUUID());
        helper.assertTrue(afterForgedClose.status() == DialogueSession.Status.PAUSED
                        && afterForgedClose.pauseDeadline() == displacedPauseDeadline,
                "forged displaced-player close packets cannot mutate or extend the already-paused story");
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
    public static void staleDialogueCommandCannotRunDuringAnotherPlayersInteraction(GameTestHelper helper) {
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
        helper.assertTrue(engine.advance(fixture.player(), token).status() == DialogueEngine.TransitionStatus.PAUSED,
                "a stale dialogue must pause when another player owns the villager interaction");
        helper.assertTrue(fixture.player().getInventory().countItem(ItemsMCA.DIVORCE_PAPERS) == 0,
                "the stale dialogue must not run commands for its former player");
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
    public static void legacyCommandPacketRejectsForeignPlayerAndDialogueOnlyEffects(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var interactions = fixture.villager().getInteractions();
        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID interactionId = interactions.interactionId();
        ServerPlayer intruder = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "legacy-command-intruder"));
        intruder.setPos(fixture.player().position());
        boolean originalArmor = fixture.villager().getVillagerBrain().getArmorWear();

        new InteractionVillagerMessage("armor", fixture.villager().getUUID(), interactionId).handleServer(intruder);
        helper.assertTrue(fixture.villager().getVillagerBrain().getArmorWear() == originalArmor,
                "a non-owner must not toggle the villager's armor through a forged command");
        helper.assertTrue(interactions.matchesInteraction(fixture.player(), interactionId),
                "a non-owner's command must not close the actual owner's interaction");

        var originalProfession = fixture.villager().getVillagerData().getProfession();
        new InteractionVillagerMessage("execute", fixture.villager().getUUID(), interactionId).handleServer(fixture.player());
        helper.assertTrue(fixture.villager().getVillagerData().getProfession().equals(originalProfession),
                "direct commands must not bypass the mayor eligibility enforced by the GUI");
        helper.assertTrue(interactions.matchesInteraction(fixture.player(), interactionId),
                "rejected privileged commands must not close the owner screen");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void legacyCommandPacketRejectsStaleInteractionId(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var interactions = fixture.villager().getInteractions();
        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID firstId = interactions.interactionId();
        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID secondId = interactions.interactionId();
        helper.assertTrue(!firstId.equals(secondId), "reopening the menu must rotate the interaction identity");
        boolean originalArmor = fixture.villager().getVillagerBrain().getArmorWear();

        // A queued command from the first menu must never act on or close the newer menu.
        new InteractionVillagerMessage("armor", fixture.villager().getUUID(), firstId).handleServer(fixture.player());
        helper.assertTrue(fixture.villager().getVillagerBrain().getArmorWear() == originalArmor,
                "an old-menu command must not change the newer menu's villager state");
        helper.assertTrue(interactions.matchesInteraction(fixture.player(), secondId),
                "an old-menu command must not dismiss the new interaction owner");

        new InteractionVillagerMessage("armor", fixture.villager().getUUID(), secondId)
                .handleServer(fixture.player());
        helper.assertTrue(fixture.villager().getVillagerBrain().getArmorWear() != originalArmor,
                "the new interaction's legitimate armor command must still execute");
        helper.assertTrue(interactions.getInteractingPlayer().isEmpty(),
                "the accepted command may only close its own matching interaction");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void legacyMenuCommandRejectsOutOfReachAndPrivilegedActions(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var interactions = fixture.villager().getInteractions();
        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID id = interactions.interactionId();
        boolean armor = fixture.villager().getVillagerBrain().getArmorWear();
        var profession = fixture.villager().getVillagerData().getProfession();

        fixture.player().setPos(fixture.villager().getX() + 6.0D,
                fixture.villager().getY(), fixture.villager().getZ());
        new InteractionVillagerMessage("armor", fixture.villager().getUUID(), id).handleServer(fixture.player());
        helper.assertTrue(fixture.villager().getVillagerBrain().getArmorWear() == armor,
                "an out-of-reach player cannot trigger menu commands even with the right screen ID");
        helper.assertTrue(interactions.matchesInteraction(fixture.player(), id),
                "a rejected distant request cannot dismiss the active menu");

        fixture.player().setPos(fixture.villager().getX() + 1.0D,
                fixture.villager().getY(), fixture.villager().getZ());
        new InteractionVillagerMessage("profession.guard", fixture.villager().getUUID(), id)
                .handleServer(fixture.player());
        helper.assertTrue(fixture.villager().getVillagerData().getProfession().equals(profession),
                "a client-only profession menu cannot grant monarch privileges");
        new InteractionVillagerMessage("slap", fixture.villager().getUUID(), id)
                .handleServer(fixture.player());
        helper.assertTrue(interactions.matchesInteraction(fixture.player(), id),
                "dialogue-only effects must not be executable via a menu command");

        new InteractionVillagerMessage("armor", fixture.villager().getUUID(), id).handleServer(fixture.player());
        helper.assertTrue(fixture.villager().getVillagerBrain().getArmorWear() != armor,
                "a nearby legitimate owner can still use the built-in armor control");

        // Work requires an eligible family member or a non-family mercenary.
        fixture.villager().setProfession(ProfessionsMCA.MERCENARY);
        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        new InteractionVillagerMessage("harvesting", fixture.villager().getUUID(),
                interactions.interactionId()).handleServer(fixture.player());
        helper.assertTrue(fixture.villager().getVillagerBrain().getCurrentJob()
                        == net.conczin.mca.entity.ai.Chore.HARVEST,
                "a nearby legitimate owner can still assign a built-in work command");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void workPacketRequiresWorkMenuEligibility(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var player = fixture.player();
        var villager = fixture.villager();
        var interactions = villager.getInteractions();
        var brain = villager.getVillagerBrain();
        interactions.interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID id = interactions.interactionId();

        helper.assertTrue(!net.conczin.mca.entity.interaction.Constraint.FAMILY.test(villager, player)
                        && !net.conczin.mca.entity.interaction.Constraint.MERCENARY.test(villager, player),
                "the initial work-denial fixture must be neither family nor mercenary");
        new InteractionVillagerMessage("harvesting", villager.getUUID(), id).handleServer(player);
        helper.assertTrue(brain.getCurrentJob() == Chore.NONE && interactions.matchesInteraction(player, id),
                "a non-family, non-mercenary owner cannot assign Work via a packet");

        brain.assignJob(Chore.HARVEST, player);
        new InteractionVillagerMessage("stopworking", villager.getUUID(), id).handleServer(player);
        helper.assertTrue(brain.getCurrentJob() == Chore.HARVEST && interactions.matchesInteraction(player, id),
                "a forbidden stopworking packet must preserve the villager's job and interaction");

        var parent = FamilyTree.get(helper.getLevel()).getOrCreate(player);
        villager.getRelationships().getFamilyEntry().replaceParents(parent, Optional.empty());
        helper.assertTrue(net.conczin.mca.entity.interaction.Constraint.FAMILY.test(villager, player),
                "adoptive parent must satisfy the Work family constraint");
        villager.setAgeState(AgeState.BABY);
        new InteractionVillagerMessage("stopworking", villager.getUUID(), id).handleServer(player);
        helper.assertTrue(brain.getCurrentJob() == Chore.HARVEST && interactions.matchesInteraction(player, id),
                "a family baby cannot stop a chore through a forged Work command");

        villager.setAgeState(AgeState.ADULT);
        new InteractionVillagerMessage("stopworking", villager.getUUID(), id).handleServer(player);
        helper.assertTrue(brain.getCurrentJob() == Chore.NONE,
                "an adult family member must still be able to stop Work");

        var mercenary = villagerNear(helper, player, 1.5D);
        mercenary.setProfession(ProfessionsMCA.MERCENARY);
        var mercenaryInteractions = mercenary.getInteractions();
        mercenaryInteractions.interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID mercenaryId = mercenaryInteractions.interactionId();
        new InteractionVillagerMessage("harvesting", mercenary.getUUID(), mercenaryId).handleServer(player);
        helper.assertTrue(mercenary.getVillagerBrain().getCurrentJob() == Chore.HARVEST,
                "a non-family mercenary must still be able to start Work");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void interactDataRequestRequiresCurrentMenuOwner(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        var interactions = fixture.villager().getInteractions();
        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID firstId = interactions.interactionId();
        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        UUID currentId = interactions.interactionId();
        ServerPlayer other = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "data-intruder"));
        other.setPos(fixture.player().position());

        // Capture the actual packet boundary while leaving the global sender intact afterwards.
        Network.Sender previous;
        try {
            Field field = Network.class.getDeclaredField("sender");
            field.setAccessible(true);
            previous = (Network.Sender) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not capture server packet sender", e);
        }
        AtomicInteger sent = new AtomicInteger();
        AtomicReference<GetInteractDataResponse> lastResponse = new AtomicReference<>();
        Network.registerSender((player, payload) -> {
            if (payload instanceof GetInteractDataResponse response) {
                lastResponse.set(response);
                sent.incrementAndGet();
            }
        });
        try {
            new GetInteractDataRequest(fixture.villager().getId(), currentId).handleServer(other);
            helper.assertTrue(sent.get() == 0,
                    "a foreign player cannot request another player's private Interact menu data");
            new GetInteractDataRequest(fixture.villager().getId(), firstId).handleServer(fixture.player());
            helper.assertTrue(sent.get() == 0,
                    "a stale menu request cannot access the replacement menu's personal data");
            new GetInteractDataRequest(fixture.villager().getId(), currentId).handleServer(fixture.player());
            helper.assertTrue(sent.get() == 1,
                    "the current nearby menu owner can request Interact menu data");
            // The wire response must carry its own correlation: a newer menu must
            // be able to reject an earlier response that was already in flight.
            FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
            try {
                GetInteractDataResponse.STREAM_CODEC.encode(encoded, lastResponse.get());
                helper.assertTrue(encoded.readInt() == fixture.villager().getId(),
                        "Interact data response must identify the villager it describes");
                helper.assertTrue(encoded.readUUID().equals(currentId),
                        "Interact data response must identify the menu whose data it describes");
            } finally {
                encoded.release();
            }
        } finally {
            Network.registerSender(previous);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void zombieCureUsesVanillaInteractionNotForgedMenuCommands(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ZombieVillagerEntityMCA zombie = EntitiesMCA.MALE_ZOMBIE_VILLAGER.create(helper.getLevel());
        helper.assertTrue(zombie != null, "expected a zombie villager fixture");
        zombie.setNoAi(true);
        zombie.setPos(fixture.player().getX() + 1.0D, fixture.player().getY(), fixture.player().getZ());
        zombie.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200));
        helper.getLevel().addFreshEntity(zombie);
        fixture.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE, 3));
        try {
            new InteractionVillagerMessage("gift", zombie.getUUID(), UUID.randomUUID())
                    .handleServer(fixture.player());
            helper.assertTrue(!zombie.isConverting(),
                    "a zombie with no open Interact menu must reject forged gift packets");
            helper.assertTrue(fixture.player().getMainHandItem().getCount() == 3,
                    "a forged zombie gift must not consume the held golden apple");
            helper.assertTrue(!InteractionMenuCommandAccess.allows(zombie, fixture.player(), "gift"),
                    "zombie cure must use native right-click, not a legacy GUI command with no menu owner");

            zombie.mobInteract(fixture.player(), InteractionHand.MAIN_HAND);
            helper.assertTrue(zombie.isConverting(),
                    "normal right-click golden-apple curing must remain available");
            helper.assertTrue(fixture.player().getMainHandItem().getCount() == 2,
                    "normal zombie curing must consume exactly one golden apple");
        } finally {
            zombie.discard();
        }
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
        helper.assertTrue(firstMenu.containsEvent(event.id()),
                "the first player must see a fresh topic on the villager");
        DialogueEngine.DialogueNodeView first = engine.select(fixture.player(), firstMenu.token(),
                DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();

        DialogueEngine.DialogueNodeView firstDone = engine.choose(fixture.player(), first.offerToken(), "agree")
                .view().orElseThrow();
        helper.assertTrue(engine.advance(fixture.player(), firstDone.offerToken()).completed(),
                "first player must complete only their own run");
        helper.assertTrue(!engine.begin(fixture.player(), fixture.villager()).containsEvent(event.id()),
                "first player's completed story must be cooling down");

        fixture.villager().getInteractions().interactAt(second, Vec3.ZERO, InteractionHand.MAIN_HAND);
        DialogueEngine.DialogueOptions secondMenu = engine.begin(second, fixture.villager());
        helper.assertTrue(secondMenu.containsEvent(event.id()),
                "the second player must still have an independent fresh topic");
        DialogueEngine.DialogueNodeView other = engine.select(second, secondMenu.token(),
                DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        helper.assertTrue(!first.sessionId().equals(other.sessionId()),
                "sequential players must have distinct dialogue sessions");

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
    public static void openingTopicsPausesActiveRunUntilValidReplacement(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueEvent activeEvent = passageEvent(ResourceLocation.parse("mca:test/active_begin"), false);
        DialogueEvent replacementEvent = simpleEvent(ResourceLocation.parse("mca:test/active_begin_replacement"));
        DialogueEngine engine = engine(activeEvent, replacementEvent);

        DialogueEngine.DialogueOptions initial = engine.begin(fixture.player(), fixture.villager());
        DialogueEngine.DialogueNodeView active = engine.select(
                fixture.player(), initial.token(), DialogueEngine.DialogueSelection.EVENT, activeEvent.id()).orElseThrow();

        DialogueEngine.DialogueOptions topics = engine.begin(fixture.player(), fixture.villager(), false);
        helper.assertTrue(topics.containsEvent(replacementEvent.id()),
                "Topics should offer a valid replacement event");
        helper.assertTrue(topics.continuationSessionId().filter(active.sessionId()::equals).isPresent(),
                "Topics should offer a continuation of the paused run");
        helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).status() == DialogueSession.Status.PAUSED,
                "opening Topics must pause the active run rather than replace it");
        helper.assertTrue(engine.select(
                fixture.player(), initial.token(), DialogueEngine.DialogueSelection.EVENT, replacementEvent.id()).isEmpty(),
                "an older menu token cannot replace the paused run"
        );
        helper.assertTrue(engine.select(
                fixture.player(), topics.token(), DialogueEngine.DialogueSelection.EVENT,
                ResourceLocation.parse("mca:test/unoffered")).isEmpty(),
                "an unoffered event cannot replace the paused run"
        );
        DialogueSession retained = sessions(engine).get(fixture.player().getUUID());
        helper.assertTrue(retained != null && retained.id().equals(active.sessionId())
                        && retained.status() == DialogueSession.Status.PAUSED,
                "rejected selections must preserve the original resumable run");
        DialogueEngine.DialogueNodeView replacement = engine.select(
                fixture.player(), topics.token(), DialogueEngine.DialogueSelection.EVENT,
                replacementEvent.id()).orElseThrow();
        helper.assertTrue(!replacement.sessionId().equals(active.sessionId()),
                "a valid selection may explicitly replace the paused run");
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

        bob.getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
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

        fixture.villager().getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        DialogueEngine.DialogueOptions aliceBrowse = engine.begin(fixture.player(), fixture.villager());
        helper.assertTrue(aliceBrowse.continuationPrompt().isPresent(), "Alice should still be resumable after Bob browsing");
        bob.getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
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
        fixture.villager().getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
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
        DialogueEngine.DialogueOptions reissued = offers(pausedEngine).get(fixture.player().getUUID());
        helper.assertTrue(reissued != null && reissued.generation() == 2L
                        && reissued.token() != continuation.token() && reissued.continuationSessionId().isEmpty(),
                "reload tick must replace the stale continuation with a fresh menu");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_reload", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 300)
    public static void realServerResourceReloadDiscardsActiveAndPausedPendingEffects(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ServerPlayer other = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "dialogue-reload"));
        other.setPos(fixture.player().position());
        VillagerEntityMCA otherVillager = villagerNear(helper, other, -1.0D);
        otherVillager.getInteractions().interactAt(other, Vec3.ZERO, InteractionHand.MAIN_HAND);
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

        DialogueEngine.DialogueOptions otherMenu = pausedEngine.begin(other, otherVillager);
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
            helper.assertTrue(pausedEngine.begin(other, otherVillager).continuationPrompt().isEmpty(),
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
        // The GameTest world survives failed runs, which can leave this temporary pack selected.
        originalPacks.remove(packId);
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
        VillagerEntityMCA otherVillager = villagerNear(helper, other, -1.0D);
        otherVillager.getInteractions().interactAt(other, Vec3.ZERO, InteractionHand.MAIN_HAND);
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

                DialogueEngine.DialogueOptions otherMenu = paused.begin(other, otherVillager);
                helper.assertTrue(otherMenu.containsEvent(validId),
                        "second player must independently see the installed addon topic");
                paused.select(other, otherMenu.token(), DialogueEngine.DialogueSelection.EVENT, validId).orElseThrow();
                sessions(paused).put(other.getUUID(),
                        sessions(paused).get(other.getUUID())
                                .withPendingEffects(List.of(new DialogueAction.Hearts(9))));
                pausedPlayerHearts.set(otherVillager.getVillagerBrain()
                        .getMemoriesForPlayer(other).getHearts());
                paused.pause(other);
                helper.assertTrue(paused.begin(other, otherVillager).continuationPrompt().isPresent(),
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
            helper.assertTrue(pausedEngine.get().begin(other, otherVillager).continuationPrompt().isEmpty(),
                    "a paused session from a removed addon must not be resumable");
            helper.assertTrue(sessions(pausedEngine.get()).isEmpty(),
                    "removing the addon must discard the paused pending actions");
            helper.assertTrue(fixture.villager().getVillagerBrain()
                            .getMemoriesForPlayer(fixture.player()).getHearts() == initialHearts.get(),
                    "removing an addon must never execute a queued heart reward");
            helper.assertTrue(otherVillager.getVillagerBrain()
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
        killed.getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
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
    public static void pendingLookupRejectsEarlyContinueAndPauseDiscardsOperation(GameTestHelper helper) {
        ResourceLocation providerId = ResourceLocation.parse("mca:pending_lifecycle_gametest");
        DialogueAsyncResolver.register(providerId, Codec.STRING.fieldOf("sample").codec(),
                (sample, context, operationId) -> new CompletableFuture<>());
        Fixture fixture = presentFixture(helper);
        fixture.villager().getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        DialogueEvent event = DialogueEvent.decode(ResourceLocation.parse("mca:test/pending_pause"),
                JsonParser.parseString("""
                        {
                          "trigger":"talk",
                          "presentation":{"mode":"ask","prompt":"dialogue.test.prompt","resume_prompt":"dialogue.test.resume"},
                          "repeat":{"type":"always"},
                          "start":"found",
                          "nodes":{"found":{
                            "line":"dialogue.test.line",
                            "resolve":{"type":"mca:pending_lifecycle_gametest","sample":"hold",
                                       "failure_line":"dialogue.location.forgot"},
                            "complete":true
                          }}
                        }
                        """).getAsJsonObject());
        DialogueEngine engine = engine(event);
        var menu = engine.begin(fixture.player(), fixture.villager());
        var selected = engine.select(fixture.player(), menu.token(),
                DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        engine.sendView(fixture.player(), selected);
        helper.assertTrue(engine.advance(fixture.player(), selected.offerToken()).status()
                        == DialogueEngine.TransitionStatus.REJECTED,
                "an unresolved lookup must reject Continue instead of committing the event");
        engine.pause(fixture.player());
        helper.assertTrue(pendingRequests(engine).snapshot().isEmpty(),
                "leaving Talk must detach and cancel the pending lookup");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void endingOrStoppingServerCancelsPendingLookup(GameTestHelper helper) {
        ResourceLocation providerId = ResourceLocation.parse("mca:pending_cleanup_gametest");
        AtomicReference<UUID> cancelled = new AtomicReference<>();
        DialogueAsyncResolver.register(providerId, Codec.STRING.fieldOf("sample").codec(),
                new DialogueAsyncResolver.Provider<String>() {
                    @Override
                    public CompletableFuture<DialogueAsyncResolver.Result> resolve(
                            String sample, DialogueContext context, UUID operationId) {
                        return new CompletableFuture<>();
                    }

                    @Override
                    public void cancel(String sample, UUID operationId) {
                        cancelled.set(operationId);
                    }
                });
        Fixture fixture = presentFixture(helper);
        fixture.villager().getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        DialogueEvent event = pendingLookupEvent(ResourceLocation.parse("mca:test/pending_cleanup"), providerId);
        DialogueEngine engine = engine(event);
        var firstMenu = engine.begin(fixture.player(), fixture.villager());
        engine.sendView(fixture.player(), engine.select(fixture.player(), firstMenu.token(),
                DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow());
        UUID firstOperation = pendingRequests(engine).snapshot().getFirst().operationId();

        engine.end(fixture.player());
        helper.assertTrue(pendingRequests(engine).snapshot().isEmpty(),
                "ending Talk must detach the pending lookup");
        helper.assertTrue(firstOperation.equals(cancelled.get()),
                "ending Talk must cancel its locator owner");

        var secondMenu = engine.begin(fixture.player(), fixture.villager());
        engine.sendView(fixture.player(), engine.select(fixture.player(), secondMenu.token(),
                DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow());
        UUID secondOperation = pendingRequests(engine).snapshot().getFirst().operationId();
        engine.clear(helper.getLevel().getServer());
        helper.assertTrue(pendingRequests(engine).snapshot().isEmpty(),
                "server shutdown must detach pending lookups");
        helper.assertTrue(secondOperation.equals(cancelled.get()),
                "server shutdown must cancel its locator owner");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void villagerLeaveCancelsPendingLookup(GameTestHelper helper) {
        ResourceLocation providerId = ResourceLocation.parse("mca:pending_leave_gametest");
        AtomicReference<UUID> cancelled = new AtomicReference<>();
        DialogueAsyncResolver.register(providerId, Codec.STRING.fieldOf("sample").codec(),
                new DialogueAsyncResolver.Provider<String>() {
                    @Override
                    public CompletableFuture<DialogueAsyncResolver.Result> resolve(
                            String sample, DialogueContext context, UUID operationId) {
                        return new CompletableFuture<>();
                    }

                    @Override
                    public void cancel(String sample, UUID operationId) {
                        cancelled.set(operationId);
                    }
                });
        Fixture fixture = presentFixture(helper);
        fixture.villager().getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        DialogueEvent event = pendingLookupEvent(ResourceLocation.parse("mca:test/pending_leave"), providerId);
        DialogueEngine engine = engine(event);
        var menu = engine.begin(fixture.player(), fixture.villager());
        engine.sendView(fixture.player(), engine.select(fixture.player(), menu.token(),
                DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow());
        UUID operationId = pendingRequests(engine).snapshot().getFirst().operationId();

        engine.onVillagerLeave(fixture.villager());
        helper.assertTrue(pendingRequests(engine).snapshot().isEmpty(),
                "villager leaving must detach the pending lookup");
        helper.assertTrue(operationId.equals(cancelled.get()),
                "villager leaving must cancel the lookup owner");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void pendingLookupDeadlineResolvesFailureAndCancelsLocator(GameTestHelper helper) {
        ResourceLocation providerId = ResourceLocation.parse("mca:pending_deadline_gametest");
        AtomicReference<UUID> cancelled = new AtomicReference<>();
        DialogueAsyncResolver.register(providerId, Codec.STRING.fieldOf("sample").codec(),
                new DialogueAsyncResolver.Provider<String>() {
                    @Override
                    public CompletableFuture<DialogueAsyncResolver.Result> resolve(
                            String sample, DialogueContext context, UUID operationId) {
                        return new CompletableFuture<>();
                    }

                    @Override
                    public void cancel(String sample, UUID operationId) {
                        cancelled.set(operationId);
                    }
                });
        Fixture fixture = presentFixture(helper);
        fixture.villager().getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        DialogueEvent event = pendingLookupEvent(ResourceLocation.parse("mca:test/pending_deadline"), providerId);
        DialogueEngine engine = engine(event);
        var menu = engine.begin(fixture.player(), fixture.villager());
        engine.sendView(fixture.player(), engine.select(fixture.player(), menu.token(),
                DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow());
        DialoguePendingRequests.Ticket ticket = pendingRequests(engine).snapshot().getFirst();
        long now = helper.getLevel().getServer().overworld().getGameTime();
        helper.assertTrue(ticket.deadline() > now, "a new lookup must receive a future deadline");
        DialoguePendingRequests.Ticket expired = new DialoguePendingRequests.Ticket(
                ticket.key(), ticket.interactionId(), ticket.operationId(), ticket.resolver(), now);
        pendingRequests(engine).install(expired);

        engine.tick(helper.getLevel().getServer());
        helper.assertTrue(pendingRequests(engine).snapshot().isEmpty(),
                "an expired lookup must stop blocking dialogue progress");
        helper.assertTrue(pendingRequests(engine).lineFor(expired.key()).isPresent(),
                "an expired lookup must deliver the localized failure line");
        helper.assertTrue(ticket.operationId().equals(cancelled.get()),
                "an expired lookup must cancel its locator owner");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void completedLookupShowsLocationCoordinatesInline(GameTestHelper helper) {
        ResourceLocation providerId = ResourceLocation.parse("mca:resolved_location_gametest");
        CompletableFuture<DialogueAsyncResolver.Result> completion = new CompletableFuture<>();
        DialogueAsyncResolver.register(providerId, Codec.STRING.fieldOf("sample").codec(),
                (sample, context, operationId) -> completion);
        Fixture fixture = presentFixture(helper);
        fixture.villager().getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        DialogueEvent event = pendingLookupEvent(ResourceLocation.parse("mca:test/resolved_location"), providerId);
        DialogueEngine engine = engine(event);
        var menu = engine.begin(fixture.player(), fixture.villager());
        engine.sendView(fixture.player(), engine.select(fixture.player(), menu.token(),
                DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow());
        DialoguePendingRequests.Ticket ticket = pendingRequests(engine).snapshot().getFirst();

        completion.complete(DialogueAsyncResolver.Result.withKey("dialogue.location.igloo", "9,70,12"));
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(pendingRequests(engine).snapshot().isEmpty(),
                    "the completed lookup must leave the pending state");
            var line = pendingRequests(engine).lineFor(ticket.key()).orElseThrow();
            helper.assertTrue(line.getContents() instanceof TranslatableContents,
                    "the result must retain the translated villager dialogue line");
            TranslatableContents contents = (TranslatableContents) line.getContents();
            helper.assertTrue(contents.getKey().endsWith("dialogue.location.igloo"),
                    "successful lookups must replace the authored placeholder with the location line");
            helper.assertTrue(contents.getArgs().length >= 2 && "9,70,12".equals(contents.getArgs()[1]),
                    "the location translation must receive coordinates as its second argument");
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void resolvedLookupEscapeReopenResumesLocationWithoutSearchingAgain(GameTestHelper helper) {
        ResourceLocation providerId = ResourceLocation.parse("mca:escape_resume_location_gametest");
        CompletableFuture<DialogueAsyncResolver.Result> completion = new CompletableFuture<>();
        AtomicInteger searches = new AtomicInteger();
        DialogueAsyncResolver.register(providerId, Codec.STRING.fieldOf("sample").codec(),
                (sample, context, operationId) -> {
                    searches.incrementAndGet();
                    return completion;
                });
        Fixture fixture = presentFixture(helper);
        var interactions = fixture.villager().getInteractions();
        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        DialogueEngine engine = MCA.getDialogueEngine().orElseThrow();
        DialogueEvent event = pendingLookupEvent(ResourceLocation.parse("mca:test/escape_resolved_location"), providerId);
        DialogueSession session = DialogueSession.start(fixture.player().getUUID(), fixture.villager().getUUID(),
                UUID.randomUUID(), DialogueEvents.INSTANCE.generation(), 912_348L, event);
        sessions(engine).put(fixture.player().getUUID(), session);
        engine.sendView(fixture.player(), new DialogueEngine.DialogueNodeView(session.id(), event.id(),
                session.offerToken(), Component.translatable("dialogue.test.line"), false, List.of(),
                DialogueEngine.AdvanceKind.BACK_TO_TOPICS));
        helper.assertTrue(searches.get() == 1, "the original Rumours line must start one lookup");

        completion.complete(DialogueAsyncResolver.Result.withKey("dialogue.location.igloo", "9,70,12"));
        helper.runAfterDelay(2, () -> {
            try {
                helper.assertTrue(pendingRequests(engine).lineFor(DialoguePendingRequests.Key.from(session)).isPresent(),
                        "the first lookup must finish before Escape");
                UUID interactionId = interactions.interactionId();
                new InteractionCloseRequest(fixture.villager().getUUID(), interactionId,
                        session.id(), session.offerToken()).handleServer(fixture.player());
                helper.assertTrue(sessions(engine).get(fixture.player().getUUID()).status()
                                == DialogueSession.Status.PAUSED,
                        "Escape must pause the same dialogue session");
                helper.assertTrue(interactions.getInteractingPlayer().isEmpty(),
                        "Escape must close the bound villager interaction");

                interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
                DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager(), false);
                helper.assertTrue(menu.continuationSessionId().filter(session.id()::equals).isPresent(),
                        "reopening must offer an explicit Resume for the paused run");
                DialogueEngine.DialogueNodeView resumed = engine.select(fixture.player(), menu.token(),
                        DialogueEngine.DialogueSelection.RESUME, null).orElseThrow();
                engine.sendView(fixture.player(), resumed);
                helper.assertTrue(searches.get() == 1,
                        "resuming a resolved location must not launch another lookup; searches=" + searches.get());
                helper.assertTrue(resumed.line().getContents() instanceof TranslatableContents contents
                                && contents.getKey().endsWith("dialogue.location.igloo")
                                && contents.getArgs().length >= 2 && "9,70,12".equals(contents.getArgs()[1]),
                        "Resume must restore the resolved inline location and its coordinates");
                helper.assertTrue(resumed.offerToken() != session.offerToken()
                                && resumed.advanceKind() == DialogueEngine.AdvanceKind.BACK_TO_TOPICS,
                        "Resume must retain Finish conversation with a fresh authenticated token");
                helper.assertTrue(pendingRequests(engine).snapshot().isEmpty(),
                        "a resolved conversation must not have an outstanding locator");
                DialoguePendingRequests.Key resumedKey = DialoguePendingRequests.Key.from(
                        sessions(engine).get(fixture.player().getUUID()));
                helper.assertTrue(engine.advance(fixture.player(), resumed.offerToken()).status()
                                == DialogueEngine.TransitionStatus.COMPLETED,
                        "Finish conversation must complete the resolved Rumours event");
                helper.assertTrue(searches.get() == 1 && !sessions(engine).containsKey(fixture.player().getUUID()),
                        "finishing the restored location must end the run without another search");
                helper.assertTrue(pendingRequests(engine).lineFor(resumedKey).isEmpty(),
                        "Finish must discard the completed lookup's retained line");
                helper.succeed();
            } finally {
                engine.end(fixture.player());
                interactions.stopInteracting();
                fixture.villager().discard();
                removePresentPlayer(fixture.player());
            }
        });
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void offlineTickDiscardsResolvedLookupAlongWithSession(GameTestHelper helper) {
        ResourceLocation providerId = ResourceLocation.parse("mca:resolved_logout_cleanup_gametest");
        CompletableFuture<DialogueAsyncResolver.Result> completion = new CompletableFuture<>();
        DialogueAsyncResolver.register(providerId, Codec.STRING.fieldOf("sample").codec(),
                (sample, context, operationId) -> completion);
        Fixture fixture = presentFixture(helper);
        var interactions = fixture.villager().getInteractions();
        interactions.interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        DialogueEvent event = pendingLookupEvent(ResourceLocation.parse("mca:test/resolved_logout_cleanup"), providerId);
        DialogueEngine engine = engine(event);
        var menu = engine.begin(fixture.player(), fixture.villager());
        var selected = engine.select(fixture.player(), menu.token(),
                DialogueEngine.DialogueSelection.EVENT, event.id()).orElseThrow();
        engine.sendView(fixture.player(), selected);
        DialoguePendingRequests.Key key = pendingRequests(engine).snapshot().getFirst().key();

        completion.complete(DialogueAsyncResolver.Result.withKey("dialogue.location.igloo", "9,70,12"));
        helper.runAfterDelay(2, () -> {
            try {
                helper.assertTrue(pendingRequests(engine).lineFor(key).isPresent(),
                        "logout test requires a completed cached location");
                engine.pause(fixture.player());
                helper.assertTrue(pendingRequests(engine).lineFor(key).isPresent(),
                        "closing the conversation must preserve the resolved line until Resume");
                removePresentPlayer(fixture.player());
                engine.tick(helper.getLevel().getServer());
                helper.assertTrue(!sessions(engine).containsKey(fixture.player().getUUID()),
                        "offline tick must discard the paused dialogue session");
                helper.assertTrue(pendingRequests(engine).lineFor(key).isEmpty(),
                        "offline tick must discard the resolved line with its owner session");
                helper.succeed();
            } finally {
                engine.end(fixture.player());
                interactions.stopInteracting();
                fixture.villager().discard();
                removePresentPlayer(fixture.player());
            }
        });
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void escapeCancelsOnlyClosingPlayersUnresolvedLookup(GameTestHelper helper) {
        ResourceLocation providerId = ResourceLocation.parse("mca:escape_cancel_isolated_gametest");
        Fixture closing = presentFixture(helper);
        Fixture other = presentFixture(helper);
        CompletableFuture<DialogueAsyncResolver.Result> closingResult = new CompletableFuture<>();
        CompletableFuture<DialogueAsyncResolver.Result> otherResult = new CompletableFuture<>();
        AtomicReference<UUID> cancelled = new AtomicReference<>();
        AtomicInteger cancellations = new AtomicInteger();
        DialogueAsyncResolver.register(providerId, Codec.STRING.fieldOf("sample").codec(),
                new DialogueAsyncResolver.Provider<String>() {
                    @Override
                    public CompletableFuture<DialogueAsyncResolver.Result> resolve(
                            String sample, DialogueContext context, UUID operationId) {
                        return context.player().getUUID().equals(closing.player().getUUID())
                                ? closingResult : otherResult;
                    }

                    @Override
                    public void cancel(String sample, UUID operationId) {
                        cancelled.set(operationId);
                        cancellations.incrementAndGet();
                    }
                });
        DialogueEngine engine = MCA.getDialogueEngine().orElseThrow();
        DialogueEvent event = pendingLookupEvent(ResourceLocation.parse("mca:test/escape_cancel_isolated"), providerId);
        closing.villager().getInteractions().interactAt(closing.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        other.villager().getInteractions().interactAt(other.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        DialogueSession closingSession = DialogueSession.start(closing.player().getUUID(), closing.villager().getUUID(),
                UUID.randomUUID(), DialogueEvents.INSTANCE.generation(), 982_301L, event);
        DialogueSession otherSession = DialogueSession.start(other.player().getUUID(), other.villager().getUUID(),
                UUID.randomUUID(), DialogueEvents.INSTANCE.generation(), 982_302L, event);
        sessions(engine).put(closing.player().getUUID(), closingSession);
        sessions(engine).put(other.player().getUUID(), otherSession);
        try {
            engine.sendView(closing.player(), new DialogueEngine.DialogueNodeView(closingSession.id(), event.id(),
                    closingSession.offerToken(), Component.translatable("dialogue.test.line"), false, List.of(),
                    DialogueEngine.AdvanceKind.BACK_TO_TOPICS));
            engine.sendView(other.player(), new DialogueEngine.DialogueNodeView(otherSession.id(), event.id(),
                    otherSession.offerToken(), Component.translatable("dialogue.test.line"), false, List.of(),
                    DialogueEngine.AdvanceKind.BACK_TO_TOPICS));
            helper.assertTrue(pendingRequests(engine).snapshot().size() == 2,
                    "two distinct players must own distinct pending lookups");
            UUID closingOperation = pendingRequests(engine).snapshot().stream()
                    .filter(ticket -> ticket.key().playerId().equals(closing.player().getUUID()))
                    .findFirst().orElseThrow().operationId();

            new InteractionCloseRequest(closing.villager().getUUID(),
                    closing.villager().getInteractions().interactionId(),
                    closingSession.id(), closingSession.offerToken()).handleServer(closing.player());
            helper.assertTrue(cancellations.get() == 1 && closingOperation.equals(cancelled.get()),
                    "Escape must cancel exactly the closing player's operation");
            helper.assertTrue(pendingRequests(engine).snapshot().size() == 1
                            && pendingRequests(engine).snapshot().getFirst().key().playerId().equals(other.player().getUUID()),
                    "the other player's concurrent lookup must stay pending");
            helper.assertTrue(sessions(engine).get(other.player().getUUID()).status() == DialogueSession.Status.ACTIVE,
                    "closing the first player's screen must leave the other session active");
            helper.succeed();
        } finally {
            engine.end(closing.player());
            engine.end(other.player());
            closing.villager().getInteractions().stopInteracting();
            other.villager().getInteractions().stopInteracting();
            closing.villager().discard();
            other.villager().discard();
            removePresentPlayer(closing.player());
            removePresentPlayer(other.player());
        }
    }

    @GameTest(batch = "mca_dialogue_engine", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void defaultRumorPoolOnlyOffersRegisteredStructures(GameTestHelper helper) {
        var structures = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (String configured : new Config().structuresInRumors) {
            ResourceLocation id = ResourceLocation.tryParse(configured);
            helper.assertTrue(id != null && structures.getHolder(id).isPresent(),
                    "default Rumours location " + configured + " must exist in the server structure registry");
        }
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

    private static DialogueEvent pendingLookupEvent(ResourceLocation eventId, ResourceLocation providerId) {
        return DialogueEvent.decode(eventId, JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{"mode":"ask","prompt":"dialogue.test.prompt","resume_prompt":"dialogue.test.resume"},
                  "repeat":{"type":"always"},
                  "start":"lookup",
                  "nodes":{"lookup":{
                    "line":"dialogue.test.line",
                    "resolve":{"type":"%s","sample":"hold","failure_line":"dialogue.location.forgot"},
                    "complete":true
                  }}
                }
                """.formatted(providerId)).getAsJsonObject());
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
        // GameTest's "air" template clears only its single template block. The
        // villager is spawned one block beside it, which can be solid terrain
        // at the server's underground test Y and cause IN_WALL -> PANIC.
        // Build a small, deterministic room for both entities instead.
        for (int x = -2; x <= 3; x++) {
            for (int z = -2; z <= 2; z++) {
                helper.getLevel().setBlock(position.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                for (int y = 0; y <= 3; y++) {
                    helper.getLevel().setBlock(position.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        ServerPlayer player = new FakePlayer(
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "dialogue-engine")
        );
        player.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        VillagerEntityMCA villager = Objects.requireNonNull(EntitiesMCA.MALE_VILLAGER.create(helper.getLevel()));
        villager.setAgeState(AgeState.ADULT);
        villager.setPos(player.getX() + 1.0D, player.getY(), player.getZ());
        helper.getLevel().addFreshEntity(villager);
        villager.getInteractions().interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND);
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
    private static void removePresentPlayer(ServerPlayer player) {
        try {
            Object playerList = player.getServer().getPlayerList();
            Field playersByUuid = playerList.getClass().getSuperclass().getDeclaredField("playersByUUID");
            playersByUuid.setAccessible(true);
            ((Map<UUID, ServerPlayer>) playersByUuid.get(playerList)).remove(player.getUUID(), player);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not remove the GameTest fake player", exception);
        }
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

    private static DialoguePendingRequests pendingRequests(DialogueEngine engine) {
        try {
            Field field = DialogueEngine.class.getDeclaredField("pendingRequests");
            field.setAccessible(true);
            return (DialoguePendingRequests) field.get(engine);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not inspect async dialogue ticket ownership", exception);
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
