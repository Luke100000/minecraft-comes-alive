package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Matches one complete registered-Floor partition back to stable Room identities. */
final class RegisteredRoomReconciler {
    private RegisteredRoomReconciler() {
    }

    static Optional<Result> reconcile(BlockPos playerPos,
                                      int expectedPlayerRoomId,
                                      Collection<Building> previousRooms,
                                      Collection<Building> scannedComponents) {
        List<Building> previous = previousRooms.stream()
                .sorted(Comparator.comparingInt(Building::getId))
                .toList();
        List<Building> components = scannedComponents.stream()
                .sorted(COMPONENT_ORDER)
                .toList();
        Building expected = previous.stream()
                .filter(room -> room.getId() == expectedPlayerRoomId)
                .findFirst().orElse(null);
        if (expected == null || components.isEmpty()) return Optional.empty();

        Building playerComponent = components.stream()
                .filter(component -> component.containsFloorPosition(playerPos))
                .findFirst()
                .orElseGet(() -> components.stream()
                        .filter(component -> component.getFloorFootprintIntersectionArea(expected) > 0)
                        .sorted(Comparator
                                .comparingLong((Building component) ->
                                        component.getFloorFootprintIntersectionArea(expected))
                                .reversed()
                                .thenComparing(COMPONENT_ORDER))
                        .findFirst()
                        .orElse(null));
        if (playerComponent == null) return Optional.empty();

        List<Assignment> assignments = new ArrayList<>();
        Set<Integer> claimedRoomIds = new HashSet<>();
        Set<Building> claimedComponents = java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>());
        assignments.add(new Assignment(playerComponent, expected));
        claimedRoomIds.add(expected.getId());
        claimedComponents.add(playerComponent);

        List<Overlap> overlaps = new ArrayList<>();
        for (Building component : components) {
            if (claimedComponents.contains(component)) continue;
            for (Building room : previous) {
                if (claimedRoomIds.contains(room.getId())) continue;
                long area = component.getFloorFootprintIntersectionArea(room);
                if (area > 0) overlaps.add(new Overlap(component, room, area));
            }
        }
        overlaps.sort(Comparator
                .comparingLong(Overlap::area).reversed()
                .thenComparingInt(overlap -> overlap.room().getId())
                .thenComparing(overlap -> overlap.component(), COMPONENT_ORDER));
        for (Overlap overlap : overlaps) {
            if (claimedComponents.contains(overlap.component())
                    || claimedRoomIds.contains(overlap.room().getId())) continue;
            assignments.add(new Assignment(overlap.component(), overlap.room()));
            claimedComponents.add(overlap.component());
            claimedRoomIds.add(overlap.room().getId());
        }
        for (Building component : components) {
            if (!claimedComponents.contains(component)) {
                assignments.add(new Assignment(component, null));
            }
        }
        assignments.sort(Comparator.comparing(Assignment::component, COMPONENT_ORDER));
        return Optional.of(new Result(assignments));
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

    record Result(List<Assignment> assignments) {
        Result {
            assignments = List.copyOf(assignments);
        }
    }

    private record Overlap(Building component, Building room, long area) {
    }
}
