package net.conczin.mca.client.resources;

import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.BuiltInAppearanceCatalog;
import net.conczin.mca.resources.EyeDefinition;
import net.conczin.mca.resources.EyeSelection;
import net.conczin.mca.resources.data.skin.*;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ClientAppearanceCatalog {
    private static final BuiltInAppearanceCatalog.Catalog BUILT_IN = BuiltInAppearanceCatalog.get();

    private static Map<String, Clothing> clothing = BUILT_IN.clothing();
    private static Map<String, BodySkin> bodySkins = BUILT_IN.bodySkins();
    private static Map<String, LayeredHair> layeredHair = BUILT_IN.layeredHair();
    private static Map<String, HairStyle> hairStyles = BUILT_IN.hairStyles();
    private static Map<String, Hair> hair = Map.of();
    private static Map<ResourceLocation, EyeDefinition> eyes = Map.of();
    private static boolean serverSnapshotReady;

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
        serverSnapshotReady = true;
    }

    public static void clear() {
        clothing = BUILT_IN.clothing();
        bodySkins = BUILT_IN.bodySkins();
        layeredHair = BUILT_IN.layeredHair();
        hairStyles = BUILT_IN.hairStyles();
        hair = Map.of();
        eyes = Map.of();
        serverSnapshotReady = false;
    }

    public static boolean applyClothingUpdate(String identifier, Optional<Clothing> value) {
        if (!serverSnapshotReady || !validEntry(identifier, value)) {
            return false;
        }
        Map<String, Clothing> changed = new HashMap<>(clothing);
        if (value.isPresent()) {
            changed.put(identifier, value.get());
        } else {
            changed.remove(identifier);
        }
        clothing = Map.copyOf(changed);
        return true;
    }

    public static boolean applyHairUpdate(String identifier, Optional<Hair> newHair, Optional<HairStyle> newStyle) {
        if (!serverSnapshotReady || !validEntry(identifier, newHair) || !validEntry(identifier, newStyle)) {
            return false;
        }
        Map<String, Hair> changedHair = new HashMap<>(hair);
        Map<String, HairStyle> changedStyles = new HashMap<>(hairStyles);
        if (newHair.isPresent()) {
            changedHair.put(identifier, newHair.get());
        } else {
            changedHair.remove(identifier);
        }
        if (newStyle.isPresent()) {
            changedStyles.put(identifier, newStyle.get());
        } else {
            changedStyles.remove(identifier);
        }
        hair = Map.copyOf(changedHair);
        hairStyles = Map.copyOf(changedStyles);
        return true;
    }

    private static boolean validEntry(String identifier, Optional<? extends SkinListEntry> value) {
        if (identifier == null || ResourceLocation.tryParse(identifier) == null || value == null) {
            return false;
        }
        return value.isEmpty() || identifier.equals(value.get().getIdentifier());
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
        return EyeSelection.idsForGender(eyes, gender);
    }

    public static List<ResourceLocation> eyeIdsForEditor(Gender filterGender) {
        return EyeSelection.idsForEditor(eyes, filterGender);
    }

    public static ResourceLocation resolveEye(ResourceLocation eye, Gender gender) {
        return EyeSelection.resolve(eyes, eye, gender);
    }

    public static EyeDefinition eyeDefinition(ResourceLocation id) {
        return eyes.get(id);
    }

}
