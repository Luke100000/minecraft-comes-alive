package net.conczin.mca.fabric.datagen;

import net.conczin.mca.MCA;
import net.conczin.mca.util.recipes.CribRecipeProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;

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
    protected Identifier getRecipeIdentifier(Identifier identifier) {
        return MCA.locate(identifier.getPath());
    }

    @Override
    protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries,
                                                   BootstrapContext<Recipe<?>> recipeOutput,
                                                   BootstrapContext<Advancement> advancementOutput) {
        return new RecipeProvider(recipeOutput, advancementOutput) {
            @Override
            public void buildRecipes() {
                CribRecipeProvider.generate(this.output, registries);
            }
        };
    }
}
