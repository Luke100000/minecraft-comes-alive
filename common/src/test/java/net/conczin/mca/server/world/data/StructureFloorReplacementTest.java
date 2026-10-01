package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureFloorReplacementTest {
    @Test
    void replacementUsesFreshGeometryAndKeepsFloorNumber() {
        TestFloorFootprint original = TestFloorFootprint.fromFootprint(64, List.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0)));
        Structure structure = new Structure(1, new BlockPos(0, 64, 0),
                List.of(TestStructureFloors.create(7, 64, 68, 3, original)));
        TestFloorFootprint fresh = TestFloorFootprint.fromFootprint(64, List.of(
                new BlockPos(1, 64, 0), new BlockPos(2, 64, 0)));

        assertTrue(structure.replaceFloorGeometry(7,
                TestStructureFloors.create(0, 64, 72, fresh)));

        StructureFloor floor = structure.getFloor(7).orElseThrow();
        assertEquals(3, floor.floorNumber());
        assertEquals(72, floor.maxPhysicalCeilingY());
        assertTrue(!floor.contains(0, 0));
        assertTrue(floor.contains(2, 0));
    }
}
