package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** One-time compatibility boundary for origin/1.21.1 and the pre-v2 floor-system format. */
final class RoomDFU {
    private RoomDFU() {
    }

    static Result load(CompoundTag villageTag) {
        Map<Integer, Building> buildings = new HashMap<>();
        Map<Integer, ExternalBuilding> externalBuildings = new HashMap<>();
        Map<Integer, Structure> structures = new HashMap<>();

        if (villageTag.contains("structures", Tag.TAG_LIST)) {
            for (Tag value : villageTag.getList("buildings", Tag.TAG_COMPOUND)) {
                Building room = new Building((CompoundTag) value);
                buildings.put(room.getId(), room);
            }
            for (Tag value : villageTag.getList("externalBuildings", Tag.TAG_COMPOUND)) {
                ExternalBuilding external = new ExternalBuilding((CompoundTag) value);
                externalBuildings.put(external.getId(), external);
            }
            for (Tag value : villageTag.getList("structures", Tag.TAG_COMPOUND)) {
                Structure structure = new Structure((CompoundTag) value);
                structures.put(structure.getId(), structure);
            }
            return new Result(buildings, externalBuildings, structures);
        }

        ListTag legacy = villageTag.getList("buildings", Tag.TAG_COMPOUND);
        boolean floorSystemFormat = legacy.stream()
                .filter(CompoundTag.class::isInstance)
                .map(CompoundTag.class::cast)
                .anyMatch(tag -> tag.contains("structureRoot")
                        || tag.contains("strictScan")
                        || tag.contains("groundFloorY")
                        || tag.contains("floorRegions"));
        if (floorSystemFormat) {
            return migrateFloorSystem(legacy);
        }

        for (Tag value : legacy) {
            CompoundTag tag = (CompoundTag) value;
            Building room = new Building(tag);
            if (room.getBuildingType().grouped()) {
                ExternalBuilding external = new ExternalBuilding(tag);
                externalBuildings.put(external.getId(), external);
                continue;
            }
            migrateOriginBuilding(room).ifPresent(migrated -> {
                buildings.put(room.getId(), room);
                structures.put(migrated.getId(), migrated);
            });
        }
        return new Result(buildings, externalBuildings, structures);
    }

    private static Result migrateFloorSystem(ListTag legacy) {
        Map<Integer, Building> buildings = new HashMap<>();
        Map<Integer, ExternalBuilding> externalBuildings = new HashMap<>();
        Map<Integer, List<LegacyFloorRoom>> byStructure = new TreeMap<>();

        for (Tag value : legacy) {
            CompoundTag tag = (CompoundTag) value;
            Building probe = new Building(tag);
            if (probe.getBuildingType().grouped()) {
                ExternalBuilding external = new ExternalBuilding(tag);
                externalBuildings.put(external.getId(), external);
                continue;
            }

            int persistedStructureId = tag.contains("structureId") ? tag.getInt("structureId") : -1;
            int structureId = persistedStructureId >= 0 ? persistedStructureId : probe.getId();
            boolean structureRoot = tag.contains("structureRoot") && tag.getBoolean("structureRoot");
            boolean strictScan = tag.contains("strictScan") && tag.getBoolean("strictScan");
            int floorY = tag.contains("floorY") ? tag.getInt("floorY") : probe.getFloorY();
            int groundFloorY = tag.contains("groundFloorY") ? tag.getInt("groundFloorY") : floorY;
            byStructure.computeIfAbsent(structureId, ignored -> new ArrayList<>())
                    .add(new LegacyFloorRoom(tag, probe, structureId, structureRoot, strictScan, floorY, groundFloorY));
        }

        Map<Integer, Structure> structures = new HashMap<>();
        for (Map.Entry<Integer, List<LegacyFloorRoom>> entry : byStructure.entrySet()) {
            migrateFloorSystemStructure(entry.getKey(), entry.getValue()).ifPresent(migrated -> {
                migrated.rooms().forEach(room -> buildings.put(room.getId(), room));
                structures.put(migrated.structure().getId(), migrated.structure());
            });
        }
        return new Result(buildings, externalBuildings, structures);
    }

