package net.conczin.mca.util;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public interface InventoryUtils {
    static Stream<ItemStack> stream(Container inventory) {
        return IntStream.range(0, inventory.getContainerSize()).mapToObj(inventory::getItem);
    }

    static int getFirstSlotContainingItem(Container inv, Predicate<ItemStack> predicate) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!predicate.test(stack)) continue;
            return i;
        }
        return -1;
    }

    static boolean contains(Container inv, Class<?> clazz) {
        for (int i = 0; i < inv.getContainerSize(); ++i) {
            final ItemStack stack = inv.getItem(i);
            final Item item = stack.getItem();

            if (item.getClass() == clazz) return true;
        }
        return false;
    }

    /**
     * Gets the best quality (max damage) item of the specified type that is in the inventory.
     *
     * @param type The class of item that will be returned.
     * @return The item stack containing the item of the specified type with the highest max damage.
     */
    static ItemStack getBestItemOfType(Container inv, @Nullable Class<?> type) {
        return type == null ? ItemStack.EMPTY : inv.getItem(getBestItemOfTypeSlot(inv, type));
    }

    static int getBestItemOfTypeSlot(Container inv, Class<?> type) {
        int highestMaxDamage = 0;
        int best = -1;

        for (int i = 0; i < inv.getContainerSize(); ++i) {
            ItemStack stackInInventory = inv.getItem(i);

            final String itemClassName = stackInInventory.getItem().getClass().getName();

            if (itemClassName.equals(type.getName()) && highestMaxDamage < stackInInventory.getMaxDamage()) {
                highestMaxDamage = stackInInventory.getMaxDamage();
                best = i;
            }
        }

        return best;
    }

    static Optional<ItemStack> getBestArmor(Container inv, EquipmentSlot slot) {
        return stream(inv)
                .filter(s -> {
                    Equippable equippable = s.get(DataComponents.EQUIPPABLE);
                    return equippable != null && equippable.slot() == slot;
                })
                .max(Comparator.comparingDouble(s -> {
                    ItemAttributeModifiers modifiers = s.get(DataComponents.ATTRIBUTE_MODIFIERS);
                    return modifiers == null ? 0.0D : modifiers.compute(Attributes.ARMOR, 0.0D, slot);
                }));
    }

    static Optional<ItemStack> getBestSword(Container inv) {
        return stream(inv)
                .filter(InventoryUtils::isWeapon)
                .max(Comparator.comparingDouble(ItemStack::getMaxDamage));
    }

    static boolean isWeapon(ItemStack stack) {
        return stack.has(DataComponents.WEAPON);
    }

    static void dropAllItems(Entity entity, Container inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (entity.level() instanceof ServerLevel serverLevel) {
                entity.spawnAtLocation(serverLevel, stack, 1.0F);
            }
        }
        inv.clearContent();
    }

    static void saveToNBT(RegistryAccess registryAccess, SimpleContainer inv, CompoundTag nbt) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registryAccess);
        inv.storeAsItemList(output.list("Inventory", ItemStack.CODEC));
        nbt.put("Inventory", output.buildResult().get("Inventory"));
    }

    static void readFromNBT(RegistryAccess registryAccess, SimpleContainer inv, CompoundTag nbt) {
        inv.fromItemList(TagValueInput.create(ProblemReporter.DISCARDING, registryAccess, nbt).listOrEmpty("Inventory", ItemStack.CODEC));
    }

    static double approximateDamage(ItemStack stack, LivingEntity entity) {
        double base = entity.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        ItemAttributeModifiers comp = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        return comp == null ? base : comp.compute(Attributes.ATTACK_DAMAGE, base, EquipmentSlot.MAINHAND);
    }
}
