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

        Building updatingRoom = rooms.stream()
                .filter(room -> room.getId() == updatingRoomId)
                .findFirst().orElse(null);
        int updatingFloorId = updatingRoom == null ? -1 : updatingRoom.getFloorId();

        for (StructureFloor oldFloor : existingFloors) {
            List<Building> floorRooms = rooms.stream()
                    .filter(room -> room.getId() != updatingRoomId)
                    .filter(room -> room.getFloorId() == oldFloor.id())
                    .toList();
            int match = bestFloorMatch(oldFloor, floorRooms, detected, usedDetected);
            if (match < 0 && oldFloor.id() == updatingFloorId) {
                match = bestUpdatingFloorMatch(oldFloor, detected, usedDetected);
            }
            if (match < 0) {
                if (!floorRooms.isEmpty()) {
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

        if (updatingFloorId >= 0 && !assigned.containsKey(updatingFloorId)) {
            return Optional.empty();
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

    private static int bestFloorMatch(StructureFloor oldFloor,
                                      List<Building> rooms,
                                      List<StructureFloor> detected,
                                      Set<Integer> used) {
        int best = -1;
        long bestScore = Long.MIN_VALUE;
        for (int i = 0; i < detected.size(); i++) {
            if (used.contains(i)) continue;
            StructureFloor candidate = detected.get(i);
            long roomScore = 0L;
            for (Building room : rooms) {
                if (!roomFootprintInside(room, candidate)) {
                    roomScore = Long.MIN_VALUE / 4;
                    break;
                }
                roomScore += 1_000_000L;
            }
            if (roomScore < 0) continue;
            long overlap = oldFloor.region() == null || candidate.region() == null
                    ? 0L : oldFloor.region().intersectionArea(candidate.region());
            if (rooms.isEmpty() && overlap == 0L) continue;
            long score = roomScore + overlap * 100L - Math.abs(oldFloor.anchorY() - candidate.anchorY());
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private static int bestUpdatingFloorMatch(StructureFloor oldFloor,
                                              List<StructureFloor> detected,
                                              Set<Integer> used) {
        int best = -1;
        long bestScore = Long.MIN_VALUE;
        for (int i = 0; i < detected.size(); i++) {
            if (used.contains(i)) continue;
            StructureFloor candidate = detected.get(i);
            int heightDelta = Math.abs(oldFloor.anchorY() - candidate.anchorY());
            if (heightDelta > BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE) continue;
            long overlap = oldFloor.region() == null || candidate.region() == null
                    ? 0L : oldFloor.region().intersectionArea(candidate.region());
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

    record Result(Map<Integer, StructureFloor> floors, int nextFloorId) {
    }
}
