package net.conczin.mca.client.gui;

import net.conczin.mca.server.world.data.*;

import java.util.*;

/** Maps persistent Structure Floor IDs to Blueprint-local display ordinals. */
final class BlueprintFloorLayout {
    private final Map<Integer, Integer> ordinalByBuildingId;
    private final Map<Long, Integer> ordinalByFloorId;
    private final Map<Integer, List<Integer>> ordinalsByStructureId;
    private final List<Integer> ordinals;

    private BlueprintFloorLayout(Map<Integer, Integer> ordinalByBuildingId,
                                 Map<Long, Integer> ordinalByFloorId,
                                 Map<Integer, List<Integer>> ordinalsByStructureId,
                                 List<Integer> ordinals) {
        this.ordinalByBuildingId = ordinalByBuildingId;
        this.ordinalByFloorId = ordinalByFloorId;
        this.ordinalsByStructureId = ordinalsByStructureId;
        this.ordinals = ordinals;
    }

    static BlueprintFloorLayout empty() {
        return new BlueprintFloorLayout(Map.of(), Map.of(), Map.of(), List.of());
    }

    static BlueprintFloorLayout build(Village village) {
        return village == null ? empty() : build(village, StructureLayout.build(village));
    }

    static BlueprintFloorLayout build(Village village, StructureLayout.Layout layout) {
        Map<Integer, Integer> byBuilding = new HashMap<>();
        Map<Long, Integer> byFloor = new HashMap<>();
        Map<Integer, List<Integer>> byStructure = new HashMap<>();
        TreeSet<Integer> available = new TreeSet<>();

        for (Structure structure : village.getStructures().values()) {
            for (StructureFloor floor : structure.getFloors()) {
                layout.ordinal(structure.getId(), floor.id()).ifPresent(ordinal ->
                        byFloor.put(floorKey(structure.getId(), floor.id()), ordinal));
            }
        }
        for (StructureLayout.LogicalBuilding building : layout.buildings()) {
            TreeSet<Integer> registered = new TreeSet<>();
            registered.add(0);
            village.getRooms().filter(room -> building.structureIds().contains(room.getStructureId())).forEach(room ->
                    layout.ordinal(room.getStructureId(), room.getFloorId()).ifPresent(ordinal -> {
                        byBuilding.put(room.getId(), ordinal);
                        registered.add(ordinal);
                    }));
            List<Integer> ordinals = List.copyOf(registered);
            building.structureIds().forEach(id -> byStructure.put(id, ordinals));
            available.addAll(registered);
        }
        return new BlueprintFloorLayout(Map.copyOf(byBuilding), Map.copyOf(byFloor),
                Map.copyOf(byStructure), List.copyOf(available));
    }

    private static long floorKey(int structureId, int floorId) {
        return ((long) structureId << 32) ^ (floorId & 0xffffffffL);
    }

    List<Integer> ordinals() { return ordinals; }

    List<Integer> ordinalsFor(Building building) {
        return ordinalsByStructureId.getOrDefault(building.getEffectiveStructureId(), List.of());
    }

    OptionalInt ordinalForFloor(int structureId, int floorId) {
        Integer ordinal = ordinalByFloorId.get(floorKey(structureId, floorId));
        return ordinal == null ? OptionalInt.empty() : OptionalInt.of(ordinal);
    }

    boolean isBuildingOnFloor(Building building, int floorOrdinal) {
        Integer ordinal = ordinalByBuildingId.get(building.getId());
        return ordinal != null && ordinal == floorOrdinal;
    }

    boolean isBuildingVisible(Building building, Integer selectedFloor) {
        if (building instanceof ExternalBuilding || building.getBuildingType().grouped()) {
            return selectedFloor == null || selectedFloor == 0;
        }
        Integer ordinal = ordinalByBuildingId.get(building.getId());
        return ordinal != null && (selectedFloor == null || ordinal.equals(selectedFloor));
    }

    OptionalInt floorOrdinalFor(Building building) {
        Integer ordinal = ordinalByBuildingId.get(building.getId());
        return ordinal == null ? OptionalInt.empty() : OptionalInt.of(ordinal);
    }
}
