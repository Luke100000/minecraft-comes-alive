package net.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.mca.entity.EquipmentSet;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.ActivityMCA;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.mca.util.InventoryUtils;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.server.world.ServerWorld;

import java.util.function.Function;
import java.util.function.Predicate;

public class EquipmentTask extends MultiTickTask<VillagerEntityMCA> {
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
        super(ImmutableMap.of(MemoryModuleTypeMCA.WEARS_ARMOR.get(), MemoryModuleState.REGISTERED));
        this.condition = condition;
        equipmentSet = set;
    }

    @Override
    protected boolean shouldRun(ServerWorld world, VillagerEntityMCA villager) {
        if (villager.isUsingRecoveryFood()) {
            return false;
        }

        // Armor visibility settings have been changed.
        if (lastArmorWearState != villager.getVillagerBrain().getArmorWear()) {
            return true;
        }

        // Cache potentially expensive equipment predicates.
        if (villager.age - lastCheckTick >= CHECK_INTERVAL) {
            lastCheckTick = villager.age;
            cachedConditionResult = condition.test(villager);
            cachedEquipmentSet = cachedConditionResult ? equipmentSet.apply(villager) : null;
        }

        EquipmentSet set = cachedEquipmentSet;
        if (set != null && isNakedCombatSet(set, villager)) {
            return false;
        }

        boolean preserveMourningHands = isPeacefullyGrieving(villager);
        boolean present = villager.getBrain().getOptionalMemory(MemoryModuleTypeMCA.WEARS_ARMOR.get()).isPresent();
        if (cachedConditionResult) {
            lastEquipTime = villager.age;
            return !present || set != null && !preserveMourningHands && isMissingRequestedHandItem(villager, set);
        } else if (villager.age - lastEquipTime > COOLDOWN) {
            return present;
        } else {
            return false;
        }
    }

    private void equipBestArmor(VillagerEntityMCA villager, EquipmentSlot slot, Item fallback) {
        ItemStack stack = InventoryUtils.getBestArmor(villager.getInventory(), slot).orElse(fallback == null ? ItemStack.EMPTY : new ItemStack(fallback));
        villager.equipStack(slot, stack);
    }

    private void equipBestWeapon(VillagerEntityMCA villager, Item fallback) {
        ItemStack stack = InventoryUtils.getBestSword(villager.getInventory()).orElse(fallback == null ? ItemStack.EMPTY : new ItemStack(fallback));
        villager.equipStack(villager.getDominantSlot(), stack);
    }

    private void equipBestRanged(VillagerEntityMCA villager, Item fallback) {
        ItemStack stack = InventoryUtils.getBestRanged(villager.getInventory()).orElse(fallback == null ? ItemStack.EMPTY : new ItemStack(fallback));
        villager.equipStack(villager.getDominantSlot(), stack);
    }

    @Override
    protected void run(ServerWorld world, VillagerEntityMCA villager, long time) {
        super.run(world, villager, time);

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
            villager.getBrain().remember(MemoryModuleTypeMCA.WEARS_ARMOR.get(), true);
        } else {
            villager.getBrain().forget(MemoryModuleTypeMCA.WEARS_ARMOR.get());
        }

        // Weapon. Peaceful grieving owns the hand slots so the flower is not
        // cleared or replaced by routine equipment refreshes. Combat still wins.
        boolean preserveMourningHands = isPeacefullyGrieving(villager);
        if (!preserveMourningHands) {
            if (wear && set != null) {
                if (isRequestedItem(set.getMainHand()) && set.getMainHand() instanceof RangedWeaponItem) {
                    equipBestRanged(villager, set.getMainHand());
                } else if (isRequestedItem(set.getMainHand())) {
                    equipBestWeapon(villager, set.getMainHand());
                } else {
                    villager.equipStack(villager.getDominantSlot(), ItemStack.EMPTY);
                }
                villager.equipStack(villager.getOpposingSlot(), isRequestedItem(set.getGetOffHand()) ? new ItemStack(set.getGetOffHand()) : ItemStack.EMPTY);
            } else if (!wear) {
                villager.setStackInHand(villager.getDominantHand(), ItemStack.EMPTY);
                villager.setStackInHand(villager.getOpposingHand(), ItemStack.EMPTY);
            }
        }

        // Armor.
        if ((wear || villager.getVillagerBrain().getArmorWear()) && set != null) {
            equipBestArmor(villager, EquipmentSlot.HEAD, set.getHead());
            equipBestArmor(villager, EquipmentSlot.CHEST, set.getChest());
            equipBestArmor(villager, EquipmentSlot.LEGS, set.getLegs());
            equipBestArmor(villager, EquipmentSlot.FEET, set.getFeet());
        } else {
            villager.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
            villager.equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY);
            villager.equipStack(EquipmentSlot.LEGS, ItemStack.EMPTY);
            villager.equipStack(EquipmentSlot.FEET, ItemStack.EMPTY);
        }
    }

    private static boolean isPeacefullyGrieving(VillagerEntityMCA villager) {
        return villager.getBrain().hasActivity(ActivityMCA.GRIEVE.get())
                && villager.getBrain().getOptionalMemory(MemoryModuleType.ATTACK_TARGET).isEmpty()
                && !villager.getVillagerBrain().isPanicking();
    }

    private static boolean isNakedCombatSet(EquipmentSet set, VillagerEntityMCA villager) {
        return EquipmentSet.NAKED.equals(set)
                && villager.getBrain().getOptionalMemory(MemoryModuleType.ATTACK_TARGET).isPresent();
    }

    private static boolean isMissingRequestedHandItem(VillagerEntityMCA villager, EquipmentSet set) {
        return isMissingRequestedItem(villager.getEquippedStack(villager.getDominantSlot()), set.getMainHand())
                || isRequestedItem(set.getGetOffHand()) && villager.getEquippedStack(villager.getOpposingSlot()).isEmpty();
    }

    private static boolean isMissingRequestedItem(ItemStack equipped, Item requested) {
        if (!isRequestedItem(requested)) {
            return false;
        }
        if (requested instanceof RangedWeaponItem) {
            return !(equipped.getItem() instanceof RangedWeaponItem);
        }
        return equipped.isEmpty();
    }

    private static boolean isRequestedItem(Item item) {
        return item != null && item != Items.AIR;
    }

}
