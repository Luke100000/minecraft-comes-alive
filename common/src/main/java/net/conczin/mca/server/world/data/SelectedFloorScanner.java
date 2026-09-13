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

/** Discovers one exact integer storey while using Minecraft surface heights only for movement. */
final class SelectedFloorScanner {
    private static final double MAX_STEP_HEIGHT = 1.125D;
    private static final Comparator<BlockPos> CELL_ORDER = Comparator
            .comparingInt((BlockPos pos) -> pos.getX())
            .thenComparingInt(BlockPos::getZ)
            .thenComparingInt(BlockPos::getY);
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
        selected = retainEnclosedRegions(world, seedCell, selected, ceilings, maxRadius, provider);
        if (selected.result() != Building.validationResult.SUCCESS || selected.floor() == null) {
            return Result.failure(selected.result(), seed);
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
        return success(seed, floor, selected.transitions(), selected.storeyEdgeCells(), connectedFloors);
    }

    private static FloorGeometry attachConnectors(
            Level world, FloorGeometry floor, Collection<BlockPos> connectors) {
        return new FloorGeometry(
                floor.cells(), StructureConnector.connectorTypesForFloor(world, connectors, floor));
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
        Set<BlockPos> coveredCells = selectedFloor.cells().stream()
                .map(FloorGeometry.Cell::feet)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
        List<BlockPos> alternateSeeds = selected.alternateSeeds().stream()
                .sorted(Comparator.comparingInt((BlockPos pos) -> pos.getY())
                        .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ))
                .toList();
        for (BlockPos pos : alternateSeeds) {
            if (coveredCells.contains(pos)) continue;
            SurfaceCell seed = inspectSurfaceCell(world, pos, ceilings).orElse(null);
            if (seed == null) continue;
            StoreyScan adjacent = traverseStorey(seed, maxSize, maxRadius, provider);
            if (adjacent.result() != Building.validationResult.SUCCESS || adjacent.floor() == null) continue;
            adjacent = retainEnclosedRegions(world, seed, adjacent, ceilings, maxRadius, provider);
            if (adjacent.result() != Building.validationResult.SUCCESS || adjacent.floor() == null) continue;
            LinkedHashSet<BlockPos> adjacentConnectors = new LinkedHashSet<>(adjacent.connectors());
            for (FloorGeometry.Cell cell : adjacent.floor().cells()) {
                collectVerticalConnectors(world, cell, adjacentConnectors);
            }
            FloorGeometry floor = attachConnectors(world, adjacent.floor(), adjacentConnectors);
            floor.cells().stream().map(FloorGeometry.Cell::feet).forEach(coveredCells::add);
            if (floors.stream().noneMatch(existing -> existing.cells().equals(floor.cells()))) floors.add(floor);
        }
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

    private static StoreyScan traverseStorey(SurfaceCell seed,
                                             int maxSize,
                                             int maxRadius,
                                             StepProvider provider) {
        SurfaceCell anchor = seed;
        StoreyContext context = new StoreyContext(anchor.feet().getY());
        ArrayDeque<SurfaceCell> queue = new ArrayDeque<>();
        Set<BlockPos> queued = new HashSet<>();
        LinkedHashMap<BlockPos, SurfaceCell> cells = new LinkedHashMap<>();
        LinkedHashSet<Transition> transitions = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> storeyEdgeCells = new LinkedHashSet<>();
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
                transitions.add(new Transition(current.feet(), landing.feet()));
                if (role == StoreyRole.EDGE) {
                    cells.putIfAbsent(landing.feet(), landing);
                    storeyEdgeCells.add(landing.feet());
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
                Set.copyOf(transitions), Set.copyOf(storeyEdgeCells),
                Set.copyOf(alternateSeeds), Set.copyOf(connectors));
    }

