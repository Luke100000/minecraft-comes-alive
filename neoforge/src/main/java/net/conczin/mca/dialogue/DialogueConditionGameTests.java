package net.conczin.mca.dialogue;

import com.mojang.authlib.GameProfile;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.DialogueEventHistory;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@PrefixGameTestTemplate(false)
public final class DialogueConditionGameTests {
    private DialogueConditionGameTests() {
    }

    @GameTest(batch = "mca_dialogue_conditions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void sampleAdultGloomyNightAndHeartsRequirementsAreDeterministic(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.villager().setAge(AgeState.ADULT.toAge());
        fixture.villager().getVillagerBrain().setPersonality(Personality.GLOOMY);
        fixture.villager().getVillagerBrain().getMemoriesForPlayer(fixture.player()).setHearts(25);
        helper.getLevel().setDayTime(18_000L);
        helper.getLevel().updateSkyBrightness();

        assertMatch(helper, fixture.context(), "{\"type\":\"mca:age_group\",\"value\":\"adult\"}");
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:personality\",\"value\":\"gloomy\"}");
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:time\",\"min\":13000,\"max\":23000}");
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:time\",\"value\":\"night\"}");
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:hearts\",\"min\":20}");
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:age_group\",\"value\":\"child\"}");
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:personality\",\"value\":\"upbeat\"}");
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:time\",\"value\":\"day\"}");
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:hearts\",\"max\":19}");
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:not\",\"condition\":{\"type\":\"mca:age_group\",\"value\":\"adult\"}}");
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:not\",\"condition\":{\"type\":\"mca:age_group\",\"value\":\"child\"}}");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_conditions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void healthWeatherBiomeAndRelationshipUseLiveServerState(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        fixture.villager().setHealth(7.0F);
        helper.getLevel().setWeatherParameters(0, 6_000, true, true);
        helper.getLevel().setRainLevel(1.0F);
        helper.getLevel().setThunderLevel(1.0F);
        ResourceLocation biome = helper.getLevel().getBiome(fixture.villager().blockPosition())
                .unwrapKey().orElseThrow().location();
        fixture.villager().getRelationships().marry(fixture.player());

        assertMatch(helper, fixture.context(), "{\"type\":\"mca:health\",\"min\":6,\"max\":8}");
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:weather\",\"value\":\"thunder\"}");
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:biome\",\"value\":\"" + biome + "\"}");
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:relationship\",\"value\":\"spouse\"}");
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:health\",\"max\":6}");
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:weather\",\"value\":\"rain\"}");
        ResourceLocation otherBiome = biome.equals(ResourceLocation.withDefaultNamespace("the_void"))
                ? ResourceLocation.withDefaultNamespace("plains")
                : ResourceLocation.withDefaultNamespace("the_void");
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:biome\",\"value\":\"" + otherBiome + "\"}");
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:relationship\",\"value\":\"engaged\"}");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_conditions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void missingExternalHistoryReferenceFailsClosedThroughNegation(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ResourceLocation missing = ResourceLocation.parse("example_addon:missing_story");
        DialogueContext unavailableContext = new DialogueContext(
                fixture.villager(), fixture.player(), fixture.context().history(), id -> !id.equals(missing));

        DialogueCondition direct = condition("{\"type\":\"mca:event_completed\",\"event\":\"" + missing + "\"}");
        DialogueCondition negated = condition("{\"type\":\"mca:not\",\"condition\":{\"type\":\"mca:event_completed\",\"event\":\"" + missing + "\"}}");

        helper.assertTrue(direct.evaluate(unavailableContext) == DialogueCondition.Evaluation.UNAVAILABLE,
                "missing external event should be unavailable");
        helper.assertTrue(negated.evaluate(unavailableContext) == DialogueCondition.Evaluation.UNAVAILABLE,
                "negation must not turn an unavailable prerequisite into a match");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_conditions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void unknownBuildingTypeFailsClosedThroughNegation(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        DialogueCondition direct = condition("{\"type\":\"mca:village_has_building\",\"value\":\"definitely_missing_dialogue_type\"}");
        DialogueCondition negated = condition("{\"type\":\"mca:not\",\"condition\":{\"type\":\"mca:village_has_building\",\"value\":\"definitely_missing_dialogue_type\"}}");

        helper.assertTrue(direct.evaluate(fixture.context()) == DialogueCondition.Evaluation.UNAVAILABLE,
                "unknown building type should be unavailable");
        helper.assertTrue(negated.evaluate(fixture.context()) == DialogueCondition.Evaluation.UNAVAILABLE,
                "negation must not turn an unknown building type into a match");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_conditions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void completedEventAndLatestChoiceUsePairScopedHistory(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ResourceLocation storyId = ResourceLocation.parse("mca:test/history_story");
        DialogueEvent story = story(storyId);
        DialogueContext context = new DialogueContext(
                fixture.villager(), fixture.player(), fixture.context().history(), storyId::equals);

        fixture.context().history().complete(
                fixture.player().getUUID(),
                fixture.villager().getUUID(),
                story,
                Set.of("ask_why"),
                helper.getLevel().getGameTime(),
                RandomSource.create(1L));

        assertMatch(helper, context, "{\"type\":\"mca:event_completed\",\"event\":\"" + storyId + "\"}");
        assertMatch(helper, context, "{\"type\":\"mca:event_choice\",\"event\":\"" + storyId
                + "\",\"choice\":\"ask_why\"}");
        assertNoMatch(helper, context, "{\"type\":\"mca:event_choice\",\"event\":\"" + storyId
                + "\",\"choice\":\"disagree\"}");

        fixture.context().history().complete(
                fixture.player().getUUID(),
                fixture.villager().getUUID(),
                story,
                Set.of("disagree"),
                helper.getLevel().getGameTime() + 1L,
                RandomSource.create(2L));

        assertNoMatch(helper, context, "{\"type\":\"mca:event_choice\",\"event\":\"" + storyId
                + "\",\"choice\":\"ask_why\"}");
        assertMatch(helper, context, "{\"type\":\"mca:event_choice\",\"event\":\"" + storyId
                + "\",\"choice\":\"disagree\"}");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_conditions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void villagerAndPlayerOwnedConditionsUseLiveState(GameTestHelper helper) {
        Fixture fixture = fixture(helper);

        fixture.villager().getVillagerBrain().modifyMoodValue(-100);
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:mood\",\"value\":\"depressed\"}");
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:mood\",\"value\":\"overjoyed\"}");

        fixture.villager().setProfession(VillagerProfession.FARMER);
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:profession\",\"value\":\"minecraft:farmer\"}");
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:profession\",\"value\":\"minecraft:cleric\"}");

        fixture.villager().getTraits().addTrait(Traits.LACTOSE_INTOLERANCE);
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:trait\",\"value\":\"mca:lactose_intolerance\"}");
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:trait\",\"value\":\"mca:athletic\"}");

        fixture.villager().getGenetics().setGender(Gender.FEMALE);
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:gender\",\"value\":\"female\"}");
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:gender\",\"value\":\"male\"}");

        var pregnancy = fixture.villager().getRelationships().getPregnancy();
        pregnancy.setPregnant(true);
        pregnancy.setBabyAge(120);
        String childGender = pregnancy.getGender().name().toLowerCase(java.util.Locale.ROOT);
        String otherGender = pregnancy.getGender() == Gender.MALE ? "female" : "male";
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:pregnancy\",\"value\":true,"
                + "\"min_progress\":100,\"max_progress\":140,\"child_gender\":\"" + childGender + "\"}");
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:pregnancy\",\"value\":true,"
                + "\"child_gender\":\"" + otherGender + "\"}");
        pregnancy.setPregnant(false);
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:pregnancy\",\"value\":false}");

        fixture.player().getInventory().add(new ItemStack(Items.OAK_LOG, 3));
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:item\",\"value\":\"minecraft:oak_log\",\"min\":3,\"max\":3}");
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:item\",\"value\":\"minecraft:oak_log\",\"min\":4}");
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:tag\",\"value\":\"minecraft:logs\",\"min\":3}");
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:inventory\",\"item\":\"minecraft:oak_log\",\"min\":3}");
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:inventory\",\"tag\":\"minecraft:logs\",\"min\":3}");
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:tag\",\"value\":\"minecraft:logs\",\"min\":4}");
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:inventory\",\"item\":\"minecraft:oak_log\",\"min\":4}");

        fixture.villager().getLongTermMemory().remember("dialogue_test");
        fixture.villager().getLongTermMemory().remember("dialogue_player." + fixture.player().getUUID());
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:memory\",\"id\":\"dialogue_test\"}");
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:memory\",\"id\":\"dialogue_player\",\"var\":\"player\"}");
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:memory\",\"id\":\"missing\",\"present\":false}");
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:memory\",\"id\":\"missing\",\"present\":true}");

        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_conditions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void familyAndAdvancementConditionsUseAuthoritativeState(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        FamilyTree tree = FamilyTree.get(helper.getLevel());
        var villagerNode = tree.getOrCreate(fixture.villager());
        var playerNode = tree.getOrCreate(fixture.player());

        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:family\",\"value\":\"child\"}");
        villagerNode.setFather(playerNode);
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:family\",\"value\":\"child\"}");
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:family\",\"value\":\"relative\"}");
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:family\",\"value\":\"parent\"}");

        ServerPlayer advancementPlayer = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "dialogue-advancement"),
                ClientInformation.createDefault());
        DialogueContext advancementContext = new DialogueContext(
                fixture.villager(), advancementPlayer, fixture.context().history(), ignored -> true);
        ResourceLocation advancementId = ResourceLocation.withDefaultNamespace("story/root");
        var advancement = Objects.requireNonNull(
                advancementPlayer.getServer().getAdvancements().get(advancementId),
                "minecraft:story/root advancement missing");
        var progress = advancementPlayer.getAdvancements().getOrStartProgress(advancement);
        assertNoMatch(helper, advancementContext, "{\"type\":\"mca:advancement\",\"value\":\"" + advancementId + "\"}");
        List<String> remainingCriteria = new ArrayList<>();
        progress.getRemainingCriteria().forEach(remainingCriteria::add);
        helper.assertTrue(!remainingCriteria.isEmpty(), "fixture advancement has no remaining criteria");
        for (String criterion : remainingCriteria) {
            progress.grantProgress(criterion);
        }
        helper.assertTrue(progress.isDone(), "fixture did not complete minecraft:story/root advancement");
        assertMatch(helper, advancementContext, "{\"type\":\"mca:advancement\",\"value\":\"" + advancementId + "\"}");

        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_conditions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void villageBuildingAndCurrentBuildingAreDistinct(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(16, 2, 16));
        prepareRoom(helper, center);
        VillageManager manager = VillageManager.get(helper.getLevel());
        helper.assertTrue(manager.findNearestVillage(center, Village.BORDER_MARGIN).isEmpty(),
                "fixture would modify an existing village");
        Building.validationResult result = manager.processBuilding(center);
        helper.assertTrue(result == Building.validationResult.SUCCESS,
                "fixture room registration failed: " + result);
        Village village = manager.findNearestVillage(center, Village.BORDER_MARGIN).orElseThrow();
        Building room = village.findPhysicalRoomAt(center).orElseThrow();
        room.setType("infirmary");
        room.setTypeForced(true);

        Fixture fixture = fixture(helper, center);
        fixture.villager().getBrain().setMemory(
                MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), center));
        fixture.villager().getResidency().seekHomeAfterClaim();
        helper.assertTrue(fixture.context().village().map(Village::getId).orElse(-1) == village.getId(),
                "villager did not resolve the registered home village");

        assertMatch(helper, fixture.context(), "{\"type\":\"mca:village_has_building\",\"value\":\"infirmary\"}");
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:village_has_building\",\"value\":\"prison\"}");
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:in_building\",\"value\":\"infirmary\"}");
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:building_assignment\",\"value\":\"infirmary\"}");
        fixture.villager().getBrain().setMemory(
                MemoryModuleType.JOB_SITE,
                GlobalPos.of(helper.getLevel().dimension(), center));
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:building_assignment\",\"value\":\"infirmary\",\"source\":\"workplace\"}");

        PlayerSaveData playerData = PlayerSaveData.get(fixture.player());
        playerData.setOverrideVillageRequirements(true);
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:rank\",\"value\":\"monarch\"}");
        playerData.setOverrideVillageRequirements(false);
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:rank\",\"value\":\"monarch\"}");

        fixture.villager().setPos(center.getX() + 12.5D, center.getY(), center.getZ() + 0.5D);
        assertMatch(helper, fixture.context(), "{\"type\":\"mca:village_has_building\",\"value\":\"infirmary\"}");
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:in_building\",\"value\":\"infirmary\"}");
        fixture.villager().getBrain().setMemory(
                MemoryModuleType.JOB_SITE,
                GlobalPos.of(helper.getLevel().dimension(), center.offset(12, 0, 0)));
        assertNoMatch(helper, fixture.context(), "{\"type\":\"mca:building_assignment\",\"value\":\"infirmary\",\"source\":\"workplace\"}");

        manager.removeVillage(village.getId());
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper) {
        return fixture(helper, helper.absolutePos(BlockPos.ZERO));
    }

    private static Fixture fixture(GameTestHelper helper, BlockPos position) {
        ServerPlayer player = new FakePlayer(
                helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "dialogue-condition"));
        player.setPos(position.getCenter());
        VillagerEntityMCA villager = Objects.requireNonNull(EntitiesMCA.MALE_VILLAGER.create(helper.getLevel()));
        villager.setAge(0);
        villager.setPos(player.getX() + 1.0D, player.getY(), player.getZ());
        helper.getLevel().addFreshEntity(villager);
        DialogueEventHistory history = DialogueEventHistory.get(helper.getLevel());
        DialogueContext context = new DialogueContext(villager, player, history, ignored -> true);
        return new Fixture(villager, player, context);
    }

    private static DialogueEvent story(ResourceLocation id) {
        DialogueEvent.Node terminal = new DialogueEvent.Node(
                List.of("dialogue.test.line"),
                Optional.empty(),
                Optional.empty(),
                true,
                false,
                false,
                false);
        return new DialogueEvent(
                id,
                DialogueEvent.Trigger.TALK,
                new DialogueEvent.Presentation(
                        DialogueEvent.PresentationMode.ASK,
                        Optional.of("dialogue.test.prompt"),
                        "dialogue.test.resume",
                        Optional.empty()),
                0,
                1.0D,
                List.of(),
                new DialogueEvent.Repeat(DialogueEvent.RepeatType.COOLDOWN, 0L, 0L),
                DialogueEvent.HistoryPolicy.STORY,
                "end",
                java.util.Map.of("end", terminal));
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

    private static void assertMatch(GameTestHelper helper, DialogueContext context, String json) {
        helper.assertTrue(condition(json).evaluate(context) == DialogueCondition.Evaluation.MATCH,
                "condition should match: " + json);
    }

    private static void assertNoMatch(GameTestHelper helper, DialogueContext context, String json) {
        helper.assertTrue(condition(json).evaluate(context) == DialogueCondition.Evaluation.NO_MATCH,
                "condition should not match: " + json);
    }

    private static DialogueCondition condition(String json) {
        return DialogueCondition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private record Fixture(VillagerEntityMCA villager, ServerPlayer player, DialogueContext context) {
    }
}
