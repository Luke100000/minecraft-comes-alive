package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonObject;
import net.conczin.mca.MCA;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.StrictJsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SkinListJson {
    private SkinListJson() {
    }

    static List<Entry> entries(Identifier fileId, JsonElement file) {
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

    static Map<Identifier, List<String>> textureCollections(ResourceManager manager, String directory) {
        FileToIdConverter converter = FileToIdConverter.json(directory);
        Map<Identifier, List<String>> collections = new LinkedHashMap<>();

        converter.listMatchingResourceStacks(manager).forEach((file, resources) -> {
            Identifier id = converter.fileToId(file);
            List<String> textures = collections.computeIfAbsent(id, ignored -> new ArrayList<>());
            for (Resource resource : resources) {
                try (var reader = resource.openAsReader()) {
                    appendTextureCollection(id, StrictJsonParser.parse(reader), textures);
                } catch (Exception exception) {
                    MCA.LOGGER.warn("Failed to read skin texture collection {} from {}", id, resource.sourcePackId(), exception);
                }
            }
        });

        return collections;
    }

    static List<String> textureCollection(Identifier id, JsonElement file) {
        List<String> textures = new ArrayList<>();
        try {
            appendTextureCollection(id, file, textures);
        } catch (Exception exception) {
            MCA.LOGGER.warn("Failed to parse built-in skin list {}", id, exception);
        }
        return textures;
    }

    private static void appendTextureCollection(Identifier id, JsonElement file, List<String> textures) {
        if (file.isJsonArray()) {
            appendTextureArray(id, file, textures);
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
        appendTextureArray(id, textureArray, textures);
    }

    private static void appendTextureArray(Identifier id, JsonElement textureArray, List<String> textures) {
        if (!textureArray.isJsonArray()) {
            throw new JsonParseException("Expected textures array");
        }
        textureArray.getAsJsonArray().forEach(element -> {
            if (!element.isJsonPrimitive()) {
                MCA.LOGGER.warn("Invalid skin texture collection entry in {}, expected a string", id);
                return;
            }
            textures.add(element.getAsString());
        });
    }

    record Entry(String identifier, JsonObject metadata) {
    }
}
