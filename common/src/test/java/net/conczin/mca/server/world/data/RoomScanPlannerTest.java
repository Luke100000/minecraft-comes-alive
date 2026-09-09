package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomScanPlannerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void sameStoreyObservationUpdatesExistingRoomWithoutAnotherWorldScan() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 3));
        Building room = room(100, 20, 0, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0)));
        Village village = village(persisted, room);
        BlockPos source = new BlockPos(6, 64, 0);
        StructureScanner.FloorObservation observation = observation(
                source, scannedFloor(64, 68, 0, 6), List.of());

        RoomScanPlan plan = RoomScanPlanner.planFresh(village, source, observation);

        assertEquals(Village.RoomScanMode.UPDATE_ROOM, plan.mode());
        assertEquals(room, plan.currentRoom().orElseThrow());
    }

    @Test
    void staircaseTransitionComponentStillResolvesTheRegisteredRoom() {
        Structure persisted = structure(20, 20, floor(0, 88, 91, 0, 3));
        Building room = room(100, 20, 0, Set.of(
                new BlockPos(0, 88, 0), new BlockPos(1, 88, 0),
                new BlockPos(2, 88, 0), new BlockPos(3, 88, 0)));
        Village village = village(persisted, room);
        BlockPos source = new BlockPos(6, 91, 0);
        FloorGeometry fresh = new FloorGeometry(Set.of(
                cell(0, 88), cell(1, 88), cell(2, 88), cell(3, 88),
                cell(4, 89), cell(5, 90), cell(6, 91)), Map.of());

        RoomScanPlan plan = RoomScanPlanner.planFresh(
                village, source, observation(source, fresh, List.of()));

        assertEquals(Village.RoomScanMode.UPDATE_ROOM, plan.mode());
        assertEquals(room, plan.currentRoom().orElseThrow());
    }

    @Test
    void sameStoreyObservationAddsRoomAcrossDoorBoundary() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 3));
        Building room = room(100, 20, 0, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0)));
        Village village = village(persisted, room);
        BlockPos source = new BlockPos(7, 64, 0);
        BlockPos door = new BlockPos(4, 64, 0);
        FloorGeometry fresh = new FloorGeometry(Set.of(
                cell(0, 64), cell(1, 64), cell(2, 64), cell(3, 64), cell(4, 64),
                cell(5, 64), cell(6, 64), cell(7, 64), cell(8, 64)),
                Map.of(door, FloorConnector.Type.DOOR));

        RoomScanPlan plan = RoomScanPlanner.planFresh(
                village, source, observation(source, fresh, List.of()));

        assertEquals(Village.RoomScanMode.ADD_ROOM, plan.mode());
        assertTrue(plan.currentRoom().isEmpty());
        assertEquals(20, plan.targetStructureId());
        assertEquals(0, plan.targetFloorId());
    }

    @Test
    void sharedDoorCellAloneDoesNotResolveTheOtherRoom() {
        BlockPos door = new BlockPos(4, 64, 0);
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 10));
        Building room = room(100, 20, 0, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0), door));
        Village village = village(persisted, room);
        BlockPos source = new BlockPos(8, 64, 0);
        FloorGeometry fresh = new FloorGeometry(Set.of(
                cell(0, 64), cell(1, 64), cell(2, 64), cell(3, 64), cell(4, 64),
                cell(5, 64), cell(6, 64), cell(7, 64), cell(8, 64), cell(9, 64), cell(10, 64)),
                Map.of(door, FloorConnector.Type.DOOR));

        RoomScanPlan plan = RoomScanPlanner.planFresh(
                village, source, observation(source, fresh, List.of()));

        assertEquals(Village.RoomScanMode.ADD_ROOM, plan.mode());
        assertTrue(plan.currentRoom().isEmpty());
    }

    @Test
    void ambiguousSameStoreyOverlapAcrossStructuresDoesNotPickOne() {
        Structure first = structure(20, 20, floor(0, 64, 68, 0, 1));
        Structure second = structure(30, 30, floor(0, 64, 68, 2, 3));
        Building firstRoom = room(100, 20, 0, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0)));
        Building secondRoom = room(101, 30, 0, Set.of(
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0)));
        Village village = new Village(1, null);
        village.registerStructure(first, firstRoom);
        village.registerStructure(second, secondRoom);
        BlockPos source = new BlockPos(4, 64, 0);

        RoomScanPlan plan = RoomScanPlanner.planFresh(village, source,
                observation(source, scannedFloor(64, 68, 0, 4), List.of()));

        assertEquals(Village.RoomScanMode.ADD_BUILDING, plan.mode());
    }

    @Test
    void differentStoreyWithoutAttachmentEvidenceDoesNotBecomeSameStoreyExpansion() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 3));
        Building room = room(100, 20, 0, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0)));
        Village village = village(persisted, room);
        BlockPos source = new BlockPos(0, 68, 0);

        RoomScanPlan plan = RoomScanPlanner.planFresh(village, source,
                observation(source, scannedFloor(68, 72, 0, 3), List.of()));

        assertEquals(Village.RoomScanMode.ADD_BUILDING, plan.mode());
    }

    @Test
    void freshObservationUsesConnectedStoreyEvidenceForAttachmentPlan() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 3));
        Building room = room(100, 20, 0, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0)));
        Village village = village(persisted, room);
        BlockPos source = new BlockPos(0, 68, 0);
        FloorGeometry upper = scannedFloor(68, 72, 0, 3);
        FloorGeometry lowerEvidence = scannedFloor(64, 68, 0, 3);

        RoomScanPlan plan = RoomScanPlanner.planFresh(
                village, source, observation(source, upper, List.of(upper, lowerEvidence)));

        assertEquals(Village.RoomScanMode.ADD_FLOOR, plan.mode());
        assertEquals(20, plan.targetBuildingId());
        assertEquals(1, plan.prospectiveFloorNumber());
    }

    @Test
    void persistedInteractionPlanDoesNotRequireFreshWorldObservation() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 3));
        Building room = room(100, 20, 0, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0)));
        Village village = village(persisted, room);

        RoomScanPlan plan = RoomScanPlanner.plan(village, null, new BlockPos(2, 64, 0));

        assertEquals(Village.RoomScanMode.UPDATE_ROOM, plan.mode());
        assertEquals(room, plan.currentRoom().orElseThrow());
    }

    private static Village village(Structure structure, Building room) {
        Village village = new Village(1, null);
        village.registerStructure(structure, room);
        return village;
    }

    private static StructureScanner.FloorObservation observation(BlockPos source,
                                                                   FloorGeometry floor,
                                                                   List<FloorGeometry> connected) {
        return new StructureScanner.FloorObservation(
                source, new SelectedFloorScanner.Result(Building.validationResult.SUCCESS,
                floor, source, source, transitions(floor), connected), List.of());
    }

    private static Structure structure(int id, int logicalBuildingId, StructureFloor floor) {
        Structure structure = new Structure(id, floor.region().cells().iterator().next(), List.of(floor));
        structure.setLogicalBuildingId(logicalBuildingId);
        return structure;
    }

    private static StructureFloor floor(int id, int anchorY, int ceilingY, int minX, int maxX) {
        Set<BlockPos> cells = java.util.stream.IntStream.rangeClosed(minX, maxX)
                .mapToObj(x -> new BlockPos(x, anchorY, 0))
                .collect(java.util.stream.Collectors.toSet());
        return TestStructureFloors.create(id, anchorY, ceilingY, 0,
                BuildingFloorRegion.fromFootprint(anchorY, cells));
    }

    private static FloorGeometry scannedFloor(int anchorY, int ceilingY, int minX, int maxX) {
        Set<FloorGeometry.Cell> cells = java.util.stream.IntStream.rangeClosed(minX, maxX)
                .mapToObj(x -> new FloorGeometry.Cell(new BlockPos(x, anchorY, 0), ceilingY))
                .collect(java.util.stream.Collectors.toSet());
        return new FloorGeometry(cells, Map.of());
    }

    private static FloorGeometry.Cell cell(int x, int y) {
        return new FloorGeometry.Cell(new BlockPos(x, y, 0), y + 4);
    }

    private static Set<SelectedFloorScanner.Transition> transitions(FloorGeometry geometry) {
        java.util.LinkedHashSet<SelectedFloorScanner.Transition> transitions = new java.util.LinkedHashSet<>();
        for (FloorGeometry.Cell first : geometry.cells()) {
            for (FloorGeometry.Cell second : geometry.cells()) {
                int horizontal = Math.abs(first.feet().getX() - second.feet().getX())
                        + Math.abs(first.feet().getZ() - second.feet().getZ());
                if (horizontal == 1 && Math.abs(first.feet().getY() - second.feet().getY()) <= 1) {
                    transitions.add(new SelectedFloorScanner.Transition(first.feet(), second.feet()));
                }
            }
        }
        return Set.copyOf(transitions);
    }

    private static Building room(int id, int structureId, int floorId, Set<BlockPos> cells) {
        Building room = new Building(cells.iterator().next());
        room.setId(id);
        room.setStructureId(structureId);
        room.setFloorId(floorId);
        int minX = cells.stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int maxX = cells.stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int minY = cells.stream().mapToInt(BlockPos::getY).min().orElseThrow();
        room.setGeometry(new BlockPos(minX, minY, 0), new BlockPos(maxX, minY + 3, 0), cells);
        return room;
    }
}
