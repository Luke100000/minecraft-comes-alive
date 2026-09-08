package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;

/** Discovers one exact integer storey while using Minecraft surface heights only for movement. */
final class SelectedFloorScanner {
    private static final double MAX_STEP_HEIGHT = 1.125D;
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final int[] LANDING_Y_OFFSETS = {0, 1, -1};

    private SelectedFloorScanner() {
    }

    static Result scan(Level world, BlockPos seed, int maxSize, int maxRadius) {
        FloorCeilingResolver ceilings = new FloorCeilingResolver(world);
        SurfaceCell seedCell = resolveSeedCell(world, seed, ceilings).orElse(null);
        if (seedCell == null) return Result.failure(Building.validationResult.NOT_IN_BUILDING, seed);
        StepProvider provider = cell -> worldSteps(world, cell, ceilings);
        StoreyScan selected = traverseStorey(seedCell, maxSize, maxRadius, provider);
        if (selected.result() != Building.validationResult.SUCCESS || selected.floor() == null) {
            return Result.failure(selected.result(), seed);
        }
        int anchorY = selected.floor().anchorY();
        if (reachesExterior(world, seedCell, anchorY, ceilings, maxRadius, provider)) {
            return Result.failure(Building.validationResult.NOT_IN_BUILDING, seed);
        }

        LinkedHashSet<BlockPos> connectors = new LinkedHashSet<>(selected.connectors());
        for (FloorGeometry.Cell cell : selected.floor().cells()) {
            collectVerticalConnectors(world, cell, connectors);
        }
        if (selected.floor().cells().size() + connectors.size() > maxSize) {
            return Result.failure(Building.validationResult.BLOCK_LIMIT, seed);
        }
        FloorGeometry floor = attachConnectors(world, selected.floor(), connectors);
        List<FloorGeometry> connectedFloors = connectedStoreyEvidence(
                world, selected, floor, provider, ceilings, maxSize, maxRadius);
        return success(seed, floor, selected.transitions(), connectedFloors);
    }

    private static FloorGeometry attachConnectors(
            Level world, FloorGeometry floor, Collection<BlockPos> connectors) {
        return StructureConnector.withConnectorAssociations(
                floor, StructureConnector.associatedFloorCells(world, connectors, floor));
    }

