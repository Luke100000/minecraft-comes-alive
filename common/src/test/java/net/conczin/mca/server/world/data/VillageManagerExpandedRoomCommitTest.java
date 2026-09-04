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
                new StructureFloor(0, 64, 68, 0, oldRegion)));
        Building main = room(100, 10, 0, oldRegion);
        village.registerStructure(current, main);

        BuildingFloorRegion freshRegion = region(64, 0, 3);
        Structure refreshed = current.copy();
        assertTrue(refreshed.replaceFloorGeometry(0,
                new StructureFloor(0, 64, 68, 0, freshRegion)));

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
                new StructureFloor(0, 64, 68, 0, oldRegion)));
        Building main = room(100, 10, 0, oldRegion);
        village.registerStructure(current, main);

        Set<FloorGeometry.Cell> oldGeometry = Set.copyOf(
                current.getFloor(0).orElseThrow().geometry().cells());
        Set<BlockPos> oldRoomCells = Set.copyOf(main.getFloorCells());

        Structure refreshed = current.copy();
        assertTrue(refreshed.replaceFloorGeometry(0,
                new StructureFloor(0, 64, 68, 0, region(64, 0, 3))));
        Building invalidReplacement = room(100, 10, 0,
                BuildingFloorRegion.fromFootprint(64, Set.of(
                        new BlockPos(0, 64, 0), new BlockPos(4, 64, 0))));

        assertFalse(village.publishFloorRefresh(refreshed, 0, List.of(invalidReplacement)));
        assertEquals(oldGeometry, village.getStructure(10).orElseThrow()
                .getFloor(0).orElseThrow().geometry().cells());
        assertEquals(oldRoomCells, village.getBuilding(100).orElseThrow().getFloorCells());
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
