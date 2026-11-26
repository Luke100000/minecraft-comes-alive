package net.conczin.mca.server.world.data;

import com.google.gson.JsonObject;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomTypeResolverTest {
    private static final Identifier BELL = Identifier.parse("minecraft:bell");
    private static final Identifier WHITE_BED = Identifier.parse("minecraft:white_bed");
    private static final Identifier CHEST = Identifier.parse("minecraft:chest");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void explicitMainRoomAggregatesEnabledContributorsEvenWhenItsOwnPreferenceIsFalse() {
        Fixture fixture = fixture(true, false);

        RoomTypeResolver.Context context = RoomTypeResolver.create(
                fixture.village(), fixture.rooms()).resolve(fixture.main());

        assertTrue(context.isMainRoom());
        assertEquals(List.of(fixture.contributor()), context.contributors());
        assertEquals(List.of(new BlockPos(2, 64, 2)), context.inheritedPoi().get(BELL));
    }

    @Test
    void globalDisableSuppressesContributionWithoutErasingRoomPreference() {
        Fixture fixture = fixture(false, true);

        RoomTypeResolver.Context context = RoomTypeResolver.create(
                fixture.village(), fixture.rooms()).resolve(fixture.main());

        assertTrue(context.inheritedPoi().isEmpty());
        assertTrue(fixture.contributor().contributesToMain());
        assertFalse(fixture.logicalBuilding().inheritanceEnabled());
        assertEquals(fixture.main().getId(), fixture.logicalBuilding().mainRoomId());
    }

    @Test
    void nonContributorNeverSharesPoiWhileBuildingInheritanceIsEnabled() {
        Fixture fixture = fixture(true, true);

        RoomTypeResolver.Context context = RoomTypeResolver.create(
                fixture.village(), fixture.rooms()).resolve(fixture.main());

        assertEquals(List.of(fixture.contributor()), context.contributors());
        assertFalse(context.contributors().contains(fixture.independent()));
    }

    @Test
    void contributingRoomOnAnotherFloorSharesPoiThroughLogicalBuilding() {
        Building main = room(1, true, new BlockPos(1, 64, 1));
        Building upper = room(2, true, new BlockPos(2, 72, 2));
        upper.setStructureId(10);
        upper.setFloorId(1);

        Structure structure = new Structure(10, BlockPos.ZERO, List.of(
                floorWithId(0, 64, 68),
                floorWithId(1, 72, 76)));
        structure.setLogicalBuildingId(10);

        Village village = new Village(1, null);
        registerStructure(village, structure, main);
        registerRoom(village, upper);
        village.refreshLogicalBuildings();

        RoomTypeResolver.Context context = RoomTypeResolver.create(village).resolve(main);

        assertEquals(List.of(upper), context.contributors());
        assertEquals(List.of(new BlockPos(2, 72, 2)), context.inheritedPoi().get(BELL));
        assertEquals(10, village.getLogicalBuildingId(upper.getStructureId()));
    }

    @Test
    void verticallyStackedIndependentBuildingDoesNotContributeAcrossLogicalBuildingBoundary() {
        Building innMain = room(1, true, new BlockPos(1, 64, 1));
        Building innUpper = room(2, true, new BlockPos(2, 72, 2));
        innUpper.setStructureId(10);
        innUpper.setFloorId(1);
        Building restaurantMain = room(3, true, new BlockPos(3, 80, 3));
        restaurantMain.setStructureId(20);

        Structure innStructure = new Structure(10, BlockPos.ZERO, List.of(
                floorWithId(0, 64, 68),
                floorWithId(1, 72, 76)));
        Structure restaurant = new Structure(20, BlockPos.ZERO, List.of(floor(80, 84)));
        innStructure.setLogicalBuildingId(10);
        restaurant.setLogicalBuildingId(20);

        Village village = new Village(1, null);
        registerStructure(village, innStructure, innMain);
        registerRoom(village, innUpper);
        registerStructure(village, restaurant, restaurantMain);
        village.refreshLogicalBuildings();

        RoomTypeResolver.Context context = RoomTypeResolver.create(village).resolve(innMain);

        assertEquals(List.of(innUpper), context.contributors());
        assertFalse(context.contributors().contains(restaurantMain));
        assertFalse(context.inheritedPoi().getOrDefault(BELL, List.of())
                .contains(new BlockPos(3, 80, 3)));
        assertEquals(3, village.getLogicalBuilding(20).orElseThrow().mainRoomId());
    }

    @Test
    void inheritedTwoRoomInnMarksContributingBedroomBedsInvalidForResidents() {
        installInheritanceBuildingTypes();

        BlockPos groundCell = new BlockPos(0, 64, 0);
        BlockPos upstairsBed = new BlockPos(0, 69, 0);
        StructureFloor ground = new StructureFloor(0, 0, 64,
                new FloorGeometry(List.of(new FloorGeometry.Cell(groundCell, 68)), List.of()));
        StructureFloor upper = new StructureFloor(1, 1, 69,
                new FloorGeometry(List.of(new FloorGeometry.Cell(upstairsBed, 73)), List.of()));
        Structure structure = new Structure(10, groundCell, List.of(ground, upper));

        Building taproom = new Building(groundCell);
        taproom.setId(11);
        taproom.setStructureId(10);
        taproom.setFloorId(0);
        taproom.setType("building");
        taproom.setGeometry(groundCell, groundCell.above(3), Set.of(groundCell));
        taproom.addBlock(Blocks.JUKEBOX, groundCell);
        taproom.addBlock(Blocks.SMOKER, groundCell);

        Building bedroom = new Building(upstairsBed);
        bedroom.setId(12);
        bedroom.setStructureId(10);
        bedroom.setFloorId(1);
        bedroom.setType("house");
        bedroom.setContributesToMain(true);
        bedroom.setGeometry(upstairsBed, upstairsBed.above(3), Set.of(upstairsBed));
        bedroom.addBlock(Blocks.WHITE_BED, upstairsBed);
        bedroom.addBlock(Blocks.WHITE_BED, upstairsBed.east());
        bedroom.addBlock(Blocks.WHITE_BED, upstairsBed.south());
        bedroom.addBlock(Blocks.WHITE_BED, upstairsBed.east().south());

        Village village = new Village(1, null);
        village.registerStructure(structure, taproom);
        village.registerRoom(bedroom);

        assertEquals("inn", village.getEffectiveBuildingType(taproom).name());
        assertEquals("inn", village.getEffectiveBuildingType(bedroom).name(),
                "Contributing upstairs Inn bedroom must inherit 'inn' presentation type");
        assertTrue(village.getEffectiveBuildingType(bedroom).noBeds());
        assertFalse(village.isPositionValidBed(upstairsBed),
                "Beds in contributing upstairs Inn room must be excluded from resident bed claims");
        assertEquals(4, village.getEffectiveBlocks(taproom).get(WHITE_BED).size());
        assertEquals(Set.of(groundCell, upstairsBed), village.getEffectiveFloorCells(taproom));
    }

    @Test
    void inheritedTwoRoomStorageExposesContributingVaultChestsInEffectiveBlocks() {
        installInheritanceBuildingTypes();

        BlockPos officeCell = new BlockPos(0, 64, 0);
        BlockPos vaultCell = new BlockPos(4, 64, 0);
        FloorGeometry floorGeo = new FloorGeometry(List.of(
                new FloorGeometry.Cell(officeCell, 68),
                new FloorGeometry.Cell(vaultCell, 68)), List.of());
        Structure structure = new Structure(10, officeCell, List.of(new StructureFloor(0, 0, 64, floorGeo)));

        Building office = new Building(officeCell);
        office.setId(11);
        office.setStructureId(10);
        office.setFloorId(0);
        office.setType("building");
        office.setGeometry(officeCell, officeCell.above(3), Set.of(officeCell));
        office.addBlock(Blocks.LECTERN, officeCell);

        Building vault = new Building(vaultCell);
        vault.setId(12);
        vault.setStructureId(10);
        vault.setFloorId(0);
        vault.setType("building");
        vault.setContributesToMain(true);
        vault.setGeometry(vaultCell, vaultCell.above(3), Set.of(vaultCell));
        vault.addBlock(Blocks.CHEST, vaultCell);
        vault.addBlock(Blocks.CHEST, vaultCell.above());
        vault.addBlock(Blocks.CHEST, vaultCell.above(2));

        Village village = new Village(1, null);
        village.registerStructure(structure, office);
        village.registerRoom(vault);

        List<Building> storages = village.getBuildingsOfType("storage").toList();
        assertEquals(List.of(office), storages);
        assertEquals(3, village.getEffectiveBlocks(office).get(CHEST).size());
    }

    @Test
    void enterBuildingAndVillageModuleResolveInheritedAndExternalBuildings() {
        installInheritanceBuildingTypes();

        BlockPos officeCell = new BlockPos(0, 64, 0);
        BlockPos vaultCell = new BlockPos(4, 64, 0);
        FloorGeometry floorGeo = new FloorGeometry(List.of(
                new FloorGeometry.Cell(officeCell, 68),
                new FloorGeometry.Cell(vaultCell, 68)), List.of());
        Structure structure = new Structure(10, officeCell, List.of(new StructureFloor(0, 0, 64, floorGeo)));

        Building office = new Building(officeCell);
        office.setId(11);
        office.setStructureId(10);
        office.setFloorId(0);
        office.setType("building");
        office.setGeometry(officeCell, officeCell.above(3), Set.of(officeCell));
        office.addBlock(Blocks.LECTERN, officeCell);

        Building vault = new Building(vaultCell);
        vault.setId(12);
        vault.setStructureId(10);
        vault.setFloorId(0);
        vault.setType("building");
        vault.setContributesToMain(true);
        vault.setGeometry(vaultCell, vaultCell.above(3), Set.of(vaultCell));
        vault.addBlock(Blocks.CHEST, vaultCell);
        vault.addBlock(Blocks.CHEST, vaultCell.above());
        vault.addBlock(Blocks.CHEST, vaultCell.above(2));

        ExternalBuilding graveyard = new ExternalBuilding(new BlockPos(20, 64, 20));
        graveyard.setId(13);
        graveyard.setType("graveyard");
        graveyard.addBlock(Blocks.BELL, new BlockPos(20, 64, 20));

        Village village = new Village(1, null);
        village.registerStructure(structure, office);
        village.registerRoom(vault);
        village.registerExternalBuilding(graveyard);

        assertEquals(Set.of("building", "storage", "graveyard"), village.getPresentBuildingTypes());
        assertEquals(List.of(graveyard), village.getBuildingsOfType("graveyard").toList());
    }

    private static void installInheritanceBuildingTypes() {
        JsonObject buildingJson = new JsonObject();
        buildingJson.addProperty("priority", 0);

        JsonObject houseJson = new JsonObject();
        houseJson.addProperty("priority", 1);
        houseJson.addProperty("visible", true);
        JsonObject houseBlocks = new JsonObject();
        houseBlocks.addProperty("minecraft:white_bed", 1);
        houseJson.add("blocks", houseBlocks);

        JsonObject innJson = new JsonObject();
        innJson.addProperty("priority", 5);
        innJson.addProperty("visible", true);
        innJson.addProperty("noBeds", true);
        JsonObject innBlocks = new JsonObject();
        innBlocks.addProperty("minecraft:jukebox", 1);
        innBlocks.addProperty("minecraft:smoker", 1);
        innBlocks.addProperty("minecraft:white_bed", 4);
        innJson.add("blocks", innBlocks);

        JsonObject storageJson = new JsonObject();
        storageJson.addProperty("priority", 4);
        storageJson.addProperty("visible", true);
        JsonObject storageBlocks = new JsonObject();
        storageBlocks.addProperty("minecraft:lectern", 1);
        storageBlocks.addProperty("minecraft:chest", 3);
        storageJson.add("blocks", storageBlocks);

        JsonObject graveyardJson = new JsonObject();
        graveyardJson.addProperty("grouped", true);
        graveyardJson.addProperty("visible", true);

        BuildingTypes.getInstance().setBuildingTypes(Map.of(
                "building", new BuildingType("building", buildingJson),
                "house", new BuildingType("house", houseJson),
                "inn", new BuildingType("inn", innJson),
                "storage", new BuildingType("storage", storageJson),
                "graveyard", new BuildingType("graveyard", graveyardJson)));
    }

    private static Fixture fixture(boolean inheritanceEnabled, boolean mainPreference) {
        Building main = room(1, true, new BlockPos(1, 64, 1));
        main.setContributesToMain(mainPreference);
        Building contributor = room(2, true, new BlockPos(2, 64, 2));
        Building independent = room(3, false, new BlockPos(3, 64, 3));
        Structure structure = new Structure(10, BlockPos.ZERO, List.of(floor(64, 68)));
        structure.setLogicalBuildingId(10);
        Village village = new Village(1, null);
        registerStructure(village, structure, main);
        registerRoom(village, contributor);
        registerRoom(village, independent);
        LogicalBuilding logical = village.getLogicalBuilding(10).orElseThrow();
        logical.setInheritanceEnabled(inheritanceEnabled);
        return new Fixture(village, logical, List.of(main, contributor, independent), main, contributor, independent);
    }

    private static Building room(int id, boolean contributes, BlockPos poi) {
        Building room = new Building(BlockPos.ZERO);
        room.setId(id);
        room.setStructureId(10);
        room.setFloorId(0);
        room.setContributesToMain(contributes);
        room.addBlock(Blocks.BELL, poi);
        return room;
    }

    private static StructureFloor floor(int anchorY, int ceilingY) {
        return floorWithId(0, anchorY, ceilingY);
    }

    private static StructureFloor floorWithId(int id, int anchorY, int ceilingY) {
        return TestStructureFloors.create(id, anchorY, ceilingY, id,
                TestFloorFootprint.fromFootprint(anchorY, Set.of(new BlockPos(0, anchorY, 0))));
    }

    private static void registerStructure(Village village, Structure structure, Building room) {
        ensureRoomOwnership(structure, room);
        village.registerStructure(structure, room);
    }

    private static void registerRoom(Village village, Building room) {
        Structure structure = village.getStructure(room.getStructureId()).orElseThrow();
        ensureRoomOwnership(structure, room);
        village.registerRoom(room);
    }

    private static void ensureRoomOwnership(Structure structure, Building room) {
        if (!room.getFloorCells().isEmpty()) return;
        StructureFloor floor = structure.getFloor(room.getFloorId()).orElseThrow();
        BlockPos cell = floor.geometry().cells().iterator().next().feet();
        room.setGeometry(cell, cell, Set.of(cell));
    }

    private record Fixture(Village village,
                           LogicalBuilding logicalBuilding,
                           List<Building> rooms,
                           Building main,
                           Building contributor,
                           Building independent) {
    }
}
