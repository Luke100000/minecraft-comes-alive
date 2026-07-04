package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.EquipmentSet;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.util.InventoryUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;

import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Predicate;

public class EquipmentTask extends Behavior<VillagerEntityMCA> {
    private static final int COOLDOWN = 100;
    private final Predicate<VillagerEntityMCA> condition;
    private final Function<VillagerEntityMCA, EquipmentSet> equipmentSet;
    private int lastEquipTime;
    private boolean lastArmorWearState;

    public EquipmentTask(Predicate<VillagerEntityMCA> condition, Function<VillagerEntityMCA, EquipmentSet> set) {
        super(ImmutableMap.of(MemoryModuleTypeMCA.WEARS_ARMOR, MemoryStatus.REGISTERED));
        this.condition = condition;
        equipmentSet = set;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA villager) {
        if (villager.isUsingRecoveryFood()) {
            return false;
        }

        EquipmentSet set = orientForDominantHand(villager, equipmentSet.apply(villager));
        if (isNakedCombatSet(set, villager)) {
            return false;
        }

        //armor visibility settings have been changed
        if (lastArmorWearState != villager.getVillagerBrain().getArmorWear()) {
            return true;
        }

        //armor change necessary
        boolean present = villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.WEARS_ARMOR).isPresent();
        if (condition.test(villager)) {
            lastEquipTime = villager.tickCount;
            return !present || isMissingRequestedHandItem(villager, set);
        } else if (villager.tickCount - lastEquipTime > COOLDOWN) {
            return present;
        } else {
            return false;
        }
    }

    private void equipBestArmor(VillagerEntityMCA villager, EquipmentSlot slot, Item fallback) {
        ItemStack stack = InventoryUtils.getBestArmor(villager.getInventory(), slot).orElse(fallback == null ? ItemStack.EMPTY : new ItemStack(fallback));
        villager.setItemSlot(slot, stack);
    }

    private void equipBestWeapon(VillagerEntityMCA villager, Item fallback) {
        ItemStack stack = InventoryUtils.getBestSword(villager.getInventory()).orElse(fallback == null ? ItemStack.EMPTY : new ItemStack(fallback));
        villager.setItemSlot(villager.getDominantSlot(), stack);
    }

    private void equipRequestedRanged(VillagerEntityMCA villager, Item requested) {
        ItemStack stack = InventoryUtils.stream(villager.getInventory())
                .filter(s -> s.is(requested))
                .max(Comparator.comparingDouble(ItemStack::getMaxDamage))
                .orElse(requested == null ? ItemStack.EMPTY : new ItemStack(requested));
        villager.setItemSlot(villager.getDominantSlot(), stack);
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.start(world, villager, time);

        if (villager.isUsingRecoveryFood()) {
            return;
        }

        lastArmorWearState = villager.getVillagerBrain().getArmorWear();
        EquipmentSet set = orientForDominantHand(villager, equipmentSet.apply(villager));
        if (isNakedCombatSet(set, villager)) {
            return;
        }

        boolean wear = condition.test(villager);

        //remember last state
        if (wear) {
            villager.getBrain().setMemory(MemoryModuleTypeMCA.WEARS_ARMOR, true);
        } else {
            villager.getBrain().eraseMemory(MemoryModuleTypeMCA.WEARS_ARMOR);
        }

        //weapon
        if (wear) {
            if (isRequestedItem(set.getMainHand()) && set.getMainHand() instanceof ProjectileWeaponItem) {
                equipRequestedRanged(villager, set.getMainHand());
            } else if (isRequestedItem(set.getMainHand())) {
                equipBestWeapon(villager, set.getMainHand());
            } else {
                villager.setItemSlot(villager.getDominantSlot(), ItemStack.EMPTY);
            }
            villager.setItemSlot(villager.getOpposingSlot(), new ItemStack(set.getGetOffHand()));
        } else {
            villager.setItemInHand(villager.getDominantHand(), ItemStack.EMPTY);
            villager.setItemInHand(villager.getOpposingHand(), ItemStack.EMPTY);
        }

        //armor
        if (wear || villager.getVillagerBrain().getArmorWear()) {
            equipBestArmor(villager, EquipmentSlot.HEAD, set.getHead());
            equipBestArmor(villager, EquipmentSlot.CHEST, set.getChest());
            equipBestArmor(villager, EquipmentSlot.LEGS, set.getLegs());
            equipBestArmor(villager, EquipmentSlot.FEET, set.getFeet());
        } else {
            villager.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            villager.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            villager.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            villager.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        }
    }

    private static boolean isNakedCombatSet(EquipmentSet set, VillagerEntityMCA villager) {
        return set == EquipmentSet.NAKED
                && villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isPresent();
    }

    private static EquipmentSet orientForDominantHand(VillagerEntityMCA villager, EquipmentSet set) {
        if (villager.getDominantHand() != InteractionHand.OFF_HAND) {
            return set;
        }
        if (set == EquipmentSet.GUARD_0) {
            return EquipmentSet.GUARD_0_LEFT;
        }
        if (set == EquipmentSet.ARCHER_0) {
            return EquipmentSet.ARCHER_0_LEFT;
        }
        if (set == EquipmentSet.ARCHER_1) {
            return EquipmentSet.ARCHER_1_LEFT;
        }
        if (set == EquipmentSet.ARCHER_2) {
            return EquipmentSet.ARCHER_2_LEFT;
        }
        return set;
    }

    private static boolean isMissingRequestedHandItem(VillagerEntityMCA villager, EquipmentSet set) {
        return isMissingRequestedItem(villager.getItemBySlot(villager.getDominantSlot()), set.getMainHand())
                || isRequestedItem(set.getGetOffHand()) && villager.getItemBySlot(villager.getOpposingSlot()).isEmpty();
    }

    private static boolean isMissingRequestedItem(ItemStack equipped, Item requested) {
        if (!isRequestedItem(requested)) {
            return false;
        }
        return !equipped.is(requested);
    }

    private static boolean isRequestedItem(Item item) {
        return item != null && item != Items.AIR;
    }
}
