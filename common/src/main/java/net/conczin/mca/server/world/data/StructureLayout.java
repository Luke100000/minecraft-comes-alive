package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

import java.util.*;

/** Pure derived view over persistent Structures, Floors, Rooms and external sites. */
public final class StructureLayout {
    private StructureLayout() {
    }

    public static Layout build(Village village) {
        if (village == null) return Layout.EMPTY;

        List<Structure> remaining = village.getStructures().values().stream()
                .filter(structure -> !structure.getFloors().isEmpty())
                .sorted(Comparator.comparingInt(Structure::getId))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<LogicalBuilding> buildings = new ArrayList<>();
        while (!remaining.isEmpty()) {
            List<Structure> members = new ArrayList<>();
            members.add(remaining.removeFirst());
            for (int i = 0; i < members.size(); i++) {
                Structure member = members.get(i);
                for (Iterator<Structure> iterator = remaining.iterator(); iterator.hasNext();) {
                    Structure candidate = iterator.next();
                    if (!overlapsHorizontally(member, candidate)) continue;
                    members.add(candidate);
                    iterator.remove();
                }
            }
            members.sort(Comparator.comparingInt(Structure::getId));
            buildings.add(buildLogicalBuilding(village, members));
        }

        buildings.sort(Comparator.comparingInt(LogicalBuilding::id));
        Map<Integer, LogicalBuilding> byStructure = new HashMap<>();
        Map<Long, Integer> ordinalByFloor = new HashMap<>();
        Map<Integer, Placement> placementByBuilding = new HashMap<>();
        for (LogicalBuilding building : buildings) {
            building.structureIds().forEach(id -> byStructure.put(id, building));
            for (Storey storey : building.storeys()) {
                storey.floors().forEach(floor -> ordinalByFloor.put(
                        floorKey(floor.structureId(), floor.floorId()), storey.ordinal()));
            }
        }

        village.getRooms().forEach(room -> {
            Integer ordinal = ordinalByFloor.get(floorKey(room.getStructureId(), room.getFloorId()));
            LogicalBuilding logical = byStructure.get(room.getStructureId());
            if (ordinal != null && logical != null) {
                placementByBuilding.put(room.getId(), new Placement(logical.id(), ordinal));
            }
        });
        village.getExternalBuildings().forEach(external -> placementByBuilding.put(
                external.getId(), placeExternal(village, buildings, external)));

        return new Layout(buildings, byStructure, ordinalByFloor, placementByBuilding);
    }

    private static LogicalBuilding buildLogicalBuilding(Village village, List<Structure> structures) {
        List<PhysicalFloor> floors = structures.stream()
                .flatMap(structure -> structure.getFloors().stream().map(floor -> new PhysicalFloor(structure, floor)))
                .sorted(Comparator.comparingInt(PhysicalFloor::anchorY)
                        .thenComparingInt(floor -> floor.structure().getId())
                        .thenComparingInt(PhysicalFloor::floorId))
                .toList();

        List<List<PhysicalFloor>> bands = clusterBands(floors);
        Set<Integer> structureIds = structures.stream().map(Structure::getId).collect(java.util.stream.Collectors.toSet());
        List<Building> rooms = village.getRooms()
                .filter(room -> structureIds.contains(room.getStructureId()))
                .sorted(Comparator.comparingInt(Building::getId))
                .toList();

        Building override = rooms.stream()
                .filter(Building::isLayoutOverride)
                .filter(room -> floorBandIndex(room.getStructureId(), room.getFloorId(), bands) >= 0)
                .findFirst()
                .orElse(null);

        int groundIndex;
        if (override != null) {
            groundIndex = floorBandIndex(override.getStructureId(), override.getFloorId(), bands);
        } else {
            List<StructureLayoutRules.GroundCandidate> candidates = new ArrayList<>();
            for (Structure structure : structures) {
                StructureFloor ground = structure.getAutomaticGroundFloor().orElse(null);
                if (ground == null) continue;
                int storeyIndex = floorBandIndex(structure.getId(), ground.id(), bands);
                if (storeyIndex < 0) continue;
                candidates.add(new StructureLayoutRules.GroundCandidate(
                        storeyIndex,
                        structure.getId(),
                        ground.anchorY(),
                        structure.getGroundReferenceY(),
                        structure.getGroundEntranceCount()));
            }
            groundIndex = StructureLayoutRules.selectGroundIndex(candidates);
        }
        if (groundIndex < 0 || groundIndex >= bands.size()) groundIndex = 0;

        List<Storey> storeys = new ArrayList<>();
        for (int i = 0; i < bands.size(); i++) {
            List<PhysicalFloor> band = bands.get(i);
            storeys.add(new Storey(band.getFirst().anchorY(), i - groundIndex, band.stream()
                    .map(floor -> new FloorRef(floor.structure().getId(), floor.floorId())).toList()));
        }

        int rootRoomId = override == null
                ? selectAutomaticRootRoom(rooms, floors, storeys.get(groundIndex).anchorY())
                : override.getId();
        return new LogicalBuilding(structures.getFirst().getId(),
                structures.stream().map(Structure::getId).toList(), storeys, groundIndex, rootRoomId);
    }

