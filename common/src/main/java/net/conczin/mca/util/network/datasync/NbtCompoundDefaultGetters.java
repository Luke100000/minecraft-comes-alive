package net.conczin.mca.util.network.datasync;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;

public class NbtCompoundDefaultGetters {

    public static int getInt(CompoundTag nbt, String key, int def) {
        return nbt.contains(key) ? nbt.getInt(key).orElse(def) : def;
    }

    public static float getFloat(CompoundTag nbt, String key, float def) {
        return nbt.contains(key) ? nbt.getFloat(key).orElse(def) : def;
    }

    public static String getString(CompoundTag nbt, String key, String def) {
        return nbt.contains(key) ? nbt.getString(key).orElse(def) : def;
    }

    public static CompoundTag getCompound(CompoundTag nbt, String key, CompoundTag def) {
        return nbt.getCompound(key).orElseGet(def::copy);
    }

    public static ItemStack getItemStack(CompoundTag nbt, String key, ItemStack def, HolderLookup.Provider provider) {
        try {
            return nbt.getCompound(key)
                    .flatMap(tag -> ItemStack.CODEC.parse(provider.createSerializationContext(NbtOps.INSTANCE), tag).result())
                    .orElse(def);
        } catch (ClassCastException ignored) {
        }
        return def;
    }
}
