package net.conczin.mca.entity.ai;

import com.mojang.authlib.GameProfile;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.item.BabyItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@PrefixGameTestTemplate(false)
public final class ProcreationInventoryGameTests {
    private ProcreationInventoryGameTests() {
    }

    @GameTest(batch = "mca_procreation_inventory", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void fullCreativeInventoryDeliversToVillager(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillager(helper);
        Player player = createPlayer(helper, true);
        fillPlayerInventory(player);

        boolean succeeded = villager.getRelationships().getPregnancy().procreate(player);

        helper.assertTrue(succeeded, "Baby delivery failed even though villager has room");
        helper.assertTrue(countBabies(player) == 0, "Full Creative inventory unexpectedly received baby");
        helper.assertTrue(countBabies(villager) >= 1, "Creative inventory consumed baby instead of villager receiving it");
        helper.succeed();
    }

    @GameTest(batch = "mca_procreation_inventory", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void fullSurvivalInventoryDeliversToVillager(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillager(helper);
        Player player = createPlayer(helper, false);
        fillPlayerInventory(player);

        boolean succeeded = villager.getRelationships().getPregnancy().procreate(player);

        helper.assertTrue(succeeded, "Baby delivery failed even though villager has room");
        helper.assertTrue(countBabies(villager) >= 1, "Full Survival inventory did not fall back to villager");
        helper.succeed();
    }

    @GameTest(batch = "mca_procreation_inventory", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void freePlayerSlotReceivesBabyFirst(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillager(helper);
        Player player = createPlayer(helper, true);

        boolean succeeded = villager.getRelationships().getPregnancy().procreate(player);

        helper.assertTrue(succeeded, "Baby delivery failed despite available player slot");
        helper.assertTrue(countBabies(player) >= 1, "Player with room did not receive baby");
        helper.assertTrue(countBabies(villager) == 0, "Baby delivered to villager instead of player");
        helper.succeed();
    }

    @GameTest(batch = "mca_procreation_inventory", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void bothInventoriesFullRejectsWithoutBaby(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillager(helper);
        Player player = createPlayer(helper, true);
        fillPlayerInventory(player);
        fillVillagerInventory(villager);

        boolean succeeded = villager.getRelationships().getPregnancy().procreate(player);

        helper.assertTrue(!succeeded, "Both inventories full must reject procreation");
        helper.assertTrue(countBabies(player) == 0 && countBabies(villager) == 0,
                "Rejected procreation created a baby in an inventory");
        helper.succeed();
    }

    @GameTest(batch = "mca_procreation_inventory", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void singleFreeSlotCannotHoldTwins(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillager(helper);
        Player player = createPlayer(helper, true);
        fillPlayerInventory(player);
        fillVillagerInventory(villager);
        villager.getInventory().setItem(0, ItemStack.EMPTY);

        helper.assertTrue(villager.getRelationships().getPregnancy().hasRoomForBabies(player, 1),
                "One free villager slot must accommodate one baby");
        helper.assertTrue(!villager.getRelationships().getPregnancy().hasRoomForBabies(player, 2),
                "One free slot must not be considered sufficient for twins");
        helper.succeed();
    }

    @GameTest(batch = "mca_procreation_inventory", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void fullInventoriesPreventAnimationAndCooldown(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillager(helper);
        Player player = createPlayer(helper, true);
        fillPlayerInventory(player);
        fillVillagerInventory(villager);

        BreedableRelationship relationship = villager.getRelationships();
        boolean started = relationship.startProcreating(player, helper.getLevel().getGameTime());

        helper.assertTrue(!started, "Full inventories must reject procreation before the animation");
        helper.assertTrue(!relationship.isProcreating(), "Rejected procreation started the animation");
        helper.assertTrue(relationship.mayProcreateAgain(helper.getLevel().getGameTime()),
                "Rejected procreation incorrectly consumed the cooldown");
        helper.succeed();
    }

    @GameTest(batch = "mca_procreation_inventory", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void insufficientTwinCapacityPreventsAnimation(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillager(helper);
        Player player = createPlayer(helper, false);
        fillPlayerInventory(player);
        fillVillagerInventory(villager);
        villager.getInventory().setItem(0, ItemStack.EMPTY);

        float previousChance = Config.getInstance().twinBabyChance;
        boolean started;
        try {
            Config.getInstance().twinBabyChance = 1.0f;
            started = villager.getRelationships().startProcreating(player, helper.getLevel().getGameTime());
        } finally {
            Config.getInstance().twinBabyChance = previousChance;
        }

        helper.assertTrue(!started, "Insufficient capacity for multiple babies must prevent the animation");
        helper.assertTrue(!villager.getRelationships().isProcreating(), "Twins triggered an animation despite insufficient capacity");
        helper.succeed();
    }

    @GameTest(batch = "mca_procreation_inventory", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void deliveryRejectionAfterAnimationRefundsCooldown(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillager(helper);
        VillagerEntityMCA spouse = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withGender(Gender.MALE)
                .withPosition(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(7, 2, 4))))
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.getRelationships().marry(spouse);
        spouse.getRelationships().marry(villager);
        BreedableRelationship relationship = villager.getRelationships();
        helper.assertTrue(relationship.startProcreating(spouse, helper.getLevel().getGameTime()),
                "Procreation with available slots failed to start");
        fillVillagerInventory(villager); // Slots may become occupied after the animation starts.
        for (int i = 0; i <= 60; i++) {
            relationship.tick(1);
        }

        helper.assertTrue(!relationship.isProcreating(), "Rejected delivery left the procreation animation stuck");
        helper.assertTrue(relationship.mayProcreateAgain(helper.getLevel().getGameTime()),
                "Rejected delivery incorrectly consumed the procreation cooldown");
        helper.assertTrue(countBabies(villager) == 0, "Rejected delivery unexpectedly created a baby");
        helper.succeed();
    }

    @GameTest(batch = "mca_procreation_inventory", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void saveReloadCancelsPendingTwinsAndRefundsCooldown(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillager(helper);
        Player player = createPlayer(helper, false);
        BreedableRelationship relationship = villager.getRelationships();
        helper.assertTrue(relationship.startProcreating(player, helper.getLevel().getGameTime()),
                "could not begin procreation before save");
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        villager.saveWithoutId(output);
        CompoundTag saved = output.buildResult();
        villager.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        helper.assertTrue(!relationship.isProcreating(), "reload preserved an animation without its rolled baby count");
        helper.assertTrue(relationship.mayProcreateAgain(helper.getLevel().getGameTime()),
                "interrupted procreation kept the cooldown");
        relationship.tick(1);
        helper.assertTrue(countBabies(player) == 0 && countBabies(villager) == 0,
                "reload created babies after the attempt was cancelled");
        helper.succeed();
    }

    private static VillagerEntityMCA spawnVillager(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(4, 2, 4));
        return VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withGender(Gender.FEMALE)
                .withPosition(Vec3.atBottomCenterOf(pos))
                .spawn(EntitySpawnReason.STRUCTURE);
    }

    private static Player createPlayer(GameTestHelper helper, boolean creative) {
        ServerPlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ProcreationTest"));
        player.getAbilities().instabuild = creative;
        return player;
    }

    private static void fillPlayerInventory(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE));
        }
    }

    private static void fillVillagerInventory(VillagerEntityMCA villager) {
        for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
            villager.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE));
        }
    }

    private static long countBabies(Player player) {
        long count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).getItem() instanceof BabyItem) count++;
        }
        return count;
    }

    private static long countBabies(VillagerEntityMCA villager) {
        long count = 0;
        for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
            if (villager.getInventory().getItem(slot).getItem() instanceof BabyItem) {
                count++;
            }
        }
        return count;
    }
}
