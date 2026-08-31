package net.conczin.mca.server.world.data;

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
        matchingTypes = List.copyOf(matchingTypes);
    }

    static RoomInheritanceUpdate invalid(boolean enabled) {
        return new RoomInheritanceUpdate(-1, false, false, enabled, List.of());
    }

    public boolean valid() {
        return roomId >= 0;
    }

    public boolean requiresTypeSelection() {
        return valid() && previousEnabled && !enabled && matchingTypes.size() > 1;
    }

    public boolean matchesType(String type) {
        return matchingTypes.contains(type);
    }
}
