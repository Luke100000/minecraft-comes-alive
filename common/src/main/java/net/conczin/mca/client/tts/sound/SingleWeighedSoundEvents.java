package net.conczin.mca.client.tts.sound;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

public class SingleWeighedSoundEvents extends WeighedSoundEvents {
    private final Sound sound;

    public SingleWeighedSoundEvents(Sound sound, Identifier identifier, @Nullable String string) {
        super(identifier, string);
        this.sound = sound;
    }


    @Override
    public int getWeight() {
        return 1;
    }

    @Override
    public Sound getSound(RandomSource randomSource) {
        return sound;
    }

    public Sound getSound() {
        return sound;
    }
}
