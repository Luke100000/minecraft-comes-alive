package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.Hair;
import net.conczin.mca.resources.data.skin.HairStyle;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class HairStyleList extends SimpleJsonResourceReloadListener {
    private static final Codec<Map<String, HairStyle.Definition>> FILE_CODEC = Codec.unboundedMap(Codec.STRING, HairStyle.DEFINITION_CODEC);
    public static final ResourceLocation ID = MCA.locate("skins/hair_styles");
    private static HairStyleList INSTANCE;
    public final HashMap<String, HairStyle> styles = new HashMap<>();

    public HairStyleList() {
        super(Resources.GSON, ID.getPath());
        INSTANCE = this;
    }

    public static HairStyleList getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        styles.clear();

        data.forEach((id, file) -> FILE_CODEC.parse(JsonOps.INSTANCE, file)
                .resultOrPartial(error -> MCA.LOGGER.warn("Invalid hair style list {}: {}", id, error))
                .ifPresent(entries -> addEntries(id, entries)));
    }

    private void addEntries(ResourceLocation id, Map<String, HairStyle.Definition> entries) {
        Gender fileGender = BodySkinList.getGenderFromPath(id);
        entries.forEach((key, definition) -> styles.put(key, definition.create(key, fileGender)));
    }

    public HashMap<String, HairStyle> getAllStyles(Map<String, Hair> extraHair) {
        HashMap<String, HairStyle> allStyles = new HashMap<>(styles);
        extraHair.values().forEach(hair -> allStyles.putIfAbsent(hair.getIdentifier(), HairStyle.fromHair(hair)));
        return allStyles;
    }

    public HairStyle get(String identifier) {
        return styles.get(identifier);
    }

    public List<HairStyle> getStyles(Gender gender) {
        return getStyles(gender, Map.of());
    }

    public List<HairStyle> getStyles(Gender gender, Map<String, Hair> extraHair) {
        return getAllStyles(extraHair).values().stream()
                .filter(style -> style.getGender() == Gender.NEUTRAL || gender == Gender.NEUTRAL || style.getGender() == gender)
                .sorted((a, b) -> SkinListEntry.compareIdentifiers(a.getIdentifier(), b.getIdentifier()))
                .toList();
    }

    public WeightedPool<String> getPool(Gender gender) {
        return getStyles(gender).stream()
                .collect(() -> new WeightedPool.Mutable<>("mca:missing"),
                        (list, entry) -> list.add(entry.getIdentifier(), entry.getChance()),
                        (a, b) -> a.entries.addAll(b.entries));
    }

    public HairStyle pick(Gender gender) {
        return get(getPool(gender).pickOne());
    }

    public HairStyle pickNext(Gender gender, String currentStyleId, int offset) {
        return get(getPool(gender).pickNext(currentStyleId, offset));
    }

    public Optional<String> findMatchingStyleId(Gender gender, Function<LayeredHair.Category, String> layerGetter) {
        return getStyles(gender).stream()
                .filter(style -> matches(style, layerGetter))
                .map(HairStyle::getIdentifier)
                .findFirst();
    }

    private static boolean matches(HairStyle style, Function<LayeredHair.Category, String> layerGetter) {
        for (LayeredHair.Category category : LayeredHair.Category.values()) {
            if (!layerGetter.apply(category).equals(style.layer(category))) {
                return false;
            }
        }
        return true;
    }
}
