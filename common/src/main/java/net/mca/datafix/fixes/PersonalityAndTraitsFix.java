package net.mca.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import net.mca.datafix.McaDataFixers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts the 1.20.1 MCA tracked-data layout to the stable 1.21.1 layout.
 *
 * <p>The 1.20.1 backport predates both the PascalCase tracked-data keys and the
 * extensible personality/trait identifiers. Its personality enum also has a
 * different ordinal order than the later 1.21 enum, so its ordinals must be
 * migrated by meaning rather than by the 1.21 ordinal table.</p>
 */
public final class PersonalityAndTraitsFix extends DataFix {
    private static final List<String> LEGACY_PERSONALITIES = List.of(
            "mca:unassigned",   // UNASSIGNED
            "mca:unassigned",   // ATHLETIC -> mca:athletic trait
            "mca:confident",    // CONFIDENT remains a personality
            "mca:friendly",
            "mca:flirty",
            "mca:upbeat",       // WITTY: retained the "likes jokes" behavior
            "mca:introverted",  // SHY
            "mca:gloomy",
            "mca:sensitive",
            "mca:greedy",
            "mca:odd",
            "mca:relaxed",      // LAZY
            "mca:crabby",       // GRUMPY
            "mca:peppy"         // PEPPY remains a personality
    );

    private static final Map<String, String> LEGACY_PERSONALITY_NAMES = Map.ofEntries(
            Map.entry("athletic", "unassigned"),
            Map.entry("witty", "upbeat"),
            Map.entry("shy", "introverted"),
            Map.entry("lazy", "relaxed"),
            Map.entry("grumpy", "crabby")
    );

    private static final Map<String, String> LEGACY_KEYS = Map.ofEntries(
            Map.entry("babyItem", "BabyItem"),
            Map.entry("wood", "Wood"),
            Map.entry("color", "Color"),
            Map.entry("isProcreating", "IsProcreating"),
            Map.entry("lastProcreation", "LastProcreation"),
            Map.entry("memories", "Memories"),
            Map.entry("personality", "Personality"),
            Map.entry("traits", "Traits"),
            Map.entry("mood", "Mood"),
            Map.entry("moveState", "MoveState"),
            Map.entry("activeChore", "ActiveChore"),
            Map.entry("choreAssigningPlayer", "ChoreAssigningPlayer"),
            Map.entry("isPanicking", "IsPanicking"),
            Map.entry("wearArmor", "WearArmor"),
            Map.entry("clothes", "Clothes"),
            Map.entry("hair", "Hair"),
            Map.entry("ageState", "AgeState"),
            Map.entry("infectionProgress", "InfectionProgress"),
            Map.entry("growthAmount", "GrowthAmount"),
            Map.entry("attackStage", "AttackStage"),
            Map.entry("hasBaby", "HasBaby"),
            Map.entry("isBabyMale", "IsBabyMale"),
            Map.entry("babyAge", "BabyAge"),
            Map.entry("buildings", "HomeVillage"),
            Map.entry("gender", "Gender"),
            Map.entry("gene_size", "GeneSize"),
            Map.entry("gene_width", "GeneWidth"),
            Map.entry("gene_breast", "GeneBreast"),
            Map.entry("gene_melanin", "GeneMelanin"),
            Map.entry("gene_hemoglobin", "GeneHemoglobin"),
            Map.entry("gene_eumelanin", "GeneEumelanin"),
            Map.entry("gene_pheomelanin", "GenePheomelanin"),
            Map.entry("gene_skin", "GeneSkin"),
            Map.entry("gene_face", "GeneFace"),
            Map.entry("gene_eyebrightness", "GeneEyeBrightness"),
            Map.entry("gene_voice", "GeneVoice"),
            Map.entry("gene_voice_tone", "GeneVoiceTone")
    );

