package net.conczin.mca.server.world.data;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Maintains one deterministic saved Main Room for one explicit logical building. */
final class MainRoomSelector {
    private MainRoomSelector() {
    }

    static boolean ensureValid(List<Structure> structures, Collection<Building> rooms) {
        Map<Integer, Structure> structuresById = structuresById(structures);
        Map<Integer, Building> eligible = eligibleRooms(structuresById, rooms);
        Selection manual = manualSelection(structures, eligible);
        return applyIfChanged(structures, manual != null
                ? manual : automaticSelection(structuresById, eligible.values()));
    }

    private static Selection manualSelection(List<Structure> structures,
                                             Map<Integer, Building> eligible) {
        if (structures == null || structures.isEmpty()) return null;
        return structures.stream()
                .filter(structure -> !structure.isMainRoomAutomatic())
                .map(structure -> {
                    Building room = eligible.get(structure.getMainRoomId());
                    return room == null ? null : selection(room, false);
                })
                .filter(java.util.Objects::nonNull)
                .min(Comparator.comparingInt(Selection::structureId)
                        .thenComparingInt(Selection::roomId))
                .orElse(null);
    }

    static boolean setManual(List<Structure> structures,
                             Collection<Building> rooms,
                             int roomId) {
        Building room = eligibleRooms(structuresById(structures), rooms).get(roomId);
        return room != null && applyIfChanged(structures, selection(room, false));
    }

    static boolean useAutomatic(List<Structure> structures, Collection<Building> rooms) {
        Map<Integer, Structure> structuresById = structuresById(structures);
        return applyIfChanged(structures,
                automaticSelection(structuresById, eligibleRooms(structuresById, rooms).values()));
    }

    static void replace(List<Structure> structures, Building replacement) {
        if (replacement == null || structures.stream()
                .noneMatch(structure -> structure.getId() == replacement.getStructureId())) {
            return;
        }
        Structure holder = structures.stream()
                .filter(structure -> structure.getMainRoomId() >= 0)
                .findFirst()
                .orElse(null);
        if (holder == null) return;
        apply(structures, new Selection(
                replacement.getId(), replacement.getStructureId(), holder.isMainRoomAutomatic()));
    }

    private static Selection automaticSelection(Map<Integer, Structure> structuresById,
                                                 Collection<Building> eligibleRooms) {
        return eligibleRooms.stream()
                .map(room -> candidate(room, structuresById))
                .filter(java.util.Objects::nonNull)
                .min(Comparator.comparingInt((Candidate candidate) ->
                                Math.abs(candidate.floor().floorNumber()))
                        .thenComparing(Comparator.comparingInt((Candidate candidate) ->
                                candidate.floor().floorNumber()).reversed())
                        .thenComparing(Comparator.comparingInt((Candidate candidate) ->
                                candidate.floor().anchorY()).reversed())
                        .thenComparingInt(candidate -> candidate.room().getId()))
                .map(candidate -> selection(candidate.room(), true))
                .orElse(null);
    }

    private static Candidate candidate(Building room, Map<Integer, Structure> structures) {
        Structure structure = structures.get(room.getStructureId());
        if (structure == null) return null;
        return structure.getFloor(room.getFloorId())
                .map(floor -> new Candidate(room, floor))
                .orElse(null);
    }

    private static Map<Integer, Structure> structuresById(List<Structure> structures) {
        if (structures == null || structures.isEmpty()) return Map.of();
        Map<Integer, Structure> indexed = new HashMap<>();
        structures.forEach(structure -> indexed.put(structure.getId(), structure));
        return indexed;
    }

    private static Map<Integer, Building> eligibleRooms(Map<Integer, Structure> structuresById,
                                                         Collection<Building> rooms) {
        if (structuresById.isEmpty() || rooms == null) return Map.of();
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
        return new Selection(room.getId(), room.getStructureId(), automatic);
    }

    private static boolean applyIfChanged(List<Structure> structures, Selection selection) {
        if (isNormalized(structures, selection)) return false;
        apply(structures, selection);
        return true;
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
        structures.forEach(Structure::clearMainRoom);
        if (selection == null) return;
        structures.stream()
                .filter(structure -> structure.getId() == selection.structureId())
                .findFirst()
                .ifPresent(holder -> {
                    if (selection.automatic()) holder.setAutomaticMainRoom(selection.roomId());
                    else holder.setManualMainRoom(selection.roomId());
                });
    }

    private record Candidate(Building room, StructureFloor floor) {
    }

    private record Selection(int roomId, int structureId, boolean automatic) {
    }
}
