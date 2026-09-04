package net.conczin.mca.server.world.data;

import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Sole compatibility boundary for current data plus the two supported upstream save shapes. */
final class RoomDFU {
    private RoomDFU() {
    }

    static Result load(CompoundTag villageTag) {
        Objects.requireNonNull(villageTag, "villageTag");
        if (villageTag.contains("buildingDataVersion")) {
            int version = villageTag.getInt("buildingDataVersion");
            if (version != Village.BUILDING_DATA_VERSION) {
                throw new IllegalArgumentException("Unsupported MCA buildingDataVersion: " + version);
            }
            return loadCurrent(villageTag);
        }
        return villageTag.contains("structures", Tag.TAG_LIST)
                ? migrateUpstreamFloorCleanSquash(villageTag)
                : migrateOrigin(villageTag.getList("buildings", Tag.TAG_COMPOUND));
    }

    private static Result loadCurrent(CompoundTag villageTag) {
        Map<Integer, Building> rooms = new HashMap<>();
        for (Tag value : villageTag.getList("buildings", Tag.TAG_COMPOUND)) {
            Building room = new Building((CompoundTag) value);
            rooms.put(room.getId(), room);
        }

        Map<Integer, ExternalBuilding> external = new HashMap<>();
        for (Tag value : villageTag.getList("externalBuildings", Tag.TAG_COMPOUND)) {
            ExternalBuilding building = new ExternalBuilding((CompoundTag) value);
            external.put(building.getId(), building);
        }

        Map<Integer, Structure> structures = new HashMap<>();
        for (Tag value : villageTag.getList("structures", Tag.TAG_COMPOUND)) {
            Structure structure = new Structure((CompoundTag) value);
            structures.put(structure.getId(), structure);
        }

        Map<Integer, LogicalBuilding> logicalBuildings = new HashMap<>();
        for (Tag value : villageTag.getList("logicalBuildings", Tag.TAG_COMPOUND)) {
            LogicalBuilding logical = new LogicalBuilding((CompoundTag) value);
            logicalBuildings.put(logical.id(), logical);
        }
        return new Result(rooms, external, structures, logicalBuildings);
    }

