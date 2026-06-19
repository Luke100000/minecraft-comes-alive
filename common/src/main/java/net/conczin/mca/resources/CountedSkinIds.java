package net.conczin.mca.resources;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CountedSkinIds {
    private CountedSkinIds() {
    }

    static List<String> expand(String pattern, int count) {
        if (!pattern.contains("%")) {
            return List.of(pattern);
        }
        if (!pattern.contains("%d") || pattern.indexOf("%d") != pattern.lastIndexOf("%d")) {
            throw new IllegalArgumentException("Skin id pattern must contain exactly one %d placeholder: " + pattern);
        }
        if (count < 0) {
            throw new IllegalArgumentException("Skin id pattern requires count: " + pattern);
        }

        List<String> identifiers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            identifiers.add(String.format(Locale.ROOT, pattern, i));
        }
        return identifiers;
    }
}
