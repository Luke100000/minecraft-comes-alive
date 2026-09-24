package net.conczin.mca.resources;

import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Shared eye validation and fallback rules for server and client catalogs. */
public final class EyeSelection {
    private static final Comparator<EyeDefinition> BY_ID = Comparator.comparing(
            entry -> entry.id().toString(),
            SkinListEntry::compareIdentifiers
    );

    private EyeSelection() {
    }

    public static boolean contains(Map<ResourceLocation, EyeDefinition> definitions, ResourceLocation eye, Gender gender) {
        EyeDefinition definition = definitions.get(eye);
        return definition != null && SkinSelection.matchesGender(definition.gender(), gender);
    }

    public static ResourceLocation resolve(Map<ResourceLocation, EyeDefinition> definitions, ResourceLocation eye, Gender gender) {
        if (contains(definitions, eye, gender)) {
            return eye;
        }

        List<ResourceLocation> candidates = idsForGender(definitions, gender);
        return candidates.isEmpty()
                ? EyeStyles.DEFAULT
                : candidates.get(Math.floorMod(eye.hashCode(), candidates.size()));
    }

    public static List<ResourceLocation> idsForGender(Map<ResourceLocation, EyeDefinition> definitions, Gender gender) {
        return definitionsForGender(definitions, gender).stream()
                .map(EyeDefinition::id)
                .toList();
    }

    static List<EyeDefinition> definitionsForGender(Map<ResourceLocation, EyeDefinition> definitions, Gender gender) {
        return definitions.values().stream()
                .filter(entry -> SkinSelection.matchesGender(entry.gender(), gender))
                .sorted(BY_ID)
                .toList();
    }
}
