package net.conczin.mca.entity.ai;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.conczin.mca.Config;
import net.conczin.mca.client.resources.EyeTextureLayers;
import net.conczin.mca.item.BabyItem;
import net.conczin.mca.network.c2s.VillagerEditorSyncRequest;
import net.conczin.mca.registry.DataComponentsMCA;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.registry.EntitiesMCA;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import java.util.Map;
import java.util.UUID;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.Objects;

@PrefixGameTestTemplate(false)
public final class EyeGeneticsGameTests {
    private EyeGeneticsGameTests() {}
    private static VillagerEntityMCA villager(GameTestHelper helper) {
        return Objects.requireNonNull(EntitiesMCA.MALE_VILLAGER.create(helper.getLevel()));
    }
    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void founderSurvivesReload(GameTestHelper helper) {
        var first = villager(helper);
        first.getGenetics().randomize();
        CompoundTag eyes = first.getGenetics().getEyePayload();
        helper.assertTrue(!eyes.isEmpty(), "fresh founder has no persistent genotype");
        CompoundTag saved = new CompoundTag(); first.save(saved);
        var second = villager(helper); second.load(saved);
        helper.assertTrue(eyes.equals(second.getGenetics().getEyePayload()), "load changed founder DNA");
        helper.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void legacyLoadPreservesSource(GameTestHelper helper) {
        CompoundTag legacy = new CompoundTag(); legacy.putFloat("GeneFace", 0.6F); legacy.putInt("EyeColor", -1);
        var entity = villager(helper); entity.readAdditionalSaveData(legacy);
        var eyes = entity.getGenetics().getEyePayload();
        helper.assertTrue(eyes.getCompound("LegacyNatural").getFloat("Scalar") == 0.6F, "legacy source not persisted");
        EyeGenetics.decodeGenotype(eyes);
        CompoundTag saved = new CompoundTag(); entity.save(saved);
        var reloaded = villager(helper); reloaded.load(saved);
        helper.assertTrue(eyes.equals(reloaded.getGenetics().getEyePayload()), "legacy load/save rerolled genotype");
        helper.succeed();
    }
    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void unrelatedSeededGeneBaseline(GameTestHelper helper) {
        var mother = villager(helper); var father = villager(helper); var child = villager(helper);
        for (Genetics.GeneType type : new Genetics.GeneType[]{Genetics.SIZE, Genetics.WIDTH, Genetics.BREAST, Genetics.MELANIN,
                Genetics.HEMOGLOBIN, Genetics.EUMELANIN, Genetics.PHEOMELANIN, Genetics.EYE_COLOR, Genetics.SKIN,
                Genetics.EYE_BRIGHTNESS, Genetics.VOICE, Genetics.VOICE_TONE}) {
            mother.getGenetics().setGene(type, 0.2F); father.getGenetics().setGene(type, 0.8F);
        }
        child.getGenetics().setGender(Gender.MALE);
        child.getGenetics().combine(mother.getGenetics(), father.getGenetics(), 431L);
        Map<String, Float> baseline = Map.ofEntries(Map.entry("VoiceTone", 0.26138198F), Map.entry("EyeBrightness", 0.29274F),
                Map.entry("Pheomelanin", 0.6682077F), Map.entry("Size", 0.67109233F), Map.entry("Hemoglobin", 0.6816057F),
                Map.entry("Breast", 0.2484419F), Map.entry("Skin", 0.54409933F), Map.entry("Voice", 0.41386658F),
                Map.entry("Eumelanin", 0.50012887F), Map.entry("Width", 0.33980185F), Map.entry("Melanin", 0.45508045F));
        for (Genetics.Gene gene : child.getGenetics()) if (gene.getType() != Genetics.EYE_COLOR) {
            helper.assertTrue(gene.get() == baseline.get(gene.getType().key()), "eye integration shifted " + gene.getType().key());
        }
        helper.assertTrue(child.getEyeTexture().equals(net.conczin.mca.resources.EyeStyles.DEFAULT), "eye texture RNG changed");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void missingParentsKeepKnownContributionAndSaveOnce(GameTestHelper helper) {
        var known = villager(helper);
        var genotype = new EyeGenetics.Genotype(new EyeGenetics.Locus(2, 2), new EyeGenetics.Locus(0, 0));
        known.getGenetics().setEyePayload(EyeGenetics.encode(genotype, null, new CompoundTag()));
        var child = villager(helper); child.getGenetics().combine(known.getGenetics(), null, 11);
        var dna = EyeGenetics.decodeGenotype(child.getGenetics().getEyePayload());
        helper.assertTrue(dna.pigmentation().first() == 2 && dna.modifier().first() == 0, "missing father erased known mother's alleles");
        var orphan = villager(helper); orphan.getGenetics().combine(null, null, 29);
        var payload = orphan.getGenetics().getEyePayload();
        orphan.getGenetics().initializeEyesIfMissing();
        helper.assertTrue(payload.equals(orphan.getGenetics().getEyePayload()), "orphan founder was rerolled");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void conversionPreservesEyePayload(GameTestHelper helper) {
        var entity = villager(helper); entity.getGenetics().randomize();
        entity.getTraits().addTrait(Traits.HETEROCHROMIA); entity.getGenetics().finishEyeExpression(83);
        var payload = entity.getGenetics().getEyePayload();
        helper.getLevel().addFreshEntity(entity);
        var zombie = entity.convertTo(net.minecraft.world.entity.EntityType.ZOMBIE_VILLAGER, false);
        helper.assertTrue(zombie instanceof ZombieVillagerEntityMCA, "infection did not use MCA zombie");
        helper.assertTrue(payload.equals(((ZombieVillagerEntityMCA) zombie).getGenetics().getEyePayload()), "infection changed eyes");
        var cured = zombie.convertTo(net.minecraft.world.entity.EntityType.VILLAGER, false);
        helper.assertTrue(cured instanceof VillagerEntityMCA, "cure did not use MCA villager");
        helper.assertTrue(payload.equals(((VillagerEntityMCA) cured).getGenetics().getEyePayload()), "cure changed eyes");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void playerProxyUsesPersistedIdentityAndLocalExpression(GameTestHelper helper) throws ReflectiveOperationException {
        UUID identity = UUID.randomUUID();
        // Keep this fixture out of the server's persistent mca_player_<UUID> saved-data storage.
        var constructor = PlayerSaveData.class.getDeclaredConstructor(net.minecraft.server.level.ServerLevel.class, UUID.class);
        constructor.setAccessible(true);
        var owner = constructor.newInstance(helper.getLevel(), identity);
        helper.assertTrue(owner.isDirty(), "fresh player founder was not marked for persistence");
        var data = owner.getEntityData(); var initial = data.getCompound(EyeGenetics.KEY).copy();
        CompoundTag traits = data.getCompound("Traits").copy(); traits.putBoolean(Traits.HETEROCHROMIA.getId().toString(), true);
        data.put("Traits", traits); owner.setEntityData(data);
        var expected = owner.getEntityData().getCompound(EyeGenetics.KEY);
        CompoundTag savedOwner = owner.save(new CompoundTag(), helper.getLevel().registryAccess());
        savedOwner.getCompound("entityData").getCompound(EyeGenetics.KEY).putString("CallerMutation", "must not reach player owner");
        helper.assertTrue(expected.equals(owner.getEntityData().getCompound(EyeGenetics.KEY)), "player save exposed mutable eye state");
        helper.assertTrue(expected.contains("Local"), "player trait edit did not persist local expression");
        helper.assertTrue(EyeGenetics.decodeGenotype(initial).equals(EyeGenetics.decodeGenotype(expected)), "player trait edit changed genotype");
        var first = VillagerLike.toVillager(owner); var second = VillagerLike.toVillager(owner);
        helper.assertTrue(first.asEntity().getUUID().equals(identity), "proxy used temporary identity");
        helper.assertTrue(expected.equals(first.getGenetics().getEyePayload()) && expected.equals(second.getGenetics().getEyePayload()), "proxy generated unsaved eyes");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void editorAndScalarApiKeepOneOwner(GameTestHelper helper) {
        var entity = villager(helper); CompoundTag legacy = new CompoundTag(); legacy.putFloat("GeneEyeColor", 0.6F);
        legacy.putInt("EyeColor", 0xFF112233); entity.readAdditionalSaveData(legacy);
        var before = entity.getGenetics().getEyePayload();
        CompoundTag server = new CompoundTag(); entity.addAdditionalSaveData(server);
        CompoundTag patch = new CompoundTag(); patch.putFloat("GeneEyeColor", 0.1F); patch.putInt("EyeColor", -1);
        patch.putFloat("GeneSize", 0.9F);
        entity.syncFromEditor(VillagerEditorSyncRequest.mergeAllowedEditorPatch(server, patch));
        helper.assertTrue(before.equals(entity.getGenetics().getEyePayload()), "ordinary editor echo changed genetics/legacy mode");
        helper.assertTrue(entity.getGenetics().getGene(Genetics.SIZE) == 0.9F, "unrelated gene edit lost");
        helper.assertTrue(EyeTextureLayers.getStaticEyeColor(entity, false) == EyeGenetics.resolveLegacyNatural(before.getCompound("LegacyNatural"), false, false, false), "dye clear lost legacy appearance");
        entity.getGenetics().getGenome(Genetics.EYE_COLOR).set(0.2F);
        helper.assertTrue(!entity.getGenetics().getEyePayload().contains("LegacyNatural"), "explicit scalar edit kept legacy mode");
        helper.assertTrue(entity.getGenetics().getGene(Genetics.EYE_COLOR) == entity.getGenetics().getGenome(Genetics.EYE_COLOR).get(), "legacy read routes disagree");
        var dna = entity.getGenetics().getEyePayload();
        try { entity.getGenetics().setGene(Genetics.EYE_COLOR, Float.NaN); helper.fail("NaN scalar accepted"); }
        catch (IllegalArgumentException expected) { helper.assertTrue(dna.equals(entity.getGenetics().getEyePayload()), "invalid edit changed DNA"); }
        entity.getTraits().addTrait(Traits.HETEROCHROMIA); entity.getGenetics().finishEyeExpression(131);
        var local = entity.getGenetics().getEyePayload();
        helper.assertTrue(local.contains("Local"), "trait addition did not generate local effect");
        helper.assertTrue(EyeGenetics.decodeGenotype(dna).equals(EyeGenetics.decodeGenotype(local)), "local effect changed genotype");
        entity.getTraits().removeTrait(Traits.HETEROCHROMIA);
        helper.assertTrue(entity.getGenetics().getNaturalEyeColor(true) == entity.getGenetics().getNaturalEyeColor(false), "removed trait still affects left eye");
        entity.getTraits().addTrait(Traits.HETEROCHROMIA); entity.getGenetics().finishEyeExpression(197);
        helper.assertTrue(local.equals(entity.getGenetics().getEyePayload()), "re-enabled trait rerolled local effect");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void storedBabyKeepsBirthGeneticsAfterParentsDisappear(GameTestHelper helper) {
        var mother = villager(helper); var father = villager(helper);
        mother.getGenetics().randomize(); father.getGenetics().randomize();
        var stack = BabyItem.createItem(mother, father, 263);
        var saved = stack.get(DataComponentsMCA.BABY_NBT).copyTag();
        var expected = saved.getCompound(EyeGenetics.KEY).copy(); mother.discard(); father.discard();
        BlockPos feet = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.absMoveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Stored Eyes"));
        stack.set(DataComponentsMCA.BABY_AGE, Config.getServerConfig().babyItemGrowUpTime);
        var duplicate = stack.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        var children = helper.getLevel().getEntitiesOfClass(VillagerEntityMCA.class, player.getBoundingBox().inflate(3));
        helper.assertTrue(children.size() == 1, "baby placement fixture did not spawn exactly one child");
        helper.assertTrue(expected.equals(children.getFirst().getGenetics().getEyePayload()), "placement rebuilt saved child DNA");
        helper.assertTrue(!saved.getUUID("UUID").equals(children.getFirst().getUUID()), "baby placement reused the stored placeholder UUID");

        BlockPos secondFeet = feet.offset(3, 0, 0);
        helper.getLevel().setBlock(secondFeet.below(), Blocks.STONE.defaultBlockState(), 3);
        player.absMoveTo(secondFeet.getX() + 0.5, secondFeet.getY(), secondFeet.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, duplicate);
        duplicate.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        var placed = helper.getLevel().getEntitiesOfClass(VillagerEntityMCA.class, player.getBoundingBox().inflate(5));
        helper.assertTrue(placed.size() == 2, "duplicated baby item did not spawn a second child");
        helper.assertTrue(placed.stream().map(VillagerEntityMCA::getUUID).distinct().count() == 2, "duplicated babies share an entity UUID");
        helper.assertTrue(placed.stream().allMatch(child -> expected.equals(child.getGenetics().getEyePayload())), "duplicate placement lost inherited eye DNA");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void opaqueFutureEyesSurviveReloadAndDeclineBreeding(GameTestHelper helper) {
        CompoundTag future = new CompoundTag(); future.putInt("Format", 99); future.putString("Evidence", "unchanged");
        var parent = villager(helper); parent.getGenetics().setEyePayload(future);
        CompoundTag saved = new CompoundTag(); parent.save(saved);
        var reloaded = villager(helper); reloaded.load(saved);
        helper.assertTrue(future.equals(reloaded.getGenetics().getEyePayload()), "unknown DNA was replaced on load");
        var child = villager(helper);
        try { child.getGenetics().combine(reloaded.getGenetics(), null, 347); helper.fail("unknown DNA used as a missing parent"); }
        catch (IllegalArgumentException expected) { helper.assertTrue(child.getGenetics().getEyePayload().isEmpty(), "failed birth partially changed child eyes"); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void missingEyeDatapackDoesNotEraseSavedSelection(GameTestHelper helper) {
        var original = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("missing_eye_pack", "rare/iris");
        var entity = villager(helper);
        entity.setEyeTexture(original);
        entity.validateAppearance();
        helper.assertTrue(original.equals(entity.getEyeTexture()), "appearance validation overwrote an unavailable eye selection");

        CompoundTag saved = new CompoundTag();
        entity.save(saved);
        var reloaded = villager(helper);
        reloaded.load(saved);
        helper.assertTrue(original.equals(reloaded.getEyeTexture()), "entity reload permanently lost a disabled eye datapack selection");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void birthFinishesLocalExpressionAfterTraits(GameTestHelper helper) {
        var mother = villager(helper); var father = villager(helper);
        mother.getGenetics().randomize(); father.getGenetics().randomize();
        mother.getTraits().addTrait(Traits.HETEROCHROMIA); mother.getGenetics().finishEyeExpression(1);
        float prior = Config.getInstance().traitInheritChance;
        try {
            // Force this trait's 0.5 inheritance coefficient to probability 1 in the fixture.
            Config.getInstance().traitInheritChance = 2;
            var child = mother.getRelationships().getPregnancy().createChild(Gender.FEMALE, father);
            helper.assertTrue(child.getTraits().hasTrait(Traits.HETEROCHROMIA), "natural child did not inherit fixture trait");
            helper.assertTrue(child.getGenetics().getEyePayload().contains("Local"), "natural child generated expression before inheriting trait");
            var stack = BabyItem.createItem(mother, father, 431);
            var dna = stack.get(DataComponentsMCA.BABY_NBT).copyTag().getCompound(EyeGenetics.KEY);
            var expected = EyeGenetics.localExpression(EyeGenetics.decodeGenotype(dna), net.minecraft.util.RandomSource.create(431 ^ EyeGenetics.LOCAL_SALT));
            helper.assertTrue(expected.equals(EyeGenetics.decodeLocalExpression(dna)), "baby item local expression used wrong seed/order");
            helper.assertTrue(!dna.getCompound("Local").equals(mother.getGenetics().getEyePayload().getCompound("Local")), "child copied mother's somatic expression");
        } finally { Config.getInstance().traitInheritChance = prior; }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void trackedPayloadDoesNotAliasLoadOrSaveTags(GameTestHelper helper) {
        var first = villager(helper); first.getGenetics().randomize();
        CompoundTag saved = new CompoundTag(); first.save(saved);
        var expected = first.getGenetics().getEyePayload();
        var second = villager(helper); second.load(saved);
        saved.getCompound(EyeGenetics.KEY).putString("MutationFromCaller", "must not reach tracker");
        helper.assertTrue(expected.equals(second.getGenetics().getEyePayload()), "load aliased the caller's mutable compound");
        CompoundTag output = new CompoundTag(); second.save(output);
        output.getCompound(EyeGenetics.KEY).putString("MutationFromSave", "must not reach tracker");
        helper.assertTrue(expected.equals(second.getGenetics().getEyePayload()), "save exposed the tracker's mutable compound");
        helper.succeed();
    }
}
