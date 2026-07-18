package net.conczin.mca.client.gui;

import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
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
    // Semantic floor identity has one source of truth. Physical floor-region detection may
    // group scan samples more loosely, but that must not merge a basement room into Ground Floor.
    private static final int FLOOR_CLUSTER_TOLERANCE = Building.SEMANTIC_FLOOR_TOLERANCE;

    private final Map<Integer, List<AssignedFloor>> assignedFloors;
    private final List<Integer> ordinals;
    private final List<VerticalStack> stacks;

    private BlueprintFloorLayout(Map<Integer, List<AssignedFloor>> assignedFloors,
                                 List<Integer> ordinals,
                                 List<VerticalStack> stacks) {
        this.assignedFloors = assignedFloors;
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
                .filter(Building::isFunctionalRoom)
                .flatMap(building -> candidatesFor(building).stream())
                .sorted(Comparator.comparingInt(FloorCandidate::anchorY)
                        .thenComparingInt(FloorCandidate::buildingId))
                .toList();

        Map<Integer, Integer> rootGroundAnchors = new HashMap<>();
        for (Building building : structuralBuildings) {
            if (!building.isStructureRoot()) {
                continue;
            }
            rootGroundAnchors.put(building.getEffectiveStructureId(), building.getGroundFloorY());
        }

        Map<Integer, List<AssignedFloor>> mutableAssignedFloors = new HashMap<>();
        TreeSet<Integer> availableOrdinals = new TreeSet<>();
        List<VerticalStack> stacks = new ArrayList<>();

        for (Map.Entry<Integer, List<FloorCandidate>> entry : getVerticalStacks(allCandidates).entrySet()) {
            List<StackFloorLevel> levels = clusterStackFloorLevels(entry.getValue());
            Integer groundY = rootGroundAnchors.get(entry.getKey());
            if (groundY == null) {
                continue;
            }

            OptionalInt groundLevelIndexResult = findRegisteredGroundFloorIndex(levels, groundY);
            if (groundLevelIndexResult.isEmpty()) {
                // Invalid legacy/regressed data is not reinterpreted by the client. The
                // server invariant creates a real Ground Floor room for canonical structures.
                continue;
            }
            int groundLevelIndex = groundLevelIndexResult.getAsInt();

            for (int levelIndex = 0; levelIndex < levels.size(); levelIndex++) {
                StackFloorLevel level = levels.get(levelIndex);
                int ordinal = levelIndex - groundLevelIndex;

                if (!level.candidates().isEmpty()) {
                    availableOrdinals.add(ordinal);
                }

                for (FloorCandidate candidate : level.candidates()) {
                    mutableAssignedFloors
                            .computeIfAbsent(candidate.buildingId(), ignored -> new ArrayList<>())
                            .add(new AssignedFloor(ordinal));
                }
            }

            stacks.add(new VerticalStack(entry.getKey(), levels, groundLevelIndex));
        }

        return new BlueprintFloorLayout(
                freezeFloorMap(mutableAssignedFloors),
                List.copyOf(availableOrdinals),
                List.copyOf(stacks)
        );
    }

    List<Integer> ordinals() {
        return ordinals;
    }

    List<Integer> ordinalsFor(Building building) {
        int structureId = building.getEffectiveStructureId();
        for (VerticalStack stack : stacks) {
            if (stack.structureId() != structureId) {
                continue;
            }

            List<Integer> structureOrdinals = new ArrayList<>(stack.levels().size());
            for (int levelIndex = 0; levelIndex < stack.levels().size(); levelIndex++) {
                structureOrdinals.add(levelIndex - stack.groundLevelIndex());
            }
            return List.copyOf(structureOrdinals);
        }
        return List.of();
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
        return false;
    }

    boolean isBuildingVisible(Building building, Integer selectedFloor) {
        if (building.getBuildingType().grouped()) {
            return selectedFloor == null || selectedFloor == 0;
        }

        if (!building.isFunctionalRoom()) {
            return false;
        }

        List<AssignedFloor> floors = assignedFloors.get(building.getId());
        return floors != null && !floors.isEmpty()
                && (selectedFloor == null || floors.stream().anyMatch(floor -> floor.ordinal() == selectedFloor));
    }

    OptionalInt floorOrdinalFor(Building building) {
        return assignedFloors.getOrDefault(building.getId(), List.of()).stream()
                .mapToInt(AssignedFloor::ordinal)
                .findFirst();
    }

    private static List<FloorCandidate> candidatesFor(Building building) {
        return List.of(new FloorCandidate(
                building.getId(),
                building.getEffectiveStructureId(),
                building.getFloorY(),
                Math.max(1L, building.getFloorFootprintArea())
        ));
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

    private static OptionalInt findRegisteredGroundFloorIndex(List<StackFloorLevel> levels, int groundY) {
        for (int i = 0; i < levels.size(); i++) {
            if (Math.abs(levels.get(i).anchorY() - groundY) <= FLOOR_CLUSTER_TOLERANCE) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
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

    private static Map<Integer, List<AssignedFloor>> freezeFloorMap(Map<Integer, List<AssignedFloor>> source) {
        Map<Integer, List<AssignedFloor>> frozen = new HashMap<>();
        source.forEach((buildingId, floors) -> frozen.put(buildingId, List.copyOf(floors)));
        return Map.copyOf(frozen);
    }

    private record FloorCandidate(int buildingId,
                                  int structureId,
                                  int anchorY,
                                  long weight) {
    }

    private record AssignedFloor(int ordinal) {
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
