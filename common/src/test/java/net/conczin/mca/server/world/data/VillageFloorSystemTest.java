package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageFloorSystemTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void floorNumbersAreRelativeToExplicitGroundFloor() {
        Village village = new Village(1, null);
        Structure low = structure(10, 77, floor(0, 40), floor(1, 44));
        Structure high = structure(11, 77, floor(0, 48));
        village.registerStructure(low, room(100, 10, 1, true));
        village.registerStructure(high, room(101, 11, 0, true));

        village.refreshLogicalBuildings();

        assertEquals(-1, low.getFloor(0).orElseThrow().floorNumber());
        assertEquals(0, low.getFloor(1).orElseThrow().floorNumber());
        assertEquals(1, high.getFloor(0).orElseThrow().floorNumber());
    }

    @Test
    void explicitRemovalRejectsCurrentMainRoom() {
        Village village = populatedVillage();

        village.removeBuilding(1);

        assertTrue(village.getBuildings().containsKey(1));
        assertEquals(1, village.getLogicalBuilding(10).orElseThrow().mainRoomId());
    }

    @Test
    void inheritanceTogglePreservesRoomContributionPreferences() {
        Village village = populatedVillage();
        Building main = village.getBuildings().get(1);

        assertTrue(village.setBuildingInheritanceEnabled(main, false));
        assertFalse(village.isBuildingInheritanceEnabled(main));
        assertTrue(village.getBuildings().get(2).contributesToMain());
        assertFalse(village.getBuildings().get(3).contributesToMain());

        assertTrue(village.setBuildingInheritanceEnabled(main, true));
        assertTrue(village.isBuildingInheritanceEnabled(main));
        assertTrue(village.getBuildings().get(2).contributesToMain());
        assertFalse(village.getBuildings().get(3).contributesToMain());
    }

    @Test
    void changingMainRoomDoesNotRewriteRoomPreferences() {
        Village village = populatedVillage();

        assertTrue(village.setMainRoom(village.getBuildings().get(2)));

        assertEquals(2, village.getLogicalBuilding(10).orElseThrow().mainRoomId());
        assertTrue(village.getBuildings().get(1).contributesToMain());
        assertTrue(village.getBuildings().get(2).contributesToMain());
        assertFalse(village.getBuildings().get(3).contributesToMain());
    }

    @Test
    void invalidMainRoomRepairsToLowestSurvivingRoomId() {
        Village village = populatedVillage();
        village.getLogicalBuilding(10).orElseThrow().setMainRoomId(99);

        village.refreshLogicalBuildings();

        assertEquals(1, village.getLogicalBuilding(10).orElseThrow().mainRoomId());
    }

    @Test
    void roomContributionMutationRequiresRegisteredRoom() {
        Village village = populatedVillage();

        assertTrue(village.setRoomContributesToMain(village.getBuildings().get(3), true));
        assertTrue(village.getBuildings().get(3).contributesToMain());
        assertFalse(village.setRoomContributesToMain(room(99, 10, 0, false), true));
    }

    @Test
    void refreshingOneFloorDoesNotRenumberManualNeighboringFloors() {
        Village village = new Village(1, null);
        Structure structure = structure(10, 77,
                new StructureFloor(3, 64, 70, -1, region(64)),
                new StructureFloor(7, 74, 80, 0, region(74)),
                new StructureFloor(9, 84, 90, 1, region(84)));
        village.registerStructure(structure, room(100, 10, 7, true));

        assertTrue(structure.ensureFloorContains(7, region(75), 82));

        assertEquals(-1, structure.getFloor(3).orElseThrow().floorNumber());
        assertEquals(0, structure.getFloor(7).orElseThrow().floorNumber());
        assertEquals(1, structure.getFloor(9).orElseThrow().floorNumber());
    }

    @Test
    void manualAttachmentRejectsAResultContainingMoreThanOneFreshFloor() {
        StructureScanner.Result scan = new StructureScanner.Result(
                Building.validationResult.SUCCESS,
                BlockPos.ZERO,
                BlockPos.ZERO,
                new BlockPos(1, 80, 1),
                List.of(
                        new StructureFloor(0, 74, 78, region(74)),
                        new StructureFloor(1, 84, 88, region(84))),
                new FloorSurface(Set.of(
                        new FloorSurface.Cell(new BlockPos(0, 74, 0), 74.0D, 78)), Map.of()));

        assertNull(VillageManager.singleScannedFloor(scan));
    }

    @Test
    void fullMaintenanceTargetsRegisteredRoomsInStableIdOrder() {
        Village village = populatedVillage();

        assertEquals(List.of(1, 2, 3), VillageManager.fullScanRoomIds(village));
    }

    private static Village populatedVillage() {
        Village village = new Village(1, null);
        village.registerStructure(structure(10, 10), room(1, 10, 0, true));
        village.getBuildings().put(2, room(2, 10, 0, true));
        village.getBuildings().put(3, room(3, 10, 0, false));
        village.refreshLogicalBuildings();
        return village;
    }

    private static Structure structure(int id, int logicalId, StructureFloor... floors) {
        List<StructureFloor> structureFloors = floors.length == 0
                ? List.of(floor(0, 64))
                : List.of(floors);
        Structure structure = new Structure(id, BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO, structureFloors);
        structure.setLogicalBuildingId(logicalId);
        return structure;
    }

    private static StructureFloor floor(int id, int y) {
        return new StructureFloor(id, y, y + 4, null);
    }

    private static BuildingFloorRegion region(int y) {
        return BuildingFloorRegion.fromFootprint(y, Set.of(
                new BlockPos(0, y, 0), new BlockPos(1, y, 0),
                new BlockPos(0, y, 1), new BlockPos(1, y, 1)));
    }

    private static Building room(int id, int structureId, int floorId, boolean contributes) {
        Building room = new Building(BlockPos.ZERO);
        room.setId(id);
        room.setStructureId(structureId);
        room.setFloorId(floorId);
        room.setContributesToMain(contributes);
        return room;
    }
}
