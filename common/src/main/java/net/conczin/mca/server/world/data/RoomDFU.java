package net.conczin.mca.server.world.data;

import net.conczin.mca.MCA;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;
import java.util.stream.Collectors;

/** Reads current building data and migrates released floor regions and legacy bounding boxes. */
final class RoomDFU {
    private RoomDFU() {
    }

    static final class UnsupportedVersionException extends IllegalArgumentException {
        UnsupportedVersionException(String message) {
            super(message);
        }
    }

    static Result load(CompoundTag villageTag) {
        Objects.requireNonNull(villageTag, "villageTag");
        if (villageTag.contains("buildingDataVersion")) {
            int version = villageTag.getInt("buildingDataVersion");
            if (version == Village.BUILDING_DATA_VERSION) return loadCurrent(villageTag);
            throw new UnsupportedVersionException("Unsupported MCA buildingDataVersion: " + version);
        }
        return villageTag.contains("structures", Tag.TAG_LIST)
                ? migrateReleasedFloors(villageTag)
                : migrateOrigin(villageTag.getList("buildings", Tag.TAG_COMPOUND));
    }

    private static Result loadCurrent(CompoundTag villageTag) {
        validateCurrentShape(villageTag);
        int villageId = villageTag.getInt("id");
        boolean typesLoaded = hasLoadedBuildingTypes();
        boolean quarantinedData = false;

        Map<Integer, Structure> structures = new HashMap<>();
        for (Tag value : villageTag.getList("structures", Tag.TAG_COMPOUND)) {
            Structure structure = new Structure((CompoundTag) value);
            putUnique(structures, structure.getId(), structure, "Structure");
        }

        Map<Integer, LogicalBuilding> logicalBuildings = new HashMap<>();
        for (Tag value : villageTag.getList("logicalBuildings", Tag.TAG_COMPOUND)) {
            LogicalBuilding logical = new LogicalBuilding((CompoundTag) value);
            putUnique(logicalBuildings, logical.id(), logical, "Logical building");
        }

        Set<Integer> seenBuildingIds = new HashSet<>();
        Map<Integer, Building> rooms = new HashMap<>();
        for (Tag value : villageTag.getList("buildings", Tag.TAG_COMPOUND)) {
            CompoundTag roomTag = (CompoundTag) value;
            int roomId = roomTag.getInt("id");
            if (!seenBuildingIds.add(roomId)) {
                throw new IllegalArgumentException("Duplicate canonical Room id " + roomId);
            }

            Building room = new Building(roomTag);
            Structure structure = structures.get(room.getStructureId());
            StructureFloor floor = structure == null ? null : structure.getFloor(room.getFloorId()).orElse(null);
            if (roomTag.getBoolean("ownsFloor") && floor != null) {
                room.setGeometry(room.getRawPos0(), room.getRawPos1(),
                        floor.geometry().cells().stream().map(FloorGeometry.Cell::feet).toList());
            }
            if (floor == null || room.getFloorCells().isEmpty()
                    || !room.getFloorCells().stream().allMatch(cell -> floor.geometry().cellAt(cell).isPresent())) {
                MCA.LOGGER.warn("Quarantining invalid room {} in village {} (structure={}, floor={}, cells={})",
                        roomId, villageId, room.getStructureId(), room.getFloorId(), room.getFloorCells().size());
                quarantinedData = true;
                continue;
            }
            rooms.put(roomId, room);
        }

        consolidateLogicalStructures(structures, rooms);

        Map<Integer, ExternalBuilding> external = new HashMap<>();
        for (Tag value : villageTag.getList("externalBuildings", Tag.TAG_COMPOUND)) {
            ExternalBuilding building = new ExternalBuilding((CompoundTag) value);
            if (!seenBuildingIds.add(building.getId())) {
                throw new IllegalArgumentException("Duplicate canonical building id " + building.getId());
            }
            if (typesLoaded && (!BuildingTypes.getInstance().hasBuildingType(building.getType())
                    || !building.getBuildingType().grouped())) {
                MCA.LOGGER.warn("Quarantining external building {} with invalid/missing type '{}' in village {}",
                        building.getId(), building.getType(), villageId);
                quarantinedData = true;
                continue;
            }
            external.put(building.getId(), building);
        }

        validateDisjointRoomOwnership(rooms.values());
        return new Result(rooms, external, structures, logicalBuildings, quarantinedData);
    }

