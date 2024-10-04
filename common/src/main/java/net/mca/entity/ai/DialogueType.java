package net.mca.entity.ai;

import net.mca.MCAClient;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.entity.ai.relationship.Personality;
import net.minecraft.util.Language;
import net.minecraft.util.math.random.Random;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum DialogueType {
    ADULT(null),
    ADULTP(ADULT),
    UNASSIGNED(ADULT),
    BABY(UNASSIGNED),
    CHILD(ADULT),
    CHILDP(CHILD),
    TODDLER(CHILD),
    TODDLERP(CHILDP),
    SPOUSE(ADULT),
    TEEN(ADULT),
    TEENP(TEEN),
    ENGAGED(ADULT);

    public final DialogueType fallback;

    private static final Random random = Random.create();

    DialogueType(DialogueType fallback) {
        this.fallback = fallback;
    }

    private static final DialogueType[] VALUES = values();

    public static final Map<String, DialogueType> MAP = Arrays.stream(VALUES).collect(Collectors.toMap(DialogueType::name, Function.identity()));

    /**
     * @return Returns the player-child variant
     */
    public DialogueType toChild() {
        return switch (this) {
            case TODDLER -> TODDLERP;
            case CHILD -> CHILDP;
            case TEEN -> TEENP;
            case ADULT -> ADULTP;
            default -> UNASSIGNED;
        };
    }

    public static DialogueType fromAge(AgeState state) {
        for (DialogueType t : values()) {
            if (t.name().equals(state.name())) {
                return t;
            }
        }
        return UNASSIGNED;
    }

    public static DialogueType byId(int id) {
        if (id < 0 || id >= VALUES.length) {
            return UNASSIGNED;
        }
        return VALUES[id];
    }

    private static Optional<String> getPrefixedPhrase(DialogueType type, String prefix, String key) {
        //first, test every dialogue type
        DialogueType t = type;
        while (t != null) {
            String s = prefix + "." + t.name().toLowerCase(Locale.ENGLISH) + "." + key;
            if (Language.getInstance().hasTranslation(s)) {
                return Optional.of(s);
            }
            t = t.fallback;
        }

        //then test without type
        String s = prefix + "." + key;
        if (Language.getInstance().hasTranslation(s)) {
            return Optional.of(s);
        }

        return Optional.empty();
    }

    public static String applyFallback(String key) {
        if (!key.contains("#")) {
            return key;
        }

        //extract flags
        Map<String, String> flags = new HashMap<>();
        for (String s : key.split("\\.")) {
            if (s.startsWith("#")) {
                flags.put(s.substring(1, 2), s.substring(2));
                key = key.replace(s + ".", "");
            }
        }

        //check for type
        DialogueType type = null;
        if (flags.containsKey("T")) {
            type = DialogueType.MAP.get(flags.get("T"));
        }
        if (type == null) {
            return key;
        }

        //first try professions
        //children can't have profession, this is already checked in the Messenger
        if (flags.containsKey("P") && random.nextBoolean()) {
            Optional<String> p = getPrefixedPhrase(type, flags.get("P"), key);
            if (p.isPresent()) {
                return p.get();
            }
        }

        //then try personality
        if (flags.containsKey("E") && MCAClient.useExpandedPersonalityTranslations()) {
            String personality = Personality.valueOf(flags.get("E")).name().toLowerCase(Locale.ROOT);
            Optional<String> p = getPrefixedPhrase(type, personality, key);
            if (p.isPresent()) {
                return p.get();
            }
        }

        //then try gender
        if (flags.containsKey("G")) {
            Optional<String> p = getPrefixedPhrase(type, flags.get("G"), key);
            if (p.isPresent()) {
                return p.get();
            }
        }

        //try all types
        DialogueType t = type;
        while (t != null) {
            String s = t.name().toLowerCase(Locale.ENGLISH) + "." + key;
            if (Language.getInstance().hasTranslation(s)) {
                return s;
            }
            t = t.fallback;
        }

        return key;
    }
}
