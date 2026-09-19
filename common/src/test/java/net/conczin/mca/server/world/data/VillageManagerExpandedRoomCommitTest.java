package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageManagerExpandedRoomCommitTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void existingStructureRefreshCommitsFloorAndNewRoomTogether() {
        Village village = new Village(1, null);
        BuildingFloorRegion oldRegion = region(64, 0, 1);
        Structure current = new Structure(10, BlockPos.ZERO, List.of(
                TestStructureFloors.create(0, 64, 68, 0, oldRegion)));
        Building main = room(100, 10, 0, oldRegion);
        village.registerStructure(current, main);

        BuildingFloorRegion freshRegion = region(64, 0, 3);
        Structure refreshed = current.copy();
        assertTrue(refreshed.replaceFloorGeometry(0,
                TestStructureFloors.create(0, 64, 68, 0, freshRegion)));

        BuildingFloorRegion newRoomRegion = region(64, 2, 3);
        Building added = room(-1, 10, 0, newRoomRegion);
        BuildingScanResult scan = new BuildingScanResult(
                Building.validationResult.SUCCESS,
                new BlockPos(2, 64, 0),
                added,
                List.of("building"),
                village).withPendingStructure(refreshed);
        VillageManager manager = new VillageManager(null);

        assertEquals(Building.validationResult.SUCCESS,
                manager.commitRoomAddition(scan, "building"));
        StructureFloor committedFloor = village.getStructure(10).orElseThrow().getFloor(0).orElseThrow();
        BlockPos newCell = new BlockPos(3, 64, 0);
        assertTrue(committedFloor.geometry().cellAt(newCell).isPresent());
        assertEquals(2, village.getRooms().count());
        assertTrue(village.getRooms().anyMatch(room -> room.getFloorCells().contains(newCell)));
    }

    @Test
    void rejectedAtomicFloorRefreshLeavesStructureAndRoomsUntouched() {
        Village village = new Village(1, null);
        BuildingFloorRegion oldRegion = region(64, 0, 1);
        Structure current = new Structure(10, BlockPos.ZERO, List.of(
                TestStructureFloors.create(0, 64, 68, 0, oldRegion)));
        Building main = room(100, 10, 0, oldRegion);
        village.registerStructure(current, main);

        Set<FloorGeometry.Cell> oldGeometry = Set.copyOf(
                current.getFloor(0).orElseThrow().geometry().cells());
        Set<BlockPos> oldRoomCells = Set.copyOf(main.getFloorCells());

        Structure refreshed = current.copy();
        assertTrue(refreshed.replaceFloorGeometry(0,
                TestStructureFloors.create(0, 64, 68, 0, region(64, 0, 3))));
        Building invalidReplacement = room(100, 10, 0,
                BuildingFloorRegion.fromFootprint(64, Set.of(
                        new BlockPos(0, 64, 0), new BlockPos(4, 64, 0))));

        assertFalse(village.publishFloorRefresh(refreshed, 0, List.of(invalidReplacement)));
        assertEquals(oldGeometry, village.getStructure(10).orElseThrow()
                .getFloor(0).orElseThrow().geometry().cells());
        assertEquals(oldRoomCells, village.getBuilding(100).orElseThrow().getFloorCells());
    }

    @Test
    void floorRefreshAllowsUnregisteredRoomComponents() {
        Village village = new Village(1, null);
        BuildingFloorRegion oldRegion = region(64, 0, 1);
        Structure current = new Structure(10, BlockPos.ZERO, List.of(
                TestStructureFloors.create(0, 64, 68, 0, oldRegion)));
        Building main = room(100, 10, 0, oldRegion);
        village.registerStructure(current, main);

        Structure refreshed = current.copy();
        assertTrue(refreshed.replaceFloorGeometry(0,
                TestStructureFloors.create(0, 64, 68, 0, region(64, 0, 3))));

        assertTrue(village.publishFloorRefresh(refreshed, 0, List.of(main)),
                "fresh Floor cells may remain unregistered until the player adds those Rooms");
        assertEquals(region(64, 0, 3).cells(), village.getStructure(10).orElseThrow()
                .getFloor(0).orElseThrow().geometry().cells().stream()
                .map(FloorGeometry.Cell::feet)
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(oldRegion.cells(), village.getBuilding(100).orElseThrow().getFloorCells());
    }

    @Test
    void stalePendingStructureRefreshFailsWithoutRegisteringANewStructure() {
        Village village = new Village(1, null);
        Structure refreshed = new Structure(10, BlockPos.ZERO, List.of(
                TestStructureFloors.create(0, 64, 68, 0, region(64, 0, 3))));
        Building added = room(-1, 10, 0, region(64, 2, 3));
        BuildingScanResult scan = new BuildingScanResult(
                Building.validationResult.SUCCESS,
                new BlockPos(2, 64, 0),
                added,
                List.of("building"),
                village).withPendingStructure(refreshed);
        VillageManager manager = new VillageManager(null);

        Building.validationResult result = assertDoesNotThrow(
                () -> manager.commitRoomAddition(scan, "building"),
                "stale Floor refresh must fail as validation, not throw");

        assertEquals(Building.validationResult.NOT_IN_BUILDING, result);
        assertEquals(0, village.getStructures().size());
        assertEquals(0, village.getRooms().count());
    }

    @Test
    void selectedRoomUpdatePreservesForcedTypeAndSibling() {
        Village village = new Village(1, null);
        Structure current = new Structure(10, BlockPos.ZERO, List.of(
                TestStructureFloors.create(0, 64, 68, 0, region(64, 0, 3))));
        Building selected = room(100, 10, 0, region(64, 0, 1));
        selected.setType("workshop");
        selected.setTypeForced(true);
        Building sibling = room(101, 10, 0, region(64, 3, 3));
        sibling.setType("building");
        village.registerStructure(current, selected);
        village.registerRoom(sibling);

        Structure refreshed = current.copy();
        Building replacement = room(100, 10, 0, region(64, 0, 2));
        replacement.setType("workshop");
        replacement.setTypeForced(true);
        RegisteredRoomUpdate update = new RegisteredRoomUpdate(
                Building.validationResult.SUCCESS, BlockPos.ZERO, village, refreshed,
                10, 0, 100, replacement, List.of("music_store", "workshop"));
        VillageManager manager = new VillageManager(null);
        Set<BlockPos> siblingCells = Set.copyOf(sibling.getFloorCells());

        assertFalse(update.requiresTypeSelection());
        assertEquals(Building.validationResult.SUCCESS,
                manager.commitRegisteredRoomUpdate(update, null));
        Building committed = village.getBuilding(100).orElseThrow();
        assertEquals("workshop", committed.getType());
        assertTrue(committed.isTypeForced());
        Building untouchedSibling = village.getBuilding(101).orElseThrow();
        assertEquals("building", untouchedSibling.getType());
        assertEquals(siblingCells, untouchedSibling.getFloorCells());
    }

    @Test
    void selectedNonForcedRoomUsesAutomaticTypeResolution() {
        Village village = new Village(1, null);
        Structure current = new Structure(10, BlockPos.ZERO, List.of(
                TestStructureFloors.create(0, 64, 68, 0, region(64, 0, 1))));
        Building selected = room(100, 10, 0, region(64, 0, 1));
        selected.setType("workshop");
        selected.setTypeForced(false);
        village.registerStructure(current, selected);

        Building replacement = room(100, 10, 0, region(64, 0, 1));
        replacement.setType("workshop");
        replacement.setTypeForced(false);
        RegisteredRoomUpdate update = new RegisteredRoomUpdate(
                Building.validationResult.SUCCESS, BlockPos.ZERO, village, current.copy(),
                10, 0, 100, replacement, List.of());
        VillageManager manager = new VillageManager(null);

        assertEquals(Building.validationResult.SUCCESS,
                manager.commitRegisteredRoomUpdate(update, null));
        Building committed = village.getBuilding(100).orElseThrow();
        assertEquals("house", committed.getType());
        assertFalse(committed.isTypeForced());
    }

    private static BuildingFloorRegion region(int y, int minX, int maxX) {
        Set<BlockPos> cells = java.util.stream.IntStream.rangeClosed(minX, maxX)
                .mapToObj(x -> new BlockPos(x, y, 0))
                .collect(java.util.stream.Collectors.toSet());
        return BuildingFloorRegion.fromFootprint(y, cells);
    }

    private static Building room(int id, int structureId, int floorId, BuildingFloorRegion region) {
        Building room = new Building(region.cells().iterator().next());
        room.setId(id);
        room.setStructureId(structureId);
        room.setFloorId(floorId);
        int minX = region.cells().stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int maxX = region.cells().stream().mapToInt(BlockPos::getX).max().orElseThrow();
        room.setGeometry(new BlockPos(minX, region.anchorY(), 0),
                new BlockPos(maxX, region.anchorY() + 3, 0), region);
        return room;
    }

}
