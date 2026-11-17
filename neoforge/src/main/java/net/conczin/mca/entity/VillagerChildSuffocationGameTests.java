package net.conczin.mca.entity;

import net.conczin.mca.Config;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.registry.DataComponentsMCA;
import net.conczin.mca.registry.ItemsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
@PrefixGameTestTemplate(false)
public final class VillagerChildSuffocationGameTests {
    private VillagerChildSuffocationGameTests() {
    }

    @GameTest(batch = "mca_child_suffocation", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void naturalBirthFromLowSeatedMotherDoesNotSpawnChildInsideFloor(GameTestHelper helper) {
        BlockPos floorPos = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos fatherPos = helper.absolutePos(new BlockPos(5, 1, 3));
        helper.getLevel().setBlock(floorPos, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(floorPos.east(), Blocks.STONE.defaultBlockState(), 3);

        VillagerEntityMCA mother = spawnAdult(helper, Gender.FEMALE, floorPos.above());
        VillagerEntityMCA father = spawnAdult(helper, Gender.MALE, fatherPos);
        mother.setPos(floorPos.getX() + 0.5D, floorPos.getY() + 0.1D, floorPos.getZ() + 0.5D);
        helper.assertTrue(!mother.isInWall(), "fixture seated mother was already suffocating in the floor");

        mother.getRelationships().marry(father);
        father.getRelationships().marry(mother);
        var pregnancy = mother.getRelationships().getPregnancy();
        pregnancy.setPregnant(true);
        pregnancy.setBabyAge(Config.getInstance().babyItemGrowUpTime - 60);
        pregnancy.tick();

        List<VillagerEntityMCA> children = helper.getLevel().getEntitiesOfClass(
                VillagerEntityMCA.class,
                mother.getBoundingBox().inflate(4.0D),
                villager -> villager != mother && villager != father && villager.getAgeState() == AgeState.TODDLER
        );
        helper.assertTrue(children.size() == 1, "natural pregnancy lifecycle did not spawn exactly one toddler child");
        VillagerEntityMCA child = children.getFirst();

        helper.assertTrue(!child.isInWall(), "newborn child inherited the seated mother's low position and spawned inside the floor");
        helper.succeed();
    }

    @GameTest(batch = "mca_child_suffocation", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 40)
    public static void growthUnderLowClearanceDoesNotLeaveChildSuffocating(GameTestHelper helper) {
        checkGrowthUnderLowClearance(helper, false);
    }

    @GameTest(batch = "mca_child_suffocation", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void growthBeforeFirstTickDoesNotLeaveChildSuffocating(GameTestHelper helper) {
        checkGrowthUnderLowClearance(helper, true);
    }

    private static void checkGrowthUnderLowClearance(GameTestHelper helper, boolean beforeFirstTick) {
        BlockPos feet = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(feet.east().below(), Blocks.STONE.defaultBlockState(), 3);

        int startAge = AgeState.TODDLER.toAge() + 88_000;
        VillagerEntityMCA child = VillagerFactory.newVillager(helper.getLevel())
                .withGender(Gender.MALE)
                .withAge(startAge)
                .withPosition(Vec3.atBottomCenterOf(feet))
                .spawn(MobSpawnType.BREEDING);
        child.getGenetics().setGene(Genetics.SIZE, 0.5F);
        child.getGenetics().setGene(Genetics.WIDTH, 0.5F);
        child.refreshDimensions();

        child.setNoAi(true);
        if (!beforeFirstTick) {
            // Advance the real entity lifecycle, independently of the fixture chunk's ticking status.
            helper.getLevel().tickNonPassenger(child);
        }
        helper.assertTrue(beforeFirstTick ? child.tickCount == 0 : child.tickCount > 0,
                "fixture did not exercise the intended resize lifecycle");
        helper.assertTrue(!child.isInWall(), "fixture child was already suffocating before the ceiling was placed");
        helper.getLevel().setBlock(feet.above(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(!child.isInWall(), "fixture ceiling already covered the child's eyes before growth");

        Vec3 beforeGrowth = child.position();
        float beforeHeight = child.getBbHeight();
        child.setAge(startAge + 3_000);
        child.refreshDimensions();

        helper.assertTrue(child.getBbHeight() > beforeHeight, "fixture did not grow the child");
        helper.assertTrue(!child.isInWall(), "growth left the child suffocating under low clearance; before="
                + beforeGrowth + " after=" + child.position() + " height=" + beforeHeight + "->" + child.getBbHeight());
        child.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_child_suffocation", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void youngVillagerIgnoresInWallDamage(GameTestHelper helper) {
        BlockPos feet = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);

        VillagerEntityMCA child = VillagerFactory.newVillager(helper.getLevel())
                .withGender(Gender.MALE)
                .withAge(AgeState.CHILD.toAge())
                .withPosition(Vec3.atBottomCenterOf(feet))
                .spawn(MobSpawnType.BREEDING);
        float healthBefore = child.getHealth();

        boolean damaged = child.hurt(helper.getLevel().damageSources().inWall(), 1.0F);

        helper.assertTrue(!damaged, "young villager accepted IN_WALL damage");
        helper.assertTrue(child.getHealth() == healthBefore, "young villager lost health to IN_WALL damage");
        helper.succeed();
    }

    @GameTest(batch = "mca_child_suffocation", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void blockedBabyItemPlacementIsRejectedWithoutConsumingItem(GameTestHelper helper) {
        BlockPos blockedFeet = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlock(blockedFeet, Blocks.STONE.defaultBlockState(), 3);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.absMoveTo(blockedFeet.getX() + 0.5D, blockedFeet.getY(), blockedFeet.getZ() + 0.5D);

        ItemStack stack = ItemsMCA.BABY_BOY.getDefaultInstance();
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Placement Test"));
        stack.set(DataComponentsMCA.BABY_AGE, Config.getServerConfig().babyItemGrowUpTime);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        int villagersBefore = helper.getLevel().getEntitiesOfClass(
                VillagerEntityMCA.class,
                player.getBoundingBox().inflate(3.0D)
        ).size();

        stack.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        int villagersAfter = helper.getLevel().getEntitiesOfClass(
                VillagerEntityMCA.class,
                player.getBoundingBox().inflate(3.0D)
        ).size();
        helper.assertTrue(stack.getCount() == 1, "blocked baby placement consumed the baby item");
        helper.assertTrue(villagersAfter == villagersBefore, "blocked baby placement spawned a villager elsewhere");
        helper.succeed();
    }

    @GameTest(batch = "mca_child_suffocation", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void babyItemPlacementUnderLowCeilingIsRejectedWithoutConsumingItem(GameTestHelper helper) {
        BlockPos feet = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(feet.above(), Blocks.STONE.defaultBlockState(), 3);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.absMoveTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);

        ItemStack stack = ItemsMCA.BABY_BOY.getDefaultInstance();
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Low Ceiling Placement Test"));
        stack.set(DataComponentsMCA.BABY_AGE, Config.getServerConfig().babyItemGrowUpTime);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        stack.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        List<VillagerEntityMCA> villagers = helper.getLevel().getEntitiesOfClass(
                VillagerEntityMCA.class,
                player.getBoundingBox().inflate(3.0D)
        );
        helper.assertTrue(stack.getCount() == 1, "low-ceiling baby placement consumed the baby item");
        helper.assertTrue(villagers.isEmpty(), "low-ceiling baby placement spawned a villager");
        helper.succeed();
    }

    @GameTest(batch = "mca_child_suffocation", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void babyItemPlacementAllowsEntityOverlapWhenBlocksAreClear(GameTestHelper helper) {
        BlockPos feet = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.absMoveTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
        spawnAdult(helper, Gender.FEMALE, feet);

        ItemStack stack = ItemsMCA.BABY_BOY.getDefaultInstance();
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Placement Test"));
        stack.set(DataComponentsMCA.BABY_AGE, Config.getServerConfig().babyItemGrowUpTime);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        stack.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        List<VillagerEntityMCA> villagers = helper.getLevel().getEntitiesOfClass(
                VillagerEntityMCA.class,
                player.getBoundingBox().inflate(2.0D)
        );
        helper.assertTrue(stack.isEmpty(), "clear baby placement did not consume the baby item");
        helper.assertTrue(villagers.size() == 2, "clear baby placement did not add exactly one villager at the intended position; count=" + villagers.size());
        VillagerEntityMCA placed = villagers.stream().filter(VillagerEntityMCA::isBaby).findFirst().orElse(null);
        helper.assertTrue(placed != null, "clear baby placement did not leave a young villager at the intended position");
        helper.assertTrue(!placed.isInWall(), "clear baby placement spawned the child suffocating");
        helper.succeed();
    }

    @GameTest(batch = "mca_child_suffocation", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void sirbenBabyUsesSharedPlacementAndAddsSirbenTrait(GameTestHelper helper) {
        BlockPos feet = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.absMoveTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);

        ItemStack stack = ItemsMCA.SIRBEN_BABY_BOY.getDefaultInstance();
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Sirben Placement Test"));
        stack.set(DataComponentsMCA.BABY_AGE, Config.getServerConfig().babyItemGrowUpTime);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);

        stack.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        VillagerEntityMCA child = helper.getLevel().getEntitiesOfClass(
                VillagerEntityMCA.class,
                player.getBoundingBox().inflate(2.0D),
                VillagerEntityMCA::isBaby
        ).stream().findFirst().orElse(null);
        helper.assertTrue(stack.isEmpty(), "Sirben baby placement did not consume the baby item");
        helper.assertTrue(child != null, "Sirben baby placement did not spawn a young villager");
        helper.assertTrue(child.getTraits().hasTrait(Traits.SIRBEN), "Sirben baby placement lost the Sirben trait");
        helper.assertTrue(!child.isInWall(), "Sirben baby placement spawned the child suffocating");
        helper.succeed();
    }

    private static VillagerEntityMCA spawnAdult(GameTestHelper helper, Gender gender, BlockPos feet) {
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withGender(gender)
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(feet))
                .spawn(MobSpawnType.BREEDING);
        villager.getGenetics().setGene(Genetics.SIZE, 0.5F);
        villager.getGenetics().setGene(Genetics.WIDTH, 0.5F);
        villager.refreshDimensions();
        return villager;
    }
}
