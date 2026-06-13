package net.conczin.mca.client.tts;

import net.conczin.mca.MCA;
import net.conczin.mca.client.tts.resources.Player2LanguageMap;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class Player2SpeechManager extends RealtimeSpeechManager {
    public Player2SpeechManager(String url) {
        super(url);
    }

    public void play(String text, String gender, String language, float pitch, float gene) {
        CompletableFuture.runAsync(() -> {
            List<String> voices = getVoices(language, gender);
            if (voices == null) {
                return;
            }
            if (voices.isEmpty()) {
                OnlineSpeechManager.languageNotSupported();
                return;
            }

            int tone = Math.min(voices.size() - 1, (int) Math.floor(gene * voices.size()));
            playInApp(text, pitch, voices.get(tone));
        });
    }

    public void playInApp(String text, float pitch, String voiceId) {
        String url = this.url + "v1/tts/speak";
        String payload = String.format(
                "{\"play_in_app\": true, \"speed\": %s, \"text\": \"%s\", \"voice_ids\": [\"%s\"]}",
                pitch, text, voiceId
        );
        download(null, url, payload, "");
    }

    public List<String> getVoices(String languageCode, String gender) {
        if (voiceInfoMap == null) {
            voiceInfoMap = fetchVoices(this.url + "v1/tts/voices");
        }
        if (voiceInfoMap == null) {
            return Collections.emptyList();
        }
        String language = Player2LanguageMap.LANGUAGE_MAP.getOrDefault(languageCode, languageCode);
        return voiceInfoMap.values().stream()
                .filter(info -> info.language.equals(language) && info.gender.equals(gender))
                .map(info -> info.id)
                .toList();
    }

    public boolean checkHealth() {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url + "v1/health").toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("player2-game-key", "minecraft-comes-alive-reborn");

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                return true;
            } else {
                MCA.LOGGER.warn("Failed to check player2 health: {} - {}", connection.getResponseCode(), connection.getResponseMessage());
            }
        } catch (IOException | IllegalArgumentException e) {
            MCA.LOGGER.debug("Failed to check player2 health: {}", e.getMessage());
        }
        return false;
    }
}
