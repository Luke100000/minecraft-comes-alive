package net.mca.client.tts;

import net.mca.Config;
import net.mca.MCA;
import net.mca.client.tts.sound.CustomEntityBoundSoundInstance;
import net.mca.client.tts.sound.SingleWeighedSoundEvents;
import net.mca.entity.CommonSpeechManager;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Genetics;
import net.mca.util.LimitedLinkedHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.EntityTrackingSoundInstance;
import net.minecraft.client.sound.Sound;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.LiteralTextContent;
import net.minecraft.text.Text;
import net.minecraft.text.TextContent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.math.floatprovider.ConstantFloatProvider;
import net.minecraft.util.math.random.Random;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SpeechManager {
    public static final SpeechManager INSTANCE = new SpeechManager();

    private final MinecraftClient client;
    private final LimitedLinkedHashMap<UUID, EntityTrackingSoundInstance> currentlyPlaying = new LimitedLinkedHashMap<>(10);

    private final RealtimeSpeechManager realtimeSpeechManager = new RealtimeSpeechManager(Config.getInstance().onlineTTSServer);
    private final Player2SpeechManager player2SpeechManager = new Player2SpeechManager(Config.getInstance().player2Url);
    private final ElevenlabsSpeechManager elevenlabsSpeechManager = new ElevenlabsSpeechManager();
    private final OnlineSpeechManager onlineSpeechManager = new OnlineSpeechManager();

    @SuppressWarnings("deprecation")
    private final Random threadSafeRandom = Random.createThreadSafe();

    public SpeechManager() {
        client = MinecraftClient.getInstance();
    }

    public EntityTrackingSoundInstance getSound(float pitch, Entity entity, Identifier soundLocation) {
        Sound sound = new Sound(soundLocation.getPath(), ConstantFloatProvider.create(1.0f), ConstantFloatProvider.create(1.0f), 1, Sound.RegistrationType.FILE, true, false, 16);
        SingleWeighedSoundEvents weightedSoundEvents = new SingleWeighedSoundEvents(sound, soundLocation, "");
        return new CustomEntityBoundSoundInstance(weightedSoundEvents, SoundEvent.of(soundLocation), SoundCategory.NEUTRAL, 1.0f, pitch, entity, threadSafeRandom.nextLong());
    }

    public void playSound(float pitch, Entity entity, Identifier soundLocation) {
        client.execute(() -> client.getSoundManager().play(SpeechManager.INSTANCE.getSound(pitch, entity, soundLocation)));
    }

    public void onChatMessage(Text message, UUID sender) {
        TextContent content = message.getContent();
        if (CommonSpeechManager.INSTANCE.translations.containsKey(content)) {
            speak(CommonSpeechManager.INSTANCE.translations.get(content), sender, true);
        } else if (content instanceof LiteralTextContent literal) {
            speak(literal.string(), sender, false);
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

    private void speak(String phrase, UUID sender, boolean translatable) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        if (currentlyPlaying.containsKey(sender) && client.getSoundManager().isPlaying(currentlyPlaying.get(sender))) {
            return;
        }

        VillagerEntityMCA villager = getSpeaker(client, sender);
        if (villager == null) return;

        if (villager.isSpeechImpaired()) return;
        if (villager.isToYoungToSpeak()) return;

        float pitch = villager.getSoundPitch();
        float gene = villager.getGenetics().getGene(Genetics.VOICE_TONE);

        String gender = villager.getGenetics().getGender().binary().getDataName();
        if (Config.getInstance().enableOnlineTTS) {
            if (translatable) {
                if (Language.getInstance().hasTranslation(phrase)) {
                    phrase = Language.getInstance().get(phrase);
                } else {
                    MCA.LOGGER.warn("Tried to play a TTS sound for a non-translatable phrase: {}", phrase);
                    return;
                }
            }

            String gameLang = client.options.language;
            switch (Config.getInstance().onlineTTSModel) {
                case "realtime" ->
                        realtimeSpeechManager.play(phrase, gender, gameLang, pitch, gene, villager, translatable);
                case "player2" -> player2SpeechManager.play(phrase, gender, gameLang, pitch, gene);
                case "elevenlabs" -> elevenlabsSpeechManager.play(phrase, gender, pitch, gene, villager, translatable);
                default -> onlineSpeechManager.play(phrase, gameLang, gender, pitch, gene, villager, translatable);
            }
        } else if (translatable) {
            // Use the resourcepack, if available
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

    private long lastHealthCheckTime = 0;
    private boolean firstRun = true;

    public void tick(MinecraftClient client) {
        if (client.world != null) {
            long time = client.world.getTime();
            if (Math.abs(time - lastHealthCheckTime) > 1200) {
                boolean enabled = Config.getInstance().villagerChatAIModel.equals("player2");
                if (firstRun || enabled) {
                    CompletableFuture.runAsync(() -> {
                        if (player2SpeechManager.checkHealth() && firstRun && !enabled) {
                            client.inGameHud.getChatHud().addMessage(Text.translatable("command.chat_ai.player2.hint", "/mca chatAI player2"));
                            firstRun = false;
                        }
                    });
                }
                lastHealthCheckTime = time;
            }
        }
    }
}