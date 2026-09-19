package net.conczin.mca.client.resources;

import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.BuiltInAppearanceCatalog;
import net.conczin.mca.resources.EyeDefinition;
import net.conczin.mca.resources.EyeStyles;
import net.conczin.mca.resources.SkinSelection;
import net.conczin.mca.resources.data.skin.*;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ClientAppearanceCatalog {
    private static final BuiltInAppearanceCatalog.Catalog BUILT_IN = BuiltInAppearanceCatalog.get();

    private static Map<String, Clothing> clothing = BUILT_IN.clothing();
    private static Map<String, BodySkin> bodySkins = BUILT_IN.bodySkins();
    private static Map<String, LayeredHair> layeredHair = BUILT_IN.layeredHair();
    private static Map<String, HairStyle> hairStyles = BUILT_IN.hairStyles();
    private static Map<String, Hair> hair = Map.of();
    private static Map<ResourceLocation, EyeDefinition> eyes = BUILT_IN.eyes();
    private static List<ResourceLocation> eyeIds = indexEyeIds(eyes);

    private ClientAppearanceCatalog() {
    }

    public static void installServerSnapshot(
            Map<String, Clothing> serverClothing,
            Map<String, BodySkin> serverBodySkins,
            Map<String, LayeredHair> serverLayeredHair,
            Map<String, HairStyle> serverHairStyles,
            Map<String, Hair> serverHair,
            Map<ResourceLocation, EyeDefinition> serverEyes
    ) {
        clothing = Map.copyOf(serverClothing);
        bodySkins = Map.copyOf(serverBodySkins);
        layeredHair = Map.copyOf(serverLayeredHair);
        hairStyles = Map.copyOf(serverHairStyles);
        hair = Map.copyOf(serverHair);
        eyes = Map.copyOf(serverEyes);
        eyeIds = indexEyeIds(eyes);
    }

    public static void clear() {
        clothing = BUILT_IN.clothing();
        bodySkins = BUILT_IN.bodySkins();
        layeredHair = BUILT_IN.layeredHair();
        hairStyles = BUILT_IN.hairStyles();
        hair = Map.of();
        eyes = BUILT_IN.eyes();
        eyeIds = indexEyeIds(eyes);
    }

    public static Map<String, Clothing> clothing() {
        return clothing;
    }

    public static Map<String, Hair> hair() {
        return hair;
    }

    public static Map<String, BodySkin> bodySkins() {
        return bodySkins;
    }

    public static Map<String, LayeredHair> layeredHair() {
        return layeredHair;
    }

    public static Map<String, HairStyle> hairStyles() {
        return hairStyles;
    }

    public static List<ResourceLocation> eyeIdsForGender(Gender gender) {
        return eyeIds.stream()
                .filter(id -> SkinSelection.matchesGender(eyes.get(id).gender(), gender))
                .toList();
    }

    public static List<ResourceLocation> eyeIdsForEditor(Gender filterGender) {
        return eyeIds.stream()
                .filter(id -> SkinSelection.matchesEditorGender(eyes.get(id).gender(), filterGender))
                .toList();
    }

    public static ResourceLocation resolveEye(ResourceLocation eye) {
        return resolveEye(eye, Gender.NEUTRAL);
    }

    public static ResourceLocation resolveEye(ResourceLocation eye, Gender gender) {
        EyeDefinition current = eyes.get(eye);
        if (current != null && SkinSelection.matchesGender(current.gender(), gender)) {
            return eye;
        }

        ResourceLocation counterpart = EyeStyles.forGender(eye, gender);
        EyeDefinition counterpartDefinition = eyes.get(counterpart);
        if (counterpartDefinition != null && SkinSelection.matchesGender(counterpartDefinition.gender(), gender)) {
            return counterpart;
        }

        int candidateCount = 0;
        for (ResourceLocation candidate : eyeIds) {
            if (SkinSelection.matchesGender(eyes.get(candidate).gender(), gender)) {
                candidateCount++;
            }
        }
        if (candidateCount == 0) {
            return EyeStyles.DEFAULT;
        }

        int index = Math.floorMod(eye.hashCode(), candidateCount);
        for (ResourceLocation candidate : eyeIds) {
            if (SkinSelection.matchesGender(eyes.get(candidate).gender(), gender) && index-- == 0) {
                return candidate;
            }
        }
        return EyeStyles.DEFAULT;
    }

    public static EyeDefinition eyeDefinition(ResourceLocation id) {
        EyeDefinition definition = eyes.get(id);
        return definition != null
                ? definition
                : new EyeDefinition(id, Gender.NEUTRAL, 1.0F, Map.of());
    }

    private static List<ResourceLocation> indexEyeIds(Map<ResourceLocation, EyeDefinition> definitions) {
        return definitions.values().stream()
                .sorted(Comparator.comparing(entry -> entry.id().toString(), SkinListEntry::compareIdentifiers))
                .map(EyeDefinition::id)
                .toList();
    }
}
