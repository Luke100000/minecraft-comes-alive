package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageBuildingStateSnapshotTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void restoreReturnsRoomsStructuresAndLogicalMetadataToSnapshotState() throws Exception {
        Village village = new Village(1, null);
        StructureFloor floor = TestStructureFloors.create(
                0, 8, 12, 0,
                BuildingFloorRegion.fromFootprint(8, List.of(
                        new BlockPos(0, 8, 0),
                        new BlockPos(1, 8, 0),
                        new BlockPos(0, 8, 1),
                        new BlockPos(1, 8, 1))));
        Structure structure = new Structure(10, new BlockPos(0, 9, 0), List.of(floor));
        Building main = room(20, 10, 0, new BlockPos(0, 9, 0));
        main.setLastScan(1234L);
        village.registerStructure(structure, main);

        Village.BuildingStateSnapshot snapshot = village.snapshotBuildingState();

        main.setLastScan(9999L);
        structure.setFloorNumber(0, 7);
        Building replacement = room(21, 10, 0, new BlockPos(1, 9, 1));
        village.registerRoom(replacement);
        assertTrue(village.setMainRoom(replacement));
        assertTrue(village.setBuildingInheritanceEnabled(replacement, false));

        village.restoreBuildingState(snapshot);

        Building restored = village.getBuilding(20).orElseThrow();
        assertNotSame(main, restored);
        assertEquals(1234L, restored.getLastScan());
        assertFalse(village.getBuilding(21).isPresent());
        assertEquals(0, village.getStructure(10).orElseThrow().getFloor(0).orElseThrow().floorNumber());

        LogicalBuilding logical = village.getLogicalBuilding(10).orElseThrow();
        assertEquals(20, logical.mainRoomId());
        assertTrue(logical.inheritanceEnabled());
    }

    private static Building room(int id, int structureId, int floorId, BlockPos source) {
        Building room = new Building(source);
        room.setId(id);
        room.setStructureId(structureId);
        room.setFloorId(floorId);
        room.setGeometry(
                source,
                source,
                BuildingFloorRegion.fromFootprint(source.getY() - 1, List.of(source.below())));
        return room;
    }
}
