package net.conczin.mca.client.gui;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

final class FamilyTreeSearchDebouncer {
    private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong(1L);

    private final long delayMillis;
    private Request pendingRequest;
    private long dueAtMillis;

    FamilyTreeSearchDebouncer(long delayMillis) {
        this.delayMillis = delayMillis;
    }

    Request schedule(String query, long nowMillis) {
        Request request = new Request(NEXT_REQUEST_ID.getAndIncrement(), query);
        pendingRequest = request;
        dueAtMillis = nowMillis + delayMillis;
        return request;
    }

    void clear() {
        pendingRequest = null;
    }

    Optional<Request> poll(long nowMillis) {
        if (pendingRequest == null || nowMillis < dueAtMillis) {
            return Optional.empty();
        }
        Request request = pendingRequest;
        pendingRequest = null;
        return Optional.of(request);
    }

    record Request(long requestId, String query) {
        boolean matches(long responseRequestId, String responseQuery) {
            return requestId == responseRequestId && query.equals(responseQuery);
        }
    }
}
