package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void registeringLegacyGraveOnlyWhenMissingPersistsNewIndexEntry() {
        GraveyardManager manager = new GraveyardManager(new CompoundTag(), null);
        GlobalPos grave = GlobalPos.of(Level.NETHER, new BlockPos(12, 70, -34));
        assertFalse(manager.isDirty());

        manager.registerMissingGrave(DECEASED, grave);

        assertEquals(grave, manager.getOccupiedGrave(DECEASED).orElseThrow());
        assertTrue(manager.isDirty());
        GraveyardManager reloaded = new GraveyardManager(manager.save(new CompoundTag(), null), null);
        assertEquals(grave, reloaded.getOccupiedGrave(DECEASED).orElseThrow());
    }

    @Test
    void registeringLegacyGraveCannotReplaceNewerLocationOrDirtyUnchangedIndex() {
        GraveyardManager manager = new GraveyardManager((net.minecraft.server.level.ServerLevel) null);
        GlobalPos latest = GlobalPos.of(Level.OVERWORLD, new BlockPos(42, 72, 7));
        GlobalPos old = GlobalPos.of(Level.OVERWORLD, new BlockPos(3, 64, 3));
        manager.setOccupiedGrave(DECEASED, latest);
        manager.setDirty(false);

        manager.registerMissingGrave(DECEASED, old);
        manager.registerMissingGrave(DECEASED, latest);

        assertEquals(latest, manager.getOccupiedGrave(DECEASED).orElseThrow());
        assertFalse(manager.isDirty());
        manager.setOccupiedGrave(DECEASED, old);
        assertEquals(old, manager.getOccupiedGrave(DECEASED).orElseThrow());
        assertTrue(manager.isDirty());
    }
}
