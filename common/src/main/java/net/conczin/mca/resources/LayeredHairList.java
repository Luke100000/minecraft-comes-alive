package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class LayeredHairList extends SimpleJsonResourceReloadListener<JsonElement> {
    public static final Identifier ID = MCA.locate("skins/layered_hair");
    private static LayeredHairList INSTANCE;
    public final HashMap<String, LayeredHair> hair = new HashMap<>();

    public LayeredHairList() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("skins/layered_hair"));
        INSTANCE = this;
    }

    public static LayeredHairList getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        hair.clear();

        data.forEach(this::addEntries);
    }

    private void addEntries(Identifier id, JsonElement file) {
        Gender fileGender = BodySkinList.getGenderFromPath(id);
        LayeredHair.Category fileCategory = getCategoryFromPath(id);
        SkinListJson.entries(id, file).forEach(entry -> {
            LayeredHair.DEFINITION_CODEC.parse(JsonOps.INSTANCE, entry.metadata())
                    .resultOrPartial(error -> MCA.LOGGER.warn("Invalid layered hair list entry {} in {}: {}", entry.identifier(), id, error))
                    .ifPresent(definition -> {
                        LayeredHair layeredHair = definition.create(entry.identifier(), fileGender, fileCategory);
                        hair.put(layeredHair.getIdentifier() + "|" + layeredHair.getGender().getDataName() + "|" + layeredHair.getCategory().getId(), layeredHair);
                    });
        });
    }

    public boolean containsIdentifier(String identifier) {
        return hair.values().stream().anyMatch(entry -> entry.getIdentifier().equals(identifier));
    }

    public WeightedPool<String> getPool(LayeredHair.Category category, Gender gender) {
        return hair.values().stream()
                .filter(h -> h.getCategory() == category)
                .filter(h -> h.getGender() == Gender.NEUTRAL || gender == Gender.NEUTRAL || h.getGender() == gender)
                .sorted((a, b) -> SkinListEntry.compareIdentifiers(a.getIdentifier(), b.getIdentifier()))
                .collect(() -> new WeightedPool.Mutable<>(""),
                        (list, entry) -> list.add(entry.getIdentifier(), entry.getChance()),
                        (a, b) -> a.entries.addAll(b.entries));
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
        return !getPool(LayeredHair.Category.BASE, gender).getEntries().isEmpty()
                && !getPool(LayeredHair.Category.BANGS, gender).getEntries().isEmpty();
    }

    private float getInclusionChance(LayeredHair.Category category, Gender gender) {
        return (float) hair.values().stream()
                .filter(h -> h.getCategory() == category)
                .filter(h -> h.getGender() == Gender.NEUTRAL || gender == Gender.NEUTRAL || h.getGender() == gender)
                .mapToDouble(LayeredHair::getChance)
                .max()
                .orElse(0.0);
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
}
