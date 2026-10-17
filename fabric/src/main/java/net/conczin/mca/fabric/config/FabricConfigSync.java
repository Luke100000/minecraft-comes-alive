package net.conczin.mca.fabric.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.config.ConfigSchema;
import net.conczin.mca.config.ConfigScope;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fabric-specific, validated SERVER authority. No COMMON/CLIENT values are sent. */
public final class FabricConfigSync {
    public static final int MAX_BYTES = 1_048_576;
    public static final int PROTOCOL_VERSION = 1;

    private FabricConfigSync() {}

    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(MCA.locate("server_config_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    byte[] utf8 = packet.json().getBytes(StandardCharsets.UTF_8);
                    if (utf8.length > MAX_BYTES) throw new IllegalArgumentException("MCA SERVER snapshot too large");
                    buf.writeVarInt(utf8.length);
                    buf.writeBytes(utf8);
                },
                buf -> {
                    int length = buf.readVarInt();
                    if (length < 0 || length > MAX_BYTES || length > buf.readableBytes())
                        throw new IllegalArgumentException("Invalid MCA SERVER snapshot size");
                    byte[] bytes = new byte[length];
                    buf.readBytes(bytes);
                    try {
                        return new Snapshot(StandardCharsets.UTF_8.newDecoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(bytes)).toString());
                    } catch (CharacterCodingException ex) {
                        throw new IllegalArgumentException("Malformed UTF-8 MCA SERVER configuration", ex);
                    }
                });

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void initializeCommon() {
        PayloadTypeRegistry.playS2C().register(Snapshot.TYPE, Snapshot.CODEC);
    }

    public static void initializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(Snapshot.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    try { applyRemote(payload); }
                    catch (Exception ex) {
                        MCA.LOGGER.warn("Rejected invalid MCA SERVER configuration: {}", ex.toString());
                        if (context.client().getConnection() != null) {
                            context.client().getConnection().getConnection().disconnect(
                                    Component.literal("Invalid or incompatible MCA server configuration"));
                        }
                    }
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Config.SERVER_SPEC.clearRemote());
    }

    public static void send(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, Snapshot.TYPE)) {
            player.connection.disconnect(Component.literal("This server requires MCA SERVER config sync"));
            return;
        }
        ServerPlayNetworking.send(player, snapshot());
    }

    public static Snapshot snapshot() {
        JsonObject entries = new JsonObject();
        for (ConfigSchema.Entry<?> entry : Config.SERVER_SPEC.entries()) {
            Object value = entry.value().get();
            if (value instanceof Boolean bool) entries.addProperty(entry.name(), bool);
            else if (value instanceof Number number) entries.addProperty(entry.name(), number);
            else if (value instanceof String string) entries.addProperty(entry.name(), string);
            else if (value instanceof List<?> list) {
                var array = new com.google.gson.JsonArray();
                for (Object element : list) array.add((String) element);
                entries.add(entry.name(), array);
            } else throw new IllegalStateException("Unsupported SERVER setting: " + entry.name());
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("protocolVersion", PROTOCOL_VERSION);
        payload.add("values", entries);
        Snapshot result = new Snapshot(payload.toString());
        if (result.json().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalStateException("MCA SERVER configuration is too large to synchronize");
        return result;
    }

    public static void applyRemote(Snapshot packet) {
        if (packet.json().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("Oversized SERVER configuration");
        Map<String, JsonElement> raw = readStrict(packet.json());
        JsonElement version = raw.get("protocolVersion");
        if (raw.size() != 2 || version == null || !version.isJsonPrimitive()
                || !version.getAsJsonPrimitive().isNumber()
                || !version.getAsString().equals(Integer.toString(PROTOCOL_VERSION))) {
            throw new IllegalArgumentException("Unknown MCA SERVER config protocol");
        }
        JsonElement valuesElement = raw.get("values");
        if (valuesElement == null || !valuesElement.isJsonObject())
            throw new IllegalArgumentException("Missing MCA SERVER settings");
        JsonObject object = valuesElement.getAsJsonObject();
        if (!object.keySet().equals(Config.SERVER_SPEC.paths()))
            throw new IllegalArgumentException("Incomplete/unknown MCA SERVER setting paths");
        Map<String, Object> candidate = new LinkedHashMap<>();
        for (ConfigSchema.Entry<?> entry : Config.SERVER_SPEC.entries()) {
            Object value = decode(entry.defaultValue(), object.get(entry.name()));
            if (!entry.value().accepts(value))
                throw new IllegalArgumentException("Invalid SERVER setting: " + entry.name());
            candidate.put(entry.name(), value);
        }
        Config.SERVER_SPEC.applyRemote(candidate);
    }

    private static Map<String, JsonElement> readStrict(String json) {
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setLenient(false);
            Map<String, JsonElement> parsed = new LinkedHashMap<>();
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (parsed.containsKey(key)) throw new IllegalArgumentException("Duplicate top-level key");
                if ("values".equals(key)) {
                    // Detect duplicates before conversion to a JsonObject (which would overwrite them).
                    JsonObject entries = new JsonObject();
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String name = reader.nextName();
                        if (entries.has(name)) throw new IllegalArgumentException("Duplicate SERVER config key");
                        entries.add(name, readValue(reader));
                    }
                    reader.endObject();
                    parsed.put(key, entries);
                } else parsed.put(key, readValue(reader));
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing JSON content");
            return parsed;
        } catch (Exception ex) { throw new IllegalArgumentException("Malformed MCA SERVER snapshot", ex); }
    }

    /** Read one value without Gson's object-map duplicate-key overwrites. */
    private static JsonElement readValue(JsonReader reader) throws java.io.IOException {
        return switch (reader.peek()) {
            case STRING -> new JsonPrimitive(reader.nextString());
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BEGIN_ARRAY -> {
                com.google.gson.JsonArray array = new com.google.gson.JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(readValue(reader));
                reader.endArray();
                yield array;
            }
            case NULL -> { reader.nextNull(); yield com.google.gson.JsonNull.INSTANCE; }
            default -> throw new IllegalArgumentException("Unexpected nested SERVER config value");
        };
    }

    private static Object decode(Object original, JsonElement element) {
        if (element == null || element.isJsonNull()) throw new IllegalArgumentException("Missing SERVER value");
        if (original instanceof List<?>) {
            if (!element.isJsonArray()) throw new IllegalArgumentException("Expected string list");
            List<String> list = new ArrayList<>();
            for (JsonElement value : element.getAsJsonArray()) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                    throw new IllegalArgumentException("Expected string list entry");
                list.add(value.getAsString());
            }
            return List.copyOf(list);
        }
        if (!element.isJsonPrimitive()) throw new IllegalArgumentException("Invalid SERVER value type");
        JsonPrimitive p = element.getAsJsonPrimitive();
        if (original instanceof Boolean && p.isBoolean()) return p.getAsBoolean();
        if (original instanceof String && p.isString()) return p.getAsString();
        if (original instanceof Integer && p.isNumber())
            return new BigDecimal(p.getAsString()).intValueExact();
        if (original instanceof Double && p.isNumber()) {
            double number = p.getAsDouble();
            if (Double.isFinite(number)) return number;
        }
        throw new IllegalArgumentException("Mismatched SERVER value type");
    }
}
