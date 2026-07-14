package net.conczin.mca.server.world.data;

import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/** One-time compatibility boundary for released origin/1.21.1 village data. */
final class RoomDFU {
    private RoomDFU() {
    }

    static Result load(CompoundTag villageTag) {
        boolean legacyInheritance = villageTag.contains("roomInheritance")
                && villageTag.getBoolean("roomInheritance");
        if (villageTag.contains("structures", Tag.TAG_LIST)) {
            return loadCurrent(villageTag, legacyInheritance);
        }

        return migrateOrigin(villageTag.getList("buildings", Tag.TAG_COMPOUND), legacyInheritance);
    }

    private static Result loadCurrent(CompoundTag villageTag, boolean legacyInheritance) {
        Map<Integer, Building> rooms = new HashMap<>();
        Map<Integer, ExternalBuilding> external = new HashMap<>();
        Map<Integer, Structure> structures = new HashMap<>();
        Map<Integer, Integer> legacyManualMainByStructure = new HashMap<>();
        for (Tag value : villageTag.getList("buildings", Tag.TAG_COMPOUND)) {
            CompoundTag roomTag = (CompoundTag) value;
            Building room = loadRoom(roomTag, legacyInheritance);
            rooms.put(room.getId(), room);
            if (roomTag.getBoolean("layoutOverride")) {
                legacyManualMainByStructure.merge(room.getStructureId(), room.getId(), Math::min);
            }
        }
        for (Tag value : villageTag.getList("externalBuildings", Tag.TAG_COMPOUND)) {
            ExternalBuilding building = new ExternalBuilding(normalizeRoomTag((CompoundTag) value));
            external.put(building.getId(), building);
        }
        for (Tag value : villageTag.getList("structures", Tag.TAG_COMPOUND)) {
            CompoundTag source = (CompoundTag) value;
            CompoundTag tag = source.copy();
            int structureId = tag.getInt("id");
            Integer manualMain = legacyManualMainByStructure.get(structureId);
            Building oldRoot = tag.contains("rootRoomId") ? rooms.get(tag.getInt("rootRoomId")) : null;
            if (!tag.contains("mainRoomId")) {
                int mainRoomId = manualMain != null ? manualMain : oldRoot == null ? -1 : oldRoot.getId();
                tag.putInt("mainRoomId", mainRoomId);
            }
            if (!tag.contains("mainRoomAutomatic")) {
                tag.putBoolean("mainRoomAutomatic", manualMain == null
                        && !tag.getBoolean("groundAnchorExplicit"));
            }
            if (!tag.contains("surfaceReferenceY")) {
                int referenceY = tag.contains("groundReferenceY")
                        ? tag.getInt("groundReferenceY")
                        : NbtHelper.decodeBlockPos(tag.get("source")).getY();
                tag.putInt("surfaceReferenceY", referenceY);
            }
            Structure structure = new Structure(tag);
            structures.put(structure.getId(), structure);
        }
        return new Result(rooms, external, structures);
    }

    private static Building loadRoom(CompoundTag tag, boolean legacyInheritance) {
        CompoundTag normalized = normalizeRoomTag(tag);
        if (!normalized.contains("inheritanceEnabled")) {
            normalized.putBoolean("inheritanceEnabled", legacyInheritance);
        }
        return new Building(normalized);
    }

    private static CompoundTag normalizeRoomTag(CompoundTag source) {
        CompoundTag tag = source.copy();
        if (!tag.contains("structureId")) tag.putInt("structureId", -1);
        if (!tag.contains("floorId")) tag.putInt("floorId", -1);
        if (!tag.contains("posX")) {
            tag.putInt("posX", Math.floorDiv(tag.getInt("pos0X") + tag.getInt("pos1X"), 2));
            tag.putInt("posY", Math.floorDiv(tag.getInt("pos0Y") + tag.getInt("pos1Y"), 2));
            tag.putInt("posZ", Math.floorDiv(tag.getInt("pos0Z") + tag.getInt("pos1Z"), 2));
        }
        if (!tag.contains("floorRegions", Tag.TAG_LIST)
                || tag.getList("floorRegions", Tag.TAG_COMPOUND).isEmpty()) {
            int anchorY = tag.contains("floorY") ? tag.getInt("floorY") : tag.getInt("pos0Y") + 1;
            List<BlockPos> cells = new ArrayList<>();
            for (int x = tag.getInt("pos0X"); x <= tag.getInt("pos1X"); x++) {
                for (int z = tag.getInt("pos0Z"); z <= tag.getInt("pos1Z"); z++) {
                    cells.add(new BlockPos(x, anchorY, z));
                }
            }
            BuildingFloorRegion region = BuildingFloorRegion.fromFootprint(anchorY, cells);
            tag.put("floorRegions", NbtHelper.fromList(List.of(region), BuildingFloorRegion::save));
        }
        if (tag.contains("blocks2", Tag.TAG_COMPOUND)) {
            CompoundTag blocks = tag.getCompound("blocks2");
            CompoundTag normalizedBlocks = new CompoundTag();
            for (String key : blocks.getAllKeys()) {
                Tag positionsTag = blocks.get(key);
                if (!(positionsTag instanceof ListTag positions)) continue;
                List<BlockPos> normalizedPositions = positions.stream()
                        .map(RoomDFU::decodeLegacyBlockPos)
                        .filter(Objects::nonNull)
                        .toList();
                normalizedBlocks.put(key, NbtHelper.fromList(
                        normalizedPositions, NbtHelper::encodeBlockPos));
            }
            tag.put("blocks2", normalizedBlocks);
        }
        return tag;
    }

    private static BlockPos decodeLegacyBlockPos(Tag value) {
        if (value instanceof CompoundTag legacy && legacy.contains("x")) {
            return new BlockPos(legacy.getInt("x"), legacy.getInt("y"), legacy.getInt("z"));
        }
        return NbtHelper.decodeBlockPos(value);
    }

    private static Result migrateOrigin(ListTag legacy, boolean legacyInheritance) {
        Map<Integer, Building> rooms = new HashMap<>();
        Map<Integer, ExternalBuilding> external = new HashMap<>();
        Map<Integer, Structure> structures = new HashMap<>();
        for (Tag value : legacy) {
            CompoundTag tag = (CompoundTag) value;
            Building room = loadRoom(tag, legacyInheritance);
            if (room.getBuildingType().grouped()) {
                ExternalBuilding building = new ExternalBuilding(normalizeRoomTag(tag));
                external.put(building.getId(), building);
                continue;
            }
            migrateOriginBuilding(room).ifPresent(structure -> {
                rooms.put(room.getId(), room);
                structures.put(structure.getId(), structure);
            });
        }
        return new Result(rooms, external, structures);
    }

    private static Optional<Structure> migrateOriginBuilding(Building room) {
        if (room.getFloorRegions().isEmpty()) {
            return Optional.empty();
        }
        BuildingFloorRegion region = room.getFloorRegions().getFirst();
        StructureFloor floor = new StructureFloor(0, region.anchorY(),
                Math.max(region.anchorY() + 1, room.getRawPos1().getY() + 1), region);
        Structure structure = new Structure(room.getId(), room.getSourceBlock(), room.getRawPos0(), room.getRawPos1(),
                List.of(floor));
        room.setStructureId(structure.getId());
        room.setFloorId(floor.id());
        structure.setAutomaticMainRoom(room.getId());
        return Optional.of(structure);
    }

    record Result(
            Map<Integer, Building> buildings,
            Map<Integer, ExternalBuilding> externalBuildings,
            Map<Integer, Structure> structures) {
    }
}
