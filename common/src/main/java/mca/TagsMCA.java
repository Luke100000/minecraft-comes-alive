package mca;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public interface TagsMCA {
    interface Blocks {
        TagKey<Block> TOMBSTONES = register("tombstones");

        static void bootstrap() {}

        static TagKey<Block> register(String path) {
            return TagKey.of(Registry.BLOCK_KEY, new Identifier(MCA.MOD_ID, path));
        }
    }

    interface Items {
        TagKey<Item> VILLAGER_EGGS = register("villager_eggs");
        TagKey<Item> ZOMBIE_EGGS = register("zombie_eggs");
        TagKey<Item> VILLAGER_PLANTABLE = register("villager_plantable");

        TagKey<Item> BABIES = register("babies");

        static void bootstrap() {}

        static TagKey<Item> register(String path) {
            return TagKey.of(Registry.ITEM_KEY, new Identifier(MCA.MOD_ID, path));
        }
    }
}
