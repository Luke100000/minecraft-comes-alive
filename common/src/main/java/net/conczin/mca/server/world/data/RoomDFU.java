package net.conczin.mca.server.world.data;

import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Compatibility boundary for released origin/1.21.1 and this branch's previous save format. */
final class RoomDFU {
    private RoomDFU() {
    }

    static Result migrate(CompoundTag villageTag) {
        return villageTag.contains("structures", Tag.TAG_LIST)
                ? migratePreviousBranch(villageTag)
                : migrateOrigin(villageTag.getList("buildings", Tag.TAG_COMPOUND));
    }

    private static Result migratePreviousBranch(CompoundTag villageTag) {
        Map<Integer, Building> rooms = new HashMap<>();
        Map<Integer, Boolean> inheritanceByRoom = new HashMap<>();
        for (Tag value : villageTag.getList("buildings", Tag.TAG_COMPOUND)) {
            CompoundTag tag = ((CompoundTag) value).copy();
            boolean inheritance = tag.getBoolean("inheritanceEnabled");
            tag.remove("inheritanceEnabled");
            tag.putBoolean("contributesToMain", inheritance);
            Building room = new Building(tag);
            rooms.put(room.getId(), room);
            inheritanceByRoom.put(room.getId(), inheritance);
        }

        Map<Integer, ExternalBuilding> external = new HashMap<>();
        for (Tag value : villageTag.getList("externalBuildings", Tag.TAG_COMPOUND)) {
            ExternalBuilding building = new ExternalBuilding((CompoundTag) value);
            external.put(building.getId(), building);
        }

        Map<Integer, Structure> structures = new HashMap<>();
        Map<Integer, CompoundTag> structureTags = new HashMap<>();
        for (Tag value : villageTag.getList("structures", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) value;
            Structure structure = new Structure(tag);
            structures.put(structure.getId(), structure);
            structureTags.put(structure.getId(), tag);
        }

        Map<Integer, LogicalBuilding> logicalBuildings = new HashMap<>();
        Map<Integer, List<Structure>> byBuilding = new HashMap<>();
        structures.values().forEach(structure -> byBuilding
                .computeIfAbsent(structure.getLogicalBuildingId(), ignored -> new ArrayList<>())
                .add(structure));
        byBuilding.forEach((buildingId, members) -> {
            Structure root = members.stream()
                    .min(Comparator.comparing((Structure structure) -> structure.getId() != buildingId)
                            .thenComparingInt(Structure::getId))
                    .orElseThrow();
            int mainRoomId = structureTags.get(root.getId()).getInt("mainRoomId");
            FloorRef ground = members.stream()
                    .sorted(Comparator.comparing((Structure structure) -> structure.getId() != buildingId)
                            .thenComparingInt(Structure::getId))
                    .flatMap(structure -> structure.getFloors().stream()
                            .filter(floor -> floor.floorNumber() == 0)
                            .map(floor -> new FloorRef(structure.getId(), floor.id())))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Previous branch save has no Ground Floor for logical building " + buildingId));
            logicalBuildings.put(buildingId, new LogicalBuilding(
                    buildingId, ground.structureId(), ground.floorId(), mainRoomId,
                    inheritanceByRoom.getOrDefault(mainRoomId, true)));
        });
        return new Result(rooms, external, structures, logicalBuildings);
    }

    private static Result migrateOrigin(ListTag legacy) {
        Map<Integer, Building> rooms = new HashMap<>();
        Map<Integer, ExternalBuilding> external = new HashMap<>();
        Map<Integer, Structure> structures = new HashMap<>();
        Map<Integer, LogicalBuilding> logicalBuildings = new HashMap<>();
        for (Tag value : legacy) {
            CompoundTag normalized = normalizeOriginBuilding((CompoundTag) value);
            Building room = new Building(normalized);
            if (room.getBuildingType().grouped()) {
                ExternalBuilding building = new ExternalBuilding(normalized);
                external.put(building.getId(), building);
                continue;
            }
            migrateOriginBuilding(room).ifPresent(structure -> {
                rooms.put(room.getId(), room);
                structures.put(structure.getId(), structure);
                logicalBuildings.put(structure.getId(), new LogicalBuilding(
                        structure.getId(), structure.getId(), room.getFloorId(), room.getId(), true));
            });
        }
        return new Result(rooms, external, structures, logicalBuildings);
    }

    private static CompoundTag normalizeOriginBuilding(CompoundTag source) {
        CompoundTag tag = source.copy();
        tag.putInt("structureId", -1);
        tag.putInt("floorId", -1);
        tag.putBoolean("contributesToMain", true);

        int anchorY = tag.getInt("pos0Y") + 1;
        List<BlockPos> cells = new ArrayList<>();
        for (int x = tag.getInt("pos0X"); x <= tag.getInt("pos1X"); x++) {
            for (int z = tag.getInt("pos0Z"); z <= tag.getInt("pos1Z"); z++) {
                cells.add(new BlockPos(x, anchorY, z));
            }
        }
        tag.put("floorRegions", NbtHelper.fromList(
                List.of(BuildingFloorRegion.fromFootprint(anchorY, cells)), BuildingFloorRegion::save));

        CompoundTag blocks = tag.getCompound("blocks2");
        CompoundTag normalizedBlocks = new CompoundTag();
        for (String key : blocks.getAllKeys()) {
            Tag positionsTag = blocks.get(key);
            if (!(positionsTag instanceof ListTag positions)) continue;
            List<BlockPos> normalizedPositions = positions.stream()
                    .map(RoomDFU::decodeOriginBlockPos)
                    .filter(Objects::nonNull)
                    .toList();
            normalizedBlocks.put(key, NbtHelper.fromList(normalizedPositions, NbtHelper::encodeBlockPos));
        }
        tag.put("blocks2", normalizedBlocks);
        return tag;
    }

    private static BlockPos decodeOriginBlockPos(Tag value) {
        if (!(value instanceof CompoundTag pos)) return null;
        return new BlockPos(pos.getInt("x"), pos.getInt("y"), pos.getInt("z"));
    }

    private static Optional<Structure> migrateOriginBuilding(Building room) {
        BuildingFloorRegion region = room.getFloorRegion().orElse(null);
        if (region == null) return Optional.empty();
        StructureFloor floor = new StructureFloor(0, region.anchorY(),
                Math.max(region.anchorY() + 1, room.getRawPos1().getY() + 1), region);
        Structure structure = new Structure(room.getId(), room.getSourceBlock(), room.getRawPos0(), room.getRawPos1(),
                List.of(floor));
        room.setStructureId(structure.getId());
        room.setFloorId(floor.id());
        return Optional.of(structure);
    }

    private record FloorRef(int structureId, int floorId) {
    }

    record Result(
            Map<Integer, Building> buildings,
            Map<Integer, ExternalBuilding> externalBuildings,
            Map<Integer, Structure> structures,
            Map<Integer, LogicalBuilding> logicalBuildings) {
    }
}
