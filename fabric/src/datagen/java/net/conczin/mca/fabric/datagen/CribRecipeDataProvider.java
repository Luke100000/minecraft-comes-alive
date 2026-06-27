package net.conczin.mca.fabric.datagen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.conczin.mca.MCA;
import net.conczin.mca.util.recipes.CribRecipeProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class CribRecipeDataProvider implements DataProvider {
    private final FabricDataOutput output;
    private final CompletableFuture<HolderLookup.Provider> registriesFuture;

    public CribRecipeDataProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        this.output = output;
        this.registriesFuture = registriesFuture;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        return registriesFuture.thenCompose(registries -> {
            Set<ResourceLocation> generatedRecipes = new HashSet<>();
            List<CompletableFuture<?>> writes = new ArrayList<>();
            RegistryOps<JsonElement> registryOps = registries.createSerializationContext(JsonOps.INSTANCE);
            PackOutput.PathProvider recipePaths = output.createRegistryElementsPathProvider(Registries.RECIPE);
            PackOutput.PathProvider advancementPaths = output.createRegistryElementsPathProvider(Registries.ADVANCEMENT);

            CribRecipeProvider.generate(new CribRecipeOutput(cache, registryOps, recipePaths, advancementPaths, generatedRecipes, writes));

            return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
        });
    }

    @Override
    public String getName() {
        return "Crib Recipes";
    }

    private static boolean isCribPath(ResourceLocation id) {
        return MCA.MOD_ID.equals(id.getNamespace()) && id.getPath().endsWith("_crib");
    }

    private static boolean isCribRecipeAdvancement(ResourceLocation id) {
        return isCribPath(id) && id.getPath().startsWith("recipes/decorations/");
    }

    private static final class CribRecipeOutput implements RecipeOutput {
        private final CachedOutput cache;
        private final RegistryOps<JsonElement> registryOps;
        private final PackOutput.PathProvider recipePaths;
        private final PackOutput.PathProvider advancementPaths;
        private final Set<ResourceLocation> generatedRecipes;
        private final List<CompletableFuture<?>> writes;

        private CribRecipeOutput(
                CachedOutput cache,
                RegistryOps<JsonElement> registryOps,
                PackOutput.PathProvider recipePaths,
                PackOutput.PathProvider advancementPaths,
                Set<ResourceLocation> generatedRecipes,
                List<CompletableFuture<?>> writes
        ) {
            this.cache = cache;
            this.registryOps = registryOps;
            this.recipePaths = recipePaths;
            this.advancementPaths = advancementPaths;
            this.generatedRecipes = generatedRecipes;
            this.writes = writes;
        }

        @Override
        public void accept(ResourceLocation recipeId, Recipe<?> recipe, AdvancementHolder advancement) {
            if (!generatedRecipes.add(recipeId)) {
                throw new IllegalStateException("Duplicate recipe " + recipeId);
            }

            JsonObject recipeJson = Recipe.CODEC.encodeStart(registryOps, recipe).getOrThrow(IllegalStateException::new).getAsJsonObject();
            if (isCribPath(recipeId)) {
                recipeJson.addProperty("show_notification", true);
            }
            writes.add(DataProvider.saveStable(cache, recipeJson, recipePaths.json(recipeId)));

            if (advancement != null) {
                JsonObject advancementJson = Advancement.CODEC.encodeStart(registryOps, advancement.value()).getOrThrow(IllegalStateException::new).getAsJsonObject();
                if (isCribRecipeAdvancement(advancement.id())) {
                    advancementJson.addProperty("sends_telemetry_event", false);
                }
                writes.add(DataProvider.saveStable(cache, advancementJson, advancementPaths.json(advancement.id())));
            }
        }

        @Override
        public Advancement.Builder advancement() {
            return Advancement.Builder.recipeAdvancement().parent(RecipeBuilder.ROOT_RECIPE_ADVANCEMENT);
        }

        @Override
        public ResourceLocation getRecipeIdentifier(ResourceLocation recipeId) {
            return MCA.locate(recipeId.getPath());
        }
    }
}
