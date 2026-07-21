package net.conczin.mca.server.world.data;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Matches freshly detected storeys back onto immutable persistent Floor IDs. */
final class StructureFloorMatcher {
    private StructureFloorMatcher() {
    }

    static Optional<Result> match(List<StructureFloor> existingFloors,
                                  int nextFloorId,
                                  List<StructureFloor> detected,
                                  Collection<Building> rooms,
                                  int updatingRoomId) {
        Map<Integer, StructureFloor> assigned = new HashMap<>();
        Set<Integer> usedDetected = new HashSet<>();
        int candidateNextFloorId = nextFloorId;
        int updatingFloorId = rooms.stream()
                .filter(room -> room.getId() == updatingRoomId)
                .mapToInt(Building::getFloorId)
                .findFirst().orElse(-1);

        for (StructureFloor oldFloor : existingFloors) {
            List<Building> anchors = rooms.stream()
                    .filter(room -> room.getId() != updatingRoomId)
                    .filter(room -> room.getFloorId() == oldFloor.id())
                    .toList();
            boolean updatingFallback = anchors.isEmpty() && oldFloor.id() == updatingFloorId;
            int match = bestMatch(oldFloor, anchors, detected, usedDetected, updatingFallback);
            if (match < 0) {
                if (!anchors.isEmpty() || oldFloor.id() == updatingFloorId) {
                    return Optional.empty();
                }
                continue;
            }
            StructureFloor geometry = detected.get(match);
            assigned.put(oldFloor.id(), oldFloor.withGeometry(
                    geometry.anchorY(), geometry.ceilingY(), geometry.region()));
            usedDetected.add(match);
        }

        for (int i = 0; i < detected.size(); i++) {
            if (usedDetected.contains(i)) continue;
            StructureFloor geometry = detected.get(i);
            int floorId = candidateNextFloorId++;
            assigned.put(floorId, new StructureFloor(floorId,
                    geometry.anchorY(), geometry.ceilingY(), geometry.region()));
        }

        for (Building room : rooms) {
            if (room.getId() == updatingRoomId) continue;
            StructureFloor floor = assigned.get(room.getFloorId());
            if (floor == null || !roomFootprintInside(room, floor)) {
                return Optional.empty();
            }
        }
        return Optional.of(new Result(Map.copyOf(assigned), candidateNextFloorId));
    }

    private static int bestMatch(StructureFloor oldFloor,
                                 List<Building> anchors,
                                 List<StructureFloor> detected,
                                 Set<Integer> used,
                                 boolean updatingFallback) {
        int best = -1;
        long bestScore = Long.MIN_VALUE;
        for (int i = 0; i < detected.size(); i++) {
            if (used.contains(i)) continue;
            StructureFloor candidate = detected.get(i);
            int heightDelta = Math.abs(oldFloor.anchorY() - candidate.anchorY());
            if (updatingFallback && heightDelta > BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE) {
                continue;
            }
            if (anchors.stream().anyMatch(room -> !roomFootprintInside(room, candidate))) {
                continue;
            }
            long overlap = oldFloor.region() == null || candidate.region() == null
                    ? 0L : oldFloor.region().intersectionArea(candidate.region());
            if (anchors.isEmpty() && !updatingFallback && overlap == 0L) {
                continue;
            }
            long score = anchors.size() * 1_000_000L + overlap * 100L - heightDelta;
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private static boolean roomFootprintInside(Building room, StructureFloor floor) {
        if (room.getFloorRegions().isEmpty() || floor.region() == null) {
            return floor.contains(room.getSourceBlock().getX(), room.getSourceBlock().getZ());
        }
        BuildingFloorRegion region = room.getFloorRegions().getFirst();
        return region.intersectionArea(floor.region()) == region.area();
    }

    record Result(Map<Integer, StructureFloor> floors, int nextFloorId) {
    }
}
