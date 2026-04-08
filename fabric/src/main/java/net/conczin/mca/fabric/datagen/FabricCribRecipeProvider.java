package net.conczin.mca.fabric.datagen;

import net.conczin.mca.util.recipes.CribRecipeProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.RecipeOutput;

import java.util.concurrent.CompletableFuture;

public class FabricCribRecipeProvider extends FabricRecipeProvider {
    public FabricCribRecipeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    public String getName() {
        return "MCA Crib Recipe Provider";
    }

    @Override
    protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput recipeOutput) {
        return new RecipeProvider(registries, recipeOutput) {
            @Override
            public void buildRecipes() {
                CribRecipeProvider.generate(this.output, registries);
            }
        };
    }
}
