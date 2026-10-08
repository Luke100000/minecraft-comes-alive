package net.conczin.mca.dialogue;

import com.mojang.authlib.GameProfile;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.resources.DialogueEvents;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.DialogueEventHistory;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;

@PrefixGameTestTemplate(false)
public final class DialogueReferenceGameTests {
    private static final ResourceLocation MOURNING = MCA.locate("personal/mourning");
    private static final ResourceLocation INFIRMARY = MCA.locate("location/infirmary");
    private static final ResourceLocation CURED_ZOMBIES = MCA.locate("personal/cured_zombies");
    private static final ResourceLocation CURED_ZOMBIES_FOLLOWUP = MCA.locate("personal/cured_zombies_followup");

    private DialogueReferenceGameTests() {
    }

    @GameTest(batch = "mca_dialogue_reference", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void relativeDeathUnlocksAndCompletesMourningEvent(GameTestHelper helper) {
        Fixture fixture = fixture(helper, helper.absolutePos(BlockPos.ZERO));
        VillagerEntityMCA deceased = spawnVillager(helper, helper.absolutePos(new BlockPos(3, 0, 0)));
        try {
            fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player()).setHearts(25);
            deceased.getRelationships().marry(fixture.villager());
            fixture.villager().getRelationships().marry(deceased);

            DialogueEngine engine = referenceEngine(MOURNING);
            helper.assertTrue(!engine.begin(fixture.player(), fixture.villager()).containsEvent(MOURNING),
                    "mourning event must not be offered before a relative death is recorded");

            deceased.getRelationships().onDeath(helper.getLevel().damageSources().generic());

            DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
            helper.assertTrue(menu.containsEvent(MOURNING),
                    "real relationship death propagation must unlock the shipped mourning event");
            DialogueEngine.TransitionResult completed = completeEvent(
                    helper, engine, fixture, menu, MOURNING, Optional.of("listen"));
            helper.assertTrue(completed.completed(), "mourning event must commit through the real dialogue engine");
        } finally {
            deceased.discard();
            fixture.villager().discard();
        }
        helper.succeed();
    }