    private static List<List<PhysicalFloor>> clusterBands(List<PhysicalFloor> floors) {
        List<List<PhysicalFloor>> bands = new ArrayList<>();
        for (PhysicalFloor floor : floors) {
            List<PhysicalFloor> band = bands.isEmpty() ? null : bands.getLast();
            if (band == null || floor.anchorY() - band.getFirst().anchorY()
                    > BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE) {
                band = new ArrayList<>();
                bands.add(band);
            }
            band.add(floor);
        }
        return bands;
    }

    private static int selectAutomaticRootRoom(List<Building> rooms,
                                               List<PhysicalFloor> floors,
                                               int groundAnchorY) {
        Map<Long, Integer> anchorByFloor = new HashMap<>();
        floors.forEach(floor -> anchorByFloor.put(floor.key(), floor.anchorY()));
        List<StructureLayoutRules.RoomCandidate> candidates = rooms.stream()
                .map(room -> {
                    Integer anchor = anchorByFloor.get(floorKey(room.getStructureId(), room.getFloorId()));
                    return anchor == null ? null : new StructureLayoutRules.RoomCandidate(room.getId(), anchor);
                })
                .filter(Objects::nonNull)
                .toList();
        return StructureLayoutRules.selectRootRoomId(candidates, groundAnchorY);
    }

    private static Placement placeExternal(Village village,
                                           List<LogicalBuilding> buildings,
                                           ExternalBuilding external) {
        List<BlockPos> positions = external.getBlockPosStream().toList();
        List<? extends Vec3i> samples = positions.isEmpty() ? List.of(external.getCenter()) : positions;
        int margin = Math.max(1, external.getBuildingType().getMargin());
        long maximumDistance = (long) margin * margin;
        LogicalBuilding owner = buildings.stream()
                .map(building -> new ExternalOwner(building,
                        building.structureIds().stream()
                                .map(village::getStructure)
                                .flatMap(Optional::stream)
                                .mapToLong(structure -> samples.stream()
                                        .mapToLong(position -> horizontalDistanceSquared(structure, position))
                                        .min().orElse(Long.MAX_VALUE))
                                .min().orElse(Long.MAX_VALUE)))
                .filter(candidate -> candidate.distanceSquared() <= maximumDistance)
                .min(Comparator.comparingLong(ExternalOwner::distanceSquared)
                        .thenComparingInt(candidate -> candidate.building().id()))
                .map(ExternalOwner::building)
                .orElse(null);
        if (owner == null || owner.storeys().isEmpty()) return new Placement(-1, 0);

        int representativeY = medianY(samples);
        int index = StructureLayoutRules.nearestStoreyIndex(
                owner.storeys().stream().map(Storey::anchorY).toList(), representativeY);
        return new Placement(owner.id(), owner.storeys().get(index).ordinal());
    }

    private static int medianY(List<? extends Vec3i> positions) {
        int[] sorted = positions.stream().mapToInt(Vec3i::getY).sorted().toArray();
        int middle = sorted.length / 2;
        return sorted.length % 2 == 1
                ? sorted[middle]
                : Math.floorDiv(sorted[middle - 1] + sorted[middle], 2);
    }

    private static long horizontalDistanceSquared(Structure structure, Vec3i pos) {
        int dx = pos.getX() < structure.getRawPos0().getX()
                ? structure.getRawPos0().getX() - pos.getX()
                : Math.max(0, pos.getX() - structure.getRawPos1().getX());
        int dz = pos.getZ() < structure.getRawPos0().getZ()
                ? structure.getRawPos0().getZ() - pos.getZ()
                : Math.max(0, pos.getZ() - structure.getRawPos1().getZ());
        return (long) dx * dx + (long) dz * dz;
    }