    private static StoreyScan retainEnclosedRegions(
            Level world,
            SurfaceCell seed,
            StoreyScan selected,
            FloorCeilingResolver ceilings,
            int maxRadius,
            StepProvider provider) {
        Map<BlockPos, FloorConnector.Type> connectorTypes = StructureConnector.connectorTypesForFloor(
                world, selected.connectors(), selected.floor());
        Set<BlockPos> boundaryCells = connectorTypes.entrySet().stream()
                .filter(entry -> entry.getValue().roomBoundary())
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
        Map<BlockPos, List<BlockPos>> neighbors = Transition.neighborIndex(selected.transitions());
        Set<BlockPos> floorCells = selected.floor().cells().stream()
                .map(FloorGeometry.Cell::feet)
                .collect(java.util.stream.Collectors.toSet());
        List<Set<BlockPos>> regions = connectedRegions(floorCells, boundaryCells, neighbors);
        StoreyContext context = new StoreyContext(seed.feet().getY());
        Set<BlockPos> exteriorCells = new HashSet<>();

        for (Set<BlockPos> region : regions) {
            BlockPos representative = region.stream()
                    .min(CELL_ORDER)
                    .orElseThrow();
            SurfaceCell regionSeed = inspectSurfaceCell(world, representative, ceilings).orElse(null);
            if (regionSeed != null && reachesExterior(
                    world, regionSeed, seed.feet(), context, ceilings, maxRadius, provider)) {
                exteriorCells.addAll(region);
            }
        }

        if (exteriorCells.contains(seed.feet())) {
            return StoreyScan.failure(Building.validationResult.NOT_IN_BUILDING);
        }

        Set<BlockPos> allowed = new HashSet<>(floorCells);
        allowed.removeAll(exteriorCells);
        Set<BlockPos> reachable = reachableCells(seed.feet(), allowed, neighbors);
        if (reachable.isEmpty()) {
            return StoreyScan.failure(Building.validationResult.NOT_IN_BUILDING);
        }

        Set<FloorGeometry.Cell> cells = selected.floor().cells().stream()
                .filter(cell -> reachable.contains(cell.feet()))
                .collect(java.util.stream.Collectors.toSet());
        Set<Transition> transitions = selected.transitions().stream()
                .filter(transition -> reachable.contains(transition.first())
                        && reachable.contains(transition.second()))
                .collect(java.util.stream.Collectors.toSet());
        Set<BlockPos> storeyEdgeCells = selected.storeyEdgeCells().stream()
                .filter(reachable::contains)
                .collect(java.util.stream.Collectors.toSet());
        Set<BlockPos> connectors = selected.connectors().stream()
                .filter(connector -> reachable.contains(StructureConnector.normalize(
                        connector, world.getBlockState(connector))))
                .collect(java.util.stream.Collectors.toSet());
        Set<BlockPos> alternateSeeds = alternateSeedsForRetainedFloor(
                world, ceilings, reachable, context, provider);
        return new StoreyScan(
                Building.validationResult.SUCCESS,
                new FloorGeometry(cells, Map.of()),
                transitions,
                storeyEdgeCells,
                alternateSeeds,
                connectors);
    }

    private static Set<BlockPos> alternateSeedsForRetainedFloor(
            Level world,
            FloorCeilingResolver ceilings,
            Set<BlockPos> retained,
            StoreyContext context,
            StepProvider provider) {
        LinkedHashSet<BlockPos> alternateSeeds = new LinkedHashSet<>();
        for (BlockPos pos : retained) {
            SurfaceCell current = inspectSurfaceCell(world, pos, ceilings).orElse(null);
            if (current == null) continue;
            StoreyRole currentRole = storeyRole(context, current, provider);
            for (HorizontalStep step : provider.steps(current)) {
                SurfaceCell candidate = step.landing();
                if (currentRole == StoreyRole.EDGE) {
                    if (!retained.contains(candidate.feet())) alternateSeeds.add(candidate.feet());
                } else if (storeyRole(context, candidate, provider) == StoreyRole.OTHER) {
                    alternateSeeds.add(candidate.feet());
                }
            }
        }
        return Set.copyOf(alternateSeeds);
    }

