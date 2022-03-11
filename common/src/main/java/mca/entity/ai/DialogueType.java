package mca.entity.ai;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import mca.entity.ai.relationship.AgeState;
import net.minecraft.util.Language;

public enum DialogueType {
    ADULT(null),
    UNASSIGNED(ADULT),
    BABY(UNASSIGNED),
    CHILD(ADULT),
    CHILDP(CHILD),
    TODDLER(CHILD),
    TODDLERP(CHILDP),
    SPOUSE(ADULT),
    TEEN(ADULT),
    TEENP(TEEN);

    public final DialogueType fallback;

    DialogueType(DialogueType fallback) {
        this.fallback = fallback;
    }

    private static final DialogueType[] VALUES = values();

    public static final Map<String, DialogueType> MAP = Arrays.stream(VALUES).collect(Collectors.toMap(DialogueType::name, Function.identity()));

    public DialogueType toChild() {
        return switch (this) {
            case TODDLER -> TODDLERP;
            case CHILD -> CHILDP;
            case TEEN -> TEENP;
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

    public static String applyFallback(String key) {
        int split = key.indexOf(".");

        if (split <= 0) {
            return key;
        }

        DialogueType type = DialogueType.MAP.get(key.substring(0, split));
        if (type == null) {
            return key;
        }

        String phrase = key.substring(split + 1);

        while (type != null) {
            String s = type.name().toLowerCase(Locale.ENGLISH) + "." + phrase;

            if (Language.getInstance().hasTranslation(s)) {
                return s;
            }

            type = type.fallback;
        }
        return phrase;
    }
}
