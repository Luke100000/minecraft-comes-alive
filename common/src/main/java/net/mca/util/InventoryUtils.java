package net.mca.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
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
     *
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
                .filter(s -> s.getItem() instanceof ArmorItem)
                .filter(s -> ((ArmorItem)s.getItem()).getEquipmentSlot() == slot)
                .max(Comparator.comparingDouble(s -> ((ArmorItem)s.getItem()).getDefense()));
    }

    static Optional<ItemStack> getBestSword(Container inv) {
        return stream(inv)
                .filter(s -> s.getItem() instanceof SwordItem)
                .max(Comparator.comparingDouble(s -> ((SwordItem)s.getItem()).getDamage()));
    }

    static Optional<ItemStack> getBestRanged(Container inv) {
        return stream(inv)
                .filter(s -> s.getItem() instanceof ProjectileWeaponItem)
                .max(Comparator.comparingDouble(s -> s.getItem().getMaxDamage()));
    }

    static void dropAllItems(Entity entity, Container inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            entity.spawnAtLocation(stack, 1.0F);
        }
        inv.clearContent();
    }

    static void load(Container inv, ListTag tagList) {
        for (int i = 0; i < inv.getContainerSize(); ++i) {
            inv.setItem(i, ItemStack.EMPTY);
        }

        for (int i = 0; i < tagList.size(); ++i) {
            CompoundTag nbt = tagList.getCompound(i);
            int slot = nbt.getByte("Slot") & 255;

            if (slot < inv.getContainerSize()) {
                inv.setItem(slot, ItemStack.of(nbt));
            }
        }
    }

    static ListTag save(Container inv) {
        ListTag tagList = new ListTag();

        for (int i = 0; i < inv.getContainerSize(); ++i) {
            ItemStack itemstack = inv.getItem(i);

            if (itemstack != ItemStack.EMPTY) {
                CompoundTag nbt = new CompoundTag();
                nbt.putByte("Slot", (byte)i);
                itemstack.setTag(nbt);
                tagList.add(nbt);
            }
        }

        return tagList;
    }

    static void saveToNBT(SimpleContainer inv, CompoundTag nbt) {
        nbt.put("Inventory", inv.createTag());
    }

    static void readFromNBT(SimpleContainer inv, CompoundTag nbt) {
        inv.fromTag(nbt.getList("Inventory", 10));
    }
}
