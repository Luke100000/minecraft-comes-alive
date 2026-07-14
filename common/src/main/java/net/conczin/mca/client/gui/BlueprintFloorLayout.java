package net.conczin.mca.client.gui;

import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.BuildingFloorRegion;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

import java.util.*;

/**
 * Derives semantic blueprint floors from persistent structure-local floor regions.
 *
 * <p>A connected interior volume may contain several walkable levels. One Building can
 * therefore belong to several semantic floors without creating fake persistent room
 * records. Absolute Y is only a geometric input; floor ordinals are assigned locally
 * inside each persistent {@code structureId}.</p>
 */
final class BlueprintFloorLayout {
    private static final int FLOOR_CLUSTER_TOLERANCE = Building.SEMANTIC_FLOOR_TOLERANCE;

    private final Map<Integer, List<AssignedRegion>> assignedRegions;
    private final List<Integer> ordinals;
    private final List<VerticalStack> stacks;

    private BlueprintFloorLayout(Map<Integer, List<AssignedRegion>> assignedRegions,
                                 List<Integer> ordinals,
                                 List<VerticalStack> stacks) {
        this.assignedRegions = assignedRegions;
        this.ordinals = ordinals;
        this.stacks = stacks;
    }

    static BlueprintFloorLayout empty() {
        return new BlueprintFloorLayout(Map.of(), List.of(), List.of());
    }

    static BlueprintFloorLayout build(Village village) {
        if (village == null) {
            return empty();
        }

        List<Building> structuralBuildings = village.getBuildings().values().stream()
                .filter(Building::isComplete)
                .filter(building -> !building.getBuildingType().grouped())
                .sorted(Comparator.comparingInt(Building::getId))
                .toList();
        if (structuralBuildings.isEmpty()) {
            return empty();
        }

        List<FloorCandidate> allCandidates = structuralBuildings.stream()
                .flatMap(building -> candidatesFor(building).stream())
                .sorted(Comparator.comparingInt(FloorCandidate::anchorY)
                        .thenComparingInt(FloorCandidate::buildingId))
                .toList();
        if (allCandidates.isEmpty()) {
            return empty();
        }

        Map<Integer, Integer> rootGroundAnchors = new HashMap<>();
        for (Building building : structuralBuildings) {
            if (!building.isStructureRoot()) {
                continue;
            }
            rootGroundAnchors.put(building.getEffectiveStructureId(), building.getGroundFloorY());
        }

        // Root regions define the physical vertical stack. Only strict children own
        // functional rooms and therefore receive visible floor assignments.
        List<FloorCandidate> candidates = allCandidates;
        if (candidates.isEmpty()) {
            return empty();
        }

        Map<Integer, List<AssignedRegion>> mutableAssignedRegions = new HashMap<>();
        TreeSet<Integer> availableOrdinals = new TreeSet<>();
        List<VerticalStack> stacks = new ArrayList<>();

        for (Map.Entry<Integer, List<FloorCandidate>> entry : getVerticalStacks(candidates).entrySet()) {
            List<StackFloorLevel> levels = clusterStackFloorLevels(entry.getValue());
            int groundY = rootGroundAnchors.getOrDefault(entry.getKey(), levels.getFirst().anchorY());
            int groundLevelIndex = getClosestStackFloorIndexPreferLower(levels, groundY);

            for (int levelIndex = 0; levelIndex < levels.size(); levelIndex++) {
                StackFloorLevel level = levels.get(levelIndex);
                int ordinal = levelIndex - groundLevelIndex;
                boolean hasRegisteredRoom = level.candidates().stream().anyMatch(FloorCandidate::strictScan);
                if (hasRegisteredRoom) {
                    availableOrdinals.add(ordinal);
                }

                for (FloorCandidate candidate : level.candidates()) {
                    if (!candidate.strictScan()) {
                        continue;
                    }
                    List<AssignedRegion> regions = mutableAssignedRegions
                            .computeIfAbsent(candidate.buildingId(), ignored -> new ArrayList<>());
                    for (RegionBounds bounds : candidate.bounds()) {
                        regions.add(new AssignedRegion(ordinal, candidate.anchorY(), bounds));
                    }
                }
            }

            stacks.add(new VerticalStack(entry.getKey(), levels, groundLevelIndex));
        }

        return new BlueprintFloorLayout(
                freezeRegionMap(mutableAssignedRegions),
                List.copyOf(availableOrdinals),
                List.copyOf(stacks)
        );
    }

    List<Integer> ordinals() {
        return ordinals;
    }

