package net.conczin.mca.server.world.data;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IndoorRoomCacheTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void unregisteredShelterCacheUsesBounded100TickTtlAndImmutableRoomCells() {
        assertEquals(100L, IndoorRoomCache.TTL_TICKS);
        BlockPos cell = new BlockPos(4, 64, 4);
        IndoorRoomCache.Room room = new IndoorRoomCache.Room(Set.of(cell));
        assertTrue(room.floorCells().contains(cell));
        assertThrows(UnsupportedOperationException.class, () -> room.floorCells().add(BlockPos.ZERO));
    }
}
