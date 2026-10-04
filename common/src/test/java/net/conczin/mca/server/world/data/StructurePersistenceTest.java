package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StructurePersistenceTest {
    @Test
    void saveOmitsLegacyNextFloorIdButStillLoadsTagsThatContainIt() {
        BlockPos source = new BlockPos(2, 64, 3);
        StructureFloor floor = new StructureFloor(4, -1, new FloorGeometry(
                List.of(new FloorGeometry.Cell(source, 68)), List.of()));
        Structure structure = new Structure(12, source, List.of(floor));
        structure.setLogicalBuildingId(7);

        CompoundTag legacyTag = structure.save();
        legacyTag.putInt("nextFloorId", 99);

        Structure reloaded = new Structure(legacyTag);

        assertEquals(12, reloaded.getId());
        assertEquals(7, reloaded.getLogicalBuildingId());
        assertEquals(source, reloaded.getSource());
        assertEquals(-1, reloaded.getFloor(4).orElseThrow().floorNumber());
        assertFalse(reloaded.save().contains("nextFloorId"));
    }
}
