package net.conczin.mca.fabric.datagen;

import net.conczin.mca.util.recipes.CribRecipeProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.CompletableFuture;

public class FabricCribRecipeProvider extends FabricRecipeProvider {
    public FabricCribRecipeProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput recipeOutput) {
        return new RecipeProvider(registries, recipeOutput) {
            @Override
            public void buildRecipes() {
                CribRecipeProvider.generate(recipeOutput, registries);
            }
        };
    }

    @Override
    public String getName() {
        return "MCA Crib Recipes";
    }

    @Override
    protected ResourceLocation getRecipeIdentifier(ResourceLocation identifier) {
        return identifier;
    }
}
