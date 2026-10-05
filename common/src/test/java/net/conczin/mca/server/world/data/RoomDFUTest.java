package net.conczin.mca.server.world.data;

import com.google.gson.JsonObject;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.util.NbtHelper;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomDFUTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void installBuildingTypes() {
        BuildingTypes types = new BuildingTypes();
        JsonObject houseJson = new JsonObject();
        JsonObject graveyardJson = new JsonObject();
        graveyardJson.addProperty("grouped", true);
        types.setBuildingTypes(Map.of(
                "house", new BuildingType("house", houseJson),
                "graveyard", new BuildingType("graveyard", graveyardJson)));
    }

    @Test
    void originFunctionalBuildingKeepsIdAndCreatesExplicitLogicalBuilding() {
        CompoundTag village = new CompoundTag();
        village.put("buildings", list(originBuilding(7, "house")));

        RoomDFU.Result migrated = RoomDFU.load(village);

        Building room = migrated.buildings().get(7);
        assertEquals(7, room.getId());
        assertEquals(7, room.getStructureId());
        assertEquals(0, room.getFloorId());
        LogicalBuilding logical = migrated.logicalBuildings().get(7);
        assertEquals(7, logical.mainRoomId());
        assertTrue(logical.inheritanceEnabled());
    }

    @Test
    void originBoundingBoxMigrationUsesRectangularApproximationUntilRescan() {
        CompoundTag village = new CompoundTag();
        village.put("buildings", list(originBuilding(7, "house")));

        Building room = RoomDFU.load(village).buildings().get(7);

        assertEquals(Set.of(
                new BlockPos(0, 64, 0), new BlockPos(0, 64, 1),
                new BlockPos(0, 64, 2), new BlockPos(0, 64, 3),
                new BlockPos(1, 64, 0), new BlockPos(1, 64, 1),
                new BlockPos(1, 64, 2), new BlockPos(1, 64, 3),
                new BlockPos(2, 64, 0), new BlockPos(2, 64, 1),
                new BlockPos(2, 64, 2), new BlockPos(2, 64, 3),
                new BlockPos(3, 64, 0), new BlockPos(3, 64, 1),
                new BlockPos(3, 64, 2), new BlockPos(3, 64, 3)),
                room.getFloorCells());
    }

    @Test
    void originApproximationMarkerPersistsOnlyForOriginMigratedStructures() {
        CompoundTag origin = new CompoundTag();
        origin.put("buildings", list(originBuilding(7, "house")));
        Structure migratedOrigin = RoomDFU.load(origin).structures().get(7);

        CompoundTag savedOrigin = migratedOrigin.save();
        assertTrue(savedOrigin.getBoolean("originGeometryApproximate"));
        assertTrue(new Structure(savedOrigin).save().getBoolean("originGeometryApproximate"));

        Structure migratedFloorClean = RoomDFU.load(upstreamFloorCleanSquashVillage(true))
                .structures().get(20);
        Structure canonical = RoomDFU.load(canonicalVillage().save()).structures().get(20);
        assertFalse(migratedFloorClean.save().contains("originGeometryApproximate"));
        assertFalse(canonical.save().contains("originGeometryApproximate"));
    }

    @Test
    void originGroupedBuildingBecomesExternalOnly() {
        CompoundTag village = new CompoundTag();
        village.put("buildings", list(originBuilding(8, "graveyard")));

        RoomDFU.Result migrated = RoomDFU.load(village);

        assertTrue(migrated.buildings().isEmpty());
        assertTrue(migrated.structures().isEmpty());
        assertTrue(migrated.logicalBuildings().isEmpty());
        assertTrue(migrated.externalBuildings().containsKey(8));
    }

    @Test
    void originBlocks2CompoundPositionsAreNormalized() {
        CompoundTag building = originBuilding(9, "house");
        CompoundTag blocks = new CompoundTag();
        CompoundTag legacyPos = new CompoundTag();
        legacyPos.putInt("x", 2);
        legacyPos.putInt("y", 65);
        legacyPos.putInt("z", 3);
        blocks.put("minecraft:bell", list(legacyPos));
        building.put("blocks2", blocks);
        CompoundTag village = new CompoundTag();
        village.put("buildings", list(building));

        Building room = RoomDFU.load(village).buildings().get(9);

        assertEquals(List.of(new BlockPos(2, 65, 3)),
                room.getBlocks().get(ResourceLocation.parse("minecraft:bell")));
    }

    @Test
    void upstreamFloorCleanSquashInheritanceBecomesLogicalAndRoomState() {
        CompoundTag village = upstreamFloorCleanSquashVillage(false);

        RoomDFU.Result migrated = RoomDFU.load(village);

        LogicalBuilding logical = migrated.logicalBuildings().get(20);
        assertEquals(10, logical.mainRoomId());
        assertFalse(logical.inheritanceEnabled());
        assertTrue(migrated.buildings().get(11).contributesToMain());
        assertFalse(migrated.buildings().get(12).contributesToMain());
    }

    @Test
    void upstreamFloorCleanSquashGroundFloorIsDerivedFromMigratedMainRoom() {
        RoomDFU.Result migrated = RoomDFU.load(upstreamFloorCleanSquashVillage(true));

        LogicalBuilding logical = migrated.logicalBuildings().get(20);
        assertEquals(10, logical.mainRoomId());
        assertEquals(20, migrated.buildings().get(logical.mainRoomId()).getStructureId());
        assertEquals(0, migrated.buildings().get(logical.mainRoomId()).getFloorId());
    }

    @Test
    void upstreamFloorCleanSquashMigratesProjectedRegionsToExactFlatCells() {
        RoomDFU.Result migrated = RoomDFU.load(upstreamFloorCleanSquashVillage(true));
        StructureFloor floor = migrated.structures().get(20).getFloor(0).orElseThrow();
        Set<BlockPos> expected = Set.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0));

        assertEquals(expected, floor.geometry().cells().stream()
                .map(FloorGeometry.Cell::feet)
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(expected, migrated.buildings().get(10).getFloorCells());
    }

    @Test
    void normalizedSavesDoNotEmitLegacyAutomaticOrRoomInheritanceFields() {
        RoomDFU.Result migrated = RoomDFU.load(upstreamFloorCleanSquashVillage(true));

        CompoundTag roomTag = migrated.buildings().get(11).save();
        CompoundTag structureTag = migrated.structures().get(20).save();
        CompoundTag logicalTag = migrated.logicalBuildings().get(20).save();
        CompoundTag floorTag = structureTag.getList("floors", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0);
        assertTrue(roomTag.contains("contributesToMain"));
        assertFalse(roomTag.contains("inheritanceEnabled"));
        assertFalse(roomTag.contains("size"));
        assertFalse(structureTag.contains("mainRoomId"));
        assertFalse(structureTag.contains("mainRoomAutomatic"));
        assertFalse(structureTag.contains("surfaceReferenceY"));
        assertTrue(floorTag.contains("floorNumber", net.minecraft.nbt.Tag.TAG_INT));
        assertTrue(logicalTag.contains("mainRoomId"));
        assertTrue(logicalTag.contains("inheritanceEnabled"));
        assertFalse(logicalTag.contains("groundStructureId"));
        assertFalse(logicalTag.contains("groundFloorId"));
    }

    @Test
    void canonicalRoomAndStructureGeometryRoundTripsExactly() {
        TestFloorFootprint footprint = TestFloorFootprint.fromFootprint(64, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(0, 64, 1), new BlockPos(1, 64, 1)));
        StructureFloor floor = TestStructureFloors.create(0, 64, 70, -2, footprint);
        Structure structure = new Structure(20, new BlockPos(0, 64, 0), List.of(floor));
        structure.setLogicalBuildingId(77);

        Building room = new Building(new BlockPos(0, 64, 0));
        room.setId(10);
        room.setStructureId(20);
        room.setFloorId(0);
        room.setType("house");
        room.setGeometry(new BlockPos(0, 64, 0), new BlockPos(1, 69, 1), footprint.cells());
        room.addBlock(Blocks.BELL, new BlockPos(0, 65, 0));

        Structure reloadedStructure = new Structure(structure.save());
        Building reloadedRoom = new Building(room.save());

        assertEquals(structure.getId(), reloadedStructure.getId());
        assertEquals(structure.getLogicalBuildingId(), reloadedStructure.getLogicalBuildingId());
        assertEquals(structure.getFloor(0).orElseThrow().floorNumber(),
                reloadedStructure.getFloor(0).orElseThrow().floorNumber());
        assertEquals(room.getFloorCells(), reloadedRoom.getFloorCells());
        assertEquals(room.getStructureId(), reloadedRoom.getStructureId());
        assertEquals(room.getFloorId(), reloadedRoom.getFloorId());
        assertEquals(room.getBlocks(), reloadedRoom.getBlocks());
    }

    @Test
    void canonicalVillageSaveUsesCurrentVersionWithoutSurfaceHeight() {
        Village village = canonicalVillage();
        CompoundTag saved = village.save();

        assertEquals(Village.BUILDING_DATA_VERSION, saved.getInt("buildingDataVersion"));
        CompoundTag cell = firstCanonicalFloorCell(saved);
        assertTrue(cell.contains("pos"));
        assertTrue(cell.contains("ceilingY", net.minecraft.nbt.Tag.TAG_INT));
        assertFalse(cell.contains("surfaceY"));
    }

    @Test
    void canonicalVillageLoadsThroughRoomDfu() {
        Village village = canonicalVillage();
        Building room = village.getBuilding(10).orElseThrow();
        StructureFloor floor = village.getStructure(20).orElseThrow().getFloor(0).orElseThrow();

        RoomDFU.Result loaded = RoomDFU.load(village.save());

        assertEquals(room.getFloorCells(), loaded.buildings().get(10).getFloorCells());
        assertEquals(floor.geometry().cells(), loaded.structures().get(20)
                .getFloor(0).orElseThrow().geometry().cells());
    }

    @Test
    void unsupportedCanonicalVersionIsRejectedInsteadOfMigrated() {
        Village village = new Village(1, null);
        CompoundTag unsupported = village.save();
        unsupported.putInt("buildingDataVersion", Village.BUILDING_DATA_VERSION + 1);

        assertThrows(IllegalArgumentException.class, () -> RoomDFU.load(unsupported));
    }

    @Test
    void canonicalStructureMissingBuildingIdIsRejectedAtDfuBoundary() {
        CompoundTag malformed = canonicalVillage().save();
        malformed.getList("structures", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0).remove("buildingId");

        assertThrows(IllegalArgumentException.class, () -> RoomDFU.load(malformed));
    }

    @Test
    void canonicalRoomMissingIdIsRejectedAtDfuBoundary() {
        CompoundTag malformed = canonicalVillage().save();
        malformed.getList("buildings", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0).remove("id");

        assertThrows(IllegalArgumentException.class, () -> RoomDFU.load(malformed));
    }

    @Test
    void canonicalExternalBuildingMissingIdIsRejectedAtDfuBoundary() {
        Village village = canonicalVillage();
        ExternalBuilding external = new ExternalBuilding(new BlockPos(20, 64, 20));
        external.setId(30);
        external.setType("graveyard");
        village.registerExternalBuilding(external);
        CompoundTag malformed = village.save();
        malformed.getList("externalBuildings", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0).remove("id");

        assertThrows(IllegalArgumentException.class, () -> RoomDFU.load(malformed));
    }

    @Test
    void canonicalStructureMissingIdIsRejectedAtDfuBoundary() {
        CompoundTag malformed = canonicalVillage().save();
        malformed.getList("structures", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0).remove("id");

        assertThrows(IllegalArgumentException.class, () -> RoomDFU.load(malformed));
    }

    @Test
    void canonicalStructureFloorMissingIdIsRejectedAtDfuBoundary() {
        CompoundTag malformed = canonicalVillage().save();
        malformed.getList("structures", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0).getList("floors", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0).remove("id");

        assertThrows(IllegalArgumentException.class, () -> RoomDFU.load(malformed));
    }

    @Test
    void canonicalLogicalBuildingMissingIdIsRejectedAtDfuBoundary() {
        CompoundTag malformed = canonicalVillage().save();
        malformed.getList("logicalBuildings", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0).remove("id");

        assertThrows(IllegalArgumentException.class, () -> RoomDFU.load(malformed));
    }

    @Test
    void canonicalStructureAllowsMissingNextFloorId() {
        CompoundTag compatible = canonicalVillage().save();
        compatible.getList("structures", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0).remove("nextFloorId");

        assertDoesNotThrow(() -> RoomDFU.load(compatible));
    }

    @Test
    void originGeometryRefreshBackoffIsBounded() {
        assertEquals(40L, VillageManager.originGeometryRefreshDelay(1));
        assertEquals(80L, VillageManager.originGeometryRefreshDelay(2));
        assertEquals(VillageManager.ORIGIN_GEOMETRY_REFRESH_MAX_DELAY,
                VillageManager.originGeometryRefreshDelay(30));
    }

    @Test
    void canonicalRoomMissingFloorCellsIsRejectedAtDfuBoundary() {
        CompoundTag malformed = canonicalVillage().save();
        malformed.getList("buildings", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0).remove("floorCells");

        assertThrows(IllegalArgumentException.class, () -> RoomDFU.load(malformed));
    }

    @Test
    void canonicalRoomWithEmptyFloorCellsIsRejectedAtDfuBoundary() {
        CompoundTag malformed = canonicalVillage().save();
        malformed.getList("buildings", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0).put("floorCells", new ListTag());

        assertThrows(IllegalArgumentException.class, () -> RoomDFU.load(malformed));
    }

    @Test
    void duplicateCanonicalRoomIdsAreRejectedBeforeMapInsertion() {
        CompoundTag malformed = canonicalVillage().save();
        duplicateFirst(malformed.getList("buildings", net.minecraft.nbt.Tag.TAG_COMPOUND));

        assertThrows(IllegalArgumentException.class, () -> RoomDFU.load(malformed));
    }

    @Test
    void canonicalVillageLoadRejectsOverlappingRoomOwnership() {
        CompoundTag malformed = canonicalVillage().save();
        ListTag rooms = malformed.getList("buildings", net.minecraft.nbt.Tag.TAG_COMPOUND);
        CompoundTag overlapping = rooms.getCompound(0).copy();
        overlapping.putInt("id", 11);
        rooms.add(overlapping);

        assertThrows(IllegalArgumentException.class, () -> RoomDFU.load(malformed));
    }

    @Test
    void duplicateCanonicalExternalBuildingIdsAreRejectedBeforeMapInsertion() {
        Village village = canonicalVillage();
        ExternalBuilding external = new ExternalBuilding(new BlockPos(20, 64, 20));
        external.setId(30);
        external.setType("graveyard");
        village.registerExternalBuilding(external);
        CompoundTag malformed = village.save();
        duplicateFirst(malformed.getList("externalBuildings", net.minecraft.nbt.Tag.TAG_COMPOUND));

        assertThrows(IllegalArgumentException.class, () -> RoomDFU.load(malformed));
    }

    @Test
    void duplicateCanonicalStructureIdsAreRejectedBeforeMapInsertion() {
        CompoundTag malformed = canonicalVillage().save();
        duplicateFirst(malformed.getList("structures", net.minecraft.nbt.Tag.TAG_COMPOUND));

        assertThrows(IllegalArgumentException.class, () -> RoomDFU.load(malformed));
    }

    @Test
    void duplicateCanonicalFloorIdsAreRejectedBeforeNestedMapInsertion() {
        CompoundTag malformed = canonicalVillage().save();
        ListTag floors = malformed.getList("structures", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0).getList("floors", net.minecraft.nbt.Tag.TAG_COMPOUND);
        duplicateFirst(floors);

        assertThrows(IllegalArgumentException.class, () -> RoomDFU.load(malformed));
    }

    @Test
    void duplicateCanonicalLogicalBuildingIdsAreRejectedBeforeMapInsertion() {
        CompoundTag malformed = canonicalVillage().save();
        duplicateFirst(malformed.getList("logicalBuildings", net.minecraft.nbt.Tag.TAG_COMPOUND));

        assertThrows(IllegalArgumentException.class, () -> RoomDFU.load(malformed));
    }

    @Test
    void canonicalFloorCellMissingCeilingYIsRejectedAtDfuBoundary() {
        CompoundTag malformed = canonicalVillage().save();
        firstCanonicalFloorCell(malformed).remove("ceilingY");

        assertThrows(IllegalArgumentException.class, () -> RoomDFU.load(malformed));
    }

    @Test
    void canonicalFloorCellRejectsCeilingAtOrBelowFeet() {
        CompoundTag malformed = canonicalVillage().save();
        firstCanonicalFloorCell(malformed).putInt("ceilingY", 64);

        assertThrows(IllegalArgumentException.class, () -> RoomDFU.load(malformed));
    }

    @Test
    void canonicalLogicalBuildingMissingInheritanceIsRejectedAtDfuBoundary() {
        CompoundTag malformed = canonicalVillage().save();
        malformed.getList("logicalBuildings", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0).remove("inheritanceEnabled");

        assertThrows(IllegalArgumentException.class, () -> RoomDFU.load(malformed));
    }

    private static CompoundTag upstreamFloorCleanSquashVillage(boolean mainInheritanceEnabled) {
        CompoundTag village = new CompoundTag();
        CompoundTag main = upstreamFloorCleanSquashRoom(10, mainInheritanceEnabled);
        CompoundTag inherited = upstreamFloorCleanSquashRoom(11, true);
        CompoundTag independent = upstreamFloorCleanSquashRoom(12, false);
        village.put("buildings", list(main, inherited, independent));
        village.put("externalBuildings", new ListTag());
        village.put("structures", list(upstreamFloorCleanSquashStructure(20)));
        return village;
    }

    private static CompoundTag upstreamFloorCleanSquashRoom(int id, boolean inheritanceEnabled) {
        CompoundTag room = originBuilding(id, "house");
        room.putInt("structureId", 20);
        room.putInt("floorId", 0);
        room.putBoolean("inheritanceEnabled", inheritanceEnabled);
        room.put("floorRegions", list(legacyRegion(64, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0)))));
        return room;
    }

    private static CompoundTag upstreamFloorCleanSquashStructure(int id) {
        CompoundTag structure = new CompoundTag();
        structure.putInt("id", id);
        structure.putInt("buildingId", id);
        structure.putInt("mainRoomId", 10);
        structure.putBoolean("mainRoomAutomatic", false);
        structure.putInt("nextFloorId", 2);
        structure.put("source", NbtHelper.encodeBlockPos(new BlockPos(0, 65, 0)));
        structure.put("min", NbtHelper.encodeBlockPos(new BlockPos(0, 60, 0)));
        structure.put("max", NbtHelper.encodeBlockPos(new BlockPos(4, 72, 4)));
        structure.putInt("surfaceReferenceY", 64);
        structure.put("floors", list(
                upstreamFloor(0, 64, 68, 0),
                upstreamFloor(1, 68, 72, 1)));
        return structure;
    }

    private static CompoundTag upstreamFloor(int id, int anchorY, int ceilingY, int number) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putInt("anchorY", anchorY);
        tag.putInt("ceilingY", ceilingY);
        tag.putInt("floorNumber", number);
        tag.put("region", legacyRegion(anchorY, Set.of(
                new BlockPos(0, anchorY, 0), new BlockPos(1, anchorY, 0))));
        return tag;
    }

    private static StructureFloor floor(int id, int anchorY, int ceilingY, int number) {
        TestFloorFootprint region = TestFloorFootprint.fromFootprint(
                anchorY, List.of(new BlockPos(0, anchorY, 0), new BlockPos(1, anchorY, 0)));
        return TestStructureFloors.create(id, anchorY, ceilingY, number, region);
    }

    @Test
    void inconsistentSavedStoreyLabelsRoundTripWithoutNewRepair() {
        Village village = canonicalVillage();
        int[] heights = {72, 76, 78, 78};
        int[] numbers = {1, 2, 3, 2};
        for (int index = 0; index < heights.length; index++) {
            int id = 21 + index;
            BlockPos position = new BlockPos(10 + index * 3, heights[index], 0);
            StructureFloor floor = new StructureFloor(0, numbers[index], new FloorGeometry(
                    List.of(new FloorGeometry.Cell(position, heights[index] + 4)), java.util.Map.of()));
            Structure structure = new Structure(id, position, List.of(floor));
            structure.setLogicalBuildingId(20);
            Building room = new Building(position);
            room.setId(11 + index);
            room.setStructureId(id);
            room.setFloorId(0);
            room.setGeometry(position, position.above(3), List.of(position));
            village.registerStructure(structure, room);
        }
        Village reloaded = new Village(village.save(), null);
        for (int index = 0; index < heights.length; index++) {
            assertEquals(numbers[index], reloaded.getStructure(21 + index).orElseThrow()
                    .getFloor(0).orElseThrow().floorNumber());
        }
    }

    private static Village canonicalVillage() {
        Village village = new Village(1, null);
        StructureFloor floor = floor(0, 64, 68, 0);
        Structure structure = new Structure(20, new BlockPos(0, 64, 0), List.of(floor));
        Building room = new Building(new BlockPos(0, 64, 0));
        room.setId(10);
        room.setStructureId(20);
        room.setFloorId(0);
        room.setGeometry(new BlockPos(0, 64, 0), new BlockPos(1, 67, 0),
                Set.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0)));
        village.registerStructure(structure, room);
        return village;
    }

    private static CompoundTag originBuilding(int id, String type) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putInt("size", 16);
        tag.putInt("pos0X", 0);
        tag.putInt("pos0Y", 63);
        tag.putInt("pos0Z", 0);
        tag.putInt("pos1X", 3);
        tag.putInt("pos1Y", 67);
        tag.putInt("pos1Z", 3);
        tag.putInt("posX", 1);
        tag.putInt("posY", 64);
        tag.putInt("posZ", 1);
        tag.putBoolean("isTypeForced", false);
        tag.putString("type", type);
        tag.put("blocks2", new CompoundTag());
        return tag;
    }

    private static ListTag list(CompoundTag... tags) {
        ListTag list = new ListTag();
        for (CompoundTag tag : tags) list.add(tag);
        return list;
    }

    private static void duplicateFirst(ListTag list) {
        list.add(list.getCompound(0).copy());
    }

    private static CompoundTag firstCanonicalFloorCell(CompoundTag village) {
        return village.getList("structures", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0)
                .getList("floors", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0)
                .getList("cells", net.minecraft.nbt.Tag.TAG_COMPOUND)
                .getCompound(0);
    }

    private static CompoundTag legacyRegion(int anchorY, Set<BlockPos> cells) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("anchorY", anchorY);
        tag.putInt("area", cells.size());
        ListTag components = new ListTag();
        if (!cells.isEmpty()) {
            List<BlockPos> ordered = cells.stream()
                    .sorted(java.util.Comparator.comparingInt((BlockPos pos) -> pos.getZ())
                            .thenComparingInt(pos -> pos.getX()))
                    .toList();
            CompoundTag componentTag = new CompoundTag();
            componentTag.putInt("minX", ordered.stream().mapToInt(BlockPos::getX).min().orElseThrow());
            componentTag.putInt("minZ", ordered.stream().mapToInt(BlockPos::getZ).min().orElseThrow());
            componentTag.putInt("maxX", ordered.stream().mapToInt(BlockPos::getX).max().orElseThrow());
            componentTag.putInt("maxZ", ordered.stream().mapToInt(BlockPos::getZ).max().orElseThrow());
            componentTag.putInt("area", ordered.size());
            ListTag spans = new ListTag();
            for (BlockPos cell : ordered) {
                CompoundTag spanTag = new CompoundTag();
                spanTag.putInt("z", cell.getZ());
                spanTag.putInt("minX", cell.getX());
                spanTag.putInt("maxX", cell.getX());
                spans.add(spanTag);
            }
            componentTag.put("spans", spans);
            components.add(componentTag);
        }
        tag.put("components", components);
        return tag;
    }
}
