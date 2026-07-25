package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Set;

/** Detached, fully analyzed update for one registered Structure Floor. */
public record RegisteredRoomUpdate(
        Building.validationResult result,
        BlockPos source,
        Village village,
        int structureId,
        int floorId,
        int expectedPlayerRoomId,
        List<Integer> previousRoomIds,
        List<RegisteredRoomReconciler.Assignment> assignments,
        List<String> playerMatchingTypes
) {
    public RegisteredRoomUpdate {
        previousRoomIds = List.copyOf(previousRoomIds);
        assignments = List.copyOf(assignments);
        playerMatchingTypes = List.copyOf(playerMatchingTypes);
    }

    static RegisteredRoomUpdate failure(Building.validationResult result,
                                        BlockPos source,
                                        Village village) {
        return new RegisteredRoomUpdate(result, source, village, -1, -1, -1,
                List.of(), List.of(), List.of());
    }

    public boolean isAmbiguous() {
        return playerMatchingTypes.size() > 1;
    }

    public boolean matchesType(String type) {
        return playerMatchingTypes.contains(type);
    }

    Set<Integer> removedRoomIds() {
        Set<Integer> assigned = assignments.stream()
                .map(RegisteredRoomReconciler.Assignment::roomId)
                .filter(id -> id >= 0)
                .collect(java.util.stream.Collectors.toSet());
        return previousRoomIds.stream()
                .filter(id -> !assigned.contains(id))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    Building playerComponent() {
        return assignments.stream()
                .filter(assignment -> assignment.roomId() == expectedPlayerRoomId)
                .map(RegisteredRoomReconciler.Assignment::component)
                .findFirst().orElse(null);
    }
}
