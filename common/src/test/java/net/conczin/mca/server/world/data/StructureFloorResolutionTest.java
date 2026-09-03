package net.conczin.mca.server.world.data;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureFloorResolutionTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void structureBoundsAreDerivedFromFloorGeometryAndNotPersistedSeparately() {
        StructureFloor floor = new StructureFloor(0, 64, 70, 0,
                BuildingFloorRegion.fromFootprint(64, Set.of(
                        new BlockPos(2, 64, 3), new BlockPos(5, 64, 7))));
        Structure structure = new Structure(10, new BlockPos(2, 64, 3), List.of(floor));

        assertEquals(new BlockPos(2, 64, 3), structure.getRawPos0());
        assertEquals(new BlockPos(5, 69, 7), structure.getRawPos1());
        assertFalse(structure.save().contains("min"));
        assertFalse(structure.save().contains("max"));
    }

    @Test
    void directPositionUsesItsVerticalFloorBandWhenFloorsShareTheSameColumn() {
        StructureFloor lower = floor(0, 64, 68);
        StructureFloor upper = floor(1, 68, 72);
        Structure structure = structure(lower, upper);

        assertEquals(lower, structure.resolveFloorAt(new BlockPos(0, 67, 0)).orElseThrow());
        assertEquals(upper, structure.resolveFloorAt(new BlockPos(0, 68, 0)).orElseThrow());
    }

    @Test
    void directPositionOutsideEveryFloorBandDoesNotSnapToNearestFloorByColumn() {
        StructureFloor floor = floor(0, 64, 68);
        Structure structure = structure(floor);

        assertTrue(structure.resolveFloorAt(new BlockPos(0, 80, 0)).isEmpty(),
                "Y must choose a real Floor band before X/Z can resolve Room ownership");
    }

    @Test
    void interactionOnSupportBlockImmediatelyBelowFloorUsesPersistedFloorGeometry() {
        StructureFloor floor = floor(0, 64, 68);
        Structure structure = structure(floor);
        Building room = room(100, 10, 0, Set.of(new BlockPos(0, 64, 0)));

        Structure.InteractionPosition interaction = structure.resolveInteractionPosition(
                new BlockPos(0, 63, 0), List.of(room)).orElseThrow();

        assertEquals(floor, interaction.floor());
        assertEquals(room, interaction.room());
    }

    @Test
    void verticalConnectorBlockImmediatelyBelowGroundFloorResolvesToGroundFloor() {
        BlockPos connectorColumn = new BlockPos(0, 88, 0);
        StructureFloor basementFloor = new StructureFloor(0, 84, 87, -1,
                BuildingFloorRegion.fromFootprint(84, Set.of(new BlockPos(0, 84, 0))),
                List.of(new StructureFloor.ConnectorMarker(
                        new BlockPos(0, 84, 0), StructureFloor.ConnectorType.TRAPDOOR)));
        StructureFloor groundFloor = new StructureFloor(0, 88, 92, 0,
                BuildingFloorRegion.fromFootprint(88, Set.of(connectorColumn)),
                List.of(new StructureFloor.ConnectorMarker(
                        connectorColumn, StructureFloor.ConnectorType.TRAPDOOR)));
        Structure basement = new Structure(10, new BlockPos(0, 84, 0),
                new BlockPos(0, 84, 0), new BlockPos(0, 86, 0), List.of(basementFloor));
        Structure ground = new Structure(11, connectorColumn,
                connectorColumn, new BlockPos(0, 91, 0), List.of(groundFloor));
        basement.setLogicalBuildingId(10);
        ground.setLogicalBuildingId(10);

        Building groundRoom = room(100, 11, 0, Set.of(connectorColumn));
        Building basementRoom = room(101, 10, 0, Set.of(new BlockPos(0, 84, 0)));
        Village village = new Village(1, null);
        village.registerStructure(ground, groundRoom);
        village.registerStructure(basement, basementRoom);

        BlockPos trapdoorBlock = new BlockPos(0, 87, 0);
        RoomScanPlan plan = village.getRoomScanPlan(null, trapdoorBlock);

        assertEquals(Village.RoomScanMode.UPDATE_ROOM, plan.mode());
        assertEquals(groundRoom, plan.currentRoom().orElseThrow());
    }

    @Test
    void persistedConnectorColumnKeepsRoomPlanOnExistingBuilding() {
        BlockPos connector = new BlockPos(0, 64, 0);
        FloorSurface surface = new FloorSurface(Set.of(
                cell(1, 64, 0), cell(2, 64, 0), cell(3, 64, 0), cell(4, 64, 0)),
                Map.of(connector, StructureFloor.ConnectorType.DOOR));
        List<FloorSurfacePartitioner.Component> components = FloorSurfacePartitioner.partition(surface);
        FloorSurfacePartitioner.Component owner = components.stream()
                .filter(component -> component.containsColumn(connector.getX(), connector.getZ()))
                .findFirst().orElseThrow();
        Set<BlockPos> roomFootprint = BuildingRoomScanner.footprintForComponent(owner, 64);

        StructureFloor persistedFloor = StructureScanner.persistedFloor(surface);
        Structure structure = new Structure(10, new BlockPos(1, 64, 0),
                new BlockPos(0, 64, 0), new BlockPos(4, 67, 0), List.of(persistedFloor));
        Building room = new Building(new BlockPos(1, 64, 0));
        room.setId(100);
        room.setStructureId(10);
        room.setFloorId(0);
        room.setGeometry(new BlockPos(0, 64, 0), new BlockPos(4, 67, 0),
                BuildingFloorRegion.fromFootprint(64, roomFootprint));
        Village village = new Village(1, null);
        village.registerStructure(structure, room);

        Structure.InteractionPosition interaction = structure
                .resolveInteractionPosition(connector, List.of(room)).orElseThrow();
        RoomScanPlan plan = village.getRoomScanPlan(null, connector);

        assertEquals(persistedFloor, interaction.floor());
        assertEquals(room, interaction.room());
        assertEquals(Village.RoomScanMode.UPDATE_ROOM, plan.mode());
        assertEquals(room, plan.currentRoom().orElseThrow());
    }

    @Test
    void reloadingCurrentRoomFootprintDoesNotAssignUnownedConnectorToRoom() {
        BlockPos connector = new BlockPos(1, 64, 0);
        StructureFloor floor = new StructureFloor(0, 64, 68, 0,
                BuildingFloorRegion.fromFootprint(64, Set.of(
                        new BlockPos(0, 64, 0), connector, new BlockPos(2, 64, 0),
                        new BlockPos(3, 64, 0), new BlockPos(4, 64, 0))),
                List.of(new StructureFloor.ConnectorMarker(
                        connector, StructureFloor.ConnectorType.DOOR)));
        Structure structure = new Structure(10, BlockPos.ZERO,
                new BlockPos(0, 64, 0), new BlockPos(4, 67, 0), List.of(floor));
        Building room = new Building(new BlockPos(2, 64, 0));
        room.setId(100);
        room.setStructureId(10);
        room.setFloorId(0);
        room.setGeometry(new BlockPos(2, 64, 0), new BlockPos(4, 67, 0),
                BuildingFloorRegion.fromFootprint(64, Set.of(
                        new BlockPos(2, 64, 0), new BlockPos(3, 64, 0), new BlockPos(4, 64, 0))));
        Village legacy = new Village(1, null);
        legacy.registerStructure(structure, room);

        Village loaded = new Village(legacy.save(), null);
        RoomScanPlan plan = loaded.getRoomScanPlan(null, connector);

        assertEquals(Village.RoomScanMode.ADD_ROOM, plan.mode());
        assertTrue(plan.currentRoom().isEmpty());
    }

    @Test
    void reloadingCurrentSharedConnectorDoesNotInventRoomOwnership() {
        BlockPos connector = new BlockPos(2, 64, 0);
        StructureFloor floor = new StructureFloor(0, 64, 68, 0,
                BuildingFloorRegion.fromFootprint(64, Set.of(
                        new BlockPos(0, 64, 0), new BlockPos(1, 64, 0), connector,
                        new BlockPos(3, 64, 0), new BlockPos(4, 64, 0), new BlockPos(5, 64, 0))),
                List.of(new StructureFloor.ConnectorMarker(
                        connector, StructureFloor.ConnectorType.DOOR)));
        Structure structure = new Structure(10, BlockPos.ZERO,
                new BlockPos(0, 64, 0), new BlockPos(5, 67, 0), List.of(floor));
        Building smaller = room(100, 10, 0, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0)));
        Building larger = room(101, 10, 0, Set.of(
                new BlockPos(3, 64, 0), new BlockPos(4, 64, 0), new BlockPos(5, 64, 0)));
        Village legacy = new Village(1, null);
        legacy.registerStructure(structure, smaller);
        legacy.registerRoom(larger);

        Village loaded = new Village(legacy.save(), null);
        RoomScanPlan plan = loaded.getRoomScanPlan(null, connector);

        assertEquals(Village.RoomScanMode.ADD_ROOM, plan.mode());
        assertTrue(plan.currentRoom().isEmpty());
    }

    private static Structure structure(StructureFloor... floors) {
        return new Structure(10, new BlockPos(0, 64, 0), BlockPos.ZERO, BlockPos.ZERO, List.of(floors));
    }

    private static StructureFloor floor(int id, int anchorY, int ceilingY) {
        BuildingFloorRegion region = BuildingFloorRegion.fromFootprint(
                anchorY, Set.of(new BlockPos(0, anchorY, 0)));
        return new StructureFloor(id, anchorY, ceilingY, id, region);
    }

    private static FloorSurface.Cell cell(int x, int y, int z) {
        return new FloorSurface.Cell(new BlockPos(x, y, z), y, y + 4);
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
        room.setGeometry(new BlockPos(minX, 64, minZ), new BlockPos(maxX, 67, maxZ),
                BuildingFloorRegion.fromFootprint(64, footprint));
        return room;
    }
}
