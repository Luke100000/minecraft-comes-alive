package net.conczin.mca.entity.ai.relationship;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum Personality {
    //Fallback on error.
    UNASSIGNED,

    FRIENDLY,      // Easier to make friends
    FLIRTY,        // Likes to chat, flirt and kiss
    PLAYFUL,         // Loves games and fun activities
    GLOOMY,        // Always assuming the worst
    SENSITIVE,     // Double heart penalty
    GREEDY,        // Finds less on chores
    ODD,           // some interactions are more difficult
    CRABBY,        // Hard to talk to
    EXTROVERTED,   // Enjoys group activities
    INTROVERTED,   // Prefers solitary activities
    RELAXED,       // Calm and unbothered
    ANXIOUS,       // Easily stressed
    PEACEFUL,      // Avoids conflict
    UPBEAT;        // Optimistic and cheerful

    private static final RandomSource random = RandomSource.create();

    public static Personality getRandom() {
        return getRandom(AgeState.ADULT);
    }

    public static Personality getRandom(AgeState ageState) {
        List<Personality> validList = new ArrayList<>();

        for (Personality personality : Personality.values()) {
            if (personality != UNASSIGNED) {
                if (personality == FLIRTY && (ageState == AgeState.BABY || ageState == AgeState.TODDLER || ageState == AgeState.CHILD)) {
                    continue;
                }
                validList.add(personality);
            }
        }

        return validList.get(random.nextInt(validList.size()));
    }

    public Component getName() {
        return Component.translatable("personality." + name().toLowerCase(Locale.ENGLISH));
    }

    public Component getDescription() {
        return Component.translatable("personalityDescription." + name().toLowerCase(Locale.ENGLISH));
    }
}
