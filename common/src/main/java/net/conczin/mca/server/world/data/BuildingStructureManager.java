package net.conczin.mca.server.world.data;

import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.stream.Stream;

/**
 * Owns persistent Building -> Rooms hierarchy rules.
 *
 * <p>Geometry is only used to discover a relationship or migrate legacy saves.
 * Once assigned, {@code structureId} is authoritative.</p>
 */
final class BuildingStructureManager {
    static final int ROOM_ATTACHMENT_VERTICAL_GAP = 2;

    private static final double SAME_ROOM_RETAINED_OVERLAP = 0.80D;

    private BuildingStructureManager() {
    }

    static boolean ensureHierarchy(Village village) {
        if (village == null) {
            return false;
        }

        boolean changed = false;

        List<Building> structural = village.getBuildings().values().stream()
                .filter(building -> !building.getBuildingType().grouped())
                .sorted(Comparator.comparingInt(Building::getId))
                .toList();

        if (structural.isEmpty()) {
            return changed;
        }

        // Existing whole-building scans are safest legacy roots. Never merge two
        // non-strict buildings merely because their bounding boxes touch.
        for (Building building : structural) {
            if (!building.hasStructure() && !building.isStrictScan()) {
                building.setStructureId(building.getId());
                building.setStructureRoot(true);
                changed = true;
            }
        }

        List<Building> unassignedRooms = structural.stream()
                .filter(building -> !building.hasStructure())
                .toList();

        for (List<Building> component : getAttachmentComponents(unassignedRooms)) {
            Set<Integer> adjacentStructures = new TreeSet<>();

            for (Building room : component) {
                for (Building assigned : structural) {
                    if (!assigned.hasStructure() || component.contains(assigned)) {
                        continue;
                    }
                    if (room.isStructurallyAttachedTo(assigned, ROOM_ATTACHMENT_VERTICAL_GAP)
                            && hasValidRoot(village, assigned.getStructureId())) {
                        adjacentStructures.add(assigned.getStructureId());
                    }
                }
            }

            if (adjacentStructures.size() == 1) {
                int structureId = adjacentStructures.iterator().next();
                for (Building room : component) {
                    room.setStructureId(structureId);
                    room.setStructureRoot(false);
                }
                changed = true;
            } else if (adjacentStructures.size() > 1) {
                // Never guess between multiple parent structures, and never manufacture
                // a strict functional room into a standalone structure root.
                MCA.LOGGER.warn(
                        "[BuildingStructures] Unassigned room component {} touches multiple structures {}; left unassigned",
                        component.stream().map(Building::getId).toList(), adjacentStructures);
            }
        }

        changed |= repairRootInvariants(village);
        return changed;
    }

    private static boolean repairRootInvariants(Village village) {
        boolean changed = false;
        Map<Integer, List<Building>> byStructure = new HashMap<>();

        village.getBuildings().values().stream()
                .filter(building -> !building.getBuildingType().grouped())
                .filter(Building::hasStructure)
                .forEach(building -> byStructure
                        .computeIfAbsent(building.getStructureId(), ignored -> new ArrayList<>())
                        .add(building));

        for (Map.Entry<Integer, List<Building>> entry : byStructure.entrySet()) {
            List<Building> members = entry.getValue();
            List<Building> roots = members.stream().filter(Building::isStructureRoot).toList();

            if (roots.size() == 1) {
                continue;
            }

            Building canonical = roots.isEmpty()
                    ? chooseCanonicalRoot(members)
                    : chooseCanonicalRoot(roots);

            for (Building member : members) {
                boolean shouldBeRoot = member.getId() == canonical.getId();
                if (member.isStructureRoot() != shouldBeRoot) {
                    member.setStructureRoot(shouldBeRoot);
                    changed = true;
                }
            }

            MCA.LOGGER.warn(
                    "[BuildingStructures] Repaired structure {} root invariant: roots={} canonicalRoot={}",
                    entry.getKey(), roots.stream().map(Building::getId).toList(), canonical.getId());
        }

        return changed;
    }

