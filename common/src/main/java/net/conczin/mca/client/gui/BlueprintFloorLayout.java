package net.conczin.mca.client.gui;

import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import java.util.*;

/**
 * Maps server-owned canonical floor identities to Blueprint-local display ordinals.
 *
 * <p>The server already canonicalizes every functional room's {@link Building#getFloorY()}.
 * The client must never cluster or reinterpret those values: it only orders the distinct
 * persisted floors inside each structure relative to the root's Ground Floor.</p>
 */
final class BlueprintFloorLayout {
    private final Map<Integer, Integer> ordinalByBuildingId;
    private final Map<Integer, List<Integer>> ordinalsByStructureId;
    private final List<Integer> ordinals;

    private BlueprintFloorLayout(Map<Integer, Integer> ordinalByBuildingId,
                                 Map<Integer, List<Integer>> ordinalsByStructureId,
                                 List<Integer> ordinals) {
        this.ordinalByBuildingId = ordinalByBuildingId;
        this.ordinalsByStructureId = ordinalsByStructureId;
        this.ordinals = ordinals;
    }

    static BlueprintFloorLayout empty() {
        return new BlueprintFloorLayout(Map.of(), Map.of(), List.of());
    }

    static BlueprintFloorLayout build(Village village) {
        if (village == null) {
            return empty();
        }

        Map<Integer, Building> rootsByStructure = new HashMap<>();
        Map<Integer, List<Building>> roomsByStructure = new HashMap<>();

        for (Building building : village.getBuildings().values()) {
            if (!building.isComplete() || building.getBuildingType().grouped()) {
                continue;
            }
            int structureId = building.getEffectiveStructureId();
            if (building.isStructureRoot()) {
                rootsByStructure.merge(structureId, building,
                        (first, second) -> first.getId() <= second.getId() ? first : second);
            }
            if (building.isFunctionalRoom()) {
                roomsByStructure.computeIfAbsent(structureId, ignored -> new ArrayList<>()).add(building);
            }
        }

        Map<Integer, Integer> ordinalByBuilding = new HashMap<>();
        Map<Integer, List<Integer>> ordinalsByStructure = new HashMap<>();
        TreeSet<Integer> availableOrdinals = new TreeSet<>();

        for (Map.Entry<Integer, List<Building>> entry : roomsByStructure.entrySet()) {
            int structureId = entry.getKey();
            Building root = rootsByStructure.get(structureId);
            if (root == null) {
                continue;
            }

            List<Integer> floorYs = entry.getValue().stream()
                    .map(Building::getFloorY)
                    .distinct()
                    .sorted()
                    .toList();
            int groundIndex = Collections.binarySearch(floorYs, root.getGroundFloorY());
            if (groundIndex < 0) {
                // Do not invent or tolerance-cluster a missing Ground Floor on the client.
                continue;
            }

            List<Integer> structureOrdinals = new ArrayList<>(floorYs.size());
            for (int index = 0; index < floorYs.size(); index++) {
                structureOrdinals.add(index - groundIndex);
            }
            ordinalsByStructure.put(structureId, List.copyOf(structureOrdinals));

            for (Building room : entry.getValue()) {
                int floorIndex = Collections.binarySearch(floorYs, room.getFloorY());
                if (floorIndex < 0) {
                    continue;
                }
                int ordinal = floorIndex - groundIndex;
                ordinalByBuilding.put(room.getId(), ordinal);
                availableOrdinals.add(ordinal);
            }
        }

        return new BlueprintFloorLayout(
                Map.copyOf(ordinalByBuilding),
                Map.copyOf(ordinalsByStructure),
                List.copyOf(availableOrdinals)
        );
    }

    List<Integer> ordinals() {
        return ordinals;
    }

    List<Integer> ordinalsFor(Building building) {
        return ordinalsByStructureId.getOrDefault(building.getEffectiveStructureId(), List.of());
    }

    boolean isBuildingOnFloor(Building building, int floorOrdinal) {
        Integer ordinal = ordinalByBuildingId.get(building.getId());
        return ordinal != null && ordinal == floorOrdinal;
    }

    boolean isBuildingVisible(Building building, Integer selectedFloor) {
        if (building.getBuildingType().grouped()) {
            return selectedFloor == null || selectedFloor == 0;
        }
        if (!building.isFunctionalRoom()) {
            return false;
        }

        Integer ordinal = ordinalByBuildingId.get(building.getId());
        return ordinal != null && (selectedFloor == null || ordinal.equals(selectedFloor));
    }

    OptionalInt floorOrdinalFor(Building building) {
        Integer ordinal = ordinalByBuildingId.get(building.getId());
        return ordinal == null ? OptionalInt.empty() : OptionalInt.of(ordinal);
    }

}
