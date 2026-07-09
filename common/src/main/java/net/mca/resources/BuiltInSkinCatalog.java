package net.mca.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import net.mca.MCA;
import net.mca.entity.ai.relationship.Gender;
import net.mca.resources.data.skin.BodySkin;
import net.mca.resources.data.skin.Clothing;
import net.mca.resources.data.skin.HairStyle;
import net.mca.resources.data.skin.LayeredHair;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BuiltInSkinCatalog {
    private static final Codec<Map<String, HairStyle.Definition>> HAIR_STYLE_FILE_CODEC = Codec.unboundedMap(Codec.STRING, HairStyle.DEFINITION_CODEC);
    private static final List<String> BODY_SKIN_FILES = List.of("skins", "skin", "female", "male");
    private static final List<String> GENDERED_SKIN_FILES = List.of("female", "male", "neutral");
    private static final List<String> HAIR_LAYER_FILES = List.of("back", "bangs", "base", "extra", "front");
    private static final Catalog CATALOG = load();

    private BuiltInSkinCatalog() {
    }

    public static Catalog get() {
        return CATALOG;
    }

    private static Catalog load() {
        HashMap<String, Clothing> clothing = new HashMap<>();
        HashMap<String, BodySkin> bodySkins = new HashMap<>();
        HashMap<String, LayeredHair> layeredHair = new HashMap<>();
        HashMap<String, HairStyle> hairStyles = new HashMap<>();

        readBundledJsonFiles(ClothingList.ID.getPath(), GENDERED_SKIN_FILES, (id, file) -> SkinCatalogLoader.addClothing(clothing, id, file));

        readBundledJsonFiles(BodySkinList.ID.getPath(), BODY_SKIN_FILES, (id, file) -> {
            Gender fileGender = BodySkinList.getGenderFromPath(id);
            SkinCatalogLoader.addBodySkins(bodySkins, id, file);
        });

        readBundledJsonFiles(HairStyleList.ID.getPath(), GENDERED_SKIN_FILES, (id, file) -> SkinCatalogLoader.addHairStyles(hairStyles, id, file));

        readBundledJsonFiles(LayeredHairList.ID.getPath(), HAIR_LAYER_FILES, (id, file) -> SkinCatalogLoader.addLayeredHair(layeredHair, id, file));

        return new Catalog(clothing, bodySkins, layeredHair, hairStyles);
    }

    private static void addLayeredHair(HashMap<String, LayeredHair> layeredHair, String texture, LayeredHair.Category category) {
        Identifier parsed;
        try {
            parsed = new Identifier(texture);
        } catch (InvalidIdentifierException exception) {
            MCA.LOGGER.warn("Invalid built-in layered hair texture identifier {}", texture, exception);
            return;
        }
        if (!parsed.getPath().startsWith("skins/layered_hair/")) {
            MCA.LOGGER.warn("Invalid built-in layered hair texture path {}", texture);
            return;
        }

        LayeredHair entry = new LayeredHair(texture, Gender.NEUTRAL, category, 1.0F);
        layeredHair.put(entry.getIdentifier() + "|" + entry.getGender().getDataName() + "|" + entry.getCategory().getId(), entry);
    }

    private static LayeredHair.Category getCategoryFromPath(Identifier id) {
        LayeredHair.Category category = LayeredHair.Category.byNameOrNull(id.getPath());
        return category == null ? LayeredHair.Category.BASE : category;
    }

    private static void readBundledJsonFiles(String directory, List<String> files, JsonFileConsumer consumer) {
        ClassLoader loader = BuiltInSkinCatalog.class.getClassLoader();
        List<String> missing = new ArrayList<>();
        boolean foundAny = false;
        for (String file : files) {
            String path = "data/" + MCA.MOD_ID + "/" + directory + "/" + file + ".json";
            Identifier id = new Identifier(MCA.MOD_ID, file);
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

    public record Catalog(Map<String, Clothing> clothing, Map<String, BodySkin> bodySkins, Map<String, LayeredHair> layeredHair, Map<String, HairStyle> hairStyles) {
        public Catalog {
            clothing = Map.copyOf(clothing);
            bodySkins = Map.copyOf(bodySkins);
            layeredHair = Map.copyOf(layeredHair);
            hairStyles = Map.copyOf(hairStyles);
        }
    }

    @FunctionalInterface
    private interface JsonFileConsumer {
        void accept(Identifier id, JsonElement file);
    }
}
