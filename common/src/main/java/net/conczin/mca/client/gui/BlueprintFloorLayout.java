package net.conczin.mca.client.gui;

import net.conczin.mca.server.world.data.*;

import java.util.*;

/** Maps registered Rooms onto logical floor ordinals derived by {@link StructureLayout}. */
final class BlueprintFloorLayout {
    private final StructureLayout.Layout structureLayout;
    private final Map<Integer, Integer> ordinalByBuildingId;
    private final Map<Integer, List<Integer>> ordinalsByStructureId;
    private final List<Integer> ordinals;

    private BlueprintFloorLayout(StructureLayout.Layout structureLayout,
                                 Map<Integer, Integer> ordinalByBuildingId,
                                 Map<Integer, List<Integer>> ordinalsByStructureId,
                                 List<Integer> ordinals) {
        this.structureLayout = structureLayout;
        this.ordinalByBuildingId = ordinalByBuildingId;
        this.ordinalsByStructureId = ordinalsByStructureId;
        this.ordinals = ordinals;
    }

    static BlueprintFloorLayout empty() {
        return new BlueprintFloorLayout(StructureLayout.build(null), Map.of(), Map.of(), List.of());
    }

    static BlueprintFloorLayout build(Village village) {
        return village == null ? empty() : build(village, StructureLayout.build(village));
    }

    static BlueprintFloorLayout build(Village village, StructureLayout.Layout layout) {
        Map<Integer, Integer> byBuilding = new HashMap<>();
        Map<Integer, List<Integer>> byStructure = new HashMap<>();
        TreeSet<Integer> available = new TreeSet<>();

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
        return new BlueprintFloorLayout(layout, Map.copyOf(byBuilding),
                Map.copyOf(byStructure), List.copyOf(available));
    }

    List<Integer> ordinals() { return ordinals; }

    List<Integer> ordinalsFor(Building building) {
        return ordinalsByStructureId.getOrDefault(building.getEffectiveStructureId(), List.of());
    }

    OptionalInt ordinalForFloor(int structureId, int floorId) {
        return structureLayout.ordinal(structureId, floorId);
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
