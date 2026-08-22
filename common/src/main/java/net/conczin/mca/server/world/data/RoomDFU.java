package net.conczin.mca.server.world.data;

import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/** One-time compatibility boundary for released origin/1.21.1 and public floor-beta village data. */
final class RoomDFU {
    private RoomDFU() {
    }

    static Result migrate(CompoundTag villageTag) {
        boolean legacyInheritance = villageTag.contains("roomInheritance")
                && villageTag.getBoolean("roomInheritance");
        if (villageTag.contains("structures", Tag.TAG_LIST)) {
            return migratePublicBeta(villageTag, legacyInheritance);
        }

        return migrateOrigin(villageTag.getList("buildings", Tag.TAG_COMPOUND));
    }

    private static Result migratePublicBeta(CompoundTag villageTag, boolean legacyInheritance) {
        Map<Integer, Building> rooms = new HashMap<>();
        Map<Integer, ExternalBuilding> external = new HashMap<>();
        Map<Integer, Structure> structures = new HashMap<>();
        Map<Integer, LogicalBuilding> logicalBuildings = new HashMap<>();
        Map<Integer, Boolean> inheritanceByRoom = new HashMap<>();
        Map<Integer, Integer> legacyManualMainByStructure = new HashMap<>();
        for (Tag value : villageTag.getList("buildings", Tag.TAG_COMPOUND)) {
            CompoundTag roomTag = (CompoundTag) value;
            CompoundTag normalized = normalizeRoomTag(roomTag);
            boolean inheritance = normalized.contains("inheritanceEnabled")
                    ? normalized.getBoolean("inheritanceEnabled") : legacyInheritance;
            normalized.remove("inheritanceEnabled");
            normalized.putBoolean("contributesToMain", inheritance);
            Building room = new Building(normalized);
            rooms.put(room.getId(), room);
            inheritanceByRoom.put(room.getId(), inheritance);
            if (roomTag.getBoolean("layoutOverride")) {
                legacyManualMainByStructure.merge(room.getStructureId(), room.getId(), Math::min);
            }
        }
        for (Tag value : villageTag.getList("externalBuildings", Tag.TAG_COMPOUND)) {
            ExternalBuilding building = new ExternalBuilding(normalizeRoomTag((CompoundTag) value));
            external.put(building.getId(), building);
        }
        Map<Integer, Integer> mainRoomByLogicalBuilding = new HashMap<>();
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
            Structure structure = new Structure(tag);
            structures.put(structure.getId(), structure);
            if (tag.getInt("mainRoomId") >= 0) {
                mainRoomByLogicalBuilding.putIfAbsent(structure.getLogicalBuildingId(), tag.getInt("mainRoomId"));
            }
        }

        Map<Integer, List<Structure>> structuresByLogicalBuilding = new HashMap<>();
        for (Structure structure : structures.values()) {
            structuresByLogicalBuilding.computeIfAbsent(structure.getLogicalBuildingId(), ignored -> new ArrayList<>())
                    .add(structure);
        }
        for (Map.Entry<Integer, List<Structure>> entry : structuresByLogicalBuilding.entrySet()) {
            List<Structure> members = entry.getValue().stream()
                    .sorted(Comparator.comparingInt(Structure::getId)).toList();
            FloorSelection ground = betaGroundFloor(members);
            if (ground == null) continue;
            int mainRoomId = mainRoomByLogicalBuilding.getOrDefault(entry.getKey(), lowestRoomId(
                    entry.getKey(), structures, rooms));
            boolean inheritanceEnabled = mainRoomId >= 0
                    && inheritanceByRoom.getOrDefault(mainRoomId, legacyInheritance);
            logicalBuildings.put(entry.getKey(), new LogicalBuilding(entry.getKey(),
                    ground.structureId(), ground.floorId(), mainRoomId, inheritanceEnabled));
        }
        return new Result(rooms, external, structures, logicalBuildings);
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

    private static Result migrateOrigin(ListTag legacy) {
        Map<Integer, Building> rooms = new HashMap<>();
        Map<Integer, ExternalBuilding> external = new HashMap<>();
        Map<Integer, Structure> structures = new HashMap<>();
        Map<Integer, LogicalBuilding> logicalBuildings = new HashMap<>();
        for (Tag value : legacy) {
            CompoundTag tag = (CompoundTag) value;
            CompoundTag normalized = normalizeRoomTag(tag);
            normalized.remove("inheritanceEnabled");
            normalized.putBoolean("contributesToMain", true);
            Building room = new Building(normalized);
            if (room.getBuildingType().grouped()) {
                ExternalBuilding building = new ExternalBuilding(normalizeRoomTag(tag));
                external.put(building.getId(), building);
                continue;
            }
            migrateOriginBuilding(room).ifPresent(structure -> {
                rooms.put(room.getId(), room);
                structures.put(structure.getId(), structure);
                logicalBuildings.put(structure.getLogicalBuildingId(), new LogicalBuilding(
                        structure.getLogicalBuildingId(), structure.getId(), room.getFloorId(), room.getId(), true));
            });
        }
        return new Result(rooms, external, structures, logicalBuildings);
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
        return Optional.of(structure);
    }

    private static FloorSelection betaGroundFloor(List<Structure> members) {
        return members.stream()
                .flatMap(structure -> structure.getFloors().stream()
                        .filter(floor -> floor.floorNumber() == 0)
                        .map(floor -> new FloorSelection(structure.getId(), floor.id(), floor.anchorY())))
                .min(Comparator.comparingInt(FloorSelection::anchorY)
                        .thenComparingInt(FloorSelection::structureId)
                        .thenComparingInt(FloorSelection::floorId))
                .orElseGet(() -> members.stream().findFirst()
                        .flatMap(structure -> structure.getFloors().stream().findFirst()
                                .map(floor -> new FloorSelection(structure.getId(), floor.id(), floor.anchorY())))
                        .orElse(null));
    }

    private static int lowestRoomId(int logicalBuildingId,
                                    Map<Integer, Structure> structures,
                                    Map<Integer, Building> rooms) {
        return rooms.values().stream()
                .filter(Building::isFunctionalRoom)
                .filter(room -> {
                    Structure structure = structures.get(room.getStructureId());
                    return structure != null && structure.getLogicalBuildingId() == logicalBuildingId;
                })
                .mapToInt(Building::getId)
                .min().orElse(-1);
    }

    private record FloorSelection(int structureId, int floorId, int anchorY) {
    }

    record Result(
            Map<Integer, Building> buildings,
            Map<Integer, ExternalBuilding> externalBuildings,
            Map<Integer, Structure> structures,
            Map<Integer, LogicalBuilding> logicalBuildings) {
    }
}
