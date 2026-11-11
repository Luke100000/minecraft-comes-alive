package net.conczin.mca.dialogue;

import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Server-thread confined pending operations and their one-shot resolved lines. */
final class DialoguePendingRequests {
    private final Map<UUID, Ticket> pending = new HashMap<>();
    private final Map<UUID, Resolved> ready = new HashMap<>();

    record Key(UUID playerId, UUID villagerId, UUID sessionId, long generation,
               long offerToken, String nodeId, int lineIndex) {
        Key {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(villagerId, "villagerId");
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(nodeId, "nodeId");
            if (lineIndex < 0) {
                throw new IllegalArgumentException("lineIndex must be nonnegative");
            }
        }

        static Key from(DialogueSession session) {
            return new Key(session.playerId(), session.villagerId(), session.id(), session.generation(),
                    session.offerToken(), session.nodeId(), session.lineIndex());
        }
    }

    record Ticket(Key key, UUID interactionId, UUID operationId,
                  DialogueAsyncResolver resolver, long deadline) {
        Ticket {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(interactionId, "interactionId");
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(resolver, "resolver");
        }
    }

    private record Resolved(Key key, Component line) {
    }

    Optional<Ticket> install(Ticket ticket) {
        ready.remove(ticket.key().playerId());
        return Optional.ofNullable(pending.put(ticket.key().playerId(), ticket));
    }

    Optional<Ticket> invalidate(UUID playerId) {
        ready.remove(playerId);
        return Optional.ofNullable(pending.remove(playerId));
    }

    /** Pausing cancels the in-flight operation but retains an already resolved line for Resume. */
    Optional<Ticket> cancelPending(UUID playerId) {
        return Optional.ofNullable(pending.remove(playerId));
    }

    void resumeResolved(Key previous, Key resumed) {
        Resolved current = ready.get(previous.playerId());
        if (current == null || !current.key().equals(previous)) {
            return;
        }
        Key expected = new Key(previous.playerId(), previous.villagerId(), previous.sessionId(),
                previous.generation(), resumed.offerToken(), previous.nodeId(), previous.lineIndex());
        if (expected.equals(resumed)) {
            ready.put(previous.playerId(), new Resolved(resumed, current.line()));
        } else {
            ready.remove(previous.playerId());
        }
    }

    Optional<Ticket> pendingFor(Key key) {
        Ticket current = pending.get(key.playerId());
        return current != null && current.key().equals(key) ? Optional.of(current) : Optional.empty();
    }

    Optional<Component> lineFor(Key key) {
        Resolved current = ready.get(key.playerId());
        return current != null && current.key().equals(key) ? Optional.of(current.line()) : Optional.empty();
    }

    boolean finish(Ticket ticket, Component line) {
        Objects.requireNonNull(line, "line");
        if (pending.get(ticket.key().playerId()) != ticket) {
            return false;
        }
        pending.remove(ticket.key().playerId());
        ready.put(ticket.key().playerId(), new Resolved(ticket.key(), line));
        return true;
    }

    List<Ticket> snapshot() {
        return List.copyOf(pending.values());
    }
}
