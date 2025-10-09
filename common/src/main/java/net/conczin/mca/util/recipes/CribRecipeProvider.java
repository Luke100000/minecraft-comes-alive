package net.conczin.mca.util.recipes;

import net.conczin.mca.entity.CribWoodType;
import net.conczin.mca.registry.ItemsMCA;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;

// TODO Forge, and code duplication
public class CribRecipeProvider {
    public static void generate(RecipeOutput recipeOutput) {
        for (CribWoodType wood : CribWoodType.values()) {
            for (DyeColor color : DyeColor.values()) {
                ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ItemsMCA.CRIBS.stream().filter(c -> {
                            return c.getColor() == color && c.getWood() == wood;
                        }).findFirst().get(), 1)
                        .define('F', fenceFromWoodType(wood))
                        .define('P', plankFromWoodType(wood))
                        .define('C', carpetFromColor(color))
                        .pattern("F F")
                        .pattern("FCF")
                        .pattern("PPP")
                        .save(recipeOutput);
            }
        }
    }

    private static ItemLike plankFromWoodType(CribWoodType woodType) {
        return switch (woodType) {
            case SPRUCE -> Blocks.SPRUCE_PLANKS;
            case ACACIA -> Blocks.ACACIA_PLANKS;
            case BIRCH -> Blocks.BIRCH_PLANKS;
            case CHERRY -> Blocks.CHERRY_PLANKS;
            case CRIMSON -> Blocks.CRIMSON_PLANKS;
            case DARK_OAK -> Blocks.DARK_OAK_PLANKS;
            case JUNGLE -> Blocks.JUNGLE_PLANKS;
            case MANGROVE -> Blocks.MANGROVE_PLANKS;
            case WARPED -> Blocks.WARPED_PLANKS;
            default -> Blocks.OAK_PLANKS;
        };
    }

    private static ItemLike fenceFromWoodType(CribWoodType woodType) {
        return switch (woodType) {
            case SPRUCE -> Blocks.SPRUCE_FENCE;
            case ACACIA -> Blocks.ACACIA_FENCE;
            case BIRCH -> Blocks.BIRCH_FENCE;
            case CHERRY -> Blocks.CHERRY_FENCE;
            case CRIMSON -> Blocks.CRIMSON_FENCE;
            case DARK_OAK -> Blocks.DARK_OAK_FENCE;
            case JUNGLE -> Blocks.JUNGLE_FENCE;
            case MANGROVE -> Blocks.MANGROVE_FENCE;
            case WARPED -> Blocks.WARPED_FENCE;
            default -> Blocks.OAK_FENCE;
        };
    }

    private static ItemLike carpetFromColor(DyeColor color) {
        return switch (color) {
            case WHITE -> Blocks.WHITE_CARPET;
            case ORANGE -> Blocks.ORANGE_CARPET;
            case MAGENTA -> Blocks.MAGENTA_CARPET;
            case LIGHT_BLUE -> Blocks.LIGHT_BLUE_CARPET;
            case YELLOW -> Blocks.YELLOW_CARPET;
            case LIME -> Blocks.LIME_CARPET;
            case PINK -> Blocks.PINK_CARPET;
            case GRAY -> Blocks.GRAY_CARPET;
            case LIGHT_GRAY -> Blocks.LIGHT_GRAY_CARPET;
            case CYAN -> Blocks.CYAN_CARPET;
            case PURPLE -> Blocks.PURPLE_CARPET;
            case BLUE -> Blocks.BLUE_CARPET;
            case BROWN -> Blocks.BROWN_CARPET;
            case GREEN -> Blocks.GREEN_CARPET;
            case BLACK -> Blocks.BLACK_CARPET;
            default -> Blocks.RED_CARPET;
        };
    }
}
