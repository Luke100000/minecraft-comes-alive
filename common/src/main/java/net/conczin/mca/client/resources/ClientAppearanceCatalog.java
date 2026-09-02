package net.conczin.mca.client.resources;

import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.BuiltInAppearanceCatalog;
import net.conczin.mca.resources.EyeDefinition;
import net.conczin.mca.resources.EyeStyles;
import net.conczin.mca.resources.data.skin.*;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ClientAppearanceCatalog {
    private static final BuiltInAppearanceCatalog.Catalog BUILT_IN = BuiltInAppearanceCatalog.get();

    private static Map<String, Clothing> clothing = BUILT_IN.clothing();
    private static Map<String, BodySkin> bodySkins = BUILT_IN.bodySkins();
    private static Map<String, LayeredHair> layeredHair = BUILT_IN.layeredHair();
    private static Map<String, HairStyle> hairStyles = BUILT_IN.hairStyles();
    private static Map<String, Hair> hair = Map.of();
    private static Map<ResourceLocation, EyeDefinition> eyes = BUILT_IN.eyes();
    private static Map<String, List<ResourceLocation>> eyeIdsByVariant = indexEyeIds(eyes);

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
        eyeIdsByVariant = indexEyeIds(eyes);
    }

    public static void clear() {
        clothing = BUILT_IN.clothing();
        bodySkins = BUILT_IN.bodySkins();
        layeredHair = BUILT_IN.layeredHair();
        hairStyles = BUILT_IN.hairStyles();
        hair = Map.of();
        eyes = BUILT_IN.eyes();
        eyeIdsByVariant = indexEyeIds(eyes);
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

    public static List<ResourceLocation> eyeIds(String variant) {
        return eyeIdsByVariant.getOrDefault(key(variant), List.of());
    }

    public static List<ResourceLocation> eyeIdsForEditor(String variant, Gender filterGender) {
        return eyeIds(variant).stream()
                .filter(id -> {
                    EyeDefinition entry = eyes.get(id);
                    return entry.gender() == Gender.NEUTRAL || entry.gender() == filterGender;
                })
                .toList();
    }

    public static boolean hasEyeGender(String variant, Gender gender) {
        return eyeIds(variant).stream().anyMatch(id -> eyes.get(id).gender() == gender);
    }

    public static ResourceLocation resolveEye(String variant, ResourceLocation eye) {
        List<ResourceLocation> pool = eyeIds(variant);
        if (pool.isEmpty()) {
            return EyeStyles.DEFAULT;
        }
        EyeDefinition definition = eyes.get(eye);
        if (definition != null && definition.variant().equals(key(variant))) {
            return eye;
        }
        return pool.get(Math.floorMod(eye.hashCode(), pool.size()));
    }

    public static EyeDefinition eyeDefinition(ResourceLocation id) {
        EyeDefinition definition = eyes.get(id);
        return definition != null
                ? definition
                : new EyeDefinition(id, EyeStyles.DEFAULT_VARIANT, Gender.NEUTRAL, 1.0F, false, Map.of());
    }

    private static Map<String, List<ResourceLocation>> indexEyeIds(Map<ResourceLocation, EyeDefinition> definitions) {
        HashMap<String, List<ResourceLocation>> variants = new HashMap<>();
        definitions.values().stream()
                .sorted(Comparator.comparing(entry -> entry.id().toString(), SkinListEntry::compareIdentifiers))
                .forEach(entry -> variants
                        .computeIfAbsent(key(entry.variant()), ignored -> new ArrayList<>())
                        .add(entry.id()));
        variants.replaceAll((variant, ids) -> List.copyOf(ids));
        return Map.copyOf(variants);
    }

    private static String key(String variant) {
        return variant.toLowerCase(Locale.ROOT);
    }
}
