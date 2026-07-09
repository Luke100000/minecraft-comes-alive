package net.mca.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.mca.MCA;
import net.mca.entity.ai.relationship.Gender;
import net.mca.resources.data.skin.BodySkin;
import net.mca.resources.data.skin.Clothing;
import net.mca.resources.data.skin.HairStyle;
import net.mca.resources.data.skin.LayeredHair;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.minecraft.util.JsonHelper;

import java.util.List;
import java.util.Map;

/** Shared JSON-to-catalog conversion for bundled and datapack skin resources. */
final class SkinCatalogLoader {
    private static final Codec<Map<String, HairStyle.Definition>> HAIR_STYLE_FILE_CODEC = Codec.unboundedMap(Codec.STRING, HairStyle.DEFINITION_CODEC);

    private SkinCatalogLoader() {
    }

    static void addClothing(Map<String, Clothing> clothing, Identifier id, JsonElement file) {
        Gender fileGender = BodySkinList.getGenderFromPath(id);
        for (SkinListJson.Entry entry : SkinListJson.entries(id, file)) {
            Gender entryGender = SkinListJson.resolveGender(fileGender, entry);
            if (entryGender == Gender.UNASSIGNED) {
                MCA.LOGGER.warn("Invalid clothing entry gender for {} in {}", entry.identifier(), id);
                continue;
            }
            JsonObject metadata = entry.metadata();
            String profession = metadata.has("profession") && !metadata.get("profession").isJsonNull() ? JsonHelper.getString(metadata, "profession", null) : null;
            boolean exclude = JsonHelper.getBoolean(metadata, "exclude", false);
            int temperature = JsonHelper.getInt(metadata, "temperature", 0);
            clothing.put(entry.identifier(), new Clothing(entry.identifier(), profession, temperature, exclude, entryGender));
        }
    }

    static void addBodySkins(Map<String, BodySkin> bodySkins, Identifier id, JsonElement file) {
        Gender fileGender = BodySkinList.getGenderFromPath(id);
        for (SkinListJson.Entry entry : SkinListJson.entries(id, file)) {
            Gender entryGender = SkinListJson.resolveGender(fileGender, entry);
            float chance = JsonHelper.getFloat(entry.metadata(), "chance", 1.0f);
            bodySkins.put(entry.identifier(), new BodySkin(entry.identifier(), entryGender, chance));
        }
    }

    static void addHairStyles(Map<String, HairStyle> hairStyles, Identifier id, JsonElement file) {
        HAIR_STYLE_FILE_CODEC.parse(JsonOps.INSTANCE, file)
                .resultOrPartial(error -> MCA.LOGGER.warn("Invalid hair style list {}: {}", id, error))
                .ifPresent(entries -> {
                    Gender fileGender = BodySkinList.getGenderFromPath(id);
                    Gender fallbackGender = fileGender == Gender.UNASSIGNED ? Gender.NEUTRAL : fileGender;
                    entries.forEach((key, definition) -> hairStyles.put(key, definition.create(key, fallbackGender)));
                });
    }

    static void addLayeredHair(Map<String, LayeredHair> layeredHair, Identifier id, JsonElement file) {
        addLayeredHair(layeredHair, id, SkinListJson.textureCollection(id, file));
    }

    static void addLayeredHair(Map<String, LayeredHair> layeredHair, Identifier id, List<String> textures) {
        LayeredHair.Category category = getLayeredHairCategory(id);
        textures.forEach(texture -> addLayeredHair(layeredHair, texture, category));
    }

    private static void addLayeredHair(Map<String, LayeredHair> layeredHair, String texture, LayeredHair.Category category) {
        Identifier parsed;
        try {
            parsed = new Identifier(texture);
        } catch (InvalidIdentifierException exception) {
            MCA.LOGGER.warn("Invalid layered hair texture identifier {}", texture, exception);
            return;
        }
        if (!parsed.getPath().startsWith("skins/layered_hair/")) {
            MCA.LOGGER.warn("Invalid layered hair texture path {}", texture);
            return;
        }
        LayeredHair entry = new LayeredHair(texture, Gender.NEUTRAL, category, 1.0F);
        layeredHair.put(LayeredHairList.key(entry.getIdentifier(), entry.getGender(), entry.getCategory()), entry);
    }

    private static LayeredHair.Category getLayeredHairCategory(Identifier id) {
        String[] parts = id.getPath().split("/");
        for (String part : parts) {
            LayeredHair.Category category = LayeredHair.Category.byNameOrNull(part);
            if (category != null) {
                return category;
            }
        }
        return LayeredHair.Category.BASE;
    }
}
