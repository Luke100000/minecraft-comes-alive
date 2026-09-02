package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.conczin.mca.MCA;
import net.conczin.mca.resources.data.skin.BodySkin;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.HairStyle;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BuiltInAppearanceCatalog {
    private static final List<String> BODY_SKIN_FILES = List.of("skins", "female", "male");
    private static final List<String> GENDERED_SKIN_FILES = List.of("female", "male", "neutral");
    private static final List<String> HAIR_LAYER_FILES = List.of("back", "bangs", "base", "extra", "front");
    private static final List<String> EYE_FILES = List.of(
            EyeStyles.DEFAULT_VARIANT,
            "female",
            "male"
    );
    private static final Catalog CATALOG = load();

    private BuiltInAppearanceCatalog() {
    }

    public static Catalog get() {
        return CATALOG;
    }

    private static Catalog load() {
        HashMap<String, Clothing> clothing = new HashMap<>();
        HashMap<String, BodySkin> bodySkins = new HashMap<>();
        HashMap<String, LayeredHair> layeredHair = new HashMap<>();
        HashMap<String, HairStyle> hairStyles = new HashMap<>();
        HashMap<ResourceLocation, EyeDefinition> eyes = new HashMap<>();

        readBundledJsonFiles(ClothingList.ID.getPath(), GENDERED_SKIN_FILES, (id, file) -> AppearanceCatalogLoader.addClothing(clothing, id, file));
        readBundledJsonFiles(BodySkinList.ID.getPath(), BODY_SKIN_FILES, (id, file) -> AppearanceCatalogLoader.addBodySkins(bodySkins, id, file));
        readBundledJsonFiles(HairStyleList.ID.getPath(), GENDERED_SKIN_FILES, (id, file) -> AppearanceCatalogLoader.addHairStyles(hairStyles, id, file));
        readBundledJsonFiles(LayeredHairList.ID.getPath(), HAIR_LAYER_FILES, (id, file) -> AppearanceCatalogLoader.addLayeredHair(layeredHair, id, file));
        readBundledJsonFiles(EyeCatalog.ID.getPath(), EYE_FILES, (id, file) -> AppearanceCatalogLoader.addEyes(eyes, id, file));

        return new Catalog(clothing, bodySkins, layeredHair, hairStyles, eyes);
    }

    private static void readBundledJsonFiles(String directory, List<String> files, JsonFileConsumer consumer) {
        ClassLoader loader = BuiltInAppearanceCatalog.class.getClassLoader();
        List<String> missing = new ArrayList<>();
        boolean foundAny = false;
        for (String file : files) {
            String path = "data/" + MCA.MOD_ID + "/" + directory + "/" + file + ".json";
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MCA.MOD_ID, file);
            try (InputStream stream = loader.getResourceAsStream(path)) {
                if (stream != null) {
                    foundAny = true;
                    try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                        consumer.accept(id, JsonParser.parseReader(reader));
                    }
                } else {
                    missing.add(path);
                }
            } catch (Exception exception) {
                MCA.LOGGER.warn("Failed to read built-in skin list {}", path, exception);
            }
        }

        if (!foundAny) {
            missing.forEach(path -> MCA.LOGGER.warn("Failed to find built-in skin list {}", path));
        }
    }

    public record Catalog(
            Map<String, Clothing> clothing,
            Map<String, BodySkin> bodySkins,
            Map<String, LayeredHair> layeredHair,
            Map<String, HairStyle> hairStyles,
            Map<ResourceLocation, EyeDefinition> eyes
    ) {
        public Catalog {
            clothing = Map.copyOf(clothing);
            bodySkins = Map.copyOf(bodySkins);
            layeredHair = Map.copyOf(layeredHair);
            hairStyles = Map.copyOf(hairStyles);
            eyes = Map.copyOf(eyes);
        }
    }

    @FunctionalInterface
    private interface JsonFileConsumer {
        void accept(ResourceLocation id, JsonElement file);
    }
}
