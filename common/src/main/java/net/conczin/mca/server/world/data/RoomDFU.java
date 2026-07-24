package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/** One-time compatibility boundary for origin/1.21.1 and the pre-v2 floor-system format. */
final class RoomDFU {
    private RoomDFU() {
    }

    static Result load(CompoundTag villageTag) {
        boolean legacyInheritance = villageTag.contains("roomInheritance")
                && villageTag.getBoolean("roomInheritance");
        if (villageTag.contains("structures", Tag.TAG_LIST)) {
            return loadCurrent(villageTag, legacyInheritance);
        }

        ListTag legacy = villageTag.getList("buildings", Tag.TAG_COMPOUND);
        boolean floorSystemFormat = legacy.stream()
                .filter(CompoundTag.class::isInstance)
                .map(CompoundTag.class::cast)
                .anyMatch(tag -> tag.contains("structureRoot")
                        || tag.contains("strictScan")
                        || tag.contains("groundFloorY")
                        || tag.contains("floorRegions"));
        return floorSystemFormat
                ? migrateFloorSystem(legacy, legacyInheritance)
                : migrateOrigin(legacy, legacyInheritance);
    }

    private static Result loadCurrent(CompoundTag villageTag, boolean legacyInheritance) {
        Map<Integer, Building> rooms = new HashMap<>();
        Map<Integer, ExternalBuilding> external = new HashMap<>();
        Map<Integer, Structure> structures = new HashMap<>();
        for (Tag value : villageTag.getList("buildings", Tag.TAG_COMPOUND)) {
            Building room = loadRoom((CompoundTag) value, legacyInheritance);
            rooms.put(room.getId(), room);
        }
        for (Tag value : villageTag.getList("externalBuildings", Tag.TAG_COMPOUND)) {
            ExternalBuilding building = new ExternalBuilding((CompoundTag) value);
            external.put(building.getId(), building);
        }
        for (Tag value : villageTag.getList("structures", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) value;
            Structure structure = new Structure(tag);
            if (!tag.contains("automaticGroundFloorId")) {
                Building oldRoot = tag.contains("rootRoomId") ? rooms.get(tag.getInt("rootRoomId")) : null;
                StructureFloor ground = oldRoot == null ? null : structure.getFloor(oldRoot.getFloorId()).orElse(null);
                if (ground == null) ground = structure.getFloors().stream().findFirst().orElse(null);
                if (ground != null) {
                    structure.setGroundEvidence(ground.id(), ground.anchorY(), 0);
                    if (oldRoot != null && tag.getBoolean("groundAnchorExplicit")) {
                        oldRoot.setLayoutOverride(true);
                    }
                }
            }
            structures.put(structure.getId(), structure);
        }
        return new Result(rooms, external, structures);
    }

    private static Building loadRoom(CompoundTag tag, boolean legacyInheritance) {
        Building room = new Building(tag);
        if (!tag.contains("inheritanceEnabled")) room.setInheritanceEnabled(legacyInheritance);
        return room;
    }