    private static Building chooseCanonicalRoot(List<Building> buildings) {
        return buildings.stream()
                .min(Comparator.comparing(Building::isStrictScan)
                        .thenComparingInt(Building::getFloorY)
                        .thenComparingInt(Building::getId))
                .orElseThrow();
    }

    private static Stream<Building> roots(Village village) {
        if (village == null) {
            return Stream.empty();
        }
        return village.getBuildings().values().stream()
                .filter(building -> !building.getBuildingType().grouped())
                .filter(Building::isStructureRoot)
                .filter(Building::isComplete);
    }

    static Optional<Building> root(Village village, int structureId) {
        return roots(village)
                .filter(building -> building.getEffectiveStructureId() == structureId)
                .min(Comparator.comparingInt(Building::getId));
    }

    static Optional<Building> containingRoot(Village village, Vec3i pos) {
        return roots(village)
                .filter(root -> root.containsStructurePosition(pos))
                .min(Comparator.comparingInt(Building::getId));
    }

    static Optional<Building> containingRawRoot(Village village, Vec3i pos) {
        return roots(village)
                .filter(root -> root.containsRawPos(pos))
                .min(Comparator.comparingInt(Building::getId));
    }

    static boolean hasValidRoot(Village village, int structureId) {
        return root(village, structureId).isPresent();
    }

    static List<Building> members(Village village, int structureId) {
        if (village == null) {
            return List.of();
        }
        return village.getBuildings().values().stream()
                .filter(building -> !building.getBuildingType().grouped())
                .filter(building -> building.getEffectiveStructureId() == structureId)
                .sorted(Comparator.comparingInt(Building::getId))
                .toList();
    }

    static Optional<Building> functionalRoomAt(Village village, Level world, BlockPos pos) {
        if (village == null || world == null) {
            return functionalRoomAt(village, pos);
        }

        Map<Integer, BuildingFloorResolver.ResolvedFloor> floorsByStructure = new HashMap<>();
        Set<Integer> unresolvedStructures = new HashSet<>();
        Building best = null;

        for (Building room : village.getBuildings().values()) {
            if (!room.isComplete() || !room.isFunctionalRoom()
                    || !room.containsFloorColumn(pos.getX(), pos.getZ())) {
                continue;
            }

            int structureId = room.getEffectiveStructureId();
            Building structureRoot = root(village, structureId).orElse(null);
            if (structureRoot == null || unresolvedStructures.contains(structureId)) {
                continue;
            }

            BuildingFloorResolver.ResolvedFloor floor = floorsByStructure.get(structureId);
            if (floor == null) {
                floor = BuildingFloorResolver.resolve(world, pos, structureRoot).orElse(null);
                if (floor == null) {
                    unresolvedStructures.add(structureId);
                    continue;
                }
                floorsByStructure.put(structureId, floor);
            }

            if (canonicalFloorY(structureRoot, room.getFloorY()) == floor.semanticY()
                    && (best == null || room.getId() < best.getId())) {
                best = room;
            }
        }

        if (best != null) {
            return Optional.of(best);
        }

        // Legacy strict rooms without a valid root have no canonical floor owner yet.
        // Keep the old raw-Y lookup only for those migration cases; rooted rooms must
        // never bypass the canonical resolver after it rejected their floor.
        return village.getBuildings().values().stream()
                .filter(Building::isComplete)
                .filter(Building::isFunctionalRoom)
                .filter(room -> !hasValidRoot(village, room.getEffectiveStructureId()))
                .filter(room -> room.containsRawPos(pos))
                .filter(room -> room.getFloorDistanceTo(pos) <= Building.SEMANTIC_FLOOR_TOLERANCE)
                .min(Comparator.comparingInt((Building room) -> room.getFloorDistanceTo(pos))
                        .thenComparingInt(Building::getId));
    }

