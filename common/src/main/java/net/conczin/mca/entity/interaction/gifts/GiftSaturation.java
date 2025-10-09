package net.conczin.mca.entity.interaction.gifts;

import net.conczin.mca.Config;
import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedList;
import java.util.List;

public class GiftSaturation {
    private List<ResourceLocation> values = new LinkedList<>();

    public void add(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        // add to queue
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        values.add(id);

        // clear old values if limit is reached
        while (values.size() > Config.getInstance().giftDesaturationQueueLength) {
            pop();
        }
    }

    public int get(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return (int) values.stream().filter(v -> v.equals(id)).count();
    }

    public void readFromNbt(ListTag nbt) {
        values = NbtHelper.toList(nbt, v -> ResourceLocation.parse(v.getAsString()));
    }

    public ListTag toNbt() {
        return NbtHelper.fromList(values, v -> StringTag.valueOf(v.toString()));
    }

    public void pop() {
        if (!values.isEmpty()) {
            values.remove(0);
        }
    }
}
