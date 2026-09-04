package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureExpansionPolicyTest {
    @Test
    void selectsUniquePersistedFloorOverlappedByFreshSameStoreyGeometry() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 1));
        ScannedFloor fresh = scannedFloor(64, 68, 0, 3);

        StructureExpansionPolicy.FloorTarget target = StructureExpansionPolicy
                .selectSameStoreyTarget(List.of(persisted), fresh)
                .orElseThrow();

        assertEquals(20, target.structure().getId());
        assertEquals(0, target.floor().id());
    }

    @Test
    void rejectsAmbiguousExpansionAcrossTwoPersistedStructures() {
        Structure first = structure(20, 20, floor(0, 64, 68, 0, 1));
        Structure second = structure(30, 30, floor(0, 64, 68, 2, 3));
        ScannedFloor fresh = scannedFloor(64, 68, 0, 4);

        assertTrue(StructureExpansionPolicy.selectSameStoreyTarget(
                List.of(first, second), fresh).isEmpty());
    }

    @Test
    void doesNotTreatDifferentSemanticBandAsSameStoreyExpansion() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 3));
        ScannedFloor freshUpper = scannedFloor(68, 72, 0, 3);

        assertTrue(StructureExpansionPolicy.selectSameStoreyTarget(
                List.of(persisted), freshUpper).isEmpty());
    }

    @Test
    void freshTransitionComponentResolvesExistingRegisteredRoom() {
        Structure persisted = structure(20, 20, floor(0, 88, 91, 0, 3));
        StructureExpansionPolicy.FloorTarget target =
                new StructureExpansionPolicy.FloorTarget(persisted, persisted.getFloor(0).orElseThrow());
        Building room = room(100, 20, 0, Set.of(
                new BlockPos(0, 88, 0), new BlockPos(1, 88, 0),
                new BlockPos(2, 88, 0), new BlockPos(3, 88, 0)));
        BlockPos topStair = new BlockPos(6, 91, 0);
        FloorSurface fresh = new FloorSurface(Set.of(
                cell(0, 88, 0), cell(1, 88, 0), cell(2, 88, 0), cell(3, 88, 0),
                cell(4, 89, 0), cell(5, 90, 0), cell(6, 91, 0)), Map.of());

        assertEquals(room, StructureExpansionPolicy.registeredRoomForFreshComponent(
                target, new ScannedFloor(fresh, 91), topStair, List.of(room)).orElseThrow());
    }

    @Test
    void freshComponentAcrossDoorBoundaryRemainsANewRoom() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 8));
        StructureExpansionPolicy.FloorTarget target =
                new StructureExpansionPolicy.FloorTarget(persisted, persisted.getFloor(0).orElseThrow());
        Building existing = room(100, 20, 0, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0)));
        BlockPos doorCell = new BlockPos(4, 64, 0);
        BlockPos newRoomCell = new BlockPos(5, 64, 0);
        FloorSurface fresh = new FloorSurface(Set.of(
                cell(0, 64, 0), cell(1, 64, 0), cell(2, 64, 0), cell(3, 64, 0),
                cell(5, 64, 0), cell(6, 64, 0), cell(7, 64, 0), cell(8, 64, 0)),
                Map.of(doorCell, StructureFloor.ConnectorType.DOOR));

        assertTrue(StructureExpansionPolicy.registeredRoomForFreshComponent(
                target, ScannedFloor.physical(fresh), newRoomCell, List.of(existing)).isEmpty());
    }

    @Test
    void sharedDoorCellAloneDoesNotMakeNewComponentAnExistingRoom() {
        Structure persisted = structure(20, 20, floor(0, 64, 68, 0, 8));
        StructureExpansionPolicy.FloorTarget target =
                new StructureExpansionPolicy.FloorTarget(persisted, persisted.getFloor(0).orElseThrow());
        BlockPos doorCell = new BlockPos(4, 64, 0);
        Building existing = room(100, 20, 0, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0), doorCell));
        BlockPos newRoomCell = new BlockPos(5, 64, 0);
        FloorSurface fresh = new FloorSurface(Set.of(
                cell(0, 64, 0), cell(1, 64, 0), cell(2, 64, 0), cell(3, 64, 0),
                cell(5, 64, 0), cell(6, 64, 0), cell(7, 64, 0), cell(8, 64, 0),
                cell(9, 64, 0), cell(10, 64, 0)),
                Map.of(doorCell, StructureFloor.ConnectorType.DOOR));

        assertTrue(StructureExpansionPolicy.registeredRoomForFreshComponent(
                target, ScannedFloor.physical(fresh), newRoomCell, List.of(existing)).isEmpty());
    }

    private static Structure structure(int id, int logicalBuildingId, StructureFloor floor) {
        Structure structure = new Structure(id, floor.region().cells().iterator().next(), List.of(floor));
        structure.setLogicalBuildingId(logicalBuildingId);
        return structure;
    }

    private static StructureFloor floor(int id, int anchorY, int ceilingY, int minX, int maxX) {
        List<BlockPos> cells = java.util.stream.IntStream.rangeClosed(minX, maxX)
                .mapToObj(x -> new BlockPos(x, anchorY, 0))
                .toList();
        return new StructureFloor(id, anchorY, ceilingY, 0,
                BuildingFloorRegion.fromFootprint(anchorY, cells));
    }

    private static ScannedFloor scannedFloor(int anchorY, int ceilingY, int minX, int maxX) {
        Set<FloorSurface.Cell> cells = java.util.stream.IntStream.rangeClosed(minX, maxX)
                .mapToObj(x -> new FloorSurface.Cell(new BlockPos(x, anchorY, 0), anchorY, ceilingY))
                .collect(java.util.stream.Collectors.toSet());
        return new ScannedFloor(new FloorSurface(cells, Map.of()), ceilingY);
    }

    private static Building room(int id, int structureId, int floorId, Set<BlockPos> footprint) {
        Building room = new Building(footprint.iterator().next());
        room.setId(id);
        room.setStructureId(structureId);
        room.setFloorId(floorId);
        int minX = footprint.stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int maxX = footprint.stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int minZ = footprint.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int maxZ = footprint.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        int y = footprint.iterator().next().getY();
        room.setGeometry(new BlockPos(minX, y, minZ), new BlockPos(maxX, y + 3, maxZ),
                BuildingFloorRegion.fromFootprint(y, footprint));
        return room;
    }

    private static FloorSurface.Cell cell(int x, int y, int z) {
        return new FloorSurface.Cell(new BlockPos(x, y, z), y, y + 4);
    }
}
