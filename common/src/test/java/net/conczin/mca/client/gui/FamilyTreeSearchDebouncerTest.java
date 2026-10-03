package net.conczin.mca.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FamilyTreeSearchDebouncerTest {
    @Test
    void rapidEditsOnlyEmitLatestQueryAfterQuietPeriod() {
        FamilyTreeSearchDebouncer debouncer = new FamilyTreeSearchDebouncer(150L);

        debouncer.schedule("a", 1_000L);
        debouncer.schedule("alice", 1_100L);

        assertTrue(debouncer.poll(1_249L).isEmpty());
        assertEquals("alice", debouncer.poll(1_250L).orElseThrow().query());
        assertTrue(debouncer.poll(1_400L).isEmpty());
    }

    @Test
    void clearDropsScheduledQuery() {
        FamilyTreeSearchDebouncer debouncer = new FamilyTreeSearchDebouncer(150L);
        debouncer.schedule("alice", 1_000L);

        debouncer.clear();

        assertTrue(debouncer.poll(2_000L).isEmpty());
    }

    @Test
    void repeatedQueryAfterAnotherEditRejectsTheOlderRequestIdentity() {
        FamilyTreeSearchDebouncer debouncer = new FamilyTreeSearchDebouncer(150L);

        FamilyTreeSearchDebouncer.Request firstAlice = debouncer.schedule("alice", 1_000L);
        debouncer.schedule("bob", 1_010L);
        FamilyTreeSearchDebouncer.Request latestAlice = debouncer.schedule("alice", 1_020L);

        assertNotEquals(firstAlice.requestId(), latestAlice.requestId());
        assertFalse(latestAlice.matches(firstAlice.requestId(), "alice"));
        assertTrue(latestAlice.matches(latestAlice.requestId(), "alice"));
    }

    @Test
    void requestIdentityDoesNotRestartForANewScreenDebouncer() {
        FamilyTreeSearchDebouncer firstScreen = new FamilyTreeSearchDebouncer(150L);
        FamilyTreeSearchDebouncer secondScreen = new FamilyTreeSearchDebouncer(150L);

        long firstId = firstScreen.schedule("alice", 1_000L).requestId();
        long secondId = secondScreen.schedule("alice", 1_000L).requestId();

        assertNotEquals(firstId, secondId);
    }
}
