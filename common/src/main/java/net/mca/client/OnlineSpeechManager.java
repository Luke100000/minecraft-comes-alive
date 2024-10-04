package net.mca.client;

import net.mca.Config;
import net.mca.MCA;
import net.mca.client.sound.CustomEntityBoundSoundInstance;
import net.mca.client.sound.SingleWeighedSoundEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.EntityTrackingSoundInstance;
import net.minecraft.client.sound.Sound;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.floatprovider.ConstantFloatProvider;

import java.io.*;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class OnlineSpeechManager {
    private static final MessageDigest MESSAGEDIGEST;
    public static final OnlineSpeechManager INSTANCE = new OnlineSpeechManager();

    private final MinecraftClient client;
    private boolean warningIssued = false;

    static {
        try {
            MESSAGEDIGEST = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public OnlineSpeechManager() {
        client = MinecraftClient.getInstance();
    }

    private final Random random = new Random();

    public static String toHex(byte[] bytes) {
        BigInteger bi = new BigInteger(1, bytes);
        return String.format(Locale.ROOT, "%0" + (bytes.length << 1) + "X", bi);
    }

    public String getHash(String text) {
        MESSAGEDIGEST.update(text.getBytes());
        return toHex(MESSAGEDIGEST.digest()).toLowerCase(Locale.ROOT);
    }

    public void play(String language, String voice, float pitch, String text, Entity entity) {
        String hash = getHash(text);
        CompletableFuture.runAsync(() -> {
            if (downloadAudio(language, voice, text, hash, false)) {
                play(language, voice, pitch, entity, hash);
            } else if (!warningIssued) {
                warningIssued = true;
                MinecraftClient.getInstance().getMessageHandler().onGameMessage(
                        Text.translatable("command.tts_busy").formatted(Formatting.ITALIC, Formatting.GRAY),
                        false
                );
            }
        });
    }

    public void play(String language, String voice, float pitch, Entity entity, String hash) {
        Identifier soundLocation = MCA.locate("tts_cache/" + language + "-" + voice + "/" + hash);
        Sound sound = new Sound(soundLocation.getPath(), ConstantFloatProvider.create(1.0f), ConstantFloatProvider.create(1.0f), 1, Sound.RegistrationType.FILE, true, false, 16);
        SingleWeighedSoundEvents weightedSoundEvents = new SingleWeighedSoundEvents(sound, soundLocation, "");
        EntityTrackingSoundInstance instance = new CustomEntityBoundSoundInstance(weightedSoundEvents, SoundEvent.of(soundLocation), SoundCategory.NEUTRAL, 1.0f, pitch, entity, random.nextLong());
        client.execute(() -> client.getSoundManager().play(instance));
    }

    public boolean downloadAudio(String language, String voice, String text, String hash, boolean dry) {
        File file = new File("tts_cache/" + language + "-" + voice + "/" + hash + ".ogg");
        if (file.exists()) {
            if (file.length() == 0) {
                try {
                    Files.delete(file.toPath());
                } catch (IOException e) {
                    MCA.LOGGER.warn("Failed to delete file " + file.getPath());
                }
            } else {
                return true;
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        try (FileOutputStream stream = new FileOutputStream(file)) {
            Map<String, String> params = Map.of(
                    "text", cleanPhrase(text),
                    "language", language,
                    "speaker", voice,
                    "file_format", "ogg",
                    "cache", "true",
                    "prepare_speakers", String.valueOf(SpeechManager.TOTAL_VOICES),
                    "load_async", "true"
            );
            String encodedURL = params.keySet().stream()
                    .map(key -> key + "=" + URLEncoder.encode(params.get(key), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&", Config.getInstance().villagerTTSServer + "v1/tts/xtts-v2?", ""));
            return downloadFile(encodedURL, stream, dry);
        } catch (IOException e) {
            MCA.LOGGER.warn("Failed to open file " + file.getPath());
        }
        return false;
    }

    private boolean downloadFile(String url, OutputStream output, boolean dry) {
        try {
            HttpURLConnection connection = (HttpURLConnection) (new URL(url)).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            int totalBytesRead = 0;
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    if (dry) {
                        break;
                    }
                }
            }

            connection.disconnect();

            return totalBytesRead > 100;
        } catch (IOException e) {
            MCA.LOGGER.warn("Failed to download " + url + ": " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("SpellCheckingInspection")
    public String cleanPhrase(String p) {
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
}
