package net.conczin.mca.server.world.data;

import net.conczin.mca.MCA;

import java.util.*;

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
            } else {
                Building root = chooseCanonicalRoot(component);
                int structureId = root.getId();
                for (Building room : component) {
                    room.setStructureId(structureId);
                    room.setStructureRoot(room.getId() == root.getId());
                }

                if (adjacentStructures.size() > 1) {
                    MCA.LOGGER.warn(
                            "[BuildingStructures] Legacy room component {} touched multiple structures {}; kept as its own structure {}",
                            component.stream().map(Building::getId).toList(), adjacentStructures, structureId);
                }
            }
            changed = true;
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

    static boolean hasValidRoot(Village village, int structureId) {
        return village != null && village.getBuildings().values().stream()
                .filter(building -> !building.getBuildingType().grouped())
                .anyMatch(building -> building.getStructureId() == structureId && building.isStructureRoot());
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

    static int memberCount(Village village, int structureId) {
        return members(village, structureId).size();
    }

    static void removeStructure(Village village, int structureId) {
        if (village == null) {
            return;
        }
        List<Integer> ids = members(village, structureId).stream().map(Building::getId).toList();
        ids.forEach(village::removeBuilding);
    }

    /**
     * Finds whether a freshly scanned shape is an existing room.
     *
     * <p>Split rule: when a new room is wholly inside an old room but does not
     * contain that old room's persistent source anchor, it is the new side of a
     * split and never inherits the old room identity.</p>
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

            Candidate candidate = scoreCandidate(scanned, existing, preferredBuildingId);
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
                .anyMatch(existing -> scanned.getIntersectionVolume(existing) > 0L);
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

    private static Candidate scoreCandidate(Building scanned, Building existing, int preferredBuildingId) {
        if (scanned.isStrictScan() && !scanned.sharesFloorBandWith(existing)) {
            return null;
        }

        long intersection = scanned.getIntersectionVolume(existing);
        if (intersection <= 0L) {
            return null;
        }

        boolean sourceAnchor = scanned.containsRawPos(existing.getSourceBlock());
        boolean scannedInsideExisting = existing.containsRawBounds(scanned);
        boolean preferred = existing.getId() == preferredBuildingId;

        double retainedOld = intersection / (double) Math.max(1L, existing.getRawVolume());
        double coveredNew = intersection / (double) Math.max(1L, scanned.getRawVolume());

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
                && scanned.getRawVolume() >= Math.round(existing.getRawVolume() * 0.75D));

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

        int floorDistance = Math.abs(scanned.getFloorY() - existing.getFloorY());
        if (floorDistance <= 2) {
            score += 300 - floorDistance * 100;
        }

        double centerDistance = scanned.getCenter().distSqr(existing.getCenter());
        if (centerDistance <= 16.0D) {
            score += 200;
        } else if (centerDistance <= 64.0D) {
            score += 100;
        }

        return new Candidate(existing, score, sourceAnchor);
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
        for (Building existing : village.getBuildings().values()) {
            if (!existing.isComplete()
                    || existing.getBuildingType().grouped()
                    || !existing.hasStructure()
                    || !hasValidRoot(village, existing.getStructureId())) {
                continue;
            }

            if (room.isStructurallyAttachedTo(existing, ROOM_ATTACHMENT_VERTICAL_GAP)) {
                candidateStructures.add(existing.getStructureId());
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
