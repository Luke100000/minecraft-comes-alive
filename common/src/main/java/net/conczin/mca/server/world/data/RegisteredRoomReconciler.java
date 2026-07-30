package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Matches one complete registered-Floor partition back to stable Room identities. */
final class RegisteredRoomReconciler {
    private RegisteredRoomReconciler() {
    }

    static Optional<Result> reconcile(BlockPos playerPos,
                                      int expectedPlayerRoomId,
                                      int mainRoomId,
                                      Collection<Building> previousRooms,
                                      Collection<Building> scannedComponents) {
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
                overlaps[component][room] = components.get(component)
                        .getFloorFootprintIntersectionArea(previous.get(room));
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

    private static final Comparator<Building> COMPONENT_ORDER = Comparator
            .comparingInt((Building room) -> room.getRawPos0().getX())
            .thenComparingInt(room -> room.getRawPos0().getZ())
            .thenComparingInt(room -> room.getRawPos1().getX())
            .thenComparingInt(room -> room.getRawPos1().getZ());

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
