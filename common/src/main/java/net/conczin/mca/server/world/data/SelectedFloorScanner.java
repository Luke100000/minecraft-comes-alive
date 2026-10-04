package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
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
import java.util.stream.Collectors;

/** Discovers one exact selected Floor while using Minecraft surface heights only for movement. */
final class SelectedFloorScanner {
    private static final double MAX_STEP_HEIGHT = 1.125D;
    private static final int FLOOR_BAND_RADIUS = 2;
    // Full-block stairs have no block metadata marking where the staircase ends. One stable
    // same-height neighbor is enough to prove that the supported cell joins a landing/plateau.
    private static final int MIN_STABLE_LANDING_PEERS = 1;
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
        private final StairFlightOwnership stairFlights;
        private final Map<BlockPos, Result> resolutions = new HashMap<>();

        Observation(Level world, int maxSize, int maxRadius) {
            this.world = world;
            this.maxSize = maxSize;
            this.maxRadius = maxRadius;
            this.ceilings = new FloorCeilingResolver(world);
            Map<BlockPos, List<HorizontalStep>> steps = new HashMap<>();
            this.provider = cell -> steps.computeIfAbsent(
                    cell.feet(), ignored -> worldSteps(world, cell, ceilings));
            this.stairFlights = new StairFlightOwnership(world, provider, maxSize, maxRadius);
        }

        private Result resolve(BlockPos seed) {
            return resolutions.computeIfAbsent(seed.immutable(), requested -> {
                SurfaceCell supported = resolveSeedCell(world, requested, ceilings).orElse(null);
                SurfaceCell traversalSeed = supported == null
                        ? adjacentTraversalSeed(world, requested, ceilings).orElse(null) : supported;
                if (traversalSeed == null) {
                    return Result.failure(Building.validationResult.NOT_IN_BUILDING, requested);
                }

                return resolveSelectedFloor(
                        world, traversalSeed, supported == null ? null : requested,
                        ceilings, provider, stairFlights, maxSize, maxRadius);
            });
        }

