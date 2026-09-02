package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.buffer.ByteBuf;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record EyeDefinition(
        ResourceLocation id,
        String variant,
        Gender gender,
        float chance,
        boolean fixedColor,
        Map<Integer, Tones> toneOverrides
) {
    public static final StreamCodec<ByteBuf, EyeDefinition> STREAM_CODEC = StreamCodec.of(
            (out, value) -> {
                ResourceLocation.STREAM_CODEC.encode(out, value.id());
                ByteBufCodecs.STRING_UTF8.encode(out, value.variant());
                SkinListEntry.GENDER_STREAM_CODEC.encode(out, value.gender());
                ByteBufCodecs.FLOAT.encode(out, value.chance());
                ByteBufCodecs.BOOL.encode(out, value.fixedColor());
                ByteBufCodecs.VAR_INT.encode(out, value.toneOverrides().size());
                value.toneOverrides().forEach((color, tones) -> {
                    out.writeInt(color);
                    out.writeInt(tones.shadow());
                    out.writeInt(tones.primary());
                    out.writeInt(tones.highlight());
                });
            },
            in -> {
                ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(in);
                String variant = ByteBufCodecs.STRING_UTF8.decode(in);
                Gender gender = SkinListEntry.GENDER_STREAM_CODEC.decode(in);
                float chance = ByteBufCodecs.FLOAT.decode(in);
                boolean fixedColor = ByteBufCodecs.BOOL.decode(in);
                int overrideCount = ByteBufCodecs.VAR_INT.decode(in);
                Map<Integer, Tones> overrides = new LinkedHashMap<>();
                for (int i = 0; i < overrideCount; i++) {
                    overrides.put(in.readInt(), new Tones(in.readInt(), in.readInt(), in.readInt()));
                }
                return new EyeDefinition(id, variant, gender, chance, fixedColor, overrides);
            }
    );

    public EyeDefinition {
        id = Objects.requireNonNull(id);
        variant = Objects.requireNonNull(variant).toLowerCase(Locale.ROOT);
        gender = gender == null || gender == Gender.UNASSIGNED ? Gender.NEUTRAL : gender;
        chance = chance <= 0.0F ? 1.0F : chance;
        toneOverrides = Map.copyOf(toneOverrides);
    }

    public static EyeDefinition parse(ResourceLocation id, String variant, Gender gender, JsonObject metadata) {
        return new EyeDefinition(
                id,
                variant,
                gender,
                GsonHelper.getAsFloat(metadata, "chance", 1.0F),
                optionalBoolean(metadata, "fixed_color", false),
                parseToneOverrides(metadata.get("tone_overrides"))
        );
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

    private static boolean optionalBoolean(JsonObject object, String name, boolean fallback) {
        JsonElement value = object.get(name);
        if (value == null) {
            return fallback;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Eye definition field '" + name + "' must be a boolean");
        }
        return value.getAsBoolean();
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
