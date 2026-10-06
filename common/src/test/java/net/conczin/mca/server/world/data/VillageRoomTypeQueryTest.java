package net.conczin.mca.server.world.data;

import com.google.gson.JsonObject;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageRoomTypeQueryTest {
    private Map<String, BuildingType> previousTypes;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void installTypes() {
        previousTypes = BuildingTypes.getInstance().getBuildingTypes();
        BuildingTypes.getInstance().setBuildingTypes(Map.of(
                "house", type("house", 0, 0),
                "building", type("building", 0, 0),
                "armory", type("armory", 10, 2),
                "blacksmith", type("blacksmith", 20, 3)));
    }

    @AfterEach
    void restoreTypes() {
        BuildingTypes.getInstance().setBuildingTypes(previousTypes);
    }

    @Test
    void standaloneRoomTypeQueryDoesNotReadPoi() {
        PoiCountingRoom main = room(1, "house");
        Village village = village(main);
        main.poiReads = 0;

        assertTrue(village.hasBuilding("house"));
        assertFalse(village.hasBuilding("armory"));
        assertEquals(0, main.poiReads, "type-only queries copied a standalone room's POIs");
    }

    @Test
    void forcedMainTypeQueryDoesNotReadOwnOrContributingPoi() {
        PoiCountingRoom main = room(1, "blacksmith");
        main.setTypeForced(true);
        PoiCountingRoom child = room(2, "building");
        Village village = village(main, child);
        main.poiReads = child.poiReads = 0;

        assertEquals(List.of(main), village.getBuildingsOfType("blacksmith").toList());
        assertEquals(0, main.poiReads);
        assertEquals(0, child.poiReads);
    }

    @Test
    void disabledInheritanceTypeQueryDoesNotReadPoi() {
        PoiCountingRoom main = room(1, "house");
        PoiCountingRoom child = room(2, "building");
        Village village = village(main, child);
        village.setBuildingInheritanceEnabled(main, false);
        main.poiReads = child.poiReads = 0;

        assertFalse(village.hasBuilding("armory"));
        assertEquals(0, main.poiReads);
        assertEquals(0, child.poiReads);
    }

    @Test
    void nonContributingRoomDoesNotCausePoiAggregation() {
        PoiCountingRoom main = room(1, "house");
        PoiCountingRoom child = room(2, "building");
        child.setContributesToMain(false);
        Village village = village(main, child);
        main.poiReads = child.poiReads = 0;

        assertFalse(village.hasBuilding("armory"));
        assertEquals(List.of(child), village.getBuildingsOfType("building").toList());
        assertEquals(0, main.poiReads);
        assertEquals(0, child.poiReads);
    }

    @Test
    void mainRoomEffectiveTypeTracksContributionChangesBetweenQueries() {
        PoiCountingRoom main = room(1, "house");
        PoiCountingRoom child = room(2, "building");
        Village village = village(main, child);

        assertEquals(List.of(main), village.getBuildingsOfType("armory").toList());
        assertEquals(List.of(child), village.getBuildingsOfType("building").toList());
        child.setContributesToMain(false);
        assertFalse(village.hasBuilding("armory"));
        child.setContributesToMain(true);
        assertTrue(village.hasBuilding("armory"));
        child.removeBlock(Blocks.BELL, new BlockPos(2, 64, 0));
        assertFalse(village.hasBuilding("armory"));
        child.addBlock(Blocks.BELL, new BlockPos(2, 64, 0));
        assertTrue(village.hasBuilding("armory"));
        village.setBuildingInheritanceEnabled(main, false);
        assertFalse(village.hasBuilding("armory"));
    }

    @Test
    void forcedTypeChangeIsVisibleOnNextQuery() {
        PoiCountingRoom main = room(1, "armory");
        main.setTypeForced(true);
        Village village = village(main);

        assertTrue(village.hasBuilding("armory"));
        main.setType("blacksmith");
        assertFalse(village.hasBuilding("armory"));
        assertTrue(village.hasBuilding("blacksmith"));
    }

    @Test
    void mainRoomChangeMovesInheritedClassificationToNewOwner() {
        PoiCountingRoom main = room(1, "house");
        PoiCountingRoom child = room(2, "building");
        Village village = village(main, child);

        assertEquals(List.of(main), village.getBuildingsOfType("armory").toList());
        assertTrue(village.setMainRoom(child));
        assertEquals(List.of(child), village.getBuildingsOfType("armory").toList());
        assertEquals(List.of(main), village.getBuildingsOfType("house").toList());
    }

    @Test
    void currentPhysicalRoomUsesItsResolvedEffectiveType() {
        PoiCountingRoom main = room(1, "house");
        PoiCountingRoom child = room(2, "building");
        Village village = village(main, child);

        assertTrue(village.isInBuildingOfType(new BlockPos(0, 64, 0), "armory"));
        assertFalse(village.isInBuildingOfType(new BlockPos(0, 64, 0), "house"));
        assertFalse(village.isInBuildingOfType(new BlockPos(40, 64, 0), "armory"));

        village.setBuildingInheritanceEnabled(main, false);
        assertFalse(village.isInBuildingOfType(new BlockPos(0, 64, 0), "armory"));
        assertTrue(village.isInBuildingOfType(new BlockPos(0, 64, 0), "house"));
    }

    private static BuildingType type(String name, int priority, int bells) {
        JsonObject definition = new JsonObject();
        definition.addProperty("priority", priority);
        JsonObject blocks = new JsonObject();
        if (bells > 0) blocks.addProperty("minecraft:bell", bells);
        definition.add("blocks", blocks);
        return new BuildingType(name, definition);
    }

    private static PoiCountingRoom room(int id, String type) {
        PoiCountingRoom room = new PoiCountingRoom();
        room.setId(id);
        room.setStructureId(10);
        room.setFloorId(0);
        room.setType(type);
        room.setGeometry(BlockPos.ZERO, BlockPos.ZERO, Set.of(new BlockPos(0, 64, 0)));
        room.addBlock(Blocks.BELL, new BlockPos(id, 64, 0));
        return room;
    }

    private static Village village(PoiCountingRoom main, PoiCountingRoom... children) {
        StructureFloor floor = TestStructureFloors.create(0, 64, 68,
                TestFloorFootprint.fromFootprint(64, Set.of(new BlockPos(0, 64, 0))));
        Village village = new Village(1, null);
        village.registerStructure(new Structure(10, BlockPos.ZERO, List.of(floor)), main);
        for (PoiCountingRoom child : children) village.registerRoom(child);
        return village;
    }

    private static final class PoiCountingRoom extends Building {
        private int poiReads;

        private PoiCountingRoom() {
            super(BlockPos.ZERO);
        }

        @Override
        public Map<ResourceLocation, List<BlockPos>> getBlocks() {
            poiReads++;
            return super.getBlocks();
        }
    }
}
