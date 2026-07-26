package net.conczin.mca.server.world.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeSet;

/** Pure derived view that groups vertically adjacent physical Structures into logical Buildings. */
public final class StructureLayout {
    private StructureLayout() {
    }

    public static Layout build(Village village) {
        if (village == null) return Layout.EMPTY;

        List<Building> rooms = village.getRooms().toList();
        List<BuildingLayout> buildings = groups(village.getStructures().values()).stream()
                .map(structures -> buildBuilding(structures, rooms))
                .toList();
        Map<Integer, BuildingLayout> byStructure = new HashMap<>();
        Map<Long, Integer> ordinalByFloor = new HashMap<>();
        Map<Integer, Integer> ordinalByRoom = new HashMap<>();
        for (BuildingLayout building : buildings) {
            building.structureIds().forEach(id -> byStructure.put(id, building));
            for (Storey storey : building.storeys()) {
                for (FloorRef floor : storey.floors()) {
                    ordinalByFloor.put(floorKey(floor.structureId(), floor.floorId()), storey.ordinal());
                }
            }
        }

        for (Building room : rooms) {
            Integer ordinal = ordinalByFloor.get(floorKey(room.getStructureId(), room.getFloorId()));
            BuildingLayout building = byStructure.get(room.getStructureId());
            if (ordinal != null && building != null) {
                ordinalByRoom.put(room.getId(), ordinal);
            }
        }
        return new Layout(buildings, byStructure, ordinalByRoom);
    }

    static List<List<Structure>> groups(Collection<Structure> structures) {
        List<Structure> remaining = structures == null ? new ArrayList<>() : structures.stream()
                .filter(structure -> !structure.getFloors().isEmpty())
                .sorted(Comparator.comparingInt(Structure::getId))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<List<Structure>> groups = new ArrayList<>();
        while (!remaining.isEmpty()) {
            List<Structure> members = new ArrayList<>();
            members.add(remaining.removeFirst());
            for (int i = 0; i < members.size(); i++) {
                Structure member = members.get(i);
                for (Iterator<Structure> iterator = remaining.iterator(); iterator.hasNext();) {
                    Structure candidate = iterator.next();
                    if (!hasAdjacentOverlappingFloor(member, candidate)) continue;
                    members.add(candidate);
                    iterator.remove();
                }
            }
            members.sort(Comparator.comparingInt(Structure::getId));
            groups.add(List.copyOf(members));
        }
        return List.copyOf(groups);
    }

    private static boolean hasAdjacentOverlappingFloor(Structure first, Structure second) {
        for (StructureFloor firstFloor : first.getFloors()) {
            if (firstFloor.region() == null) continue;
            for (StructureFloor secondFloor : second.getFloors()) {
                if (secondFloor.region() == null
                        || firstFloor.region().intersectionArea(secondFloor.region()) == 0) continue;
                StructureFloor lower = firstFloor.anchorY() < secondFloor.anchorY() ? firstFloor : secondFloor;
                StructureFloor upper = lower == firstFloor ? secondFloor : firstFloor;
                if (lower.anchorY() == upper.anchorY()) continue;
                int verticalGap = upper.anchorY() - lower.ceilingY();
                if (verticalGap >= 0
                        && verticalGap <= BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE) {
                    return true;
                }
            }
        }
        return false;
    }

