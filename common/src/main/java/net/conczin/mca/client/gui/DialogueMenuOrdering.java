package net.conczin.mca.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** Extracts one server-highlighted topic without reordering or losing the other offered options. */
final class DialogueMenuOrdering {
    private DialogueMenuOrdering() {
    }

    static <T> Selection<T> split(List<T> options, Predicate<? super T> highlighted) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(highlighted, "highlighted");
        T featured = null;
        List<T> remaining = new ArrayList<>(options.size());
        for (T option : options) {
            if (featured == null && highlighted.test(option)) {
                featured = option;
            } else {
                remaining.add(option);
            }
        }
        return new Selection<>(Optional.ofNullable(featured), List.copyOf(remaining));
    }

    record Selection<T>(Optional<T> featured, List<T> remaining) {
    }
}