    static Optional<Building> functionalRoomAt(Village village, Vec3i pos) {
        if (village == null) {
            return Optional.empty();
        }
        return village.getBuildings().values().stream()
                .filter(Building::isComplete)
                .filter(Building::isFunctionalRoom)
                .filter(building -> building.containsRawPos(pos))
                .filter(building -> building.getFloorDistanceTo(pos) <= Building.SEMANTIC_FLOOR_TOLERANCE)
                .min(Comparator.comparingInt((Building building) -> building.getFloorDistanceTo(pos))
                        .thenComparingInt(Building::getId));
    }

    static int canonicalFloorY(Village village, Building room) {
        if (room == null) {
            return Integer.MIN_VALUE;
        }
        return canonicalFloorY(village, room.getEffectiveStructureId(), room.getFloorY());
    }

    private static int canonicalFloorY(Village village, int structureId, int floorY) {
        return root(village, structureId)
                .map(structureRoot -> canonicalFloorY(structureRoot, floorY))
                .orElse(floorY);
    }

    private static int canonicalFloorY(Building structureRoot, int floorY) {
        return structureRoot.resolveFloorBand(floorY)
                .map(Building.FloorBand::anchorY)
                .orElse(floorY);
    }

    static boolean isGroundFloor(Village village, Building room) {
        Building structureRoot = room == null
                ? null
                : root(village, room.getEffectiveStructureId()).orElse(null);
        return structureRoot != null && isGroundFloor(structureRoot, room.getFloorY());
    }

    static boolean isGroundFloor(Building structureRoot, int floorY) {
        if (structureRoot == null) {
            return false;
        }
        return canonicalFloorY(structureRoot, floorY)
                == canonicalFloorY(structureRoot, structureRoot.getGroundFloorY());
    }

    static void removeStructure(Village village, int structureId) {
        if (village == null) {
            return;
        }
        List<Integer> ids = members(village, structureId).stream().map(Building::getId).toList();
        village.removeBuildings(ids);
    }

    static BuildingScanResult resolveScanIdentity(BuildingScanResult scan,
                                                  int preferredBuildingId,
                                                  boolean assignIfNew) {
        if (scan.result() != Building.validationResult.SUCCESS) {
            return scan;
        }

        Building building = scan.building();
        MatchResult match = matchExistingRoom(building, scan.village(), preferredBuildingId);
        Building.validationResult result = match.result();
        int existingBuildingId = -1;
        List<Integer> mergedBuildingIds = List.of();

        if (result == Building.validationResult.SUCCESS && match.hasMatch()) {
            Building existing = match.primary();
            building.setStructureId(existing.getStructureId());
            building.setStructureRoot(existing.isStructureRoot());
            existingBuildingId = existing.getId();
            mergedBuildingIds = match.mergedBuildingIds();
        } else if (result == Building.validationResult.SUCCESS
                && assignIfNew
                && building.isStrictScan()) {
            result = assignNewRoom(building, scan.village());
        }

        return new BuildingScanResult(
                result,
                scan.source(),
                scan.strictScan(),
                building,
                scan.matchingTypes(),
                scan.village(),
                existingBuildingId,
                mergedBuildingIds
        );
    }

