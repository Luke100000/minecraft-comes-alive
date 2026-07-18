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
        changed |= canonicalizeRoomFloors(village);
        return changed;
    }

    private static boolean canonicalizeRoomFloors(Village village) {
        boolean changed = false;
        for (Building room : village.getBuildings().values()) {
            if (!room.isFunctionalRoom() || !room.hasStructure()) {
                continue;
            }

            Building structureRoot = root(village, room.getEffectiveStructureId()).orElse(null);
            if (structureRoot == null) {
                continue;
            }

            int previousFloorY = room.getFloorY();
            List<BuildingFloorRegion> previousRegions = room.getFloorRegions();
            room.canonicalizeFloor(structureRoot);
            if (room.getFloorY() != previousFloorY || !room.getFloorRegions().equals(previousRegions)) {
                changed = true;
            }
        }
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

            if (room.getFloorY() == floor.semanticY()
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

    static boolean isGroundFloor(Village village, Building room) {
        if (room == null) {
            return false;
        }
        return root(village, room.getEffectiveStructureId())
                .map(structureRoot -> isGroundFloor(structureRoot, room.getFloorY()))
                .orElse(false);
    }

    static boolean isGroundFloor(Building structureRoot, int floorY) {
        return structureRoot != null && floorY == structureRoot.getGroundFloorY();
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
        BuildingRoomIdentity.MatchResult match = BuildingRoomIdentity.matchExistingRoom(
                building, scan.village(), preferredBuildingId);
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

}
