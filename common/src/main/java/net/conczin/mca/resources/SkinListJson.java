package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonParser;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.MCA;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public final class SkinListJson {
    private SkinListJson() {
    }

    public static List<Entry> entries(ResourceLocation fileId, JsonElement file) {
        if (!file.isJsonArray()) {
            MCA.LOGGER.warn("Invalid skin list {}, expected an array", fileId);
            return List.of();
        }

        List<Entry> entries = new ArrayList<>();
        file.getAsJsonArray().forEach(element -> {
            if (element.isJsonPrimitive()) {
                entries.add(new Entry(element.getAsString(), new JsonObject()));
                return;
            }
            if (!element.isJsonObject()) {
                MCA.LOGGER.warn("Invalid skin list entry in {}, expected a string or object", fileId);
                return;
            }

            JsonObject object = element.getAsJsonObject();
            if (!object.has("id")) {
                MCA.LOGGER.warn("Invalid skin list entry in {}, missing id", fileId);
                return;
            }

            JsonObject metadata = object.deepCopy();
            String identifier = GsonHelper.getAsString(metadata, "id");
            metadata.remove("id");
            entries.add(new Entry(identifier, metadata));
        });
        return entries;
    }

    /**
     * Resolves gender for both the old split-file layout:
     *   skins/body/female.json + skins/body/male.json
     * and the merged layout:
     *   skins/body/skins.json with per-entry gender metadata.
     *
     * This also keeps old numeric gender metadata working for list types that
     * predate the newer string codecs.
     */
    public static Gender resolveGender(Gender fileGender, Entry entry) {
        Gender metadataGender = getGenderFromMetadata(entry.metadata());
        if (metadataGender != Gender.UNASSIGNED) {
            return metadataGender;
        }

        Gender inferred = inferGenderFromIdentifier(entry.identifier());
        if (inferred != Gender.UNASSIGNED) {
            return inferred;
        }

        return fileGender == null ? Gender.UNASSIGNED : fileGender;
    }

    private static Gender getGenderFromMetadata(JsonObject metadata) {
        JsonElement element = metadata.get("gender");
        if (element == null || !element.isJsonPrimitive()) {
            return Gender.UNASSIGNED;
        }

        JsonPrimitive primitive = element.getAsJsonPrimitive();
        return primitive.isNumber() ? Gender.byId(primitive.getAsInt()) : Gender.byName(primitive.getAsString());
    }

    private static Gender inferGenderFromIdentifier(String identifier) {
        if (identifier.contains("/female/") || identifier.contains("/female_")) {
            return Gender.FEMALE;
        }
        if (identifier.contains("/male/") || identifier.contains("/male_")) {
            return Gender.MALE;
        }
        return Gender.UNASSIGNED;
    }

    public static Map<ResourceLocation, List<String>> textureCollections(ResourceManager manager, String directory) {
        return textureCollections(manager, directory, SkinListJson::stringEntries);
    }

    public static Map<ResourceLocation, List<Entry>> textureEntryCollections(ResourceManager manager, String directory) {
        return textureCollections(manager, directory, SkinListJson::entries);
    }

    private static <T> Map<ResourceLocation, List<T>> textureCollections(
            ResourceManager manager,
            String directory,
            BiFunction<ResourceLocation, JsonElement, List<T>> entryReader
    ) {
        FileToIdConverter converter = FileToIdConverter.json(directory);
        Map<ResourceLocation, List<T>> collections = new LinkedHashMap<>();

        converter.listMatchingResourceStacks(manager).forEach((file, resources) -> {
            ResourceLocation id = converter.fileToId(file);
            List<T> textures = collections.computeIfAbsent(id, ignored -> new ArrayList<>());
            for (Resource resource : resources) {
                try (Reader reader = resource.openAsReader()) {
                    appendTextureCollection(id, JsonParser.parseReader(reader), textures, entryReader);
                } catch (Exception exception) {
                    MCA.LOGGER.warn("Failed to read skin texture collection {} from {}", id, resource.sourcePackId(), exception);
                }
            }
        });

        return collections;
    }

    public static List<String> textureCollection(ResourceLocation id, JsonElement file) {
        List<String> textures = new ArrayList<>();
        appendTextureCollection(id, file, textures, SkinListJson::stringEntries);
        return textures;
    }

    private static <T> void appendTextureCollection(
            ResourceLocation id,
            JsonElement file,
            List<T> textures,
            BiFunction<ResourceLocation, JsonElement, List<T>> entryReader
    ) {
        if (file.isJsonArray()) {
            textures.addAll(entryReader.apply(id, file));
            return;
        }
        if (!file.isJsonObject()) {
            throw new JsonParseException("Expected object or array");
        }

        JsonObject object = file.getAsJsonObject();
        if (GsonHelper.getAsBoolean(object, "replace", false)) {
            textures.clear();
        }
        JsonElement textureArray = object.get("textures");
        if (textureArray == null) {
            MCA.LOGGER.warn("Invalid skin texture collection {}, missing textures", id);
            return;
        }
        textures.addAll(entryReader.apply(id, textureArray));
    }

    private static List<String> stringEntries(ResourceLocation id, JsonElement textureArray) {
        if (!textureArray.isJsonArray()) {
            throw new JsonParseException("Expected textures array");
        }
        List<String> textures = new ArrayList<>();
        textureArray.getAsJsonArray().forEach(element -> {
            if (!element.isJsonPrimitive()) {
                MCA.LOGGER.warn("Invalid skin texture collection entry in {}, expected a string", id);
                return;
            }
            textures.add(element.getAsString());
        });
        return textures;
    }

    public record Entry(String identifier, JsonObject metadata) {
    }
}
