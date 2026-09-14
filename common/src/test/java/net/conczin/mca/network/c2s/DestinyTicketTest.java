package net.conczin.mca.network.c2s;

import net.minecraft.SharedConstants;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DestinyTicketTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void destinyTeleportUsesShortLivedLoadingTicket() {
        TicketType ticket = DestinyMessage.destinyTeleportTicket();

        assertEquals(5L, ticket.timeout());
        assertTrue(ticket.doesLoad());
        assertFalse(ticket.doesSimulate());
        assertFalse(ticket.persist());
        assertNotSame(TicketType.PLAYER_LOADING, ticket);
    }
}
