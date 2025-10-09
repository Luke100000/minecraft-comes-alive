package net.mca.client.tts.sound;

import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

public class CustomEntityBoundSoundInstance extends EntityBoundSoundInstance {
    private final SingleWeighedSoundEvents weighedSoundEvents;

    public CustomEntityBoundSoundInstance(SingleWeighedSoundEvents weighedSoundEvents, SoundEvent soundEvent, SoundSource soundSource, float volume, float pitch, Entity entity, long l) {
        super(soundEvent, soundSource, volume, pitch, entity, l);

        this.weighedSoundEvents = weighedSoundEvents;
    }

    @Override
    public WeighedSoundEvents resolve(SoundManager soundManager) {
        this.sound = weighedSoundEvents.getSound();
        return weighedSoundEvents;
    }
}