    private static BuildingLayout buildBuilding(List<Structure> structures, List<Building> rooms) {
        List<PhysicalFloor> floors = structures.stream()
                .flatMap(structure -> structure.getFloors().stream()
                        .map(floor -> new PhysicalFloor(structure.getId(), floor.id(), floor.anchorY(),
                                structure.getSurfaceReferenceY())))
                .sorted(Comparator.comparingInt(PhysicalFloor::anchorY)
                        .thenComparingInt(PhysicalFloor::structureId)
                        .thenComparingInt(PhysicalFloor::floorId))
                .toList();
        List<List<PhysicalFloor>> bands = clusterBands(floors);
        MainRoomSelector.Selection selection = MainRoomSelector.resolve(structures, rooms);
        int groundIndex = groundBandIndex(floors, bands);

        List<Storey> storeys = new ArrayList<>();
        for (int i = 0; i < bands.size(); i++) {
            List<PhysicalFloor> band = bands.get(i);
            storeys.add(new Storey(band.getFirst().anchorY(), i - groundIndex, band.stream()
                    .map(floor -> new FloorRef(floor.structureId(), floor.floorId()))
                    .toList()));
        }
        return new BuildingLayout(
                structures.getFirst().getId(),
                structures.stream().map(Structure::getId).toList(),
                storeys,
                selection == null ? -1 : selection.roomId(),
                selection == null || selection.automatic());
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

    /** Ground is physical terrain-relative evidence, not whichever Room the player registered first. */
    private static int groundBandIndex(List<PhysicalFloor> floors,
                                       List<List<PhysicalFloor>> bands) {
        PhysicalFloor ground = floors.stream().min(Comparator
                .comparingInt((PhysicalFloor floor) ->
                        Math.abs(floor.anchorY() - floor.surfaceReferenceY()))
                .thenComparing(Comparator.comparingInt(PhysicalFloor::anchorY).reversed())
                .thenComparingInt(PhysicalFloor::structureId)
                .thenComparingInt(PhysicalFloor::floorId))
                .orElse(null);
        if (ground == null) return 0;
        int index = floorBandIndex(ground.structureId(), ground.floorId(), bands);
        return Math.max(0, index);
    }

    private static int floorBandIndex(int structureId,
                                      int floorId,
                                      List<List<PhysicalFloor>> bands) {
        long key = floorKey(structureId, floorId);
        for (int i = 0; i < bands.size(); i++) {
            if (bands.get(i).stream().anyMatch(floor ->
                    floorKey(floor.structureId(), floor.floorId()) == key)) return i;
        }
        return -1;
    }

    private static long floorKey(int structureId, int floorId) {
        return ((long) structureId << 32) ^ (floorId & 0xffffffffL);
    }

    private record PhysicalFloor(int structureId, int floorId, int anchorY, int surfaceReferenceY) {
    }

    public record Layout(List<BuildingLayout> buildings,
                         Map<Integer, BuildingLayout> byStructure,
                         Map<Integer, Integer> ordinalByRoom) {
        private static final Layout EMPTY = new Layout(List.of(), Map.of(), Map.of());

        public Layout {
            buildings = List.copyOf(buildings);
            byStructure = Map.copyOf(byStructure);
            ordinalByRoom = Map.copyOf(ordinalByRoom);
        }

        public Optional<BuildingLayout> buildingFor(int structureId) {
            return Optional.ofNullable(byStructure.get(structureId));
        }

        public OptionalInt ordinalForRoom(int roomId) {
            Integer ordinal = ordinalByRoom.get(roomId);
            return ordinal == null ? OptionalInt.empty() : OptionalInt.of(ordinal);
        }

        public boolean isRoomOnFloor(int roomId, int floorOrdinal) {
            return ordinalForRoom(roomId).orElse(Integer.MIN_VALUE) == floorOrdinal;
        }

        public List<Integer> ordinals() {
            TreeSet<Integer> ordinals = new TreeSet<>();
            buildings.forEach(building ->
                    building.storeys().forEach(storey -> ordinals.add(storey.ordinal())));
            return List.copyOf(ordinals);
        }

        public List<Integer> ordinalsForStructure(int structureId) {
            BuildingLayout building = byStructure.get(structureId);
            return building == null ? List.of() : building.storeys().stream()
                    .map(Storey::ordinal)
                    .toList();
        }

        public OptionalInt mainRoomIdForStructure(int structureId) {
            BuildingLayout building = byStructure.get(structureId);
            return building == null || building.mainRoomId() < 0
                    ? OptionalInt.empty() : OptionalInt.of(building.mainRoomId());
        }
    }

    public record BuildingLayout(int id,
                                 List<Integer> structureIds,
                                 List<Storey> storeys,
                                 int mainRoomId,
                                 boolean mainRoomAutomatic) {
        public BuildingLayout {
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
