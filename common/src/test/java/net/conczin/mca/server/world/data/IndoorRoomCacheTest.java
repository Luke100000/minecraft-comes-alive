package net.conczin.mca.server.world.data;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndoorRoomCacheTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void openStateDoesNotInvalidateStructuralToggleables() {
        var closedDoor = Blocks.OAK_DOOR.defaultBlockState();
        var closedGate = Blocks.OAK_FENCE_GATE.defaultBlockState();
        var closedTrapdoor = Blocks.OAK_TRAPDOOR.defaultBlockState();

        assertEquals(IndoorRoomCache.structuralState(closedDoor),
                IndoorRoomCache.structuralState(closedDoor.setValue(BlockStateProperties.OPEN, true)));
        assertEquals(IndoorRoomCache.structuralState(closedGate),
                IndoorRoomCache.structuralState(closedGate.setValue(BlockStateProperties.OPEN, true)));
        assertEquals(IndoorRoomCache.structuralState(closedTrapdoor),
                IndoorRoomCache.structuralState(closedTrapdoor.setValue(BlockStateProperties.OPEN, true)));
    }
}
