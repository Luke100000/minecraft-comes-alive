package net.conczin.mca.client.gui;

import com.google.gson.JsonObject;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.BuildingFloorRegion;
import net.conczin.mca.server.world.data.RoomTypeResolver;
import net.conczin.mca.server.world.data.Structure;
import net.conczin.mca.server.world.data.StructureFloor;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintTooltipHierarchyTest {
    private Map<String, BuildingType> previousBuildingTypes;
    private Language previousLanguage;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void installBuildingTypes() {
        BuildingTypes buildingTypes = BuildingTypes.getInstance();
        previousBuildingTypes = buildingTypes.getBuildingTypes();
        JsonObject workshopDefinition = new JsonObject();
        JsonObject workshopBlocks = new JsonObject();
        workshopBlocks.addProperty("minecraft:jukebox", 2);
        workshopDefinition.add("blocks", workshopBlocks);
        buildingTypes.setBuildingTypes(Map.of(
                "house", new BuildingType("house", new JsonObject()),
                "bedroom", new BuildingType("bedroom", new JsonObject()),
                "workshop", new BuildingType("workshop", workshopDefinition)
        ));
        previousLanguage = Language.getInstance();
        Language.inject(new TestLanguage(previousLanguage, Map.of(
                "buildingType.house", "House",
                "buildingType.bedroom", "Bedroom",
                "buildingType.workshop", "Workshop",
                "gui.blueprint.roomTooltip.resident", "Resident: %1$s",
                "gui.blueprint.roomTooltip.residents", "Residents: %1$s"
        )));
    }

    @AfterEach
    void restoreBuildingTypes() {
        BuildingTypes.getInstance().setBuildingTypes(previousBuildingTypes);
        Language.inject(previousLanguage);
    }

    @Test
    void wholeBuildingTooltipShowsBuildingFloorRoomDetailHierarchy() throws Exception {
        Fixture fixture = fixture();
        BlueprintTooltipFactory factory = BlueprintTooltipFactory.create(
                fixture.village(), RoomTypeResolver.create(fixture.village()));

        List<String> lines = factory.tooltip(fixture.groundRoom(), null, true).stream()
                .map(component -> component.getString())
                .toList();

        assertEquals(List.of(0, 2, 2, 2, 4), lines.stream()
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
    void overlappingFloorsInSameBuildingRemainTooltipAlternatives() {
        Building ground = room(1, 10, 0, "house");
        Building basement = room(2, 10, 1, "bedroom");
        BlueprintMapRenderer.HoverTarget groundTarget = new BlueprintMapRenderer.HoverTarget(
                ground, 0, false, 10, 64);
        BlueprintMapRenderer.HoverTarget basementTarget = new BlueprintMapRenderer.HoverTarget(
                basement, -1, false, 10, 60);

        List<BlueprintMapRenderer.HoverTarget> targets = BlueprintScreen.tooltipTargets(
                List.of(basementTarget, groundTarget), 10);

        assertEquals(List.of(groundTarget, basementTarget), targets);
    }

    @Test
    void soleTitleMatchingRoomDoesNotRepeatBuildingType() throws Exception {
        Fixture fixture = fixture();
        fixture.groundRoom().addBlock(Blocks.YELLOW_BED, BlockPos.ZERO);
        BlueprintTooltipFactory factory = BlueprintTooltipFactory.create(
                fixture.village(), RoomTypeResolver.create(fixture.village()));

        List<String> lines = factory.tooltip(fixture.groundRoom(), 0, true).stream()
                .map(component -> component.getString())
                .toList();

        assertEquals(1L, lines.stream().filter("House"::equals).count());
        assertEquals(List.of(0, 2, 2, 4), lines.stream()
                .map(BlueprintTooltipHierarchyTest::leadingSpaces)
                .toList());
        assertTrue(lines.stream().anyMatch(line -> line.contains("1 × Yellow Bed")));
    }

    @Test
    void residentLabelsAreExplicitAndPluralized() {
        assertEquals("Resident: Hye-Sook",
                BlueprintTooltipFactory.residentLabel(List.of("Hye-Sook")).getString());
        assertEquals("Residents: Hye-Sook, Alex",
                BlueprintTooltipFactory.residentLabel(List.of("Hye-Sook", "Alex")).getString());
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

    @Test
    void aggregateTooltipIncludesRegisteredRoomEvenWhenTypeRequirementsAreIncomplete() throws Exception {
        Fixture fixture = fixture();
        Building incomplete = room(3, 10, 1, "workshop");
        setRoomGeometry(incomplete, new BlockPos(0, 68, 0));
        incomplete.addBlock(Blocks.JUKEBOX, new BlockPos(0, 69, 0));
        assertFalse(incomplete.isComplete());
        fixture.village().registerRoom(incomplete);
        assertTrue(fixture.village().setRoomContributesToMain(incomplete, false));
        BlueprintTooltipFactory factory = BlueprintTooltipFactory.create(
                fixture.village(), RoomTypeResolver.create(fixture.village()));

        List<String> lines = factory.tooltip(fixture.groundRoom(), null, true).stream()
                .map(Component::getString)
                .toList();

        assertTrue(lines.stream().anyMatch(line -> line.contains("1 × Jukebox")));
    }

    private static Fixture fixture() throws Exception {
        Village village = new Village(1, null);
        Structure structure = new Structure(
                10,
                BlockPos.ZERO,
                List.of(
                        floor(0, 64, 67, 0),
                        floor(1, 68, 71, 1)
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
        village.registerRoom(upper);
        return new Fixture(village, ground, upper);
    }

    private static StructureFloor floor(int id, int anchorY, int ceilingY, int floorNumber) throws Exception {
        return new StructureFloor(id, anchorY, ceilingY, floorNumber,
                region(anchorY));
    }

    private static BuildingFloorRegion region(int anchorY) throws Exception {
        Method fromFootprint = BuildingFloorRegion.class.getDeclaredMethod(
                "fromFootprint", int.class, java.util.Collection.class);
        fromFootprint.setAccessible(true);
        return (BuildingFloorRegion) fromFootprint.invoke(null, anchorY,
                Set.of(new BlockPos(0, anchorY, 0)));
    }

    private static Building room(int id, int structureId, int floorId, String type) {
        Building room = new Building(BlockPos.ZERO);
        room.setId(id);
        room.setStructureId(structureId);
        room.setFloorId(floorId);
        room.setType(type);
        room.setTypeForced(true);
        return room;
    }

    private static void setRoomGeometry(Building room, BlockPos cell) throws Exception {
        Method setGeometry = Building.class.getDeclaredMethod(
                "setGeometry", BlockPos.class, BlockPos.class, java.util.Collection.class);
        setGeometry.setAccessible(true);
        setGeometry.invoke(room, cell, cell, Set.of(cell));
    }

    private static int leadingSpaces(String value) {
        int spaces = 0;
        while (spaces < value.length() && value.charAt(spaces) == ' ') spaces++;
        return spaces;
    }

    private record Fixture(Village village, Building groundRoom, Building upperRoom) {
    }

    private static final class TestLanguage extends Language {
        private final Language delegate;
        private final Map<String, String> overrides;

        private TestLanguage(Language delegate, Map<String, String> overrides) {
            this.delegate = delegate;
            this.overrides = overrides;
        }

        @Override
        public String getOrDefault(String key, String fallback) {
            return overrides.getOrDefault(key, delegate.getOrDefault(key, fallback));
        }

        @Override
        public boolean has(String key) {
            return overrides.containsKey(key) || delegate.has(key);
        }

        @Override
        public boolean isDefaultRightToLeft() {
            return delegate.isDefaultRightToLeft();
        }

        @Override
        public FormattedCharSequence getVisualOrder(FormattedText text) {
            return delegate.getVisualOrder(text);
        }
    }
}
