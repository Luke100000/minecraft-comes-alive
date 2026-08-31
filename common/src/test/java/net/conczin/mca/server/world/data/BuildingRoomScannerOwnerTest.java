package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingRoomScannerOwnerTest {
    @Test
    void doorsAreTraversalOnlyAndNeverOwnFloorCells() {
        assertFalse(StructureConnector.ownsFloorCell(Blocks.OAK_DOOR.defaultBlockState()));
        assertTrue(StructureConnector.ownsFloorCell(Blocks.OAK_FENCE_GATE.defaultBlockState()));
        assertTrue(StructureConnector.ownsFloorCell(Blocks.OAK_TRAPDOOR.defaultBlockState()));
        assertTrue(StructureConnector.ownsFloorCell(Blocks.LADDER.defaultBlockState()));
    }

    @Test
    void connectorSourceChoosesLargestAdjacentInterior() {
        BuildingFloorRegion.Component outside = component(-2, 0, -1, 0);
        BuildingFloorRegion.Component interior = component(1, 0, 5, 3);
        BlockPos doorCell = new BlockPos(0, 88, 0);
        StructureFloor floor = new StructureFloor(0, 88, 94, 0, null);

        BuildingFloorRegion.Component selected = BuildingRoomScanner.selectComponent(
                doorCell, floor, Set.of(doorCell), List.of(outside, interior));

        assertEquals(interior, selected);
        assertEquals(interior, BuildingRoomScanner.componentOwner(List.of(outside, interior)));
    }

    @Test
    void equalAreaComponentsUseStableBoundsTieBreak() {
        BuildingFloorRegion.Component first = component(-4, 0, -3, 1);
        BuildingFloorRegion.Component second = component(1, 0, 2, 1);

        assertEquals(first, BuildingRoomScanner.componentOwner(List.of(second, first)));
    }

    private static BuildingFloorRegion.Component component(
            int minX, int minZ, int maxX, int maxZ) {
        int area = (maxX - minX + 1) * (maxZ - minZ + 1);
        return new BuildingFloorRegion.Component(minX, minZ, maxX, maxZ, area, List.of());
    }
}
