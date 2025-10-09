package net.mca.util.network.datasync;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class NbtCompoundDefaultGetters {

    public static int getInt(CompoundTag nbt, String key, int def) {
        try {
            if (nbt.contains(key, 99)) {
                return nbt.getInt(key);
            }
        } catch (ClassCastException ignored) {
        }
        return def;
    }

    public static float getFloat(CompoundTag nbt, String key, float def) {
        try {
            if (nbt.contains(key, 99)) {
                return nbt.getFloat(key);
            }
        } catch (ClassCastException ignored) {
        }
        return def;
    }

    public static String getString(CompoundTag nbt, String key, String def) {
        try {
            if (nbt.contains(key, 8)) {
                return nbt.getString(key);
            }
        } catch (ClassCastException ignored) {
        }
        return def;
    }

    public static CompoundTag getCompound(CompoundTag nbt, String key, CompoundTag def) {
        try {
            if (nbt.contains(key, 10)) {
                return nbt.getCompound(key);
            }
        } catch (ClassCastException ignored) {
        }
        return def.copy();
    }

    public static ItemStack getItemStack(CompoundTag nbt, String key, ItemStack def) {
        try {
            if (nbt.contains(key, 10)) {
                return ItemStack.of(nbt.getCompound(key));
            }
        } catch (ClassCastException ignored) {
        }
        return def;
    }
}
