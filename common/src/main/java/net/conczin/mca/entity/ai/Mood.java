package net.conczin.mca.entity.ai;

import net.conczin.mca.util.RegistryRef;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;

public class Mood {
    private final String name;

    private final int soundInterval;
    private final RegistryRef<SoundEvent> soundMale;
    private final RegistryRef<SoundEvent> soundFemale;
    private final int particleInterval;
    private final SimpleParticleType particle;
    private final ChatFormatting color;
    private final String building;

    Mood(String name, int soundInterval, RegistryRef<SoundEvent> soundMale, RegistryRef<SoundEvent> soundFemale, int particleInterval, SimpleParticleType particle, ChatFormatting color, String building) {
        this.name = name;
        this.soundInterval = soundInterval;
        this.soundMale = soundMale;
        this.soundFemale = soundFemale;
        this.particleInterval = particleInterval;
        this.particle = particle;
        this.color = color;
        this.building = building;
    }

    public Component getText() {
        return Component.translatable("mood." + name.toLowerCase(Locale.ENGLISH));
    }

    public String getName() {
        return name;
    }

    public int getSoundInterval() {
        return soundInterval;
    }

    public SoundEvent getSoundMale() {
        return soundMale.get();
    }

    public SoundEvent getSoundFemale() {
        return soundFemale.get();
    }

    public int getParticleInterval() {
        return particleInterval;
    }

    public SimpleParticleType getParticle() {
        return particle;
    }

    public ChatFormatting getColor() {
        return color;
    }

    public String getBuilding() {
        return building;
    }
}
