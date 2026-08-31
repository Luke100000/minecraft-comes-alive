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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        RoomDFU.Result migrated = RoomDFU.migrate(village);

        Building room = migrated.buildings().get(7);
        assertEquals(7, room.getId());
        assertEquals(7, room.getStructureId());
        assertEquals(0, room.getFloorId());
        LogicalBuilding logical = migrated.logicalBuildings().get(7);
        assertEquals(7, logical.groundStructureId());
        assertEquals(0, logical.groundFloorId());
        assertEquals(7, logical.mainRoomId());
        assertTrue(logical.inheritanceEnabled());
    }

    @Test
    void originGroupedBuildingBecomesExternalOnly() {
        CompoundTag village = new CompoundTag();
        village.put("buildings", list(originBuilding(8, "graveyard")));

        RoomDFU.Result migrated = RoomDFU.migrate(village);

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

        Building room = RoomDFU.migrate(village).buildings().get(9);

        assertEquals(List.of(new BlockPos(2, 65, 3)),
                room.getBlocks().get(ResourceLocation.parse("minecraft:bell")));
    }

    @Test
    void previousBranchInheritanceBecomesLogicalAndRoomState() {
        CompoundTag village = previousBranchVillage(false);

        RoomDFU.Result migrated = RoomDFU.migrate(village);

        LogicalBuilding logical = migrated.logicalBuildings().get(20);
        assertEquals(10, logical.mainRoomId());
        assertFalse(logical.inheritanceEnabled());
        assertTrue(migrated.buildings().get(11).contributesToMain());
        assertFalse(migrated.buildings().get(12).contributesToMain());
    }

    @Test
    void previousBranchGroundFloorZeroBecomesExplicitGroundReference() {
        RoomDFU.Result migrated = RoomDFU.migrate(previousBranchVillage(true));

        LogicalBuilding logical = migrated.logicalBuildings().get(20);
        assertEquals(20, logical.groundStructureId());
        assertEquals(0, logical.groundFloorId());
    }

    @Test
    void normalizedSavesDoNotEmitLegacyAutomaticOrRoomInheritanceFields() {
        RoomDFU.Result migrated = RoomDFU.migrate(previousBranchVillage(true));

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
        assertFalse(floorTag.contains("floorNumber"));
        assertTrue(logicalTag.contains("mainRoomId"));
        assertTrue(logicalTag.contains("inheritanceEnabled"));
    }

    @Test
    void currentRoomAndStructureGeometryRoundTripsWithoutNewSaveFormat() {
        BuildingFloorRegion footprint = BuildingFloorRegion.fromFootprint(64, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(0, 64, 1), new BlockPos(1, 64, 1)));
        StructureFloor floor = new StructureFloor(0, 64, 70, 0, footprint);
        Structure structure = new Structure(20, new BlockPos(0, 64, 0),
                new BlockPos(0, 64, 0), new BlockPos(1, 69, 1), List.of(floor));
        structure.setLogicalBuildingId(77);

        Building room = new Building(new BlockPos(0, 64, 0));
        room.setId(10);
        room.setStructureId(20);
        room.setFloorId(0);
        room.setType("house");
        room.setGeometry(new BlockPos(0, 64, 0), new BlockPos(1, 69, 1), footprint);
        room.addBlock(Blocks.BELL, new BlockPos(0, 65, 0));

        Structure reloadedStructure = new Structure(structure.save());
        Building reloadedRoom = new Building(room.save());

        assertEquals(structure.getId(), reloadedStructure.getId());
        assertEquals(structure.getLogicalBuildingId(), reloadedStructure.getLogicalBuildingId());
        assertEquals(structure.getFloor(0).orElseThrow().floorNumber(),
                reloadedStructure.getFloor(0).orElseThrow().floorNumber());
        assertEquals(room.getFloorRegion(), reloadedRoom.getFloorRegion());
        assertEquals(room.getStructureId(), reloadedRoom.getStructureId());
        assertEquals(room.getFloorId(), reloadedRoom.getFloorId());
        assertEquals(room.getBlocks(), reloadedRoom.getBlocks());
    }

    private static CompoundTag previousBranchVillage(boolean mainInheritanceEnabled) {
        CompoundTag village = new CompoundTag();
        CompoundTag main = previousBranchRoom(10, mainInheritanceEnabled);
        CompoundTag inherited = previousBranchRoom(11, true);
        CompoundTag independent = previousBranchRoom(12, false);
        village.put("buildings", list(main, inherited, independent));
        village.put("externalBuildings", new ListTag());
        village.put("structures", list(previousBranchStructure(20)));
        return village;
    }

    private static CompoundTag previousBranchRoom(int id, boolean inheritanceEnabled) {
        CompoundTag room = originBuilding(id, "house");
        room.putInt("structureId", 20);
        room.putInt("floorId", 0);
        room.putBoolean("inheritanceEnabled", inheritanceEnabled);
        room.put("floorRegions", NbtHelper.fromList(List.of(
                BuildingFloorRegion.fromFootprint(64, List.of(
                        new BlockPos(0, 64, 0), new BlockPos(1, 64, 0)))),
                BuildingFloorRegion::save));
        return room;
    }

    private static CompoundTag previousBranchStructure(int id) {
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
                legacyFloor(0, 64, 68, 0),
                legacyFloor(1, 68, 72, 1)));
        return structure;
    }

    private static CompoundTag legacyFloor(int id, int anchorY, int ceilingY, int number) {
        CompoundTag tag = floor(id, anchorY, ceilingY, number).save();
        tag.putInt("floorNumber", number);
        return tag;
    }

    private static StructureFloor floor(int id, int anchorY, int ceilingY, int number) {
        BuildingFloorRegion region = BuildingFloorRegion.fromFootprint(
                anchorY, List.of(new BlockPos(0, anchorY, 0), new BlockPos(1, anchorY, 0)));
        return new StructureFloor(id, anchorY, ceilingY, number, region);
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
}