    public PersonalityAndTraitsFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return fixTypeEverywhereTyped(
                "MCA: migrate 1.20 tracked keys, personality and trait identifiers",
                getInputSchema().getType(McaDataFixers.MCA_DATA),
                typed -> typed.update(DSL.remainderFinder(), PersonalityAndTraitsFix::rewrite)
        );
    }

    private static <T> Dynamic<T> rewrite(Dynamic<T> root) {
        var entries = root.asMapOpt().result().orElse(null);
        if (entries == null) {
            return root;
        }

        Map<String, Dynamic<T>> values = new LinkedHashMap<>();
        Dynamic<T> legacyHairRed = null;
        Dynamic<T> legacyHairGreen = null;
        Dynamic<T> legacyHairBlue = null;
        var iterator = entries.iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            String sourceKey = entry.getFirst().asString("");
            if (sourceKey.isEmpty()) {
                continue;
            }

            switch (sourceKey) {
                case "hair_color_red" -> {
                    legacyHairRed = entry.getSecond();
                    continue;
                }
                case "hair_color_green" -> {
                    legacyHairGreen = entry.getSecond();
                    continue;
                }
                case "hair_color_blue" -> {
                    legacyHairBlue = entry.getSecond();
                    continue;
                }
                default -> {
                }
            }

            String canonicalKey = LEGACY_KEYS.getOrDefault(sourceKey, sourceKey);
            if (sourceKey.equals(canonicalKey)) {
                values.put(canonicalKey, entry.getSecond());
            } else {
                values.putIfAbsent(canonicalKey, entry.getSecond());
            }
        }

        if (!values.containsKey("HairColor")
                && (legacyHairRed != null || legacyHairGreen != null || legacyHairBlue != null)) {
            float red = legacyFloat(legacyHairRed);
            float green = legacyFloat(legacyHairGreen);
            float blue = legacyFloat(legacyHairBlue);
            int hairColor = red > 0.0F || green > 0.0F || blue > 0.0F
                    ? packArgb(red, green, blue)
                    : 0xFF000000;
            values.put("HairColor", root.createInt(hairColor));
        }

        Dynamic<T> personality = values.get("Personality");
        boolean legacyAthletic = personality != null && isLegacyAthletic(personality);
        if (personality != null) {
            values.put("Personality", migratePersonality(personality));
        }

        Dynamic<T> traits = values.get("Traits");
        if (traits != null || legacyAthletic) {
            values.put("Traits", migrateTraits(traits == null ? root : traits, legacyAthletic, traits == null));
        }

        Map<Dynamic<?>, Dynamic<?>> migrated = new LinkedHashMap<>();
        for (Map.Entry<String, Dynamic<T>> entry : values.entrySet()) {
            migrated.put(root.createString(entry.getKey()), entry.getValue());
        }
        return root.createMap(migrated);
    }

    private static <T> boolean isLegacyAthletic(Dynamic<T> personality) {
        Number numeric = personality.asNumber().result().orElse(null);
        if (numeric != null) {
            return numeric.intValue() == 1;
        }
        String value = personality.asString().result().orElse("").toLowerCase(Locale.ROOT);
        return value.equals("athletic") || value.equals("mca:athletic");
    }

    private static <T> Dynamic<T> migratePersonality(Dynamic<T> personality) {
        Number numeric = personality.asNumber().result().orElse(null);
        if (numeric != null) {
            return personality.createString(legacyPersonalityId(numeric.intValue()));
        }

        String value = personality.asString().result().orElse(null);
        if (value == null) {
            return personality;
        }

        String normalized = value.toLowerCase(Locale.ROOT);
        String namespace = "mca";
        String path = normalized;
        int colon = normalized.indexOf(':');
        if (colon >= 0) {
            namespace = normalized.substring(0, colon);
            path = normalized.substring(colon + 1);
        }

        if (namespace.equals("mca")) {
            path = LEGACY_PERSONALITY_NAMES.getOrDefault(path, path);
        }
        if (path.isBlank()) {
            path = "unassigned";
        }
        return personality.createString(namespace + ":" + path);
    }

    private static String legacyPersonalityId(int ordinal) {
        return ordinal >= 0 && ordinal < LEGACY_PERSONALITIES.size()
                ? LEGACY_PERSONALITIES.get(ordinal)
                : LEGACY_PERSONALITIES.get(0);
    }

    private static <T> Dynamic<T> migrateTraits(Dynamic<T> traits, boolean addAthletic, boolean createEmpty) {
        Map<String, Dynamic<T>> canonicalValues = new LinkedHashMap<>();
        if (!createEmpty) {
            var entries = traits.asMapOpt().result().orElse(null);
            if (entries == null) {
                return traits;
            }

            var iterator = entries.iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                String sourceId = entry.getFirst().asString("");
                if (sourceId.isEmpty()) {
                    continue;
                }
                String normalized = sourceId.toLowerCase(Locale.ROOT);
                String canonicalId = normalized.contains(":") ? normalized : "mca:" + normalized;

                if (sourceId.contains(":")) {
                    canonicalValues.put(canonicalId, entry.getSecond());
                } else {
                    canonicalValues.putIfAbsent(canonicalId, entry.getSecond());
                }
            }
        }

        if (addAthletic) {
            canonicalValues.put("mca:athletic", traits.createBoolean(true));
        }

        Map<Dynamic<?>, Dynamic<?>> migrated = new LinkedHashMap<>();
        for (Map.Entry<String, Dynamic<T>> entry : canonicalValues.entrySet()) {
            migrated.put(traits.createString(entry.getKey()), entry.getValue());
        }
        return traits.createMap(migrated);
    }

    private static <T> float legacyFloat(Dynamic<T> value) {
        if (value == null) {
            return 0.0F;
        }
        Number numeric = value.asNumber().result().orElse(null);
        return numeric == null ? 0.0F : numeric.floatValue();
    }

    private static int packArgb(float red, float green, float blue) {
        int r = Math.max(0, Math.min(255, Math.round(red * 255.0F)));
        int g = Math.max(0, Math.min(255, Math.round(green * 255.0F)));
        int b = Math.max(0, Math.min(255, Math.round(blue * 255.0F)));
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
