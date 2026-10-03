package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageManagerExpectedTargetTest {
    private static final BlockPos SOURCE = new BlockPos(4, 70, -3);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void removeRoomRejectsAStaleExpectedRoomBeforeMutation() {
        TargetVillage village = new TargetVillage(room(22), 7);
        VillageManager manager = new TargetManager(village);

        VillageManager.BuildingEditResult result = manager.removeRoom(SOURCE, 21);

        assertEquals(VillageManager.BuildingEditResult.TARGET_CHANGED, result);
        assertFalse(village.roomRemoved);
    }

    @Test
    void removeFloorRejectsAStaleExpectedBuildingBeforeMutation() {
        TargetVillage village = new TargetVillage(room(22), 7);
        VillageManager manager = new TargetManager(village);

        VillageManager.BuildingEditResult result = manager.removeFloor(SOURCE, 2, 6);

        assertEquals(VillageManager.BuildingEditResult.TARGET_CHANGED, result);
        assertFalse(village.floorRemoved);
    }

    @Test
    void removeFloorRejectsAStaleExpectedPhysicalFloorBeforeMutation() {
        TargetVillage village = new TargetVillage(room(22), 7);
        VillageManager manager = new TargetManager(village);

        VillageManager.BuildingEditResult result = manager.removeFloor(SOURCE, 0, 7, 10, 99);

        assertEquals(VillageManager.BuildingEditResult.TARGET_CHANGED, result);
        assertFalse(village.floorRemoved);
    }

    @Test
    void removeEmptyPersistedFloorWithoutStandingInARoom() {
        for (int floorNumber : List.of(-1, 1)) {
            Village village = emptyFloorVillage(floorNumber);
            VillageManager manager = new TargetManager(village);
            BlockPos source = SOURCE.above(8 * floorNumber);
            assertTrue(village.findInteractionRoomAt(source).isEmpty());
            assertEquals(Village.RoomScanMode.ADD_ROOM, village.getRoomScanPlan(null, source).mode());

            assertEquals(VillageManager.BuildingEditResult.SUCCESS,
                    manager.removeFloor(source, floorNumber, 10, 10, 1));
            assertTrue(village.getStructure(10).orElseThrow().getFloor(1).isEmpty());
            assertTrue(village.getBuilding(22).isPresent());
        }
    }

    @Test
    void emptyFloorRemovalRejectsStaleTargetsWithoutMutation() {
        Village village = emptyFloorVillage(1);
        VillageManager manager = new TargetManager(village);
        BlockPos source = SOURCE.above(8);

        assertEquals(VillageManager.BuildingEditResult.TARGET_CHANGED,
                manager.removeFloor(source, 1, 11, 10, 1));
        assertEquals(VillageManager.BuildingEditResult.TARGET_CHANGED,
                manager.removeFloor(source, 1, 10, 10, 99));
        assertEquals(VillageManager.BuildingEditResult.TARGET_CHANGED,
                manager.removeFloor(source, -1, 10, 10, 1));
        assertTrue(village.getStructure(10).orElseThrow().getFloor(1).isPresent());
    }

    @Test
    void emptyFloorRemovalRequiresAnInteractionStructure() {
        Village village = emptyFloorVillage(1);
        VillageManager manager = new TargetManager(village);

        assertEquals(VillageManager.BuildingEditResult.NO_BUILDING,
                manager.removeFloor(SOURCE.offset(20, 8, 0), 1, 10, 10, 1));
        assertTrue(village.getStructure(10).orElseThrow().getFloor(1).isPresent());
    }

    @Test
    void emptyFloorRemovalCannotTargetAnotherLogicalBuilding() {
        Village village = emptyFloorVillage(1);
        BlockPos otherSource = SOURCE.offset(20, 0, 0);
        Structure other = new Structure(30, otherSource, List.of(new StructureFloor(0, 0,
                new FloorGeometry(List.of(new FloorGeometry.Cell(otherSource, otherSource.getY() + 4)), Map.of()))));
        Building otherRoom = room(32);
        otherRoom.setStructureId(30);
        otherRoom.setGeometry(otherSource, otherSource.above(3), Set.of(otherSource));
        village.registerStructure(other, otherRoom);
        VillageManager manager = new TargetManager(village);

        assertEquals(VillageManager.BuildingEditResult.TARGET_CHANGED,
                manager.removeFloor(otherSource, 1, 10, 10, 1));
        assertTrue(village.getStructure(10).orElseThrow().getFloor(1).isPresent());
    }

    @Test
    void unregisteredComponentCannotRemoveAFloorWithAnotherRegisteredRoom() {
        Village village = emptyFloorVillage(1);
        BlockPos source = SOURCE.above(8);
        Building upperRoom = room(23);
        upperRoom.setFloorId(1);
        BlockPos roomSource = source.east();
        upperRoom.setGeometry(roomSource, roomSource.above(3), Set.of(roomSource));
        village.registerRoom(upperRoom);
        VillageManager manager = new TargetManager(village);
        assertEquals(Village.RoomScanMode.ADD_ROOM, village.getRoomScanPlan(null, source).mode());

        assertEquals(VillageManager.BuildingEditResult.NO_FLOOR,
                manager.removeFloor(source, 1, 10, 10, 1));
        assertTrue(village.getStructure(10).orElseThrow().getFloor(1).isPresent());
        assertTrue(village.getBuilding(23).isPresent());
    }

    private static Village emptyFloorVillage(int floorNumber) {
        Village village = new Village(1, null);
        BlockPos upper = SOURCE.above(8 * floorNumber);
        Structure structure = new Structure(10, SOURCE, List.of(
                new StructureFloor(0, 0, new FloorGeometry(
                        List.of(new FloorGeometry.Cell(SOURCE, SOURCE.getY() + 4)), Map.of())),
                new StructureFloor(1, floorNumber, new FloorGeometry(
                        List.of(new FloorGeometry.Cell(upper, upper.getY() + 4),
                                new FloorGeometry.Cell(upper.east(), upper.getY() + 4)), Map.of()))));
        Building main = room(22);
        main.setGeometry(SOURCE, SOURCE.above(3), Set.of(SOURCE));
        village.registerStructure(structure, main);
        return village;
    }

    @Test
    void forceRoomTypeRejectsAStaleExpectedRoomBeforeMutation() {
        TargetVillage village = new TargetVillage(room(22), 7);
        VillageManager manager = new TargetManager(village);

        VillageManager.BuildingEditResult result = manager.forceRoomType(SOURCE, "blocked", 21);

        assertEquals(VillageManager.BuildingEditResult.TARGET_CHANGED, result);
        assertFalse(village.roomTypeChanged);
    }

    @Test
    void setMainRoomRejectsAStaleExpectedRoomBeforeMutation() {
        TargetVillage village = new TargetVillage(room(22), 7);
        VillageManager manager = new TargetManager(village);

        VillageManager.BuildingEditResult result = manager.setMainRoom(SOURCE, 21);

        assertEquals(VillageManager.BuildingEditResult.TARGET_CHANGED, result);
        assertFalse(village.mainRoomChanged);
    }

    @Test
    void removeBuildingRejectsAStaleExpectedLogicalBuildingBeforeMutation() {
        TargetVillage village = new TargetVillage(room(22), 7);
        VillageManager manager = new TargetManager(village);

        VillageManager.BuildingEditResult result = manager.removeBuilding(SOURCE, 6);

        assertEquals(VillageManager.BuildingEditResult.TARGET_CHANGED, result);
        assertFalse(village.logicalBuildingRemoved);
    }

    private static Building room(int id) {
        Building room = new Building(BlockPos.ZERO);
        room.setId(id);
        room.setStructureId(10);
        room.setFloorId(0);
        return room;
    }

    private static final class TargetManager extends VillageManager {
        private final Village village;

        private TargetManager(Village village) {
            super(null);
            this.village = village;
        }

        @Override
        public Optional<Village> findNearestVillage(BlockPos pos, int margin) {
            return Optional.of(village);
        }
    }

    private static final class TargetVillage extends Village {
        private final Building room;
        private final int logicalBuildingId;
        private final Structure structure;
        private boolean roomRemoved;
        private boolean floorRemoved;
        private boolean mainRoomChanged;
        private boolean logicalBuildingRemoved;
        private boolean roomTypeChanged;

        private TargetVillage(Building room, int logicalBuildingId) {
            super(1, null);
            this.room = room;
            this.logicalBuildingId = logicalBuildingId;
            StructureFloor floor = new StructureFloor(0, 0,
                    new FloorGeometry(List.of(new FloorGeometry.Cell(BlockPos.ZERO, 4)), Map.of()));
            structure = new Structure(room.getStructureId(), BlockPos.ZERO, List.of(floor));
            structure.setLogicalBuildingId(logicalBuildingId);
        }

        @Override
        public Optional<Building> findInteractionRoomAt(BlockPos pos) {
            return Optional.of(room);
        }

        @Override
        public RoomScanPlan getRoomScanPlan(Level level, BlockPos pos) {
            return RoomScanPlan.updateRoom(room, pos);
        }

        @Override
        public Optional<Structure> getStructure(int id) {
            return id == structure.getId() ? Optional.of(structure) : Optional.empty();
        }

        @Override
        public Optional<Building> getBuildingAt(Vec3i pos) {
            return Optional.of(room);
        }

        @Override
        public int getLogicalBuildingId(int structureId) {
            return logicalBuildingId;
        }

        @Override
        public Optional<Structure> getStructureFor(Building room) {
            return Optional.of(structure);
        }

        @Override
        public boolean isMainRoom(Building room) {
            return false;
        }

        @Override
        public boolean removeRoom(int roomId) {
            roomRemoved = true;
            return true;
        }

        @Override
        boolean removeFloor(int buildingId, int floorNumber) {
            floorRemoved = true;
            return true;
        }

        @Override
        public boolean setMainRoom(Building room) {
            mainRoomChanged = true;
            return true;
        }

        @Override
        void setRoomType(Building room, String type, boolean forced) {
            roomTypeChanged = true;
        }

        @Override
        void removeLogicalBuilding(int buildingId) {
            logicalBuildingRemoved = true;
        }
    }
}
