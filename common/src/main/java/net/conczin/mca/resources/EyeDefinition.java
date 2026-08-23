package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

public record EyeDefinition(
        ResourceLocation id,
        Gender gender,
        Map<Integer, Tones> toneOverrides
) {
    public EyeDefinition {
        toneOverrides = Map.copyOf(toneOverrides);
    }

    public static EyeDefinition parse(ResourceLocation id, Gender gender, JsonObject metadata) {
        return new EyeDefinition(id, gender, parseToneOverrides(metadata.get("tone_overrides")));
    }

    public Tones tones(int selectedArgb) {
        Tones override = toneOverrides.get(selectedArgb & 0x00FFFFFF);
        if (override != null) {
            return override;
        }

        int red = (selectedArgb >>> 16) & 0xFF;
        int green = (selectedArgb >>> 8) & 0xFF;
        int blue = selectedArgb & 0xFF;
        return new Tones(
                color(shadow(red), shadow(green), shadow(blue)),
                color(red, green, blue),
                color(highlight(red), highlight(green), highlight(blue))
        );
    }

    public static int parseColor(String value) {
        if (value == null || !value.matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("Expected color in #RRGGBB format: " + value);
        }
        return 0xFF000000 | Integer.parseInt(value.substring(1), 16);
    }

    private static Map<Integer, Tones> parseToneOverrides(JsonElement element) {
        if (element == null) {
            return Map.of();
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Eye tone_overrides must be a JSON object keyed by #RRGGBB");
        }

        Map<Integer, Tones> overrides = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            int key = parseColor(entry.getKey()) & 0x00FFFFFF;
            if (!entry.getValue().isJsonObject()) {
                throw new IllegalArgumentException("Eye tone override for " + entry.getKey() + " must be a JSON object");
            }
            JsonObject object = entry.getValue().getAsJsonObject();
            overrides.put(key, new Tones(
                    parseColor(requiredString(object, "shadow")),
                    parseColor(requiredString(object, "primary")),
                    parseColor(requiredString(object, "highlight"))
            ));
        }
        return overrides;
    }

    private static String requiredString(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Eye definition field '" + name + "' must be a string");
        }
        return value.getAsString();
    }

    private static int shadow(int channel) {
        return (channel * 128) / 255;
    }

    private static int highlight(int channel) {
        return (channel + 255) / 2;
    }

    private static int color(int red, int green, int blue) {
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    public record Tones(int shadow, int primary, int highlight) {
    }
}