    @GameTest(
            batch = "mca_dialogue_reference",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 180
    )
    public static void infirmaryEventUsesCurrentBuildingAndCooldownExpires(GameTestHelper helper) {
        DialogueEngine engine = referenceEngine(INFIRMARY);
        BlockPos center = helper.absolutePos(new BlockPos(16, 2, 16));
        prepareRoom(helper, center);
        VillageManager manager = VillageManager.get(helper.getLevel());
        helper.assertTrue(manager.findNearestVillage(center, Village.BORDER_MARGIN).isEmpty(),
                "fixture would modify an existing village");
        Building.validationResult result = manager.processBuilding(center);
        helper.assertTrue(result == Building.validationResult.SUCCESS,
                "fixture room registration failed: " + result);
        Village village = manager.findNearestVillage(center, Village.BORDER_MARGIN).orElseThrow();
        Fixture fixture;
        try {
            Building room = village.findPhysicalRoomAt(center).orElseThrow();
            room.setType("infirmary");
            room.setTypeForced(true);
            fixture = fixture(helper, center);
        } catch (RuntimeException | Error failure) {
            manager.removeVillage(village.getId());
            throw failure;
        }
        Runnable cleanup = () -> {
            engine.end(fixture.player());
            fixture.villager().discard();
            manager.removeVillage(village.getId());
        };
        try {
            fixture.villager().setNoAi(true);
            fixture.villager().getBrain().setMemory(
                    MemoryModuleType.HOME,
                    GlobalPos.of(helper.getLevel().dimension(), center));
            fixture.villager().getResidency().seekHomeAfterClaim();

            DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
            helper.assertTrue(menu.containsEvent(INFIRMARY),
                    "villager standing in an infirmary must be offered the shipped infirmary event");
            DialogueEngine.TransitionResult completed = completeEvent(
                    helper, engine, fixture, menu, INFIRMARY, Optional.empty());
            helper.assertTrue(completed.completed(), "infirmary event must complete through DialogueEngine");

            DialogueEventHistory history = DialogueEventHistory.get(fixture.player().serverLevel());
            long now = helper.getLevel().getServer().overworld().getGameTime();
            long nextEligibleAt = history.nextEligibleAt(
                    fixture.player().getUUID(), fixture.villager().getUUID(), INFIRMARY);
            helper.assertTrue(nextEligibleAt > now, "completing the infirmary event must persist its five-second cooldown");
            helper.assertTrue(!engine.begin(fixture.player(), fixture.villager()).containsEvent(INFIRMARY),
                    "infirmary event must be excluded immediately after completion");

            helper.runAfterDelay(105, () -> {
                try {
                    helper.assertTrue(helper.getLevel().getServer().overworld().getGameTime() >= nextEligibleAt,
                            "fixture did not advance beyond the persisted cooldown deadline");
                    helper.assertTrue(engine.begin(fixture.player(), fixture.villager()).containsEvent(INFIRMARY),
                            "infirmary event must become eligible again after the cooldown expires");
                } finally {
                    cleanup.run();
                }
                helper.succeed();
            });
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
    }

    @GameTest(batch = "mca_dialogue_reference", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void phyrraPositiveReplyPersistsChoiceAndRequiresExplicitFollowupSelection(GameTestHelper helper) {
        DialogueEngine engine = referenceEngine(CURED_ZOMBIES, CURED_ZOMBIES_FOLLOWUP);
        long originalDayTime = helper.getLevel().getDayTime();
        Fixture fixture = phyrraFixture(helper, new BlockPos(0, 0, 0));
        try {
            DialogueEventHistory history = DialogueEventHistory.get(fixture.player().serverLevel());
            Memories memories = fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player());
            int heartsBefore = memories.getHearts();

            DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
            helper.assertTrue(menu.containsEvent(CURED_ZOMBIES), "Phyrra story must be offered when its authored requirements match");
            helper.assertTrue(!menu.containsEvent(CURED_ZOMBIES_FOLLOWUP),
                    "remembered-choice follow-up must not be offered before the parent story completes");
            completeEvent(helper, engine, fixture, menu, CURED_ZOMBIES, Optional.of("experience_changes_you"));

            helper.assertTrue(history.completed(fixture.player().getUUID(), fixture.villager().getUUID(), CURED_ZOMBIES),
                    "Phyrra story completion must persist durable pair-scoped history");
            helper.assertTrue(history.chose(
                            fixture.player().getUUID(), fixture.villager().getUUID(), CURED_ZOMBIES, "experience_changes_you"),
                    "positive reply must persist its stable choice ID");
            helper.assertTrue(fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player()).getHearts()
                            == heartsBefore + 5,
                    "positive Phyrra reply must apply its authored +5 hearts exactly once on completion");

            DialogueEngine.DialogueOptions followupMenu = engine.begin(fixture.player(), fixture.villager());
            helper.assertTrue(!followupMenu.containsEvent(CURED_ZOMBIES),
                    "completed story must be excluded during its immediate cooldown");
            helper.assertTrue(followupMenu.containsEvent(CURED_ZOMBIES_FOLLOWUP),
                    "positive remembered choice must make the follow-up available in the Talk menu");
            helper.assertTrue(engine.select(
                            fixture.player(), followupMenu.token(), DialogueEngine.DialogueSelection.RESUME, null).isEmpty(),
                    "eligible follow-up must not be auto-started as a continuation");
            helper.assertTrue(engine.select(
                            fixture.player(), followupMenu.token(), DialogueEngine.DialogueSelection.EVENT,
                            CURED_ZOMBIES_FOLLOWUP).isPresent(),
                    "follow-up must start only after explicit player selection");
        } finally {
            engine.end(fixture.player());
            fixture.villager().discard();
            helper.getLevel().setDayTime(originalDayTime);
            helper.getLevel().updateSkyBrightness();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_reference", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void phyrraNeutralReplyPersistsWithoutUnlockingPositiveFollowup(GameTestHelper helper) {
        assertPhyrraNonPositivePath(helper, "dont_know", 0);
    }

    @GameTest(batch = "mca_dialogue_reference", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void phyrraNegativeReplyPersistsWithoutUnlockingPositiveFollowup(GameTestHelper helper) {
        assertPhyrraNonPositivePath(helper, "challenge_the_question", -5);
    }

    @GameTest(
            batch = "mca_dialogue_reference_repeat",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 180
    )
    public static void phyrraRepeatReplacesRememberedChoiceAndCommitsEachRewardOnce(GameTestHelper helper) {
        DialogueEngine engine = referenceEngine(CURED_ZOMBIES, CURED_ZOMBIES_FOLLOWUP);
        long originalDayTime = helper.getLevel().getDayTime();
        long startingGameTime = helper.getLevel().getGameTime();
        boolean daylightCycle = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        Fixture fixture = phyrraFixture(helper, BlockPos.ZERO);
        DialogueEventHistory history = DialogueEventHistory.get(fixture.player().serverLevel());
        IntSupplier hearts = () -> fixture.villager().getVillagerBrain()
                .getMemoriesForPlayer(fixture.player()).getHearts();
        int heartsBefore = hearts.getAsInt();
        UUID playerId = fixture.player().getUUID();
        UUID villagerId = fixture.villager().getUUID();
        long[] finalOfferToken = new long[1];
        Runnable cleanup = () -> {
            engine.end(fixture.player());
            fixture.villager().discard();
            long elapsedDaylight = daylightCycle ? helper.getLevel().getGameTime() - startingGameTime : 0L;
            helper.getLevel().setDayTime(originalDayTime + elapsedDaylight);
            helper.getLevel().updateSkyBrightness();
        };

        try {
            DialogueEngine.DialogueOptions firstMenu = engine.begin(fixture.player(), fixture.villager());
            helper.assertTrue(firstMenu.containsEvent(CURED_ZOMBIES), "Phyrra story must initially be eligible");
            DialogueEngine.TransitionResult first = completeEvent(
                    helper, engine, fixture, firstMenu, CURED_ZOMBIES, Optional.of("experience_changes_you"), token -> {
                        helper.assertTrue(hearts.getAsInt() == heartsBefore,
                                "positive hearts must remain pending until final acknowledgement");
                        finalOfferToken[0] = token;
                    });
            helper.assertTrue(first.completed(), "first story run must complete");
            helper.assertTrue(hearts.getAsInt() == heartsBefore + 5,
                    "first run must apply the authored +5 hearts once");
            helper.assertTrue(engine.advance(fixture.player(), finalOfferToken[0]).status()
                            == DialogueEngine.TransitionStatus.REJECTED,
                    "duplicate first final acknowledgement must be rejected");
            helper.assertTrue(hearts.getAsInt() == heartsBefore + 5,
                    "duplicate first final acknowledgement must not award hearts again");
            helper.assertTrue(history.chose(playerId, villagerId, CURED_ZOMBIES, "experience_changes_you"),
                    "first run must remember the positive choice");

            long firstCompletedAt = helper.getLevel().getServer().overworld().getGameTime();
            long nextEligibleAt = history.nextEligibleAt(playerId, villagerId, CURED_ZOMBIES);
            helper.assertTrue(nextEligibleAt == firstCompletedAt + 100,
                    "shipped Phyrra story must have a 100-tick cooldown");
            DialogueEngine.DialogueOptions afterFirst = engine.begin(fixture.player(), fixture.villager());
            helper.assertTrue(!afterFirst.containsEvent(CURED_ZOMBIES),
                    "story must be unavailable immediately after completion");
            helper.assertTrue(afterFirst.containsEvent(CURED_ZOMBIES_FOLLOWUP),
                    "positive remembered choice must unlock the shipped follow-up");

            helper.runAfterDelay(99, () -> {
                try {
                    helper.assertTrue(helper.getLevel().getServer().overworld().getGameTime() < nextEligibleAt,
                            "fixture must check the cooldown before 100 ticks have elapsed");
                    helper.assertTrue(!engine.begin(fixture.player(), fixture.villager()).containsEvent(CURED_ZOMBIES),
                            "story must remain excluded just before its cooldown expires");
                } catch (RuntimeException | Error failure) {
                    cleanup.run();
                    throw failure;
                }
            });

            helper.runAfterDelay(101, () -> {
                try {
                    helper.assertTrue(helper.getLevel().getServer().overworld().getGameTime() >= nextEligibleAt,
                            "fixture must advance naturally beyond the cooldown deadline");
                    DialogueEngine.DialogueOptions secondMenu = engine.begin(fixture.player(), fixture.villager());
                    helper.assertTrue(secondMenu.containsEvent(CURED_ZOMBIES),
                            "Phyrra story must be eligible again after 100 ticks");
                    helper.assertTrue(secondMenu.containsEvent(CURED_ZOMBIES_FOLLOWUP),
                            "positive follow-up must remain eligible until the second completion");

                    DialogueEngine.TransitionResult second = completeEvent(
                            helper, engine, fixture, secondMenu, CURED_ZOMBIES,
                            Optional.of("challenge_the_question"), token -> {
                                helper.assertTrue(hearts.getAsInt() == heartsBefore + 5,
                                        "negative hearts must remain pending until final acknowledgement");
                                finalOfferToken[0] = token;
                            });
                    helper.assertTrue(second.completed(), "second story run must complete");
                    helper.assertTrue(hearts.getAsInt() == heartsBefore,
                            "second run must apply only its authored -5 hearts");
                    helper.assertTrue(engine.advance(fixture.player(), finalOfferToken[0]).status()
                                    == DialogueEngine.TransitionStatus.REJECTED,
                            "duplicate second final acknowledgement must be rejected");
                    helper.assertTrue(hearts.getAsInt() == heartsBefore,
                            "duplicate second final acknowledgement must not apply hearts again");
                    helper.assertTrue(history.completed(playerId, villagerId, CURED_ZOMBIES),
                            "story completion history must survive repeating the event");
                    helper.assertTrue(history.chose(playerId, villagerId, CURED_ZOMBIES, "challenge_the_question")
                                    && !history.chose(playerId, villagerId, CURED_ZOMBIES, "experience_changes_you"),
                            "latest completion must replace the remembered positive choice");
                    DialogueEngine.DialogueOptions afterSecond = engine.begin(fixture.player(), fixture.villager());
                    helper.assertTrue(!afterSecond.containsEvent(CURED_ZOMBIES_FOLLOWUP),
                            "positive-dependent follow-up must disappear after the negative choice");
                    helper.assertTrue(!afterSecond.containsEvent(CURED_ZOMBIES),
                            "second completion must restart the story cooldown");
                } finally {
                    cleanup.run();
                }
                helper.succeed();
            });
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
    }

    private static void assertPhyrraNonPositivePath(GameTestHelper helper, String choiceId, int heartsDelta) {
        DialogueEngine engine = referenceEngine(CURED_ZOMBIES, CURED_ZOMBIES_FOLLOWUP);
        long originalDayTime = helper.getLevel().getDayTime();
        Fixture fixture = phyrraFixture(helper, BlockPos.ZERO);
        try {
            DialogueEventHistory history = DialogueEventHistory.get(fixture.player().serverLevel());
            Memories memories = fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player());
            int heartsBefore = memories.getHearts();

            DialogueEngine.DialogueOptions menu = engine.begin(fixture.player(), fixture.villager());
            completeEvent(helper, engine, fixture, menu, CURED_ZOMBIES, Optional.of(choiceId));

            helper.assertTrue(history.chose(fixture.player().getUUID(), fixture.villager().getUUID(), CURED_ZOMBIES, choiceId),
                    "selected Phyrra reply must persist its stable choice ID");
            helper.assertTrue(fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player()).getHearts()
                            == heartsBefore + heartsDelta,
                    "Phyrra reply must apply only its authored heart consequence");
            DialogueEngine.DialogueOptions after = engine.begin(fixture.player(), fixture.villager());
            helper.assertTrue(!after.containsEvent(CURED_ZOMBIES_FOLLOWUP),
                    "non-positive reply must not satisfy the positive remembered-choice follow-up");
        } finally {
            engine.end(fixture.player());
            fixture.villager().discard();
            helper.getLevel().setDayTime(originalDayTime);
            helper.getLevel().updateSkyBrightness();
        }
        helper.succeed();
    }

    private static DialogueEngine referenceEngine(ResourceLocation... requiredEvents) {
        for (ResourceLocation id : requiredEvents) {
            if (DialogueEvents.INSTANCE.get(id).isEmpty()) {
                throw new AssertionError("Shipped dialogue event was not loaded: " + id);
            }
        }
        return new DialogueEngine(DialogueEvents.INSTANCE, RandomSource.create(1L));
    }

    private static DialogueEngine.TransitionResult completeEvent(
            GameTestHelper helper,
            DialogueEngine engine,
            Fixture fixture,
            DialogueEngine.DialogueOptions menu,
            ResourceLocation eventId,
            Optional<String> selectedChoice
    ) {
        return completeEvent(helper, engine, fixture, menu, eventId, selectedChoice, token -> {});
    }

    private static DialogueEngine.TransitionResult completeEvent(
            GameTestHelper helper,
            DialogueEngine engine,
            Fixture fixture,
            DialogueEngine.DialogueOptions menu,
            ResourceLocation eventId,
            Optional<String> selectedChoice,
            LongConsumer finalOfferToken
    ) {
        helper.assertTrue(menu.containsEvent(eventId), "Talk menu did not offer expected event " + eventId);
        DialogueEngine.DialogueNodeView view = engine.select(
                fixture.player(), menu.token(), DialogueEngine.DialogueSelection.EVENT, eventId).orElseThrow();
        Optional<String> pendingChoice = selectedChoice;

        for (int step = 0; step < 32; step++) {
            if (!view.choices().isEmpty()) {
                String choiceId = pendingChoice.orElseThrow(() -> new AssertionError(
                        "event " + eventId + " unexpectedly requires another authored choice"));
                helper.assertTrue(view.choices().stream().anyMatch(choice -> choice.id().equals(choiceId)),
                        "event " + eventId + " did not offer expected choice " + choiceId);
                DialogueEngine.TransitionResult chosen = engine.choose(fixture.player(), view.offerToken(), choiceId);
                helper.assertTrue(chosen.status() == DialogueEngine.TransitionStatus.ADVANCED,
                        "choice " + choiceId + " did not advance event " + eventId);
                view = chosen.view().orElseThrow();
                pendingChoice = Optional.empty();
                continue;
            }

            if (view.advanceKind() == DialogueEngine.AdvanceKind.NEXT) {
                DialogueEngine.TransitionResult advanced = engine.advance(fixture.player(), view.offerToken());
                helper.assertTrue(advanced.status() == DialogueEngine.TransitionStatus.ADVANCED,
                        "Next did not advance event " + eventId);
                view = advanced.view().orElseThrow();
                continue;
            }

            if (view.advanceKind() == DialogueEngine.AdvanceKind.BACK_TO_TOPICS) {
                helper.assertTrue(pendingChoice.isEmpty(), "event ended before expected choice was presented");
                finalOfferToken.accept(view.offerToken());
                DialogueEngine.TransitionResult completed = engine.advance(fixture.player(), view.offerToken());
                helper.assertTrue(completed.status() == DialogueEngine.TransitionStatus.COMPLETED,
                        "Back to topics did not commit event " + eventId);
                return completed;
            }

            throw new AssertionError("event " + eventId + " reached a node with no legal continuation");
        }
        throw new AssertionError("event " + eventId + " exceeded the bounded progression fixture");
    }

    private static Fixture phyrraFixture(GameTestHelper helper, BlockPos relativePosition) {
        Fixture fixture = fixture(helper, helper.absolutePos(relativePosition));
        fixture.villager().getVillagerBrain().setPersonality(Personality.GLOOMY);
        fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player()).setHearts(25);
        helper.getLevel().setDayTime(18_000L);
        helper.getLevel().updateSkyBrightness();
        return fixture;
    }

