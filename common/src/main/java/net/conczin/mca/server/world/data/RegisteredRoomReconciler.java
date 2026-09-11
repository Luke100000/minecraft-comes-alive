package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Matches a caller-provided Room lineage back to stable Room identities. */
final class RegisteredRoomReconciler {
    private RegisteredRoomReconciler() {
    }

    static Optional<Result> reconcile(BlockPos playerPos,
                                      int expectedPlayerRoomId,
                                      int mainRoomId,
                                      Collection<Building> previousRooms,
                                      Collection<Building> scannedComponents,
                                      FloorGeometry floor) {
        if (floor == null) return Optional.empty();
        List<Building> previous = previousRooms.stream()
                .sorted(Comparator.comparingInt(Building::getId))
                .toList();
        List<Building> components = scannedComponents.stream()
                .sorted(COMPONENT_ORDER)
                .toList();
        List<Integer> roomIds = previous.stream().map(Building::getId).toList();
        int expectedRoom = roomIds.indexOf(expectedPlayerRoomId);
        if (expectedRoom < 0 || components.isEmpty()) return Optional.empty();

        long[][] overlaps = new long[components.size()][previous.size()];
        for (int component = 0; component < components.size(); component++) {
            for (int room = 0; room < previous.size(); room++) {
                overlaps[component][room] = floor.roomIdentityOverlapCount(
                        components.get(component).getFloorCells(), previous.get(room).getFloorCells());
            }
        }

        int playerComponent = -1;
        for (int component = 0; component < components.size(); component++) {
            if (components.get(component).containsFloorPosition(playerPos)) {
                playerComponent = component;
                break;
            }
        }
        if (playerComponent < 0) {
            long bestOverlap = 0;
            for (int component = 0; component < components.size(); component++) {
                if (overlaps[component][expectedRoom] > bestOverlap) {
                    bestOverlap = overlaps[component][expectedRoom];
                    playerComponent = component;
                }
            }
        }
        if (playerComponent < 0) return Optional.empty();

        int[] owners = RoomIdentityPolicy.assign(
                roomIds, expectedPlayerRoomId, mainRoomId, playerComponent, overlaps);
        Map<Integer, Building> previousById = previous.stream().collect(
                java.util.stream.Collectors.toMap(Building::getId, room -> room));
        List<Assignment> assignments = new ArrayList<>(components.size());
        for (int component = 0; component < components.size(); component++) {
            assignments.add(new Assignment(
                    components.get(component), previousById.get(owners[component])));
        }

        return Optional.of(new Result(roomIds, assignments, components.get(playerComponent)));
    }

    static Optional<List<Building>> updateLineage(Building selected,
                                                  Collection<Building> freshComponents,
                                                  Collection<Building> otherRooms,
                                                  FloorGeometry floor) {
        if (selected == null || freshComponents == null || otherRooms == null || floor == null) {
            return Optional.empty();
        }
        List<Building> lineage = freshComponents.stream()
                .filter(component -> hasIdentityOverlap(component, selected, floor))
                .sorted(COMPONENT_ORDER)
                .toList();
        if (lineage.isEmpty()) return Optional.empty();
        for (Building component : lineage) {
            for (Building other : otherRooms) {
                if (hasIdentityOverlap(component, other, floor)) return Optional.empty();
            }
        }
        return Optional.of(lineage);
    }

    /**
     * Reconciles a complete fresh Floor partition for Add Room. Boundary connector cells are
     * deliberately excluded from persistence identity because boundary ownership is topology,
     * not stable Room identity.
     */
    static Optional<List<Building>> reconcileAddition(Collection<Building> previousRooms,
                                                       Collection<Building> scannedComponents,
                                                       Building addedRoom,
                                                       FloorGeometry floor) {
        if (previousRooms == null || scannedComponents == null || addedRoom == null || floor == null) {
            return Optional.empty();
        }
        List<Building> previous = previousRooms.stream()
                .sorted(Comparator.comparingInt(Building::getId))
                .toList();
        List<Building> components = scannedComponents.stream()
                .sorted(COMPONENT_ORDER)
                .toList();
        Building addedComponent = null;
        for (Building component : components) {
            if (!component.getFloorCells().equals(addedRoom.getFloorCells())) continue;
            if (addedComponent != null) return Optional.empty();
            addedComponent = component;
        }
        if (addedComponent == null) return Optional.empty();

        Building selectedAddedComponent = addedComponent;
        List<Building> addedMatches = previous.stream()
                .filter(room -> hasIdentityOverlap(selectedAddedComponent, room, floor))
                .toList();
        if (addedMatches.size() > 1) return Optional.empty();

        List<Building> replacements = new ArrayList<>(previous.size());
        Set<Integer> matchedRoomIds = new HashSet<>();
        for (Building component : components) {
            if (component == addedComponent) continue;
            List<Building> matches = previous.stream()
                    .filter(room -> hasIdentityOverlap(component, room, floor))
                    .toList();
            if (matches.isEmpty()) continue;
            if (matches.size() > 1) return Optional.empty();

            Building previousRoom = matches.getFirst();
            if (!matchedRoomIds.add(previousRoom.getId())) return Optional.empty();
            preserveIdentity(component, previousRoom);
            replacements.add(component);
        }
        if (matchedRoomIds.size() != previous.size()) return Optional.empty();
        return Optional.of(List.copyOf(replacements));
    }

    static void preserveIdentity(Building component, Building previous) {
        component.setId(previous.getId());
        component.setType(previous.getType());
        component.setTypeForced(previous.isTypeForced());
        component.setContributesToMain(previous.contributesToMain());
    }

    static boolean hasIdentityOverlap(Building component,
                                      Building previous,
                                      FloorGeometry floor) {
        return floor.roomIdentityOverlapCount(
                component.getFloorCells(), previous.getFloorCells()) > 0;
    }

    private static final Comparator<Building> COMPONENT_ORDER = Comparator
            .comparingInt((Building room) -> room.getRawPos0().getX())
            .thenComparingInt(room -> room.getRawPos0().getZ())
            .thenComparingInt(room -> room.getRawPos0().getY())
            .thenComparingInt(room -> room.getRawPos1().getX())
            .thenComparingInt(room -> room.getRawPos1().getZ())
            .thenComparingInt(room -> room.getRawPos1().getY())
            .thenComparing((first, second) -> RoomPartitioner.compareFloorCellSets(
                    first.getFloorCells(), second.getFloorCells()));

    record Assignment(Building component, Building previous) {
        int roomId() {
            return previous == null ? -1 : previous.getId();
        }

        boolean createsRoom() {
            return previous == null;
        }
    }

    record Result(List<Integer> previousRoomIds,
                  List<Assignment> assignments,
                  Building playerComponent) {
        Result {
            previousRoomIds = List.copyOf(previousRoomIds);
            assignments = List.copyOf(assignments);
        }
    }
}
