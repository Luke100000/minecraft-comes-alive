package net.conczin.mca.entity;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.SkinVisualIds;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.List;

@PrefixGameTestTemplate(false)
public final class VillagerNbtSummonAppearanceGameTests {
    private static final String MALE = "mca:male_villager";
    private static final String FEMALE = "mca:female_villager";

    private VillagerNbtSummonAppearanceGameTests() {
    }

    @GameTest(batch = "mca_nbt_summon_appearance", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void maleNbtSummonHasRenderableAppearance(GameTestHelper helper) throws CommandSyntaxException {
        checkFreshSummon(helper, MALE, Gender.MALE);
    }

    @GameTest(batch = "mca_nbt_summon_appearance", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void femaleNbtSummonHasRenderableAppearance(GameTestHelper helper) throws CommandSyntaxException {
        checkFreshSummon(helper, FEMALE, Gender.FEMALE);
    }

    @GameTest(batch = "mca_nbt_summon_appearance", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void nbtPassengerHasRenderableAppearance(GameTestHelper helper) throws CommandSyntaxException {
        CompoundTag passenger = freshNbt();
        passenger.putString("id", FEMALE);
        ListTag passengers = new ListTag();
        passengers.add(passenger);
        CompoundTag vehicleNbt = freshNbt();
        vehicleNbt.put("Passengers", passengers);
        Entity vehicle = summon(helper, "minecraft:pig", vehicleNbt, false);
        try {
            helper.assertTrue(vehicle.getPassengers().size() == 1 && vehicle.getFirstPassenger() instanceof VillagerEntityMCA,
                    "vanilla recursive summon did not create the villager passenger");
            VillagerEntityMCA villager = (VillagerEntityMCA) vehicle.getFirstPassenger();
            assertRenderableAppearance(helper, villager, Gender.FEMALE);
        } finally {
            discardTree(vehicle);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_nbt_summon_appearance", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void explicitGenderWithoutClothesHasRenderableAppearance(GameTestHelper helper) throws CommandSyntaxException {
        CompoundTag nbt = freshNbt();
        nbt.putInt("Gender", Gender.FEMALE.getId());
        VillagerEntityMCA villager = (VillagerEntityMCA) summon(helper, MALE, nbt, false);
        try {
            assertRenderableAppearance(helper, villager, Gender.FEMALE);
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_nbt_summon_appearance", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void zombieWithMissingGenderHasRenderableAppearance(GameTestHelper helper) throws CommandSyntaxException {
        checkZombieSummon(helper, "mca:male_zombie_villager", freshNbt(), Gender.MALE);
    }

    @GameTest(batch = "mca_nbt_summon_appearance", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void zombieWithInvalidGenderHasRenderableAppearance(GameTestHelper helper) throws CommandSyntaxException {
        CompoundTag nbt = freshNbt();
        nbt.putInt("Gender", 999);
        checkZombieSummon(helper, "mca:female_zombie_villager", nbt, Gender.FEMALE);
    }

    @GameTest(batch = "mca_nbt_summon_appearance", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void neutralGenderPreservesExplicitAppearance(GameTestHelper helper) throws CommandSyntaxException {
        String skin = "mca:skins/skin/female/4_classic.png";
        String clothes = "mca:skins/clothing/normal/neutral/none/0.png";
        CompoundTag nbt = freshNbt();
        nbt.putInt("Gender", Gender.NEUTRAL.getId());
        nbt.putString("Skin", skin);
        nbt.putString("Clothes", clothes);
        VillagerEntityMCA villager = (VillagerEntityMCA) summon(helper, MALE, nbt, false);
        try {
            helper.assertTrue(villager.getGenetics().getGender() == Gender.NEUTRAL, "valid saved neutral gender was replaced");
            helper.assertTrue(skin.equals(villager.getSkin()) && clothes.equals(villager.getClothes()),
                    "explicit appearance for neutral gender was replaced");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_nbt_summon_appearance", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void explicitAppearanceSurvivesNbtSummon(GameTestHelper helper) throws CommandSyntaxException {
        String skin = "mca:skins/skin/female/4_classic.png";
        String clothes = "mca:skins/clothing/normal/female/none/0.png";
        String hair = "mca:skins/hair/female/3.png";
        int skinDye = 0xFFBC8765;
        int hairDye = 0xFF543210;
        CompoundTag nbt = freshNbt();
        nbt.putInt("Gender", Gender.FEMALE.getId());
        nbt.putString("Skin", skin);
        nbt.putString("Clothes", clothes);
        nbt.putString("HairStyle", hair);
        nbt.putString("HairBase", hair);
        nbt.putBoolean("ClothingLocked", true);
        nbt.putInt("SkinColor", skinDye);
        nbt.putInt("HairColor", hairDye);
        VillagerEntityMCA villager = (VillagerEntityMCA) summon(helper, MALE, nbt, false);
        try {
            assertRenderableAppearance(helper, villager, Gender.FEMALE);
            helper.assertTrue(skin.equals(villager.getSkin()), "explicit body skin was replaced");
            helper.assertTrue(clothes.equals(villager.getClothes()) && villager.isClothingLocked(), "explicit locked clothing was replaced");
            helper.assertTrue(hair.equals(villager.getHairStyleId()) && hair.equals(villager.getLayeredHair(LayeredHair.Category.BASE)),
                    "explicit hair style or base layer was replaced");
            helper.assertTrue(villager.getSkinDye() == skinDye && villager.getHairDye() == hairDye,
                    "explicit skin or hair dye was replaced");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_nbt_summon_appearance", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void initializedAppearanceSurvivesEntityReload(GameTestHelper helper) throws CommandSyntaxException {
        VillagerEntityMCA original = (VillagerEntityMCA) summon(helper, FEMALE, new CompoundTag(), true);
        CompoundTag saved;
        Gender gender;
        String skin;
        String clothes;
        String hair;
        try {
            gender = original.getGenetics().getGender();
            helper.assertTrue(gender == Gender.MALE || gender == Gender.FEMALE, "normal summon did not initialize gender");
            assertRenderableAppearance(helper, original, gender);
            skin = original.getSkin();
            clothes = original.getClothes();
            hair = original.getHairStyleId();
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, original.registryAccess());
            original.saveWithoutId(output);
            saved = output.buildResult();
        } finally {
            original.discard();
        }
        saved.remove("UUID");
        VillagerEntityMCA loaded = (VillagerEntityMCA) summon(helper, FEMALE, saved, false);
        try {
            assertRenderableAppearance(helper, loaded, gender);
            helper.assertTrue(skin.equals(loaded.getSkin()) && clothes.equals(loaded.getClothes()) && hair.equals(loaded.getHairStyleId()),
                    "reloading an initialized villager replaced its appearance");
        } finally {
            loaded.discard();
        }
        helper.succeed();
    }

    private static void checkFreshSummon(GameTestHelper helper, String type, Gender gender) throws CommandSyntaxException {
        VillagerEntityMCA villager = (VillagerEntityMCA) summon(helper, type, freshNbt(), false);
        try {
            assertRenderableAppearance(helper, villager, gender);
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    private static void checkZombieSummon(GameTestHelper helper, String type, CompoundTag nbt, Gender gender) throws CommandSyntaxException {
        ZombieVillagerEntityMCA zombie = (ZombieVillagerEntityMCA) summon(helper, type, nbt, false);
        try {
            assertRenderableAppearance(helper, zombie, gender);
        } finally {
            zombie.discard();
        }
        helper.succeed();
    }

    private static void assertRenderableAppearance(GameTestHelper helper, VillagerLike<?> villager, Gender gender) {
        helper.assertTrue(villager.getGenetics().getGender() == gender,
                "expected " + gender + " gender, got " + villager.getGenetics().getGender() + " (body skin: " + villager.getSkin() + ")");
        helper.assertTrue(villager.getSkin().isBlank() || SkinVisualIds.isBodySkin(villager.getSkin()),
                "body skin is neither a valid catalog skin nor the built-in gendered fallback: " + villager.getSkin());
        helper.assertTrue(!villager.getClothes().isBlank() && SkinVisualIds.isClothing(villager.getClothes()),
                "villager has no valid clothing texture: " + villager.getClothes());
    }

    private static CompoundTag freshNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putBoolean("NoAI", true);
        nbt.putBoolean("PersistenceRequired", true);
        return nbt;
    }

    private static Entity summon(GameTestHelper helper, String type, CompoundTag nbt, boolean finalizeSpawn) throws CommandSyntaxException {
        var entityType = BuiltInRegistries.ENTITY_TYPE.get(Identifier.parse(type)).orElseThrow();
        var source = helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel());
        return SummonCommand.createEntity(source, entityType, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3))), nbt, finalizeSpawn);
    }

    private static void discardTree(Entity root) {
        List<Entity> entities = root.getSelfAndPassengers().toList();
        for (Entity entity : entities.reversed()) {
            entity.discard();
        }
    }
}
