package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Matches one complete registered-Floor partition back to stable Room identities. */
final class RegisteredRoomReconciler {
    private static final long ASSIGNMENT_INFINITY = Long.MAX_VALUE / 4L;

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

        // Stable Room identity follows topology, not the player. On a split, the old Room ID
        // therefore stays with the greatest-overlap parent component while playerComponent
        // remains available solely for the interaction/type-selection path.
        List<Assignment> assignments = new ArrayList<>(matchRemaining(components, previous));
        assignments.sort(Comparator.comparing(Assignment::component, COMPONENT_ORDER));
        Set<Integer> assignedRoomIds = assignments.stream()
                .map(Assignment::roomId)
                .filter(id -> id >= 0)
                .collect(java.util.stream.Collectors.toSet());
        Set<Integer> removedRoomIds = previous.stream()
                .map(Building::getId)
                .filter(id -> !assignedRoomIds.contains(id))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return Optional.of(new Result(
                previous.stream().map(Building::getId).toList(),
                assignments, removedRoomIds, playerComponent));
    }

    /**
     * Preserves the greatest number of Room IDs, then the greatest total footprint overlap.
     * Dummy columns let every component remain assignable when no previous Room overlaps it.
     */
    private static List<Assignment> matchRemaining(List<Building> components, List<Building> rooms) {
        if (components.isEmpty()) return List.of();

        int roomColumns = rooms.size();
        long totalArea = 0L;
        for (Building component : components) {
            long area = Math.max(0L, component.getFloorFootprintArea());
            totalArea = area > ASSIGNMENT_INFINITY - totalArea
                    ? ASSIGNMENT_INFINITY : totalArea + area;
        }
        long roomReuseBonus = totalArea + 1L;
        long[][] scores = new long[components.size()][roomColumns + components.size()];
        for (int componentIndex = 0; componentIndex < components.size(); componentIndex++) {
            Building component = components.get(componentIndex);
            for (int roomIndex = 0; roomIndex < roomColumns; roomIndex++) {
                long overlap = component.getFloorFootprintIntersectionArea(rooms.get(roomIndex));
                if (overlap > 0L) {
                    scores[componentIndex][roomIndex] = roomReuseBonus + overlap;
                }
            }
        }

        int[] assignedColumns = maximumWeightAssignment(scores);
        List<Assignment> assignments = new ArrayList<>(components.size());
        for (int componentIndex = 0; componentIndex < components.size(); componentIndex++) {
            int roomIndex = assignedColumns[componentIndex];
            Building room = roomIndex >= 0
                    && roomIndex < roomColumns
                    && scores[componentIndex][roomIndex] > 0L
                    ? rooms.get(roomIndex) : null;
            assignments.add(new Assignment(components.get(componentIndex), room));
        }
        return List.copyOf(assignments);
    }

    /** Rectangular Hungarian assignment; rows never outnumber columns because each has a dummy. */
    private static int[] maximumWeightAssignment(long[][] scores) {
        int rowCount = scores.length;
        int columnCount = scores[0].length;
        long maximumScore = 0L;
        for (long[] row : scores) {
            for (long score : row) {
                maximumScore = Math.max(maximumScore, score);
            }
        }
        long[] rowPotential = new long[rowCount + 1];
        long[] columnPotential = new long[columnCount + 1];
        int[] matchedRow = new int[columnCount + 1];
        int[] previousColumn = new int[columnCount + 1];

        for (int row = 1; row <= rowCount; row++) {
            matchedRow[0] = row;
            long[] minimum = new long[columnCount + 1];
            Arrays.fill(minimum, ASSIGNMENT_INFINITY);
            boolean[] used = new boolean[columnCount + 1];
            int column = 0;
            do {
                used[column] = true;
                int currentRow = matchedRow[column];
                long delta = ASSIGNMENT_INFINITY;
                int nextColumn = 0;
                for (int candidate = 1; candidate <= columnCount; candidate++) {
                    if (used[candidate]) continue;
                    long cost = maximumScore - scores[currentRow - 1][candidate - 1]
                            - rowPotential[currentRow] - columnPotential[candidate];
                    if (cost < minimum[candidate]) {
                        minimum[candidate] = cost;
                        previousColumn[candidate] = column;
                    }
                    if (minimum[candidate] < delta) {
                        delta = minimum[candidate];
                        nextColumn = candidate;
                    }
                }
                for (int candidate = 0; candidate <= columnCount; candidate++) {
                    if (used[candidate]) {
                        rowPotential[matchedRow[candidate]] += delta;
                        columnPotential[candidate] -= delta;
                    } else {
                        minimum[candidate] -= delta;
                    }
                }
                column = nextColumn;
            } while (matchedRow[column] != 0);

            do {
                int nextColumn = previousColumn[column];
                matchedRow[column] = matchedRow[nextColumn];
                column = nextColumn;
            } while (column != 0);
        }

        int[] assignedColumn = new int[rowCount];
        Arrays.fill(assignedColumn, -1);
        for (int column = 1; column <= columnCount; column++) {
            if (matchedRow[column] != 0) {
                assignedColumn[matchedRow[column] - 1] = column - 1;
            }
        }
        return assignedColumn;
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
                  Set<Integer> removedRoomIds,
                  Building playerComponent) {
        Result {
            previousRoomIds = List.copyOf(previousRoomIds);
            assignments = List.copyOf(assignments);
            removedRoomIds = Set.copyOf(removedRoomIds);
        }
    }
}
