package net.conczin.mca.server.world.data;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResidentHomeAssignmentsTest {
    @Test
    void rejectedClaimDoesNotMutateExistingAssignment() {
        UUID resident = UUID.randomUUID();
        UUID canonicalOwner = UUID.randomUUID();
        Map<UUID, Long> homes = new HashMap<>(Map.of(resident, 10L, canonicalOwner, 20L));

        assertFalse(ResidentHomeAssignments.claim(homes, resident, 20L));
        assertEquals(Map.of(resident, 10L, canonicalOwner, 20L), homes);
    }

    @Test
    void acceptedReassignmentReplacesOnlyResidentsOwnHome() {
        UUID resident = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Map<UUID, Long> homes = new HashMap<>(Map.of(resident, 10L, other, 20L));

        assertTrue(ResidentHomeAssignments.claim(homes, resident, 30L));
        assertEquals(Map.of(resident, 30L, other, 20L), homes);
    }
}
