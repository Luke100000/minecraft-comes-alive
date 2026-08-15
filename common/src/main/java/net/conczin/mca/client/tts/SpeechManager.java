package net.conczin.mca.client.tts;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.client.tts.sound.CustomEntityBoundSoundInstance;
import net.conczin.mca.client.tts.sound.SingleWeighedSoundEvents;
import net.conczin.mca.entity.CommonSpeechManager;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.util.LimitedLinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.entity.Entity;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class SpeechManager {
    public static final SpeechManager INSTANCE = new SpeechManager();

    private final Minecraft client;
    private final LimitedLinkedHashMap<UUID, EntityBoundSoundInstance> currentlyPlaying = new LimitedLinkedHashMap<>(10);

    private final RealtimeSpeechManager realtimeSpeechManager = new RealtimeSpeechManager(Config.getInstance().onlineTTSServer);
    private final Player2SpeechManager player2SpeechManager = new Player2SpeechManager(Config.getInstance().player2Url);
    private final ElevenlabsSpeechManager elevenlabsSpeechManager = new ElevenlabsSpeechManager();
    private final OnlineSpeechManager onlineSpeechManager = new OnlineSpeechManager();

    @SuppressWarnings("deprecation")
    private final RandomSource threadSafeRandom = RandomSource.createThreadSafe();

    public SpeechManager() {
        client = Minecraft.getInstance();
    }

    public EntityBoundSoundInstance getSound(float pitch, Entity entity, ResourceLocation soundLocation) {
        Sound sound = new Sound(soundLocation.getPath(), ConstantFloat.of(1.0f), ConstantFloat.of(1.0f), 1, Sound.Type.FILE, true, false, 16);
        SingleWeighedSoundEvents weightedSoundEvents = new SingleWeighedSoundEvents(sound, soundLocation, "");
        return new CustomEntityBoundSoundInstance(weightedSoundEvents, SoundEvent.createVariableRangeEvent(soundLocation), SoundSource.NEUTRAL, 1.0f, pitch, entity, threadSafeRandom.nextLong());
    }

    public void playSound(float pitch, Entity entity, ResourceLocation soundLocation) {
        client.execute(() -> client.getSoundManager().play(SpeechManager.INSTANCE.getSound(pitch, entity, soundLocation)));
    }

    public void playPreview(VillagerEntityMCA villager) {
        stopPreview(villager);
        String phrase = VoicePreviewSamples.random(ThreadLocalRandom.current());
        if (Config.getInstance().enableOnlineTTS) {
            speak(phrase, villager.getUUID(), villager, true);
        } else {
            tryPlayVoicePackSound(phrase, villager.getUUID(), villager);
        }
    }

    public boolean canPreviewVoiceTone() {
        return Config.getInstance().enableOnlineTTS || hasVoicePackSounds();
    }

    public void stopPreview(VillagerEntityMCA villager) {
        EntityBoundSoundInstance sound = currentlyPlaying.remove(villager.getUUID());
        if (sound != null) {
            client.getSoundManager().stop(sound);
        }
    }

    public void onChatMessage(Component message, UUID sender) {
        ComponentContents content = message.getContents();
        if (CommonSpeechManager.INSTANCE.translations.containsKey(content)) {
            speak(CommonSpeechManager.INSTANCE.translations.get(content), sender, true);
        } else if (content instanceof LiteralContents literal) {
            speak(literal.text(), sender, false);
        }
    }

    private VillagerEntityMCA getSpeaker(Minecraft client, UUID sender) {
        if (client.level != null) {
            for (Entity entity : client.level.entitiesForRendering()) {
                if (entity instanceof VillagerEntityMCA v && entity.getUUID().equals(sender)) {
                    return v;
                }
            }
        }
        return null;
    }

    private void speak(String phrase, UUID sender, boolean translatable) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        if (currentlyPlaying.containsKey(sender) && client.getSoundManager().isActive(currentlyPlaying.get(sender))) {
            return;
        }

        VillagerEntityMCA villager = getSpeaker(client, sender);
        if (villager == null) return;

        speak(phrase, sender, villager, translatable);
    }

    private void speak(String phrase, UUID sender, VillagerEntityMCA villager, boolean translatable) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        if (villager.isSpeechImpaired()) return;
        if (villager.isToYoungToSpeak()) return;

        float pitch = villager.getVoicePitch();
        float gene = villager.getGenetics().getGene(Genetics.VOICE_TONE);

        String gender = villager.getGenetics().getGender().binary().getDataName();
        if (Config.getInstance().enableOnlineTTS) {
            if (translatable) {
                if (Language.getInstance().has(phrase)) {
                    phrase = Language.getInstance().getOrDefault(phrase);
                } else {
                    MCA.LOGGER.warn("Tried to play a TTS sound for a non-translatable phrase: {}", phrase);
                    return;
                }
            }

            String gameLang = client.options.languageCode;
            switch (Config.getInstance().onlineTTSModel) {
                case "realtime" ->
                        realtimeSpeechManager.play(phrase, gender, gameLang, pitch, gene, villager, translatable);
                case "player2" -> player2SpeechManager.play(phrase, gender, gameLang, pitch, gene);
                case "elevenlabs" -> elevenlabsSpeechManager.play(phrase, gender, pitch, gene, villager, translatable);
                default -> onlineSpeechManager.play(phrase, gameLang, gender, pitch, gene, villager, translatable);
            }
        } else if (translatable) {
            tryPlayVoicePackSound(phrase, sender, villager);
        }
    }

    private boolean tryPlayVoicePackSound(String phrase, UUID sender, VillagerEntityMCA villager) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return false;
        }

        float pitch = villager.getVoicePitch();
        float gene = villager.getGenetics().getGene(Genetics.VOICE_TONE);
        String gender = villager.getGenetics().getGender().binary().getDataName();
        int tone = Math.min(9, (int)Math.floor(gene * 10.0f));
        ResourceLocation sound = new ResourceLocation("mca_voices", phrase.toLowerCase(Locale.ROOT) + "/" + gender + "_" + tone);
        Collection<ResourceLocation> keys = client.getSoundManager().getAvailableSounds();
        if (!keys.contains(sound)) {
            return false;
        }

        EntityBoundSoundInstance instance = new EntityBoundSoundInstance(SoundEvent.createVariableRangeEvent(sound), SoundSource.NEUTRAL, 1.0f, pitch, villager, threadSafeRandom.nextLong());
        currentlyPlaying.put(sender, instance);
        client.getSoundManager().play(instance);
        return true;
    }

    private boolean hasVoicePackSounds() {
        return client.getSoundManager().getAvailableSounds().stream().anyMatch(sound -> sound.getNamespace().equals("mca_voices"));
    }

    private long lastHealthCheckTime = 0;
    private boolean firstRun = true;

    public void tick(Minecraft client) {
        if (client.level != null) {
            long time = client.level.getGameTime();
            if (Math.abs(time - lastHealthCheckTime) > 1200) {
                boolean enabled = Config.getInstance().villagerChatAIModel.equals("player2");
                if (firstRun || enabled) {
                    CompletableFuture.runAsync(() -> {
                        if (player2SpeechManager.checkHealth() && firstRun && !enabled) {
                            client.gui.getChat().addMessage(Component.translatable("command.chat_ai.player2.hint", "/mca chatAI player2"));
                            firstRun = false;
                        }
                    });
                }
                lastHealthCheckTime = time;
            }
        }
    }
}