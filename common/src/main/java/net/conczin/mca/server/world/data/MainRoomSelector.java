package net.conczin.mca.server.world.data;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;

/** Deterministic Main Room selection for one physical Structure. */
final class MainRoomSelector {
    private MainRoomSelector() {
    }

    static OptionalInt select(Structure structure, Collection<Building> rooms) {
        if (structure == null || rooms == null) return OptionalInt.empty();

        List<Building> eligible = rooms.stream()
                .filter(Building::isFunctionalRoom)
                .filter(room -> room.getStructureId() == structure.getId())
                .filter(room -> structure.getFloor(room.getFloorId()).isPresent())
                .sorted(Comparator.comparingInt(Building::getId))
                .toList();
        if (eligible.isEmpty()) return OptionalInt.empty();

        int preferredFloorId = structure.getAutomaticGroundFloorId();
        int automaticFloorId = preferredFloorId;
        boolean preferredFloorHasRoom = eligible.stream()
                .anyMatch(room -> room.getFloorId() == automaticFloorId);
        if (!preferredFloorHasRoom) {
            preferredFloorId = eligible.stream()
                    .map(room -> structure.getFloor(room.getFloorId()).orElseThrow())
                    .distinct()
                    .min(Comparator
                            .comparingInt((StructureFloor floor) ->
                                    Math.abs(floor.anchorY() - structure.getGroundReferenceY()))
                            .thenComparingInt(StructureFloor::anchorY)
                            .thenComparingInt(StructureFloor::id))
                    .orElseThrow()
                    .id();
        }

        int selectedFloorId = preferredFloorId;
        return eligible.stream()
                .filter(room -> room.getFloorId() == selectedFloorId)
                .mapToInt(Building::getId)
                .min();
    }

    static boolean ensureValid(Structure structure, Collection<Building> rooms) {
        if (structure == null) return false;
        if (isValid(structure, rooms, structure.getMainRoomId())) return false;

        int replacement = select(structure, rooms).orElse(-1);
        boolean changed = structure.getMainRoomId() != replacement
                || !structure.isMainRoomAutomatic();
        structure.setAutomaticMainRoom(replacement);
        return changed;
    }

    static boolean isValid(Structure structure, Collection<Building> rooms, int roomId) {
        return structure != null && rooms != null && rooms.stream()
                .anyMatch(room -> room.getId() == roomId
                        && room.isFunctionalRoom()
                        && room.getStructureId() == structure.getId()
                        && structure.getFloor(room.getFloorId()).isPresent());
    }
}
