package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.BodySkin;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.HairStyle;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared JSON-to-catalog conversion for bundled and datapack skin resources. */
final class AppearanceCatalogLoader {
    private static final Codec<Map<String, HairStyle.Definition>> HAIR_STYLE_FILE_CODEC = Codec.unboundedMap(Codec.STRING, HairStyle.DEFINITION_CODEC);

    private AppearanceCatalogLoader() {
    }

    static void addClothing(Map<String, Clothing> clothing, ResourceLocation id, JsonElement file) {
        Gender fileGender = BodySkinList.getGenderFromPath(id);

        for (SkinListJson.Entry entry : SkinListJson.entries(id, file)) {
            Gender entryGender = SkinListJson.resolveGender(fileGender, entry);
            if (entryGender == Gender.UNASSIGNED) {
                MCA.LOGGER.warn("Invalid clothing entry gender for {} in {}", entry.identifier(), id);
                continue;
            }

            JsonObject metadata = entry.metadata();
            String profession = metadata.has("profession") && !metadata.get("profession").isJsonNull() ? GsonHelper.getAsString(metadata, "profession", null) : null;
            boolean exclude = GsonHelper.getAsBoolean(metadata, "exclude", false);
            int temperature = GsonHelper.getAsInt(metadata, "temperature", 0);
            clothing.put(entry.identifier(), new Clothing(entry.identifier(), profession, temperature, exclude, entryGender));
        }
    }

    static void addBodySkins(Map<String, BodySkin> bodySkins, ResourceLocation id, JsonElement file) {
        Gender fileGender = BodySkinList.getGenderFromPath(id);
        for (SkinListJson.Entry entry : SkinListJson.entries(id, file)) {
            Gender entryGender = SkinListJson.resolveGender(fileGender, entry);
            float chance = GsonHelper.getAsFloat(entry.metadata(), "chance", 1.0f);
            bodySkins.put(entry.identifier(), new BodySkin(entry.identifier(), entryGender, chance));
        }
    }

    static void addHairStyles(Map<String, HairStyle> hairStyles, ResourceLocation id, JsonElement file) {
        HAIR_STYLE_FILE_CODEC.parse(JsonOps.INSTANCE, file)
                .resultOrPartial(error -> MCA.LOGGER.warn("Invalid hair style list {}: {}", id, error))
                .ifPresent(entries -> {
                    Gender fileGender = BodySkinList.getGenderFromPath(id);
                    Gender fallbackGender = fileGender == Gender.UNASSIGNED ? Gender.NEUTRAL : fileGender;
                    entries.forEach((key, definition) -> hairStyles.put(key, definition.create(key, fallbackGender)));
                });
    }

    static void addLayeredHair(Map<String, LayeredHair> layeredHair, ResourceLocation id, JsonElement file) {
        addLayeredHair(layeredHair, id, SkinListJson.textureCollection(id, file));
    }

    static void addEyes(Map<ResourceLocation, EyeDefinition> eyes, ResourceLocation id, JsonElement file) {
        addEyes(eyes, id, SkinListJson.textureEntryCollection(id, file));
    }

    static void addEyes(Map<ResourceLocation, EyeDefinition> eyes, ResourceLocation id, List<SkinListJson.Entry> entries) {
        EyeFile file = eyeFile(id);
        for (SkinListJson.Entry entry : entries) {
            ResourceLocation texture;
            try {
                texture = ResourceLocation.parse(entry.identifier());
            } catch (ResourceLocationException exception) {
                MCA.LOGGER.warn("Invalid eye texture identifier {}", entry.identifier(), exception);
                continue;
            }
            if (!SkinVisualIds.isEyeTexturePath(texture)) {
                MCA.LOGGER.warn("Invalid eye texture path {}", entry.identifier());
                continue;
            }

            Gender gender = entry.metadata().has("gender")
                    ? SkinListJson.resolveGender(null, entry)
                    : file.gender() != Gender.UNASSIGNED
                    ? file.gender()
                    : SkinListJson.resolveGender(null, entry);
            if (gender == Gender.UNASSIGNED) {
                gender = Gender.NEUTRAL;
            }
            try {
                EyeDefinition definition = EyeDefinition.parse(texture, file.variant(), gender, entry.metadata());
                eyes.put(texture, definition);
            } catch (IllegalArgumentException exception) {
                MCA.LOGGER.warn("Invalid eye definition {}", texture, exception);
            }
        }
    }

    private static EyeFile eyeFile(ResourceLocation id) {
        String path = id.getPath().toLowerCase(Locale.ROOT);
        Gender topLevelGender = Gender.byName(path);
        if (topLevelGender == Gender.MALE || topLevelGender == Gender.FEMALE) {
            return new EyeFile(EyeStyles.DEFAULT_VARIANT, topLevelGender);
        }

        int separator = path.lastIndexOf('/');
        if (separator >= 0) {
            Gender gender = Gender.byName(path.substring(separator + 1));
            if (gender != Gender.UNASSIGNED) {
                return new EyeFile(path.substring(0, separator), gender);
            }
        }
        return new EyeFile(path, Gender.UNASSIGNED);
    }

    private record EyeFile(String variant, Gender gender) {
    }

    static void addLayeredHair(Map<String, LayeredHair> layeredHair, ResourceLocation id, List<String> textures) {
        LayeredHair.Category category = getLayeredHairCategory(id);
        textures.forEach(texture -> addLayeredHair(layeredHair, texture, category));
    }

    private static void addLayeredHair(Map<String, LayeredHair> layeredHair, String texture, LayeredHair.Category category) {
        ResourceLocation parsed;
        try {
            parsed = ResourceLocation.parse(texture);
        } catch (ResourceLocationException exception) {
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

    private static LayeredHair.Category getLayeredHairCategory(ResourceLocation id) {
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
