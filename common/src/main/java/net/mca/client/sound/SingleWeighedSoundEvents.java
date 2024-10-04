package net.mca.client.sound;

import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

public class SingleWeighedSoundEvents extends WeightedSoundSet {
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
    public Sound getSound(Random randomSource) {
        return sound;
    }

    public Sound getSound() {
        return sound;
    }
}
