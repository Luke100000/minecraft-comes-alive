package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    void freshSameFloorOverlapDoesNotInventRegisteredRoomIdentity() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 3));
        Building room = room(100, 20, 0, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0)));
        Village village = village(persisted, room);
        BlockPos source = new BlockPos(6, 64, 0);
        StructureScanner.FloorObservation observation = observation(
                source, scannedFloor(64, 68, 0, 6));

        RoomScanPlan plan = RoomScanPlanner.planFresh(village, source, observation);

        assertEquals(Village.RoomScanMode.ADD_ROOM, plan.mode());
        assertTrue(plan.currentRoom().isEmpty());
        assertEquals(20, plan.targetStructureId());
        assertEquals(0, plan.targetFloorId());
    }

    @Test
    void attachmentAboveLandingReusesExistingStorey() {
        Structure ground = structure(20, 20, floor(0, 67, 71, 0, 3));
        Village village = village(ground, room(100, 20, 0, Set.of(new BlockPos(0, 67, 0))));
        Structure landing = structure(21, 20, new StructureFloor(0, 2, scannedFloor(76, 80, 5, 8)));
        village.registerStructure(landing, room(101, 21, 0, Set.of(new BlockPos(5, 76, 0))));
        BlockPos source = new BlockPos(9, 78, 0);
        RoomScanPlan plan = RoomScanPlanner.planFresh(village, source,
                observation(source, scannedFloor(78, 82, 9, 12), Set.of(new BlockPos(8, 76, 0))));
        assertEquals(Village.RoomScanMode.ADD_ATTACHMENT, plan.mode());
        assertEquals(2, plan.prospectiveFloorNumber());
    }

    @Test
    void conflictingStoreyNumbersPreserveAttachmentFailure() {
        Structure ground = structure(20, 20, floor(0, 67, 71, 0, 3));
        Village village = village(ground, room(100, 20, 0, Set.of(new BlockPos(0, 67, 0))));
        village.registerStructure(structure(21, 20, new StructureFloor(0, 2, scannedFloor(76, 80, 5, 8))),
                room(101, 21, 0, Set.of(new BlockPos(5, 76, 0))));
        village.registerStructure(structure(22, 20, new StructureFloor(0, 3, scannedFloor(78, 82, 15, 18))),
                room(102, 22, 0, Set.of(new BlockPos(15, 78, 0))));
        BlockPos source = new BlockPos(9, 78, 0);
        var analysis = RoomScanPlanner.analyzeFresh(village, source,
                observation(source, scannedFloor(78, 82, 9, 12), Set.of(new BlockPos(8, 76, 0))));
        assertEquals(Building.validationResult.AMBIGUOUS_STRUCTURE, analysis.result());
        assertEquals(Village.RoomScanMode.ADD_ATTACHMENT, analysis.plan().mode());
        assertEquals(20, analysis.plan().targetBuildingId());
        assertTrue(!analysis.plan().hasProspectiveFloor());
        assertEquals(3, village.getStructure(22).orElseThrow().getFloor(0).orElseThrow().floorNumber());
        assertEquals(Village.RoomScanMode.UPDATE_ROOM,
                village.getRoomScanPlan(null, new BlockPos(15, 78, 0)).mode());
    }

    @Test
    void supportedBandDescentUsesTheExactSelectedRoomCell() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 3));
        BlockPos selectedCell = new BlockPos(2, 64, 0);
        Building room = room(100, 20, 0, Set.of(selectedCell));
        Village village = village(persisted, room);
        BlockPos source = new BlockPos(4, 67, 0);

        RoomScanPlan plan = RoomScanPlanner.planFresh(village, source,
                observation(selectedCell, source, scannedFloor(64, 68, 0, 3), Set.of()));

        assertEquals(Village.RoomScanMode.UPDATE_ROOM, plan.mode());
        assertEquals(room, plan.currentRoom().orElseThrow());
        assertEquals(source, plan.interactionSource());
        assertEquals(selectedCell, plan.scanSeed());
    }

    @Test
    void unsupportedHandoffDoesNotClaimTheSelectedCellsRoom() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 3));
        BlockPos selectedCell = new BlockPos(2, 64, 0);
        Building room = room(100, 20, 0, Set.of(selectedCell));
        Village village = village(persisted, room);
        BlockPos source = new BlockPos(4, 67, 0);

        RoomScanPlan plan = RoomScanPlanner.planFresh(village, source,
                observation(selectedCell, null, scannedFloor(64, 68, 0, 3), Set.of()));

        assertEquals(Village.RoomScanMode.ADD_BUILDING, plan.mode());
        assertTrue(plan.currentRoom().isEmpty());
        assertEquals(source, plan.scanSeed());
    }

    @Test
    void exactRoomCellRemainsOwnedWhenItsFloorGeometryIsStale() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 4));
        BlockPos source = new BlockPos(4, 64, 0);
        Building room = room(100, 20, 0, Set.of(source));
        Village village = village(persisted, room);
        BlockPos selectedCell = new BlockPos(2, 64, 0);
        assertTrue(persisted.replaceFloorGeometry(0, scannedFloor(64, 68, 0, 3)));

        RoomScanPlan plan = RoomScanPlanner.planFresh(village, source,
                observation(selectedCell, selectedCell, scannedFloor(64, 68, 0, 3), Set.of()));

        assertEquals(Village.RoomScanMode.UPDATE_ROOM, plan.mode());
        assertEquals(room, plan.currentRoom().orElseThrow());
        assertEquals(selectedCell, plan.scanSeed());
    }

    @Test
    void freshCellDoesNotBorrowRoomOwnershipFromAnotherHeight() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 6));
        Building room = room(100, 20, 0, Set.of(new BlockPos(6, 64, 0)));
        Village village = village(persisted, room);
        BlockPos source = new BlockPos(6, 65, 0);
        FloorGeometry fresh = new FloorGeometry(Set.of(
                cell(0, 64), cell(1, 64), cell(2, 64), cell(3, 64),
                cell(4, 64), cell(5, 64), cell(6, 65)), Map.of());

        RoomScanPlan plan = RoomScanPlanner.planFresh(village, source, observation(source, fresh));

        assertEquals(Village.RoomScanMode.ADD_ROOM, plan.mode());
        assertTrue(plan.currentRoom().isEmpty());
    }

    @Test
    void staircaseTransitionOverlapDoesNotInventRegisteredRoomIdentity() {
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
                village, source, observation(source, fresh));

        assertEquals(Village.RoomScanMode.ADD_ROOM, plan.mode());
        assertTrue(plan.currentRoom().isEmpty());
        assertEquals(20, plan.targetStructureId());
        assertEquals(0, plan.targetFloorId());
    }

    @Test
    void sameFloorObservationAddsRoomAcrossDoorBoundary() {
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
                village, source, observation(source, fresh));

        assertEquals(Village.RoomScanMode.ADD_ROOM, plan.mode());
        assertTrue(plan.currentRoom().isEmpty());
        assertEquals(20, plan.targetStructureId());
        assertEquals(0, plan.targetFloorId());
    }

    @Test
    void addedRoomKeepsTheScannersExactSelectedCell() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 3));
        Building room = room(100, 20, 0, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0)));
        Village village = village(persisted, room);
        BlockPos door = new BlockPos(4, 64, 0);
        BlockPos expectedSeed = new BlockPos(6, 64, 0);
        BlockPos source = new BlockPos(6, 65, 0);
        FloorGeometry fresh = new FloorGeometry(Set.of(
                cell(0, 64), cell(1, 64), cell(2, 64), cell(3, 64), cell(4, 64),
                cell(5, 65), cell(6, 64)), Map.of(door, FloorConnector.Type.DOOR));

        RoomScanPlan plan = RoomScanPlanner.planFresh(
                village, source, observation(source, fresh));

        assertEquals(Village.RoomScanMode.ADD_ROOM, plan.mode());
        assertEquals(expectedSeed, plan.scanSeed());
    }

    @Test
    void freshDoorWithoutWorldOrientationDoesNotGuessRegisteredSide() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 3));
        Building room = room(100, 20, 0, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0)));
        Village village = village(persisted, room);
        BlockPos door = new BlockPos(4, 64, 0);
        FloorGeometry fresh = new FloorGeometry(Set.of(
                cell(0, 64), cell(1, 64), cell(2, 64), cell(3, 64), cell(4, 64),
                cell(5, 64), cell(6, 64)), Map.of(door, FloorConnector.Type.DOOR));

        RoomScanPlan plan = RoomScanPlanner.planFresh(
                village, door, observation(door, fresh));

        assertEquals(Village.RoomScanMode.ADD_ROOM, plan.mode());
        assertTrue(plan.currentRoom().isEmpty());
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
                village, source, observation(source, fresh));

        assertEquals(Village.RoomScanMode.ADD_ROOM, plan.mode());
        assertTrue(plan.currentRoom().isEmpty());
    }

    @Test
    void ambiguousSameFloorOverlapAcrossStructuresDoesNotPickOne() {
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
                observation(source, scannedFloor(64, 68, 0, 4)));

        assertEquals(Village.RoomScanMode.ADD_BUILDING, plan.mode());
    }

    @Test
    void overlappingDifferentFloorWithoutConnectorEvidenceUsesAttachmentFallback() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 3));
        Building room = room(100, 20, 0, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0)));
        Village village = village(persisted, room);
        BlockPos source = new BlockPos(0, 68, 0);

        RoomScanPlan plan = RoomScanPlanner.planFresh(village, source,
                observation(source, scannedFloor(68, 72, 0, 3)));

        assertEquals(Village.RoomScanMode.ADD_ATTACHMENT, plan.mode());
        assertEquals(20, plan.targetBuildingId());
        assertEquals(1, plan.prospectiveFloorNumber());
    }

    @Test
    void adjacentDifferentFloorWithoutConnectorEvidenceRemainsAddBuilding() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 3));
        Building room = room(100, 20, 0, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0)));
        Village village = village(persisted, room);
        BlockPos source = new BlockPos(4, 68, 0);

        RoomScanPlan plan = RoomScanPlanner.planFresh(village, source,
                observation(source, scannedFloor(68, 72, 4, 7)));

        assertEquals(Village.RoomScanMode.ADD_BUILDING, plan.mode());
        assertEquals(-1, plan.targetBuildingId());
    }

    @Test
    void freshObservationUsesPersistedTransitionEvidenceForAttachmentPlan() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 3));
        Building room = room(100, 20, 0, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0)));
        Village village = village(persisted, room);
        BlockPos source = new BlockPos(0, 68, 0);
        FloorGeometry upper = scannedFloor(68, 72, 0, 3);

        RoomScanPlan plan = RoomScanPlanner.planFresh(
                village, source, observation(source, upper, Set.of(new BlockPos(0, 67, 0))));

        assertEquals(Village.RoomScanMode.ADD_ATTACHMENT, plan.mode());
        assertEquals(20, plan.targetBuildingId());
        assertEquals(1, plan.prospectiveFloorNumber());
    }

    @Test
    void lowerTransitionOutsidePersistedFloorUsesPersistedFloorEvidenceInsteadOfSameFloorExpansion() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 3));
        Building room = room(100, 20, 0, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0)));
        Village village = village(persisted, room);
        BlockPos source = new BlockPos(4, 63, 0);
        FloorGeometry primary = scannedFloor(63, 68, 0, 4);

        RoomScanPlan plan = RoomScanPlanner.planFresh(
                village, source, observation(source, primary, Set.of(new BlockPos(0, 64, 0))));

        assertEquals(Village.RoomScanMode.ADD_ATTACHMENT, plan.mode());
        assertEquals(20, plan.targetBuildingId());
        assertEquals(-1, plan.prospectiveFloorNumber());
        assertTrue(plan.selectedAttachmentFloor() != null
                && plan.selectedAttachmentFloor().geometry().sameCellPositions(primary));
    }

    @Test
    void interactionBelowAnchorStaysOnCanonicalUpperFloor() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 3));
        Building room = room(100, 20, 0, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0)));
        Village village = village(persisted, room);
        BlockPos source = new BlockPos(4, 63, 0);
        FloorGeometry primary = new FloorGeometry(Set.of(
                new FloorGeometry.Cell(new BlockPos(0, 64, 0), 68),
                new FloorGeometry.Cell(new BlockPos(1, 64, 0), 68),
                new FloorGeometry.Cell(new BlockPos(2, 64, 0), 68),
                new FloorGeometry.Cell(new BlockPos(3, 64, 0), 68),
                new FloorGeometry.Cell(source, 68)), Map.of());
        FloorGeometry lower = scannedFloor(60, 64, 0, 4);

        RoomScanPlan plan = RoomScanPlanner.planFresh(
                village, source, observation(source, primary));

        assertEquals(Village.RoomScanMode.ADD_ROOM, plan.mode());
        assertEquals(20, plan.targetStructureId());
        assertEquals(0, plan.targetFloorId());
    }

    @Test
    void upperTransitionOutsidePersistedFloorUsesPersistedFloorEvidenceInsteadOfSameFloorExpansion() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 3));
        Building room = room(100, 20, 0, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0)));
        Village village = village(persisted, room);
        BlockPos source = new BlockPos(4, 67, 0);
        FloorGeometry primary = scannedFloor(65, 69, 0, 4);

        RoomScanPlan plan = RoomScanPlanner.planFresh(
                village, source, observation(source, primary, Set.of(new BlockPos(0, 64, 0))));

        assertEquals(Village.RoomScanMode.ADD_ATTACHMENT, plan.mode());
        assertEquals(20, plan.targetBuildingId());
        assertEquals(1, plan.prospectiveFloorNumber());
    }

    @Test
    void transitionEvidenceRemainsSelectedFloorLocal() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 3));
        Building room = room(100, 20, 0, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0)));
        Village village = village(persisted, room);
        BlockPos source = new BlockPos(4, 63, 0);
        FloorGeometry primary = scannedFloor(63, 68, 0, 4);

        RoomScanPlan plan = RoomScanPlanner.planFresh(village, source,
                observation(source, primary, Set.of(new BlockPos(0, 64, 0))));

        assertEquals(Village.RoomScanMode.ADD_ATTACHMENT, plan.mode());
        assertTrue(plan.selectedAttachmentFloor() != null
                && plan.selectedAttachmentFloor().geometry().sameCellPositions(primary),
                "attachment planning must keep the selected Floor instead of substituting another scanned Floor");
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

    @Test
    void persistedInteractionLookupUsesTheLevelAwareVillageResolver() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 3));
        Building room = room(100, 20, 0, Set.of(new BlockPos(0, 64, 0)));
        LevelAwareVillage village = new LevelAwareVillage(persisted, room);
        BlockPos source = new BlockPos(8, 70, 8);

        RoomScanPlan plan = RoomScanPlanner.plan(village, null, source);

        assertTrue(village.levelAwareResolverUsed);
        assertEquals(Village.RoomScanMode.UPDATE_ROOM, plan.mode());
        assertEquals(room, plan.currentRoom().orElseThrow());
    }

    @Test
    void unownedDoorCellDoesNotBorrowAdjacentRegisteredRoom() {
        BlockPos door = new BlockPos(4, 64, 0);
        FloorGeometry geometry = new FloorGeometry(Set.of(
                cell(0, 64), cell(1, 64), cell(2, 64), cell(3, 64), cell(4, 64),
                cell(5, 64), cell(6, 64), cell(7, 64), cell(8, 64)),
                Map.of(door, FloorConnector.Type.DOOR));
        StructureFloor persistedFloor = new StructureFloor(0, 0, geometry);
        Structure persisted = structure(20, 20, persistedFloor);
        Building room = room(100, 20, 0, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0)));
        Village village = village(persisted, room);

        RoomScanPlan plan = RoomScanPlanner.plan(village, null, door);

        assertEquals(Village.RoomScanMode.ADD_ROOM, plan.mode());
        assertEquals(20, plan.targetStructureId());
        assertEquals(0, plan.targetFloorId());
        assertTrue(plan.currentRoom().isEmpty());
    }

    private static Village village(Structure structure, Building room) {
        Village village = new Village(1, null);
        village.registerStructure(structure, room);
        return village;
    }

    private static StructureScanner.FloorObservation observation(BlockPos source, FloorGeometry floor) {
        return observation(source, floor, Set.of());
    }

    private static StructureScanner.FloorObservation observation(
            BlockPos source,
            FloorGeometry floor,
            Set<BlockPos> transitionSeeds) {
        BlockPos selectedCell = floor.interactionCellAt(source.getX(), source.getY(), source.getZ())
                .map(FloorGeometry.Cell::feet).orElse(source);
        return observation(selectedCell, source, floor, transitionSeeds);
    }

    private static StructureScanner.FloorObservation observation(
            BlockPos selectedCell,
            BlockPos supportedSource,
            FloorGeometry floor,
            Set<BlockPos> transitionSeeds) {
        return new StructureScanner.FloorObservation(
                new SelectedFloorScanner.Result(Building.validationResult.SUCCESS,
                floor, selectedCell, supportedSource, selectedCell, selectedCell,
                transitions(floor), Set.of(), transitionSeeds), List.of());
    }

    private static Structure structure(int id, int logicalBuildingId, StructureFloor floor) {
        Structure structure = new Structure(id, floor.geometry().cells().iterator().next().feet(), List.of(floor));
        structure.setLogicalBuildingId(logicalBuildingId);
        return structure;
    }

    private static StructureFloor floor(int id, int anchorY, int ceilingY, int minX, int maxX) {
        Set<BlockPos> cells = java.util.stream.IntStream.rangeClosed(minX, maxX)
                .mapToObj(x -> new BlockPos(x, anchorY, 0))
                .collect(java.util.stream.Collectors.toSet());
        return TestStructureFloors.create(id, anchorY, ceilingY, 0,
                TestFloorFootprint.fromFootprint(anchorY, cells));
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

    private static final class LevelAwareVillage extends Village {
        private final Structure structure;
        private final Building room;
        private boolean levelAwareResolverUsed;

        private LevelAwareVillage(Structure structure, Building room) {
            super(1, null);
            this.structure = structure;
            this.room = room;
        }

        @Override
        Optional<ResolvedInteraction> resolveInteractionPosition(Level level, BlockPos pos) {
            levelAwareResolverUsed = true;
            return Optional.of(new ResolvedInteraction(
                    structure,
                    new Structure.InteractionPosition(structure.getFloor(room.getFloorId()).orElseThrow(), room)
            ));
        }
    }
}
