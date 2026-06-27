package net.conczin.mca.client.tts;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.client.tts.resources.OnlineLanguageMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class OnlineSpeechManager {
    public static final int TOTAL_VOICES = 10;

    private boolean warningIssued = false;

    public static void languageNotSupported() {
        Minecraft.getInstance().gui.chatListener().handleSystemMessage(
                Component.translatable("command.tts_unsupported_language").withStyle(s -> s
                        .withColor(ChatFormatting.RED)
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create("https://github.com/Luke100000/minecraft-comes-alive/wiki/TTS")))), false);
    }

    @SuppressWarnings("SpellCheckingInspection")
    public static String cleanPhrase(String p) {
        p = p.replaceAll("\\*.*\\*", "");
        p = p.replace("%supporter%", "someone");
        p = p.replace("%Supporter%", "someone");
        p = p.replace("some %2$s", "something");
        p = p.replace("at %2$s", "somewhere here");
        p = p.replace("At %2$s", "Somewhere here");
        p = p.replace(" to %2$s", " to here");
        p = p.replace(", %1$s.", ".");
        p = p.replace(", %1$s!", "!");
        p = p.replace(" %1$s!", "!");
        p = p.replace(", %1$s.", ".");
        p = p.replace("%1$s!", " ");
        p = p.replace("%1$s, ", " ");
        p = p.replace("%1$s", " ");
        p = p.replace("avoid %2$s", "avoid that location");
        p = p.replace(" Should be around %2$s.", "");
        p = p.replace("  ", " ");
        p = p.replace(" ,", ",");
        p = p.replace("Bahaha! ", "");
        p = p.replace("Run awaaaaaay! ", "Run!");
        p = p.replace("Aaaaaaaahhh! ", "");
        p = p.replace("Aaaaaaahhh! ", "");
        p = p.replace("Aaaaaaaaaaahhh! ", "");
        p = p.replace("AAAAAAAAAAAAAAAAAAAHHHHHH!!!!!! ", "");
        p = p.trim();
        return p;
    }

    public void play(String phrase, String gameLang, String gender, float pitch, float gene, Entity entity, boolean translatable) {
        if (!translatable) return;
        String text = OnlineSpeechManager.cleanPhrase(phrase);
        String language = OnlineLanguageMap.LANGUAGE_MAP.getOrDefault(gameLang, "");
        if (language.isEmpty()) {
            languageNotSupported();
        } else {
            int tone = Math.min(TOTAL_VOICES - 1, (int) Math.floor(gene * TOTAL_VOICES));
            String voice = gender + "_" + tone;

            CompletableFuture.runAsync(() -> {
                String hash = language + "-" + voice + "/" + AudioCache.getHash(text) + ".ogg";
                if (AudioCache.cachedRetrieve(hash, output -> {
                    downloadAudio(output, language, voice, text);
                })) {
                    Identifier soundLocation = MCA.locate("tts_cache/" + hash);
                    SpeechManager.INSTANCE.playSound(pitch, entity, soundLocation);
                } else if (!warningIssued) {
                    warningIssued = true;
                    Minecraft.getInstance().gui.chatListener().handleSystemMessage(
                            Component.translatable("command.tts_busy").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY),
                            false
                    );
                }
            });
        }
    }

    public void downloadAudio(OutputStream output, String language, String voice, String text) {
        Map<String, String> params = Map.of(
                "text", text,
                "language", language,
                "speaker", voice,
                "file_format", "ogg",
                "cache", "true",
                "prepare_speakers", String.valueOf(TOTAL_VOICES),
                "load_async", "true"
        );
        String url = params.keySet().stream()
                .map(key -> key + "=" + URLEncoder.encode(params.get(key), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&", Config.getInstance().onlineTTSServer + "v1/tts/xtts-v2?", ""));
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
            }

            connection.disconnect();
        } catch (IOException e) {
            MCA.LOGGER.warn("Failed to download {}: {}", url, e.getMessage());
        }
    }
}
