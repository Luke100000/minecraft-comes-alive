package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.BodySkin;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.HairStyle;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BuiltInSkinCatalog {
    private static final Codec<Map<String, HairStyle.Definition>> HAIR_STYLE_FILE_CODEC = Codec.unboundedMap(Codec.STRING, HairStyle.DEFINITION_CODEC);
    private static final List<String> BODY_SKIN_FILES = List.of("skin", "female", "male");
    private static final List<String> GENDERED_SKIN_FILES = List.of("skin", "female", "male", "neutral");
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

        readBundledJsonFiles("skins/clothing", GENDERED_SKIN_FILES, (id, file) -> {
            Gender fileGender = BodySkinList.getGenderFromPath(id);

            for (SkinListJson.Entry entry : SkinListJson.entries(id, file)) {
                Gender entryGender = SkinListJson.resolveGender(fileGender, entry);
                if (entryGender == Gender.UNASSIGNED) {
                    MCA.LOGGER.warn("Invalid built-in clothing entry gender for {} in {}", entry.identifier(), id);
                    continue;
                }

                JsonObject metadata = entry.metadata();
                String profession = metadata.has("profession") && !metadata.get("profession").isJsonNull() ? GsonHelper.getAsString(metadata, "profession", null) : null;
                boolean exclude = GsonHelper.getAsBoolean(metadata, "exclude", false);
                int temperature = GsonHelper.getAsInt(metadata, "temperature", 0);

                clothing.put(entry.identifier(), new Clothing(entry.identifier(), profession, temperature, exclude, entryGender));
            }
        });

        readBundledJsonFiles(BodySkinList.ID.getPath(), BODY_SKIN_FILES, (id, file) -> {
            Gender fileGender = BodySkinList.getGenderFromPath(id);
            SkinListJson.entries(id, file).forEach(entry -> {
                Gender entryGender = SkinListJson.resolveGender(fileGender, entry);
                float chance = GsonHelper.getAsFloat(entry.metadata(), "chance", 1.0f);
                bodySkins.put(entry.identifier(), new BodySkin(entry.identifier(), entryGender, chance));
            });
        });

        readBundledJsonFiles("skins/hair_styles", GENDERED_SKIN_FILES, (id, file) -> {
            Gender gender = BodySkinList.getGenderFromPath(id);
            HAIR_STYLE_FILE_CODEC.parse(JsonOps.INSTANCE, file)
                    .resultOrPartial(error -> MCA.LOGGER.warn("Invalid built-in hair style list {}: {}", id, error))
                    .ifPresent(entries -> entries.forEach((key, definition) -> hairStyles.put(key, definition.create(key, gender))));
        });

        readBundledJsonFiles("hair_layers", HAIR_LAYER_FILES, (id, file) ->
                SkinListJson.textureCollection(id, file).forEach(texture -> addLayeredHair(layeredHair, texture, getCategoryFromPath(id)))
        );

        return new Catalog(clothing, bodySkins, layeredHair, hairStyles);
    }

    private static void addLayeredHair(HashMap<String, LayeredHair> layeredHair, String texture, LayeredHair.Category category) {
        Identifier parsed = Identifier.tryParse(texture);
        if (parsed == null) {
            MCA.LOGGER.warn("Invalid built-in layered hair texture identifier {}", texture);
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
            Identifier id = MCA.locate(file);
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
