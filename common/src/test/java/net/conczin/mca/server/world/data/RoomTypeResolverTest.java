package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    private static Fixture fixture(boolean inheritanceEnabled, boolean mainPreference) {
        Building main = room(1, true, new BlockPos(1, 64, 1));
        main.setContributesToMain(mainPreference);
        Building contributor = room(2, true, new BlockPos(2, 64, 2));
        Building independent = room(3, false, new BlockPos(3, 64, 3));
        Structure structure = new Structure(10, BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO,
                List.of(new StructureFloor(0, 64, 68, null)));
        structure.setLogicalBuildingId(10);
        Village village = new Village(1, null);
        village.registerStructure(structure, main);
        village.getBuildings().put(contributor.getId(), contributor);
        village.getBuildings().put(independent.getId(), independent);
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

    private record Fixture(Village village,
                           LogicalBuilding logicalBuilding,
                           List<Building> rooms,
                           Building main,
                           Building contributor,
                           Building independent) {
    }
}
