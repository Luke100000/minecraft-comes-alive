package net.conczin.mca.server.world.data;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/** Maintains one deterministic, saved Main Room for one physical Structure. */
final class MainRoomSelector {
    private MainRoomSelector() {
    }

    static Selection resolve(Structure structure, Collection<Building> rooms) {
        if (structure == null) return null;
        Map<Integer, Building> eligible = eligibleRooms(structure, rooms);
        Building saved = eligible.get(structure.getMainRoomId());
        return saved == null
                ? automaticSelection(structure, eligible.values())
                : selection(saved, structure.isMainRoomAutomatic());
    }

    static boolean ensureValid(Structure structure, Collection<Building> rooms) {
        return applyIfChanged(structure, resolve(structure, rooms));
    }

    static boolean setManual(Structure structure, Collection<Building> rooms, int roomId) {
        Building room = eligibleRooms(structure, rooms).get(roomId);
        return room != null && applyIfChanged(structure, selection(room, false));
    }

    static boolean useAutomatic(Structure structure, Collection<Building> rooms) {
        if (structure == null) return false;
        return applyIfChanged(structure,
                automaticSelection(structure, eligibleRooms(structure, rooms).values()));
    }

    static void transfer(Structure structure,
                         int removedRoomId,
                         int survivorRoomId,
                         int survivorStructureId) {
        if (structure == null || structure.getMainRoomId() != removedRoomId) return;
        if (structure.getId() != survivorStructureId) {
            structure.clearMainRoom();
            return;
        }
        boolean automatic = structure.isMainRoomAutomatic();
        apply(structure, new Selection(survivorRoomId, -1, automatic));
    }

    private static Selection automaticSelection(Structure structure,
                                                Collection<Building> eligibleRooms) {
        return eligibleRooms.stream()
                .min(Comparator
                        .comparingInt((Building room) -> surfaceDistance(room, structure))
                        .thenComparing(Comparator.comparingInt(
                                (Building room) -> floorAnchor(room, structure)).reversed())
                        .thenComparingInt(Building::getId))
                .map(room -> selection(room, true))
                .orElse(null);
    }

    private static int surfaceDistance(Building room, Structure structure) {
        int floorY = floorAnchor(room, structure);
        return floorY == Integer.MIN_VALUE
                ? Integer.MAX_VALUE
                : Math.abs(floorY - structure.getSurfaceReferenceY());
    }

    private static int floorAnchor(Building room, Structure structure) {
        return structure.getFloor(room.getFloorId())
                .map(StructureFloor::anchorY)
                .orElse(Integer.MIN_VALUE);
    }

    private static Map<Integer, Building> eligibleRooms(Structure structure,
                                                         Collection<Building> rooms) {
        if (structure == null || rooms == null) return Map.of();
        return rooms.stream()
                .filter(room -> room.getId() >= 0)
                .filter(Building::isFunctionalRoom)
                .filter(room -> room.getStructureId() == structure.getId())
                .filter(room -> structure.getFloor(room.getFloorId()).isPresent())
                .collect(Collectors.toMap(Building::getId, room -> room, (first, ignored) -> first));
    }

    private static Selection selection(Building room, boolean automatic) {
        return new Selection(room.getId(), room.getFloorId(), automatic);
    }

    private static boolean applyIfChanged(Structure structure, Selection selection) {
        if (isCurrent(structure, selection)) return false;
        apply(structure, selection);
        return true;
    }

    private static boolean isCurrent(Structure structure, Selection selection) {
        return selection == null
                ? structure.getMainRoomId() < 0 && structure.isMainRoomAutomatic()
                : structure.getMainRoomId() == selection.roomId()
                && structure.isMainRoomAutomatic() == selection.automatic();
    }

    private static void apply(Structure structure, Selection selection) {
        if (selection == null) {
            structure.clearMainRoom();
        } else if (selection.automatic()) {
            structure.setAutomaticMainRoom(selection.roomId());
        } else {
            structure.setManualMainRoom(selection.roomId());
        }
    }

    record Selection(int roomId, int floorId, boolean automatic) {
    }
}
