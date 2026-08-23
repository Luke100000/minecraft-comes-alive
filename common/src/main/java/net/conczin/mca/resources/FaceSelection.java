package net.conczin.mca.resources;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class FaceSelection {
    private FaceSelection() {
    }

    public static float geneForIndex(int index, int count) {
        requireCount(count);
        int clampedIndex = Math.max(0, Math.min(count - 1, index));
        return (clampedIndex + 0.5F) / count;
    }

    public static int indexForGene(float gene, int count) {
        requireCount(count);
        return (int)Math.min(count - 1, Math.max(0, gene * count));
    }

    public static <T> List<T> enabledOrFallback(List<T> source, Predicate<T> disabled) {
        List<T> enabled = source.stream().filter(disabled.negate()).toList();
        return enabled.isEmpty() ? source : enabled;
    }

    public static <T> List<Indexed<T>> indexed(List<T> values) {
        List<Indexed<T>> indexed = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            indexed.add(new Indexed<>(index, values.get(index)));
        }
        return List.copyOf(indexed);
    }

    private static void requireCount(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Face selection must contain at least one entry");
        }
    }

    public record Indexed<T>(int index, T value) {
    }
}
