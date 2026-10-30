package net.conczin.mca.server.world.data;

import com.google.gson.JsonObject;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.Tasks;
import net.conczin.mca.resources.data.tasks.BlockingTask;
import net.conczin.mca.resources.data.tasks.BuildingTask;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageRoomTypeQueryTest {
    private Map<String, BuildingType> previousTypes;
    private Tasks previousTasks;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void installTypes() {
        previousTasks = Tasks.getInstance();
        previousTypes = BuildingTypes.getInstance().getBuildingTypes();
        BuildingTypes.getInstance().setBuildingTypes(Map.of(
                "house", type("house", 0, 0),
                "building", type("building", 0, 0),
                "armory", type("armory", 10, 2),
                "blacksmith", type("blacksmith", 20, 3)));
    }

    @AfterEach
    void restoreTypes() throws ReflectiveOperationException {
        BuildingTypes.getInstance().setBuildingTypes(previousTypes);
        var instance = Tasks.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        instance.set(null, previousTasks);
    }

    @Test
    void rankRequirementsClassifyInheritedRoomsOncePerEvaluation() {
        PoiCountingRoom main = room(1, "house");
        PoiCountingRoom child = room(2, "building");
        Village village = village(main, child);
        Tasks tasks = rankTasks();
        tasks.tasks.put(Rank.MONARCH, List.of(new BuildingTask("armory"), new BuildingTask("building")));
        main.poiReads = 0;

        assertEquals(Rank.MONARCH, Tasks.getRank(village, null));
        assertEquals(1, main.poiReads, "each building requirement reclassified the same main room");

        child.setContributesToMain(false);
        assertEquals(Rank.OUTLAW, Tasks.getRank(village, null), "next rank check reused stale room contributions");
        child.setContributesToMain(true);
        assertEquals(Rank.MONARCH, Tasks.getRank(village, null));
        BuildingTypes.getInstance().setBuildingTypes(Map.of(
                "house", type("house", 0, 0), "building", type("building", 0, 0),
                "armory", type("armory", 10, 3)));
        assertEquals(Rank.OUTLAW, Tasks.getRank(village, null), "next rank check ignored reloaded definitions");
    }

    @Test
    void failedEarlierRequirementDoesNotClassifyRooms() {
        PoiCountingRoom main = room(1, "house");
        Village village = village(main, room(2, "building"));
        Tasks tasks = rankTasks();
        tasks.tasks.put(Rank.MONARCH, List.of(blockingTask(), new BuildingTask("armory")));
        main.poiReads = 0;

        assertEquals(Rank.OUTLAW, Tasks.getRank(village, null));
        assertEquals(0, main.poiReads, "building classification ran before a failing prerequisite");
    }

    @Test
    void rankRequirementsKeepGroupedSiteCompletionAndRoomSemantics() {
        Map<String, BuildingType> types = new HashMap<>(BuildingTypes.getInstance().getBuildingTypes());
        JsonObject grouped = new JsonObject();
        grouped.addProperty("grouped", true);
        JsonObject blocks = new JsonObject();
        blocks.addProperty("minecraft:bell", 2);
        grouped.add("blocks", blocks);
        types.put("graveyard", new BuildingType("graveyard", grouped));
        BuildingTypes.getInstance().setBuildingTypes(types);
        PoiCountingRoom main = room(1, "graveyard");
        main.setTypeForced(true);
        Village village = village(main);
        Tasks tasks = rankTasks();
        tasks.tasks.put(Rank.MONARCH, List.of(new BuildingTask("graveyard")));
        assertEquals(Rank.OUTLAW, Tasks.getRank(village, null), "a functional room satisfied a grouped requirement");

        ExternalBuilding site = new ExternalBuilding(BlockPos.ZERO);
        site.setId(20);
        site.setType("graveyard");
        site.addBlock(Blocks.BELL, new BlockPos(20, 64, 0));
        village.registerExternalBuilding(site);
        assertEquals(Rank.OUTLAW, Tasks.getRank(village, null), "an incomplete external site satisfied its requirement");
        site.addBlock(Blocks.BELL, new BlockPos(21, 64, 0));
        assertEquals(Rank.MONARCH, Tasks.getRank(village, null));
        site.setType("armory");
        tasks.tasks.put(Rank.MONARCH, List.of(new BuildingTask("armory")));
        assertEquals(Rank.OUTLAW, Tasks.getRank(village, null), "an external site satisfied a functional-room requirement");
    }

    private static Tasks rankTasks() {
        Tasks tasks = new Tasks();
        for (Rank rank : Rank.values()) tasks.tasks.put(rank, List.of(blockingTask()));
        return tasks;
    }

    private static BlockingTask blockingTask() {
        JsonObject json = new JsonObject();
        json.addProperty("id", "unmet");
        return new BlockingTask(json);
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
