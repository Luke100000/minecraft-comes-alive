package net.conczin.mca.client.tts;

import com.google.gson.JsonObject;
import net.conczin.mca.Config;
import net.minecraft.world.entity.Entity;

import java.io.OutputStream;
import java.util.List;

public class ElevenlabsSpeechManager extends RealtimeSpeechManager {
    public ElevenlabsSpeechManager() {
        super("https://api.elevenlabs.io/v1/text-to-speech/");
    }

    public void play(String text, String gender, float pitch, float gene, Entity entity, boolean cacheable) {
        List<String> voices = gender.equals("male") ? Config.getInstance().elevenlabsMaleVoices : Config.getInstance().elevenlabsFemaleVoices;
        if (voices.isEmpty()) return;

        int tone = Math.min(voices.size() - 1, (int) Math.floor(gene * voices.size()));
        play(text, pitch, entity, cacheable, voices.get(tone));
    }

    static String buildSpeakPayload(String text, String modelId) {
        JsonObject voiceSettings = new JsonObject();
        voiceSettings.addProperty("stability", 0.3);
        voiceSettings.addProperty("similarity_boost", 0.5);
        voiceSettings.addProperty("style", 0.05);
        voiceSettings.addProperty("use_speaker_boost", true);

        JsonObject payload = new JsonObject();
        payload.addProperty("text", text);
        payload.addProperty("model_id", modelId);
        payload.add("voice_settings", voiceSettings);
        return payload.toString();
    }

    @Override
    public void downloadAudio(OutputStream output, String voiceId, String text) {
        String payload = buildSpeakPayload(text, Config.getInstance().elevenlabsModel);
        RealtimeSpeechManager.download(output, this.url + voiceId + "?output_format=pcm_22050", payload, Config.getInstance().elevenlabsPrivateAPIkey);
    }
}
