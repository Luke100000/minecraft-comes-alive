package net.conczin.mca.dialogue;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueAsyncLifecycleTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID VILLAGER = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final UUID INTERACTION = UUID.fromString("00000000-0000-0000-0000-000000000104");
    private static final DialogueAsyncResolver RESOLVER = new DialogueAsyncResolver.StructureLocation(
            Optional.of("configured_rumors"), Optional.empty(), "gui.dialogue.searching", "dialogue.location.forgot");

    @Test
    void staleAsyncCompletionsAreDiscardedWithoutOverwritingCurrentLine() {
        DialoguePendingRequests pending = new DialoguePendingRequests();
        var first = ticket(PLAYER, 1L, 100L);
        var next = ticket(PLAYER, 2L, 100L);
        assertTrue(pending.install(first).isEmpty());
        assertEquals(Optional.of(first), pending.install(next));
        assertFalse(pending.finish(first, Component.literal("stale coordinates")));
        assertTrue(pending.lineFor(first.key()).isEmpty());
        assertEquals(Optional.of(next), pending.pendingFor(next.key()));

        assertTrue(pending.finish(next, Component.literal("new coordinates")));
        assertFalse(pending.finish(next, Component.literal("duplicate")));
        assertEquals("new coordinates", pending.lineFor(next.key()).orElseThrow().getString());
        assertTrue(pending.lineFor(first.key()).isEmpty());
        assertTrue(pending.pendingFor(next.key()).isEmpty());
    }

    @Test
    void leavingOrReloadingCancelsPendingWorkAndResolvedSnapshot() {
        DialoguePendingRequests pending = new DialoguePendingRequests();
        var first = ticket(PLAYER, 10L, 200L);
        pending.install(first);
        assertEquals(Optional.of(first), pending.invalidate(PLAYER));
        assertFalse(pending.finish(first, Component.literal("late")));

        var resumed = ticket(PLAYER, 11L, 300L);
        assertNotEquals(first.operationId(), resumed.operationId(), "resume requires a fresh locator owner");
        pending.install(resumed);
        assertTrue(pending.finish(resumed, Component.literal("found")));
        assertEquals("found", pending.lineFor(resumed.key()).orElseThrow().getString());
        assertTrue(pending.invalidate(PLAYER).isEmpty());
        assertTrue(pending.lineFor(resumed.key()).isEmpty());
    }

    @Test
    void independentPlayersHaveIndependentTicketsAndTimeoutWindows() {
        DialoguePendingRequests pending = new DialoguePendingRequests();
        var one = ticket(PLAYER, 42L, 600L);
        var other = ticket(UUID.fromString("00000000-0000-0000-0000-000000000105"), 42L, 900L);
        pending.install(one);
        pending.install(other);
        assertEquals(List.of(one), pending.snapshot().stream()
                .filter(ticket -> ticket.deadline() <= 700L).toList());
        assertEquals(Optional.of(one), pending.invalidate(PLAYER));
        assertEquals(Optional.of(other), pending.pendingFor(other.key()));
        assertTrue(pending.finish(other, Component.literal("other player's answer")));
        assertEquals("other player's answer", pending.lineFor(other.key()).orElseThrow().getString());
    }

    private static DialoguePendingRequests.Ticket ticket(UUID player, long token, long deadline) {
        return new DialoguePendingRequests.Ticket(
                new DialoguePendingRequests.Key(player, VILLAGER, SESSION, 3L, token, "location", 0),
                INTERACTION, UUID.randomUUID(), RESOLVER, deadline);
    }
}
