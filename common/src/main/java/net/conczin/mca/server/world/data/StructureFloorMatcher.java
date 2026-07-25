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
        Set<Integer> usedDetected = new HashSet<>();
        Set<Integer> requiredFloorIds = rooms.stream()
                .map(Building::getFloorId)
                .collect(java.util.stream.Collectors.toSet());
        int candidateNextFloorId = nextFloorId;

        List<StructureFloor> requiredFloors = existingFloors.stream()
                .filter(floor -> requiredFloorIds.contains(floor.id()))
                .sorted(Comparator
                        .comparingInt((StructureFloor floor) ->
                                candidateMatches(floor, detected, rooms).size())
                        .thenComparingInt(StructureFloor::id))
                .toList();
        Map<Integer, Integer> requiredMatches = new HashMap<>();
        if (!assignRequired(0, requiredFloors, detected, rooms,
                usedDetected, requiredMatches)) {
            return Optional.empty();
        }
        for (StructureFloor oldFloor : requiredFloors) {
            int match = requiredMatches.get(oldFloor.id());
            assign(oldFloor, detected.get(match), assigned);
        }

        for (StructureFloor oldFloor : existingFloors) {
            if (requiredFloorIds.contains(oldFloor.id())) continue;
            int match = bestMatch(oldFloor, detected, usedDetected, List.of());
            if (match < 0) {
                continue;
            }
            StructureFloor geometry = detected.get(match);
            assign(oldFloor, geometry, assigned);
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
            StructureFloor floor = assigned.get(room.getFloorId());
            if (floor == null || !roomFootprintInside(room, floor)) return Optional.empty();
        }
        return Optional.of(new Result(Map.copyOf(assigned), candidateNextFloorId));
    }

    private static boolean assignRequired(int index,
                                          List<StructureFloor> required,
                                          List<StructureFloor> detected,
                                          Collection<Building> rooms,
                                          Set<Integer> used,
                                          Map<Integer, Integer> matches) {
        if (index >= required.size()) return true;
        StructureFloor floor = required.get(index);
        for (int candidate : candidateMatches(floor, detected, rooms)) {
            if (!used.add(candidate)) continue;
            matches.put(floor.id(), candidate);
            if (assignRequired(index + 1, required, detected, rooms, used, matches)) return true;
            matches.remove(floor.id());
            used.remove(candidate);
        }
        return false;
    }

    private static List<Integer> candidateMatches(StructureFloor oldFloor,
                                                  List<StructureFloor> detected,
                                                  Collection<Building> rooms) {
        List<Building> floorRooms = rooms.stream()
                .filter(room -> room.getFloorId() == oldFloor.id())
                .toList();
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < detected.size(); i++) {
            StructureFloor candidate = detected.get(i);
            if (matchScore(oldFloor, candidate) == Long.MIN_VALUE) continue;
            if (floorRooms.stream().allMatch(room -> roomFootprintInside(room, candidate))) {
                candidates.add(i);
            }
        }
        candidates.sort(Comparator
                .comparingLong((Integer candidate) ->
                        matchScore(oldFloor, detected.get(candidate))).reversed()
                .thenComparingInt(Integer::intValue));
        return List.copyOf(candidates);
    }

    private static int bestMatch(StructureFloor oldFloor,
                                 List<StructureFloor> detected,
                                 Set<Integer> used,
                                 Collection<Building> rooms) {
        int best = -1;
        long bestScore = Long.MIN_VALUE;
        for (int i = 0; i < detected.size(); i++) {
            if (used.contains(i)) continue;
            StructureFloor candidate = detected.get(i);
            if (rooms.stream()
                    .filter(room -> room.getFloorId() == oldFloor.id())
                    .anyMatch(room -> !roomFootprintInside(room, candidate))) continue;
            long score = matchScore(oldFloor, candidate);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private static long matchScore(StructureFloor oldFloor, StructureFloor candidate) {
        int heightDelta = Math.abs(oldFloor.anchorY() - candidate.anchorY());
        long overlap = oldFloor.region() == null || candidate.region() == null
                ? 0L : oldFloor.region().intersectionArea(candidate.region());
        if (overlap == 0L && heightDelta > BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE) {
            return Long.MIN_VALUE;
        }
        return overlap * 100L - heightDelta;
    }

    private static void assign(StructureFloor oldFloor,
                               StructureFloor geometry,
                               Map<Integer, StructureFloor> assigned) {
        assigned.put(oldFloor.id(), oldFloor.withGeometry(
                geometry.anchorY(), geometry.ceilingY(), geometry.region()));
    }

    private static boolean roomFootprintInside(Building room, StructureFloor floor) {
        if (room.getFloorRegions().isEmpty() || floor.region() == null) {
            return floor.contains(room.getSourceBlock().getX(), room.getSourceBlock().getZ());
        }
        BuildingFloorRegion region = room.getFloorRegions().getFirst();
        return region.intersectionArea(floor.region()) == region.area();
    }

    record Result(Map<Integer, StructureFloor> floors,
                  int nextFloorId) {
    }
}
