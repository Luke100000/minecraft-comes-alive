package mca.crafting.recipe;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import mca.MCA;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.RecipeType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.function.Supplier;

public interface RecipesMCA {

    DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(MCA.MOD_ID, Registry.RECIPE_SERIALIZER_KEY);

   // RecipeType<PressingRecipe> PRESSING = type("pressing");
   // RegistrySupplier<RecipeSerializer<PressingRecipe>> PRESSING_SERIALIZER = serializer("pressing", PressingRecipe.Serializer::new);

    static void bootstrap() {
        RECIPE_SERIALIZERS.register();
    }

    // TODO: Migrate to Deferred when Mojang inevitably freezes this registry
    static <T extends Recipe<?>> RecipeType<T> type(String name) {
        Identifier id = new Identifier(MCA.MOD_ID, name);
        return Registry.register(Registry.RECIPE_TYPE, id, new RecipeType<>() {
            @Override
            public String toString() {
                return name;
            }
        });
    }

    static <T extends RecipeSerializer<?>> RegistrySupplier<T> serializer(String name, Supplier<T> obj) {
        Identifier id = new Identifier(MCA.MOD_ID, name);
        return RECIPE_SERIALIZERS.register(id, obj);
    }
}
