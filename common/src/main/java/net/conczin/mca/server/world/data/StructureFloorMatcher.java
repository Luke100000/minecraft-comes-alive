package net.conczin.mca.server.world.data;

import java.util.*;

/** Matches freshly detected storeys back onto immutable persistent Floor IDs. */
final class StructureFloorMatcher {
    private StructureFloorMatcher() {
    }

    static Optional<Result> match(List<StructureFloor> existingFloors,
                                  int nextFloorId,
                                  List<StructureFloor> detected,
                                  Collection<Building> rooms) {
        Map<Integer, StructureFloor> assigned = new HashMap<>();
        Map<Integer, Integer> persistentIdByDetectedId = new HashMap<>();
        Set<Integer> usedDetected = new HashSet<>();
        Set<Integer> requiredFloorIds = rooms.stream()
                .map(Building::getFloorId)
                .collect(java.util.stream.Collectors.toSet());
        int candidateNextFloorId = nextFloorId;

        for (StructureFloor oldFloor : existingFloors) {
            int match = bestMatch(oldFloor, detected, usedDetected);
            if (match < 0) {
                if (requiredFloorIds.contains(oldFloor.id())) return Optional.empty();
                continue;
            }
            StructureFloor geometry = detected.get(match);
            assigned.put(oldFloor.id(), oldFloor.withGeometry(
                    geometry.anchorY(), geometry.ceilingY(), geometry.region()));
            persistentIdByDetectedId.put(geometry.id(), oldFloor.id());
            usedDetected.add(match);
        }

        for (int i = 0; i < detected.size(); i++) {
            if (usedDetected.contains(i)) continue;
            StructureFloor geometry = detected.get(i);
            int floorId = candidateNextFloorId++;
            assigned.put(floorId, new StructureFloor(floorId,
                    geometry.anchorY(), geometry.ceilingY(), geometry.region()));
            persistentIdByDetectedId.put(geometry.id(), floorId);
        }
        for (Building room : rooms) {
            StructureFloor floor = assigned.get(room.getFloorId());
            if (floor == null || !roomFootprintInside(room, floor)) return Optional.empty();
        }
        return Optional.of(new Result(Map.copyOf(assigned), candidateNextFloorId,
                Map.copyOf(persistentIdByDetectedId)));
    }

    private static int bestMatch(StructureFloor oldFloor,
                                 List<StructureFloor> detected,
                                 Set<Integer> used) {
        int best = -1;
        long bestScore = Long.MIN_VALUE;
        for (int i = 0; i < detected.size(); i++) {
            if (used.contains(i)) continue;
            StructureFloor candidate = detected.get(i);
            int heightDelta = Math.abs(oldFloor.anchorY() - candidate.anchorY());
            long overlap = oldFloor.region() == null || candidate.region() == null
                    ? 0L : oldFloor.region().intersectionArea(candidate.region());
            if (overlap == 0L && heightDelta > BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE) {
                continue;
            }
            long score = overlap * 100L - heightDelta;
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

    record Result(Map<Integer, StructureFloor> floors,
                  int nextFloorId,
                  Map<Integer, Integer> persistentIdByDetectedId) {
    }
}