    /**
     * Finds whether a freshly scanned shape is an existing room.
     *
     * <p>Ordinary matching keeps persistent room identity stable. Split identity is
     * resolved separately after all disconnected components have been discovered.</p>
     */
    static MatchResult matchExistingRoom(Building scanned, Village village, int preferredBuildingId) {
        if (village == null) {
            return MatchResult.noMatch();
        }

        ensureHierarchy(village);

        List<Candidate> candidates = new ArrayList<>();
        for (Building existing : village.getBuildings().values()) {
            // Non-strict entries are whole-building aggregates; strict entries are
            // explicit room identities. Never collapse one representation into the
            // other merely because their volumes overlap.
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
        if (!sameCanonicalFloor(village, structureId, expected, retained, added)) {
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

    static boolean sameRoomGeometry(Building first, Building second, Village village) {
        if (first == null || second == null
                || !sameCanonicalFloor(village, first.getEffectiveStructureId(), first, second)) {
            return false;
        }
        long firstArea = first.getFloorFootprintArea();
        long secondArea = second.getFloorFootprintArea();
        return firstArea == secondArea
                && first.getHorizontalFootprintIntersectionArea(second) == firstArea;
    }

    private static boolean sameCanonicalFloor(Village village,
                                              int structureId,
                                              Building... rooms) {
        if (rooms.length == 0) {
            return true;
        }
        int floorY = canonicalFloorY(village, structureId, rooms[0].getFloorY());
        for (int i = 1; i < rooms.length; i++) {
            if (canonicalFloorY(village, structureId, rooms[i].getFloorY()) != floorY) {
                return false;
            }
        }
        return true;
    }

    private static Candidate scoreCandidate(Building scanned,
                                            Building existing,
                                            int preferredBuildingId,
                                            Village village) {
        boolean preferred = existing.getId() == preferredBuildingId;
        boolean exactRoomTopology = scanned.isStrictScan() && existing.isStrictScan();
        boolean sameSemanticFloor = !exactRoomTopology
                || sharesSemanticFloor(scanned, existing, village);
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

        // Deterministic split handling: manual scans give the old identity only to
        // the side containing the old source anchor. An explicit refresh of a known
        // room may recover without the anchor, but only with strong retained overlap.
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
        if (existing.getId() == preferredBuildingId) {
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

    private static boolean sharesSemanticFloor(Building scanned, Building existing, Village village) {
        int structureId = existing.getEffectiveStructureId();
        return canonicalFloorY(village, structureId, scanned.getFloorY())
                == canonicalFloorY(village, structureId, existing.getFloorY());
    }

    private static boolean roomsOverlapOnSemanticFloor(Building first, Building second, Village village) {
        return sharesSemanticFloor(first, second, village)
                && first.getHorizontalFootprintIntersectionArea(second) > 0L;
    }

    /**
     * Assigns a genuinely new room to exactly one rooted structure.
     */
    static Building.validationResult assignNewRoom(Building room, Village village) {
        if (village == null) {
            return Building.validationResult.NOT_IN_BUILDING;
        }

        ensureHierarchy(village);

        Set<Integer> candidateStructures = new TreeSet<>();
        for (Building root : roots(village).filter(Building::hasStructure).toList()) {
            if (root.containsRawPos(room.getSourceBlock())
                    || room.isStructurallyAttachedTo(root, ROOM_ATTACHMENT_VERTICAL_GAP)) {
                candidateStructures.add(root.getStructureId());
            }
        }

        if (candidateStructures.isEmpty()) {
            return Building.validationResult.NOT_IN_BUILDING;
        }
        if (candidateStructures.size() > 1) {
            return Building.validationResult.AMBIGUOUS_STRUCTURE;
        }

        room.setStructureId(candidateStructures.iterator().next());
        room.setStructureRoot(false);
        return Building.validationResult.SUCCESS;
    }

    private static List<List<Building>> getAttachmentComponents(List<Building> buildings) {
        List<List<Building>> components = new ArrayList<>();
        Set<Integer> assigned = new HashSet<>();

        for (Building start : buildings) {
            if (!assigned.add(start.getId())) {
                continue;
            }

            List<Building> component = new ArrayList<>();
            ArrayDeque<Building> queue = new ArrayDeque<>();
            queue.add(start);

            while (!queue.isEmpty()) {
                Building current = queue.removeFirst();
                component.add(current);

                for (Building candidate : buildings) {
                    if (!assigned.contains(candidate.getId())
                            && current.isStructurallyAttachedTo(candidate, ROOM_ATTACHMENT_VERTICAL_GAP)) {
                        assigned.add(candidate.getId());
                        queue.addLast(candidate);
                    }
                }
            }

            components.add(component);
        }

        return components;
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
