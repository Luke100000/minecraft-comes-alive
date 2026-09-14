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
import java.util.LinkedHashSet;
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
            int version = getInt(villageTag, "buildingDataVersion");
            if (version == Village.BUILDING_DATA_VERSION) return loadCurrent(villageTag);
            throw new IllegalArgumentException("Unsupported MCA buildingDataVersion: " + version);
        }
        return villageTag.getList("structures").isPresent()
                ? migrateUpstreamFloorCleanSquash(villageTag)
                : migrateOrigin(getList(villageTag, "buildings"));
    }

    private static Result loadCurrent(CompoundTag villageTag) {
        validateCurrentShape(villageTag);

        Map<Integer, Building> rooms = new HashMap<>();
        for (Tag value : getList(villageTag, "buildings")) {
            Building room = new Building((CompoundTag) value);
            if (room.getFloorCells().isEmpty()) {
                throw new IllegalArgumentException("Room " + room.getId()
                        + " has empty canonical floor-cell ownership");
            }
            putUnique(rooms, room.getId(), room, "Room");
        }

        Map<Integer, ExternalBuilding> external = new HashMap<>();
        for (Tag value : getList(villageTag, "externalBuildings")) {
            ExternalBuilding building = new ExternalBuilding((CompoundTag) value);
            if (rooms.containsKey(building.getId())) {
                throw new IllegalArgumentException("Duplicate canonical building id " + building.getId());
            }
            putUnique(external, building.getId(), building, "External building");
        }

        Map<Integer, Structure> structures = new HashMap<>();
        for (Tag value : getList(villageTag, "structures")) {
            Structure structure = new Structure((CompoundTag) value);
            putUnique(structures, structure.getId(), structure, "Structure");
        }

        Map<Integer, LogicalBuilding> logicalBuildings = new HashMap<>();
        for (Tag value : getList(villageTag, "logicalBuildings")) {
            LogicalBuilding logical = new LogicalBuilding((CompoundTag) value);
            putUnique(logicalBuildings, logical.id(), logical, "Logical building");
        }
        return new Result(rooms, external, structures, logicalBuildings);
    }

    private static <T> void putUnique(Map<Integer, T> target, int id, T value, String kind) {
        if (target.putIfAbsent(id, value) != null) {
            throw new IllegalArgumentException("Duplicate canonical " + kind + " id " + id);
        }
    }

    private static Result migrateUpstreamFloorCleanSquash(CompoundTag villageTag) {
        Map<Integer, Structure> structures = new HashMap<>();
        Map<Integer, CompoundTag> oldStructureTags = new HashMap<>();
        for (Tag value : getList(villageTag, "structures")) {
            CompoundTag oldStructure = (CompoundTag) value;
            int id = getInt(oldStructure, "id");
            int logicalBuildingId = getInt(oldStructure, "buildingId");
            List<StructureFloor> floors = getList(oldStructure, "floors").stream()
                    .map(tag -> migrateUpstreamFloor((CompoundTag) tag))
                    .toList();
            if (floors.isEmpty()) continue;
            BlockPos source = NbtHelper.decodeBlockPos(oldStructure.get("source"));
            if (source == null) {
                throw new IllegalArgumentException("Upstream floor-clean Structure is missing a valid source");
            }
            Structure structure = new Structure(id, source, floors);
            structure.setLogicalBuildingId(logicalBuildingId);
            structures.put(id, structure);
            oldStructureTags.put(id, oldStructure);
        }

        Map<Integer, Building> rooms = new HashMap<>();
        Map<Integer, Boolean> inheritanceByRoom = new HashMap<>();
        for (Tag value : getList(villageTag, "buildings")) {
            CompoundTag oldRoom = (CompoundTag) value;
            int structureId = getInt(oldRoom, "structureId");
            int floorId = getInt(oldRoom, "floorId");
            boolean inheritanceEnabled = getBoolean(oldRoom, "inheritanceEnabled");
            inheritanceByRoom.put(getInt(oldRoom, "id"), inheritanceEnabled);

            Structure structure = structures.get(structureId);
            StructureFloor floor = structure == null ? null : structure.getFloor(floorId).orElse(null);
            Set<BlockPos> exactCells = floor == null ? Set.of() : upstreamRoomCells(oldRoom, floor);
            Building room = new Building(canonicalBuildingTag(
                    oldRoom, exactCells, structureId, floorId, inheritanceEnabled));
            rooms.put(room.getId(), room);
        }

        Map<Integer, ExternalBuilding> external = new HashMap<>();
        for (Tag value : getList(villageTag, "externalBuildings")) {
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
            if (oldRoot == null) {
                throw new IllegalArgumentException("Upstream floor-clean Structure is missing its persisted root");
            }
            int mainRoomId = getInt(oldRoot, "mainRoomId");
            logicalBuildings.put(buildingId, new LogicalBuilding(
                    buildingId, mainRoomId,
                    inheritanceByRoom.getOrDefault(mainRoomId, true)));
        });
        return new Result(rooms, external, structures, logicalBuildings);
    }

    private static StructureFloor migrateUpstreamFloor(CompoundTag oldFloor) {
        LegacyFloorRegion region = loadLegacyFloorRegion(getCompound(oldFloor, "region"));
        int ceilingY = getInt(oldFloor, "ceilingY");
        List<FloorGeometry.Cell> cells = region.cells().stream()
                .map(pos -> new FloorGeometry.Cell(
                        pos, Math.max(pos.getY() + 1, ceilingY)))
                .toList();
        return new StructureFloor(
                getInt(oldFloor, "id"), getInt(oldFloor, "floorNumber"),
                new FloorGeometry(cells, Map.of()));
    }

    private static Set<BlockPos> upstreamRoomCells(CompoundTag oldRoom, StructureFloor floor) {
        ListTag regions = getList(oldRoom, "floorRegions");
        if (regions.isEmpty()) return Set.of();
        LegacyFloorRegion oldRegion = loadLegacyFloorRegion(regions.getCompound(0).orElseGet(CompoundTag::new));
        return floor.geometry().cells().stream()
                .map(FloorGeometry.Cell::feet)
                .filter(pos -> oldRegion.containsHorizontally(pos.getX(), pos.getZ()))
                .map(BlockPos::immutable)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static LegacyFloorRegion loadLegacyFloorRegion(CompoundTag tag) {
        int anchorY = getInt(tag, "anchorY");
        Set<BlockPos> cells = new LinkedHashSet<>();
        for (Tag componentValue : getList(tag, "components")) {
            CompoundTag component = (CompoundTag) componentValue;
            for (Tag spanValue : getList(component, "spans")) {
                CompoundTag span = (CompoundTag) spanValue;
                int z = getInt(span, "z");
                int minX = getInt(span, "minX");
                int maxX = getInt(span, "maxX");
                for (int x = minX; x <= maxX; x++) cells.add(new BlockPos(x, anchorY, z));
            }
        }
        return new LegacyFloorRegion(Set.copyOf(cells));
    }

    private static void validateCurrentShape(CompoundTag villageTag) {
        for (Tag value : getList(villageTag, "buildings")) {
            requireCurrentBuildingShape((CompoundTag) value, "Room");
        }
        for (Tag value : getList(villageTag, "externalBuildings")) {
            requireCurrentBuildingShape((CompoundTag) value, "External building");
        }
        for (Tag value : getList(villageTag, "structures")) {
            CompoundTag structure = (CompoundTag) value;
            require(structure, "buildingId", Tag.TAG_INT, "Structure");
            require(structure, "source", "Structure");
            require(structure, "floors", Tag.TAG_LIST, "Structure");
        }
        for (Tag value : getList(villageTag, "logicalBuildings")) {
            CompoundTag logical = (CompoundTag) value;
            require(logical, "mainRoomId", Tag.TAG_INT, "Logical building");
            require(logical, "inheritanceEnabled", Tag.TAG_BYTE, "Logical building");
        }
    }

    private static void requireCurrentBuildingShape(CompoundTag building, String kind) {
        require(building, "floorCells", Tag.TAG_LIST, kind);
        require(building, "contributesToMain", Tag.TAG_BYTE, kind);
        require(building, "structureId", Tag.TAG_INT, kind);
        require(building, "floorId", Tag.TAG_INT, kind);
    }

    private static void require(CompoundTag tag, String key, int type, String kind) {
        boolean present = switch (type) {
            case Tag.TAG_INT -> tag.getInt(key).isPresent();
            case Tag.TAG_BYTE -> tag.getBoolean(key).isPresent();
            case Tag.TAG_LIST -> tag.getList(key).isPresent();
            case Tag.TAG_COMPOUND -> tag.getCompound(key).isPresent();
            case Tag.TAG_STRING -> tag.getString(key).isPresent();
            default -> tag.contains(key);
        };
        if (!present) {
            throw new IllegalArgumentException(kind + " is missing required canonical field " + key);
        }
    }

    private static void require(CompoundTag tag, String key, String kind) {
        if (!tag.contains(key)) {
            throw new IllegalArgumentException(kind + " is missing required canonical field " + key);
        }
    }

    /**
     * Origin-format saves persisted only a building bounding box, not observed Floor cells.
     * The rectangular cells created here are therefore a compatibility approximation that
     * remains authoritative only until normal scanning replaces it with observed geometry.
     */
    private static Result migrateOrigin(ListTag legacy) {
        Map<Integer, Building> rooms = new HashMap<>();
        Map<Integer, ExternalBuilding> external = new HashMap<>();
        Map<Integer, Structure> structures = new HashMap<>();
        Map<Integer, LogicalBuilding> logicalBuildings = new HashMap<>();
        for (Tag value : legacy) {
            CompoundTag old = (CompoundTag) value;
            int id = getInt(old, "id");
            boolean grouped = BuildingTypes.getInstance().getBuildingType(getString(old, "type")).grouped();
            if (grouped) {
                ExternalBuilding building = new ExternalBuilding(
                        canonicalOriginBuildingTag(old, Set.of(), -1, -1, true));
                external.put(building.getId(), building);
                continue;
            }

            int anchorY = getInt(old, "pos0Y") + 1;
            Set<BlockPos> floorCells = rectangularCells(old, anchorY);
            if (floorCells.isEmpty()) continue;
            Building room = new Building(canonicalOriginBuildingTag(old, floorCells, id, 0, true));
            int ceilingY = Math.max(anchorY + 1, getInt(old, "pos1Y") + 1);
            FloorGeometry geometry = new FloorGeometry(floorCells.stream()
                    .map(pos -> new FloorGeometry.Cell(pos, ceilingY))
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
        for (int x = getInt(tag, "pos0X"); x <= getInt(tag, "pos1X"); x++) {
            for (int z = getInt(tag, "pos0Z"); z <= getInt(tag, "pos1Z"); z++) {
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
        tag.putInt("structureId", structureId);
        tag.putInt("floorId", floorId);
        tag.putBoolean("contributesToMain", contributesToMain);
        tag.put("floorCells", NbtHelper.fromList(floorCells, NbtHelper::encodeBlockPos));
        return tag;
    }

    private static CompoundTag canonicalOriginBuildingTag(CompoundTag source,
                                                           Set<BlockPos> floorCells,
                                                           int structureId,
                                                           int floorId,
                                                           boolean contributesToMain) {
        CompoundTag tag = canonicalBuildingTag(
                source, floorCells, structureId, floorId, contributesToMain);
        tag.put("blocks2", normalizeOriginBlocks(getCompound(source, "blocks2")));
        return tag;
    }

    private static CompoundTag normalizeOriginBlocks(CompoundTag blocks) {
        CompoundTag normalized = new CompoundTag();
        for (String key : blocks.keySet()) {
            List<BlockPos> normalizedPositions = blocks.getList(key).orElseGet(ListTag::new).stream()
                    .map(CompoundTag.class::cast)
                    .map(pos -> new BlockPos(getInt(pos, "x"), getInt(pos, "y"), getInt(pos, "z")))
                    .toList();
            normalized.put(key, NbtHelper.fromList(normalizedPositions, NbtHelper::encodeBlockPos));
        }
        return normalized;
    }

    private static int getInt(CompoundTag tag, String key) {
        return tag.getInt(key).orElse(0);
    }

    private static boolean getBoolean(CompoundTag tag, String key) {
        return tag.getBoolean(key).orElse(false);
    }

    private static String getString(CompoundTag tag, String key) {
        return tag.getString(key).orElse("");
    }

    private static CompoundTag getCompound(CompoundTag tag, String key) {
        return tag.getCompound(key).orElseGet(CompoundTag::new);
    }

    private static ListTag getList(CompoundTag tag, String key) {
        return tag.getList(key).orElseGet(ListTag::new);
    }

    record Result(
            Map<Integer, Building> buildings,
            Map<Integer, ExternalBuilding> externalBuildings,
            Map<Integer, Structure> structures,
            Map<Integer, LogicalBuilding> logicalBuildings) {
    }

    private record LegacyFloorRegion(Set<BlockPos> cells) {
        boolean containsHorizontally(int x, int z) {
            return cells.stream().anyMatch(pos -> pos.getX() == x && pos.getZ() == z);
        }
    }
}
