package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.Hair;
import net.conczin.mca.resources.data.skin.HairStyle;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;

public class HairStyleList extends SimpleJsonResourceReloadListener<JsonElement> {
    private static final Codec<Map<String, HairStyle.Definition>> FILE_CODEC = Codec.unboundedMap(Codec.STRING, HairStyle.DEFINITION_CODEC);
    public static final Identifier ID = MCA.locate("skins/hair_styles");
    private static HairStyleList INSTANCE;
    public final HashMap<String, HairStyle> styles = new HashMap<>();

    public HairStyleList() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("skins/hair_styles"));
        INSTANCE = this;
    }

    public static HairStyleList getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        styles.clear();

        data.forEach((id, file) -> FILE_CODEC.parse(JsonOps.INSTANCE, file)
                .resultOrPartial(error -> MCA.LOGGER.warn("Invalid hair style list {}: {}", id, error))
                .ifPresent(entries -> addEntries(id, entries)));
    }

    private void addEntries(Identifier id, Map<String, HairStyle.Definition> entries) {
        Gender fileGender = BodySkinList.getGenderFromPath(id);
        entries.forEach((key, definition) -> styles.put(key, definition.create(key, fileGender)));
    }

    public HashMap<String, HairStyle> getAllStyles(Map<String, Hair> legacyHair) {
        HashMap<String, HairStyle> allStyles = new HashMap<>(styles);
        legacyHair.values().forEach(hair -> allStyles.putIfAbsent(hair.getIdentifier(), HairStyle.fromHair(hair)));
        return allStyles;
    }

    public HairStyle get(String identifier) {
        HairStyle style = styles.get(identifier);
        if (style != null) {
            return style;
        }

        HairList hairList = HairList.getInstance();
        Hair legacy = hairList == null ? null : hairList.hair.get(identifier);
        return legacy == null ? null : HairStyle.fromHair(legacy);
    }

    public WeightedPool<String> getPool(Gender gender) {
        HairList hairList = HairList.getInstance();
        Map<String, Hair> legacyHair = hairList == null ? Map.of() : hairList.hair;
        return getAllStyles(legacyHair).values().stream()
                .filter(style -> style.getGender() == Gender.NEUTRAL || gender == Gender.NEUTRAL || style.getGender() == gender)
                .collect(() -> new WeightedPool.Mutable<>("mca:missing"),
                        (list, entry) -> list.add(entry.getIdentifier(), entry.getChance()),
                        (a, b) -> a.entries.addAll(b.entries));
    }
}
