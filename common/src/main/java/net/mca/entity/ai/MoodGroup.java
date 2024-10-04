package net.mca.entity.ai;

import net.mca.SoundsMCA;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.Arrays;
import java.util.List;

public class MoodGroup {
    public static final MoodGroup INSTANCE = new MoodGroup(
            new MoodBuilder("depressed")
                    .sounds(4, SoundsMCA.VILLAGER_MALE_CRY, SoundsMCA.VILLAGER_FEMALE_CRY)
                    .particles(20, ParticleTypes.SPLASH)
                    .building("inn")
                    .color(Formatting.RED).build(),
            new MoodBuilder("sad")
                    .sounds(8, SoundsMCA.VILLAGER_MALE_CRY, SoundsMCA.VILLAGER_FEMALE_CRY)
                    .particles(50, ParticleTypes.SPLASH)
                    .building("inn")
                    .color(Formatting.GOLD).build(),
            new MoodBuilder("unhappy").build(),
            new MoodBuilder("passive").build(),
            new MoodBuilder("fine").build(),
            new MoodBuilder("happy")
                    .color(Formatting.DARK_GREEN).build(),
            new MoodBuilder("overjoyed")
                    .sounds(8, SoundsMCA.VILLAGER_MALE_LAUGH, SoundsMCA.VILLAGER_FEMALE_LAUGH)
                    .particles(50, ParticleTypes.HAPPY_VILLAGER)
                    .color(Formatting.GREEN).build());

    //-15 to 15 is a range create normal interactions, but mood can go -15 to -100 due to player interactions.
    public static final int NORMAL_MIN_LEVEL = -15;
    public static final int MAX_LEVEL = 15;

    private final List<Mood> moods;

    MoodGroup(Mood... m) {
        moods = Arrays.asList(m);
    }

    // clamps to valid range
    public static int clampMood(int moodPoints) {
        return MathHelper.clamp(moodPoints, NORMAL_MIN_LEVEL, MAX_LEVEL);
    }

    // returns the index of mood based on mood points
    private int getLevel(int moodPoints) {
        return MathHelper.clamp(
                (moodPoints - NORMAL_MIN_LEVEL) * moods.size() / (MAX_LEVEL - NORMAL_MIN_LEVEL),
                0,
                moods.size() - 1
        );
    }

    public Mood getMood(int moodPoints) {
        int level = getLevel(moodPoints);
        return moods.get(level);
    }
}
