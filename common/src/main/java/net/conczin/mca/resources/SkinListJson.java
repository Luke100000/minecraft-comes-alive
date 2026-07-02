package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
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

    public static Map<ResourceLocation, List<String>> textureCollections(ResourceManager manager, String directory) {
        FileToIdConverter converter = FileToIdConverter.json(directory);
        Map<ResourceLocation, List<String>> collections = new LinkedHashMap<>();

        converter.listMatchingResourceStacks(manager).forEach((file, resources) -> {
            ResourceLocation id = converter.fileToId(file);
            List<String> textures = collections.computeIfAbsent(id, ignored -> new ArrayList<>());
            for (Resource resource : resources) {
                try (Reader reader = resource.openAsReader()) {
                    appendTextureCollection(id, JsonParser.parseReader(reader), textures);
                } catch (Exception exception) {
                    MCA.LOGGER.warn("Failed to read skin texture collection {} from {}", id, resource.sourcePackId(), exception);
                }
            }
        });

        return collections;
    }

    public static List<String> textureCollection(ResourceLocation id, JsonElement file) {
        List<String> textures = new ArrayList<>();
        appendTextureCollection(id, file, textures);
        return textures;
    }

    private static void appendTextureCollection(ResourceLocation id, JsonElement file, List<String> textures) {
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

    private static void appendTextureArray(ResourceLocation id, JsonElement textureArray, List<String> textures) {
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

    public record Entry(String identifier, JsonObject metadata) {
    }
}
