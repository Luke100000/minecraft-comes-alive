package net.conczin.mca.client.resources;

import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.CustomSkinListRequest;
import net.conczin.mca.resources.BuiltInSkinCatalog;
import net.conczin.mca.resources.data.skin.BodySkin;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.Hair;
import net.conczin.mca.resources.data.skin.HairStyle;
import net.conczin.mca.resources.data.skin.LayeredHair;

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
    private static HashMap<String, Clothing> builtInClothing = new HashMap<>();
    private static HashMap<String, BodySkin> builtInBodySkins = new HashMap<>();
    private static HashMap<String, LayeredHair> builtInLayeredHair = new HashMap<>();
    private static HashMap<String, HairStyle> builtInHairStyles = new HashMap<>();
    private static HashMap<String, Clothing> serverClothing = new HashMap<>();
    private static HashMap<String, BodySkin> serverBodySkins = new HashMap<>();
    private static HashMap<String, LayeredHair> serverLayeredHair = new HashMap<>();
    private static HashMap<String, HairStyle> serverHairStyles = new HashMap<>();
    private static HashMap<String, Hair> serverHair = new HashMap<>();

    private ClientSkinCatalog() {
    }

    public static void installServerDelta(HashMap<String, Clothing> clothing, HashMap<String, BodySkin> bodySkins, HashMap<String, LayeredHair> layeredHair, HashMap<String, HairStyle> hairStyles, HashMap<String, Hair> hair) {
        serverClothing = new HashMap<>(clothing);
        serverBodySkins = new HashMap<>(bodySkins);
        serverLayeredHair = new HashMap<>(layeredHair);
        serverHairStyles = new HashMap<>(hairStyles);
        serverHair = new HashMap<>(hair);
        mergeCatalogs();
        loaded = hasBuiltInResources();
    }

    public static void markCustomSkinsOutdated() {
        customSkinsOutdated = true;
    }

    public static void markClientResourcesOutdated() {
        builtInClothing.clear();
        builtInBodySkins.clear();
        builtInLayeredHair.clear();
        builtInHairStyles.clear();
        loaded = false;
        mergeCatalogs();
    }

    public static void clear() {
        clothing.clear();
        hair.clear();
        bodySkins.clear();
        layeredHair.clear();
        hairStyles.clear();
        serverClothing.clear();
        serverBodySkins.clear();
        serverLayeredHair.clear();
        serverHairStyles.clear();
        serverHair.clear();
        mergeCatalogs();
        loaded = hasBuiltInResources();
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

        loadBuiltInCatalog();
        mergeCatalogs();
        loaded = hasBuiltInResources();
    }

    private static void loadBuiltInCatalog() {
        BuiltInSkinCatalog.Catalog catalog = BuiltInSkinCatalog.get();
        builtInClothing = new HashMap<>(catalog.clothing());
        builtInBodySkins = new HashMap<>(catalog.bodySkins());
        builtInLayeredHair = new HashMap<>(catalog.layeredHair());
        builtInHairStyles = new HashMap<>(catalog.hairStyles());
    }

    private static void mergeCatalogs() {
        clothing = new HashMap<>(builtInClothing);
        bodySkins = new HashMap<>(builtInBodySkins);
        layeredHair = new HashMap<>(builtInLayeredHair);
        hairStyles = new HashMap<>(builtInHairStyles);
        hair = new HashMap<>();

        clothing.putAll(serverClothing);
        bodySkins.putAll(serverBodySkins);
        layeredHair.putAll(serverLayeredHair);
        hairStyles.putAll(serverHairStyles);
        hair.putAll(serverHair);
        serverHair.values().forEach(custom -> hairStyles.putIfAbsent(custom.getIdentifier(), HairStyle.fromHair(custom)));
    }

    private static boolean hasBuiltInResources() {
        return !builtInClothing.isEmpty()
                || !builtInBodySkins.isEmpty()
                || !builtInLayeredHair.isEmpty()
                || !builtInHairStyles.isEmpty();
    }
}
