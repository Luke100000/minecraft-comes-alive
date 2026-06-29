package net.conczin.mca.client.resources;

import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.CustomSkinListRequest;
import net.conczin.mca.resources.BodySkinList;
import net.conczin.mca.resources.ClothingList;
import net.conczin.mca.resources.HairStyleList;
import net.conczin.mca.resources.LayeredHairList;
import net.conczin.mca.resources.data.skin.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ClientSkinCatalog {
    private static boolean customSkinsOutdated = true;
    private static boolean loaded;
    private static HashMap<String, Clothing> clothing = new HashMap<>();
    private static HashMap<String, Hair> hair = new HashMap<>();
    private static HashMap<String, BodySkin> bodySkins = new HashMap<>();
    private static HashMap<String, LayeredHair> layeredHair = new HashMap<>();
    private static HashMap<String, HairStyle> hairStyles = new HashMap<>();
    private static HashMap<String, Clothing> customClothing = new HashMap<>();
    private static HashMap<String, Hair> customHair = new HashMap<>();

    private ClientSkinCatalog() {
    }

    public static void installCustomSkins(HashMap<String, Clothing> clothing, HashMap<String, Hair> hair) {
        customClothing = new HashMap<>(clothing);
        customHair = new HashMap<>(hair);
        loadClientResources();
        loaded = hasClientResources();
    }

    public static void markCustomSkinsOutdated() {
        customSkinsOutdated = true;
        loaded = false;
    }

    public static void markClientResourcesOutdated() {
        clothing.clear();
        hair.clear();
        bodySkins.clear();
        layeredHair.clear();
        hairStyles.clear();
        loaded = false;
    }

    public static void clear() {
        clothing.clear();
        hair.clear();
        bodySkins.clear();
        layeredHair.clear();
        hairStyles.clear();
        customClothing.clear();
        customHair.clear();
        loaded = false;
        customSkinsOutdated = true;
    }

    public static Map<String, Clothing> clothing() {
        sync();
        return Collections.unmodifiableMap(clothing);
    }

    public static Map<String, Hair> hair() {
        sync();
        return Collections.unmodifiableMap(hair);
    }

    public static Map<String, BodySkin> bodySkins() {
        sync();
        return Collections.unmodifiableMap(bodySkins);
    }

    public static Map<String, LayeredHair> layeredHair() {
        sync();
        return Collections.unmodifiableMap(layeredHair);
    }

    public static Map<String, HairStyle> hairStyles() {
        sync();
        return Collections.unmodifiableMap(hairStyles);
    }

    public static void sync() {
        seedClientResources();
        if (customSkinsOutdated) {
            Network.sendToServer(new CustomSkinListRequest());
            customSkinsOutdated = false;
        }
    }

    private static void seedClientResources() {
        if (loaded) {
            return;
        }

        loadClientResources();
        loaded = hasClientResourceLists();
    }

    private static void loadClientResources() {
        ClothingList clothingList = ClothingList.getInstance();
        BodySkinList bodySkinList = BodySkinList.getInstance();
        LayeredHairList layeredHairList = LayeredHairList.getInstance();

        clothing = clothingList == null ? new HashMap<>() : new HashMap<>(clothingList.clothing);
        hair = new HashMap<>();
        bodySkins = bodySkinList == null ? new HashMap<>() : new HashMap<>(bodySkinList.skins);
        layeredHair = layeredHairList == null ? new HashMap<>() : new HashMap<>(layeredHairList.hair);
        clothing.putAll(customClothing);
        hair.putAll(customHair);
        hairStyles = loadHairStyles(hair);
    }

    private static boolean hasClientResources() {
        return hasClientResourceLists();
    }

    private static boolean hasClientResourceLists() {
        ClothingList clothingList = ClothingList.getInstance();
        BodySkinList bodySkinList = BodySkinList.getInstance();
        LayeredHairList layeredHairList = LayeredHairList.getInstance();
        HairStyleList hairStyleList = HairStyleList.getInstance();
        return clothingList != null && !clothingList.clothing.isEmpty()
                || bodySkinList != null && !bodySkinList.skins.isEmpty()
                || layeredHairList != null && !layeredHairList.hair.isEmpty()
                || hairStyleList != null && !hairStyleList.styles.isEmpty();
    }

    private static HashMap<String, HairStyle> loadHairStyles(Map<String, Hair> legacyHair) {
        HairStyleList hairStyleList = HairStyleList.getInstance();
        if (hairStyleList != null) {
            return hairStyleList.getAllStyles(legacyHair);
        }

        HashMap<String, HairStyle> styles = new HashMap<>();
        legacyHair.values().forEach(hair -> styles.putIfAbsent(hair.getIdentifier(), HairStyle.fromHair(hair)));
        return styles;
    }
}
