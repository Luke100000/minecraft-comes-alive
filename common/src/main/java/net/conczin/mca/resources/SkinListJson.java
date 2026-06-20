package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.MCA;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

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

    record Entry(String identifier, JsonObject metadata) {
    }
}
