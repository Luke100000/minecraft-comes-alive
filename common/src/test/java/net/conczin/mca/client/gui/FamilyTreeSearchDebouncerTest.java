package net.conczin.mca.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FamilyTreeSearchDebouncerTest {
    @Test
    void rapidEditsOnlyEmitLatestQueryAfterQuietPeriod() {
        FamilyTreeSearchDebouncer debouncer = new FamilyTreeSearchDebouncer(150L);

        debouncer.schedule("a", 1_000L);
        debouncer.schedule("alice", 1_100L);

        assertTrue(debouncer.poll(1_249L).isEmpty());
        assertEquals("alice", debouncer.poll(1_250L).orElseThrow());
        assertTrue(debouncer.poll(1_400L).isEmpty());
    }

    @Test
    void clearDropsScheduledQuery() {
        FamilyTreeSearchDebouncer debouncer = new FamilyTreeSearchDebouncer(150L);
        debouncer.schedule("alice", 1_000L);

        debouncer.clear();

        assertTrue(debouncer.poll(2_000L).isEmpty());
    }
}
