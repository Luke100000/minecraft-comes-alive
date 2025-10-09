package net.mca.entity.ai.relationship;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

public enum Personality {
    //Fallback on error.
    UNASSIGNED,

    ATHLETIC,      //Runs 15% faster
    CONFIDENT,     //Deals way more attack damage
    FRIENDLY,      //Bonus 15% points to all interactions
    FLIRTY,        //likes to chat, flirt and kiss
    WITTY,         //likes jokes.
    SHY,           //TODO: 7.4.0
    GLOOMY,        //Always assuming the worst -- TODO: 7.4.0
    SENSITIVE,     //Double heart penalty
    GREEDY,        //Finds less on chores
    ODD,           //some interactions are more difficult
    LAZY,          //20% slower
    GRUMPY,        //Hard to talk to -- TODO: 7.4.0
    PEPPY;         //They're super loud and hyperactive

    private static final RandomSource random = RandomSource.create();

    public static Personality getRandom() {
        List<Personality> validList = new ArrayList<>();

        for (Personality personality : Personality.values()) {
            if (personality != UNASSIGNED) {
                validList.add(personality);
            }
        }

        return validList.get(random.nextInt(validList.size()));
    }

    public float getSpeedModifier() {
        if (this == Personality.ATHLETIC) {
            return 1.15F;
        }
        if (this == Personality.LAZY) {
            return 0.8F;
        }
        return 1;
    }

    public Component getName() {
        return Component.translatable("personality." + name().toLowerCase(Locale.ENGLISH));
    }

    public Component getDescription() {
        return Component.translatable("personalityDescription." + name().toLowerCase(Locale.ENGLISH));
    }
}
