package net.conczin.mca.util.network.datasync;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;

public class NbtCompoundDefaultGetters {

    public static int getInt(CompoundTag nbt, String key, int def) {
        return nbt.getInt(key).orElse(def);
    }

    public static float getFloat(CompoundTag nbt, String key, float def) {
        return nbt.getFloat(key).orElse(def);
    }

    public static String getString(CompoundTag nbt, String key, String def) {
        return nbt.getString(key).orElse(def);
    }

    public static CompoundTag getCompound(CompoundTag nbt, String key, CompoundTag def) {
        return nbt.getCompound(key).map(CompoundTag::copy).orElseGet(def::copy);
    }

    public static ItemStack getItemStack(CompoundTag nbt, String key, ItemStack def, HolderLookup.Provider provider) {
        try {
            if (nbt.contains(key)) {
                return ItemStack.CODEC
                        .parse(provider.createSerializationContext(NbtOps.INSTANCE), nbt.getCompound(key).orElseGet(CompoundTag::new))
                        .result()
                        .orElse(def);
            }
        } catch (ClassCastException ignored) {
        }
        return def;
    }
}
