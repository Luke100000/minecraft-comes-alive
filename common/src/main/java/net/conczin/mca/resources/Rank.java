package net.conczin.mca.resources;

import java.util.Locale;

public enum Rank {
    OUTLAW,
    PEASANT,
    MERCHANT,
    NOBLE,
    MAYOR,
    MONARCH;

    private static final Rank[] VALUES = values();

    public Rank promote() {
        if (ordinal() + 1 < VALUES.length) {
            return VALUES[ordinal() + 1];
        } else {
            return Rank.MONARCH;
        }
    }

    public Rank degrade() {
        if (ordinal() - 1 >= 0) {
            return VALUES[ordinal() - 1];
        } else {
            return null;
        }
    }

    public static Rank fromName(String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException ignored) {
            
        }
        return PEASANT;
    }

    public boolean isAtLeast(Rank r) {
        return ordinal() >= r.ordinal();
    }

    public String getTranslationKey() {
        return "gui.village.rank." + name().toLowerCase(Locale.ENGLISH);
    }
}