    private static Optional<MigratedStructure> migrateFloorSystemStructure(
            int legacyStructureId,
            List<LegacyFloorRoom> records
    ) {
        LegacyFloorRoom container = records.stream()
                .filter(record -> record.structureRoot() && !record.strictScan())
                .min(Comparator.comparingInt(record -> record.room().getId()))
                .or(() -> records.stream()
                        .filter(record -> !record.strictScan())
                        .min(Comparator.comparingInt(record -> record.room().getId())))
                .orElse(null);

        List<LegacyFloorRoom> functional = records.stream()
                .filter(LegacyFloorRoom::strictScan)
                .filter(record -> !record.structureRoot())
                .sorted(Comparator.comparingInt(record -> record.room().getId()))
                .toList();

        List<BuildingFloorRegion> regions = collectFloorRegions(container, functional);
        if (regions.isEmpty()) {
            return Optional.empty();
        }

        Bounds bounds = bounds(container, functional, regions);
        if (bounds == null) {
            return Optional.empty();
        }

        List<StructureFloor> floors = createFloors(regions, bounds.max().getY() + 1);
        if (floors.isEmpty()) {
            return Optional.empty();
        }

        List<Building> rooms = new ArrayList<>();
        for (LegacyFloorRoom legacyRoom : functional) {
            StructureFloor floor = nearestFloor(floors, legacyRoom.floorY());
            if (floor == null) {
                continue;
            }
            Building room = new Building(legacyRoom.tag());
            room.setStructureId(legacyStructureId);
            room.setFloorId(floor.id());
            rooms.add(room);
        }

        int groundY = container == null
                ? floors.getFirst().anchorY()
                : container.groundFloorY();
        StructureFloor groundFloor = nearestFloor(floors, groundY);
        if (groundFloor == null) {
            return Optional.empty();
        }

        Building rootRoom = rooms.stream()
                .filter(room -> room.getFloorId() == groundFloor.id())
                .min(Comparator.comparingInt(Building::getId))
                .orElse(null);
        if (rootRoom == null) {
            if (container == null || rooms.stream().anyMatch(room -> room.getId() == container.room().getId())) {
                return Optional.empty();
            }
            rootRoom = createFallbackRootRoom(container.room().getId(), legacyStructureId, groundFloor);
            if (rootRoom == null) {
                return Optional.empty();
            }
            rooms.add(rootRoom);
        }

        BlockPos source = container == null ? rootRoom.getSourceBlock() : container.room().getSourceBlock();
        Structure structure = new Structure(
                legacyStructureId,
                source,
                bounds.min(),
                bounds.max(),
                floors,
                volumeSlices(floors)
        );
        structure.setRootRoomId(rootRoom.getId());
        return Optional.of(new MigratedStructure(structure, List.copyOf(rooms)));
    }

    private static Optional<Structure> migrateOriginBuilding(Building room) {
        room.ensureFallbackFloorRegion(room.getFloorY());
        if (room.getFloorRegions().isEmpty()) {
            return Optional.empty();
        }
        BuildingFloorRegion region = room.getFloorRegions().getFirst();
        StructureFloor floor = new StructureFloor(0, region.anchorY(),
                Math.max(region.anchorY() + 1, room.getRawPos1().getY() + 1), region);
        Structure structure = new Structure(room.getId(), room.getSourceBlock(), room.getRawPos0(), room.getRawPos1(),
                List.of(floor), volumeSlices(List.of(floor)));
        structure.setRootRoomId(room.getId());
        room.setStructureId(structure.getId());
        room.setFloorId(floor.id());
        return Optional.of(structure);
    }

    private static List<BuildingFloorRegion> collectFloorRegions(
            LegacyFloorRoom container,
            List<LegacyFloorRoom> functional
    ) {
        Map<Integer, BuildingFloorRegion> byY = new TreeMap<>();
        if (container != null) {
            for (BuildingFloorRegion region : container.room().getFloorRegions()) {
                byY.merge(region.anchorY(), region,
                        (first, second) -> first.area() >= second.area() ? first : second);
            }
        }
        for (LegacyFloorRoom room : functional) {
            for (BuildingFloorRegion region : room.room().getFloorRegions()) {
                BuildingFloorRegion anchored = region.withAnchorY(room.floorY());
                byY.merge(room.floorY(), anchored,
                        (first, second) -> first.area() >= second.area() ? first : second);
            }
            if (!byY.containsKey(room.floorY())) {
                Building fallback = room.room();
                List<BlockPos> cells = rectangleFootprint(fallback.getRawPos0(), fallback.getRawPos1(), room.floorY());
                if (!cells.isEmpty()) {
                    byY.put(room.floorY(), BuildingFloorRegion.fromFootprint(room.floorY(), cells));
                }
            }
        }
        return List.copyOf(byY.values());
    }

