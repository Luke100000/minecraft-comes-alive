package net.conczin.mca.util.recipes;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.CribWoodType;
import net.conczin.mca.registry.ItemsMCA;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.triggers.InventoryChangeTrigger;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Optional;

// TODO Forge, and code duplication
public class CribRecipeProvider {
    public static void generate(RecipeOutput recipeOutput, HolderLookup.Provider registries) {
        HolderGetter<Item> items = registries.lookupOrThrow(Registries.ITEM);
        for (CribWoodType wood : CribWoodType.values()) {
            for (DyeColor color : DyeColor.values()) {
                ItemLike planks = plankFromWoodType(wood);
                Item crib = ItemsMCA.CRIBS.stream()
                        .filter(c -> c.getColor() == color && c.getWood() == wood)
                        .findFirst()
                        .get();

                ShapedRecipeBuilder.shaped(items, RecipeCategory.DECORATIONS, crib, 1)
                        .define('F', fenceFromWoodType(wood))
                        .define('P', planks)
                        .define('C', carpetFromColor(color))
                        .pattern("F F")
                        .pattern("FCF")
                        .pattern("PPP")
                        .unlockedBy("has_" + BuiltInRegistries.ITEM.getKey(planks.asItem()).getPath(), hasItem(items, planks))
                        .save(recipeOutput, ResourceKey.create(Registries.RECIPE, MCA.locate(BuiltInRegistries.ITEM.getKey(crib).getPath())));
            }
        }
    }

    private static Criterion<InventoryChangeTrigger.TriggerInstance> hasItem(HolderGetter<Item> items, ItemLike item) {
        return CriteriaTriggers.INVENTORY_CHANGED.createCriterion(
                new InventoryChangeTrigger.TriggerInstance(
                        Optional.empty(),
                        InventoryChangeTrigger.TriggerInstance.Slots.ANY,
                        List.of(ItemPredicate.Builder.item().of(items, item).build())
                )
        );
    }

    private static ItemLike plankFromWoodType(CribWoodType woodType) {
        return switch (woodType) {
            case OAK -> Blocks.OAK_PLANKS;
            case SPRUCE -> Blocks.SPRUCE_PLANKS;
            case ACACIA -> Blocks.ACACIA_PLANKS;
            case BAMBOO -> Blocks.BAMBOO_PLANKS;
            case BIRCH -> Blocks.BIRCH_PLANKS;
            case CHERRY -> Blocks.CHERRY_PLANKS;
            case CRIMSON -> Blocks.CRIMSON_PLANKS;
            case DARK_OAK -> Blocks.DARK_OAK_PLANKS;
            case JUNGLE -> Blocks.JUNGLE_PLANKS;
            case MANGROVE -> Blocks.MANGROVE_PLANKS;
            case WARPED -> Blocks.WARPED_PLANKS;
        };
    }

    private static ItemLike fenceFromWoodType(CribWoodType woodType) {
        return switch (woodType) {
            case OAK -> Blocks.OAK_FENCE;
            case SPRUCE -> Blocks.SPRUCE_FENCE;
            case ACACIA -> Blocks.ACACIA_FENCE;
            case BAMBOO -> Blocks.BAMBOO_FENCE;
            case BIRCH -> Blocks.BIRCH_FENCE;
            case CHERRY -> Blocks.CHERRY_FENCE;
            case CRIMSON -> Blocks.CRIMSON_FENCE;
            case DARK_OAK -> Blocks.DARK_OAK_FENCE;
            case JUNGLE -> Blocks.JUNGLE_FENCE;
            case MANGROVE -> Blocks.MANGROVE_FENCE;
            case WARPED -> Blocks.WARPED_FENCE;
        };
    }

    private static ItemLike carpetFromColor(DyeColor color) {
        return Blocks.CARPET.pick(color);
    }
}
