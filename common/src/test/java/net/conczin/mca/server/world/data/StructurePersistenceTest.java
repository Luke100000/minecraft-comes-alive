package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructurePersistenceTest {
    @Test
    void structureRequiresAtLeastOneFloor() {
        assertThrows(IllegalArgumentException.class,
                () -> new Structure(12, BlockPos.ZERO, List.of()));
    }

    @Test
    void directFloorRemovalCannotLeaveEmptyStructure() {
        StructureFloor floor = new StructureFloor(4, -1, new FloorGeometry(
                List.of(new FloorGeometry.Cell(BlockPos.ZERO, 4)), List.of()));
        Structure structure = new Structure(12, BlockPos.ZERO, List.of(floor));

        assertFalse(structure.removeFloor(4));
        assertTrue(structure.getFloor(4).isPresent());
    }

    @Test
    void directFloorRemovalStillRemovesOneFloorFromMultiFloorStructure() {
        StructureFloor first = new StructureFloor(4, 0, new FloorGeometry(
                List.of(new FloorGeometry.Cell(BlockPos.ZERO, 4)), List.of()));
        BlockPos upper = BlockPos.ZERO.above(8);
        StructureFloor second = new StructureFloor(5, 1, new FloorGeometry(
                List.of(new FloorGeometry.Cell(upper, 12)), List.of()));
        Structure structure = new Structure(12, BlockPos.ZERO, List.of(first, second));

        assertTrue(structure.removeFloor(5));
        assertTrue(structure.getFloor(4).isPresent());
        assertTrue(structure.getFloor(5).isEmpty());
    }

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

    @Test
    void floorWithoutPersistedAnchorRetainsLegacyRepresentativeHeight() {
        FloorGeometry geometry = new FloorGeometry(List.of(
                new FloorGeometry.Cell(new BlockPos(0, 64, 0), 70),
                new FloorGeometry.Cell(new BlockPos(1, 66, 0), 72),
                new FloorGeometry.Cell(new BlockPos(2, 66, 0), 72)), List.of());
        CompoundTag tag = new StructureFloor(4, 0, 64, geometry).save();
        tag.remove("anchorY");

        StructureFloor loaded = StructureFloor.load(tag);

        assertEquals(66, loaded.anchorY());
        assertEquals(0, FloorGrouping.prospectiveNumber(List.of(loaded), loaded, 68).number().orElseThrow());
    }
}
