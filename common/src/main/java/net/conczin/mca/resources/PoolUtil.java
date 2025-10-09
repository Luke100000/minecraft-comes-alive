package net.conczin.mca.resources;

import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.Optional;

public interface PoolUtil {

    static <T> Optional<T> pop(List<T> selection, RandomSource rng) {
        return Optional.ofNullable(popOne(selection, null, rng));
    }

    static <T> T popOne(List<T> selection, T def, RandomSource rng) {

        if (selection.isEmpty()) {
            return def;
        }

        return selection.remove(rng.nextInt(selection.size()));
    }

    static <T> Optional<T> pick(List<T> selection, RandomSource rng) {
        return Optional.ofNullable(pickOne(selection, null, rng));
    }

    static <T> T pickOne(List<T> selection, T def, RandomSource rng) {

        if (selection.isEmpty()) {
            return def;
        }

        return selection.get(rng.nextInt(selection.size()));
    }

    static <T> T pickOne(T[] selection, T def, RandomSource rng) {

        if (selection.length == 0) {
            return def;
        }

        return selection[rng.nextInt(selection.length)];
    }
}