    private static List<Set<BlockPos>> connectedRegions(
            Set<BlockPos> floorCells,
            Set<BlockPos> boundaryCells,
            Map<BlockPos, List<BlockPos>> neighbors) {
        Set<BlockPos> openCells = new HashSet<>(floorCells);
        openCells.removeAll(boundaryCells);
        Set<BlockPos> visited = new HashSet<>();
        List<Set<BlockPos>> regions = new ArrayList<>();
        for (BlockPos seed : openCells.stream().sorted(CELL_ORDER).toList()) {
            Set<BlockPos> region = connectedCells(seed, openCells, neighbors, visited);
            if (!region.isEmpty()) regions.add(region);
        }
        return List.copyOf(regions);
    }

    private static Set<BlockPos> reachableCells(
            BlockPos seed,
            Set<BlockPos> allowed,
            Map<BlockPos, List<BlockPos>> neighbors) {
        return connectedCells(seed, allowed, neighbors, new HashSet<>());
    }

    private static Set<BlockPos> connectedCells(
            BlockPos seed,
            Set<BlockPos> allowed,
            Map<BlockPos, List<BlockPos>> neighbors,
            Set<BlockPos> visited) {
        if (!allowed.contains(seed) || !visited.add(seed)) return Set.of();
        LinkedHashSet<BlockPos> connected = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.addLast(seed);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            connected.add(current);
            for (BlockPos next : neighbors.getOrDefault(current, List.of())) {
                if (allowed.contains(next) && visited.add(next)) queue.addLast(next);
            }
        }
        return Set.copyOf(connected);
    }

    private static StoreyRole storeyRole(StoreyContext context,
                                         SurfaceCell candidate,
                                         StepProvider provider) {
        int y = candidate.feet().getY();
        if (y < context.minOwnedY()
                || y > context.maxOwnedY() + 1) return StoreyRole.OTHER;
        if (y == context.maxOwnedY() + 1) return StoreyRole.EDGE;
        if (y == context.anchorY() && hasDescendingStep(candidate, provider)
                && descendsBelowOwnedBand(candidate, context, provider)) return StoreyRole.OTHER;
        return StoreyRole.OWNED;
    }

    private static boolean descendsBelowOwnedBand(
            SurfaceCell start, StoreyContext context, StepProvider provider) {
        ArrayDeque<SurfaceCell> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.addLast(start);
        visited.add(start.feet());
        while (!queue.isEmpty()) {
            SurfaceCell current = queue.removeFirst();
            for (HorizontalStep step : provider.steps(current)) {
                if (step.connector() != null) continue;
                SurfaceCell next = step.landing();
                int nextY = next.feet().getY();
                if (nextY > current.feet().getY()) continue;
                if (nextY < context.minOwnedY()) return true;
                if (visited.add(next.feet())) queue.addLast(next);
            }
        }
        return false;
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
                OptionalDouble surfaceY = supportedSurfaceY(world, connector);
                if (surfaceY.isPresent() && canStep(current.surfaceY(), surfaceY.getAsDouble())) {
                    steps.add(new PhysicalStep(new SurfaceProbe(connector, surfaceY.getAsDouble()), connector));
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
            BlockPos scanAnchor,
            StoreyContext context,
            FloorCeilingResolver ceilings,
            int maxRadius,
            StepProvider provider) {
        ArrayDeque<SurfaceCell> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.addLast(start);
        visited.add(start.feet());

        while (!queue.isEmpty()) {
            SurfaceCell current = queue.removeFirst();
            if (horizontalDistance(current.feet(), scanAnchor) >= maxRadius - 1) return true;

            for (PhysicalStep step : physicalSteps(world, new SurfaceProbe(current.feet(), current.surfaceY()))) {
                if (step.connector() != null) continue;
                SurfaceProbe landing = step.landing();
                BlockPos next = landing.feet();
                if (visited.contains(next)) continue;
                SurfaceCell probe = new SurfaceCell(next, landing.surfaceY(), next.getY() + 2);
                if (storeyRole(context, probe, provider) != StoreyRole.OWNED) continue;
                OptionalInt ceiling = ceilings.ceilingY(next);
                if (ceiling.isEmpty()) return true;
                SurfaceCell cell = new SurfaceCell(next, landing.surfaceY(), ceiling.getAsInt());
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
        if (!isInteriorOccupancyAllowed(world, feet) || !hasInteriorHeadroom(world, feet)) {
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
        if (StructureConnector.isHorizontalBoundary(state)) return true;
        var shape = state.getCollisionShape(world, pos);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) < 1.0D;
    }

    private static boolean hasInteriorHeadroom(Level world, BlockPos feet) {
        BlockPos above = feet.above();
        if (isOpen(world, above)) return true;
        BlockState feetState = world.getBlockState(feet);
        BlockState aboveState = world.getBlockState(above);
        return StructureConnector.isHorizontalBoundary(feetState)
                && StructureConnector.isHorizontalBoundary(aboveState)
                && StructureConnector.normalize(feet, feetState)
                .equals(StructureConnector.normalize(above, aboveState));
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
                                  Set<BlockPos> storeyEdgeCells,
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
                new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ),
                transitions, storeyEdgeCells, connectedFloors);
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

        static Map<BlockPos, List<BlockPos>> neighborIndex(Collection<Transition> transitions) {
            Map<BlockPos, LinkedHashSet<BlockPos>> indexed = new LinkedHashMap<>();
            if (transitions != null) {
                for (Transition transition : transitions) {
                    indexed.computeIfAbsent(transition.first(), ignored -> new LinkedHashSet<>())
                            .add(transition.second());
                    indexed.computeIfAbsent(transition.second(), ignored -> new LinkedHashSet<>())
                            .add(transition.first());
                }
            }
            Map<BlockPos, List<BlockPos>> frozen = new LinkedHashMap<>();
            indexed.forEach((cell, neighbors) -> frozen.put(cell, List.copyOf(neighbors)));
            return Map.copyOf(frozen);
        }

        boolean connects(BlockPos a, BlockPos b) {
            return b.equals(other(a));
        }

        BlockPos other(BlockPos pos) {
            return first.equals(pos) ? second : second.equals(pos) ? first : null;
        }
    }

    private enum StoreyRole {
        OWNED, EDGE, OTHER
    }

    private record StoreyContext(int anchorY) {
        int minOwnedY() {
            return anchorY - StructureFloor.BAND_TOLERANCE;
        }

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
                      Set<BlockPos> storeyEdgeCells,
                      Set<BlockPos> alternateSeeds,
                      Set<BlockPos> connectors) {
        StoreyScan {
            transitions = transitions == null ? Set.of() : Set.copyOf(transitions);
            storeyEdgeCells = storeyEdgeCells == null ? Set.of() : Set.copyOf(storeyEdgeCells);
            alternateSeeds = alternateSeeds == null ? Set.of() : Set.copyOf(alternateSeeds);
            connectors = connectors == null ? Set.of() : Set.copyOf(connectors);
        }

        static StoreyScan failure(Building.validationResult result) {
            return new StoreyScan(result, null, Set.of(), Set.of(), Set.of(), Set.of());
        }
    }

    private record SurfaceProbe(BlockPos feet, double surfaceY) {
    }

    record Result(Building.validationResult result,
                  FloorGeometry floor,
                  BlockPos min,
                  BlockPos max,
                  Set<Transition> transitions,
                  Set<BlockPos> storeyEdgeCells,
                  List<FloorGeometry> connectedFloors) {
        Result {
            transitions = transitions == null ? Set.of() : Set.copyOf(transitions);
            storeyEdgeCells = storeyEdgeCells == null ? Set.of() : Set.copyOf(storeyEdgeCells);
            connectedFloors = connectedFloors == null ? List.of() : List.copyOf(connectedFloors);
        }

        static Result failure(Building.validationResult result, BlockPos source) {
            return new Result(result, null, source, source, Set.of(), Set.of(), List.of());
        }
    }
}
