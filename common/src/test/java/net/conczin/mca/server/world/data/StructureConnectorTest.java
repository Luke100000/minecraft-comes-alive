package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureConnectorTest {
    @Test
    void verticalColumnConnectsOnlyFloorsItPhysicallyReaches() {
        StructureFloor candidate = floor(64, 68);
        StructureFloor connectedBelow = floor(60, 64);
        StructureFloor distantBelow = floor(52, 56);
        List<BlockPos> actualConnectorColumn = List.of(
                new BlockPos(0, 62, 0),
                new BlockPos(0, 63, 0),
                new BlockPos(0, 64, 0));

        assertTrue(StructureConnector.connectsFloors(actualConnectorColumn, candidate, connectedBelow));
        assertFalse(StructureConnector.connectsFloors(actualConnectorColumn, candidate, distantBelow));
    }

    private static StructureFloor floor(int anchorY, int ceilingY) {
        return new StructureFloor(0, anchorY, ceilingY, 0,
                BuildingFloorRegion.fromFootprint(anchorY, Set.of(
                        new BlockPos(0, anchorY, 0),
                        new BlockPos(1, anchorY, 0),
                        new BlockPos(0, anchorY, 1),
                        new BlockPos(1, anchorY, 1))),
                List.of(new StructureFloor.ConnectorMarker(
                        new BlockPos(0, anchorY, 0), StructureFloor.ConnectorType.LADDER)));
    }
}
