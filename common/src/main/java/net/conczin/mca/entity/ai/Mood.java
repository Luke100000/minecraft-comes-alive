package net.conczin.mca.entity.ai;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;

import java.util.Locale;

public class Mood {
    private final String name;

    private final int soundInterval;
    private final SoundEvent soundMale;
    private final SoundEvent soundFemale;
    private final int particleInterval;
    private final SimpleParticleType particle;
    private final ChatFormatting color;
    private final String building;

    Mood(String name, int soundInterval, SoundEvent soundMale, SoundEvent soundFemale, int particleInterval, SimpleParticleType particle, ChatFormatting color, String building) {
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
        return soundMale;
    }

    public SoundEvent getSoundFemale() {
        return soundFemale;
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
