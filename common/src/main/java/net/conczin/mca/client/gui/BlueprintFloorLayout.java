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
    private static final int FLOOR_CLUSTER_TOLERANCE = 2;
    private static final String TOWN_CENTER_TYPE = "town_center";

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

        List<FloorLevel> discoveredAbsoluteFloors = getAbsoluteFloorLevels(allCandidates);
        int groundIndex = getGroundFloorIndex(village, discoveredAbsoluteFloors);
        int globalGroundY = groundIndex >= 0
                ? discoveredAbsoluteFloors.get(groundIndex).anchorY()
                : allCandidates.getFirst().anchorY();

        Map<Integer, Integer> rootGroundAnchors = new HashMap<>();
        for (Building building : structuralBuildings) {
            if (building.isStrictScan()) {
                continue;
            }
            allCandidates.stream()
                    .filter(candidate -> candidate.buildingId() == building.getId())
                    .min(Comparator.comparingInt(candidate -> Math.abs(candidate.anchorY() - globalGroundY)))
                    .ifPresent(candidate -> rootGroundAnchors.put(building.getId(), candidate.anchorY()));
        }

        // A whole-building scan owns the structure and its ground footprint. Higher
        // and lower semantic floors exist only while explicit room records own them.
        List<FloorCandidate> candidates = allCandidates.stream()
                .filter(candidate -> candidate.strictScan()
                        || rootGroundAnchors.getOrDefault(candidate.buildingId(), Integer.MIN_VALUE) == candidate.anchorY())
                .toList();
        if (candidates.isEmpty()) {
            return empty();
        }

        Map<Integer, List<AssignedRegion>> mutableAssignedRegions = new HashMap<>();
        TreeSet<Integer> availableOrdinals = new TreeSet<>();
        List<VerticalStack> stacks = new ArrayList<>();

        for (Map.Entry<Integer, List<FloorCandidate>> entry : getVerticalStacks(candidates).entrySet()) {
            List<StackFloorLevel> levels = clusterStackFloorLevels(entry.getValue());
            int groundLevelIndex = getClosestStackFloorIndexPreferLower(levels, globalGroundY);

            for (int levelIndex = 0; levelIndex < levels.size(); levelIndex++) {
                StackFloorLevel level = levels.get(levelIndex);
                int ordinal = levelIndex - groundLevelIndex;
                availableOrdinals.add(ordinal);

                for (FloorCandidate candidate : level.candidates()) {
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
        if (!building.isStrictScan()) {
            return selectedFloor == 0
                    ? List.of(RegionBounds.fromBuilding(building))
                    : List.of();
        }

        LinkedHashSet<RegionBounds> regions = new LinkedHashSet<>();
        for (AssignedRegion assigned : assignedRegions.getOrDefault(building.getId(), List.of())) {
            if (assigned.ordinal() == selectedFloor) {
                regions.add(assigned.bounds());
            }
        }

        return List.copyOf(regions);
    }

    BlockPos iconPositionFor(Building building, Village village) {
        if (village == null || building.getBuildingType().grouped()) {
            return building.getCenter();
        }

        int structureId = building.getEffectiveStructureId();
        return village.getBuildings().values().stream()
                .filter(candidate -> !candidate.getBuildingType().grouped())
                .filter(Building::isStructureRoot)
                .filter(candidate -> candidate.getEffectiveStructureId() == structureId)
                .min(Comparator.comparingInt(Building::getId))
                .map(Building::getCenter)
                .orElseGet(building::getCenter);
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

    private static List<FloorLevel> getAbsoluteFloorLevels(List<FloorCandidate> candidates) {
        List<FloorCandidate> sorted = candidates.stream()
                .sorted(Comparator.comparingInt(FloorCandidate::anchorY)
                        .thenComparingInt(FloorCandidate::buildingId))
                .toList();

        List<MutableFloorLevel> clusters = new ArrayList<>();
        for (FloorCandidate candidate : sorted) {
            MutableFloorLevel cluster = clusters.isEmpty() ? null : clusters.getLast();
            if (cluster == null || candidate.anchorY() - cluster.minY > FLOOR_CLUSTER_TOLERANCE) {
                cluster = new MutableFloorLevel(candidate.anchorY());
                clusters.add(cluster);
            }
            cluster.add(candidate);
        }

        return clusters.stream().map(MutableFloorLevel::freeze).toList();
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

    private static int getGroundFloorIndex(Village village, List<FloorLevel> floors) {
        if (floors.isEmpty()) {
            return -1;
        }

        int townCenterFloor = getTownCenterGroundFloorIndex(village, floors);
        if (townCenterFloor >= 0) {
            return townCenterFloor;
        }

        int villageCenterY = village.getCenter().getY();
        int bestIndex = 0;
        for (int i = 1; i < floors.size(); i++) {
            FloorLevel candidate = floors.get(i);
            FloorLevel best = floors.get(bestIndex);
            if (candidate.weight() > best.weight()
                    || (candidate.weight() == best.weight() && candidate.buildingCount() > best.buildingCount())
                    || (candidate.weight() == best.weight()
                    && candidate.buildingCount() == best.buildingCount()
                    && Math.abs(candidate.anchorY() - villageCenterY) < Math.abs(best.anchorY() - villageCenterY))
                    || (candidate.weight() == best.weight()
                    && candidate.buildingCount() == best.buildingCount()
                    && Math.abs(candidate.anchorY() - villageCenterY) == Math.abs(best.anchorY() - villageCenterY)
                    && candidate.anchorY() > best.anchorY())) {
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private static int getTownCenterGroundFloorIndex(Village village, List<FloorLevel> floors) {
        int[] votes = new int[floors.size()];
        long[] distances = new long[floors.size()];
        boolean foundTownCenter = false;

        for (Building building : village.getBuildings().values()) {
            if (!TOWN_CENTER_TYPE.equals(building.getType())) {
                continue;
            }

            List<BlockPos> positions = building.getBlockPosStream().toList();
            if (positions.isEmpty()) {
                positions = List.of(building.getCenter());
            }
            for (BlockPos pos : positions) {
                int floorIndex = getSupportingFloorIndex(floors, pos.getY());
                if (floorIndex < 0) {
                    continue;
                }
                votes[floorIndex]++;
                distances[floorIndex] += Math.abs((long) pos.getY() - floors.get(floorIndex).anchorY());
                foundTownCenter = true;
            }
        }

        if (!foundTownCenter) {
            return -1;
        }

        int bestIndex = 0;
        for (int i = 1; i < floors.size(); i++) {
            if (votes[i] > votes[bestIndex]
                    || (votes[i] == votes[bestIndex] && distances[i] < distances[bestIndex])
                    || (votes[i] == votes[bestIndex]
                    && distances[i] == distances[bestIndex]
                    && floors.get(i).anchorY() < floors.get(bestIndex).anchorY())) {
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    /**
     * A bell can be mounted above the walkable floor, so nearest-Y matching may jump
     * to the storey above. Use the highest detected floor at or below the POI instead.
     */
    private static int getSupportingFloorIndex(List<FloorLevel> floors, int y) {
        if (floors.isEmpty()) {
            return -1;
        }

        int supportingIndex = -1;
        for (int i = 0; i < floors.size(); i++) {
            if (floors.get(i).anchorY() <= y) {
                supportingIndex = i;
            } else {
                break;
            }
        }
        return supportingIndex >= 0 ? supportingIndex : 0;
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

    private record FloorLevel(int anchorY, long weight, int buildingCount) {
    }

    private record VerticalStack(int structureId, List<StackFloorLevel> levels, int groundLevelIndex) {
    }

    private record StackFloorLevel(int anchorY, List<FloorCandidate> candidates) {
    }

    private static final class MutableFloorLevel {
        private final int minY;
        private long weightedY;
        private long totalWeight;
        private final Set<Integer> buildingIds = new TreeSet<>();

        private MutableFloorLevel(int minY) {
            this.minY = minY;
        }

        private void add(FloorCandidate candidate) {
            weightedY += (long) candidate.anchorY() * candidate.weight();
            totalWeight += candidate.weight();
            buildingIds.add(candidate.buildingId());
        }

        private FloorLevel freeze() {
            int anchorY = (int) Math.round((double) weightedY / totalWeight);
            return new FloorLevel(anchorY, totalWeight, buildingIds.size());
        }
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
