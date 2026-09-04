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
    void roomsCanOwnDifferentExactCellsInTheSameFloorColumn() {
        BlockPos lowerCell = new BlockPos(2, 88, 3);
        BlockPos upperCell = new BlockPos(2, 91, 3);
        FloorGeometry geometry = new FloorGeometry(Set.of(
                new FloorGeometry.Cell(lowerCell, 88, 90),
                new FloorGeometry.Cell(upperCell, 91, 93)), Map.of());
        StructureFloor floor = new StructureFloor(0, 0, geometry);
        Structure structure = new Structure(10, lowerCell, List.of(floor));
        Building lower = room(100, 10, 0, Set.of(lowerCell));
        Building upper = room(101, 10, 0, Set.of(upperCell));

        assertEquals(lower, structure.resolveInteractionPosition(lowerCell, List.of(lower, upper))
                .orElseThrow().room());
        assertEquals(upper, structure.resolveInteractionPosition(upperCell, List.of(lower, upper))
                .orElseThrow().room());
        assertTrue(lower.ownsFloorCell(lowerCell));
        assertFalse(lower.ownsFloorCell(upperCell));
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
                List.of(new FloorConnector.Marker(
                        new BlockPos(0, 84, 0), FloorConnector.Type.TRAPDOOR)));
        StructureFloor groundFloor = new StructureFloor(0, 88, 92, 0,
                BuildingFloorRegion.fromFootprint(88, Set.of(connectorColumn)),
                List.of(new FloorConnector.Marker(
                        connectorColumn, FloorConnector.Type.TRAPDOOR)));
        Structure basement = new Structure(10, new BlockPos(0, 84, 0), List.of(basementFloor));
        Structure ground = new Structure(11, connectorColumn, List.of(groundFloor));
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
        FloorGeometry geometry = new FloorGeometry(Set.of(
                geometryCell(0, 64, 0), geometryCell(1, 64, 0), geometryCell(2, 64, 0),
                geometryCell(3, 64, 0), geometryCell(4, 64, 0)),
                Map.of(connector, FloorConnector.Type.DOOR));
        List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry);
        RoomPartitioner.Component owner = components.stream()
                .filter(component -> component.contains(connector))
                .findFirst().orElseThrow();
        Set<BlockPos> roomFootprint = BuildingRoomScanner.floorCellsForComponent(owner);

        StructureFloor persistedFloor = new StructureFloor(0, 0, geometry);
        Structure structure = new Structure(10, new BlockPos(1, 64, 0), List.of(persistedFloor));
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
    void semanticCeilingComesFromNextFloorWhilePhysicalCeilingRemainsExact() {
        FloorGeometry lowerGeometry = new FloorGeometry(Set.of(
                geometryCell(0, 88, 0), geometryCell(1, 88, 0), geometryCell(2, 88, 0),
                geometryCell(3, 88, 0), geometryCell(3, 89, 1), geometryCell(3, 90, 2)), Map.of());
        FloorGeometry upperGeometry = new FloorGeometry(Set.of(
                geometryCell(0, 91, 3), geometryCell(1, 91, 3),
                geometryCell(2, 91, 3), geometryCell(3, 91, 3)), Map.of());
        StructureFloor lower = new StructureFloor(0, 0, lowerGeometry);
        StructureFloor upper = new StructureFloor(1, 1, upperGeometry);
        Structure structure = structure(lower, upper);

        assertEquals(94, lower.maxPhysicalCeilingY());
        assertEquals(91, structure.semanticCeilingY(lower));
    }

    @Test
    void reloadingCurrentRoomFootprintDoesNotAssignUnownedConnectorToRoom() {
        BlockPos connector = new BlockPos(1, 64, 0);
        StructureFloor floor = new StructureFloor(0, 64, 68, 0,
                BuildingFloorRegion.fromFootprint(64, Set.of(
                        new BlockPos(0, 64, 0), connector, new BlockPos(2, 64, 0),
                        new BlockPos(3, 64, 0), new BlockPos(4, 64, 0))),
                List.of(new FloorConnector.Marker(
                        connector, FloorConnector.Type.DOOR)));
        Structure structure = new Structure(10, BlockPos.ZERO, List.of(floor));
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
                List.of(new FloorConnector.Marker(
                        connector, FloorConnector.Type.DOOR)));
        Structure structure = new Structure(10, BlockPos.ZERO, List.of(floor));
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

    @Test
    void exactTransitionCellResolvesLowerFloorUnlessUpperFloorOwnsSameColumn() {
        StructureFloor lower = new StructureFloor(0, 0, new FloorGeometry(Set.of(
                new FloorGeometry.Cell(new BlockPos(0, 88, 0), 88, 91),
                new FloorGeometry.Cell(new BlockPos(1, 88, 0), 88, 91),
                new FloorGeometry.Cell(new BlockPos(1, 91, 0), 91, 93)), Map.of()));
        StructureFloor upper = new StructureFloor(1, 1, new FloorGeometry(Set.of(
                new FloorGeometry.Cell(new BlockPos(0, 91, 0), 91, 94)), Map.of()));
        Structure structure = structure(lower, upper);

        assertEquals(lower, structure.physicalFloorAt(new BlockPos(1, 91, 0)).orElseThrow());
        assertEquals(upper, structure.physicalFloorAt(new BlockPos(0, 91, 0)).orElseThrow());
    }

    private static Structure structure(StructureFloor... floors) {
        return new Structure(10, new BlockPos(0, 64, 0), List.of(floors));
    }

    private static StructureFloor floor(int id, int anchorY, int ceilingY) {
        BuildingFloorRegion region = BuildingFloorRegion.fromFootprint(
                anchorY, Set.of(new BlockPos(0, anchorY, 0)));
        return new StructureFloor(id, anchorY, ceilingY, id, region);
    }

    private static FloorGeometry.Cell geometryCell(int x, int y, int z) {
        return new FloorGeometry.Cell(new BlockPos(x, y, z), y, y + 4);
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
        int minY = footprint.stream().mapToInt(BlockPos::getY).min().orElseThrow();
        int maxY = footprint.stream().mapToInt(BlockPos::getY).max().orElseThrow() + 3;
        room.setGeometry(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), footprint);
        return room;
    }
}