        Result selected(BlockPos seed) {
            return resolve(seed);
        }
    }

    private static Result resolveSelectedFloor(
            Level world,
            SurfaceCell requestedSurface,
            BlockPos supportedSource,
            FloorCeilingResolver ceilings,
            StepProvider provider,
            StairFlightOwnership stairFlights,
            int maxSize,
            int maxRadius) {
        SurfaceCell stairOwner = stairFlights.owner(requestedSurface);
        SurfaceCell traversalSeed = stairOwner != null ? stairOwner
                : resolveSelectedFloorAnchor(requestedSurface, provider);
        Result selected = scanSelectedFloor(world, requestedSurface, traversalSeed, supportedSource,
                ceilings, provider, stairFlights, maxSize, maxRadius);
        if (selected.floor() == null
                || stairOwner == null && !isExplicitStairTransitionCell(world, requestedSurface.feet())) {
            return selected;
        }

        int anchorY = selected.floor().anchorY();
        if (anchorY == traversalSeed.feet().getY() || !canStep(traversalSeed.surfaceY(), anchorY)) return selected;
        // A flight can end a step below its room. Use the discovered main surface for the final
        // band, otherwise selecting that flight can clip stairs along the higher landing's edge.
        SurfaceCell mainSurface = selected.floor().cells().stream()
                .map(FloorGeometry.Cell::feet)
                .filter(pos -> pos.getY() == anchorY)
                .sorted(CELL_ORDER)
                .map(pos -> inspectSurfaceCell(world, pos, ceilings))
                .flatMap(Optional::stream)
                .findFirst().orElse(null);
        if (mainSurface == null) return selected;
        return scanSelectedFloor(world, requestedSurface, mainSurface, supportedSource,
                ceilings, provider, stairFlights, maxSize, maxRadius);
    }

    private static Result scanSelectedFloor(
            Level world,
            SurfaceCell requestedSurface,
            SurfaceCell traversalSeed,
            BlockPos supportedSource,
            FloorCeilingResolver ceilings,
            StepProvider provider,
            StairFlightOwnership stairFlights,
            int maxSize,
            int maxRadius) {
        FloorBandClassifier classifier = new FloorBandClassifier(
                world, new FloorBand(traversalSeed.feet().getY()), provider, stairFlights);
        TraversalScan traversal = traverseSelectedFloor(
                world, traversalSeed, ceilings, maxSize, maxRadius, provider, classifier);
        if (traversal.result() != Building.validationResult.SUCCESS) {
            return Result.failure(traversal.result(), traversalSeed.feet());
        }
        traversal = retainEnclosedRegions(
                world, traversalSeed, traversal, ceilings, maxRadius, provider, classifier);
        if (traversal.result() != Building.validationResult.SUCCESS) {
            return Result.failure(traversal.result(), traversalSeed.feet());
        }
        MaterializedFloor materialized = materializeStructuralFloor(world, traversal, ceilings, classifier);

        LinkedHashSet<BlockPos> connectors = new LinkedHashSet<>(traversal.connectors());
        for (FloorGeometry.Cell cell : materialized.floor().cells()) {
            collectVerticalConnectors(world, cell, connectors);
        }
        if (materialized.floor().cells().size() + connectors.size() > maxSize) {
            return Result.failure(Building.validationResult.BLOCK_LIMIT, traversalSeed.feet());
        }
        FloorGeometry floor = new FloorGeometry(materialized.floor().cells(),
                StructureConnector.connectorMarkersForFloor(world, connectors, materialized.floor()));
        BlockPos selectedCell = floor.cellAt(requestedSurface.feet()).isPresent()
                ? requestedSurface.feet() : traversalSeed.feet();
        return success(selectedCell, supportedSource, floor,
                materialized.transitions(), traversal.verticalBoundaryCells(), traversal.adjacentFloorSeeds());
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

    private static TraversalScan traverseSelectedFloor(Level world,
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
                return TraversalScan.failure(Building.validationResult.SIZE_LIMIT);
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
                FloorBandDecision decision = classifier.decision(landing);
                if (!decision.owned()) {
                    adjacentFloorSeeds.add(landing.feet());
                    continue;
                }
                transitions.add(new Transition(current.feet(), landing.feet()));
                if (!decision.continueTraversal()) {
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
                return TraversalScan.failure(Building.validationResult.BLOCK_LIMIT);
            }
        }

        return new TraversalScan(Building.validationResult.SUCCESS, cells,
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
            SurfaceCell seed, StepProvider provider) {
        SurfaceCell current = seed;
        Set<BlockPos> visited = new HashSet<>();
        while (visited.add(current.feet())
                && (isDescendingFloorBoundary(
                        current, new FloorBand(current.feet().getY()), provider)
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

    private static TraversalScan retainEnclosedRegions(
            Level world,
            SurfaceCell seed,
            TraversalScan traversal,
            FloorCeilingResolver ceilings,
            int maxRadius,
            StepProvider provider,
            FloorBandClassifier classifier) {
        Set<BlockPos> traversalCells = traversal.cells().keySet();
        Set<BlockPos> boundaryCells = traversal.connectors().stream()
                .filter(traversalCells::contains)
                .collect(Collectors.toSet());
        Map<BlockPos, List<BlockPos>> neighbors = Transition.neighborIndex(traversal.transitions());
        List<Set<BlockPos>> regions = connectedRegions(traversalCells, boundaryCells, neighbors);
        Set<BlockPos> exteriorCells = new HashSet<>();

        for (Set<BlockPos> region : regions) {
            BlockPos representative = region.stream()
                    .min(CELL_ORDER)
                    .orElseThrow();
            SurfaceCell regionSeed = traversal.cells().get(representative);
            if (reachesExterior(
                    world, regionSeed, seed.feet(), ceilings, maxRadius, provider, classifier)) {
                exteriorCells.addAll(region);
            }
        }

        if (exteriorCells.contains(seed.feet())) {
            return TraversalScan.failure(Building.validationResult.NOT_IN_BUILDING);
        }

        Set<BlockPos> allowed = new HashSet<>(traversalCells);
        allowed.removeAll(exteriorCells);
        Set<BlockPos> reachable = connectedCells(seed.feet(), allowed, neighbors, new HashSet<>());
        if (reachable.isEmpty()) {
            return TraversalScan.failure(Building.validationResult.NOT_IN_BUILDING);
        }

        Map<BlockPos, SurfaceCell> retainedCells = new HashMap<>();
        for (BlockPos pos : reachable) {
            retainedCells.put(pos, traversal.cells().get(pos));
        }
        Set<Transition> transitions = traversal.transitions().stream()
                .filter(transition -> reachable.contains(transition.first())
                        && reachable.contains(transition.second()))
                .collect(Collectors.toSet());
        Set<BlockPos> verticalBoundaryCells = traversal.verticalBoundaryCells().stream()
                .filter(reachable::contains)
                .collect(Collectors.toSet());
        Set<BlockPos> connectors = traversal.connectors().stream()
                .filter(connector -> reachable.contains(StructureConnector.normalize(
                        connector, world.getBlockState(connector))))
                .collect(Collectors.toSet());
        Set<BlockPos> adjacentFloorSeeds = adjacentFloorSeedsForRetainedFloor(
                world, ceilings, reachable, provider, classifier);
        return new TraversalScan(
                Building.validationResult.SUCCESS,
                retainedCells,
                transitions,
                verticalBoundaryCells,
                adjacentFloorSeeds,
                connectors);
    }

    /**
     * Traversal proves enclosure; it is not the canonical Floor. Build structural ownership once
     * from the retained traversal evidence, including supported interior columns occupied by blocks.
     * Ambiguous full-block head obstructions need independent surrounding floor evidence so a
     * one-block wall cavity cannot manufacture Floor ownership.
     */
    private static MaterializedFloor materializeStructuralFloor(
            Level world,
            TraversalScan traversal,
            FloorCeilingResolver ceilings,
            FloorBandClassifier classifier) {
        LinkedHashMap<BlockPos, SurfaceCell> floorCells = new LinkedHashMap<>();
        Set<Long> ownedColumns = new HashSet<>();
        LinkedHashSet<Transition> transitions = new LinkedHashSet<>(traversal.transitions());
        ArrayDeque<SurfaceCell> queue = new ArrayDeque<>();
        Set<BlockPos> structuralOnlyCells = new HashSet<>();

        for (SurfaceCell traversalCell : traversal.cells().values().stream()
                .sorted(Comparator.comparing(SurfaceCell::feet, CELL_ORDER))
                .toList()) {
            floorCells.put(traversalCell.feet(), traversalCell);
            ownedColumns.add(FloorGeometry.columnKey(
                    traversalCell.feet().getX(), traversalCell.feet().getZ()));
            queue.addLast(traversalCell);
        }

        while (!queue.isEmpty()) {
            SurfaceCell current = queue.removeFirst();
            for (Direction direction : HORIZONTAL) {
                BlockPos candidatePos = current.feet().relative(direction);
                SurfaceCell existing = floorCells.get(candidatePos);
                if (existing != null) {
                    if (structuralOnlyCells.contains(current.feet())) {
                        transitions.add(new Transition(current.feet(), existing.feet()));
                    }
                    continue;
                }
                long candidateColumn = FloorGeometry.columnKey(candidatePos.getX(), candidatePos.getZ());
                if (ownedColumns.contains(candidateColumn)) continue;
                SurfaceCell candidate = resolveStructuralNeighbor(
                        world, candidatePos, current, ceilings, classifier, traversal.cells()).orElse(null);
                if (candidate == null) continue;

                floorCells.put(candidatePos, candidate);
                ownedColumns.add(candidateColumn);
                structuralOnlyCells.add(candidatePos);
                transitions.add(new Transition(current.feet(), candidatePos));
                queue.addLast(candidate);
            }
        }

        return new MaterializedFloor(
                new FloorGeometry(floorCells.values().stream()
                        .map(SurfaceCell::canonical)
                        .toList(), Map.of()),
                Set.copyOf(transitions));
    }

    private static Optional<SurfaceCell> resolveStructuralNeighbor(
            Level world,
            BlockPos feet,
            SurfaceCell adjacent,
            FloorCeilingResolver ceilings,
            FloorBandClassifier classifier,
            Map<BlockPos, SurfaceCell> enclosedCells) {
        // A climbable opening belongs to the enclosed Floor beside it even though the ladder's
        // narrow collision shape is not a walking surface. It stays out of traversal discovery.
        if (StructureConnector.isVerticalTopExit(world, feet)) {
            OptionalDouble handoffY = interactionHandoffY(world, feet, ceilings);
            OptionalInt ceilingY = ceilings.ceilingY(feet);
            if (handoffY.isEmpty() || ceilingY.isEmpty() || !hasInteriorHeadroom(world, feet)) {
                return Optional.empty();
            }
            return Optional.of(new SurfaceCell(feet, handoffY.getAsDouble(),
                    Math.min(ceilingY.getAsInt(), adjacent.ceilingY())));
        }
        BlockState state = world.getBlockState(feet);
        if (!state.getFluidState().isEmpty()
                || StructureConnector.isConnector(state)
                || isExplicitStairTransitionCell(world, feet)) {
            return Optional.empty();
        }

        OptionalDouble surfaceY = floorSurfaceLevel(world, feet);
        if (surfaceY.isEmpty() || !canStep(adjacent.surfaceY(), surfaceY.getAsDouble())) {
            return Optional.empty();
        }

        if (isTraversalOccupancyAllowed(world, feet)) {
            if (hasInteriorHeadroom(world, feet)) return Optional.empty();
            BlockPos head = feet.above();
            if (world.getBlockState(head).isCollisionShapeFullBlock(world, head)
                    && !hasSurroundingFloorEvidence(
                    world, feet, surfaceY.getAsDouble(), enclosedCells)) {
                return Optional.empty();
            }
            return Optional.of(new SurfaceCell(
                    feet, surfaceY.getAsDouble(), adjacent.ceilingY()));
        }

        for (int y = feet.getY() + 1; y < adjacent.ceilingY(); y++) {
            BlockPos interior = new BlockPos(feet.getX(), y, feet.getZ());
            if (!isOpen(world, interior)) continue;
            if (!hasInteriorHeadroom(world, interior)
                    && !hasSurroundingFloorEvidence(world, feet, surfaceY.getAsDouble(), enclosedCells)) {
                continue;
            }
            OptionalInt ceilingY = ceilings.ceilingY(interior);
            if (ceilingY.isPresent()) {
                int localCeilingY = Math.min(ceilingY.getAsInt(), adjacent.ceilingY());
                SurfaceCell topSurface = inspectSurfaceCell(world, interior, ceilings).orElse(null);
                if (topSurface != null && !classifier.decision(topSurface).owned()) {
                    localCeilingY = interior.getY();
                }
                return Optional.of(new SurfaceCell(feet, surfaceY.getAsDouble(), localCeilingY));
            }
        }
        return Optional.empty();
    }

    private static boolean hasSurroundingFloorEvidence(
            Level world,
            BlockPos feet,
            double surfaceY,
            Map<BlockPos, SurfaceCell> enclosedCells) {
        int neighbors = 0;
        for (Direction direction : HORIZONTAL) {
            BlockPos horizontal = feet.relative(direction);
            boolean ownedNeighbor = false;
            for (SurfaceProbe landing : findLandings(world, surfaceY, horizontal)) {
                // A geometrically compatible cell across a solid wall is not evidence for this Floor.
                if (enclosedCells.containsKey(landing.feet())) {
                    ownedNeighbor = true;
                    break;
                }
            }
            if (ownedNeighbor && ++neighbors >= 2) return true;
        }
        return false;
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
            FloorBandDecision currentDecision = classifier.decision(current);
            for (HorizontalStep step : provider.steps(current)) {
                SurfaceCell candidate = step.landing();
                if (!currentDecision.continueTraversal()) {
                    if (!retained.contains(candidate.feet())) adjacentFloorSeeds.add(candidate.feet());
                } else if (!classifier.decision(candidate).owned()) {
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

    /** Surfaces without a proven flight retain the ordinary uneven-floor enclosure rules. */
    private static FloorBandDecision floorBandDecision(Level world,
                                                       FloorBand band,
                                                       SurfaceCell candidate,
                                                       StepProvider provider) {
        int y = candidate.feet().getY();
        if (y < band.minOwnedY()
                || y > band.maxOwnedY()) return FloorBandDecision.ADJACENT;

        boolean stairTransition = stairTransitionReachesBelowBand(world, candidate, provider);
        if (y > band.anchorY()
                && stairTransition
                && stableSameHeightPeerCount(candidate, provider) >= MIN_STABLE_LANDING_PEERS) {
            return FloorBandDecision.ADJACENT;
        }
        if (stairTransition
                && (y == band.maxOwnedY() || y == band.anchorY())) {
            return FloorBandDecision.OWNED_BOUNDARY;
        }
        if (y == band.anchorY()
                && isDescendingFloorBoundary(candidate, band, provider)) {
            return FloorBandDecision.ADJACENT;
        }
        return FloorBandDecision.OWNED;
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
        OptionalDouble surfaceY = floorSurfaceY(world, feet);
        if (surfaceY.isEmpty()) return Optional.empty();
        OptionalInt ceiling = ceilings.ceilingY(feet);
        if (ceiling.isEmpty()) return Optional.empty();
        return Optional.of(new SurfaceCell(feet, surfaceY.getAsDouble(), ceiling.getAsInt()));
    }

    private static List<HorizontalStep> worldSteps(
            Level world, SurfaceCell current, FloorCeilingResolver ceilings) {
        List<HorizontalStep> steps = new ArrayList<>();
        for (FloorStep step : floorSteps(world, new SurfaceProbe(current.feet(), current.surfaceY()))) {
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

    private static List<FloorStep> floorSteps(Level world, SurfaceProbe current) {
        List<FloorStep> steps = new ArrayList<>();
        for (Direction direction : HORIZONTAL) {
            BlockPos horizontal = current.feet().relative(direction);
            BlockPos connector = null;
            for (int dy : LANDING_Y_OFFSETS) {
                BlockPos candidate = horizontal.offset(0, dy, 0);
                BlockState state = world.getBlockState(candidate);
                if (StructureConnector.isHorizontalBoundary(state)) {
                    connector = StructureConnector.normalize(candidate, state);
                    break;
                }
            }
            if (connector != null) {
                OptionalDouble surfaceY = floorSurfaceY(world, connector);
                if (surfaceY.isPresent() && canStep(current.surfaceY(), surfaceY.getAsDouble())) {
                    steps.add(new FloorStep(new SurfaceProbe(connector, surfaceY.getAsDouble()), connector));
                }
            } else {
                for (SurfaceProbe landing : findLandings(world, current.surfaceY(), horizontal)) {
                    steps.add(new FloorStep(landing, null));
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
            OptionalDouble surfaceY = floorSurfaceY(world, feet);
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
        ArrayDeque<SurfaceCell> floorQueue = new ArrayDeque<>();
        ArrayDeque<BlockPos> openQueue = new ArrayDeque<>();
        Set<BlockPos> visitedFloor = new HashSet<>();
        Set<BlockPos> visitedOpen = new HashSet<>();
        floorQueue.addLast(start);
        visitedFloor.add(start.feet());

        while (!floorQueue.isEmpty() || !openQueue.isEmpty()) {
            if (!openQueue.isEmpty()) {
                BlockPos open = openQueue.removeFirst();
                if (horizontalDistance(open, scanAnchor) >= maxRadius - 1) {
                    classifier.exterior.put(start.feet(), true);
                    return true;
                }
                for (Direction direction : HORIZONTAL) {
                    BlockPos next = open.relative(direction);
                    if (visitedOpen.contains(next) || !isOpenPassage(world, next)) continue;
                    if (horizontalDistance(next, scanAnchor) >= maxRadius) continue;
                    visitedOpen.add(next);
                    openQueue.addLast(next);
                }
                continue;
            }

            SurfaceCell current = floorQueue.removeFirst();
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
            for (Direction direction : HORIZONTAL) {
                BlockPos opening = current.feet().relative(direction);
                if (!findLandings(world, current.surfaceY(), opening).isEmpty()) continue;
                if (isOpenPassage(world, opening) && visitedOpen.add(opening)) {
                    openQueue.addLast(opening);
                }
            }

            for (FloorStep step : floorSteps(world, new SurfaceProbe(current.feet(), current.surfaceY()))) {
                if (step.connector() != null) continue;
                SurfaceProbe landing = step.landing();
                BlockPos next = landing.feet();
                if (visitedFloor.contains(next)) continue;
                SurfaceCell probe = new SurfaceCell(next, landing.surfaceY(), next.getY() + 2);
                FloorBandDecision decision = classifier.decision(probe);
                if (!decision.owned() || !decision.continueTraversal()) continue;
                OptionalInt ceiling = ceilings.ceilingY(next);
                if (ceiling.isEmpty()) {
                    // Edges can be directional: only the start is proven to reach this exit.
                    classifier.exterior.put(start.feet(), true);
                    return true;
                }
                SurfaceCell cell = new SurfaceCell(next, landing.surfaceY(), ceiling.getAsInt());
                visitedFloor.add(next);
                floorQueue.addLast(cell);
            }
        }
        // Exhausting the traversal proves every visited cell has no route outside.
        visitedFloor.forEach(cell -> classifier.exterior.put(cell, false));
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

    private static OptionalDouble floorSurfaceY(Level world, BlockPos feet) {
        if (!isTraversalOccupancyAllowed(world, feet) || !hasInteriorHeadroom(world, feet)) {
            return OptionalDouble.empty();
        }
        return floorSurfaceLevel(world, feet);
    }

    private static OptionalDouble floorSurfaceLevel(Level world, BlockPos feet) {
        BlockPos support = feet.below();
        BlockState supportState = world.getBlockState(support);
        var shape = supportState.getCollisionShape(world, support);
        if (shape.isEmpty()) return OptionalDouble.empty();
        double width = shape.max(Direction.Axis.X) - shape.min(Direction.Axis.X);
        double depth = shape.max(Direction.Axis.Z) - shape.min(Direction.Axis.Z);
        if (width * depth < 0.25D) {
            // Vanilla makes an open trapdoor pathfindable and rotates its collision vertically.
            // FloorGeometry models structural ownership, so opening a hatch must not erase the
            // canonical exit cell even though it is no longer a horizontal support surface.
            if (supportState.getBlock() instanceof TrapDoorBlock
                    && supportState.getValue(TrapDoorBlock.OPEN)) {
                return OptionalDouble.of(feet.getY());
            }
            return OptionalDouble.empty();
        }
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
        if (!StructureConnector.isVerticalTopExit(world, pos)) return OptionalDouble.empty();
        if (inspectSurfaceCell(world, pos.above(), ceilings).isPresent()) return OptionalDouble.empty();
        return OptionalDouble.of(pos.getY());
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
                                  BlockPos supportedSource,
                                  FloorGeometry geometry,
                                  Set<Transition> transitions,
                                  Set<BlockPos> verticalBoundaryCells,
                                  Set<BlockPos> adjacentFloorSeeds) {
        FloorGeometry.Bounds bounds = FloorGeometry.bounds(geometry.cells(), -1);
        return new Result(Building.validationResult.SUCCESS, geometry, seed, supportedSource,
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

    private record FloorBandDecision(boolean owned, boolean continueTraversal) {
        private static final FloorBandDecision OWNED = new FloorBandDecision(true, true);
        private static final FloorBandDecision OWNED_BOUNDARY = new FloorBandDecision(true, false);
        private static final FloorBandDecision ADJACENT = new FloorBandDecision(false, false);
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
        private final StairFlightOwnership stairFlights;
        private final Map<BlockPos, FloorBandDecision> decisions = new HashMap<>();
        private final Map<BlockPos, Boolean> exterior = new HashMap<>();

        private FloorBandClassifier(Level world, FloorBand band, StepProvider provider,
                                    StairFlightOwnership stairFlights) {
            this.world = world;
            this.band = band;
            this.provider = provider;
            this.stairFlights = stairFlights;
        }

        private FloorBandDecision decision(SurfaceCell cell) {
            return decisions.computeIfAbsent(cell.feet(), ignored -> {
                SurfaceCell owner = stairFlights.owner(cell);
                if (owner != null) {
                    // A flight's landing can be one walking step below the room's main surface.
                    return canStep(band.anchorY(), owner.surfaceY())
                            ? FloorBandDecision.OWNED : FloorBandDecision.ADJACENT;
                }
                return floorBandDecision(world, band, cell, provider);
            });
        }
    }

    /** One operation's local flights; floor selection and exact geometry share this allocation. */
    private static final class StairFlightOwnership {
        private final Level world;
        private final StepProvider provider;
        private final int maxSize;
        private final int maxRadius;
        private final Map<BlockPos, SurfaceCell> owners = new HashMap<>();
        private final Set<BlockPos> rejected = new HashSet<>();

        private StairFlightOwnership(Level world, StepProvider provider, int maxSize, int maxRadius) {
            this.world = world;
            this.provider = provider;
            this.maxSize = maxSize;
            this.maxRadius = maxRadius;
        }

        private SurfaceCell owner(SurfaceCell seed) {
            if (rejected.contains(seed.feet())) return null;
            if (owners.containsKey(seed.feet())) return owners.get(seed.feet());

            Map<BlockPos, SurfaceCell> flight = discover(seed);
            owners.putAll(flight);
            if (flight.isEmpty()) rejected.add(seed.feet());
            return owners.get(seed.feet());
        }

        private Map<BlockPos, SurfaceCell> discover(SurfaceCell seed) {
            List<HorizontalStep> seedSteps = provider.steps(seed);
            boolean ascending = seedSteps.stream().anyMatch(step -> step.connector() == null
                    && step.landing().feet().getY() > seed.feet().getY());
            boolean descending = hasDescendingStep(seed, provider);
            if (!ascending && !descending) return reject(seed.feet());
            boolean stableSeed = stableSameHeightPeerCount(seed, provider) >= MIN_STABLE_LANDING_PEERS;
            // A landing joining two flights belongs to the landing, not to a combined staircase.
            if (stableSeed && ascending && descending) return reject(seed.feet());

            ArrayDeque<SurfaceCell> queue = new ArrayDeque<>();
            Map<BlockPos, SurfaceCell> cells = new LinkedHashMap<>();
            Set<BlockPos> queued = new HashSet<>();
            List<SurfaceCell> landings = new ArrayList<>();
            queue.add(seed);
            queued.add(seed.feet());
            while (!queue.isEmpty()) {
                SurfaceCell current = queue.removeFirst();
                if (horizontalDistance(current.feet(), seed.feet()) >= maxRadius
                        || cells.size() >= maxSize) {
                    rejected.add(current.feet());
                    return reject(cells.keySet());
                }
                cells.put(current.feet(), current);
                boolean stable = stableSameHeightPeerCount(current, provider) >= MIN_STABLE_LANDING_PEERS;
                if (stable) {
                    landings.add(current);
                    if (!current.feet().equals(seed.feet())) continue;
                }
                for (HorizontalStep step : provider.steps(current)) {
                    if (step.connector() != null) continue;
                    SurfaceCell next = step.landing();
                    if (stable && next.feet().getY() == current.feet().getY()) continue;
                    if (queued.add(next.feet())) queue.addLast(next);
                }
            }

            List<Integer> landingHeights = landings.stream().map(cell -> cell.feet().getY())
                    .distinct().sorted().toList();
            if (landingHeights.size() != 2
                    || landingHeights.getLast() - landingHeights.getFirst() < 2) {
                return reject(cells.keySet());
            }
            SurfaceCell lower = landings.stream()
                    .filter(cell -> cell.feet().getY() == landingHeights.getFirst())
                    .min(Comparator.comparing(SurfaceCell::feet, CELL_ORDER)).orElseThrow();
            SurfaceCell upper = landings.stream()
                    .filter(cell -> cell.feet().getY() == landingHeights.getLast())
                    .min(Comparator.comparing(SurfaceCell::feet, CELL_ORDER)).orElseThrow();
            boolean explicitStairs = cells.keySet().stream()
                    .anyMatch(pos -> isExplicitStairTransitionCell(world, pos));
            // Ordinary supports within the existing uneven-floor range are not proof of stairs.
            if (!explicitStairs && upper.feet().getY() - lower.feet().getY() <= FLOOR_BAND_RADIUS) {
                return reject(cells.keySet());
            }
            if (cells.values().stream().anyMatch(cell -> cell.feet().getY() < lower.feet().getY()
                    || cell.feet().getY() > upper.feet().getY())) return reject(cells.keySet());
            List<Integer> stepHeights = cells.values().stream()
                    .filter(cell -> !explicitStairs || isExplicitStairTransitionCell(world, cell.feet())
                            || stableSameHeightPeerCount(cell, provider) < MIN_STABLE_LANDING_PEERS)
                    .map(cell -> cell.feet().getY()).distinct().sorted().toList();
            if (stepHeights.size() < 2) return reject(cells.keySet());
            // Count rows rather than blocks across the width. The odd middle row belongs upstairs.
            int upperStartY = stepHeights.get(stepHeights.size() / 2);
            Map<BlockPos, SurfaceCell> allocation = new HashMap<>();
            for (SurfaceCell cell : cells.values()) {
                allocation.put(cell.feet(), cell.feet().getY() < upperStartY ? lower : upper);
            }
            return allocation;
        }

        private Map<BlockPos, SurfaceCell> reject(BlockPos cell) {
            rejected.add(cell);
            return Map.of();
        }

        private Map<BlockPos, SurfaceCell> reject(Collection<BlockPos> cells) {
            rejected.addAll(cells);
            return Map.of();
        }
    }

    private record HorizontalStep(SurfaceCell landing, BlockPos connector) {
        private static final Comparator<HorizontalStep> ORDER = Comparator
                .comparingInt((HorizontalStep step) -> step.landing().feet().getY())
                .thenComparingInt(step -> step.landing().feet().getX())
                .thenComparingInt(step -> step.landing().feet().getZ());
    }

    private record FloorStep(SurfaceProbe landing, BlockPos connector) {
    }

    @FunctionalInterface
    private interface StepProvider {
        List<HorizontalStep> steps(SurfaceCell cell);
    }

    private record TraversalScan(Building.validationResult result,
                                 Map<BlockPos, SurfaceCell> cells,
                                 Set<Transition> transitions,
                                 Set<BlockPos> verticalBoundaryCells,
                                 Set<BlockPos> adjacentFloorSeeds,
                                 Set<BlockPos> connectors) {
        TraversalScan {
            cells = cells == null ? Map.of() : Map.copyOf(cells);
            transitions = transitions == null ? Set.of() : Set.copyOf(transitions);
            verticalBoundaryCells = verticalBoundaryCells == null ? Set.of() : Set.copyOf(verticalBoundaryCells);
            adjacentFloorSeeds = adjacentFloorSeeds == null ? Set.of() : Set.copyOf(adjacentFloorSeeds);
            connectors = connectors == null ? Set.of() : Set.copyOf(connectors);
        }

        static TraversalScan failure(Building.validationResult result) {
            return new TraversalScan(result, Map.of(), Set.of(), Set.of(), Set.of(), Set.of());
        }
    }

    private record MaterializedFloor(FloorGeometry floor, Set<Transition> transitions) {
    }

    private record SurfaceProbe(BlockPos feet, double surfaceY) {
    }

    /** The selected Floor cell and, when supported directly, the input that selected it. */
    record Result(Building.validationResult result,
                  FloorGeometry floor,
                  BlockPos seed,
                  BlockPos supportedSource,
                  BlockPos min,
                  BlockPos max,
                  Set<Transition> transitions,
                  Set<BlockPos> verticalBoundaryCells,
                  Set<BlockPos> adjacentFloorSeeds) {
        Result {
            seed = seed.immutable();
            supportedSource = supportedSource == null ? null : supportedSource.immutable();
            transitions = transitions == null ? Set.of() : Set.copyOf(transitions);
            verticalBoundaryCells = verticalBoundaryCells == null ? Set.of() : Set.copyOf(verticalBoundaryCells);
            adjacentFloorSeeds = adjacentFloorSeeds == null ? Set.of() : Set.copyOf(adjacentFloorSeeds);
        }

        static Result failure(Building.validationResult result, BlockPos source) {
            return new Result(result, null, source, null, source, source,
                    Set.of(), Set.of(), Set.of());
        }
    }
}