    private static void consolidateLogicalStructures(Map<Integer, Structure> structures,
                                                     Map<Integer, Building> rooms) {
        Map<Integer, List<Structure>> byLogicalBuilding = new HashMap<>();
        for (Structure structure : structures.values()) {
            byLogicalBuilding.computeIfAbsent(structure.getLogicalBuildingId(), ignored -> new ArrayList<>())
                    .add(structure);
        }

        for (Map.Entry<Integer, List<Structure>> entry : byLogicalBuilding.entrySet()) {
            int buildingId = entry.getKey();
            List<Structure> members = entry.getValue();
            if (members.size() <= 1) continue;

            members.sort(Comparator.comparing((Structure s) -> s.getId() != buildingId)
                    .thenComparingInt(Structure::getId));
            Structure primary = members.getFirst();

            for (int i = 1; i < members.size(); i++) {
                Structure sibling = members.get(i);
                if (hasSameBandCellOverlap(primary, sibling)) continue;

                Map<Integer, Integer> remappedFloors = new HashMap<>();
                for (StructureFloor siblingFloor : sibling.getFloors()) {
                    int newFloorId = primary.nextFloorId();
                    primary.putFloor(new StructureFloor(
                            newFloorId, siblingFloor.floorNumber(), siblingFloor.anchorY(), siblingFloor.geometry()));
                    remappedFloors.put(siblingFloor.id(), newFloorId);
                }

                int siblingId = sibling.getId();
                for (Building room : rooms.values()) {
                    if (room.getStructureId() == siblingId) {
                        Integer newFloorId = remappedFloors.get(room.getFloorId());
                        if (newFloorId != null) {
                            room.setStructureId(primary.getId());
                            room.setFloorId(newFloorId);
                        }
                    }
                }
                structures.remove(siblingId);
            }
        }
    }

    private static boolean hasSameBandCellOverlap(Structure primary, Structure sibling) {
        for (StructureFloor siblingFloor : sibling.getFloors()) {
            for (StructureFloor primaryFloor : primary.getFloors()) {
                if (!siblingFloor.hasNearbyAnchor(primaryFloor)) continue;
                boolean exactOverlap = siblingFloor.geometry().cells().stream()
                        .anyMatch(cell -> primaryFloor.geometry().cellAt(cell.feet()).isPresent());
                if (exactOverlap) return true;
            }
        }
        return false;
    }

    private static boolean hasLoadedBuildingTypes() {
        BuildingTypes instance = BuildingTypes.getInstance();
        return !instance.getBuildingTypes().isEmpty() || !instance.getServerBuildingTypes().isEmpty();
    }

    private static void validateDisjointRoomOwnership(Collection<Building> rooms) {
        Map<FloorRef, Set<BlockPos>> ownedCells = new HashMap<>();
        for (Building room : rooms) {
            Set<BlockPos> floorCells = ownedCells.computeIfAbsent(
                    new FloorRef(room.getStructureId(), room.getFloorId()), ignored -> new HashSet<>());
            for (BlockPos cell : room.getFloorCells()) {
                if (!floorCells.add(cell)) {
                    throw new IllegalArgumentException("Canonical Rooms overlap at floor cell " + cell);
                }
            }
        }
    }

    private static <T> void putUnique(Map<Integer, T> target, int id, T value, String kind) {
        if (target.putIfAbsent(id, value) != null) {
            throw new IllegalArgumentException("Duplicate " + kind + " id " + id);
        }
    }

    private record FloorRef(int structureId, int floorId) {
    }

