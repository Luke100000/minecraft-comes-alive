package net.conczin.mca.entity;

import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.Mourning;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.conczin.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Set;
import java.util.UUID;

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
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, villager.registryAccess());
        villager.saveWithoutId(output);
        CompoundTag saved = output.buildResult();
        VillagerEntityMCA loaded = VillagerFactory.newVillager(helper.getLevel()).build();
        try {
            loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
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
        ZombieVillagerEntityMCA zombie = Gender.MALE.getZombieType().create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
        helper.assertTrue(zombie != null, "could not create zombie fixture");
        zombie.setNoAi(true);
        zombie.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4))));
        zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        zombie.setItemSlot(EquipmentSlot.HEAD, InventoryUtils.temporary(new ItemStack(Items.DIAMOND_HELMET)));
        helper.getLevel().addFreshEntity(zombie);
        VillagerEntityMCA cured = (VillagerEntityMCA) zombie.convertTo(EntityTypes.VILLAGER,
                ConversionParams.single(zombie, true, false), mob -> {});
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
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, villager.registryAccess());
        villager.saveWithoutId(output);
        CompoundTag saved = output.buildResult();
        CompoundTag legacyData = saved.getCompound(VillagerEntityMCA.MCA_DATA_KEY).orElseThrow();
        legacyData.remove("EquipmentInventorySeparated");
        legacyData.remove("Inventory");
        VillagerEntityMCA loaded = VillagerFactory.newVillager(helper.getLevel()).build();
        try {
            loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
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

    @GameTest(batch = "mca_equipment_zombification", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void villagerToZombieConversionAccountsForOwnedEquipment(GameTestHelper helper) {
        VillagerEntityMCA source = spawn(helper);
        var bounds = source.getBoundingBox().inflate(2);
        var existingDrops = Set.copyOf(helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds));
        Mob converted = null;
        try {
            source.getInventory().clearContent();
            ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
            source.getInventory().setItem(0, sword);
            source.setItemSlot(EquipmentSlot.MAINHAND, sword);
            source.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            source.setItemSlot(EquipmentSlot.HEAD, InventoryUtils.temporary(new ItemStack(Items.NETHERITE_HELMET)));
            helper.assertTrue(source.getMainHandItem() == sword && source.getInventory().getItem(0) == sword,
                    "fixture must equip the actual inventory stack");
            UUID identity = source.getUUID();

            converted = source.convertTo(EntityTypes.ZOMBIE_VILLAGER,
                    ConversionParams.single(source, false, false), zombie -> {});
            helper.assertTrue(converted instanceof ZombieVillagerEntityMCA && source.isRemoved()
                            && identity.equals(converted.getUUID()) && helper.getLevel().getEntity(identity) == converted,
                    "infection conversion did not replace and admit the source under the same UUID");
            var drops = newDrops(helper, bounds, existingDrops);
            assertConvertedItemCounts(helper, converted, drops, Items.NETHERITE_SWORD, 1);
            assertConvertedItemCounts(helper, converted, drops, Items.SHIELD, 1);
            assertConvertedItemCounts(helper, converted, drops, Items.NETHERITE_HELMET, 0);
            helper.succeed();
        } finally {
            newDrops(helper, bounds, existingDrops).forEach(ItemEntity::discard);
            if (converted != null) converted.discard();
            source.discard();
        }
    }

    @GameTest(batch = "mca_equipment_zombification_transfer", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void transferredBorrowedStackDoesNotReappearOnConversion(GameTestHelper helper) {
        VillagerEntityMCA source = spawn(helper);
        var bounds = source.getBoundingBox().inflate(2);
        var existingDrops = Set.copyOf(helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds));
        Mob converted = null;
        try {
            source.getInventory().clearContent();
            ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
            source.getInventory().setItem(0, sword);
            source.setItemSlot(EquipmentSlot.MAINHAND, sword);
            helper.assertTrue(source.getMainHandItem() == sword, "fixture must borrow the inventory stack");
            SimpleContainer recipient = new SimpleContainer(1);
            recipient.setItem(0, source.getInventory().removeItemNoUpdate(0));
            source.getInventory().setChanged();
            helper.assertTrue(recipient.getItem(0) == sword && sword.getCount() == 1
                            && source.getMainHandItem().isEmpty(),
                    "inventory transfer failed to retain the original item and retract its borrowed hand");
            UUID identity = source.getUUID();

            converted = source.convertTo(EntityTypes.ZOMBIE_VILLAGER,
                    ConversionParams.single(source, false, false), zombie -> {});
            helper.assertTrue(converted instanceof ZombieVillagerEntityMCA && source.isRemoved()
                            && identity.equals(converted.getUUID()) && helper.getLevel().getEntity(identity) == converted,
                    "infection conversion did not replace and admit the source under the same UUID");
            helper.assertTrue(recipient.getItem(0) == sword && sword.getCount() == 1,
                    "conversion changed the recipient's transferred item");
            assertConvertedItemCounts(helper, converted, newDrops(helper, bounds, existingDrops),
                    Items.NETHERITE_SWORD, 0);
            helper.succeed();
        } finally {
            newDrops(helper, bounds, existingDrops).forEach(ItemEntity::discard);
            if (converted != null) converted.discard();
            source.discard();
        }
    }

    private static List<ItemEntity> newDrops(GameTestHelper helper, AABB bounds, Set<ItemEntity> existingDrops) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds,
                drop -> !existingDrops.contains(drop));
    }

    private static void assertConvertedItemCounts(GameTestHelper helper, Mob converted, List<ItemEntity> drops,
                                                  Item item, int expectedInventoryCount) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        converted.saveWithoutId(output);
        CompoundTag mcaData = output.buildResult().getCompound(VillagerEntityMCA.MCA_DATA_KEY).orElseThrow();
        var entries = TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(),
                mcaData).listOrEmpty("Inventory", ItemStack.CODEC);
        int inventoryCount = 0;
        for (ItemStack stack : entries) {
            if (stack.is(item)) inventoryCount += stack.getCount();
        }
        helper.assertTrue(inventoryCount == expectedInventoryCount,
                "converted inventory count for " + item + ": " + inventoryCount + " expected " + expectedInventoryCount);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            helper.assertTrue(!converted.getItemBySlot(slot).is(item),
                    "conversion additionally equipped " + item + " in " + slot);
        }
        helper.assertTrue(drops.stream().noneMatch(drop -> drop.getItem().is(item)),
                "conversion additionally dropped " + item);
    }

    @GameTest(batch = "mca_equipment_full_overflow", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void replacingRealEquipmentWithFullInventoryDropsOnlyOverflow(GameTestHelper helper) {
        assertEquipmentOverflow(helper, 0, 0, 4);
    }

    @GameTest(batch = "mca_equipment_partial_overflow", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void replacingRealEquipmentWithPartialCapacityDropsOnlyRemainder(GameTestHelper helper) {
        assertEquipmentOverflow(helper, 62, 64, 2);
    }

    private static void assertEquipmentOverflow(GameTestHelper helper, int initialEmeralds,
                                                int expectedStored, int expectedDropped) {
        VillagerEntityMCA villager = spawn(helper);
        var bounds = villager.getBoundingBox().inflate(2);
        var existingDrops = Set.copyOf(helper.getLevel().getEntitiesOfClass(ItemEntity.class, bounds));
        try {
            villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            SimpleContainer inventory = villager.getInventory();
            inventory.clearContent();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            if (initialEmeralds > 0) {
                inventory.setItem(0, new ItemStack(Items.EMERALD, initialEmeralds));
            }
            ItemStack displaced = new ItemStack(Items.EMERALD, 4);
            villager.setItemSlot(EquipmentSlot.MAINHAND, displaced);
            helper.assertTrue(villager.getMainHandItem() == displaced
                            && !InventoryUtils.containsReference(inventory, displaced),
                    "fixture must independently own the four equipped emeralds");
            helper.assertTrue(InventoryUtils.stream(inventory).noneMatch(ItemStack::isEmpty)
                            && inventory.canAddItem(displaced) == (initialEmeralds > 0)
                            && InventoryUtils.stream(inventory).filter(stack -> stack.is(Items.EMERALD))
                            .mapToInt(ItemStack::getCount).sum() == initialEmeralds,
                    "fixture must have the intended full or partially compatible inventory");

            ItemStack prop = InventoryUtils.temporary(new ItemStack(Items.WHITE_TULIP));
            villager.setItemSlot(EquipmentSlot.MAINHAND, prop);
            helper.assertTrue(villager.getMainHandItem() == prop,
                    "equipment replacement did not equip the requested temporary flower");
            int stored = InventoryUtils.stream(inventory).filter(stack -> stack.is(Items.EMERALD))
                    .mapToInt(ItemStack::getCount).sum();
            int dropped = newDrops(helper, bounds, existingDrops).stream()
                    .filter(drop -> drop.blockPosition().equals(villager.blockPosition()))
                    .map(ItemEntity::getItem).filter(stack -> stack.is(Items.EMERALD))
                    .mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(stored == expectedStored && dropped == expectedDropped,
                    "equipment replacement stored " + stored + " emeralds and dropped " + dropped
                            + "; expected " + expectedStored + " stored and " + expectedDropped + " dropped");

            villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            helper.assertTrue(villager.getMainHandItem().isEmpty()
                            && InventoryUtils.stream(inventory).noneMatch(stack -> stack.is(Items.WHITE_TULIP))
                            && newDrops(helper, bounds, existingDrops).stream()
                            .noneMatch(drop -> drop.getItem().is(Items.WHITE_TULIP)),
                    "clearing temporary equipment materialized a real flower");
            helper.succeed();
        } finally {
            newDrops(helper, bounds, existingDrops).forEach(ItemEntity::discard);
            villager.discard();
        }
    }

    private static VillagerEntityMCA spawn(GameTestHelper helper) {
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel()).withAge(0)
                .withPosition(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4))))
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.setNoAi(true);
        return villager;
    }
}
