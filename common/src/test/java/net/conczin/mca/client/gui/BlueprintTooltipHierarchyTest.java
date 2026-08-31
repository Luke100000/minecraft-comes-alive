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
import net.minecraft.locale.Language;
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
        buildingTypes.setBuildingTypes(Map.of(
                "house", new BuildingType("house", new JsonObject()),
                "bedroom", new BuildingType("bedroom", new JsonObject())
        ));
        previousLanguage = Language.getInstance();
        Language.inject(new TestLanguage(previousLanguage, Map.of(
                "buildingType.house", "House",
                "buildingType.bedroom", "Bedroom",
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
        village.registerRoom(upper);
        return new Fixture(village, ground, upper);
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
