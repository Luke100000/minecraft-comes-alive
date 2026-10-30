package net.conczin.mca.server.world.data;

import net.conczin.mca.resources.data.BuildingType;

import java.util.List;

/** Detached inheritance mutation plus the direct Room types eligible after the change. */
public record RoomInheritanceUpdate(
        int roomId,
        boolean mainRoom,
        boolean previousEnabled,
        boolean enabled,
        List<String> matchingTypes
) {
    public RoomInheritanceUpdate {
        matchingTypes = matchingTypes == null ? List.of() : List.copyOf(matchingTypes);
    }

    static RoomInheritanceUpdate invalid(boolean enabled) {
        return new RoomInheritanceUpdate(-1, false, false, enabled, List.of());
    }

    static RoomInheritanceUpdate analyze(Village village, Building room, boolean enabled) {
        if (village == null || room == null || !room.isFunctionalRoom()
                || village.getBuilding(room.getId()).orElse(null) != room) {
            return invalid(enabled);
        }
        boolean mainRoom = village.isMainRoom(room);
        boolean previousEnabled = mainRoom
                ? village.isBuildingInheritanceEnabled(room)
                : room.contributesToMain();
        List<String> matchingTypes = enabled ? List.of() : village.getMatchingRoomTypes(room).stream()
                .map(BuildingType::name)
                .toList();
        return new RoomInheritanceUpdate(
                room.getId(), mainRoom, previousEnabled, enabled, matchingTypes);
    }

    public boolean valid() {
        return roomId >= 0;
    }

    public boolean requiresTypeSelection() {
        return valid() && previousEnabled && !enabled
                && RoomTypeResolver.requiresTypeChoice(matchingTypes);
    }

    public boolean matchesType(String type) {
        return RoomTypeResolver.matchesTypeChoice(matchingTypes, type);
    }
}
