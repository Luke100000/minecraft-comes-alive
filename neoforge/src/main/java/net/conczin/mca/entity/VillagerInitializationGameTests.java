package net.conczin.mca.entity;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.resources.BodySkinList;
import net.conczin.mca.resources.HairStyleList;
import net.conczin.mca.resources.SkinVisualIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.List;

@PrefixGameTestTemplate(false)
public final class VillagerInitializationGameTests {
    private static final String BATCH = "mca_villager_initialization";
    private static final String MALE = "mca:male_villager";
    private static final String FEMALE = "mca:female_villager";
    private static final String ZOMBIE = "mca:male_zombie_villager";
    private static final float EPSILON = 0.0001F;

    private VillagerInitializationGameTests() {
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void freshEntityHasCachedDimensions(GameTestHelper helper) {
        for (var type : List.of(EntitiesMCA.MALE_VILLAGER, EntitiesMCA.FEMALE_VILLAGER,
                EntitiesMCA.MALE_ZOMBIE_VILLAGER, EntitiesMCA.FEMALE_ZOMBIE_VILLAGER)) {
            Entity entity = type.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
            try {
                assertCache(helper, entity);
            } finally {
                entity.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void minimalNbtPassengerSitsAtCorrectHeight(GameTestHelper helper) throws CommandSyntaxException {
        checkPassenger(helper, "minecraft:pig", FEMALE);
        checkPassenger(helper, "minecraft:horse", FEMALE);
        checkPassenger(helper, "minecraft:horse", ZOMBIE);
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void missingMcaAgeUsesVanillaAge(GameTestHelper helper) throws CommandSyntaxException {
        int age = AgeState.TODDLER.toAge() + AgeState.getStageDuration() / 2;
        CompoundTag nbt = freshNbt();
        nbt.putInt("Age", age);
        VillagerEntityMCA villager = (VillagerEntityMCA) summon(helper, MALE, nbt, false);
        try {
            assertAge(helper, villager, AgeState.TODDLER, age);
        } finally {
            villager.discard();
        }

        nbt.putInt("Age", -AgeState.getMaxAge() * 2);
        villager = (VillagerEntityMCA) summon(helper, MALE, nbt, false);
        try {
            assertAge(helper, villager, AgeState.BABY, -AgeState.getMaxAge());
            villager.aiStep();
            helper.assertTrue(villager.getAgeState() == AgeState.BABY, "normal aging restored an invalid MCA age stage");
            helper.assertTrue(villager.getAge() == -AgeState.getMaxAge() + 1, "vanilla age was not normalized before aging");
        } finally {
            villager.discard();
        }

        CompoundTag babyNbt = freshNbt();
        babyNbt.putBoolean("IsBaby", true);
        ZombieVillagerEntityMCA zombie = (ZombieVillagerEntityMCA) summon(helper, ZOMBIE, babyNbt, false);
        try {
            helper.assertTrue(zombie.getAgeState() == AgeState.BABY, "missing MCA zombie age lost vanilla baby state");
            assertCache(helper, zombie);
        } finally {
            zombie.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void partialAgePreservesExplicitGrowthAndAppearance(GameTestHelper helper) throws CommandSyntaxException {
        int age = AgeState.CHILD.toAge() + AgeState.getStageDuration() / 2;
        CompoundTag nbt = freshNbt();
        nbt.putInt("Age", age);
        nbt.putInt("AgeState", AgeState.CHILD.ordinal());
        nbt.putInt("GrowthAmount", age);
        nbt.putString("Skin", "");
        nbt.putString("HairStyle", "");
        nbt.putString("Hair", "");
        VillagerEntityMCA villager = (VillagerEntityMCA) summon(helper, FEMALE, nbt, false);
        try {
            assertAge(helper, villager, AgeState.CHILD, age);
            helper.assertTrue(villager.getSkin().isBlank() && villager.getHairStyleId().isBlank(),
                    "explicit blank appearance was replaced");
            nbt.remove("GrowthAmount");
            nbt.putInt("Age", 0);
            villager.readAdditionalSaveDataForEditor(nbt);
            assertAge(helper, villager, AgeState.ADULT, 0);
            nbt.remove("Age");
            villager.readAdditionalSaveDataForEditor(nbt);
            assertAge(helper, villager, AgeState.CHILD, AgeState.CHILD.toAge());
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void restoredAgePreservesCooldownAndLock(GameTestHelper helper) throws CommandSyntaxException {
        CompoundTag nbt = freshNbt();
        nbt.putInt("Age", 600);
        VillagerEntityMCA villager = (VillagerEntityMCA) summon(helper, MALE, nbt, false);
        try {
            helper.assertTrue(villager.getAge() == 600, "adult breeding cooldown was replaced");
            villager.aiStep();
            helper.assertTrue(villager.getAge() == 599, "adult breeding cooldown did not advance");
        } finally {
            villager.discard();
        }

        nbt = freshNbt();
        nbt.putInt("Age", -AgeState.getMaxAge() * 2);
        nbt.putBoolean("AgeLocked", true);
        villager = (VillagerEntityMCA) summon(helper, MALE, nbt, false);
        try {
            assertAge(helper, villager, AgeState.BABY, -AgeState.getMaxAge());
            villager.aiStep();
            helper.assertTrue(villager.isAgeLocked() && villager.getAge() == -AgeState.getMaxAge(),
                    "age normalization removed the age lock");
        } finally {
            villager.discard();
        }

        nbt = freshNbt();
        nbt.putInt("GrowthAmount", -AgeState.getMaxAge() * 2);
        villager = (VillagerEntityMCA) summon(helper, MALE, nbt, false);
        try {
            assertAge(helper, villager, AgeState.BABY, -AgeState.getMaxAge());
            villager.aiStep();
            helper.assertTrue(villager.getAge() == -AgeState.getMaxAge() + 1, "legacy growth was not normalized before aging");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void missingAppearanceUsesLoadedCatalog(GameTestHelper helper) throws CommandSyntaxException {
        for (String type : List.of(MALE, ZOMBIE)) {
            Entity entity = summon(helper, type, freshNbt(), false);
            VillagerLike<?> villager = (VillagerLike<?>) entity;
            try {
                Gender gender = villager.getGenetics().getGender();
                helper.assertTrue(!BodySkinList.getInstance().getPool(gender).getEntries().isEmpty(), "body catalog fixture is empty");
                helper.assertTrue(!HairStyleList.getInstance().getPool(gender).getEntries().isEmpty(), "hair catalog fixture is empty");
                helper.assertTrue(!villager.getSkin().isBlank() && SkinVisualIds.isBodySkin(villager.getSkin()), "missing skin was not restored from the catalog");
                helper.assertTrue(!villager.getHairStyleId().isBlank() && SkinVisualIds.isHairStyle(villager.getHairStyleId()), "missing hair was not restored from the catalog");
            } finally {
                entity.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void changedScaleInputsRefreshDimensions(GameTestHelper helper) throws CommandSyntaxException {
        for (String type : List.of(MALE, ZOMBIE)) {
            Entity entity = summon(helper, type, freshNbt(), false);
            VillagerLike<?> villager = (VillagerLike<?>) entity;
            try {
                villager.getGenetics().setGene(Genetics.SIZE, 0.1F);
                assertCache(helper, entity);
                villager.getGenetics().setGene(Genetics.WIDTH, 0.1F);
                assertCache(helper, entity);
                villager.getGenetics().setGender(Gender.FEMALE);
                assertCache(helper, entity);
                villager.getTraits().addTrait(Traits.DWARFISM);
                assertCache(helper, entity);
                villager.getTraits().removeTrait(Traits.DWARFISM);
                assertCache(helper, entity);
            } finally {
                entity.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void syncedScaleSnapshotUsesFinalDimensions(GameTestHelper helper) throws CommandSyntaxException {
        int age = AgeState.TODDLER.toAge() + AgeState.getStageDuration() / 2;
        CompoundTag nbt = freshNbt();
        nbt.putInt("Age", age);
        nbt.putInt("AgeState", AgeState.TODDLER.ordinal());
        nbt.putInt("GrowthAmount", age);
        VillagerEntityMCA sender = (VillagerEntityMCA) summon(helper, MALE, nbt, false);
        VillagerEntityMCA replica = EntitiesMCA.MALE_VILLAGER.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
        try {
            // The server replica owns its vanilla age independently of the client's visual age anchor.
            replica.setAge(age);
            sender.getGenetics().setGene(Genetics.SIZE, 0.1F);
            sender.getGenetics().setGene(Genetics.WIDTH, 0.8F);
            sender.getTraits().addTrait(Traits.DWARFISM);
            replica.getEntityData().assignValues(sender.getEntityData().getNonDefaultValues());
            assertAge(helper, replica, AgeState.TODDLER, age);
            assertNear(helper, replica.getBbHeight(), sender.getBbHeight(), "initial synchronized height");
            sender.getEntityData().packDirty();
            int nextAge = AgeState.CHILD.toAge() + AgeState.getStageDuration() / 3;
            sender.setAge(nextAge);
            replica.setAge(nextAge);
            sender.getTraits().removeTrait(Traits.DWARFISM);
            replica.getEntityData().assignValues(sender.getEntityData().packDirty());
            assertAge(helper, replica, AgeState.CHILD, nextAge);
            assertNear(helper, replica.getBbHeight(), sender.getBbHeight(), "updated synchronized height");
        } finally {
            replica.discard();
            sender.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void normalSpawnReloadAndConversionRetainDimensions(GameTestHelper helper) throws CommandSyntaxException {
        VillagerEntityMCA original = (VillagerEntityMCA) summon(helper, FEMALE, new CompoundTag(), true);
        ZombieVillagerEntityMCA converted = EntitiesMCA.FEMALE_ZOMBIE_VILLAGER.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
        CompoundTag saved;
        try {
            assertCache(helper, original);
            original.getTraits().addTrait(Traits.DWARFISM);
            original.getGenetics().setGene(Genetics.WIDTH, 0.2F);
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, original.registryAccess());
            original.saveWithoutId(output);
            saved = output.buildResult();
            converted.copyVillagerAttributesFrom(original);
            assertCache(helper, converted);
        } finally {
            original.discard();
            converted.discard();
        }
        saved.remove("UUID");
        VillagerEntityMCA loaded = (VillagerEntityMCA) summon(helper, FEMALE, saved, false);
        try {
            assertCache(helper, loaded);
            helper.assertTrue(loaded.getTraits().hasTrait(Traits.DWARFISM), "reload lost explicit trait");
            assertNear(helper, loaded.getGenetics().getGene(Genetics.WIDTH), 0.2F, "saved width gene");
        } finally {
            loaded.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void breedingFinalizationRetainsInheritedScale(GameTestHelper helper) {
        VillagerEntityMCA child = EntitiesMCA.FEMALE_VILLAGER.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
        try {
            child.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3))));
            child.setAge(AgeState.TODDLER.toAge());
            child.getGenetics().setGene(Genetics.SIZE, 0.2F);
            child.getGenetics().setGene(Genetics.WIDTH, 0.8F);
            child.getTraits().addTrait(Traits.DWARFISM);
            child.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(child.blockPosition()), EntitySpawnReason.BREEDING, null);
            assertNear(helper, child.getGenetics().getGene(Genetics.SIZE), 0.2F, "inherited size");
            assertNear(helper, child.getGenetics().getGene(Genetics.WIDTH), 0.8F, "inherited width");
            helper.assertTrue(child.getTraits().hasTrait(Traits.DWARFISM), "breeding randomized inherited traits");
            assertAge(helper, child, AgeState.TODDLER, AgeState.TODDLER.toAge());
        } finally {
            child.discard();
        }
        helper.succeed();
    }

    private static void checkPassenger(GameTestHelper helper, String vehicleType, String passengerType) throws CommandSyntaxException {
        CompoundTag passengerNbt = freshNbt();
        passengerNbt.putString("id", passengerType);
        ListTag passengers = new ListTag();
        passengers.add(passengerNbt);
        CompoundTag vehicleNbt = freshNbt();
        vehicleNbt.put("Passengers", passengers);
        Entity vehicle = summon(helper, vehicleType, vehicleNbt, false);
        try {
            if (vehicle instanceof AbstractHorse horse) {
                horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
                helper.assertTrue(horse.isSaddled(), "horse fixture was not saddled");
            }
            Entity passenger = vehicle.getFirstPassenger();
            helper.assertTrue(passenger instanceof VillagerLike<?>, "missing MCA passenger");
            VillagerLike<?> villager = (VillagerLike<?>) passenger;
            helper.assertTrue(villager.getAgeState() == AgeState.ADULT, "minimal NBT did not initialize adult age");
            vehicle.positionRider(passenger);
            assertCache(helper, passenger);
            assertNear(helper, vehicle.getPassengerRidingPosition(passenger).y - passenger.getY(),
                    villager.getVisualVerticalScaleFactor() * 0.6F,
                    "passenger seat offset");
        } finally {
            for (Entity entity : vehicle.getSelfAndPassengers().toList().reversed()) {
                entity.discard();
            }
        }
    }

    private static void assertAge(GameTestHelper helper, VillagerEntityMCA villager, AgeState stage, int age) {
        helper.assertTrue(villager.getAgeState() == stage, "expected " + stage + ", got " + villager.getAgeState());
        float expectedHeight = stage.getHeight() + AgeState.getDelta(age) * (stage.getNext().getHeight() - stage.getHeight());
        assertNear(helper, villager.getVillagerDimensions().getHeight(), expectedHeight, "growth height");
        assertCache(helper, villager);
    }

    private static void assertCache(GameTestHelper helper, Entity entity) {
        EntityDimensions fresh = entity.getDimensions(entity.getPose());
        assertNear(helper, entity.getBbWidth(), fresh.width(), "cached width");
        assertNear(helper, entity.getBbHeight(), fresh.height(), "cached height");
        assertNear(helper, entity.getEyeHeight(), fresh.eyeHeight(), "cached eye height");
        assertNear(helper, entity.getVehicleAttachmentPoint(entity).y,
                fresh.attachments().get(EntityAttachment.VEHICLE, 0, entity.getYRot()).y, "cached vehicle attachment");
    }

    private static void assertNear(GameTestHelper helper, double actual, double expected, String label) {
        helper.assertTrue(Math.abs(actual - expected) < EPSILON, label + ": expected " + expected + ", got " + actual);
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
}
