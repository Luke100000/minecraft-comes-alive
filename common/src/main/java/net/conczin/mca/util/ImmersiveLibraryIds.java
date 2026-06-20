package net.conczin.mca.util;

import java.util.OptionalInt;

public final class ImmersiveLibraryIds {
    public static final String PREFIX = "immersive_library:";

    private ImmersiveLibraryIds() {
    }

    public static boolean isValid(String identifier) {
        return contentId(identifier).isPresent();
    }

    public static OptionalInt contentId(String identifier) {
        if (!identifier.startsWith(PREFIX)) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(identifier.substring(PREFIX.length())));
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }
}
