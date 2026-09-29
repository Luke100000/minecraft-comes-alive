package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.List;

/** Detached, fully analyzed replacement for one selected registered Room. */
public record RegisteredRoomUpdate(
        Building.validationResult result,
        BlockPos source,
        Village village,
        Structure refreshedStructure,
        int structureId,
        int floorId,
        int expectedRoomId,
        Building replacementRoom,
        List<String> matchingTypes
) {
    public RegisteredRoomUpdate {
        matchingTypes = matchingTypes == null ? List.of() : List.copyOf(matchingTypes);
    }

    static RegisteredRoomUpdate failure(Building.validationResult result,
                                        BlockPos source,
                                        Village village) {
        return new RegisteredRoomUpdate(result, source, village, null, -1, -1, -1,
                null, List.of());
    }

    public boolean isAmbiguous() {
        return RoomTypeResolver.requiresTypeChoice(matchingTypes);
    }

    public boolean requiresTypeSelection() {
        return result == Building.validationResult.SUCCESS
                && replacementRoom != null
                && !replacementRoom.isTypeForced()
                && isAmbiguous();
    }

    public boolean matchesType(String type) {
        return RoomTypeResolver.matchesTypeChoice(matchingTypes, type);
    }
}
