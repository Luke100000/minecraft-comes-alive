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
        return new StructureScanner.FloorObservation(source, floor, connected, List.of());
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
        return new StructureFloor(id, anchorY, ceilingY, 0,
                BuildingFloorRegion.fromFootprint(anchorY, cells));
    }

    private static FloorGeometry scannedFloor(int anchorY, int ceilingY, int minX, int maxX) {
        Set<FloorGeometry.Cell> cells = java.util.stream.IntStream.rangeClosed(minX, maxX)
                .mapToObj(x -> new FloorGeometry.Cell(new BlockPos(x, anchorY, 0), anchorY, ceilingY))
                .collect(java.util.stream.Collectors.toSet());
        return new FloorGeometry(cells, Map.of());
    }

    private static FloorGeometry.Cell cell(int x, int y) {
        return new FloorGeometry.Cell(new BlockPos(x, y, 0), y, y + 4);
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
