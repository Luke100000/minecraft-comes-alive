package net.conczin.mca.entity;

import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.Mourning;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class VillagerEquipmentOwnershipGameTests {
    private VillagerEquipmentOwnershipGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void deathDropsBorrowedEquipmentOnceAndDiscardsProps(GameTestHelper helper) {
        VillagerEntityMCA villager = spawn(helper);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        villager.getInventory().setItem(0, sword);
        villager.setItemSlot(EquipmentSlot.MAINHAND, sword);
        villager.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        villager.setItemSlot(EquipmentSlot.OFFHAND, InventoryUtils.temporary(new ItemStack(Items.DIAMOND_SWORD)));
        villager.setGuaranteedDrop(EquipmentSlot.OFFHAND);
        var bounds = villager.getBoundingBox().inflate(2);
        villager.setHealth(0);
        villager.die(helper.getLevel().damageSources().genericKill());
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds);
        try {
            int swords = drops.stream().map(ItemEntity::getItem).filter(stack -> stack.is(Items.IRON_SWORD))
                    .mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(swords == 1, "borrowed inventory sword dropped " + swords + " times");
            helper.assertTrue(drops.stream().noneMatch(drop -> drop.getItem().is(Items.DIAMOND_SWORD)),
                    "temporary equipment escaped as a death drop");
        } finally {
            drops.forEach(ItemEntity::discard);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void saveReloadKeepsBorrowedAndRealEquipmentOnceAndOmitsProps(GameTestHelper helper) {
        VillagerEntityMCA villager = spawn(helper);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        villager.getInventory().setItem(0, sword);
        villager.setItemSlot(EquipmentSlot.MAINHAND, sword);
        villager.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        villager.setItemSlot(EquipmentSlot.HEAD, InventoryUtils.temporary(new ItemStack(Items.DIAMOND_HELMET)));
        CompoundTag saved = villager.saveWithoutId(new CompoundTag());
        VillagerEntityMCA loaded = VillagerFactory.newVillager(helper.getLevel()).build();
        try {
            loaded.load(saved);
            helper.assertTrue(InventoryUtils.stream(loaded.getInventory()).filter(stack -> stack.is(Items.IRON_SWORD))
                            .mapToInt(ItemStack::getCount).sum() == 1,
                    "save/reload duplicated or lost the borrowed sword");
            helper.assertTrue(InventoryUtils.stream(loaded.getInventory()).filter(stack -> stack.is(Items.SHIELD))
                            .mapToInt(ItemStack::getCount).sum() == 1,
                    "save/reload duplicated or lost real equipment outside inventory");
            helper.assertTrue(InventoryUtils.stream(loaded.getInventory()).noneMatch(stack -> stack.is(Items.DIAMOND_HELMET)),
                    "save/reload retained a generated prop");
        } finally {
            villager.discard();
            loaded.discard();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void cureKeepsRealEquipmentAndOmitsProps(GameTestHelper helper) {
        ZombieVillagerEntityMCA zombie = Gender.MALE.getZombieType().create(helper.getLevel());
        helper.assertTrue(zombie != null, "could not create zombie fixture");
        zombie.setNoAi(true);
        BlockPos position = helper.absolutePos(new BlockPos(4, 2, 4));
        zombie.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        zombie.setItemSlot(EquipmentSlot.HEAD, InventoryUtils.temporary(new ItemStack(Items.DIAMOND_HELMET)));
        helper.getLevel().addFreshEntity(zombie);
        VillagerEntityMCA cured = (VillagerEntityMCA) zombie.convertTo(EntityType.VILLAGER, true);
        try {
            helper.assertTrue(cured != null, "could not cure zombie fixture");
            helper.assertTrue(cured.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD),
                    "cure lost real equipment");
            helper.assertTrue(!cured.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET)
                            && InventoryUtils.stream(cured.getInventory()).noneMatch(stack -> stack.is(Items.DIAMOND_HELMET)),
                    "cure transferred generated equipment into real villager items");
        } finally {
            zombie.discard();
            if (cured != null) cured.discard();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void legacyMourningHandMigratesIntoInventoryExactlyOnce(GameTestHelper helper) {
        VillagerEntityMCA villager = spawn(helper);
        ItemStack flower = new ItemStack(Items.WHITE_TULIP);
        villager.setItemSlot(EquipmentSlot.MAINHAND, flower);
        villager.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_FLOWER, flower.copy());
        villager.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_PREVIOUS_MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        CompoundTag saved = villager.saveWithoutId(new CompoundTag());
        saved.remove("EquipmentInventorySeparated");
        saved.remove("Inventory");
        VillagerEntityMCA loaded = VillagerFactory.newVillager(helper.getLevel()).build();
        try {
            loaded.load(saved);
            Mourning.finish(loaded);
            helper.assertTrue(loaded.getMainHandItem().is(Items.IRON_SWORD), "legacy mourning lost its displaced weapon");
            helper.assertTrue(InventoryUtils.stream(loaded.getInventory()).filter(stack -> stack.is(Items.IRON_SWORD))
                            .mapToInt(ItemStack::getCount).sum() == 1
                            && InventoryUtils.containsReference(loaded.getInventory(), loaded.getMainHandItem()),
                    "legacy mourning restored a duplicated weapon instead of borrowing its migrated inventory item");
            helper.assertTrue(InventoryUtils.stream(loaded.getInventory()).noneMatch(stack -> stack.is(Items.WHITE_TULIP)),
                    "legacy mourning turned its generated flower into an inventory item");
        } finally {
            villager.discard();
            loaded.discard();
        }
        helper.succeed();
    }

    private static VillagerEntityMCA spawn(GameTestHelper helper) {
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel()).withAge(0)
                .withPosition(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4))))
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        return villager;
    }
}
