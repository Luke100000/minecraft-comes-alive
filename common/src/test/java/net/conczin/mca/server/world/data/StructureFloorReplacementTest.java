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
                List.of(new StructureFloor(7, 3, 64, new FloorGeometry(
                        List.of(
                                new FloorGeometry.Cell(new BlockPos(0, 64, 0), 68),
                                new FloorGeometry.Cell(new BlockPos(1, 64, 0), 68)),
                        List.of()))));
        FloorGeometry fresh = new FloorGeometry(List.of(
                new FloorGeometry.Cell(new BlockPos(1, 64, 0), 72),
                new FloorGeometry.Cell(new BlockPos(2, 66, 0), 72),
                new FloorGeometry.Cell(new BlockPos(3, 66, 0), 72)), List.of());

        assertTrue(structure.replaceFloorGeometry(7, fresh));

        StructureFloor floor = structure.getFloor(7).orElseThrow();
        assertEquals(3, floor.floorNumber());
        assertEquals(64, floor.anchorY());
        assertEquals(72, floor.maxPhysicalCeilingY());
        assertTrue(!floor.contains(0, 0));
        assertTrue(floor.contains(3, 0));
    }
}
