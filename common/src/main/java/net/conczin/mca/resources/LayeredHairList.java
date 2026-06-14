package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.LayeredHair;
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
    private static final Codec<Map<String, LayeredHair.Definition>> FILE_CODEC = Codec.unboundedMap(Codec.STRING, LayeredHair.DEFINITION_CODEC);
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

        data.forEach((id, file) -> FILE_CODEC.parse(JsonOps.INSTANCE, file)
                .resultOrPartial(error -> MCA.LOGGER.warn("Invalid layered hair list {}: {}", id, error))
                .ifPresent(entries -> addEntries(id, entries)));
    }

    private void addEntries(Identifier id, Map<String, LayeredHair.Definition> entries) {
        Gender fileGender = BodySkinList.getGenderFromPath(id);
        LayeredHair.Category fileCategory = getCategoryFromPath(id);
        entries.forEach((key, definition) -> {
            int count = Math.max(1, definition.count());
            for (int i = 0; i < count; i++) {
                String identifier = BodySkinList.formatIdentifier(key, i);
                LayeredHair entry = definition.create(identifier, fileGender, fileCategory);
                hair.put(entry.getIdentifier() + "|" + entry.getGender().getDataName() + "|" + entry.getCategory().getId(), entry);
            }
        });
    }

    public boolean containsIdentifier(String identifier) {
        return hair.values().stream().anyMatch(entry -> entry.getIdentifier().equals(identifier));
    }

    public WeightedPool<String> getPool(LayeredHair.Category category, Gender gender) {
        return hair.values().stream()
                .filter(h -> h.getCategory() == category)
                .filter(h -> h.getGender() == Gender.NEUTRAL || gender == Gender.NEUTRAL || h.getGender() == gender)
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