    private static List<FloorGeometry> connectedStoreyEvidence(
            Level world,
            StoreyScan selected,
            FloorGeometry selectedFloor,
            StepProvider provider,
            FloorCeilingResolver ceilings,
            int maxSize,
            int maxRadius) {
        List<FloorGeometry> floors = new ArrayList<>();
        floors.add(selectedFloor);
        selected.alternateSeeds().stream()
                .sorted(Comparator.comparingInt((BlockPos pos) -> pos.getY())
                        .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ))
                .forEach(pos -> inspectSurfaceCell(world, pos, ceilings).ifPresent(seed -> {
                    StoreyScan adjacent = traverseStorey(seed, maxSize, maxRadius, provider);
                    if (adjacent.result() != Building.validationResult.SUCCESS || adjacent.floor() == null) return;
                    LinkedHashSet<BlockPos> adjacentConnectors = new LinkedHashSet<>(adjacent.connectors());
                    for (FloorGeometry.Cell cell : adjacent.floor().cells()) {
                        collectVerticalConnectors(world, cell, adjacentConnectors);
                    }
                    FloorGeometry floor = attachConnectors(world, adjacent.floor(), adjacentConnectors);
                    if (floors.stream().noneMatch(existing -> existing.cells().equals(floor.cells()))) floors.add(floor);
                }));
        floors.sort(Comparator.comparingInt(FloorGeometry::anchorY));
        return List.copyOf(floors);
    }

    private static Optional<SurfaceCell> resolveSeedCell(
            Level world, BlockPos seed, FloorCeilingResolver ceilings) {
        BlockPos below = seed.below();
        if (isLowObstacle(world, below)) {
            Optional<SurfaceCell> underlying = inspectSurfaceCell(world, below, ceilings);
            if (underlying.isPresent()) return underlying;
        }
        return inspectSurfaceCell(world, seed, ceilings);
    }

    static StoreyScan selectStorey(Collection<SurfaceCell> discovered,
                                   Collection<Transition> transitions,
                                   BlockPos seed) {
        if (discovered == null || discovered.isEmpty() || seed == null) return StoreyScan.empty();
        Map<BlockPos, SurfaceCell> byPos = discovered.stream().collect(java.util.stream.Collectors.toMap(
                SurfaceCell::feet, cell -> cell, (first, ignored) -> first));
        SurfaceCell seedCell = byPos.get(seed);
        if (seedCell == null) return StoreyScan.empty();
        Collection<Transition> edges = transitions == null ? List.of() : transitions;
        StepProvider provider = current -> edges.stream()
                .filter(edge -> edge.first().equals(current.feet()) || edge.second().equals(current.feet()))
                .map(edge -> edge.first().equals(current.feet()) ? edge.second() : edge.first())
                .map(byPos::get)
                .filter(java.util.Objects::nonNull)
                .map(cell -> new HorizontalStep(cell, null))
                .sorted(HorizontalStep.ORDER)
                .toList();
        return traverseStorey(seedCell, Integer.MAX_VALUE, Integer.MAX_VALUE, provider);
    }

    private static StoreyScan traverseStorey(SurfaceCell seed,
                                             int maxSize,
                                             int maxRadius,
                                             StepProvider provider) {
        SurfaceCell anchor = resolveStoreyAnchor(seed, provider);
        StoreyContext context = new StoreyContext(anchor.feet().getY());
        ArrayDeque<SurfaceCell> queue = new ArrayDeque<>();
        Set<BlockPos> queued = new HashSet<>();
        LinkedHashMap<BlockPos, SurfaceCell> cells = new LinkedHashMap<>();
        LinkedHashSet<Transition> transitions = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> alternateSeeds = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> connectors = new LinkedHashSet<>();
        queue.addLast(anchor);
        queued.add(anchor.feet());

        while (!queue.isEmpty()) {
            SurfaceCell current = queue.removeFirst();
            if (horizontalDistance(current.feet(), anchor.feet()) >= maxRadius) {
                return StoreyScan.failure(Building.validationResult.SIZE_LIMIT);
            }
            cells.put(current.feet(), current);
            for (HorizontalStep step : provider.steps(current)) {
                if (step.connector() != null) connectors.add(step.connector());
                SurfaceCell landing = step.landing();
                StoreyRole role = storeyRole(context, landing, provider);
                if (role == StoreyRole.OTHER) {
                    alternateSeeds.add(landing.feet());
                    continue;
                }
                if (step.connector() == null) transitions.add(new Transition(current.feet(), landing.feet()));
                if (role == StoreyRole.EDGE) {
                    cells.putIfAbsent(landing.feet(), landing);
                    provider.steps(landing).stream()
                            .map(HorizontalStep::landing)
                            .map(SurfaceCell::feet)
                            .filter(pos -> !cells.containsKey(pos))
                            .forEach(alternateSeeds::add);
                } else if (queued.add(landing.feet())) {
                    queue.addLast(landing);
                }
            }
            if (cells.size() + connectors.size() > maxSize) {
                return StoreyScan.failure(Building.validationResult.BLOCK_LIMIT);
            }
        }

        FloorGeometry floor = new FloorGeometry(cells.values().stream()
                .map(SurfaceCell::canonical).collect(java.util.stream.Collectors.toSet()), Map.of());
        return new StoreyScan(Building.validationResult.SUCCESS, floor,
                Set.copyOf(transitions), Set.copyOf(alternateSeeds), Set.copyOf(connectors));
    }

    private static SurfaceCell resolveStoreyAnchor(SurfaceCell seed, StepProvider provider) {
        SurfaceCell current = seed;
        Set<BlockPos> visited = new HashSet<>();
        while (visited.add(current.feet()) && !hasSameHeightPeer(current, provider)) {
            int currentY = current.feet().getY();
            SurfaceCell next = provider.steps(current).stream()
                    .filter(step -> step.connector() == null)
                    .map(HorizontalStep::landing)
                    .filter(candidate -> candidate.feet().getY() < currentY)
                    .max(Comparator.comparingInt(candidate -> candidate.feet().getY()))
                    .orElse(null);
            if (next == null) break;
            current = next;
        }
        return current;
    }

    private static StoreyRole storeyRole(StoreyContext context,
                                         SurfaceCell candidate,
                                         StepProvider provider) {
        int y = candidate.feet().getY();
        if (y < context.anchorY() || y > context.maxOwnedY() + 1) return StoreyRole.OTHER;
        if (y == context.maxOwnedY() + 1) return StoreyRole.EDGE;
        if (y == context.anchorY() && hasDescendingStep(candidate, provider)
                && sameHeightPeerCount(candidate, provider) < 2) return StoreyRole.OTHER;
        return StoreyRole.OWNED;
    }

    private static boolean hasSameHeightPeer(SurfaceCell cell, StepProvider provider) {
        return sameHeightPeerCount(cell, provider) > 0;
    }

    private static long sameHeightPeerCount(SurfaceCell cell, StepProvider provider) {
        return provider.steps(cell).stream()
                .filter(step -> step.connector() == null
                        && step.landing().feet().getY() == cell.feet().getY())
                .limit(2)
                .count();
    }

    private static boolean hasDescendingStep(SurfaceCell cell, StepProvider provider) {
        return provider.steps(cell).stream()
                .anyMatch(step -> step.connector() == null
                        && step.landing().feet().getY() < cell.feet().getY());
    }

    static Optional<SurfaceCell> inspectSurfaceCell(
            Level world, BlockPos feet, FloorCeilingResolver ceilings) {
        OptionalDouble surfaceY = supportedSurfaceY(world, feet);
        if (surfaceY.isEmpty()) return Optional.empty();
        OptionalInt ceiling = ceilings.ceilingY(feet);
        if (ceiling.isEmpty()) return Optional.empty();
        return Optional.of(new SurfaceCell(feet, surfaceY.getAsDouble(), ceiling.getAsInt()));
    }

    private static List<HorizontalStep> worldSteps(
            Level world, SurfaceCell current, FloorCeilingResolver ceilings) {
        List<HorizontalStep> steps = new ArrayList<>();
        for (PhysicalStep step : physicalSteps(world, new SurfaceProbe(current.feet(), current.surfaceY()))) {
            SurfaceProbe landing = step.landing();
            OptionalInt ceiling = ceilings.ceilingY(landing.feet());
            if (ceiling.isPresent()) {
                steps.add(new HorizontalStep(new SurfaceCell(
                        landing.feet(), landing.surfaceY(), ceiling.getAsInt()), step.connector()));
            }
        }
        steps.sort(HorizontalStep.ORDER);
        return List.copyOf(steps);
    }

    private static List<PhysicalStep> physicalSteps(Level world, SurfaceProbe current) {
        List<PhysicalStep> steps = new ArrayList<>();
        for (Direction direction : HORIZONTAL) {
            BlockPos horizontal = current.feet().relative(direction);
            BlockPos connector = null;
            for (int dy : LANDING_Y_OFFSETS) {
                BlockPos candidate = horizontal.offset(0, dy, 0);
                BlockState state = world.getBlockState(candidate);
                if (StructureConnector.isConnector(state)) {
                    connector = StructureConnector.normalize(candidate, state);
                    break;
                }
            }
            if (connector != null) {
                BlockState state = world.getBlockState(connector);
                if (!StructureConnector.isHorizontalBoundary(state)) continue;
                for (SurfaceProbe farSide : findLandings(
                        world, current.surfaceY(), connector.relative(direction))) {
                    steps.add(new PhysicalStep(farSide, connector));
                }
            } else {
                for (SurfaceProbe landing : findLandings(world, current.surfaceY(), horizontal)) {
                    steps.add(new PhysicalStep(landing, null));
                }
            }
        }
        return List.copyOf(steps);
    }

    private static List<SurfaceProbe> findLandings(
            Level world, double currentSurfaceY, BlockPos horizontal) {
        List<SurfaceProbe> landings = new ArrayList<>();
        for (int dy : LANDING_Y_OFFSETS) {
            BlockPos feet = horizontal.offset(0, dy, 0);
            OptionalDouble surfaceY = supportedSurfaceY(world, feet);
            if (surfaceY.isPresent() && canStep(currentSurfaceY, surfaceY.getAsDouble())) {
                landings.add(new SurfaceProbe(feet, surfaceY.getAsDouble()));
            }
        }
        return List.copyOf(landings);
    }

    private static boolean reachesExterior(
            Level world,
            SurfaceCell start,
            int seedY,
            FloorCeilingResolver ceilings,
            int maxRadius,
            StepProvider provider) {
        StoreyContext context = new StoreyContext(seedY);
        ArrayDeque<SurfaceCell> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.addLast(start);
        visited.add(start.feet());

        while (!queue.isEmpty()) {
            SurfaceCell current = queue.removeFirst();
            if (horizontalDistance(current.feet(), start.feet()) >= maxRadius - 1) return true;

            for (PhysicalStep step : physicalSteps(world, new SurfaceProbe(current.feet(), current.surfaceY()))) {
                if (step.connector() != null) continue;
                SurfaceProbe landing = step.landing();
                BlockPos next = landing.feet();
                int y = next.getY();
                if (y < context.anchorY() || y > context.maxOwnedY() || visited.contains(next)) continue;

                OptionalInt ceiling = ceilings.ceilingY(next);
                if (ceiling.isEmpty()) return true;
                SurfaceCell cell = new SurfaceCell(next, landing.surfaceY(), ceiling.getAsInt());
                if (storeyRole(context, cell, provider) != StoreyRole.OWNED) continue;
                visited.add(next);
                queue.addLast(cell);
            }
        }
        return false;
    }

    private static void collectVerticalConnectors(
            Level world, FloorGeometry.Cell cell, Set<BlockPos> connectors) {
        for (int y = cell.feet().getY() - 1; y < cell.ceilingY(); y++) {
            collectVerticalConnectorAt(world,
                    new BlockPos(cell.feet().getX(), y, cell.feet().getZ()), connectors);
            for (Direction direction : HORIZONTAL) {
                collectVerticalConnectorAt(world,
                        new BlockPos(cell.feet().getX() + direction.getStepX(), y,
                                cell.feet().getZ() + direction.getStepZ()), connectors);
            }
        }
    }

    private static void collectVerticalConnectorAt(
            Level world, BlockPos candidate, Set<BlockPos> connectors) {
        BlockPos connector = StructureConnector.verticalInteractionConnector(world, candidate);
        if (connector != null && StructureConnector.isVertical(world, connector)) {
            connectors.add(StructureConnector.normalize(connector, world.getBlockState(connector)));
        }
    }

    private static OptionalDouble supportedSurfaceY(Level world, BlockPos feet) {
        if (!isInteriorOccupancyAllowed(world, feet) || !isOpen(world, feet.above())) {
            return OptionalDouble.empty();
        }
        BlockPos support = feet.below();
        var shape = world.getBlockState(support).getCollisionShape(world, support);
        if (shape.isEmpty()) return OptionalDouble.empty();
        double width = shape.max(Direction.Axis.X) - shape.min(Direction.Axis.X);
        double depth = shape.max(Direction.Axis.Z) - shape.min(Direction.Axis.Z);
        if (width * depth < 0.25D) return OptionalDouble.empty();
        return OptionalDouble.of(WalkNodeEvaluator.getFloorLevel(world, feet));
    }

    /** Low furniture occupies the room without redefining the structural floor underneath it. */
    private static boolean isInteriorOccupancyAllowed(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) return false;
        var shape = state.getCollisionShape(world, pos);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) < 1.0D;
    }

    private static boolean isLowObstacle(Level world, BlockPos pos) {
        return isInteriorOccupancyAllowed(world, pos) && !isOpen(world, pos);
    }

    private static boolean isOpen(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getFluidState().isEmpty() && state.getCollisionShape(world, pos).isEmpty();
    }

    private static int horizontalDistance(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX()) + Math.abs(first.getZ() - second.getZ());
    }

    static boolean canStep(double fromSurfaceY, double toSurfaceY) {
        return Math.abs(toSurfaceY - fromSurfaceY) <= MAX_STEP_HEIGHT;
    }

    private static Result success(BlockPos seed,
                                  FloorGeometry floor,
                                  Set<Transition> transitions,
                                  List<FloorGeometry> connectedFloors) {
        FloorGeometry geometry = floor;
        Set<BlockPos> footprint = geometry.projection().cells();
        int minX = footprint.stream().mapToInt(BlockPos::getX).min().orElse(seed.getX());
        int minZ = footprint.stream().mapToInt(BlockPos::getZ).min().orElse(seed.getZ());
        int maxX = footprint.stream().mapToInt(BlockPos::getX).max().orElse(seed.getX());
        int maxZ = footprint.stream().mapToInt(BlockPos::getZ).max().orElse(seed.getZ());
        int minY = geometry.cells().stream().mapToInt(cell -> cell.feet().getY() - 1)
                .min().orElse(seed.getY() - 1);
        int maxY = geometry.cells().stream().mapToInt(cell -> cell.ceilingY() - 1)
                .max().orElse(seed.getY());
        return new Result(Building.validationResult.SUCCESS, floor,
                new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), transitions, connectedFloors);
    }

    record SurfaceCell(BlockPos feet, double surfaceY, int ceilingY) {
        SurfaceCell {
            feet = feet.immutable();
        }

        FloorGeometry.Cell canonical() {
            return new FloorGeometry.Cell(feet, ceilingY);
        }
    }

    record Transition(BlockPos first, BlockPos second) {
        Transition {
            first = first.immutable();
            second = second.immutable();
        }

        boolean connects(BlockPos a, BlockPos b) {
            return (first.equals(a) && second.equals(b))
                    || (first.equals(b) && second.equals(a));
        }
    }

    private enum StoreyRole {
        OWNED, EDGE, OTHER
    }

    private record StoreyContext(int anchorY) {
        int maxOwnedY() {
            return anchorY + StructureFloor.BAND_TOLERANCE;
        }
    }

    private record HorizontalStep(SurfaceCell landing, BlockPos connector) {
        private static final Comparator<HorizontalStep> ORDER = Comparator
                .comparingInt((HorizontalStep step) -> step.landing().feet().getY())
                .thenComparingInt(step -> step.landing().feet().getX())
                .thenComparingInt(step -> step.landing().feet().getZ());
    }

    private record PhysicalStep(SurfaceProbe landing, BlockPos connector) {
    }

    @FunctionalInterface
    private interface StepProvider {
        List<HorizontalStep> steps(SurfaceCell cell);
    }

    record StoreyScan(Building.validationResult result,
                      FloorGeometry floor,
                      Set<Transition> transitions,
                      Set<BlockPos> alternateSeeds,
                      Set<BlockPos> connectors) {
        StoreyScan {
            transitions = transitions == null ? Set.of() : Set.copyOf(transitions);
            alternateSeeds = alternateSeeds == null ? Set.of() : Set.copyOf(alternateSeeds);
            connectors = connectors == null ? Set.of() : Set.copyOf(connectors);
        }

        static StoreyScan empty() {
            return failure(Building.validationResult.NOT_IN_BUILDING);
        }

        static StoreyScan failure(Building.validationResult result) {
            return new StoreyScan(result, null, Set.of(), Set.of(), Set.of());
        }
    }

    private record SurfaceProbe(BlockPos feet, double surfaceY) {
    }

    record Result(Building.validationResult result,
                  FloorGeometry floor,
                  BlockPos min,
                  BlockPos max,
                  Set<Transition> transitions,
                  List<FloorGeometry> connectedFloors) {
        Result {
            transitions = transitions == null ? Set.of() : Set.copyOf(transitions);
            connectedFloors = connectedFloors == null ? List.of() : List.copyOf(connectedFloors);
        }

        static Result failure(Building.validationResult result, BlockPos source) {
            return new Result(result, null, source, source, Set.of(), List.of());
        }
    }
}
