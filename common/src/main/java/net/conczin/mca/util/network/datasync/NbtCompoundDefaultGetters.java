package net.conczin.mca.util.network.datasync;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public class NbtCompoundDefaultGetters {

    public static int getInt(CompoundTag nbt, String key, int def) {
        return nbt.contains(key) ? nbt.getInt(key) : def;
    }

    public static float getFloat(CompoundTag nbt, String key, float def) {
        return nbt.contains(key) ? nbt.getFloat(key) : def;
    }

    public static String getString(CompoundTag nbt, String key, String def) {
        return nbt.contains(key) ? nbt.getString(key) : def;
    }

    public static CompoundTag getCompound(CompoundTag nbt, String key, CompoundTag def) {
        return nbt.contains(key, 10) ? nbt.getCompound(key) : def.copy();
    }

    public static ItemStack getItemStack(CompoundTag nbt, String key, ItemStack def, HolderLookup.Provider provider) {
        try {
            if (nbt.contains(key, 10)) {
                return ItemStack.parse(provider, nbt.getCompound(key)).orElse(def);
            }
        } catch (ClassCastException ignored) {
        }
        return def;
    }
}
