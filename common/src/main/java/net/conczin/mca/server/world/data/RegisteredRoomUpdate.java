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
        Set<Integer> removedRoomIds,
        Building playerComponent,
        List<String> playerMatchingTypes
) {
    public RegisteredRoomUpdate {
        previousRoomIds = List.copyOf(previousRoomIds);
        assignments = List.copyOf(assignments);
        removedRoomIds = Set.copyOf(removedRoomIds);
        playerMatchingTypes = List.copyOf(playerMatchingTypes);
    }

    static RegisteredRoomUpdate failure(Building.validationResult result,
                                        BlockPos source,
                                        Village village) {
        return new RegisteredRoomUpdate(result, source, village, -1, -1, -1,
                List.of(), List.of(), Set.of(), null, List.of());
    }

    public boolean isAmbiguous() {
        return playerMatchingTypes.size() > 1;
    }

    public boolean matchesType(String type) {
        return playerMatchingTypes.contains(type);
    }
}
