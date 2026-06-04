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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;


public class RealtimeSpeechManager {
    private static final int CHUNK_SIZE = 1024;

    public final String url;
    public Map<String, VoiceInfo> voiceInfoMap;

    public RealtimeSpeechManager(String url) {
        this.url = url;
    }

    public static void download(OutputStream output, String url, String payload, String key) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
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
        } catch (IOException e) {
            MCA.LOGGER.warn("Failed to download audio: {}", e.getMessage());
        }
    }

    public void play(String text, String gender, String language, float pitch, float gene, Entity entity, boolean cacheable) {
        List<String> voices = getVoices(language, gender);
        if (voices == null) {
            return;
        }
        if (voices.isEmpty()) {
            OnlineSpeechManager.languageNotSupported();
            return;
        }

        int tone = Math.min(voices.size() - 1, (int) Math.floor(gene * voices.size()));
        play(text, pitch, entity, cacheable, voices.get(tone));
    }

    public void play(String phrase, float pitch, Entity entity, boolean cacheable, String voice) {
        CompletableFuture.runAsync(() -> {
            String text = cacheable ? OnlineSpeechManager.cleanPhrase(phrase) : phrase;
            String hash = cacheable ? AudioCache.getHash(voice) + "/" + AudioCache.getHash(text) : "realtime";
            if (AudioCache.get(hash, output -> {
                downloadAudio(output, voice, text);
            }, cacheable)) {
                Identifier soundLocation = MCA.locate("tts_cache/" + hash);
                SpeechManager.INSTANCE.playSound(pitch, entity, soundLocation);
            }
        });
    }

    public List<String> getVoices(String language, String gender) {
        if (voiceInfoMap == null) {
            voiceInfoMap = fetchVoices(this.url + "v1/tts/piper/voices");
        }
        if (voiceInfoMap == null) {
            return null;
        }
        return voiceInfoMap.values().stream()
                .filter(info -> info.language.toLowerCase(Locale.ROOT).substring(0, 2).equals(language.substring(0, 2)) && info.gender.equals(gender))
                .map(info -> info.id)
                .toList();
    }

    public Map<String, VoiceInfo> fetchVoices(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept-Encoding", "gzip");
            connection.setRequestProperty("Accept", "application/json");

            try (InputStream input = connection.getInputStream();
                 InputStreamReader reader = new InputStreamReader("gzip".equals(connection.getContentEncoding()) ? new GZIPInputStream(input) : input, StandardCharsets.UTF_8)) {

                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray voices = root.getAsJsonArray("voices");

                Map<String, VoiceInfo> result = new HashMap<>();
                for (JsonElement elem : voices) {
                    JsonObject obj = elem.getAsJsonObject();
                    VoiceInfo info = new VoiceInfo(
                            obj.get("id").getAsString(),
                            obj.get("language").getAsString(),
                            obj.get("gender").getAsString()
                    );
                    result.put(info.id, info);
                }
                return result;
            }
        } catch (IOException e) {
            MCA.LOGGER.warn("Failed to fetch voices: {}", e.getMessage());
            return null;
        }
    }

    public void downloadAudio(OutputStream output, String voiceId, String text) {
        String url = this.url + "v1/tts/piper/speak";
        String payload = String.format(
                "{\"text\": \"%s\", \"voice\": \"%s\"}",
                text, voiceId
        );
        download(output, url, payload, "");
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