    private static Result migrateUpstreamFloorCleanSquash(CompoundTag villageTag) {
        Map<Integer, Structure> structures = new HashMap<>();
        Map<Integer, CompoundTag> oldStructureTags = new HashMap<>();
        for (Tag value : villageTag.getList("structures", Tag.TAG_COMPOUND)) {
            CompoundTag oldStructure = (CompoundTag) value;
            int id = oldStructure.getInt("id");
            int logicalBuildingId = oldStructure.contains("buildingId")
                    ? oldStructure.getInt("buildingId") : id;
            List<StructureFloor> floors = oldStructure.getList("floors", Tag.TAG_COMPOUND).stream()
                    .map(tag -> migrateUpstreamFloor((CompoundTag) tag))
                    .toList();
            if (floors.isEmpty()) continue;
            BlockPos source = decodeCompatibleBlockPos(oldStructure.get("source"));
            if (source == null) source = floors.getFirst().geometry().cells().iterator().next().feet();
            Structure structure = new Structure(id, source, floors);
            structure.setLogicalBuildingId(logicalBuildingId);
            structures.put(id, structure);
            oldStructureTags.put(id, oldStructure);
        }

        Map<Integer, Building> rooms = new HashMap<>();
        Map<Integer, Boolean> inheritanceByRoom = new HashMap<>();
        for (Tag value : villageTag.getList("buildings", Tag.TAG_COMPOUND)) {
            CompoundTag oldRoom = (CompoundTag) value;
            int structureId = oldRoom.getInt("structureId");
            int floorId = oldRoom.getInt("floorId");
            boolean inheritanceEnabled = oldRoom.getBoolean("inheritanceEnabled");
            inheritanceByRoom.put(oldRoom.getInt("id"), inheritanceEnabled);

            Structure structure = structures.get(structureId);
            StructureFloor floor = structure == null ? null : structure.getFloor(floorId).orElse(null);
            Set<BlockPos> exactCells = floor == null ? Set.of() : upstreamRoomCells(oldRoom, floor);
            Building room = new Building(canonicalBuildingTag(
                    oldRoom, exactCells, structureId, floorId, inheritanceEnabled));
            rooms.put(room.getId(), room);
        }

        Map<Integer, ExternalBuilding> external = new HashMap<>();
        for (Tag value : villageTag.getList("externalBuildings", Tag.TAG_COMPOUND)) {
            CompoundTag old = (CompoundTag) value;
            ExternalBuilding building = new ExternalBuilding(
                    canonicalBuildingTag(old, Set.of(), -1, -1, true));
            external.put(building.getId(), building);
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
            CompoundTag oldRoot = oldStructureTags.get(root.getId());
            int mainRoomId = oldRoot == null ? -1 : oldRoot.getInt("mainRoomId");
            logicalBuildings.put(buildingId, new LogicalBuilding(
                    buildingId, mainRoomId,
                    inheritanceByRoom.getOrDefault(mainRoomId, true)));
        });
        return new Result(rooms, external, structures, logicalBuildings);
    }

    private static StructureFloor migrateUpstreamFloor(CompoundTag oldFloor) {
        BuildingFloorRegion region = BuildingFloorRegion.load(oldFloor.getCompound("region"));
        int ceilingY = oldFloor.getInt("ceilingY");
        List<FloorGeometry.Cell> cells = region.cells().stream()
                .map(pos -> new FloorGeometry.Cell(
                        pos, pos.getY(), Math.max(pos.getY() + 1, ceilingY)))
                .toList();
        Set<BlockPos> cellPositions = cells.stream().map(FloorGeometry.Cell::feet)
                .collect(Collectors.toSet());
        Map<BlockPos, StructureFloor.ConnectorType> connectors = new LinkedHashMap<>();
        for (Tag value : oldFloor.getList("connectors", Tag.TAG_COMPOUND)) {
            CompoundTag oldConnector = (CompoundTag) value;
            BlockPos pos = decodeCompatibleBlockPos(oldConnector.get("pos"));
            StructureFloor.ConnectorType type = StructureFloor.ConnectorType.fromSerializedName(
                    oldConnector.getString("type"));
            if (pos != null && type != null && cellPositions.contains(pos)) connectors.put(pos, type);
        }
        return new StructureFloor(
                oldFloor.getInt("id"), oldFloor.getInt("floorNumber"),
                new FloorGeometry(cells, connectors));
    }

    private static Set<BlockPos> upstreamRoomCells(CompoundTag oldRoom, StructureFloor floor) {
        ListTag regions = oldRoom.getList("floorRegions", Tag.TAG_COMPOUND);
        if (regions.isEmpty()) return Set.of();
        BuildingFloorRegion oldRegion = BuildingFloorRegion.load(regions.getCompound(0));
        return floor.geometry().cells().stream()
                .map(FloorGeometry.Cell::feet)
                .filter(pos -> oldRegion.containsHorizontally(pos.getX(), pos.getZ()))
                .map(BlockPos::immutable)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Result migrateOrigin(ListTag legacy) {
        Map<Integer, Building> rooms = new HashMap<>();
        Map<Integer, ExternalBuilding> external = new HashMap<>();
        Map<Integer, Structure> structures = new HashMap<>();
        Map<Integer, LogicalBuilding> logicalBuildings = new HashMap<>();
        for (Tag value : legacy) {
            CompoundTag old = (CompoundTag) value;
            int id = old.getInt("id");
            boolean grouped = BuildingTypes.getInstance().getBuildingType(old.getString("type")).grouped();
            if (grouped) {
                ExternalBuilding building = new ExternalBuilding(
                        canonicalBuildingTag(old, Set.of(), -1, -1, true));
                external.put(building.getId(), building);
                continue;
            }

            int anchorY = old.getInt("pos0Y") + 1;
            Set<BlockPos> floorCells = rectangularCells(old, anchorY);
            if (floorCells.isEmpty()) continue;
            Building room = new Building(canonicalBuildingTag(old, floorCells, id, 0, true));
            int ceilingY = Math.max(anchorY + 1, old.getInt("pos1Y") + 1);
            FloorGeometry geometry = new FloorGeometry(floorCells.stream()
                    .map(pos -> new FloorGeometry.Cell(pos, pos.getY(), ceilingY))
                    .toList(), Map.of());
            Structure structure = new Structure(id, room.getSourceBlock(), List.of(
                    new StructureFloor(0, 0, geometry)));
            rooms.put(id, room);
            structures.put(id, structure);
            logicalBuildings.put(id, new LogicalBuilding(id, id, true));
        }
        return new Result(rooms, external, structures, logicalBuildings);
    }

    private static Set<BlockPos> rectangularCells(CompoundTag tag, int y) {
        java.util.LinkedHashSet<BlockPos> cells = new java.util.LinkedHashSet<>();
        for (int x = tag.getInt("pos0X"); x <= tag.getInt("pos1X"); x++) {
            for (int z = tag.getInt("pos0Z"); z <= tag.getInt("pos1Z"); z++) {
                cells.add(new BlockPos(x, y, z));
            }
        }
        return Set.copyOf(cells);
    }

    private static CompoundTag canonicalBuildingTag(CompoundTag source,
                                                     Set<BlockPos> floorCells,
                                                     int structureId,
                                                     int floorId,
                                                     boolean contributesToMain) {
        CompoundTag tag = source.copy();
        tag.remove("size");
        tag.remove("floorRegions");
        tag.remove("inheritanceEnabled");
        tag.remove("rootRoomId");
        tag.remove("mainRoomAutomatic");
        tag.putInt("structureId", structureId);
        tag.putInt("floorId", floorId);
        tag.putBoolean("contributesToMain", contributesToMain);
        tag.put("floorCells", NbtHelper.fromList(floorCells, NbtHelper::encodeBlockPos));
        tag.put("blocks2", normalizeBlocks(source.getCompound("blocks2")));
        return tag;
    }

    private static CompoundTag normalizeBlocks(CompoundTag blocks) {
        CompoundTag normalized = new CompoundTag();
        for (String key : blocks.getAllKeys()) {
            Tag positionsTag = blocks.get(key);
            if (!(positionsTag instanceof ListTag positions)) continue;
            List<BlockPos> normalizedPositions = positions.stream()
                    .map(RoomDFU::decodeCompatibleBlockPos)
                    .filter(Objects::nonNull)
                    .toList();
            normalized.put(key, NbtHelper.fromList(normalizedPositions, NbtHelper::encodeBlockPos));
        }
        return normalized;
    }

    private static BlockPos decodeCompatibleBlockPos(Tag value) {
        if (value instanceof CompoundTag pos
                && pos.contains("x") && pos.contains("y") && pos.contains("z")) {
            return new BlockPos(pos.getInt("x"), pos.getInt("y"), pos.getInt("z"));
        }
        return value == null ? null : NbtHelper.decodeBlockPos(value);
    }

    record Result(
            Map<Integer, Building> buildings,
            Map<Integer, ExternalBuilding> externalBuildings,
            Map<Integer, Structure> structures,
            Map<Integer, LogicalBuilding> logicalBuildings) {
    }
}
