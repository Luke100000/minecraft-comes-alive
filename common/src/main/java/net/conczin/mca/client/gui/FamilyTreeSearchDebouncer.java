package net.conczin.mca.client.gui;

import java.util.Optional;

final class FamilyTreeSearchDebouncer {
    private final long delayMillis;
    private String pendingQuery;
    private long dueAtMillis;

    FamilyTreeSearchDebouncer(long delayMillis) {
        this.delayMillis = delayMillis;
    }

    void schedule(String query, long nowMillis) {
        pendingQuery = query;
        dueAtMillis = nowMillis + delayMillis;
    }

    void clear() {
        pendingQuery = null;
    }

    Optional<String> poll(long nowMillis) {
        if (pendingQuery == null || nowMillis < dueAtMillis) {
            return Optional.empty();
        }
        String query = pendingQuery;
        pendingQuery = null;
        return Optional.of(query);
    }
}