    private static Result migrateReleasedFloors(CompoundTag villageTag) {
        boolean quarantinedData = false;
        Map<Integer, Structure> structures = new HashMap<>();
        Map<Integer, CompoundTag> oldStructureTags = new HashMap<>();
        for (Tag value : villageTag.getList("structures", Tag.TAG_COMPOUND)) {
            CompoundTag oldStructure = (CompoundTag) value;
            int id = oldStructure.getInt("id");
            int logicalBuildingId = oldStructure.contains("buildingId", Tag.TAG_INT) ? oldStructure.getInt("buildingId") : id;
            List<StructureFloor> floors = oldStructure.getList("floors", Tag.TAG_COMPOUND).stream()
                    .map(tag -> migrateReleasedFloor((CompoundTag) tag))
                    .toList();
            if (floors.isEmpty()) {
                MCA.LOGGER.warn("Quarantining legacy structure {} with no floors", id);
                quarantinedData = true;
                continue;
            }
            BlockPos source = NbtHelper.decodeBlockPos(oldStructure.get("source"));
            if (source == null) {
                throw new IllegalArgumentException("Legacy Structure is missing a valid source");
            }
            Structure structure = new Structure(id, source, floors);
            structure.setLogicalBuildingId(logicalBuildingId);
            putUnique(structures, id, structure, "legacy structure");
            oldStructureTags.put(id, oldStructure);
        }

        Map<Integer, Building> rooms = new HashMap<>();
        Map<Integer, Boolean> inheritanceByRoom = new HashMap<>();
        for (Tag value : villageTag.getList("buildings", Tag.TAG_COMPOUND)) {
            CompoundTag oldRoom = (CompoundTag) value;
            int structureId = oldRoom.getInt("structureId");
            int floorId = oldRoom.getInt("floorId");
            boolean inheritanceEnabled = !oldRoom.contains("inheritanceEnabled", Tag.TAG_BYTE)
                    || oldRoom.getBoolean("inheritanceEnabled");
            inheritanceByRoom.put(oldRoom.getInt("id"), inheritanceEnabled);

            Structure structure = structures.get(structureId);
            StructureFloor floor = structure == null ? null : structure.getFloor(floorId).orElse(null);
            Set<BlockPos> exactCells = floor == null ? Set.of() : releasedRoomCells(oldRoom, floor);
            if (exactCells.isEmpty()) {
                throw new IllegalArgumentException("Legacy room " + oldRoom.getInt("id") + " has no matching floor cells");
            }
            Building room = new Building(canonicalBuildingTag(
                    oldRoom, exactCells, structureId, floorId, inheritanceEnabled));
            putUnique(rooms, room.getId(), room, "legacy room");
        }

        Map<Integer, ExternalBuilding> external = new HashMap<>();
        for (Tag value : villageTag.getList("externalBuildings", Tag.TAG_COMPOUND)) {
            CompoundTag old = (CompoundTag) value;
            ExternalBuilding building = new ExternalBuilding(
                    canonicalBuildingTag(old, Set.of(), -1, -1, true));
            if (rooms.containsKey(building.getId())) {
                throw new IllegalArgumentException("Duplicate legacy building id " + building.getId());
            }
            putUnique(external, building.getId(), building, "legacy external building");
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
                throw new IllegalArgumentException("Legacy Structure is missing its persisted root");
            }
            int mainRoomId = oldRoot.getInt("mainRoomId");
            logicalBuildings.put(buildingId, new LogicalBuilding(
                    buildingId, mainRoomId,
                    inheritanceByRoom.getOrDefault(mainRoomId, true)));
        });
        validateDisjointRoomOwnership(rooms.values());
        return new Result(rooms, external, structures, logicalBuildings, quarantinedData);
    }

    private static StructureFloor migrateReleasedFloor(CompoundTag oldFloor) {
        LegacyFloorRegion region = loadLegacyFloorRegion(oldFloor.getCompound("region"));
        int ceilingY = oldFloor.getInt("ceilingY");
        List<FloorGeometry.Cell> cells = region.cells().stream()
                .map(pos -> new FloorGeometry.Cell(
                        pos, Math.max(pos.getY() + 1, ceilingY)))
                .toList();
        return new StructureFloor(
                oldFloor.getInt("id"), oldFloor.getInt("floorNumber"), region.anchorY(),
                new FloorGeometry(cells, Map.of()));
    }

    private static Set<BlockPos> releasedRoomCells(CompoundTag oldRoom, StructureFloor floor) {
        ListTag regions = oldRoom.getList("floorRegions", Tag.TAG_COMPOUND);
        if (regions.isEmpty()) return Set.of();
        LegacyFloorRegion oldRegion = loadLegacyFloorRegion(regions.getCompound(0));
        return floor.geometry().cells().stream()
                .map(FloorGeometry.Cell::feet)
                .filter(pos -> oldRegion.containsHorizontally(pos.getX(), pos.getZ()))
                .map(BlockPos::immutable)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static LegacyFloorRegion loadLegacyFloorRegion(CompoundTag tag) {
        int anchorY = tag.getInt("anchorY");
        Set<BlockPos> cells = new LinkedHashSet<>();
        for (Tag componentValue : tag.getList("components", Tag.TAG_COMPOUND)) {
            CompoundTag component = (CompoundTag) componentValue;
            for (Tag spanValue : component.getList("spans", Tag.TAG_COMPOUND)) {
                CompoundTag span = (CompoundTag) spanValue;
                int z = span.getInt("z");
                int minX = span.getInt("minX");
                int maxX = span.getInt("maxX");
                for (int x = minX; x <= maxX; x++) cells.add(new BlockPos(x, anchorY, z));
            }
        }
        return new LegacyFloorRegion(anchorY, Set.copyOf(cells));
    }

    private static void validateCurrentShape(CompoundTag villageTag) {
        for (Tag value : villageTag.getList("buildings", Tag.TAG_COMPOUND)) {
            CompoundTag room = (CompoundTag) value;
            require(room, "id", Tag.TAG_INT, "Room");
            require(room, "contributesToMain", Tag.TAG_BYTE, "Room");
            require(room, "structureId", Tag.TAG_INT, "Room");
            require(room, "floorId", Tag.TAG_INT, "Room");
        }
        for (Tag value : villageTag.getList("externalBuildings", Tag.TAG_COMPOUND)) {
            CompoundTag external = (CompoundTag) value;
            require(external, "id", Tag.TAG_INT, "External building");
        }
        for (Tag value : villageTag.getList("structures", Tag.TAG_COMPOUND)) {
            CompoundTag structure = (CompoundTag) value;
            require(structure, "id", Tag.TAG_INT, "Structure");
            require(structure, "buildingId", Tag.TAG_INT, "Structure");
            require(structure, "source", "Structure");
            require(structure, "floors", Tag.TAG_LIST, "Structure");
            ListTag floors = structure.getList("floors", Tag.TAG_COMPOUND);
            if (floors.isEmpty()) {
                throw new IllegalArgumentException("Structure requires at least one canonical floor");
            }
            for (Tag floorValue : floors) {
                require((CompoundTag) floorValue, "id", Tag.TAG_INT, "StructureFloor");
            }
        }
        for (Tag value : villageTag.getList("logicalBuildings", Tag.TAG_COMPOUND)) {
            CompoundTag logical = (CompoundTag) value;
            require(logical, "id", Tag.TAG_INT, "Logical building");
            require(logical, "mainRoomId", Tag.TAG_INT, "Logical building");
            require(logical, "inheritanceEnabled", Tag.TAG_BYTE, "Logical building");
        }
    }

    private static void require(CompoundTag tag, String key, int type, String kind) {
        if (!tag.contains(key, type)) {
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
     * The rectangular cells created here are a compatibility approximation until normal scanning
     * replaces them with observed geometry.
     */
    private static Result migrateOrigin(ListTag legacy) {
        boolean quarantinedData = false;
        Set<Integer> buildingIds = new HashSet<>();
        Map<Integer, Building> rooms = new HashMap<>();
        Map<Integer, ExternalBuilding> external = new HashMap<>();
        Map<Integer, Structure> structures = new HashMap<>();
        Map<Integer, LogicalBuilding> logicalBuildings = new HashMap<>();
        for (Tag value : legacy) {
            CompoundTag old = (CompoundTag) value;
            int id = old.getInt("id");
            if (!buildingIds.add(id)) {
                throw new IllegalArgumentException("Duplicate legacy building id " + id);
            }
            String rawType = old.getString("type");
            boolean grouped = BuildingTypes.getInstance().getBuildingType(rawType).grouped();
            if (grouped) {
                ExternalBuilding building = new ExternalBuilding(
                        canonicalOriginBuildingTag(old, Set.of(), -1, -1, true));
                external.put(building.getId(), building);
                continue;
            }

            int anchorY = old.getInt("pos0Y") + 1;
            Set<BlockPos> floorCells = rectangularCells(old, anchorY);
            if (floorCells.isEmpty()) {
                MCA.LOGGER.warn("Quarantining legacy building {} with no floor area", id);
                quarantinedData = true;
                continue;
            }
            Building room = new Building(canonicalOriginBuildingTag(old, floorCells, id, 0, true));
            int ceilingY = Math.max(anchorY + 1, old.getInt("pos1Y") + 1);
            FloorGeometry geometry = new FloorGeometry(floorCells.stream()
                    .map(pos -> new FloorGeometry.Cell(pos, ceilingY))
                    .toList(), Map.of());
            Structure structure = new Structure(id, room.getSourceBlock(), List.of(
                    new StructureFloor(0, 0, geometry)));
            structure.setOriginGeometryApproximate(true);
            rooms.put(id, room);
            structures.put(id, structure);
            logicalBuildings.put(id, new LogicalBuilding(id, id, true));
        }
        return new Result(rooms, external, structures, logicalBuildings, quarantinedData);
    }

    private static Set<BlockPos> rectangularCells(CompoundTag tag, int y) {
        LinkedHashSet<BlockPos> cells = new LinkedHashSet<>();
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
        tag.putInt("structureId", structureId);
        tag.putInt("floorId", floorId);
        tag.putBoolean("contributesToMain", contributesToMain);
        tag.putLongArray("floorCellPositions", floorCells.stream().mapToLong(BlockPos::asLong).sorted().toArray());
        return tag;
    }

    private static CompoundTag canonicalOriginBuildingTag(CompoundTag source,
                                                          Set<BlockPos> floorCells,
                                                          int structureId,
                                                          int floorId,
                                                          boolean contributesToMain) {
        CompoundTag tag = canonicalBuildingTag(source, floorCells, structureId, floorId, contributesToMain);
        tag.put("blocks2", normalizeOriginBlocks(source.getCompound("blocks2")));
        return tag;
    }

    private static CompoundTag normalizeOriginBlocks(CompoundTag blocks) {
        CompoundTag normalized = new CompoundTag();
        for (String key : blocks.getAllKeys()) {
            List<BlockPos> normalizedPositions = blocks.getList(key, Tag.TAG_COMPOUND).stream()
                    .map(CompoundTag.class::cast)
                    .map(pos -> new BlockPos(pos.getInt("x"), pos.getInt("y"), pos.getInt("z")))
                    .toList();
            normalized.put(key, NbtHelper.fromList(normalizedPositions, NbtHelper::encodeBlockPos));
        }
        return normalized;
    }

    record Result(
            Map<Integer, Building> buildings,
            Map<Integer, ExternalBuilding> externalBuildings,
            Map<Integer, Structure> structures,
            Map<Integer, LogicalBuilding> logicalBuildings,
            boolean quarantinedData) {
    }
    private record LegacyFloorRegion(int anchorY, Set<BlockPos> cells) {
        boolean containsHorizontally(int x, int z) {
            return cells.stream().anyMatch(pos -> pos.getX() == x && pos.getZ() == z);
        }
    }
}
