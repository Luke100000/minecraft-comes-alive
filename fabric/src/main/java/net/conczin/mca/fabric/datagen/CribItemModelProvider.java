package net.conczin.mca.fabric.datagen;

import com.google.gson.JsonObject;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.CribWoodType;
import net.conczin.mca.registry.ItemsMCA;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class CribItemModelProvider implements DataProvider {
    private final PackOutput.PathProvider modelPathProvider;
    private final PackOutput.PathProvider itemDefinitionPathProvider;

    public CribItemModelProvider(FabricDataOutput output) {
        this.modelPathProvider = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models/item");
        this.itemDefinitionPathProvider = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "items");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (CribWoodType wood : CribWoodType.values()) {
            for (DyeColor color : DyeColor.values()) {
                Item item = ItemsMCA.CRIBS.stream()
                        .filter(crib -> crib.getColor() == color && crib.getWood() == wood)
                        .findFirst()
                        .orElse(ItemsMCA.CRIBS.getFirst());

                JsonObject textures = new JsonObject();
                textures.addProperty("layer0", MCA.locate("item/crib/beds/" + color.getName()).toString());
                textures.addProperty("layer1", MCA.locate("item/crib/frames/" + wood.toString().toLowerCase(Locale.ROOT)).toString());

                JsonObject model = new JsonObject();
                model.addProperty("parent", ResourceLocation.withDefaultNamespace("item/generated").toString());
                model.add("textures", textures);

                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
                futures.add(DataProvider.saveStable(output, model, modelPathProvider.json(itemId)));
                futures.add(DataProvider.saveStable(output, itemDefinition(itemId), itemDefinitionPathProvider.json(itemId)));
            }
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private static JsonObject itemDefinition(ResourceLocation itemId) {
        JsonObject model = new JsonObject();
        model.addProperty("type", ResourceLocation.withDefaultNamespace("model").toString());
        model.addProperty("model", ResourceLocation.fromNamespaceAndPath(itemId.getNamespace(), "item/" + itemId.getPath()).toString());

        JsonObject root = new JsonObject();
        root.add("model", model);
        root.add("properties", new JsonObject());
        return root;
    }

    @Override
    public String getName() {
        return "MCA Crib Item Models";
    }
}
