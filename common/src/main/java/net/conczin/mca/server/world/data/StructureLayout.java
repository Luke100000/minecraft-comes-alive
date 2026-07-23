package net.conczin.mca.server.world.data;

import java.util.*;

/** Pure derived view over persistent physical Structures and StructureFloors. */
public final class StructureLayout {
    private StructureLayout() {
    }

    public static Layout build(Village village) {
        if (village == null || village.getStructures().isEmpty()) return Layout.EMPTY;

        List<Structure> remaining = new ArrayList<>(village.getStructures().values());
        remaining.sort(Comparator.comparingInt(Structure::getId));
        List<LogicalBuilding> buildings = new ArrayList<>();

        while (!remaining.isEmpty()) {
            List<Structure> members = new ArrayList<>();
            members.add(remaining.remove(0));
            for (int i = 0; i < members.size(); i++) {
                Structure member = members.get(i);
                for (Iterator<Structure> iterator = remaining.iterator(); iterator.hasNext();) {
                    Structure candidate = iterator.next();
                    if (!stacked(member, candidate)) continue;
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
        for (LogicalBuilding building : buildings) {
            building.structureIds().forEach(id -> byStructure.put(id, building));
            for (Storey storey : building.storeys()) {
                storey.floors().forEach(floor -> ordinalByFloor.put(
                        floorKey(floor.structureId(), floor.floorId()), storey.ordinal()));
            }
        }
        return new Layout(buildings, byStructure, ordinalByFloor);
    }

    private static LogicalBuilding buildLogicalBuilding(Village village, List<Structure> structures) {
        List<PhysicalFloor> floors = structures.stream()
                .flatMap(structure -> structure.getFloors().stream().map(floor -> new PhysicalFloor(structure, floor)))
                .sorted(Comparator.comparingInt(PhysicalFloor::anchorY)
                        .thenComparingInt(floor -> floor.structure().getId())
                        .thenComparingInt(PhysicalFloor::floorId))
                .toList();

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

        Set<Long> groundFloors = new HashSet<>();
        for (Structure structure : structures) {
            structure.getGroundFloor(village).ifPresent(floor -> groundFloors.add(floorKey(structure.getId(), floor.id())));
        }
        int groundIndex = 0;
        for (int i = 0; i < bands.size(); i++) {
            if (bands.get(i).stream().anyMatch(floor -> groundFloors.contains(floor.key()))) {
                groundIndex = i;
                break;
            }
        }

        Set<Long> logicalGround = bands.isEmpty() ? Set.of() : bands.get(groundIndex).stream()
                .map(PhysicalFloor::key).collect(java.util.stream.Collectors.toSet());
        int mainRoomId = structures.stream()
                .filter(structure -> structure.getGroundFloor(village)
                        .map(floor -> logicalGround.contains(floorKey(structure.getId(), floor.id())))
                        .orElse(false))
                .mapToInt(Structure::getRootRoomId)
                .filter(id -> id >= 0)
                .findFirst()
                .orElseGet(() -> structures.stream().mapToInt(Structure::getRootRoomId)
                        .filter(id -> id >= 0).findFirst().orElse(-1));

        List<Storey> storeys = new ArrayList<>();
        for (int i = 0; i < bands.size(); i++) {
            List<PhysicalFloor> band = bands.get(i);
            storeys.add(new Storey(band.getFirst().anchorY(), i - groundIndex, band.stream()
                    .map(floor -> new FloorRef(floor.structure().getId(), floor.floorId())).toList()));
        }
        return new LogicalBuilding(structures.getFirst().getId(),
                structures.stream().map(Structure::getId).toList(), storeys, groundIndex, mainRoomId);
    }

    private static boolean stacked(Structure first, Structure second) {
        for (StructureFloor firstFloor : first.getFloors()) {
            for (StructureFloor secondFloor : second.getFloors()) {
                StructureFloor lower = firstFloor.anchorY() <= secondFloor.anchorY() ? firstFloor : secondFloor;
                StructureFloor upper = lower == firstFloor ? secondFloor : firstFloor;
                if (upper.anchorY() - lower.anchorY() <= BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE) continue;
                int gap = upper.anchorY() - lower.ceilingY();
                if (gap < 0 || gap > 1 || lower.region() == null || upper.region() == null) continue;
                long minimumArea = Math.min(lower.area(), upper.area());
                if (minimumArea > 0 && lower.region().intersectionArea(upper.region()) * 2 >= minimumArea) return true;
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

    public record Layout(List<LogicalBuilding> buildings,
                         Map<Integer, LogicalBuilding> byStructure,
                         Map<Long, Integer> ordinalByFloor) {
        private static final Layout EMPTY = new Layout(List.of(), Map.of(), Map.of());

        public Layout {
            buildings = List.copyOf(buildings);
            byStructure = Map.copyOf(byStructure);
            ordinalByFloor = Map.copyOf(ordinalByFloor);
        }

        public Optional<LogicalBuilding> buildingFor(int structureId) {
            return Optional.ofNullable(byStructure.get(structureId));
        }

        public OptionalInt ordinal(int structureId, int floorId) {
            Integer ordinal = ordinalByFloor.get(floorKey(structureId, floorId));
            return ordinal == null ? OptionalInt.empty() : OptionalInt.of(ordinal);
        }
    }

    public record LogicalBuilding(int id,
                                  List<Integer> structureIds,
                                  List<Storey> storeys,
                                  int groundStoreyIndex,
                                  int mainRoomId) {
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
}
