package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureFloorResolutionTest {
    @Test
    void directPositionUsesItsVerticalFloorBandWhenFloorsShareTheSameColumn() {
        StructureFloor lower = floor(0, 64, 68);
        StructureFloor upper = floor(1, 68, 72);
        Structure structure = structure(lower, upper);

        assertEquals(lower, structure.resolveFloorAt(new BlockPos(0, 67, 0)).orElseThrow());
        assertEquals(upper, structure.resolveFloorAt(new BlockPos(0, 68, 0)).orElseThrow());
    }

    @Test
    void directPositionOutsideEveryFloorBandDoesNotSnapToNearestFloorByColumn() {
        StructureFloor floor = floor(0, 64, 68);
        Structure structure = structure(floor);

        assertTrue(structure.resolveFloorAt(new BlockPos(0, 80, 0)).isEmpty(),
                "Y must choose a real Floor band before X/Z can resolve Room ownership");
    }

    private static Structure structure(StructureFloor... floors) {
        return new Structure(10, new BlockPos(0, 64, 0), BlockPos.ZERO, BlockPos.ZERO, List.of(floors));
    }

    private static StructureFloor floor(int id, int anchorY, int ceilingY) {
        BuildingFloorRegion region = BuildingFloorRegion.fromFootprint(
                anchorY, Set.of(new BlockPos(0, anchorY, 0)));
        return new StructureFloor(id, anchorY, ceilingY, id, region);
    }
}
