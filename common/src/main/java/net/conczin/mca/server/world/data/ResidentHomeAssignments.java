package net.conczin.mca.server.world.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class ResidentHomeAssignments {
    private ResidentHomeAssignments() {
    }

    static int deduplicate(Map<UUID, Long> homes) {
        Map<Long, UUID> owners = new HashMap<>();
        homes.forEach((resident, home) -> owners.merge(home, resident, ResidentHomeAssignments::earlier));

        int originalSize = homes.size();
        homes.entrySet().removeIf(entry -> !entry.getKey().equals(owners.get(entry.getValue())));
        return originalSize - homes.size();
    }

    static boolean claim(Map<UUID, Long> homes, UUID resident, long home) {
        homes.remove(resident);

        boolean alreadyClaimed = homes.values().stream().anyMatch(existingHome -> existingHome == home);
        if (alreadyClaimed) {
            return false;
        }

        homes.put(resident, home);
        return true;
    }

    static void claimAuthoritatively(Map<UUID, Long> homes, UUID resident, long home) {
        homes.remove(resident);
        homes.entrySet().removeIf(entry -> entry.getValue() == home);
        homes.put(resident, home);
    }

    private static UUID earlier(UUID first, UUID second) {
        return first.compareTo(second) <= 0 ? first : second;
    }
}