    private static int floorBandIndex(int structureId,
                                      int floorId,
                                      List<List<PhysicalFloor>> bands) {
        long key = floorKey(structureId, floorId);
        for (int i = 0; i < bands.size(); i++) {
            if (bands.get(i).stream().anyMatch(floor -> floor.key() == key)) return i;
        }
        return -1;
    }

    private static boolean overlapsHorizontally(Structure first, Structure second) {
        for (StructureFloor firstFloor : first.getFloors()) {
            if (firstFloor.region() == null) continue;
            for (StructureFloor secondFloor : second.getFloors()) {
                if (secondFloor.region() != null
                        && firstFloor.region().intersectionArea(secondFloor.region()) > 0) return true;
            }
        }
        return false;
    }

    private static long floorKey(int structureId, int floorId) {
        return ((long) structureId << 32) ^ (floorId & 0xffffffffL);
    }

    private record PhysicalFloor(Structure structure, StructureFloor floor) {
        int floorId() { return floor.id(); }
        int anchorY() { return floor.anchorY(); }
        long key() { return floorKey(structure.getId(), floor.id()); }
    }

    private record ExternalOwner(LogicalBuilding building, long distanceSquared) {
    }

    public record Layout(List<LogicalBuilding> buildings,
                         Map<Integer, LogicalBuilding> byStructure,
                         Map<Long, Integer> ordinalByFloor,
                         Map<Integer, Placement> placementByBuilding) {
        private static final Layout EMPTY = new Layout(List.of(), Map.of(), Map.of(), Map.of());

        public Layout {
            buildings = List.copyOf(buildings);
            byStructure = Map.copyOf(byStructure);
            ordinalByFloor = Map.copyOf(ordinalByFloor);
            placementByBuilding = Map.copyOf(placementByBuilding);
        }

        public Optional<LogicalBuilding> buildingFor(int structureId) {
            return Optional.ofNullable(byStructure.get(structureId));
        }

        public OptionalInt ordinal(int structureId, int floorId) {
            Integer ordinal = ordinalByFloor.get(floorKey(structureId, floorId));
            return ordinal == null ? OptionalInt.empty() : OptionalInt.of(ordinal);
        }

        public Optional<Placement> placementFor(int buildingId) {
            return Optional.ofNullable(placementByBuilding.get(buildingId));
        }

        public OptionalInt ordinalForBuilding(int buildingId) {
            Placement placement = placementByBuilding.get(buildingId);
            return placement == null ? OptionalInt.empty() : OptionalInt.of(placement.ordinal());
        }

        public boolean isBuildingOnFloor(int buildingId, int floorOrdinal) {
            return ordinalForBuilding(buildingId).orElse(Integer.MIN_VALUE) == floorOrdinal;
        }

        public List<Integer> ordinals() {
            TreeSet<Integer> ordinals = new TreeSet<>();
            buildings.forEach(building -> building.storeys().forEach(storey -> ordinals.add(storey.ordinal())));
            placementByBuilding.values().forEach(placement -> ordinals.add(placement.ordinal()));
            return List.copyOf(ordinals);
        }

        public List<Integer> ordinalsForStructure(int structureId) {
            LogicalBuilding building = byStructure.get(structureId);
            return building == null ? List.of() : building.storeys().stream()
                    .map(Storey::ordinal)
                    .distinct()
                    .sorted()
                    .toList();
        }

        public OptionalInt rootRoomIdForStructure(int structureId) {
            LogicalBuilding building = byStructure.get(structureId);
            return building == null || building.rootRoomId() < 0
                    ? OptionalInt.empty() : OptionalInt.of(building.rootRoomId());
        }
    }

    public record LogicalBuilding(int id,
                                  List<Integer> structureIds,
                                  List<Storey> storeys,
                                  int groundStoreyIndex,
                                  int rootRoomId) {
        public LogicalBuilding {
            structureIds = List.copyOf(structureIds);
            storeys = List.copyOf(storeys);
        }
    }

    public record Storey(int anchorY, int ordinal, List<FloorRef> floors) {
        public Storey {
            floors = List.copyOf(floors);
        }
    }

    public record FloorRef(int structureId, int floorId) {
    }

    public record Placement(int logicalBuildingId, int ordinal) {
    }
}
