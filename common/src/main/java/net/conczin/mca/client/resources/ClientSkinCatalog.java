package net.conczin.mca.client.resources;

import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.CustomSkinListRequest;
import net.conczin.mca.resources.BuiltInSkinCatalog;
import net.conczin.mca.resources.data.skin.*;

import java.util.HashMap;
import java.util.Map;

public final class ClientSkinCatalog {
    private static boolean customSkinsOutdated = true;
    private static final BuiltInSkinCatalog.Catalog BUILT_IN = BuiltInSkinCatalog.get();
    private static Map<String, Clothing> serverClothing = Map.of();
    private static Map<String, BodySkin> serverBodySkins = Map.of();
    private static Map<String, LayeredHair> serverLayeredHair = Map.of();
    private static Map<String, HairStyle> serverHairStyles = Map.of();
    private static Map<String, Hair> serverHair = Map.of();
    private static Map<String, Clothing> clothing = BUILT_IN.clothing();
    private static Map<String, BodySkin> bodySkins = BUILT_IN.bodySkins();
    private static Map<String, LayeredHair> layeredHair = BUILT_IN.layeredHair();
    private static Map<String, HairStyle> hairStyles = BUILT_IN.hairStyles();

    private ClientSkinCatalog() {
    }

    public static void installServerDelta(Map<String, Clothing> clothing, Map<String, BodySkin> bodySkins, Map<String, LayeredHair> layeredHair, Map<String, HairStyle> hairStyles, Map<String, Hair> hair) {
        serverClothing = Map.copyOf(clothing);
        serverBodySkins = Map.copyOf(bodySkins);
        serverLayeredHair = Map.copyOf(layeredHair);
        serverHairStyles = Map.copyOf(hairStyles);
        serverHair = Map.copyOf(hair);
        mergeCatalogs();
    }

    public static void markCustomSkinsOutdated() {
        customSkinsOutdated = true;
    }

    public static void clear() {
        serverClothing = Map.of();
        serverBodySkins = Map.of();
        serverLayeredHair = Map.of();
        serverHairStyles = Map.of();
        serverHair = Map.of();
        mergeCatalogs();
        customSkinsOutdated = true;
    }

    public static Map<String, Clothing> clothing() {
        sync();
        return clothing;
    }

    public static Map<String, Hair> hair() {
        sync();
        return serverHair;
    }

    public static Map<String, BodySkin> bodySkins() {
        sync();
        return bodySkins;
    }

    public static Map<String, LayeredHair> layeredHair() {
        sync();
        return layeredHair;
    }

    public static Map<String, HairStyle> hairStyles() {
        sync();
        return hairStyles;
    }

    public static void sync() {
        if (customSkinsOutdated) {
            Network.sendToServer(new CustomSkinListRequest());
            customSkinsOutdated = false;
        }
    }

    private static void mergeCatalogs() {
        clothing = merge(BUILT_IN.clothing(), serverClothing);
        bodySkins = merge(BUILT_IN.bodySkins(), serverBodySkins);
        layeredHair = merge(BUILT_IN.layeredHair(), serverLayeredHair);

        HashMap<String, HairStyle> styles = new HashMap<>(BUILT_IN.hairStyles());
        styles.putAll(serverHairStyles);
        serverHair.values().forEach(custom -> styles.putIfAbsent(custom.getIdentifier(), HairStyle.fromHair(custom)));
        hairStyles = Map.copyOf(styles);
    }

    private static <T> Map<String, T> merge(Map<String, T> builtIn, Map<String, T> server) {
        if (server.isEmpty()) {
            return builtIn;
        }

        HashMap<String, T> merged = new HashMap<>(builtIn);
        merged.putAll(server);
        return Map.copyOf(merged);
    }

}
