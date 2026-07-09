package net.mca.util.recipes;

import net.mca.entity.CribWoodType;
import net.mca.item.CribItem;
import net.mca.item.ItemsMCA;
import net.minecraft.block.Blocks;
import net.minecraft.data.server.recipe.RecipeJsonProvider;
import net.minecraft.data.server.recipe.ShapedRecipeJsonBuilder;
import net.minecraft.item.ItemConvertible;
import net.minecraft.recipe.book.RecipeCategory;
import net.minecraft.util.DyeColor;

import java.util.function.Consumer;

public class CribRecipeProvider
{
	public static void generate(Consumer<RecipeJsonProvider> consumer)
	{
		for(CribWoodType wood : CribWoodType.values())
		{
			for(DyeColor color : DyeColor.values())
			{
				ShapedRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, ItemsMCA.CRIBS.stream().filter(c ->
				{
					CribItem crib = (CribItem) c.get();
					return crib.getColor() == color && crib.getWood() == wood;
				}).findFirst().get().get(), 1)
				.input(Character.valueOf('F'), fenceFromWoodType(wood))
				.input(Character.valueOf('P'), plankFromWoodType(wood))
				.input(Character.valueOf('C'), carpetFromColor(color))
				.pattern("F F")
				.pattern("FCF")
				.pattern("PPP")
				.offerTo(consumer);
			}
		}
	}

	private static ItemConvertible plankFromWoodType(CribWoodType woodType)
	{
		switch(woodType)
		{
			case SPRUCE:
				return Blocks.SPRUCE_PLANKS;
			case ACACIA:
				return Blocks.ACACIA_PLANKS;
			case BAMBOO:
				return Blocks.BAMBOO_PLANKS;
			case BIRCH:
				return Blocks.BIRCH_PLANKS;
			case CHERRY:
				return Blocks.CHERRY_PLANKS;
			case CRIMSON:
				return Blocks.CRIMSON_PLANKS;
			case DARK_OAK:
				return Blocks.DARK_OAK_PLANKS;
			case JUNGLE:
				return Blocks.JUNGLE_PLANKS;
			case MANGROVE:
				return Blocks.MANGROVE_PLANKS;
			case WARPED:
				return Blocks.WARPED_PLANKS;
			default:
				return Blocks.OAK_PLANKS;
		}
	}

	private static ItemConvertible fenceFromWoodType(CribWoodType woodType)
	{
		switch(woodType)
		{
			case SPRUCE:
				return Blocks.SPRUCE_FENCE;
			case ACACIA:
				return Blocks.ACACIA_FENCE;
			case BAMBOO:
				return Blocks.BAMBOO_FENCE;
			case BIRCH:
				return Blocks.BIRCH_FENCE;
			case CHERRY:
				return Blocks.CHERRY_FENCE;
			case CRIMSON:
				return Blocks.CRIMSON_FENCE;
			case DARK_OAK:
				return Blocks.DARK_OAK_FENCE;
			case JUNGLE:
				return Blocks.JUNGLE_FENCE;
			case MANGROVE:
				return Blocks.MANGROVE_FENCE;
			case WARPED:
				return Blocks.WARPED_FENCE;
			default:
				return Blocks.OAK_FENCE;
		}
	}
	
	private static ItemConvertible carpetFromColor(DyeColor color)
	{
		switch(color)
		{
			case WHITE:
				return Blocks.WHITE_CARPET;
			case ORANGE:
				return Blocks.ORANGE_CARPET;
			case MAGENTA:
				return Blocks.MAGENTA_CARPET;
			case LIGHT_BLUE:
				return Blocks.LIGHT_BLUE_CARPET;
			case YELLOW:
				return Blocks.YELLOW_CARPET;
			case LIME:
				return Blocks.LIME_CARPET;
			case PINK:
				return Blocks.PINK_CARPET;
			case GRAY:
				return Blocks.GRAY_CARPET;
			case LIGHT_GRAY:
				return Blocks.LIGHT_GRAY_CARPET;
			case CYAN:
				return Blocks.CYAN_CARPET;
			case PURPLE:
				return Blocks.PURPLE_CARPET;
			case BLUE:
				return Blocks.BLUE_CARPET;
			case BROWN:
				return Blocks.BROWN_CARPET;
			case GREEN:
				return Blocks.GREEN_CARPET;
			case BLACK:
				return Blocks.BLACK_CARPET;
			default:
				return Blocks.RED_CARPET;
		}
	}
}