    private static List<StructureFloor> createFloors(List<BuildingFloorRegion> regions, int topExclusive) {
        List<StructureFloor> floors = new ArrayList<>();
        for (int i = 0; i < regions.size(); i++) {
            BuildingFloorRegion region = regions.get(i);
            int ceiling = i + 1 < regions.size()
                    ? Math.max(region.anchorY() + 1, regions.get(i + 1).anchorY())
                    : Math.max(region.anchorY() + 1, topExclusive);
            floors.add(new StructureFloor(i, region.anchorY(), ceiling, region));
        }
        return List.copyOf(floors);
    }

    private static Building createFallbackRootRoom(int id, int structureId, StructureFloor floor) {
        if (floor.region() == null || floor.region().components().isEmpty()) {
            return null;
        }
        BuildingFloorRegion.Component component = floor.region().components().stream()
                .max(Comparator.comparingInt(BuildingFloorRegion.Component::area))
                .orElse(null);
        if (component == null) {
            return null;
        }
        BlockPos min = new BlockPos(component.minX(), floor.anchorY(), component.minZ());
        BlockPos max = new BlockPos(component.maxX(), Math.max(floor.anchorY(), floor.ceilingY() - 1), component.maxZ());
        BlockPos source = new BlockPos((component.minX() + component.maxX()) / 2,
                floor.anchorY(), (component.minZ() + component.maxZ()) / 2);
        Building root = new Building(source);
        root.setId(id);
        root.setStructureId(structureId);
        root.setFloorId(floor.id());
        root.setGeometry(min, max, floor.area(), floor.region());
        return root;
    }

    private static Bounds bounds(
            LegacyFloorRoom container,
            List<LegacyFloorRoom> functional,
            List<BuildingFloorRegion> regions
    ) {
        List<Building> sources = new ArrayList<>();
        if (container != null) sources.add(container.room());
        functional.forEach(record -> sources.add(record.room()));
        if (sources.isEmpty()) return null;

        int minX = sources.stream().map(Building::getRawPos0).mapToInt(BlockPos::getX).min().orElse(0);
        int minY = Math.min(
                sources.stream().map(Building::getRawPos0).mapToInt(BlockPos::getY).min().orElse(0),
                regions.stream().mapToInt(BuildingFloorRegion::anchorY).min().orElse(0));
        int minZ = sources.stream().map(Building::getRawPos0).mapToInt(BlockPos::getZ).min().orElse(0);
        int maxX = sources.stream().map(Building::getRawPos1).mapToInt(BlockPos::getX).max().orElse(0);
        int maxY = Math.max(
                sources.stream().map(Building::getRawPos1).mapToInt(BlockPos::getY).max().orElse(0),
                regions.stream().mapToInt(BuildingFloorRegion::anchorY).max().orElse(0));
        int maxZ = sources.stream().map(Building::getRawPos1).mapToInt(BlockPos::getZ).max().orElse(0);
        return new Bounds(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    private static StructureFloor nearestFloor(List<StructureFloor> floors, int y) {
        return floors.stream().min(Comparator
                .comparingInt((StructureFloor floor) -> Math.abs(floor.anchorY() - y))
                .thenComparingInt(StructureFloor::anchorY)
                .thenComparingInt(StructureFloor::id)).orElse(null);
    }

    private static List<BuildingFloorRegion> volumeSlices(List<StructureFloor> floors) {
        List<BuildingFloorRegion> slices = new ArrayList<>();
        for (StructureFloor floor : floors) {
            if (floor.region() == null) continue;
            for (int y = floor.anchorY(); y < floor.ceilingY(); y++) {
                slices.add(floor.region().withAnchorY(y));
            }
        }
        return List.copyOf(slices);
    }

    private static List<BlockPos> rectangleFootprint(BlockPos min, BlockPos max, int y) {
        List<BlockPos> cells = new ArrayList<>();
        for (int z = min.getZ(); z <= max.getZ(); z++) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                cells.add(new BlockPos(x, y, z));
            }
        }
        return cells;
    }

    private record LegacyFloorRoom(
            CompoundTag tag,
            Building room,
            int legacyStructureId,
            boolean structureRoot,
            boolean strictScan,
            int floorY,
            int groundFloorY
    ) {
    }

    private record MigratedStructure(Structure structure, List<Building> rooms) {
    }

    private record Bounds(BlockPos min, BlockPos max) {
    }

    record Result(
            Map<Integer, Building> buildings,
            Map<Integer, ExternalBuilding> externalBuildings,
            Map<Integer, Structure> structures
    ) {
    }
}
