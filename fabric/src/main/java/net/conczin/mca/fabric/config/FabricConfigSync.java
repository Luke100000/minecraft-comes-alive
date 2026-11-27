package net.conczin.mca.fabric.config;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.config.ConfigSchema;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
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
    private static Runnable pendingClientLogin;

    private FabricConfigSync() {}

    /** FCP receives SERVER TOML in the configuration phase, before MCA's play packets. */
    static boolean requiresMcaSnapshot(boolean serverHasFcp, boolean clientHasFcp) {
        return !serverHasFcp || !clientHasFcp;
    }

    /** Only FCP-equipped clients advertise this play receiver; no marker is ever sent. */
    public record FcpClientCapability() implements CustomPacketPayload {
        public static final Type<FcpClientCapability> TYPE = new Type<>(MCA.locate("fcp_client_capability"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FcpClientCapability> CODEC = StreamCodec.of(
                (buf, marker) -> {}, buf -> new FcpClientCapability());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** FCP's CONFIGURATION-phase native sync has completed before the play join. */
    public record NativeSyncReady() implements CustomPacketPayload {
        public static final Type<NativeSyncReady> TYPE = new Type<>(MCA.locate("fcp_native_sync_ready"));
        public static final StreamCodec<RegistryFriendlyByteBuf, NativeSyncReady> CODEC = StreamCodec.of(
                (buf, marker) -> {}, buf -> new NativeSyncReady());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

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
        PayloadTypeRegistry.playS2C().register(FcpClientCapability.TYPE, FcpClientCapability.CODEC);
        PayloadTypeRegistry.playS2C().register(NativeSyncReady.TYPE, NativeSyncReady.CODEC);
    }

    public static void initializeClient() {
        ClientPlayConnectionEvents.INIT.register((handler, client) -> {
            pendingClientLogin = null;
            if (client.getSingleplayerServer() == null) Config.SERVER_SPEC.beginRemoteSync();
        });
        ClientPlayNetworking.registerGlobalReceiver(NativeSyncReady.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    Config.SERVER_SPEC.completeNativeRemoteSync();
                    finishClientSync();
                }));
        if (FabricConfigBootstrap.hasFcp()) {
            // canSend(TYPE) is used by the server to select FCP's already-completed native sync.
            ClientPlayNetworking.registerGlobalReceiver(FcpClientCapability.TYPE, (payload, context) -> {});
        }
        ClientPlayNetworking.registerGlobalReceiver(Snapshot.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    try {
                        applyRemote(payload);
                        finishClientSync();
                    }
                    catch (Exception ex) {
                        MCA.LOGGER.warn("Rejected invalid MCA SERVER configuration: {}", ex.toString());
                        if (context.client().getConnection() != null) {
                            context.client().getConnection().getConnection().disconnect(
                                    Component.literal("Invalid or incompatible MCA server configuration"));
                        }
                    }
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onClientDisconnect());
    }

    /** Called from the client JOIN event, before play sync is necessarily complete. */
    public static void onClientJoin(Runnable afterServerConfig) {
        if (Config.SERVER_SPEC.isAwaitingRemote()) pendingClientLogin = afterServerConfig;
        else afterServerConfig.run();
    }

    static void finishClientSync() {
        if (Config.SERVER_SPEC.isAwaitingRemote()) return;
        Runnable task = pendingClientLogin;
        pendingClientLogin = null;
        if (task != null) task.run();
    }

    static void onClientDisconnect() {
        pendingClientLogin = null;
        Config.SERVER_SPEC.clearRemote();
    }

    public static void send(ServerPlayer player) {
        if (!requiresMcaSnapshot(FabricConfigBootstrap.hasFcp(),
                ServerPlayNetworking.canSend(player, FcpClientCapability.TYPE))) {
            if (!ServerPlayNetworking.canSend(player, NativeSyncReady.TYPE)) {
                player.connection.disconnect(Component.literal("Missing MCA native config sync acknowledgement support"));
            } else ServerPlayNetworking.send(player, new NativeSyncReady());
            return;
        }
        if (!ServerPlayNetworking.canSend(player, Snapshot.TYPE)) {
            player.connection.disconnect(Component.literal("This server requires MCA SERVER config sync"));
            return;
        }
        ServerPlayNetworking.send(player, snapshot());
    }

    /** FCP only syncs SERVER files on login; after a live server reload reconnect native peers. */
    public static void onFcpServerReload(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, FcpClientCapability.TYPE)) {
                player.connection.disconnect(Component.literal("MCA server configuration changed. Reconnect to synchronize."));
            } else {
                send(player);
            }
        }
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
        Config.SERVER_SPEC.applyRemote(readSnapshot(packet.json()));
    }

    private static Map<String, Object> readSnapshot(String json) {
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            boolean versionRead = false;
            Map<String, Object> values = null;
            reader.beginObject();
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "protocolVersion" -> {
                        if (versionRead || reader.peek() != JsonToken.NUMBER
                                || !new BigDecimal(reader.nextString()).toString().equals(Integer.toString(PROTOCOL_VERSION)))
                            throw new IllegalArgumentException("Duplicate or unknown MCA SERVER config protocol");
                        versionRead = true;
                    }
                    case "values" -> {
                        if (values != null) throw new IllegalArgumentException("Duplicate SERVER settings");
                        values = readSettings(reader);
                    }
                    default -> throw new IllegalArgumentException("Unknown MCA SERVER snapshot field");
                }
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing JSON content");
            if (!versionRead || values == null) throw new IllegalArgumentException("Incomplete MCA SERVER snapshot");
            return values;
        } catch (IOException | IllegalStateException | ArithmeticException ex) {
            throw new IllegalArgumentException("Malformed MCA SERVER snapshot", ex);
        }
    }

    private static Map<String, Object> readSettings(JsonReader reader) throws IOException {
        Map<String, Object> values = new LinkedHashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            ConfigSchema.Entry<?> entry = Config.SERVER_SPEC.entry(name);
            if (entry == null || values.containsKey(name))
                throw new IllegalArgumentException("Unknown or duplicate SERVER setting: " + name);
            values.put(name, readValue(reader, entry.defaultValue()));
        }
        reader.endObject();
        return values;
    }

    private static Object readValue(JsonReader reader, Object original) throws IOException {
        JsonToken token = reader.peek();
        if (original instanceof List<?> && token == JsonToken.BEGIN_ARRAY) {
            List<String> list = new ArrayList<>();
            reader.beginArray();
            while (reader.hasNext()) {
                if (reader.peek() != JsonToken.STRING)
                    throw new IllegalArgumentException("Expected string list entry");
                list.add(reader.nextString());
            }
            reader.endArray();
            return list;
        }
        if (original instanceof Boolean && token == JsonToken.BOOLEAN) return reader.nextBoolean();
        if (original instanceof String && token == JsonToken.STRING) return reader.nextString();
        if (original instanceof Integer && token == JsonToken.NUMBER)
            return new BigDecimal(reader.nextString()).intValueExact();
        if (original instanceof Double && token == JsonToken.NUMBER) {
            double number = new BigDecimal(reader.nextString()).doubleValue();
            if (Double.isFinite(number)) return number;
        }
        throw new IllegalArgumentException("Mismatched SERVER value type");
    }
}
