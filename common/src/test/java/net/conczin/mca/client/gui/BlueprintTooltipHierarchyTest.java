package net.conczin.mca.client.gui;

import com.google.gson.JsonObject;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.RoomTypeResolver;
import net.conczin.mca.server.world.data.Structure;
import net.conczin.mca.server.world.data.StructureFloor;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BlueprintTooltipHierarchyTest {
    private Map<String, BuildingType> previousBuildingTypes;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void installBuildingTypes() {
        BuildingTypes buildingTypes = BuildingTypes.getInstance();
        previousBuildingTypes = buildingTypes.getBuildingTypes();
        buildingTypes.setBuildingTypes(Map.of(
                "house", new BuildingType("house", new JsonObject()),
                "bedroom", new BuildingType("bedroom", new JsonObject())
        ));
    }

    @AfterEach
    void restoreBuildingTypes() {
        BuildingTypes.getInstance().setBuildingTypes(previousBuildingTypes);
    }

    @Test
    void wholeBuildingTooltipShowsBuildingFloorRoomDetailHierarchy() throws Exception {
        Fixture fixture = fixture();
        BlueprintTooltipFactory factory = BlueprintTooltipFactory.create(
                fixture.village(), RoomTypeResolver.create(fixture.village()));

        List<String> lines = factory.tooltip(fixture.groundRoom(), null, true).stream()
                .map(component -> component.getString())
                .toList();

        assertEquals(List.of(0, 2, 4, 2, 4, 6, 8), lines.stream()
                .map(BlueprintTooltipHierarchyTest::leadingSpaces)
                .toList());
    }

    @Test
    void directRoomTooltipKeepsRoomAsRootAndDetailsOneLevelBelow() throws Exception {
        Fixture fixture = fixture();
        BlueprintTooltipFactory factory = BlueprintTooltipFactory.create(
                fixture.village(), RoomTypeResolver.create(fixture.village()));

        List<String> lines = factory.tooltip(fixture.upperRoom(), 1, false).stream()
                .map(component -> component.getString())
                .toList();

        assertEquals(List.of(0, 2, 2, 2, 4), lines.stream()
                .map(BlueprintTooltipHierarchyTest::leadingSpaces)
                .toList());
    }

    @Test
    void alsoHereTargetsAreSiblingEntriesInsteadOfRoomDetails() throws Exception {
        Fixture fixture = fixture();
        BlueprintTooltipFactory factory = BlueprintTooltipFactory.create(
                fixture.village(), RoomTypeResolver.create(fixture.village()));

        String compact = factory.compactTooltip(fixture.upperRoom(), 1, 1).getString();

        assertFalse(compact.startsWith(" "));
    }

    @Test
    void concreteRoomHoverWinsOverSameBuildingShellInAllFloorsMode() throws Exception {
        Fixture fixture = fixture();
        BlueprintMapRenderer.HoverTarget roomTarget = new BlueprintMapRenderer.HoverTarget(
                fixture.upperRoom(), 1, false, 10, 68);
        List<BlueprintMapRenderer.HoverTarget> targets = new ArrayList<>(List.of(roomTarget));

        Method addStructureHover = BlueprintMapRenderer.class.getDeclaredMethod(
                "addStructureHover", List.class, Building.class, int.class, Integer.class, int.class);
        addStructureHover.setAccessible(true);
        addStructureHover.invoke(null, targets, fixture.groundRoom(), 10, null, 64);

        assertEquals(List.of(roomTarget), targets);
    }

    private static Fixture fixture() throws Exception {
        Village village = new Village(1, null);
        Structure structure = new Structure(
                10,
                BlockPos.ZERO,
                BlockPos.ZERO,
                BlockPos.ZERO,
                List.of(
                        new StructureFloor(0, 64, 67, 0, null),
                        new StructureFloor(1, 68, 71, 1, null)
                )
        );
        Method setLogicalBuildingId = Structure.class.getDeclaredMethod("setLogicalBuildingId", int.class);
        setLogicalBuildingId.setAccessible(true);
        setLogicalBuildingId.invoke(structure, 10);

        Building ground = room(1, 10, 0, "house");
        Building upper = room(2, 10, 1, "bedroom");
        upper.addBlock(Blocks.CRAFTING_TABLE, BlockPos.ZERO);

        Method registerStructure = Village.class.getDeclaredMethod(
                "registerStructure", Structure.class, Building.class);
        registerStructure.setAccessible(true);
        registerStructure.invoke(village, structure, ground);
        village.getBuildings().put(upper.getId(), upper);
        return new Fixture(village, ground, upper);
    }

    private static Building room(int id, int structureId, int floorId, String type) {
        Building room = new Building(BlockPos.ZERO);
        room.setId(id);
        room.setStructureId(structureId);
        room.setFloorId(floorId);
        room.setType(type);
        return room;
    }

    private static int leadingSpaces(String value) {
        int spaces = 0;
        while (spaces < value.length() && value.charAt(spaces) == ' ') spaces++;
        return spaces;
    }

    private record Fixture(Village village, Building groundRoom, Building upperRoom) {
    }
}
