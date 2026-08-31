package net.conczin.mca.client.gui;

import com.google.gson.JsonObject;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Structure;
import net.conczin.mca.server.world.data.StructureFloor;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlueprintMapGeometryTest {
    private Map<String, BuildingType> previousBuildingTypes;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void installBuildingTypes() {
        BuildingTypes types = BuildingTypes.getInstance();
        previousBuildingTypes = types.getBuildingTypes();
        types.setBuildingTypes(Map.of(
                "house", new BuildingType("house", new JsonObject())
        ));
    }

    @AfterEach
    void restoreBuildingTypes() {
        BuildingTypes.getInstance().setBuildingTypes(previousBuildingTypes);
    }

    @Test
    void buildingShapeIsOneCellPaddingAroundRoomsFromEveryFloor() {
        Set<BlueprintMapFootprint.Cell> ground = Set.of(
                new BlueprintMapFootprint.Cell(0, 0),
                new BlueprintMapFootprint.Cell(1, 0),
                new BlueprintMapFootprint.Cell(0, 1));
        Set<BlueprintMapFootprint.Cell> upper = Set.of(
                new BlueprintMapFootprint.Cell(2, 0),
                new BlueprintMapFootprint.Cell(2, 1));
        LinkedHashSet<BlueprintMapFootprint.Cell> union = new LinkedHashSet<>(ground);
        union.addAll(upper);

        BlueprintMapGeometry.BuildingShape shape =
                BlueprintMapGeometry.buildBuildingShape(List.of(ground, upper));
        Set<BlueprintMapFootprint.Cell> expectedOutline = BlueprintMapFootprint.expand(union, 1);
        LinkedHashSet<BlueprintMapFootprint.Cell> expectedShell = new LinkedHashSet<>(expectedOutline);
        expectedShell.removeAll(union);

        assertEquals(expectedOutline, shape.outline().cells());
        assertEquals(expectedShell, shape.shell().cells());
    }

    @Test
    void buildingShapeIsEmptyWithoutRegisteredRooms() {
        BlueprintMapGeometry.BuildingShape shape = BlueprintMapGeometry.buildBuildingShape(List.of());

        assertEquals(Set.of(), shape.outline().cells());
        assertEquals(Set.of(), shape.shell().cells());
    }

    @Test
    void buildingOutlineExcludesBasementButShadeTracksSelectedFloor() throws Exception {
        Village village = new Village(1, null);
        Structure groundStructure = structure(10, 10, 64, 0);
        Building groundRoom = room(1, 10, 0, new BlockPos(0, 64, 0));
        registerStructure(village, groundStructure, groundRoom);

        Structure upperStructure = structure(12, 10, 68, 1);
        Building upperRoom = room(3, 12, 0, new BlockPos(2, 68, 0));
        village.getStructures().put(12, upperStructure);
        village.getBuildings().put(3, upperRoom);

        Structure basementStructure = structure(11, 10, 60, -1);
        Building basementRoom = room(2, 11, 0, new BlockPos(1, 60, 0));
        village.getStructures().put(11, basementStructure);
        village.getBuildings().put(2, basementRoom);

        BlueprintMapGeometry map = BlueprintMapGeometry.build(village, null);
        BlueprintMapGeometry.MapGeometry basement = map.get(-1);
        BlueprintMapGeometry.MapGeometry ground = map.get(0);

        Set<BlueprintMapFootprint.Cell> expectedOutline = Set.of(
                new BlueprintMapFootprint.Cell(-1, -1),
                new BlueprintMapFootprint.Cell(-1, 0),
                new BlueprintMapFootprint.Cell(-1, 1),
                new BlueprintMapFootprint.Cell(0, -1),
                new BlueprintMapFootprint.Cell(0, 0),
                new BlueprintMapFootprint.Cell(0, 1),
                new BlueprintMapFootprint.Cell(1, -1),
                new BlueprintMapFootprint.Cell(1, 0),
                new BlueprintMapFootprint.Cell(1, 1),
                new BlueprintMapFootprint.Cell(2, -1),
                new BlueprintMapFootprint.Cell(2, 0),
                new BlueprintMapFootprint.Cell(2, 1),
                new BlueprintMapFootprint.Cell(3, -1),
                new BlueprintMapFootprint.Cell(3, 0),
                new BlueprintMapFootprint.Cell(3, 1));

        assertEquals(expectedOutline, basement.structureLayers().getFirst().outlineCells());
        assertEquals(expectedOutline, ground.structureLayers().getFirst().outlineCells());
        assertEquals(expectedOutline.size() - 1, basement.structureLayers().getFirst().shellCells().size());
        assertEquals(expectedOutline.size() - 1, ground.structureLayers().getFirst().shellCells().size());
        assertEquals(false, basement.structureLayers().getFirst().shellCells()
                .contains(new BlueprintMapFootprint.Cell(1, 0)));
        assertEquals(true, basement.structureLayers().getFirst().shellCells()
                .contains(new BlueprintMapFootprint.Cell(0, 0)));
        assertEquals(false, ground.structureLayers().getFirst().shellCells()
                .contains(new BlueprintMapFootprint.Cell(0, 0)));
        assertEquals(true, ground.structureLayers().getFirst().shellCells()
                .contains(new BlueprintMapFootprint.Cell(1, 0)));
    }

    private static Structure structure(int id, int buildingId, int y, int floorNumber) throws Exception {
        Structure structure = new Structure(
                id,
                new BlockPos(0, y, 0),
                new BlockPos(0, y, 0),
                new BlockPos(0, y + 2, 0),
                List.of(new StructureFloor(0, y, y + 3, floorNumber, null)));
        Method setter = Structure.class.getDeclaredMethod("setLogicalBuildingId", int.class);
        setter.setAccessible(true);
        setter.invoke(structure, buildingId);
        return structure;
    }

    private static Building room(int id, int structureId, int floorId, BlockPos source) {
        Building room = new Building(source);
        room.setId(id);
        room.setStructureId(structureId);
        room.setFloorId(floorId);
        room.setType("house");
        room.setTypeForced(true);
        return room;
    }

    private static void registerStructure(Village village, Structure structure, Building room) throws Exception {
        Method register = Village.class.getDeclaredMethod("registerStructure", Structure.class, Building.class);
        register.setAccessible(true);
        register.invoke(village, structure, room);
    }
}
