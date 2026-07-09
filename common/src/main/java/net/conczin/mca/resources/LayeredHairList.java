package net.conczin.mca.resources;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LayeredHairList extends SimplePreparableReloadListener<Map<ResourceLocation, List<String>>> {
    public static final ResourceLocation ID = MCA.locate("hair_layers");
    private static LayeredHairList INSTANCE;
    public final HashMap<String, LayeredHair> hair = new HashMap<>();

    public LayeredHairList() {
        INSTANCE = this;
    }

    public static LayeredHairList getInstance() {
        return INSTANCE;
    }

    @Override
    protected Map<ResourceLocation, List<String>> prepare(ResourceManager manager, ProfilerFiller profiler) {
        return SkinListJson.textureCollections(manager, "hair_layers");
    }

    @Override
    protected void apply(Map<ResourceLocation, List<String>> data, ResourceManager manager, ProfilerFiller profiler) {
        hair.clear();
        data.forEach(this::addEntries);
    }

    private void addEntries(ResourceLocation id, List<String> textures) {
        LayeredHair.Category fileCategory = getCategoryFromPath(id);
        textures.forEach(texture -> addEntry(texture, fileCategory));
    }

    private void addEntry(String texture, LayeredHair.Category category) {
        ResourceLocation parsed;
        try {
            parsed = ResourceLocation.parse(texture);
        } catch (ResourceLocationException exception) {
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

    private static LayeredHair.Category getCategoryFromPath(ResourceLocation id) {
        String[] parts = id.getPath().split("/");
        for (String part : parts) {
            LayeredHair.Category category = LayeredHair.Category.byNameOrNull(part);
            if (category != null) {
                return category;
            }
        }
        return LayeredHair.Category.BASE;
    }
}
