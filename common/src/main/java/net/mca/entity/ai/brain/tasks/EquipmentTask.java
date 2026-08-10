package net.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.mca.entity.EquipmentSet;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.ActivityMCA;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.mca.util.InventoryUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import java.util.function.Function;
import java.util.function.Predicate;

public class EquipmentTask extends Behavior<VillagerEntityMCA> {
    private static final int COOLDOWN = 100;
    private static final int CHECK_INTERVAL = 20;
    private int lastEquipTime;
    private final Predicate<VillagerEntityMCA> condition;
    private final Function<VillagerEntityMCA, EquipmentSet> equipmentSet;
    private boolean lastArmorWearState;
    private int lastCheckTick = -CHECK_INTERVAL;
    private boolean cachedConditionResult;
    private EquipmentSet cachedEquipmentSet;

    public EquipmentTask(Predicate<VillagerEntityMCA> condition, Function<VillagerEntityMCA, EquipmentSet> set) {
        super(ImmutableMap.of(MemoryModuleTypeMCA.WEARS_ARMOR.get(), MemoryStatus.REGISTERED));
        this.condition = condition;
        equipmentSet = set;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA villager) {
        if (villager.isUsingRecoveryFood()) {
            return false;
        }

        // Armor visibility settings have been changed.
        if (lastArmorWearState != villager.getVillagerBrain().getArmorWear()) {
            return true;
        }

        // Cache potentially expensive equipment predicates.
        if (villager.tickCount - lastCheckTick >= CHECK_INTERVAL) {
            lastCheckTick = villager.tickCount;
            cachedConditionResult = condition.test(villager);
            cachedEquipmentSet = cachedConditionResult ? equipmentSet.apply(villager) : null;
        }

        EquipmentSet set = cachedEquipmentSet;
        if (set != null && isNakedCombatSet(set, villager)) {
            return false;
        }

        boolean preserveMourningHands = isPeacefullyGrieving(villager);
        boolean present = villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.WEARS_ARMOR.get()).isPresent();
        if (cachedConditionResult) {
            lastEquipTime = villager.tickCount;
            return !present || set != null && !preserveMourningHands && isMissingRequestedHandItem(villager, set);
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

    private void equipBestRanged(VillagerEntityMCA villager, Item fallback) {
        ItemStack stack = InventoryUtils.getBestRanged(villager.getInventory()).orElse(fallback == null ? ItemStack.EMPTY : new ItemStack(fallback));
        villager.setItemSlot(villager.getDominantSlot(), stack);
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.start(world, villager, time);

        if (villager.isUsingRecoveryFood()) {
            return;
        }

        lastArmorWearState = villager.getVillagerBrain().getArmorWear();
        boolean wear = cachedConditionResult;
        EquipmentSet set = cachedEquipmentSet;

        if ((wear || villager.getVillagerBrain().getArmorWear()) && set == null) {
            set = equipmentSet.apply(villager);
            cachedEquipmentSet = set;
        }

        if (set != null && isNakedCombatSet(set, villager)) {
            return;
        }

        // Remember the last equipment state.
        if (wear) {
            villager.getBrain().setMemory(MemoryModuleTypeMCA.WEARS_ARMOR.get(), true);
        } else {
            villager.getBrain().eraseMemory(MemoryModuleTypeMCA.WEARS_ARMOR.get());
        }

        // Weapon. Peaceful grieving owns the hand slots so the flower is not
        // cleared or replaced by routine equipment refreshes. Combat still wins.
        boolean preserveMourningHands = isPeacefullyGrieving(villager);
        if (!preserveMourningHands) {
            if (wear && set != null) {
                if (isRequestedItem(set.getMainHand()) && set.getMainHand() instanceof ProjectileWeaponItem) {
                    equipBestRanged(villager, set.getMainHand());
                } else if (isRequestedItem(set.getMainHand())) {
                    equipBestWeapon(villager, set.getMainHand());
                } else {
                    villager.setItemSlot(villager.getDominantSlot(), ItemStack.EMPTY);
                }
                villager.setItemSlot(villager.getOpposingSlot(), isRequestedItem(set.getGetOffHand()) ? new ItemStack(set.getGetOffHand()) : ItemStack.EMPTY);
            } else if (!wear) {
                villager.setItemInHand(villager.getDominantHand(), ItemStack.EMPTY);
                villager.setItemInHand(villager.getOpposingHand(), ItemStack.EMPTY);
            }
        }

        // Armor.
        if ((wear || villager.getVillagerBrain().getArmorWear()) && set != null) {
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

    private static boolean isPeacefullyGrieving(VillagerEntityMCA villager) {
        return villager.getBrain().isActive(ActivityMCA.GRIEVE.get())
                && villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isEmpty()
                && !villager.getVillagerBrain().isPanicking();
    }

    private static boolean isNakedCombatSet(EquipmentSet set, VillagerEntityMCA villager) {
        return EquipmentSet.NAKED.equals(set)
                && villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isPresent();
    }

    private static boolean isMissingRequestedHandItem(VillagerEntityMCA villager, EquipmentSet set) {
        return isMissingRequestedItem(villager.getItemBySlot(villager.getDominantSlot()), set.getMainHand())
                || isRequestedItem(set.getGetOffHand()) && villager.getItemBySlot(villager.getOpposingSlot()).isEmpty();
    }

    private static boolean isMissingRequestedItem(ItemStack equipped, Item requested) {
        if (!isRequestedItem(requested)) {
            return false;
        }
        if (requested instanceof ProjectileWeaponItem) {
            return !(equipped.getItem() instanceof ProjectileWeaponItem);
        }
        return equipped.isEmpty();
    }

    private static boolean isRequestedItem(Item item) {
        return item != null && item != Items.AIR;
    }

}
