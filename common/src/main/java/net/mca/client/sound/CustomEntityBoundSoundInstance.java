package net.mca.client.sound;

import net.minecraft.client.sound.EntityTrackingSoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

public class CustomEntityBoundSoundInstance extends EntityTrackingSoundInstance {
    private final SingleWeighedSoundEvents weighedSoundEvents;

    public CustomEntityBoundSoundInstance(SingleWeighedSoundEvents weighedSoundEvents, SoundEvent soundEvent, SoundCategory soundSource, float volume, float pitch, Entity entity, long l) {
        super(soundEvent, soundSource, volume, pitch, entity, l);

        this.weighedSoundEvents = weighedSoundEvents;
    }

    @Override
    public WeightedSoundSet getSoundSet(SoundManager soundManager) {
        this.sound = weighedSoundEvents.getSound();
        return weighedSoundEvents;
    }
}
