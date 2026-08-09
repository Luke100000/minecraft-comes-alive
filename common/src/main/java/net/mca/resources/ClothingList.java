package net.mca.resources;

import com.google.gson.JsonElement;
import net.mca.Config;
import net.mca.MCA;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.relationship.Gender;
import net.mca.resources.data.skin.Clothing;
import net.mca.server.world.data.CustomClothingManager;
import net.minecraft.registry.Registries;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.village.VillagerProfession;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ClothingList extends JsonDataLoader {
    public static final Identifier ID = MCA.locate("skins/clothing");
    private static ClothingList INSTANCE;
    public final HashMap<String, Clothing> clothing = new HashMap<>();

    public ClothingList() {
        super(Resources.GSON, ID.getPath());
        INSTANCE = this;
    }

    public static ClothingList getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, Profiler profiler) {
        clothing.clear();

        data.forEach((id, file) -> SkinCatalogLoader.addClothing(clothing, id, file));
    }

    /**
     * Gets a pool of clothing options valid for this entity's gender and profession.
     */
    public WeightedPool<String> getPool(VillagerLike<?> villager) {
        return toPool(getOptions(villager, allClothing()));
    }

    public WeightedPool<String> getPool(Gender gender, @Nullable VillagerProfession profession) {
        return toPool(getOptions(gender, profession, allClothing()));
    }

    private List<Clothing> getOptions(VillagerLike<?> villager, Collection<Clothing> available) {
        return SkinSelection.clothingForVillager(available, villager, Config.getInstance().professionConversionsMap);
    }

    private List<Clothing> getOptions(Gender gender, @Nullable VillagerProfession profession, Collection<Clothing> available) {
        Map<String, String> map = Config.getInstance().professionConversionsMap;
        String currentValue = profession == null ? "minecraft:none" : Registries.VILLAGER_PROFESSION.getId(profession).toString();
        return getOptions(gender, SkinSelection.mapProfession(currentValue, map), available);
    }

    public WeightedPool<String> getPool(Gender gender, @Nullable String profession) {
        return toPool(getOptions(gender, profession, allClothing()));
    }

    public WeightedPool<String> getEditorPool(Gender gender) {
        return toPool(getEditorOptions(gender, allClothing()));
    }

    public List<Clothing> getEditorOptions(Gender gender, Collection<Clothing> available) {
        return SkinSelection.editorClothing(available, gender);
    }

    private List<Clothing> getOptions(Gender gender, @Nullable String profession, Collection<Clothing> available) {
        return SkinSelection.clothingForProfession(available, gender, profession);
    }

    private List<Clothing> allClothing() {
        return Stream.concat(clothing.values().stream(), CustomClothingManager.getClothing().getEntries().values().stream()).toList();
    }

    private WeightedPool<String> toPool(List<Clothing> options) {
        return SkinSelection.toPool(options, "mca:missing");
    }
}