    private static Fixture fixture(GameTestHelper helper, BlockPos position) {
        ServerPlayer player = new FakePlayer(
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "dialogue-ref"));
        player.setPos(position.getCenter());
        VillagerEntityMCA villager = spawnVillager(helper, position.offset(1, 0, 0));
        villager.setNoAi(true);
        return new Fixture(player, villager);
    }

    private static VillagerEntityMCA spawnVillager(GameTestHelper helper, BlockPos absolutePosition) {
        VillagerEntityMCA villager = Objects.requireNonNull(EntitiesMCA.MALE_VILLAGER.create(helper.getLevel()));
        villager.setAgeState(AgeState.ADULT);
        villager.setPos(absolutePosition.getCenter());
        helper.getLevel().addFreshEntity(villager);
        return villager;
    }

    private static void prepareRoom(GameTestHelper helper, BlockPos center) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                helper.getLevel().setBlock(center.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(center.offset(x, 3, z), Blocks.STONE.defaultBlockState(), 3);
                for (int y = 0; y < 3; y++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (Math.abs(x) == 4 || Math.abs(z) == 4) {
                        helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
                    } else {
                        helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        BlockPos foot = center.south(2);
        var bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        helper.getLevel().setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
        helper.getLevel().setBlock(foot.east(), bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
        helper.getLevel().setBlock(center.north(2), Blocks.CHEST.defaultBlockState(), 3);
    }

    private record Fixture(ServerPlayer player, VillagerEntityMCA villager) {
    }
}