    private static Result migrateOrigin(ListTag legacy, boolean legacyInheritance) {
        Map<Integer, Building> rooms = new HashMap<>();
        Map<Integer, ExternalBuilding> external = new HashMap<>();
        Map<Integer, Structure> structures = new HashMap<>();
        for (Tag value : legacy) {
            CompoundTag tag = (CompoundTag) value;
            Building room = loadRoom(tag, legacyInheritance);
            if (room.getBuildingType().grouped()) {
                ExternalBuilding building = new ExternalBuilding(tag);
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

    private static Result migrateFloorSystem(ListTag legacy, boolean legacyInheritance) {
        Map<Integer, ExternalBuilding> external = new HashMap<>();
        Map<Integer, List<LegacyFloorRoom>> byStructure = new TreeMap<>();
        for (Tag value : legacy) {
            CompoundTag tag = (CompoundTag) value;
            Building room = loadRoom(tag, legacyInheritance);
            if (room.getBuildingType().grouped()) {
                ExternalBuilding building = new ExternalBuilding(tag);
                external.put(building.getId(), building);
                continue;
            }
            int structureId = tag.contains("structureId") && tag.getInt("structureId") >= 0
                    ? tag.getInt("structureId") : room.getId();
            byStructure.computeIfAbsent(structureId, ignored -> new ArrayList<>()).add(new LegacyFloorRoom(
                    tag,
                    room,
                    tag.contains("structureRoot") && tag.getBoolean("structureRoot"),
                    tag.contains("strictScan") && tag.getBoolean("strictScan"),
                    tag.contains("floorY") ? tag.getInt("floorY") : room.getFloorY(),
                    tag.contains("groundFloorY") ? tag.getInt("groundFloorY") : room.getFloorY()));
        }

        Map<Integer, Building> rooms = new HashMap<>();
        Map<Integer, Structure> structures = new HashMap<>();
        byStructure.forEach((structureId, records) -> migrateFloorSystemStructure(structureId, records)
                .ifPresent(migrated -> {
                    migrated.rooms().forEach(room -> rooms.put(room.getId(), room));
                    structures.put(structureId, migrated.structure());
                }));
        return new Result(rooms, external, structures);
    }

    /**
     * Migrates only deterministic persisted geometry. Development Structures that require invented
     * Floors are discarded individually and can be rescanned in-game.
     */
    private static Optional<MigratedStructure> migrateFloorSystemStructure(
            int structureId,
            List<LegacyFloorRoom> records) {
        LegacyFloorRoom container = records.stream()
                .filter(record -> record.structureRoot() && !record.strictScan())
                .min(Comparator.comparingInt(record -> record.room().getId()))
                .or(() -> records.stream()
                        .filter(record -> !record.strictScan())
                        .min(Comparator.comparingInt(record -> record.room().getId())))
                .orElse(null);
        if (container == null) {
            return Optional.empty();
        }

        List<LegacyFloorRoom> functional = records.stream()
                .filter(LegacyFloorRoom::strictScan)
                .filter(record -> !record.structureRoot())
                .filter(record -> !record.room().getFloorRegions().isEmpty())
                .sorted(Comparator.comparingInt(record -> record.room().getId()))
                .toList();
        List<BuildingFloorRegion> regions = collectFloorRegions(container, functional);
        if (regions.isEmpty()) return Optional.empty();

        BlockPos min = container.room().getRawPos0();
        BlockPos max = container.room().getRawPos1();
        List<StructureFloor> floors = createFloors(regions, max.getY() + 1);
        StructureFloor ground = nearestFloor(floors, container.groundFloorY());
        if (ground == null) {
            return Optional.empty();
        }

        List<Building> rooms = new ArrayList<>();
        for (LegacyFloorRoom legacyRoom : functional) {
            StructureFloor floor = nearestFloor(floors, legacyRoom.floorY());
            if (floor == null) continue;
            Building room = legacyRoom.room();
            room.setStructureId(structureId);
            room.setFloorId(floor.id());
            rooms.add(room);
        }
        Structure structure = new Structure(
                structureId,
                container.room().getSourceBlock(),
                min,
                max,
                floors);
        structure.setGroundEvidence(ground.id(), container.groundFloorY(), 0);
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
                List.of(floor));
        structure.setGroundEvidence(floor.id(), floor.anchorY(), 0);
        room.setStructureId(structure.getId());
        room.setFloorId(floor.id());
        return Optional.of(structure);
    }

    private static List<BuildingFloorRegion> collectFloorRegions(
            LegacyFloorRoom container,
            List<LegacyFloorRoom> functional) {
        Map<Integer, BuildingFloorRegion> byY = new TreeMap<>();
        for (BuildingFloorRegion region : container.room().getFloorRegions()) {
            byY.merge(region.anchorY(), region, RoomDFU::largerRegion);
        }
        for (LegacyFloorRoom room : functional) {
            for (BuildingFloorRegion region : room.room().getFloorRegions()) {
                byY.merge(room.floorY(), region.withAnchorY(room.floorY()), RoomDFU::largerRegion);
            }
        }
        return List.copyOf(byY.values());
    }

    private static BuildingFloorRegion largerRegion(BuildingFloorRegion first, BuildingFloorRegion second) {
        return first.area() >= second.area() ? first : second;
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

    private static StructureFloor nearestFloor(List<StructureFloor> floors, int y) {
        return floors.stream().min(Comparator
                .comparingInt((StructureFloor floor) -> Math.abs(floor.anchorY() - y))
                .thenComparingInt(StructureFloor::anchorY)
                .thenComparingInt(StructureFloor::id)).orElse(null);
    }

    private record LegacyFloorRoom(
            CompoundTag tag,
            Building room,
            boolean structureRoot,
            boolean strictScan,
            int floorY,
            int groundFloorY) {
    }

    private record MigratedStructure(Structure structure, List<Building> rooms) {
    }

    record Result(
            Map<Integer, Building> buildings,
            Map<Integer, ExternalBuilding> externalBuildings,
            Map<Integer, Structure> structures) {
    }
}
