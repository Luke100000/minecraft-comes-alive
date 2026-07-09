package net.mca.resources;

import net.mca.MCA;
import net.mca.entity.ai.relationship.Gender;
import net.mca.resources.data.skin.LayeredHair;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SinglePreparationResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.minecraft.util.profiler.Profiler;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LayeredHairList extends SinglePreparationResourceReloader<Map<Identifier, List<String>>> {
    public static final Identifier ID = MCA.locate("hair_layers");
    private static LayeredHairList INSTANCE;
    public final HashMap<String, LayeredHair> hair = new HashMap<>();

    public LayeredHairList() {
        INSTANCE = this;
    }

    public static LayeredHairList getInstance() {
        return INSTANCE;
    }

    @Override
    protected Map<Identifier, List<String>> prepare(ResourceManager manager, Profiler profiler) {
        return SkinListJson.textureCollections(manager, "hair_layers");
    }

    @Override
    protected void apply(Map<Identifier, List<String>> data, ResourceManager manager, Profiler profiler) {
        hair.clear();
        data.forEach((id, textures) -> SkinCatalogLoader.addLayeredHair(hair, id, textures));
    }

    private void addEntries(Identifier id, List<String> textures) {
        LayeredHair.Category fileCategory = getCategoryFromPath(id);
        textures.forEach(texture -> addEntry(texture, fileCategory));
    }

    private void addEntry(String texture, LayeredHair.Category category) {
        Identifier parsed;
        try {
            parsed = new Identifier(texture);
        } catch (InvalidIdentifierException exception) {
            MCA.LOGGER.warn("Invalid layered hair texture identifier {}", texture, exception);
            return;
        }
        if (!parsed.getPath().startsWith("skins/layered_hair/")) {
            MCA.LOGGER.warn("Invalid layered hair texture path {}", texture);
            return;
        }

        LayeredHair layeredHair = new LayeredHair(texture, Gender.NEUTRAL, category, 1.0F);
        hair.put(layeredHair.getIdentifier() + "|" + layeredHair.getGender().getDataName() + "|" + layeredHair.getCategory().getId(), layeredHair);
    }

    public boolean containsIdentifier(String identifier) {
        return hair.values().stream().anyMatch(entry -> entry.getIdentifier().equals(identifier));
    }

    public LayeredHair get(String identifier, LayeredHair.Category category) {
        return hair.get(key(identifier, Gender.NEUTRAL, category));
    }

    public WeightedPool<String> getPool(LayeredHair.Category category, Gender gender) {
        return SkinSelection.toPool(SkinSelection.layeredHair(hair.values(), category, gender), "");
    }

    public String pick(LayeredHair.Category category, Gender gender) {
        WeightedPool<String> pool = getPool(category, gender);
        if (pool.getEntries().isEmpty()) {
            return "";
        }
        if (!category.isRequired() && API.getRng().nextFloat() > getInclusionChance(category, gender)) {
            return "";
        }
        return pool.pickOne();
    }

    public Map<LayeredHair.Category, String> pickAll(Gender gender) {
        EnumMap<LayeredHair.Category, String> result = new EnumMap<>(LayeredHair.Category.class);
        for (LayeredHair.Category category : LayeredHair.Category.values()) {
            result.put(category, pick(category, gender));
        }
        return result;
    }

    public boolean hasRequiredHair(Gender gender) {
        for (LayeredHair.Category category : LayeredHair.Category.values()) {
            if (category.isRequired() && getPool(category, gender).getEntries().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private float getInclusionChance(LayeredHair.Category category, Gender gender) {
        return SkinSelection.maxChance(SkinSelection.layeredHair(hair.values(), category, gender));
    }

    private static LayeredHair.Category getCategoryFromPath(Identifier id) {
        String[] parts = id.getPath().split("/");
        for (String part : parts) {
            LayeredHair.Category category = LayeredHair.Category.byNameOrNull(part);
            if (category != null) {
                return category;
            }
        }
        return LayeredHair.Category.BASE;
    }

    public static String key(String identifier, Gender gender, LayeredHair.Category category) {
        return identifier + "|" + gender.getDataName() + "|" + category.getId();
    }
}
