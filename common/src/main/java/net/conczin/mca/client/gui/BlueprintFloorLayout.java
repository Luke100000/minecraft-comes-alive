package net.conczin.mca.client.gui;

import net.conczin.mca.MCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.ExternalBuilding;
import net.conczin.mca.server.world.data.Structure;
import net.conczin.mca.server.world.data.StructureFloor;
import net.conczin.mca.server.world.data.Village;

import java.util.*;
import java.util.stream.IntStream;

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
        if (village == null) return empty();
        Map<Integer, Integer> byBuilding = new HashMap<>();
        Map<Long, Integer> byFloor = new HashMap<>();
        Map<Integer, List<Integer>> byStructure = new HashMap<>();
        TreeSet<Integer> available = new TreeSet<>();

        for (Structure structure : village.getStructures().values()) {
            Building root = village.getBuilding(structure.getRootRoomId()).orElse(null);
            if (root == null) continue;
            List<StructureFloor> floors = structure.getFloors();
            int groundIndex = IntStream.range(0, floors.size()).filter(i -> floors.get(i).id() == root.getFloorId()).findFirst().orElse(-1);
            if (groundIndex < 0) continue;

            // Keep an ordinal mapping for every physical Floor so Structure geometry can still
            // resolve unregistered storeys. The Blueprint floor selector itself remains HEAD-like:
            // Ground is always visible, while other Floors appear only after a Room is registered.
            for (int i = 0; i < floors.size(); i++) {
                StructureFloor floor = floors.get(i);
                byFloor.put(floorKey(structure.getId(), floor.id()), i - groundIndex);
            }
            MCA.LOGGER.info("[FloorDebug][Blueprint] structureId={} rootRoomId={} rootFloorId={} groundIndex={} floors={}",
                    structure.getId(), root.getId(), root.getFloorId(), groundIndex,
                    java.util.stream.IntStream.range(0, floors.size())
                            .mapToObj(i -> floors.get(i).id() + "@" + floors.get(i).anchorY()
                                    + "=>" + (i - groundIndex)).toList());

            TreeSet<Integer> registeredOrdinals = new TreeSet<>();
            registeredOrdinals.add(0);
            village.getRooms().filter(room -> room.getStructureId() == structure.getId()).forEach(room -> {
                Integer ordinal = byFloor.get(floorKey(structure.getId(), room.getFloorId()));
                if (ordinal != null) {
                    byBuilding.put(room.getId(), ordinal);
                    registeredOrdinals.add(ordinal);
                }
            });
            byStructure.put(structure.getId(), List.copyOf(registeredOrdinals));
            available.addAll(registeredOrdinals);
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
