package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

/** Discovers one exact selected Floor while using Minecraft surface heights only for movement. */
final class SelectedFloorScanner {
    private static final double MAX_STEP_HEIGHT = 1.125D;
    private static final int FLOOR_BAND_RADIUS = 2;
    // Full-block stairs have no block metadata marking where the staircase ends. Two stable
    // same-height exits are enough evidence that the cell is a landing/plateau, not the stair tip.
    private static final int MIN_STABLE_LANDING_PEERS = 2;
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

    /** Resolves exactly one selected Floor. */
    static Result scan(Level world, BlockPos seed, int maxSize, int maxRadius) {
        return new Observation(world, maxSize, maxRadius).selected(seed);
    }

    /** All discovery evidence lives for one operation; none is retained across world changes. */
    static final class Observation {
        private final Level world;
        private final int maxSize;
        private final int maxRadius;
        private final FloorCeilingResolver ceilings;
        private final StepProvider provider;
        private final Map<BlockPos, Result> resolutions = new HashMap<>();

        Observation(Level world, int maxSize, int maxRadius) {
            this.world = world;
            this.maxSize = maxSize;
            this.maxRadius = maxRadius;
            this.ceilings = new FloorCeilingResolver(world);
            Map<BlockPos, List<HorizontalStep>> steps = new HashMap<>();
            this.provider = cell -> steps.computeIfAbsent(
                    cell.feet(), ignored -> worldSteps(world, cell, ceilings));
        }

        private Result resolve(BlockPos seed) {
            return resolutions.computeIfAbsent(seed.immutable(), requested -> {
                SurfaceCell traversalSeed = resolveScanSeed(world, requested, ceilings).orElse(null);
                if (traversalSeed == null) {
                    return Result.failure(Building.validationResult.NOT_IN_BUILDING, requested);
                }

                traversalSeed = resolveSelectedFloorAnchor(world, traversalSeed, provider);
                return resolveSelectedFloor(
                        world, traversalSeed, ceilings, provider, maxSize, maxRadius);
            });
        }

        Result selected(BlockPos seed) {
            return resolve(seed);
        }
    }

