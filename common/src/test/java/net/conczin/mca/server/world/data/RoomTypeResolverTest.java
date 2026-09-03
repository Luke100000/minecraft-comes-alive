package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomTypeResolverTest {
    private static final ResourceLocation BELL = ResourceLocation.parse("minecraft:bell");

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
        upper.setStructureId(11);

        Structure groundStructure = new Structure(10, BlockPos.ZERO, List.of(floor(64, 68)));
        Structure upperStructure = new Structure(11, BlockPos.ZERO, List.of(floor(72, 76)));
        groundStructure.setLogicalBuildingId(10);
        upperStructure.setLogicalBuildingId(10);

        Village village = new Village(1, null);
        village.registerStructure(groundStructure, main);
        village.registerStructure(upperStructure, upper);
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
        innUpper.setStructureId(11);
        Building restaurantMain = room(3, true, new BlockPos(3, 80, 3));
        restaurantMain.setStructureId(20);

        Structure innGround = new Structure(10, BlockPos.ZERO, List.of(floor(64, 68)));
        Structure innUpperStructure = new Structure(11, BlockPos.ZERO, List.of(floor(72, 76)));
        Structure restaurant = new Structure(20, BlockPos.ZERO, List.of(floor(80, 84)));
        innGround.setLogicalBuildingId(10);
        innUpperStructure.setLogicalBuildingId(10);
        restaurant.setLogicalBuildingId(20);

        Village village = new Village(1, null);
        village.registerStructure(innGround, innMain);
        village.registerStructure(innUpperStructure, innUpper);
        village.registerStructure(restaurant, restaurantMain);
        village.refreshLogicalBuildings();

        RoomTypeResolver.Context context = RoomTypeResolver.create(village).resolve(innMain);

        assertEquals(List.of(innUpper), context.contributors());
        assertFalse(context.contributors().contains(restaurantMain));
        assertFalse(context.inheritedPoi().getOrDefault(BELL, List.of())
                .contains(new BlockPos(3, 80, 3)));
        assertEquals(3, village.getLogicalBuilding(20).orElseThrow().mainRoomId());
    }

    private static Fixture fixture(boolean inheritanceEnabled, boolean mainPreference) {
        Building main = room(1, true, new BlockPos(1, 64, 1));
        main.setContributesToMain(mainPreference);
        Building contributor = room(2, true, new BlockPos(2, 64, 2));
        Building independent = room(3, false, new BlockPos(3, 64, 3));
        Structure structure = new Structure(10, BlockPos.ZERO, List.of(floor(64, 68)));
        structure.setLogicalBuildingId(10);
        Village village = new Village(1, null);
        village.registerStructure(structure, main);
        village.registerRoom(contributor);
        village.registerRoom(independent);
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
        room.getBlocks().put(BELL, List.of(poi));
        return room;
    }

    private static StructureFloor floor(int anchorY, int ceilingY) {
        return new StructureFloor(0, anchorY, ceilingY,
                BuildingFloorRegion.fromFootprint(anchorY, Set.of(new BlockPos(0, anchorY, 0))));
    }

    private record Fixture(Village village,
                           LogicalBuilding logicalBuilding,
                           List<Building> rooms,
                           Building main,
                           Building contributor,
                           Building independent) {
    }
}
