package net.mca;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public interface TagsMCA {
    interface Blocks {
        TagKey<Block> TOMBSTONES = register("tombstones");

        static void bootstrap() {
        }

        static TagKey<Block> register(String path) {
            return TagKey.create(Registries.BLOCK, new ResourceLocation(MCA.MOD_ID, path));
        }
    }

    interface Items {
        TagKey<Item> VILLAGER_EGGS = register("villager_eggs");
        TagKey<Item> ZOMBIE_EGGS = register("zombie_eggs");
        TagKey<Item> VILLAGER_PLANTABLE = register("villager_plantable");
        TagKey<Item> BABIES = register("babies");

        static void bootstrap() {
        }

        static TagKey<Item> register(String path) {
            return TagKey.create(Registries.ITEM, new ResourceLocation(MCA.MOD_ID, path));
        }
    }
}