    private static Result resolveSelectedFloor(
            Level world,
            SurfaceCell traversalSeed,
            FloorCeilingResolver ceilings,
            StepProvider provider,
            int maxSize,
            int maxRadius) {
        FloorBandClassifier classifier = new FloorBandClassifier(
                world, new FloorBand(traversalSeed.feet().getY()), provider);
        SelectedFloorScan scan = traverseSelectedFloor(
                world, traversalSeed, ceilings, maxSize, maxRadius, provider, classifier);
        if (scan.result() != Building.validationResult.SUCCESS || scan.floor() == null) {
            return Result.failure(scan.result(), traversalSeed.feet());
        }
        scan = retainEnclosedRegions(
                world, traversalSeed, scan, ceilings, maxRadius, provider, classifier);
        if (scan.result() != Building.validationResult.SUCCESS || scan.floor() == null) {
            return Result.failure(scan.result(), traversalSeed.feet());
        }

        LinkedHashSet<BlockPos> connectors = new LinkedHashSet<>(scan.connectors());
        for (FloorGeometry.Cell cell : scan.floor().cells()) {
            collectVerticalConnectors(world, cell, connectors);
        }
        if (scan.floor().cells().size() + connectors.size() > maxSize) {
            return Result.failure(Building.validationResult.BLOCK_LIMIT, traversalSeed.feet());
        }
        FloorGeometry floor = new FloorGeometry(scan.floor().cells(),
                StructureConnector.connectorMarkersForFloor(world, connectors, scan.floor()));
        return success(traversalSeed.feet(), floor,
                scan.transitions(), scan.verticalBoundaryCells(), scan.adjacentFloorSeeds());
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

    private static Optional<SurfaceCell> resolveScanSeed(
            Level world, BlockPos seed, FloorCeilingResolver ceilings) {
        SurfaceCell traversalSeed = resolveSeedCell(world, seed, ceilings).orElse(null);
        if (traversalSeed != null) return Optional.of(traversalSeed);

        return adjacentTraversalSeed(world, seed, ceilings);
    }

    private static Optional<SurfaceCell> adjacentTraversalSeed(
            Level world, BlockPos membershipCell, FloorCeilingResolver ceilings) {
        OptionalDouble handoffY = interactionHandoffY(world, membershipCell, ceilings);
        if (handoffY.isEmpty()) return Optional.empty();

        for (Direction direction : HORIZONTAL) {
            BlockPos horizontal = membershipCell.relative(direction);
            for (SurfaceProbe landing : findLandings(world, handoffY.getAsDouble(), horizontal)) {
                OptionalInt ceiling = ceilings.ceilingY(landing.feet());
                if (ceiling.isPresent()) {
                    return Optional.of(new SurfaceCell(
                            landing.feet(), landing.surfaceY(), ceiling.getAsInt()));
                }
            }
        }
        return Optional.empty();
    }

    private static SelectedFloorScan traverseSelectedFloor(Level world,
                                             SurfaceCell seed,
                                             FloorCeilingResolver ceilings,
                                             int maxSize,
                                             int maxRadius,
                                             StepProvider provider,
                                             FloorBandClassifier classifier) {
        SurfaceCell anchor = seed;
        ArrayDeque<SurfaceCell> queue = new ArrayDeque<>();
        Set<BlockPos> queued = new HashSet<>();
        LinkedHashMap<BlockPos, SurfaceCell> cells = new LinkedHashMap<>();
        LinkedHashSet<Transition> transitions = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> verticalBoundaryCells = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> adjacentFloorSeeds = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> connectors = new LinkedHashSet<>();
        queue.addLast(anchor);
        queued.add(anchor.feet());

        while (!queue.isEmpty()) {
            SurfaceCell current = queue.removeFirst();
            if (horizontalDistance(current.feet(), anchor.feet()) >= maxRadius) {
                return SelectedFloorScan.failure(Building.validationResult.SIZE_LIMIT);
            }
            cells.put(current.feet(), current);
            List<HorizontalStep> steps = StructureConnector.isHorizontalBoundary(
                    world.getBlockState(current.feet()))
                    ? enclosedBoundaryContinuations(
                    world, current, anchor.feet(), ceilings, maxRadius, provider, classifier, queued)
                    : provider.steps(current);
            for (HorizontalStep step : steps) {
                if (step.connector() != null) connectors.add(step.connector());
                SurfaceCell landing = step.landing();
                FloorBandRole role = classifier.role(landing);
                if (role == FloorBandRole.OTHER) {
                    adjacentFloorSeeds.add(landing.feet());
                    continue;
                }
                transitions.add(new Transition(current.feet(), landing.feet()));
                if (role == FloorBandRole.EDGE) {
                    cells.putIfAbsent(landing.feet(), landing);
                    verticalBoundaryCells.add(landing.feet());
                    provider.steps(landing).stream()
                            .map(HorizontalStep::landing)
                            .map(SurfaceCell::feet)
                            .filter(pos -> !cells.containsKey(pos))
                            .forEach(adjacentFloorSeeds::add);
                } else if (queued.add(landing.feet())) {
                    queue.addLast(landing);
                }
            }
            if (cells.size() + connectors.size() > maxSize) {
                return SelectedFloorScan.failure(Building.validationResult.BLOCK_LIMIT);
            }
        }

        FloorGeometry floor = new FloorGeometry(cells.values().stream()
                .map(SurfaceCell::canonical).collect(java.util.stream.Collectors.toSet()), Map.of());
        return new SelectedFloorScan(Building.validationResult.SUCCESS, floor,
                Set.copyOf(transitions), Set.copyOf(verticalBoundaryCells),
                Set.copyOf(adjacentFloorSeeds), Set.copyOf(connectors));
    }

    private static List<HorizontalStep> enclosedBoundaryContinuations(
            Level world,
            SurfaceCell boundary,
            BlockPos scanAnchor,
            FloorCeilingResolver ceilings,
            int maxRadius,
            StepProvider provider,
            FloorBandClassifier classifier,
            Set<BlockPos> queued) {
        return provider.steps(boundary).stream()
                .filter(step -> step.connector() == null)
                .filter(step -> !queued.contains(step.landing().feet()))
                .filter(step -> !reachesExterior(
                        world, step.landing(), scanAnchor, ceilings, maxRadius, provider, classifier))
                .toList();
    }

    private static SurfaceCell resolveSelectedFloorAnchor(
            Level world, SurfaceCell seed, StepProvider provider) {
        SurfaceCell current = seed;
        Set<BlockPos> visited = new HashSet<>();
        while (visited.add(current.feet())
                && (isDescendingTransitionSeed(world, current, provider)
                || stableSameHeightPeerCount(current, provider) == 0)) {
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

    private static boolean isDescendingTransitionSeed(
            Level world, SurfaceCell cell, StepProvider provider) {
        if (isExplicitStairTransitionCell(world, cell.feet())) return true;
        return isDescendingFloorBoundary(
                cell, new FloorBand(cell.feet().getY()), provider);
    }

    private static boolean isDescendingFloorBoundary(
            SurfaceCell cell, FloorBand band, StepProvider provider) {
        return hasDescendingStep(cell, provider)
                && descendsBelowFloorBand(cell, band, provider)
                && stableSameHeightPeerCount(cell, provider) < MIN_STABLE_LANDING_PEERS;
    }

    private static boolean isExplicitStairTransitionCell(Level world, BlockPos feet) {
        return world.getBlockState(feet).getBlock() instanceof StairBlock
                || world.getBlockState(feet.below()).getBlock() instanceof StairBlock;
    }

    private static long stableSameHeightPeerCount(SurfaceCell cell, StepProvider provider) {
        return provider.steps(cell).stream()
                .filter(step -> step.connector() == null)
                .map(HorizontalStep::landing)
                .filter(peer -> peer.feet().getY() == cell.feet().getY())
                .filter(peer -> !hasDescendingStep(peer, provider))
                .map(SurfaceCell::feet)
                .distinct()
                .count();
    }

    private static SelectedFloorScan retainEnclosedRegions(
            Level world,
            SurfaceCell seed,
            SelectedFloorScan selected,
            FloorCeilingResolver ceilings,
            int maxRadius,
            StepProvider provider,
            FloorBandClassifier classifier) {
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
        Set<BlockPos> exteriorCells = new HashSet<>();

        for (Set<BlockPos> region : regions) {
            BlockPos representative = region.stream()
                    .min(CELL_ORDER)
                    .orElseThrow();
            SurfaceCell regionSeed = inspectSurfaceCell(world, representative, ceilings).orElse(null);
            if (regionSeed != null && reachesExterior(
                    world, regionSeed, seed.feet(), ceilings, maxRadius, provider, classifier)) {
                exteriorCells.addAll(region);
            }
        }

        if (exteriorCells.contains(seed.feet())) {
            return SelectedFloorScan.failure(Building.validationResult.NOT_IN_BUILDING);
        }

        Set<BlockPos> allowed = new HashSet<>(floorCells);
        allowed.removeAll(exteriorCells);
        Set<BlockPos> reachable = connectedCells(seed.feet(), allowed, neighbors, new HashSet<>());
        if (reachable.isEmpty()) {
            return SelectedFloorScan.failure(Building.validationResult.NOT_IN_BUILDING);
        }

        Set<FloorGeometry.Cell> cells = selected.floor().cells().stream()
                .filter(cell -> reachable.contains(cell.feet()))
                .collect(java.util.stream.Collectors.toSet());
        Set<Transition> transitions = selected.transitions().stream()
                .filter(transition -> reachable.contains(transition.first())
                        && reachable.contains(transition.second()))
                .collect(java.util.stream.Collectors.toSet());
        Set<BlockPos> verticalBoundaryCells = selected.verticalBoundaryCells().stream()
                .filter(reachable::contains)
                .collect(java.util.stream.Collectors.toSet());
        Set<BlockPos> connectors = selected.connectors().stream()
                .filter(connector -> reachable.contains(StructureConnector.normalize(
                        connector, world.getBlockState(connector))))
                .collect(java.util.stream.Collectors.toSet());
        Set<BlockPos> adjacentFloorSeeds = adjacentFloorSeedsForRetainedFloor(
                world, ceilings, reachable, provider, classifier);
        SelectedFloorScan retainedFloor = new SelectedFloorScan(
                Building.validationResult.SUCCESS,
                new FloorGeometry(cells, Map.of()),
                transitions,
                verticalBoundaryCells,
                adjacentFloorSeeds,
                connectors);
        return retainedFloor;
    }

    private static Set<BlockPos> adjacentFloorSeedsForRetainedFloor(
            Level world,
            FloorCeilingResolver ceilings,
            Set<BlockPos> retained,
            StepProvider provider,
            FloorBandClassifier classifier) {
        LinkedHashSet<BlockPos> adjacentFloorSeeds = new LinkedHashSet<>();
        for (BlockPos pos : retained) {
            SurfaceCell current = inspectSurfaceCell(world, pos, ceilings).orElse(null);
            if (current == null) continue;
            FloorBandRole currentRole = classifier.role(current);
            for (HorizontalStep step : provider.steps(current)) {
                SurfaceCell candidate = step.landing();
                if (currentRole == FloorBandRole.EDGE) {
                    if (!retained.contains(candidate.feet())) adjacentFloorSeeds.add(candidate.feet());
                } else if (classifier.role(candidate) == FloorBandRole.OTHER) {
                    adjacentFloorSeeds.add(candidate.feet());
                }
            }
        }
        return Set.copyOf(adjacentFloorSeeds);
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

    private static FloorBandRole floorBandRole(Level world,
                                         FloorBand band,
                                         SurfaceCell candidate,
                                         StepProvider provider) {
        int y = candidate.feet().getY();
        if (y < band.minOwnedY()
                || y > band.maxOwnedY() + 1) return FloorBandRole.OTHER;
        if (y == band.maxOwnedY() + 1) return FloorBandRole.EDGE;
        if (y == band.maxOwnedY()
                && stairTransitionReachesBelowBand(world, candidate, provider)) return FloorBandRole.EDGE;
        if (y == band.anchorY()
                && (isDescendingFloorBoundary(candidate, band, provider)
                || stairTransitionReachesBelowBand(world, candidate, provider))) return FloorBandRole.OTHER;
        return FloorBandRole.OWNED;
    }

    private static boolean stairTransitionReachesBelowBand(
            Level world, SurfaceCell start, StepProvider provider) {
        if (!isExplicitStairTransitionCell(world, start.feet())) return false;
        int boundaryY = start.feet().getY() - FLOOR_BAND_RADIUS;
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
                if (nextY >= current.feet().getY()) continue;
                if (nextY <= boundaryY) return true;
                if (visited.add(next.feet())) queue.addLast(next);
            }
        }
        return false;
    }

    private static boolean descendsBelowFloorBand(
            SurfaceCell start, FloorBand band, StepProvider provider) {
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
                if (nextY < band.minOwnedY()) return true;
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
            FloorCeilingResolver ceilings,
            int maxRadius,
            StepProvider provider,
            FloorBandClassifier classifier) {
        Boolean known = classifier.exterior.get(start.feet());
        if (known != null) return known;
        ArrayDeque<SurfaceCell> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.addLast(start);
        visited.add(start.feet());

        while (!queue.isEmpty()) {
            SurfaceCell current = queue.removeFirst();
            if (horizontalDistance(current.feet(), scanAnchor) >= maxRadius - 1) {
                classifier.exterior.put(start.feet(), true);
                return true;
            }
            Boolean reachable = classifier.exterior.get(current.feet());
            if (Boolean.TRUE.equals(reachable)) {
                classifier.exterior.put(start.feet(), true);
                return true;
            }
            if (Boolean.FALSE.equals(reachable)) continue;
            if (hasOpenAirEscape(world, current, scanAnchor, maxRadius)) {
                classifier.exterior.put(start.feet(), true);
                return true;
            }

            for (PhysicalStep step : physicalSteps(world, new SurfaceProbe(current.feet(), current.surfaceY()))) {
                if (step.connector() != null) continue;
                SurfaceProbe landing = step.landing();
                BlockPos next = landing.feet();
                if (visited.contains(next)) continue;
                SurfaceCell probe = new SurfaceCell(next, landing.surfaceY(), next.getY() + 2);
                if (classifier.role(probe) != FloorBandRole.OWNED) continue;
                OptionalInt ceiling = ceilings.ceilingY(next);
                if (ceiling.isEmpty()) {
                    // Edges can be directional: only the start is proven to reach this exit.
                    classifier.exterior.put(start.feet(), true);
                    return true;
                }
                SurfaceCell cell = new SurfaceCell(next, landing.surfaceY(), ceiling.getAsInt());
                visited.add(next);
                queue.addLast(cell);
            }
        }
        // Exhausting the traversal proves every visited cell has no route outside.
        visited.forEach(cell -> classifier.exterior.put(cell, false));
        return false;
    }

    private static boolean hasOpenAirEscape(
            Level world, SurfaceCell current, BlockPos scanAnchor, int maxRadius) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        for (Direction direction : HORIZONTAL) {
            BlockPos opening = current.feet().relative(direction);
            if (!findLandings(world, current.surfaceY(), opening).isEmpty()) continue;
            if (isOpenPassage(world, opening) && visited.add(opening)) queue.addLast(opening);
        }

        while (!queue.isEmpty()) {
            BlockPos open = queue.removeFirst();
            if (horizontalDistance(open, scanAnchor) >= maxRadius - 1) return true;
            for (Direction direction : HORIZONTAL) {
                BlockPos next = open.relative(direction);
                if (visited.contains(next) || !isOpenPassage(world, next)) continue;
                if (horizontalDistance(next, scanAnchor) >= maxRadius) continue;
                visited.add(next);
                queue.addLast(next);
            }
        }
        return false;
    }

    private static boolean isOpenPassage(Level world, BlockPos feet) {
        return isOpen(world, feet) && isOpen(world, feet.above());
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
        if (!isTraversalOccupancyAllowed(world, feet) || !hasInteriorHeadroom(world, feet)) {
            return OptionalDouble.empty();
        }
        return supportedFloorLevel(world, feet);
    }

    private static OptionalDouble supportedFloorLevel(Level world, BlockPos feet) {
        BlockPos support = feet.below();
        var shape = world.getBlockState(support).getCollisionShape(world, support);
        if (shape.isEmpty()) return OptionalDouble.empty();
        double width = shape.max(Direction.Axis.X) - shape.min(Direction.Axis.X);
        double depth = shape.max(Direction.Axis.Z) - shape.min(Direction.Axis.Z);
        if (width * depth < 0.25D) return OptionalDouble.empty();
        return OptionalDouble.of(WalkNodeEvaluator.getFloorLevel(world, feet));
    }

    /** Traversal may cross open cells and low furniture without redefining the structural floor. */
    private static boolean isTraversalOccupancyAllowed(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) return false;
        if (StructureConnector.isHorizontalBoundary(state)) return true;
        var shape = state.getCollisionShape(world, pos);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) < 1.0D;
    }

    private static OptionalDouble interactionHandoffY(
            Level world, BlockPos pos, FloorCeilingResolver ceilings) {
        if (!isVerticalConnectorTopExit(world, pos)) return OptionalDouble.empty();
        if (inspectSurfaceCell(world, pos.above(), ceilings).isPresent()) return OptionalDouble.empty();
        return OptionalDouble.of(pos.getY());
    }

    private static boolean isVerticalConnectorTopExit(Level world, BlockPos pos) {
        if (!isOpen(world, pos)) return false;
        BlockPos connector = StructureConnector.verticalInteractionConnector(world, pos);
        return connector != null
                && connector.equals(pos.below())
                && StructureConnector.isVertical(world, connector);
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
        return isTraversalOccupancyAllowed(world, pos) && !isOpen(world, pos);
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
                                  FloorGeometry geometry,
                                  Set<Transition> transitions,
                                  Set<BlockPos> verticalBoundaryCells,
                                  Set<BlockPos> adjacentFloorSeeds) {
        FloorGeometry.Bounds bounds = FloorGeometry.bounds(geometry.cells(), -1);
        return new Result(Building.validationResult.SUCCESS, geometry,
                bounds.min(), bounds.max(),
                transitions, verticalBoundaryCells, adjacentFloorSeeds);
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

    private enum FloorBandRole {
        OWNED, EDGE, OTHER
    }

    private record FloorBand(int anchorY) {
        int minOwnedY() {
            return anchorY - FLOOR_BAND_RADIUS;
        }

        int maxOwnedY() {
            return anchorY + FLOOR_BAND_RADIUS;
        }
    }

    private static final class FloorBandClassifier {
        private final Level world;
        private final FloorBand band;
        private final StepProvider provider;
        private final Map<BlockPos, FloorBandRole> roles = new HashMap<>();
        private final Map<BlockPos, Boolean> exterior = new HashMap<>();

        private FloorBandClassifier(Level world, FloorBand band, StepProvider provider) {
            this.world = world;
            this.band = band;
            this.provider = provider;
        }

        private FloorBandRole role(SurfaceCell cell) {
            return roles.computeIfAbsent(
                    cell.feet(), ignored -> floorBandRole(world, band, cell, provider));
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

    record SelectedFloorScan(Building.validationResult result,
                      FloorGeometry floor,
                      Set<Transition> transitions,
                      Set<BlockPos> verticalBoundaryCells,
                      Set<BlockPos> adjacentFloorSeeds,
                      Set<BlockPos> connectors) {
        SelectedFloorScan {
            transitions = transitions == null ? Set.of() : Set.copyOf(transitions);
            verticalBoundaryCells = verticalBoundaryCells == null ? Set.of() : Set.copyOf(verticalBoundaryCells);
            adjacentFloorSeeds = adjacentFloorSeeds == null ? Set.of() : Set.copyOf(adjacentFloorSeeds);
            connectors = connectors == null ? Set.of() : Set.copyOf(connectors);
        }

        static SelectedFloorScan failure(Building.validationResult result) {
            return new SelectedFloorScan(result, null, Set.of(), Set.of(), Set.of(), Set.of());
        }
    }

    private record SurfaceProbe(BlockPos feet, double surfaceY) {
    }

    record Result(Building.validationResult result,
                  FloorGeometry floor,
                  BlockPos min,
                  BlockPos max,
                  Set<Transition> transitions,
                  Set<BlockPos> verticalBoundaryCells,
                  Set<BlockPos> adjacentFloorSeeds) {
        Result {
            transitions = transitions == null ? Set.of() : Set.copyOf(transitions);
            verticalBoundaryCells = verticalBoundaryCells == null ? Set.of() : Set.copyOf(verticalBoundaryCells);
            adjacentFloorSeeds = adjacentFloorSeeds == null ? Set.of() : Set.copyOf(adjacentFloorSeeds);
        }

        static Result failure(Building.validationResult result, BlockPos source) {
            return new Result(result, null, source, source,
                    Set.of(), Set.of(), Set.of());
        }
    }
}