    boolean isBlockOnFloor(Building building, BlockPos blockPos, int floorOrdinal) {
        for (VerticalStack stack : stacks) {
            if (stack.structureId() != building.getEffectiveStructureId()) {
                continue;
            }

            int levelIndex = getClosestStackFloorIndexPreferLower(stack.levels(), blockPos.getY());
            StackFloorLevel level = stack.levels().get(levelIndex);
            return Math.abs(level.anchorY() - blockPos.getY()) <= FLOOR_CLUSTER_TOLERANCE
                    && levelIndex - stack.groundLevelIndex() == floorOrdinal;
        }
        return floorOrdinal == 0;
    }

    boolean isBuildingVisible(Building building, Integer selectedFloor) {
        if (building.getBuildingType().grouped()) {
            return selectedFloor == null || selectedFloor == 0;
        }

        if (!building.isFunctionalRoom()) {
            return false;
        }

        List<AssignedRegion> regions = assignedRegions.get(building.getId());
        return regions != null && !regions.isEmpty()
                && (selectedFloor == null || regions.stream().anyMatch(region -> region.ordinal() == selectedFloor));
    }

    List<RegionBounds> regionsFor(Building building, Integer selectedFloor) {
        if (selectedFloor == null) {
            return List.of(RegionBounds.fromBuilding(building));
        }
        if (!isBuildingVisible(building, selectedFloor)) {
            return List.of();
        }
        if (building.getBuildingType().grouped()) {
            return List.of(RegionBounds.fromBuilding(building));
        }
        LinkedHashSet<RegionBounds> regions = new LinkedHashSet<>();
        for (AssignedRegion assigned : assignedRegions.getOrDefault(building.getId(), List.of())) {
            if (assigned.ordinal() == selectedFloor) {
                regions.add(assigned.bounds());
            }
        }

        return List.copyOf(regions);
    }

    BlockPos iconPositionFor(Building building) {
        return building.getCenter();
    }

    boolean isPlayerVisible(LocalPlayer player, int selectedFloor, Village village) {
        BlockPos playerPos = player.blockPosition();
        AssignedRegion bestContaining = null;

        for (Map.Entry<Integer, List<AssignedRegion>> entry : assignedRegions.entrySet()) {
            Building building = village.getBuilding(entry.getKey()).orElse(null);
            if (building == null || !building.isComplete() || building.getBuildingType().grouped()) {
                continue;
            }

            for (AssignedRegion region : entry.getValue()) {
                if (!region.bounds().containsHorizontally(playerPos.getX(), playerPos.getZ())) {
                    continue;
                }
                if (bestContaining == null || isBetterPlayerRegion(region, bestContaining, playerPos.getY())) {
                    bestContaining = region;
                }
            }
        }

        if (bestContaining != null) {
            return bestContaining.ordinal() == selectedFloor;
        }

        AssignedRegion nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        for (List<AssignedRegion> regions : assignedRegions.values()) {
            for (AssignedRegion region : regions) {
                long distance = region.bounds().horizontalDistanceSqr(playerPos.getX(), playerPos.getZ())
                        + square((long) playerPos.getY() - region.anchorY());
                if (distance < nearestDistance
                        || (distance == nearestDistance && nearest != null
                        && region.anchorY() < nearest.anchorY())) {
                    nearest = region;
                    nearestDistance = distance;
                }
            }
        }

        return nearest == null ? selectedFloor == 0 : nearest.ordinal() == selectedFloor;
    }

    private static List<FloorCandidate> candidatesFor(Building building) {
        List<BuildingFloorRegion> regions = building.getFloorRegions();
        if (regions.isEmpty()) {
            return List.of(new FloorCandidate(
                    building.getId(),
                    building.getEffectiveStructureId(),
                    building.getFloorY(),
                    Math.max(1, building.getHorizontalArea()),
                    List.of(RegionBounds.fromBuilding(building)),
                    building.isStrictScan()
            ));
        }

        List<FloorCandidate> candidates = new ArrayList<>();
        for (BuildingFloorRegion region : regions) {
            List<RegionBounds> bounds = boundsFor(region);
            if (bounds.isEmpty()) {
                bounds = List.of(RegionBounds.fromBuilding(building));
            }
            candidates.add(new FloorCandidate(
                    building.getId(),
                    building.getEffectiveStructureId(),
                    region.anchorY(),
                    Math.max(1, region.area()),
                    bounds,
                    building.isStrictScan()
            ));
        }
        return List.copyOf(candidates);
    }


    private static List<RegionBounds> boundsFor(BuildingFloorRegion region) {
        return region.components().stream()
                .map(RegionBounds::fromComponent)
                .distinct()
                .toList();
    }

