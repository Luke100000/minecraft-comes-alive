package net.conczin.mca.server.world.data;

import java.util.*;

/**
 * Owns persistent room identity decisions.
 *
 * <p>Room geometry is discovered by {@link BuildingRoomScanner}; structure hierarchy is
 * owned by {@link BuildingStructureManager}. This class is the only place that decides
 * whether newly discovered geometry represents an existing room and which side of a
 * two-way split retains the persistent room ID.</p>
 */
final class BuildingRoomIdentity {
    private static final double SAME_ROOM_RETAINED_OVERLAP = 0.80D;

    private BuildingRoomIdentity() {
    }

    static MatchResult matchExistingRoom(Building scanned, Village village, int preferredBuildingId) {
        if (village == null) {
            return MatchResult.noMatch();
        }

        BuildingStructureManager.ensureHierarchy(village);

        List<Candidate> candidates = new ArrayList<>();
        for (Building existing : village.getBuildings().values()) {
            if (existing.getBuildingType().grouped()
                    || existing.isStrictScan() != scanned.isStrictScan()) {
                continue;
            }

            Candidate candidate = scoreCandidate(scanned, existing, preferredBuildingId, village);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        if (candidates.isEmpty()) {
            return MatchResult.noMatch();
        }

        Set<Integer> structureIds = candidates.stream()
                .map(candidate -> candidate.building().getEffectiveStructureId())
                .collect(TreeSet::new, Set::add, Set::addAll);
        if (structureIds.size() > 1) {
            return MatchResult.failure(Building.validationResult.AMBIGUOUS_STRUCTURE);
        }

        int matchedStructureId = structureIds.iterator().next();
        boolean overlapsAnotherStructure = village.getBuildings().values().stream()
                .filter(existing -> !existing.getBuildingType().grouped())
                .filter(existing -> existing.getEffectiveStructureId() != matchedStructureId)
                .filter(existing -> !existing.isStructureRoot())
                .anyMatch(existing -> scanned.isStrictScan() && existing.isStrictScan()
                        ? roomsOverlapOnSemanticFloor(scanned, existing, village)
                        : scanned.getIntersectionVolume(existing) > 0L);
        if (overlapsAnotherStructure) {
            return MatchResult.failure(Building.validationResult.AMBIGUOUS_STRUCTURE);
        }

        candidates.sort(Comparator
                .comparing((Candidate candidate) -> candidate.building().isStructureRoot()).reversed()
                .thenComparing(candidate -> candidate.building().getId() == preferredBuildingId ? 0 : 1)
                .thenComparing(Candidate::sourceAnchor, Comparator.reverseOrder())
                .thenComparing(Comparator.comparingInt(Candidate::score).reversed())
                .thenComparingInt(candidate -> candidate.building().getId()));

        Building primary = candidates.getFirst().building();
        List<Integer> mergedIds = candidates.stream()
                .map(Candidate::building)
                .map(Building::getId)
                .filter(id -> id != primary.getId())
                .distinct()
                .sorted()
                .toList();

        return MatchResult.match(primary, mergedIds);
    }

    static Building.validationResult validateRoomSplit(Building expected,
                                                       Building retained,
                                                       Building added,
                                                       Village village) {
        if (expected == null || retained == null || added == null) {
            return Building.validationResult.TOO_SMALL;
        }
        if (!expected.isStrictScan() || expected.isStructureRoot()
                || !retained.isStrictScan() || retained.isStructureRoot()
                || !added.isStrictScan() || added.isStructureRoot()) {
            return Building.validationResult.OVERLAP;
        }

        int structureId = expected.getEffectiveStructureId();
        if (retained.getEffectiveStructureId() != structureId
                || added.getEffectiveStructureId() != structureId) {
            return Building.validationResult.AMBIGUOUS_STRUCTURE;
        }
        if (!sameCanonicalFloor(village, expected, retained, added)) {
            return Building.validationResult.OVERLAP;
        }

        long retainedArea = retained.getFloorFootprintArea();
        long addedArea = added.getFloorFootprintArea();
        if (retained.getHorizontalFootprintIntersectionArea(expected) != retainedArea
                || added.getHorizontalFootprintIntersectionArea(expected) != addedArea) {
            return Building.validationResult.OVERLAP;
        }
        if (retained.getHorizontalFootprintIntersectionArea(added) > 0L
                || selectSplitRetainedSide(expected, retained, added).orElse(null) != retained) {
            return Building.validationResult.OVERLAP;
        }

        if (village != null) {
            for (Building room : village.getBuildings().values()) {
                if (room.getId() == expected.getId() || !room.isFunctionalRoom()) {
                    continue;
                }
                if (roomsOverlapOnSemanticFloor(retained, room, village)
                        || roomsOverlapOnSemanticFloor(added, room, village)) {
                    return room.getEffectiveStructureId() == structureId
                            ? Building.validationResult.OVERLAP
                            : Building.validationResult.AMBIGUOUS_STRUCTURE;
                }
            }
        }
        return Building.validationResult.SUCCESS;
    }

    static Optional<Building> selectSplitRetainedSide(Building expected,
                                                       Building first,
                                                       Building second) {
        if (expected == null || first == null || second == null) {
            return Optional.empty();
        }

        boolean firstHasAnchor = first.containsFloorPosition(expected.getSourceBlock());
        boolean secondHasAnchor = second.containsFloorPosition(expected.getSourceBlock());
        if (firstHasAnchor != secondHasAnchor) {
            return Optional.of(firstHasAnchor ? first : second);
        }
        if (firstHasAnchor) {
            return Optional.empty();
        }

        long firstOverlap = first.getHorizontalFootprintIntersectionArea(expected);
        long secondOverlap = second.getHorizontalFootprintIntersectionArea(expected);
        if (firstOverlap != secondOverlap) {
            return Optional.of(firstOverlap > secondOverlap ? first : second);
        }

        double firstDistance = first.getCenter().distSqr(expected.getSourceBlock());
        double secondDistance = second.getCenter().distSqr(expected.getSourceBlock());
        int distanceComparison = Double.compare(firstDistance, secondDistance);
        if (distanceComparison != 0) {
            return Optional.of(distanceComparison < 0 ? first : second);
        }

        Comparator<Building> deterministic = Comparator
                .comparingInt((Building building) -> building.getSourceBlock().getX())
                .thenComparingInt(building -> building.getSourceBlock().getZ())
                .thenComparingInt(building -> building.getSourceBlock().getY());
        return Optional.of(deterministic.compare(first, second) <= 0 ? first : second);
    }

    static boolean sameRoomGeometry(Building first, Building second) {
        return sameRoomGeometry(first, second, null);
    }

    static boolean sameRoomGeometry(Building first, Building second, Building structureRoot) {
        if (first == null || second == null
                || !sameCanonicalFloor(structureRoot, first, second)) {
            return false;
        }
        long firstArea = first.getFloorFootprintArea();
        long secondArea = second.getFloorFootprintArea();
        return firstArea == secondArea
                && first.getHorizontalFootprintIntersectionArea(second) == firstArea;
    }

    private static Candidate scoreCandidate(Building scanned,
                                            Building existing,
                                            int preferredBuildingId,
                                            Village village) {
        boolean preferred = existing.getId() == preferredBuildingId;
        boolean exactRoomTopology = scanned.isStrictScan() && existing.isStrictScan();
        boolean sameSemanticFloor = !exactRoomTopology
                || sameCanonicalFloor(village, scanned, existing);
        if (exactRoomTopology && !preferred && !sameSemanticFloor) {
            return null;
        }

        long intersection = exactRoomTopology && sameSemanticFloor
                ? scanned.getHorizontalFootprintIntersectionArea(existing)
                : exactRoomTopology ? 0L : scanned.getIntersectionVolume(existing);
        if (intersection <= 0L) {
            return null;
        }

        long existingMeasure = exactRoomTopology
                ? existing.getFloorFootprintArea()
                : existing.getRawVolume();
        long scannedMeasure = exactRoomTopology
                ? scanned.getFloorFootprintArea()
                : scanned.getRawVolume();

        boolean sourceAnchor = exactRoomTopology
                ? scanned.containsFloorPosition(existing.getSourceBlock())
                : scanned.containsRawPos(existing.getSourceBlock());
        double retainedOld = intersection / (double) Math.max(1L, existingMeasure);
        double coveredNew = intersection / (double) Math.max(1L, scannedMeasure);
        boolean scannedInsideExisting = exactRoomTopology
                ? coveredNew >= 0.999D
                : existing.containsRawBounds(scanned);

        if (scannedInsideExisting && !sourceAnchor
                && !(preferred && retainedOld >= SAME_ROOM_RETAINED_OVERLAP)) {
            return null;
        }

        boolean strongIdentity = sourceAnchor
                || retainedOld >= SAME_ROOM_RETAINED_OVERLAP
                || (preferred && retainedOld >= 0.65D)
                || (coveredNew >= 0.90D
                && scannedMeasure >= Math.round(existingMeasure * 0.75D));
        if (!strongIdentity) {
            return null;
        }

        int score = 0;
        if (sourceAnchor) {
            score += 10_000;
        }
        if (preferred) {
            score += 2_000;
        }
        score += (int) Math.round(retainedOld * 2_000.0D);
        score += (int) Math.round(coveredNew * 1_000.0D);
        if (sameSemanticFloor) {
            score += 300;
        }

        double centerDistance = scanned.getCenter().distSqr(existing.getCenter());
        if (centerDistance <= 16.0D) {
            score += 200;
        } else if (centerDistance <= 64.0D) {
            score += 100;
        }

        return new Candidate(existing, score, sourceAnchor);
    }

    private static boolean sameCanonicalFloor(Building structureRoot, Building... rooms) {
        if (rooms.length == 0) {
            return true;
        }
        int floorY = BuildingStructureManager.canonicalFloorY(
                structureRoot, rooms[0].getFloorY());
        for (int i = 1; i < rooms.length; i++) {
            if (BuildingStructureManager.canonicalFloorY(
                    structureRoot, rooms[i].getFloorY()) != floorY) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameCanonicalFloor(Village village, Building... rooms) {
        if (rooms.length == 0) {
            return true;
        }

        Building fallbackRoot = Arrays.stream(rooms)
                .filter(Building::hasStructure)
                .map(room -> BuildingStructureManager.root(
                        village, room.getEffectiveStructureId()).orElse(null))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        Integer floorY = null;
        for (Building room : rooms) {
            Building root = room.hasStructure()
                    ? BuildingStructureManager.root(
                    village, room.getEffectiveStructureId()).orElse(fallbackRoot)
                    : fallbackRoot;
            int canonicalY = BuildingStructureManager.canonicalFloorY(root, room.getFloorY());
            if (floorY != null && canonicalY != floorY) {
                return false;
            }
            floorY = canonicalY;
        }
        return true;
    }

    private static boolean roomsOverlapOnSemanticFloor(Building first,
                                                        Building second,
                                                        Village village) {
        return sameCanonicalFloor(village, first, second)
                && first.getHorizontalFootprintIntersectionArea(second) > 0L;
    }

    record MatchResult(Building.validationResult result, Building primary, List<Integer> mergedBuildingIds) {
        static MatchResult noMatch() {
            return new MatchResult(Building.validationResult.SUCCESS, null, List.of());
        }

        static MatchResult match(Building primary, List<Integer> mergedBuildingIds) {
            return new MatchResult(Building.validationResult.SUCCESS, primary, List.copyOf(mergedBuildingIds));
        }

        static MatchResult failure(Building.validationResult result) {
            return new MatchResult(result, null, List.of());
        }

        boolean hasMatch() {
            return primary != null;
        }
    }

    private record Candidate(Building building, int score, boolean sourceAnchor) {
    }
}
