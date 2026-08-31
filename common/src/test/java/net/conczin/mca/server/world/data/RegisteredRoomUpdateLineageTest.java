package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisteredRoomUpdateLineageTest {
    @Test
    void splitIncludesEveryFreshComponentDescendingFromSelectedRoomButNotUnrelatedComponents() {
        Building selected = room(12, 0, 4);
        Building left = room(-1, 0, 1);
        Building right = room(-1, 3, 4);
        Building unrelated = room(-1, 10, 11);

        List<Building> lineage = VillageManager.updateLineage(
                selected, List.of(left, right, unrelated), List.of()).orElseThrow();

        assertEquals(List.of(left, right), lineage);
    }

    @Test
    void lineageRejectsFreshComponentThatAlsoConsumesRegisteredNeighbor() {
        Building selected = room(12, 0, 4);
        Building mergedFresh = room(-1, 0, 7);
        Building neighbor = room(20, 5, 7);

        assertTrue(VillageManager.updateLineage(
                selected, List.of(mergedFresh), List.of(neighbor)).isEmpty());
    }

    @Test
    void splitKeepsOldIdOnSourceComponentAndCreatesAnotherRoom() {
        Building previous = room(12, 0, 4);
        Building left = room(-1, 0, 1);
        Building right = room(-1, 3, 4);

        RegisteredRoomReconciler.Result result = RegisteredRoomReconciler.reconcile(
                new BlockPos(0, 64, 0), 12, -1,
                List.of(previous), List.of(left, right)).orElseThrow();

        RegisteredRoomReconciler.Assignment player = result.assignments().stream()
                .filter(assignment -> assignment.component() == result.playerComponent())
                .findFirst().orElseThrow();
        assertEquals(12, player.roomId());
        assertEquals(1, result.assignments().stream()
                .filter(RegisteredRoomReconciler.Assignment::createsRoom).count());
    }

    @Test
    void structureFloorGrowthKeepsExistingCellsAndFloorNumber() {
        BuildingFloorRegion original = BuildingFloorRegion.fromFootprint(64, List.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0)));
        Structure structure = new Structure(1, new BlockPos(0, 64, 0),
                new BlockPos(0, 64, 0), new BlockPos(1, 67, 0),
                List.of(new StructureFloor(7, 64, 68, 3, original)));
        BuildingFloorRegion expandedRoom = BuildingFloorRegion.fromFootprint(64, List.of(
                new BlockPos(1, 64, 0), new BlockPos(2, 64, 0)));

        assertTrue(structure.ensureFloorContains(7, expandedRoom, 72));

        StructureFloor floor = structure.getFloor(7).orElseThrow();
        assertEquals(3, floor.floorNumber());
        assertEquals(72, floor.ceilingY());
        assertTrue(floor.region().containsHorizontally(0, 0));
        assertTrue(floor.region().containsHorizontally(2, 0));
    }

    @Test
    void commitScopeRejectsLineageThatNowOverlapsNeighbor() {
        Building selectedComponent = room(-1, 0, 4);
        Building splitComponent = room(-1, 6, 7);
        Building neighbor = room(20, 7, 9);
        List<RegisteredRoomReconciler.Assignment> assignments = List.of(
                new RegisteredRoomReconciler.Assignment(selectedComponent, room(12, 0, 4)),
                new RegisteredRoomReconciler.Assignment(splitComponent, null));

        assertTrue(VillageManager.lineageOverlapsRegisteredRooms(
                assignments, List.of(neighbor)));
    }

    private static Building room(int id, int minX, int maxX) {
        BuildingFloorRegion footprint = BuildingFloorRegion.fromFootprint(64,
                java.util.stream.IntStream.rangeClosed(minX, maxX)
                        .mapToObj(x -> new BlockPos(x, 64, 0))
                        .toList());
        Building room = new Building(new BlockPos(minX, 64, 0));
        room.setId(id);
        room.setStructureId(1);
        room.setFloorId(0);
        room.setGeometry(new BlockPos(minX, 64, 0), new BlockPos(maxX, 68, 0),
                maxX - minX + 1, footprint);
        return room;
    }
}
