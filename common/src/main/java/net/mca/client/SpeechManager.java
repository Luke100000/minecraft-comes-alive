package net.mca.client;

import net.mca.Config;
import net.mca.entity.CommonSpeechManager;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Genetics;
import net.mca.util.LimitedLinkedHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.EntityTrackingSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.math.random.Random;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

public class SpeechManager {
    public static final SpeechManager INSTANCE = new SpeechManager();

    public static final int TOTAL_VOICES = 10;

    private final LimitedLinkedHashMap<UUID, EntityTrackingSoundInstance> currentlyPlaying = new LimitedLinkedHashMap<>(10);

    @SuppressWarnings("deprecation")
    private final Random threadSafeRandom = Random.createThreadSafe();

    public void onChatMessage(Text message, UUID sender) {
        TextContent content = message.getContent();
        if (CommonSpeechManager.INSTANCE.translations.containsKey(content)) {
            speak(CommonSpeechManager.INSTANCE.translations.get(content), sender);
        } else {
            for (Text sibling : message.getSiblings()) {
                if (CommonSpeechManager.INSTANCE.translations.containsKey(sibling.getContent())) {
                    speak(CommonSpeechManager.INSTANCE.translations.get(sibling.getContent()), sender);
                }
            }
        }
    }

    private VillagerEntityMCA getSpeaker(MinecraftClient client, UUID sender) {
        if (client.world != null) {
            for (Entity entity : client.world.getEntities()) {
                if (entity instanceof VillagerEntityMCA v && entity.getUuid().equals(sender)) {
                    return v;
                }
            }
        }
        return null;
    }

    private void speak(String phrase, UUID sender) {
        if (currentlyPlaying.containsKey(sender) && MinecraftClient.getInstance().getSoundManager().isPlaying(currentlyPlaying.get(sender))) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            VillagerEntityMCA villager = getSpeaker(client, sender);
            if (villager != null) {
                if (villager.isSpeechImpaired()) return;
                if (villager.isToYoungToSpeak()) return;

                float pitch = villager.getSoundPitch();
                float gene = villager.getGenetics().getGene(Genetics.VOICE_TONE);

                String gender = villager.getGenetics().getGender().binary().getDataName();
                if (Config.getInstance().enableOnlineTTS) {
                    String gameLang = client.options.language;
                    if (LanguageMap.LANGUAGE_MAP.containsKey(gameLang) && !LanguageMap.LANGUAGE_MAP.get(gameLang).isEmpty()) {
                        String content = Language.getInstance().get(phrase);
                        int tone = Math.min(TOTAL_VOICES - 1, (int) Math.floor(gene * TOTAL_VOICES));
                        String voice = gender + "_" + tone;
                        OnlineSpeechManager.INSTANCE.play(LanguageMap.LANGUAGE_MAP.get(gameLang), voice, pitch, content, villager);
                    } else {
                        MutableText styled = (Text.translatable("command.tts_unsupported_language")).styled(s -> s
                                .withColor(Formatting.RED)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://github.com/Luke100000/minecraft-comes-alive/wiki/TTS")));
                        client.inGameHud.getChatHud().addMessage(styled);
                    }
                } else {
                    int tone = Math.min(9, (int) Math.floor(gene * 10.0f));
                    Identifier sound = new Identifier("mca_voices", phrase.toLowerCase(Locale.ROOT) + "/" + gender + "_" + tone);

                    if (client.world != null && client.player != null) {
                        Collection<Identifier> keys = client.getSoundManager().getKeys();
                        if (keys.contains(sound)) {
                            EntityTrackingSoundInstance instance = new EntityTrackingSoundInstance(SoundEvent.of(sound), SoundCategory.NEUTRAL, 1.0f, pitch, villager, threadSafeRandom.nextLong());
                            currentlyPlaying.put(sender, instance);
                            client.getSoundManager().play(instance);
                        }
                    }
                }
            }
        }
    }
}
