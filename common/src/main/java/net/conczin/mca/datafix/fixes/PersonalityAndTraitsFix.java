package net.conczin.mca.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import net.conczin.mca.datafix.McaDataFixers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts the former ordinal personality and bare MCA trait keys to stable,
 * namespaced identifiers.
 */
public final class PersonalityAndTraitsFix extends DataFix {
    private static final List<String> LEGACY_PERSONALITIES = List.of(
            "mca:unassigned",
            "mca:friendly",
            "mca:flirty",
            "mca:playful",
            "mca:gloomy",
            "mca:sensitive",
            "mca:greedy",
            "mca:odd",
            "mca:crabby",
            "mca:extroverted",
            "mca:introverted",
            "mca:relaxed",
            "mca:anxious",
            "mca:peaceful",
            "mca:upbeat"
    );

    public PersonalityAndTraitsFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return fixTypeEverywhereTyped(
                "MCA: migrate personality and trait identifiers",
                getInputSchema().getType(McaDataFixers.MCA_DATA),
                typed -> typed.update(DSL.remainderFinder(), PersonalityAndTraitsFix::rewrite)
        );
    }

    private static <T> Dynamic<T> rewrite(Dynamic<T> root) {
        return root
                .update("Personality", PersonalityAndTraitsFix::migratePersonality)
                .update("Traits", PersonalityAndTraitsFix::migrateTraits);
    }

    private static <T> Dynamic<T> migratePersonality(Dynamic<T> personality) {
        Number numeric = personality.asNumber().result().orElse(null);
        if (numeric != null) {
            return personality.createString(legacyPersonalityId(numeric.intValue()));
        }

        String value = personality.asString().result().orElse(null);
        if (value != null && !value.contains(":")) {
            String normalized = value.toLowerCase(Locale.ROOT);
            return personality.createString(normalized.isBlank()
                    ? LEGACY_PERSONALITIES.getFirst()
                    : "mca:" + normalized);
        }

        return personality;
    }

    private static String legacyPersonalityId(int ordinal) {
        return ordinal >= 0 && ordinal < LEGACY_PERSONALITIES.size()
                ? LEGACY_PERSONALITIES.get(ordinal)
                : LEGACY_PERSONALITIES.getFirst();
    }

    private static <T> Dynamic<T> migrateTraits(Dynamic<T> traits) {
        var entries = traits.asMapOpt().result().orElse(null);
        if (entries == null) {
            return traits;
        }

        Map<String, Dynamic<T>> canonicalValues = new LinkedHashMap<>();
        var iterator = entries.iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            String sourceId = entry.getFirst().asString("");
            boolean namespaced = sourceId.contains(":");
            String canonicalId = sourceId.isEmpty() || namespaced ? sourceId : "mca:" + sourceId;

            if (namespaced) {
                canonicalValues.put(canonicalId, entry.getSecond());
            } else {
                canonicalValues.putIfAbsent(canonicalId, entry.getSecond());
            }
        }

        Map<Dynamic<?>, Dynamic<?>> migrated = new LinkedHashMap<>();
        for (Map.Entry<String, Dynamic<T>> entry : canonicalValues.entrySet()) {
            migrated.put(traits.createString(entry.getKey()), entry.getValue());
        }
        return traits.createMap(migrated);
    }
}
