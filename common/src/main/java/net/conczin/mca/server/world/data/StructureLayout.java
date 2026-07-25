package net.conczin.mca.server.world.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeSet;

/** Pure derived floor view: one layout entry for one persistent physical Structure. */
public final class StructureLayout {
    private StructureLayout() {
    }

    public static Layout build(Village village) {
        if (village == null) return Layout.EMPTY;

        List<BuildingLayout> buildings = village.getStructures().values().stream()
                .filter(structure -> !structure.getFloors().isEmpty())
                .sorted(Comparator.comparingInt(Structure::getId))
                .map(structure -> buildStructure(village, structure))
                .toList();
        Map<Integer, BuildingLayout> byStructure = new HashMap<>();
        Map<Long, Integer> ordinalByFloor = new HashMap<>();
        Map<Integer, Placement> placementByRoom = new HashMap<>();
        for (BuildingLayout building : buildings) {
            byStructure.put(building.structureId(), building);
            for (Storey storey : building.storeys()) {
                for (FloorRef floor : storey.floors()) {
                    ordinalByFloor.put(floorKey(floor.structureId(), floor.floorId()), storey.ordinal());
                }
            }
        }

        village.getRooms().forEach(room -> {
            Integer ordinal = ordinalByFloor.get(floorKey(room.getStructureId(), room.getFloorId()));
            if (ordinal != null && byStructure.containsKey(room.getStructureId())) {
                placementByRoom.put(room.getId(), new Placement(room.getStructureId(), ordinal));
            }
        });
        return new Layout(buildings, byStructure, ordinalByFloor, placementByRoom);
    }

    private static BuildingLayout buildStructure(Village village, Structure structure) {
        List<StructureFloor> floors = structure.getFloors();
        Building mainRoom = village.getBuilding(structure.getMainRoomId())
                .filter(Building::isFunctionalRoom)
                .filter(room -> room.getStructureId() == structure.getId())
                .filter(room -> structure.getFloor(room.getFloorId()).isPresent())
                .orElse(null);
        int groundFloorId = mainRoom == null
                ? structure.getAutomaticGroundFloorId() : mainRoom.getFloorId();
        int groundIndex = 0;
        for (int i = 0; i < floors.size(); i++) {
            if (floors.get(i).id() == groundFloorId) {
                groundIndex = i;
                break;
            }
        }

        List<Storey> storeys = new ArrayList<>();
        for (int i = 0; i < floors.size(); i++) {
            StructureFloor floor = floors.get(i);
            storeys.add(new Storey(floor.anchorY(), i - groundIndex,
                    List.of(new FloorRef(structure.getId(), floor.id()))));
        }
        return new BuildingLayout(structure.getId(), storeys, groundIndex,
                mainRoom == null ? -1 : mainRoom.getId());
    }

    private static long floorKey(int structureId, int floorId) {
        return ((long) structureId << 32) ^ (floorId & 0xffffffffL);
    }

    public record Layout(List<BuildingLayout> buildings,
                         Map<Integer, BuildingLayout> byStructure,
                         Map<Long, Integer> ordinalByFloor,
                         Map<Integer, Placement> placementByRoom) {
        private static final Layout EMPTY = new Layout(List.of(), Map.of(), Map.of(), Map.of());

        public Layout {
            buildings = List.copyOf(buildings);
            byStructure = Map.copyOf(byStructure);
            ordinalByFloor = Map.copyOf(ordinalByFloor);
            placementByRoom = Map.copyOf(placementByRoom);
        }

        public Optional<BuildingLayout> buildingFor(int structureId) {
            return Optional.ofNullable(byStructure.get(structureId));
        }

        public OptionalInt ordinal(int structureId, int floorId) {
            Integer ordinal = ordinalByFloor.get(floorKey(structureId, floorId));
            return ordinal == null ? OptionalInt.empty() : OptionalInt.of(ordinal);
        }

        public Optional<Placement> placementFor(int roomId) {
            return Optional.ofNullable(placementByRoom.get(roomId));
        }

        public OptionalInt ordinalForBuilding(int roomId) {
            Placement placement = placementByRoom.get(roomId);
            return placement == null ? OptionalInt.empty() : OptionalInt.of(placement.ordinal());
        }

        public boolean isBuildingOnFloor(int roomId, int floorOrdinal) {
            return ordinalForBuilding(roomId).orElse(Integer.MIN_VALUE) == floorOrdinal;
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

    public record BuildingLayout(int structureId,
                                 List<Storey> storeys,
                                 int groundStoreyIndex,
                                 int mainRoomId) {
        public BuildingLayout {
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

    public record Placement(int structureId, int ordinal) {
    }
}