    private static Map<Integer, List<FloorCandidate>> getVerticalStacks(List<FloorCandidate> candidates) {
        Map<Integer, List<FloorCandidate>> byStructure = new TreeMap<>();
        for (FloorCandidate candidate : candidates) {
            byStructure.computeIfAbsent(candidate.structureId(), ignored -> new ArrayList<>())
                    .add(candidate);
        }
        for (List<FloorCandidate> stack : byStructure.values()) {
            stack.sort(Comparator.comparingInt(FloorCandidate::anchorY)
                    .thenComparingInt(FloorCandidate::buildingId));
        }
        return byStructure;
    }

    private static List<StackFloorLevel> clusterStackFloorLevels(List<FloorCandidate> candidates) {
        List<MutableStackFloorLevel> clusters = new ArrayList<>();
        for (FloorCandidate candidate : candidates) {
            MutableStackFloorLevel cluster = clusters.isEmpty() ? null : clusters.getLast();
            if (cluster == null || candidate.anchorY() - cluster.minY > FLOOR_CLUSTER_TOLERANCE) {
                cluster = new MutableStackFloorLevel(candidate.anchorY());
                clusters.add(cluster);
            }
            cluster.add(candidate);
        }
        return clusters.stream().map(MutableStackFloorLevel::freeze).toList();
    }

    private static int getClosestStackFloorIndexPreferLower(List<StackFloorLevel> levels, int y) {
        int bestIndex = 0;
        int bestDistance = Math.abs(levels.getFirst().anchorY() - y);
        for (int i = 1; i < levels.size(); i++) {
            int distance = Math.abs(levels.get(i).anchorY() - y);
            if (distance < bestDistance
                    || (distance == bestDistance && levels.get(i).anchorY() < levels.get(bestIndex).anchorY())) {
                bestIndex = i;
                bestDistance = distance;
            }
        }
        return bestIndex;
    }

    private static boolean isBetterPlayerRegion(AssignedRegion candidate, AssignedRegion current, int playerY) {
        boolean candidateSupporting = candidate.anchorY() <= playerY;
        boolean currentSupporting = current.anchorY() <= playerY;
        if (candidateSupporting != currentSupporting) {
            return candidateSupporting;
        }
        if (candidateSupporting) {
            return candidate.anchorY() > current.anchorY();
        }
        return candidate.anchorY() < current.anchorY();
    }

    private static long square(long value) {
        return value * value;
    }

    private static Map<Integer, List<AssignedRegion>> freezeRegionMap(Map<Integer, List<AssignedRegion>> source) {
        Map<Integer, List<AssignedRegion>> frozen = new HashMap<>();
        source.forEach((buildingId, regions) -> frozen.put(buildingId, List.copyOf(regions)));
        return Map.copyOf(frozen);
    }

    record RegionBounds(int minX, int minZ, int maxX, int maxZ) {
        static RegionBounds fromBuilding(Building building) {
            BlockPos min = building.getPos0();
            BlockPos max = building.getPos1();
            return new RegionBounds(min.getX(), min.getZ(), max.getX(), max.getZ());
        }

        static RegionBounds fromComponent(BuildingFloorRegion.Component component) {
            return new RegionBounds(component.minX(), component.minZ(), component.maxX(), component.maxZ());
        }

        boolean containsHorizontally(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        long horizontalDistanceSqr(int x, int z) {
            long dx = x < minX ? (long) minX - x : x > maxX ? (long) x - maxX : 0L;
            long dz = z < minZ ? (long) minZ - z : z > maxZ ? (long) z - maxZ : 0L;
            return square(dx) + square(dz);
        }
    }

    private record FloorCandidate(int buildingId,
                                  int structureId,
                                  int anchorY,
                                  long weight,
                                  List<RegionBounds> bounds,
                                  boolean strictScan) {
        private FloorCandidate {
            bounds = List.copyOf(bounds);
        }
    }

    private record AssignedRegion(int ordinal, int anchorY, RegionBounds bounds) {
    }

    private record VerticalStack(int structureId, List<StackFloorLevel> levels, int groundLevelIndex) {
    }

    private record StackFloorLevel(int anchorY, List<FloorCandidate> candidates) {
    }

    private static final class MutableStackFloorLevel {
        private final int minY;
        private long weightedY;
        private long totalWeight;
        private final List<FloorCandidate> candidates = new ArrayList<>();

        private MutableStackFloorLevel(int minY) {
            this.minY = minY;
        }

        private void add(FloorCandidate candidate) {
            weightedY += (long) candidate.anchorY() * candidate.weight();
            totalWeight += candidate.weight();
            candidates.add(candidate);
        }

        private StackFloorLevel freeze() {
            int anchorY = (int) Math.round((double) weightedY / totalWeight);
            return new StackFloorLevel(anchorY, List.copyOf(candidates));
        }
    }
}
