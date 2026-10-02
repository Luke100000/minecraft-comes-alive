package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraveyardManagerTest {
    private static final UUID DECEASED = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void occupiedGraveRoundTripsThroughSavedData() {
        GraveyardManager manager = new GraveyardManager((net.minecraft.server.level.ServerLevel) null);
        GlobalPos grave = GlobalPos.of(Level.NETHER, new BlockPos(12, 70, -34));
        manager.setOccupiedGrave(DECEASED, grave);

        CompoundTag saved = manager.save(new CompoundTag(), null);
        GraveyardManager loaded = new GraveyardManager(saved, null);

        assertEquals(grave, loaded.getOccupiedGrave(DECEASED).orElseThrow());
    }

    @Test
    void legacyDataWithoutOccupiedGravesLoadsEmpty() {
        GraveyardManager loaded = new GraveyardManager(new CompoundTag(), null);

        assertTrue(loaded.getOccupiedGrave(DECEASED).isEmpty());
    }

    @Test
    void clearingOldLocationDoesNotRemoveReplacementLocation() {
        GraveyardManager manager = new GraveyardManager((net.minecraft.server.level.ServerLevel) null);
        GlobalPos oldGrave = GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 64, 1));
        GlobalPos replacement = GlobalPos.of(Level.OVERWORLD, new BlockPos(40, 70, -5));
        manager.setOccupiedGrave(DECEASED, oldGrave);
        manager.setOccupiedGrave(DECEASED, replacement);

        manager.clearOccupiedGrave(DECEASED, oldGrave);

        assertEquals(replacement, manager.getOccupiedGrave(DECEASED).orElseThrow());
    }
}
