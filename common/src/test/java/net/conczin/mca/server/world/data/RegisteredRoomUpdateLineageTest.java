package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisteredRoomUpdateLineageTest {
    @Test
    void splitIncludesEveryFreshComponentDescendingFromSelectedRoomButNotUnrelatedComponents() {
        Building selected = room(12, 0, 4);
        Building left = room(-1, 0, 1);
        Building right = room(-1, 3, 4);
        Building unrelated = room(-1, 10, 11);
        FloorGeometry floor = new FloorGeometry(Set.of(
                cell(0), cell(1), cell(2), cell(3), cell(4), cell(10), cell(11)), Map.of());

        List<Building> lineage = RegisteredRoomReconciler.updateLineage(
                selected, List.of(left, right, unrelated), List.of(), floor).orElseThrow();

        assertEquals(List.of(left, right), lineage);
    }

    @Test
    void lineageRejectsFreshComponentThatAlsoConsumesRegisteredNeighbor() {
        Building selected = room(12, 0, 4);
        Building mergedFresh = room(-1, 0, 7);
        Building neighbor = room(20, 5, 7);
        FloorGeometry floor = new FloorGeometry(Set.of(
                cell(0), cell(1), cell(2), cell(3), cell(4), cell(5), cell(6), cell(7)), Map.of());

        assertTrue(RegisteredRoomReconciler.updateLineage(
                selected, List.of(mergedFresh), List.of(neighbor), floor).isEmpty());
    }

    @Test
    void lineageIgnoresBoundaryOnlyOverlapWithSelectedRoom() {
        Building selected = room(12, 0, 2);
        Building retained = room(-1, 0, 1);
        Building boundaryOnly = room(-1, 2, 4);
        Building neighbor = room(20, 3, 4);
        BlockPos door = new BlockPos(2, 64, 0);
        FloorGeometry floor = new FloorGeometry(Set.of(
                cell(0), cell(1), cell(2), cell(3), cell(4)),
                Map.of(door, FloorConnector.Type.DOOR));

        assertFalse(floor.isRoomIdentityCell(door));
        assertEquals(Optional.of(List.of(retained)), RegisteredRoomReconciler.updateLineage(
                selected, List.of(retained, boundaryOnly), List.of(neighbor), floor));
    }

    @Test
    void splitKeepsOldIdOnSourceComponentAndCreatesAnotherRoom() {
        Building previous = room(12, 0, 4);
        Building left = room(-1, 0, 1);
        Building right = room(-1, 3, 4);

        RegisteredRoomReconciler.Result result = RegisteredRoomReconciler.reconcile(
                new BlockPos(0, 64, 0), 12, -1,
                List.of(previous), List.of(left, right),
                new FloorGeometry(Set.of(cell(0), cell(1), cell(2), cell(3), cell(4)), Map.of()))
                .orElseThrow();

        RegisteredRoomReconciler.Assignment player = result.assignments().stream()
                .filter(assignment -> assignment.component() == result.playerComponent())
                .findFirst().orElseThrow();
        assertEquals(12, player.roomId());
        assertEquals(1, result.assignments().stream()
                .filter(RegisteredRoomReconciler.Assignment::createsRoom).count());
    }

    @Test
    void boundaryConnectorOwnershipDoesNotMergeRoomIdentities() {
        Building main = room(10, 0, 2);
        Building adjacent = room(20, 3, 4);
        Building freshMain = room(-1, 0, 1);
        Building freshAdjacent = room(-1, 2, 4);
        BlockPos door = new BlockPos(2, 64, 0);
        FloorGeometry floor = new FloorGeometry(Set.of(
                cell(0), cell(1), cell(2), cell(3), cell(4)),
                Map.of(door, FloorConnector.Type.DOOR));

        RegisteredRoomReconciler.Result result = RegisteredRoomReconciler.reconcile(
                new BlockPos(3, 64, 0), 20, 10,
                List.of(main, adjacent), List.of(freshMain, freshAdjacent), floor).orElseThrow();

        RegisteredRoomReconciler.Assignment player = result.assignments().stream()
                .filter(assignment -> assignment.component() == result.playerComponent())
                .findFirst().orElseThrow();
        assertEquals(20, player.roomId());
    }

    @Test
    void stackedSplitKeepsRoomIdOnSameComponentRegardlessOfInputOrder() {
        BlockPos currentCell = new BlockPos(10, 64, 0);
        BlockPos lowerCell = new BlockPos(0, 64, 0);
        BlockPos upperCell = new BlockPos(0, 70, 0);
        Building current = room(10, Set.of(currentCell));
        Building stacked = room(20, Set.of(lowerCell, upperCell));
        Building freshCurrent = room(-1, Set.of(currentCell));
        Building lower = room(-1, Set.of(lowerCell));
        Building upper = room(-1, Set.of(upperCell));
        FloorGeometry floor = new FloorGeometry(Set.of(
                new FloorGeometry.Cell(currentCell, 68),
                new FloorGeometry.Cell(lowerCell, 68),
                new FloorGeometry.Cell(upperCell, 74)), Map.of());

        RegisteredRoomReconciler.Result upperFirst = RegisteredRoomReconciler.reconcile(
                currentCell, 10, 10,
                List.of(current, stacked), List.of(upper, freshCurrent, lower), floor).orElseThrow();
        RegisteredRoomReconciler.Result lowerFirst = RegisteredRoomReconciler.reconcile(
                currentCell, 10, 10,
                List.of(current, stacked), List.of(lower, freshCurrent, upper), floor).orElseThrow();

        assertEquals(Set.of(lowerCell), componentForRoom(upperFirst, 20).getFloorCells());
        assertEquals(Set.of(lowerCell), componentForRoom(lowerFirst, 20).getFloorCells());
    }

    @Test
    void equalBoundsSplitKeepsRoomIdOnCanonicalExactComponent() {
        BlockPos currentCell = new BlockPos(10, 64, 0);
        Set<BlockPos> firstCells = Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 1));
        Set<BlockPos> secondCells = Set.of(
                new BlockPos(0, 64, 1), new BlockPos(1, 64, 0));
        Building current = room(10, Set.of(currentCell));
        Building previous = room(20, java.util.stream.Stream.concat(
                        firstCells.stream(), secondCells.stream())
                .collect(java.util.stream.Collectors.toSet()));
        Building freshCurrent = room(-1, Set.of(currentCell));
        Building first = room(-1, firstCells);
        Building second = room(-1, secondCells);
        FloorGeometry floor = new FloorGeometry(java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(new FloorGeometry.Cell(currentCell, 68)),
                        java.util.stream.Stream.concat(firstCells.stream(), secondCells.stream())
                                .map(pos -> new FloorGeometry.Cell(pos, 68)))
                .collect(java.util.stream.Collectors.toSet()), Map.of());

        RegisteredRoomReconciler.Result secondFirst = RegisteredRoomReconciler.reconcile(
                currentCell, 10, 10,
                List.of(current, previous), List.of(second, freshCurrent, first), floor).orElseThrow();
        RegisteredRoomReconciler.Result firstFirst = RegisteredRoomReconciler.reconcile(
                currentCell, 10, 10,
                List.of(current, previous), List.of(first, freshCurrent, second), floor).orElseThrow();

        assertEquals(firstCells, componentForRoom(secondFirst, 20).getFloorCells());
        assertEquals(firstCells, componentForRoom(firstFirst, 20).getFloorCells());
    }

    @Test
    void splitLineageUsesCanonicalExactCellsInsteadOfAggregateBounds() {
        BlockPos currentCell = new BlockPos(10, 64, 0);
        Set<BlockPos> boundsFirstCells = Set.of(
                new BlockPos(0, 64, 10), new BlockPos(1, 64, 0));
        Set<BlockPos> exactFirstCells = Set.of(
                new BlockPos(0, 64, 5), new BlockPos(2, 64, 5));
        Building current = room(10, Set.of(currentCell));
        Building previous = room(20, java.util.stream.Stream.concat(
                        boundsFirstCells.stream(), exactFirstCells.stream())
                .collect(java.util.stream.Collectors.toSet()));
        Building freshCurrent = room(-1, Set.of(currentCell));
        Building boundsFirst = room(-1, boundsFirstCells);
        Building exactFirst = room(-1, exactFirstCells);
        FloorGeometry floor = new FloorGeometry(java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(new FloorGeometry.Cell(currentCell, 68)),
                        java.util.stream.Stream.concat(boundsFirstCells.stream(), exactFirstCells.stream())
                                .map(pos -> new FloorGeometry.Cell(pos, 68)))
                .collect(java.util.stream.Collectors.toSet()), Map.of());

        RegisteredRoomReconciler.Result boundsInputFirst = RegisteredRoomReconciler.reconcile(
                currentCell, 10, 10,
                List.of(current, previous), List.of(boundsFirst, freshCurrent, exactFirst), floor).orElseThrow();
        RegisteredRoomReconciler.Result exactInputFirst = RegisteredRoomReconciler.reconcile(
                currentCell, 10, 10,
                List.of(current, previous), List.of(exactFirst, freshCurrent, boundsFirst), floor).orElseThrow();

        assertEquals(exactFirstCells, componentForRoom(boundsInputFirst, 20).getFloorCells());
        assertEquals(exactFirstCells, componentForRoom(exactInputFirst, 20).getFloorCells());
    }

    @Test
    void addRoomCanClaimOneSideOfAStaleRoomSplitByANewDoor() {
        Building previous = room(12, 0, 4);
        Building retained = room(-1, 0, 2);
        Building added = room(-1, 3, 4);
        BlockPos door = new BlockPos(2, 64, 0);
        FloorGeometry floor = new FloorGeometry(Set.of(
                cell(0), cell(1), cell(2), cell(3), cell(4)),
                Map.of(door, FloorConnector.Type.DOOR));

        List<Building> replacements = RegisteredRoomReconciler.reconcileAddition(
                List.of(previous), List.of(retained, added), added, floor).orElseThrow();

        assertEquals(1, replacements.size());
        assertEquals(12, replacements.getFirst().getId());
        assertEquals(retained.getFloorCells(), replacements.getFirst().getFloorCells());
    }

    @Test
    void addRoomIgnoresOtherFreshComponentsThatWereNeverRegistered() {
        Building previous = room(12, 0, 2);
        Building retained = room(-1, 0, 2);
        Building added = room(-1, 4, 6);
        Building unrelated = room(-1, 8, 10);
        FloorGeometry floor = new FloorGeometry(Set.of(
                cell(0), cell(1), cell(2),
                cell(4), cell(5), cell(6),
                cell(8), cell(9), cell(10)), Map.of());

        List<Building> replacements = RegisteredRoomReconciler.reconcileAddition(
                List.of(previous), List.of(retained, added, unrelated), added, floor).orElseThrow();

        assertEquals(1, replacements.size());
        assertEquals(12, replacements.getFirst().getId());
        assertEquals(retained.getFloorCells(), replacements.getFirst().getFloorCells());
    }

    @Test
    void structureFloorReplacementUsesFreshGeometryAndKeepsFloorNumber() {
        BuildingFloorRegion original = BuildingFloorRegion.fromFootprint(64, List.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0)));
        Structure structure = new Structure(1, new BlockPos(0, 64, 0),
                List.of(TestStructureFloors.create(7, 64, 68, 3, original)));
        BuildingFloorRegion fresh = BuildingFloorRegion.fromFootprint(64, List.of(
                new BlockPos(1, 64, 0), new BlockPos(2, 64, 0)));

        assertTrue(structure.replaceFloorGeometry(7,
                TestStructureFloors.create(0, 64, 72, fresh)));

        StructureFloor floor = structure.getFloor(7).orElseThrow();
        assertEquals(3, floor.floorNumber());
        assertEquals(72, floor.maxPhysicalCeilingY());
        assertTrue(!floor.region().containsHorizontally(0, 0));
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
        FloorGeometry floor = new FloorGeometry(Set.of(
                cell(0), cell(1), cell(2), cell(3), cell(4),
                cell(6), cell(7), cell(8), cell(9)), Map.of());

        assertTrue(VillageManager.lineageOverlapsRegisteredRooms(
                assignments, List.of(neighbor), floor));
    }

    @Test
    void commitScopeIgnoresBoundaryOnlyOverlapWithNeighbor() {
        Building selectedComponent = room(-1, 0, 2);
        Building neighbor = room(20, 2, 4);
        List<RegisteredRoomReconciler.Assignment> assignments = List.of(
                new RegisteredRoomReconciler.Assignment(selectedComponent, room(12, 0, 1)));
        BlockPos door = new BlockPos(2, 64, 0);
        FloorGeometry floor = new FloorGeometry(Set.of(
                cell(0), cell(1), cell(2), cell(3), cell(4)),
                Map.of(door, FloorConnector.Type.DOOR));

        assertFalse(floor.isRoomIdentityCell(door));
        assertFalse(VillageManager.lineageOverlapsRegisteredRooms(
                assignments, List.of(neighbor), floor));
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
        room.setGeometry(new BlockPos(minX, 64, 0), new BlockPos(maxX, 68, 0), footprint);
        return room;
    }

    private static Building room(int id, Set<BlockPos> floorCells) {
        int minX = floorCells.stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int minY = floorCells.stream().mapToInt(BlockPos::getY).min().orElseThrow();
        int minZ = floorCells.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int maxX = floorCells.stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int maxY = floorCells.stream().mapToInt(BlockPos::getY).max().orElseThrow() + 4;
        int maxZ = floorCells.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        Building room = new Building(floorCells.iterator().next());
        room.setId(id);
        room.setStructureId(1);
        room.setFloorId(0);
        room.setGeometry(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), floorCells);
        return room;
    }

    private static Building componentForRoom(RegisteredRoomReconciler.Result result, int roomId) {
        return result.assignments().stream()
                .filter(assignment -> assignment.roomId() == roomId)
                .map(RegisteredRoomReconciler.Assignment::component)
                .findFirst()
                .orElseThrow();
    }

    private static FloorGeometry.Cell cell(int x) {
        return new FloorGeometry.Cell(new BlockPos(x, 64, 0), 68);
    }
}
