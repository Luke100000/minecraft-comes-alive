package net.conczin.mca.dialogue;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates a coordinate tuple and formats its clipboard representation. */
public final class DialogueCoordinates {
    private static final Pattern TRIPLET = Pattern.compile(
            "\\s*(-?\\d+)(?:\\s*,\\s*|\\s+)(-?\\d+)(?:\\s*,\\s*|\\s+)(-?\\d+)\\s*");

    private DialogueCoordinates() {
    }

    public static Optional<String> copyValue(String text) {
        if (text == null || text.length() > 128) {
            return Optional.empty();
        }
        Matcher match = TRIPLET.matcher(text);
        if (!match.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(match.group(1)) + " "
                    + Integer.parseInt(match.group(2)) + " "
                    + Integer.parseInt(match.group(3)));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
