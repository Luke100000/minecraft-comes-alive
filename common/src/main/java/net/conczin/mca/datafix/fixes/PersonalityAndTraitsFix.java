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
                typed -> typed.update(DSL.remainderFinder(), this::rewrite)
        );
    }

    private <T> Dynamic<T> rewrite(Dynamic<T> root) {
        Dynamic<T> migrated = root.update("Personality", this::migratePersonality);
        return migrated.update("Traits", this::migrateTraits);
    }

    private <T> Dynamic<T> migratePersonality(Dynamic<T> personality) {
        var numeric = personality.asNumber().result();
        if (numeric.isPresent()) {
            return personality.createString(legacyPersonalityId(numeric.get().intValue()));
        }

        var string = personality.asString().result();
        if (string.isPresent() && !string.get().contains(":")) {
            return personality.createString("mca:" + string.get().toLowerCase(Locale.ROOT));
        }

        return personality;
    }

    private String legacyPersonalityId(int ordinal) {
        return ordinal >= 0 && ordinal < LEGACY_PERSONALITIES.size()
                ? LEGACY_PERSONALITIES.get(ordinal)
                : LEGACY_PERSONALITIES.getFirst();
    }

    private <T> Dynamic<T> migrateTraits(Dynamic<T> traits) {
        var entries = traits.asMapOpt().result();
        if (entries.isEmpty()) {
            return traits;
        }

        Map<String, Dynamic<T>> canonicalValues = new LinkedHashMap<>();
        entries.get().forEach(entry -> {
            String sourceId = entry.getFirst().asString("");
            String canonicalId = sourceId.isEmpty() || sourceId.contains(":")
                    ? sourceId
                    : "mca:" + sourceId;
            boolean sourceIsCanonical = sourceId.contains(":");

            if (sourceIsCanonical || !canonicalValues.containsKey(canonicalId)) {
                canonicalValues.put(canonicalId, entry.getSecond());
            }
        });

        Map<Dynamic<?>, Dynamic<?>> migrated = new LinkedHashMap<>();
        canonicalValues.forEach((id, value) -> migrated.put(traits.createString(id), value));
        return traits.createMap(migrated);
    }
}
