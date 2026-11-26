package net.conczin.mca.client.tts;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.conczin.mca.MCA;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;

public class RealtimeSpeechManager {
    public static final int CONNECT_TIMEOUT_MS = 5_000;
    public static final int READ_TIMEOUT_MS = 10_000;
    public static final long VOICE_FETCH_RETRY_COOLDOWN_MS = 30_000L;
    private static final int CHUNK_SIZE = 1024;

    public final String url;
    public volatile Map<String, VoiceInfo> voiceInfoMap;
    private final Object voiceFetchLock = new Object();
    private CompletableFuture<Map<String, VoiceInfo>> inFlightVoiceFetch;
    private volatile long nextVoiceFetchAllowedAt;

    public RealtimeSpeechManager(String url) {
        this.url = url;
    }

    public static void download(OutputStream output, String url, String payload, String key) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("xi-api-key", key);
            connection.setRequestProperty("player2-game-key", "minecraft-comes-alive-reborn");

            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                if (output == null) return;
                try (InputStream inputStream = connection.getInputStream()) {
                    byte[] buffer = new byte[CHUNK_SIZE];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                    }
                }
            } else {
                MCA.LOGGER.warn("Failed to get audio: {} - {}", connection.getResponseCode(), connection.getResponseMessage());
            }
        } catch (IOException | IllegalArgumentException e) {
            MCA.LOGGER.warn("Failed to download audio: {}", e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public void play(String text, String gender, String language, float pitch, float gene, Entity entity, boolean cacheable) {
        UUID entityId = entity.getUUID();
        long request = SpeechManager.INSTANCE.beginVoiceRequest(entityId);
        getVoicesAsync(language, gender).whenCompleteAsync((voices, throwable) -> {
            boolean playbackQueued = false;
            try {
                if (throwable != null || voices == null) {
                    return;
                }
                if (voices.isEmpty()) {
                    OnlineSpeechManager.languageNotSupported();
                    return;
                }

                int tone = Math.min(voices.size() - 1, (int) Math.floor(gene * voices.size()));
                playbackQueued = prepareAndQueuePlayback(text, pitch, entity, cacheable, voices.get(tone), request);
            } catch (RuntimeException e) {
                MCA.LOGGER.warn("Failed to prepare realtime villager voice", e);
            } finally {
                if (!playbackQueued) {
                    SpeechManager.INSTANCE.finishVoiceRequest(entityId, request);
                }
            }
        });
    }

    public void play(String phrase, float pitch, Entity entity, boolean cacheable, String voice) {
        UUID entityId = entity.getUUID();
        long request = SpeechManager.INSTANCE.beginVoiceRequest(entityId);
        CompletableFuture.runAsync(() -> {
            boolean playbackQueued = false;
            try {
                playbackQueued = prepareAndQueuePlayback(phrase, pitch, entity, cacheable, voice, request);
            } catch (RuntimeException e) {
                MCA.LOGGER.warn("Failed to prepare realtime villager voice", e);
            } finally {
                if (!playbackQueued) {
                    SpeechManager.INSTANCE.finishVoiceRequest(entityId, request);
                }
            }
        });
    }

    static String cacheKey(String voice, String text, boolean cacheable, long request) {
        return cacheable ? AudioCache.getHash(voice) + "/" + AudioCache.getHash(text) : "realtime/" + request;
    }

    private boolean prepareAndQueuePlayback(String phrase, float pitch, Entity entity, boolean cacheable, String voice, long request) {
        String text = cacheable ? OnlineSpeechManager.cleanPhrase(phrase) : phrase;
        String hash = cacheKey(voice, text, cacheable, request);
        if (AudioCache.get(hash, output -> downloadAudio(output, voice, text), cacheable)) {
            Identifier soundLocation = MCA.locate("tts_cache/" + hash);
            SpeechManager.INSTANCE.playSound(pitch, entity, soundLocation, request);
            return true;
        }
        return false;
    }

    public CompletableFuture<List<String>> getVoicesAsync(String language, String gender) {
        Map<String, VoiceInfo> loaded = this.voiceInfoMap;
        if (loaded != null) {
            return CompletableFuture.completedFuture(filterVoices(loaded, language, gender));
        }
        return ensureVoicesLoadedAsync().thenApply(voices -> filterVoices(voices, language, gender));
    }

    CompletableFuture<Map<String, VoiceInfo>> ensureVoicesLoadedAsync() {
        Map<String, VoiceInfo> loaded = this.voiceInfoMap;
        if (loaded != null) {
            return CompletableFuture.completedFuture(loaded);
        }
        if (System.currentTimeMillis() < nextVoiceFetchAllowedAt) {
            return CompletableFuture.completedFuture(null);
        }
        synchronized (voiceFetchLock) {
            loaded = this.voiceInfoMap;
            if (loaded != null) {
                return CompletableFuture.completedFuture(loaded);
            }
            if (System.currentTimeMillis() < nextVoiceFetchAllowedAt) {
                return CompletableFuture.completedFuture(null);
            }
            if (inFlightVoiceFetch != null) {
                return inFlightVoiceFetch;
            }
            CompletableFuture<Map<String, VoiceInfo>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    Map<String, VoiceInfo> fetched = fetchVoices(this.url + "v1/tts/piper/voices");
                    if (fetched != null) {
                        this.voiceInfoMap = fetched;
                    }
                    return fetched;
                } finally {
                    synchronized (voiceFetchLock) {
                        inFlightVoiceFetch = null;
                    }
                }
            });
            inFlightVoiceFetch = future;
            return future;
        }
    }

    static List<String> filterVoices(Map<String, VoiceInfo> voices, String language, String gender) {
        if (voices == null) {
            return null;
        }
        if (language == null || language.length() < 2 || gender == null) {
            return List.of();
        }
        String languagePrefix = language.substring(0, 2).toLowerCase(Locale.ROOT);
        return voices.values().stream()
                .filter(info -> info != null
                        && info.id != null
                        && info.language != null
                        && info.language.length() >= 2
                        && info.language.substring(0, 2).toLowerCase(Locale.ROOT).equals(languagePrefix)
                        && gender.equals(info.gender))
                .map(info -> info.id)
                .toList();
    }

    public Map<String, VoiceInfo> fetchVoices(String url) {
        if (System.currentTimeMillis() < nextVoiceFetchAllowedAt) {
            return null;
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept-Encoding", "gzip");
            connection.setRequestProperty("Accept", "application/json");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                MCA.LOGGER.warn("Failed to fetch voices: {} - {}", responseCode, connection.getResponseMessage());
                nextVoiceFetchAllowedAt = System.currentTimeMillis() + VOICE_FETCH_RETRY_COOLDOWN_MS;
                return null;
            }

            try (InputStream input = connection.getInputStream();
                 InputStreamReader reader = new InputStreamReader("gzip".equals(connection.getContentEncoding()) ? new GZIPInputStream(input) : input, StandardCharsets.UTF_8)) {

                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed == null || !parsed.isJsonObject()) {
                    nextVoiceFetchAllowedAt = System.currentTimeMillis() + VOICE_FETCH_RETRY_COOLDOWN_MS;
                    return null;
                }
                JsonObject root = parsed.getAsJsonObject();
                JsonArray voices = root.getAsJsonArray("voices");
                if (voices == null) {
                    nextVoiceFetchAllowedAt = System.currentTimeMillis() + VOICE_FETCH_RETRY_COOLDOWN_MS;
                    return null;
                }

                Map<String, VoiceInfo> result = new HashMap<>();
                for (JsonElement elem : voices) {
                    if (elem == null || !elem.isJsonObject()) {
                        continue;
                    }
                    JsonObject obj = elem.getAsJsonObject();
                    if (!obj.has("id") || !obj.has("language") || !obj.has("gender")) {
                        continue;
                    }
                    VoiceInfo info = new VoiceInfo(
                            obj.get("id").getAsString(),
                            obj.get("language").getAsString(),
                            obj.get("gender").getAsString()
                    );
                    result.put(info.id, info);
                }
                nextVoiceFetchAllowedAt = 0L;
                return Map.copyOf(result);
            }
        } catch (IOException | RuntimeException e) {
            MCA.LOGGER.warn("Failed to fetch voices: {}", e.getMessage());
            nextVoiceFetchAllowedAt = System.currentTimeMillis() + VOICE_FETCH_RETRY_COOLDOWN_MS;
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static String buildSpeakPayload(String voiceId, String text) {
        JsonObject payload = new JsonObject();
        payload.addProperty("text", text);
        payload.addProperty("voice", voiceId);
        return payload.toString();
    }

    public void downloadAudio(OutputStream output, String voiceId, String text) {
        String url = this.url + "v1/tts/piper/speak";
        download(output, url, buildSpeakPayload(voiceId, text), "");
    }

    public static class VoiceInfo {
        String id;
        String language;
        String gender;

        public VoiceInfo(String id, String language, String gender) {
            this.id = id;
            this.language = language;
            this.gender = gender;
        }
    }
}
