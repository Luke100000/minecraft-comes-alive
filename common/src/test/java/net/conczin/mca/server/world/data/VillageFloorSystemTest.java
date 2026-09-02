package net.conczin.mca.server.world.data;

import com.google.gson.JsonObject;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
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
    void floorNumbersAreRebuiltFromLogicalGroundAfterSaveLoad() {
        Village village = new Village(1, null);
        Structure low = structure(10, 77, floor(0, 40), floor(1, 44));
        Structure high = structure(11, 77, floor(0, 48));
        village.registerStructure(low, room(100, 10, 1, true));
        village.registerStructure(high, room(101, 11, 0, true));
        village.refreshLogicalBuildings();

        Village reloaded = new Village(village.save(), null);

        assertEquals(-1, reloaded.getStructure(10).orElseThrow()
                .getFloor(0).orElseThrow().floorNumber());
        assertEquals(0, reloaded.getStructure(10).orElseThrow()
                .getFloor(1).orElseThrow().floorNumber());
        assertEquals(1, reloaded.getStructure(11).orElseThrow()
                .getFloor(0).orElseThrow().floorNumber());
    }

    @Test
    void explicitRemovalRejectsCurrentMainRoom() {
        Village village = populatedVillage();

        assertFalse(village.removeRoom(1));

        assertTrue(village.getBuildings().containsKey(1));
        assertEquals(1, village.getLogicalBuilding(10).orElseThrow().mainRoomId());
    }

    @Test
    void explicitRoomRemovalReturnsFalseForUnknownRoom() {
        Village village = populatedVillage();

        assertFalse(village.removeRoom(999));
        assertEquals(3, village.getBuildings().size());
    }

    @Test
    void explicitExternalRemovalDoesNotTouchFunctionalRooms() {
        Village village = populatedVillage();
        ExternalBuilding external = new ExternalBuilding(new BlockPos(20, 64, 20));
        external.setId(50);
        external.setType("town_center");
        village.registerExternalBuilding(external);

        assertTrue(village.removeExternalBuilding(50));

        assertTrue(village.getBuilding(50).isEmpty());
        assertTrue(village.getBuilding(1).isPresent());
        assertTrue(village.getBuilding(2).isPresent());
        assertFalse(village.removeExternalBuilding(50));
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
    void disablingSharingWithOneEligibleTypeAutoResolvesWithoutPolymorph() {
        Map<String, BuildingType> previous = installSingleWorkshopType();
        try {
            Village village = populatedVillage();
            Building room = village.getBuildings().get(2);
            room.addBlock(Blocks.CRAFTING_TABLE, BlockPos.ZERO);

            RoomInheritanceUpdate update = village.analyzeRoomInheritanceUpdate(room, false);

            assertEquals(List.of("workshop"), update.matchingTypes());
            assertFalse(update.requiresTypeSelection());
            assertEquals(Building.validationResult.SUCCESS,
                    village.commitRoomInheritanceUpdate(update, null));
            assertFalse(room.contributesToMain());
            assertEquals("workshop", room.getType());
            assertFalse(room.isTypeForced());
        } finally {
            BuildingTypes.getInstance().setBuildingTypes(previous);
        }
    }

    @Test
    void invalidPolymorphChoiceDoesNotDisableRoomSharing() {
        Map<String, BuildingType> previous = installSingleWorkshopType();
        try {
            Village village = populatedVillage();
            Building room = village.getBuildings().get(2);
            room.addBlock(Blocks.CRAFTING_TABLE, BlockPos.ZERO);
            RoomInheritanceUpdate update = village.analyzeRoomInheritanceUpdate(room, false);

            assertEquals(Building.validationResult.INVALID_TYPE,
                    village.commitRoomInheritanceUpdate(update, "not_eligible"));
            assertTrue(room.contributesToMain());
        } finally {
            BuildingTypes.getInstance().setBuildingTypes(previous);
        }
    }

    @Test
    void ambiguousInheritancePolymorphAppliesSharingAndSelectedTypeAtomically() {
        Map<String, BuildingType> previous = installAmbiguousWorkshopTypes();
        try {
            Village village = populatedVillage();
            Building room = village.getBuildings().get(2);
            room.addBlock(Blocks.CRAFTING_TABLE, BlockPos.ZERO);
            RoomInheritanceUpdate update = village.analyzeRoomInheritanceUpdate(room, false);

            assertTrue(update.requiresTypeSelection());
            assertEquals(Building.validationResult.SUCCESS,
                    village.commitRoomInheritanceUpdate(update, "workshop"));
            assertFalse(room.contributesToMain());
            assertEquals("workshop", room.getType());
            assertTrue(room.isTypeForced());
        } finally {
            BuildingTypes.getInstance().setBuildingTypes(previous);
        }
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
    void replacingOneFloorGeometryDoesNotRenumberNeighboringFloors() {
        Village village = new Village(1, null);
        Structure structure = structure(10, 77,
                new StructureFloor(3, 64, 70, -1, region(64)),
                new StructureFloor(7, 74, 80, 0, region(74)),
                new StructureFloor(9, 84, 90, 1, region(84)));
        village.registerStructure(structure, room(100, 10, 7, true));

        assertTrue(structure.replaceFloorGeometry(7,
                new StructureFloor(0, 75, 82, region(75))));

        assertEquals(-1, structure.getFloor(3).orElseThrow().floorNumber());
        assertEquals(0, structure.getFloor(7).orElseThrow().floorNumber());
        assertEquals(1, structure.getFloor(9).orElseThrow().floorNumber());
        assertEquals(75, structure.getFloor(7).orElseThrow().anchorY());
    }

    @Test
    void onlyOutermostEmptyUpperAndBasementFloorsAreRemovable() {
        Village village = new Village(1, null);
        Structure structure = structure(10, 10,
                new StructureFloor(1, 64, 68, -2, region(64)),
                new StructureFloor(2, 68, 72, -1, region(68)),
                new StructureFloor(3, 72, 76, 0, region(72)),
                new StructureFloor(4, 76, 80, 1, region(76)),
                new StructureFloor(5, 80, 84, 2, region(80)));
        Building main = room(100, 10, 3, true);
        village.registerStructure(structure, main);
        village.refreshLogicalBuildings();

        assertTrue(village.canRemoveFloor(10, 1));
        assertFalse(village.canRemoveFloor(10, 2));
        assertFalse(village.canRemoveFloor(10, 3));
        assertFalse(village.canRemoveFloor(10, 4));
        assertTrue(village.canRemoveFloor(10, 5));

        assertTrue(village.removeFloor(10, 5));
        assertTrue(structure.getFloor(5).isEmpty());
        assertTrue(village.canRemoveFloor(10, 4));

        assertTrue(village.removeFloor(10, 1));
        assertTrue(structure.getFloor(1).isEmpty());
        assertTrue(village.canRemoveFloor(10, 2));
        assertEquals(main, village.getBuilding(100).orElseThrow());
        assertEquals(0, structure.getFloor(3).orElseThrow().floorNumber());
    }

    @Test
    void removingLastRoomFromTerminalFloorKeepsFloorAvailableForExplicitRemoval() {
        Village village = new Village(1, null);
        Structure structure = structure(10, 10,
                new StructureFloor(0, 64, 68, 0, region(64)),
                new StructureFloor(1, 72, 76, 1, region(72)));
        Building main = room(100, 10, 0, true);
        Building upperRoom = room(101, 10, 1, true);
        village.registerStructure(structure, main);
        village.registerRoom(upperRoom);
        village.refreshLogicalBuildings();

        assertTrue(village.removeRoom(101));

        assertTrue(village.getBuilding(101).isEmpty());
        assertTrue(structure.getFloor(1).isPresent());
        assertTrue(village.canRemoveFloor(10, 1));
        assertTrue(structure.getFloor(0).isPresent());
        assertEquals(main, village.getBuilding(100).orElseThrow());
    }

    @Test
    void removingLastRoomFromMiddleFloorKeepsTheFloor() {
        Village village = new Village(1, null);
        Structure structure = structure(10, 10,
                new StructureFloor(0, 64, 68, 0, region(64)),
                new StructureFloor(1, 72, 76, 1, region(72)),
                new StructureFloor(2, 80, 84, 2, region(80)));
        Building main = room(100, 10, 0, true);
        Building middleRoom = room(101, 10, 1, true);
        village.registerStructure(structure, main);
        village.registerRoom(middleRoom);
        village.refreshLogicalBuildings();

        assertTrue(village.removeRoom(101));

        assertTrue(village.getBuilding(101).isEmpty());
        assertTrue(structure.getFloor(1).isPresent());
        assertTrue(structure.getFloor(2).isPresent());
        assertEquals(main, village.getBuilding(100).orElseThrow());
    }

    @Test
    void fullMaintenanceTargetsRegisteredRoomsInStableIdOrder() {
        Village village = populatedVillage();

        assertEquals(List.of(1, 2, 3), VillageManager.fullScanRoomIds(village));
    }

    private static Village populatedVillage() {
        Village village = new Village(1, null);
        village.registerStructure(structure(10, 10), room(1, 10, 0, true));
        village.registerRoom(room(2, 10, 0, true));
        village.registerRoom(room(3, 10, 0, false));
        village.refreshLogicalBuildings();
        return village;
    }

    private static Map<String, BuildingType> installSingleWorkshopType() {
        BuildingTypes types = BuildingTypes.getInstance();
        Map<String, BuildingType> previous = types.getBuildingTypes();
        types.setBuildingTypes(Map.of("workshop", craftingTableType("workshop")));
        return previous;
    }

    private static Map<String, BuildingType> installAmbiguousWorkshopTypes() {
        BuildingTypes types = BuildingTypes.getInstance();
        Map<String, BuildingType> previous = types.getBuildingTypes();
        types.setBuildingTypes(Map.of(
                "music_store", craftingTableType("music_store"),
                "workshop", craftingTableType("workshop")));
        return previous;
    }

    private static BuildingType craftingTableType(String name) {
        JsonObject blocks = new JsonObject();
        blocks.addProperty("minecraft:crafting_table", 1);
        JsonObject type = new JsonObject();
        type.add("blocks", blocks);
        return new BuildingType(name, type);
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
