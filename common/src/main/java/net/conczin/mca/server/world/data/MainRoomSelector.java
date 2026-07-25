package net.conczin.mca.server.world.data;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Maintains one deterministic, saved Main Room for a logical Building. */
final class MainRoomSelector {
    private MainRoomSelector() {
    }

    static Selection resolve(List<Structure> structures, Collection<Building> rooms) {
        if (structures == null || structures.isEmpty()) return null;
        Map<Integer, Building> eligible = eligibleRooms(structures, rooms);
        List<Selection> saved = structures.stream()
                .map(structure -> {
                    Building room = eligible.get(structure.getMainRoomId());
                    return room == null ? null : selection(room, structure.isMainRoomAutomatic());
                })
                .filter(Objects::nonNull)
                .toList();
        if (saved.size() == 1) return saved.getFirst();

        Selection manual = saved.stream()
                .filter(selection -> !selection.automatic())
                .min(Comparator.comparingInt(Selection::structureId)
                        .thenComparingInt(Selection::roomId))
                .orElse(null);
        return manual != null ? manual : automaticSelection(eligible.values());
    }

    static boolean ensureValid(List<Structure> structures, Collection<Building> rooms) {
        Selection selection = resolve(structures, rooms);
        if (isNormalized(structures, selection)) return false;
        apply(structures, selection);
        return true;
    }

    static boolean setManual(List<Structure> structures,
                             Collection<Building> rooms,
                             int roomId) {
        Building room = eligibleRooms(structures, rooms).get(roomId);
        if (room == null) return false;
        Selection selection = selection(room, false);
        if (isNormalized(structures, selection)) return false;
        apply(structures, selection);
        return true;
    }

    static boolean useAutomatic(List<Structure> structures, Collection<Building> rooms) {
        Selection selection = automaticSelection(eligibleRooms(structures, rooms).values());
        if (selection == null || isNormalized(structures, selection)) return false;
        apply(structures, selection);
        return true;
    }

    static void transfer(List<Structure> structures,
                         int removedRoomId,
                         int survivorRoomId,
                         int survivorStructureId) {
        Structure holder = structures.stream()
                .filter(structure -> structure.getMainRoomId() == removedRoomId)
                .findFirst()
                .orElse(null);
        if (holder == null) return;
        boolean automatic = holder.isMainRoomAutomatic();
        apply(structures, new Selection(
                survivorRoomId, survivorStructureId, -1, automatic));
    }

    private static Selection automaticSelection(Collection<Building> eligibleRooms) {
        return eligibleRooms.stream()
                .min(Comparator.comparingInt(Building::getId))
                .map(room -> selection(room, true))
                .orElse(null);
    }

    private static Map<Integer, Building> eligibleRooms(List<Structure> structures,
                                                        Collection<Building> rooms) {
        if (structures == null || rooms == null) return Map.of();
        Map<Integer, Structure> structuresById = new HashMap<>();
        structures.forEach(structure -> structuresById.put(structure.getId(), structure));
        Map<Integer, Building> eligible = new HashMap<>();
        for (Building room : rooms) {
            Structure structure = structuresById.get(room.getStructureId());
            if (room.getId() >= 0 && room.isFunctionalRoom() && structure != null
                    && structure.getFloor(room.getFloorId()).isPresent()) {
                eligible.put(room.getId(), room);
            }
        }
        return eligible;
    }

    private static Selection selection(Building room, boolean automatic) {
        return new Selection(room.getId(), room.getStructureId(), room.getFloorId(), automatic);
    }

    private static boolean isNormalized(List<Structure> structures, Selection selection) {
        for (Structure structure : structures) {
            boolean holder = selection != null && structure.getId() == selection.structureId();
            if (holder) {
                if (structure.getMainRoomId() != selection.roomId()
                        || structure.isMainRoomAutomatic() != selection.automatic()) return false;
            } else if (structure.getMainRoomId() >= 0 || !structure.isMainRoomAutomatic()) {
                return false;
            }
        }
        return true;
    }

    private static void apply(List<Structure> structures, Selection selection) {
        Structure holder = selection == null ? null : structures.stream()
                .filter(structure -> structure.getId() == selection.structureId())
                .findFirst()
                .orElse(null);
        if (selection != null && holder == null) return;
        for (Structure structure : structures) {
            structure.clearMainRoom();
        }
        if (holder == null) return;
        if (selection.automatic()) holder.setAutomaticMainRoom(selection.roomId());
        else holder.setManualMainRoom(selection.roomId());
    }

    record Selection(int roomId, int structureId, int floorId, boolean automatic) {
    }
}
