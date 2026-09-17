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
import java.util.IdentityHashMap;
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
    private static final int STOREY_HEIGHT_RADIUS = 2;
    private static final int VOLUME_CELL_LIMIT_MULTIPLIER = 16;
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
    private static final Direction[] VOLUME_DIRECTIONS = Direction.values();
    private static final int[] LANDING_Y_OFFSETS = {0, 1, -1};

    private SelectedFloorScanner() {
    }

    /** Resolves the selected Floor and explicitly explores its connected storeys. */
    static Result scan(Level world, BlockPos seed, int maxSize, int maxRadius) {
        return new Observation(world, maxSize, maxRadius).connected(seed);
    }

    static Result scanSelected(Level world, BlockPos seed, int maxSize, int maxRadius) {
        return new Observation(world, maxSize, maxRadius).selected(seed);
    }

    /** All discovery evidence lives for one operation; none is retained across world changes. */
    static final class Observation {
        private final Level world;
        private final int maxSize;
        private final int maxRadius;
        private final FloorCeilingResolver ceilings;
        private final StepProvider provider;
        private final Map<BlockPos, StoreyResolution> resolutions = new HashMap<>();
        private final Map<BlockPos, EnclosedVolume> volumesBySeed = new HashMap<>();

        Observation(Level world, int maxSize, int maxRadius) {
            this.world = world;
            this.maxSize = maxSize;
            this.maxRadius = maxRadius;
            this.ceilings = new FloorCeilingResolver(world);
            Map<BlockPos, List<HorizontalStep>> steps = new HashMap<>();
            this.provider = cell -> steps.computeIfAbsent(
                    cell.feet(), ignored -> worldSteps(world, cell, ceilings));
        }

        private StoreyResolution resolve(BlockPos seed) {
            return resolutions.computeIfAbsent(seed.immutable(), requested -> {
                SurfaceCell traversalSeed = resolveScanSeed(world, requested, ceilings).orElse(null);
                if (traversalSeed == null) {
                    return StoreyResolution.failure(Building.validationResult.NOT_IN_BUILDING);
                }

                EnclosedVolume volume = enclosedVolume(traversalSeed.feet());
                if (volume.result() != Building.validationResult.SUCCESS) {
                    return StoreyResolution.failure(volume.result());
                }

                return resolveCanonicalStorey(
                        world, traversalSeed, volume, ceilings, provider, maxSize, maxRadius);
            });
        }

        private EnclosedVolume enclosedVolume(BlockPos seed) {
            return volumesBySeed.computeIfAbsent(seed.immutable(), ignored ->
                    discoverEnclosedVolume(world, seed, ceilings, maxSize, maxRadius));
        }

        Result selected(BlockPos seed) {
            StoreyResolution resolution = resolve(seed);
            return resolution.storey() == null ? Result.failure(resolution.result(), seed)
                    : success(seed, resolution.storey(), new ConnectedStoreys(List.of(resolution.storey()), List.of()));
        }

        Result connected(BlockPos seed) {
            StoreyResolution resolution = resolve(seed);
            if (resolution.storey() == null) return Result.failure(resolution.result(), seed);
            return success(seed, resolution.storey(), discoverConnectedStoreys(resolution.storey(), this));
        }
    }

    private static StoreyResolution resolveCanonicalStorey(
            Level world,
            SurfaceCell requestedSeed,
            EnclosedVolume volume,
            FloorCeilingResolver ceilings,
            StepProvider provider,
            int maxSize,
            int maxRadius) {
        StepProvider enclosedSteps = withinVolume(provider, volume);
        SurfaceCell traversalSeed = volume.supportedCells().get(requestedSeed.feet());
        if (traversalSeed == null) {
            return StoreyResolution.failure(Building.validationResult.NOT_IN_BUILDING);
        }

        traversalSeed = resolveStoreyAnchor(world, traversalSeed, enclosedSteps);
        StoreyClassifier classifier = new StoreyClassifier(
                world, new StoreyContext(traversalSeed.feet().getY()), provider);
        StoreyScan scan = traverseStorey(
                world, traversalSeed, ceilings, maxSize, maxRadius, enclosedSteps, classifier);
        if (scan.result() != Building.validationResult.SUCCESS || scan.floor() == null) {
            return StoreyResolution.failure(scan.result());
        }
        scan = retainEnclosedRegions(
                world, traversalSeed, scan, ceilings, maxRadius, provider, classifier);
        if (scan.result() != Building.validationResult.SUCCESS || scan.floor() == null) {
            return StoreyResolution.failure(scan.result());
        }

        LinkedHashSet<BlockPos> connectors = new LinkedHashSet<>(scan.connectors());
        for (FloorGeometry.Cell cell : scan.floor().cells()) {
            collectVerticalConnectors(world, cell, connectors);
        }
        if (scan.floor().cells().size() + connectors.size() > maxSize) {
            return StoreyResolution.failure(Building.validationResult.BLOCK_LIMIT);
        }
        FloorGeometry floor = new FloorGeometry(scan.floor().cells(),
                StructureConnector.connectorMarkersForFloor(world, connectors, scan.floor()));
        DiscoveredStorey storey = new DiscoveredStorey(
                floor, scan.transitions(), scan.storeyEdgeCells(), scan.transitionSeeds());
        return StoreyResolution.success(storey);
    }

    private static ConnectedStoreys discoverConnectedStoreys(
            DiscoveredStorey selected,
            Observation observation) {
        List<DiscoveredStorey> storeys = new ArrayList<>();
        List<StoreyLink> links = new ArrayList<>();
        ArrayDeque<PendingTransition> queue = new ArrayDeque<>();
        Map<DiscoveredStorey, Set<BlockPos>> visitedTransitions = new IdentityHashMap<>();
        Map<BlockPos, DiscoveredStorey> storeysByCell = new HashMap<>();
        storeys.add(selected);
        indexStorey(storeysByCell, selected);
        selected.transitionSeeds().stream().sorted(CELL_ORDER)
                .map(seed -> new PendingTransition(selected, seed))
                .forEach(queue::addLast);

        while (!queue.isEmpty()) {
            PendingTransition pending = queue.removeFirst();
            if (!visitedTransitions.computeIfAbsent(pending.from(), ignored -> new HashSet<>())
                    .add(pending.seed())) continue;

            DiscoveredStorey knownStorey = storeysByCell.get(pending.seed());
            StoreyResolution resolution = knownStorey == null
                    ? observation.resolve(pending.seed())
                    : StoreyResolution.success(knownStorey);
            if (resolution.result() != Building.validationResult.SUCCESS || resolution.storey() == null) continue;
            DiscoveredStorey resolved = resolution.storey();

            DiscoveredStorey canonical = storeys.stream()
                    .filter(existing -> existing.floor().sameCellPositions(resolved.floor()))
                    .findFirst().orElse(null);
            boolean newlyDiscovered = canonical == null;
            if (newlyDiscovered) {
                canonical = resolved;
                storeys.add(canonical);
                indexStorey(storeysByCell, canonical);
            }
            DiscoveredStorey canonicalStorey = canonical;

            if (!pending.from().floor().sameCellPositions(canonicalStorey.floor())
                    && links.stream().noneMatch(link ->
                    sameLink(link, pending.from().floor(), canonicalStorey.floor()))) {
                links.add(new StoreyLink(
                        pending.from().floor(), canonicalStorey.floor(), pending.seed()));
            }
            if (newlyDiscovered) {
                DiscoveredStorey from = canonicalStorey;
                canonicalStorey.transitionSeeds().stream().sorted(CELL_ORDER)
                        .map(seed -> new PendingTransition(from, seed))
                        .forEach(queue::addLast);
            }
        }
        storeys.sort(Comparator.comparingInt(storey -> storey.floor().anchorY()));
        return new ConnectedStoreys(storeys, links);
    }

    private static void indexStorey(
            Map<BlockPos, DiscoveredStorey> storeysByCell, DiscoveredStorey storey) {
        for (FloorGeometry.Cell cell : storey.floor().cells()) {
            storeysByCell.putIfAbsent(cell.feet(), storey);
        }
    }

    private static boolean sameLink(StoreyLink link, FloorGeometry first, FloorGeometry second) {
        return link.from().sameCellPositions(first) && link.to().sameCellPositions(second)
                || link.from().sameCellPositions(second) && link.to().sameCellPositions(first);
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

    private static EnclosedVolume discoverEnclosedVolume(
            Level world,
            BlockPos seed,
            FloorCeilingResolver ceilings,
            int maxSize,
            int maxRadius) {
        if (!isInteriorVolumeCell(world, seed)) {
            return EnclosedVolume.failure(Building.validationResult.NOT_IN_BUILDING);
        }

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        LinkedHashSet<BlockPos> cells = new LinkedHashSet<>();
        long volumeCellLimit = (long) maxSize * VOLUME_CELL_LIMIT_MULTIPLIER;
        BlockPos immutableSeed = seed.immutable();
        queue.addLast(immutableSeed);
        cells.add(immutableSeed);

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();

            for (Direction direction : VOLUME_DIRECTIONS) {
                BlockPos next = current.relative(direction);
                if (horizontalDistance(next, immutableSeed) > maxRadius) continue;
                if (cells.contains(next) || !isInteriorVolumeCell(world, next)) continue;
                if (ceilings.ceilingY(next).isEmpty()) continue;

                BlockPos immutable = next.immutable();
                cells.add(immutable);
                queue.addLast(immutable);
                if (cells.size() > volumeCellLimit) {
                    return EnclosedVolume.failure(Building.validationResult.BLOCK_LIMIT);
                }
            }
        }

        return EnclosedVolume.success(cells, supportedCellsInVolume(world, cells, ceilings));
    }

    private static Map<BlockPos, SurfaceCell> supportedCellsInVolume(
            Level world, Set<BlockPos> volume, FloorCeilingResolver ceilings) {
        LinkedHashMap<BlockPos, SurfaceCell> supported = new LinkedHashMap<>();
        for (BlockPos cell : volume.stream().sorted(CELL_ORDER).toList()) {
            BlockPos below = cell.below();
            if (volume.contains(below)
                    && isLowObstacle(world, below)
                    && inspectSurfaceCell(world, below, ceilings).isPresent()) continue;

            SurfaceCell surface = inspectSurfaceCell(world, cell, ceilings).orElse(null);
            if (surface != null) supported.put(cell.immutable(), surface);
        }
        return Map.copyOf(supported);
    }

    private static StepProvider withinVolume(StepProvider physical, EnclosedVolume volume) {
        return cell -> physical.steps(cell).stream()
                .filter(step -> volume.supportedCells().containsKey(step.landing().feet()))
                .map(step -> new HorizontalStep(
                        volume.supportedCells().get(step.landing().feet()), step.connector()))
                .sorted(HorizontalStep.ORDER)
                .toList();
    }

    private static StoreyScan traverseStorey(Level world,
                                             SurfaceCell seed,
                                             FloorCeilingResolver ceilings,
                                             int maxSize,
                                             int maxRadius,
                                             StepProvider provider,
                                             StoreyClassifier classifier) {
        SurfaceCell anchor = seed;
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
            List<HorizontalStep> steps = StructureConnector.isHorizontalBoundary(
                    world.getBlockState(current.feet()))
                    ? enclosedBoundaryContinuations(
                    world, current, anchor.feet(), ceilings, maxRadius, provider, classifier, queued)
                    : provider.steps(current);
            for (HorizontalStep step : steps) {
                if (step.connector() != null) connectors.add(step.connector());
                SurfaceCell landing = step.landing();
                StoreyRole role = classifier.role(landing);
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

    private static List<HorizontalStep> enclosedBoundaryContinuations(
            Level world,
            SurfaceCell boundary,
            BlockPos scanAnchor,
            FloorCeilingResolver ceilings,
            int maxRadius,
            StepProvider provider,
            StoreyClassifier classifier,
            Set<BlockPos> queued) {
        return provider.steps(boundary).stream()
                .filter(step -> step.connector() == null)
                .filter(step -> !queued.contains(step.landing().feet()))
                .filter(step -> !reachesExterior(
                        world, step.landing(), scanAnchor, ceilings, maxRadius, provider, classifier))
                .toList();
    }

    private static SurfaceCell resolveStoreyAnchor(
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
        if (isStairOccupancy(world, cell.feet())) return true;
        return isDescendingStoreyTransition(
                cell, new StoreyContext(cell.feet().getY()), provider);
    }

    private static boolean isDescendingStoreyTransition(
            SurfaceCell cell, StoreyContext context, StepProvider provider) {
        return hasDescendingStep(cell, provider)
                && descendsBelowOwnedBand(cell, context, provider)
                && stableSameHeightPeerCount(cell, provider) < MIN_STABLE_LANDING_PEERS;
    }

    private static boolean isStairOccupancy(Level world, BlockPos feet) {
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

    private static StoreyScan retainEnclosedRegions(
            Level world,
            SurfaceCell seed,
            StoreyScan selected,
            FloorCeilingResolver ceilings,
            int maxRadius,
            StepProvider provider,
            StoreyClassifier classifier) {
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
            return StoreyScan.failure(Building.validationResult.NOT_IN_BUILDING);
        }

        Set<BlockPos> allowed = new HashSet<>(floorCells);
        allowed.removeAll(exteriorCells);
        Set<BlockPos> reachable = connectedCells(seed.feet(), allowed, neighbors, new HashSet<>());
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
                world, ceilings, reachable, provider, classifier);
        StoreyScan retainedStorey = new StoreyScan(
                Building.validationResult.SUCCESS,
                new FloorGeometry(cells, Map.of()),
                transitions,
                storeyEdgeCells,
                alternateSeeds,
                connectors);
        return retainedStorey;
    }

    private static Set<BlockPos> alternateSeedsForRetainedFloor(
            Level world,
            FloorCeilingResolver ceilings,
            Set<BlockPos> retained,
            StepProvider provider,
            StoreyClassifier classifier) {
        LinkedHashSet<BlockPos> alternateSeeds = new LinkedHashSet<>();
        for (BlockPos pos : retained) {
            SurfaceCell current = inspectSurfaceCell(world, pos, ceilings).orElse(null);
            if (current == null) continue;
            StoreyRole currentRole = classifier.role(current);
            for (HorizontalStep step : provider.steps(current)) {
                SurfaceCell candidate = step.landing();
                if (currentRole == StoreyRole.EDGE) {
                    if (!retained.contains(candidate.feet())) alternateSeeds.add(candidate.feet());
                } else if (classifier.role(candidate) == StoreyRole.OTHER) {
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

    private static StoreyRole storeyRole(Level world,
                                         StoreyContext context,
                                         SurfaceCell candidate,
                                         StepProvider provider) {
        int y = candidate.feet().getY();
        if (y < context.minOwnedY()
                || y > context.maxOwnedY() + 1) return StoreyRole.OTHER;
        if (y == context.maxOwnedY() + 1) return StoreyRole.EDGE;
        if (y == context.maxOwnedY()
                && descendsFullStoreyFromStair(world, candidate, provider)) return StoreyRole.EDGE;
        if (y == context.anchorY()
                && (isDescendingStoreyTransition(candidate, context, provider)
                || descendsFullStoreyFromStair(world, candidate, provider))) return StoreyRole.OTHER;
        return StoreyRole.OWNED;
    }

    private static boolean descendsFullStoreyFromStair(
            Level world, SurfaceCell start, StepProvider provider) {
        if (!isStairOccupancy(world, start.feet())) return false;
        int boundaryY = start.feet().getY() - STOREY_HEIGHT_RADIUS;
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
            FloorCeilingResolver ceilings,
            int maxRadius,
            StepProvider provider,
            StoreyClassifier classifier) {
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

            for (PhysicalStep step : physicalSteps(world, new SurfaceProbe(current.feet(), current.surfaceY()))) {
                if (step.connector() != null) continue;
                SurfaceProbe landing = step.landing();
                BlockPos next = landing.feet();
                if (visited.contains(next)) continue;
                SurfaceCell probe = new SurfaceCell(next, landing.surfaceY(), next.getY() + 2);
                if (classifier.role(probe) != StoreyRole.OWNED) continue;
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

    private static boolean isInteriorVolumeCell(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) return false;
        if (StructureConnector.isHorizontalBoundary(state)
                || StructureConnector.isVertical(world, pos)) return true;

        var shape = state.getCollisionShape(world, pos);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) < 1.0D;
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
                                  DiscoveredStorey selected,
                                  ConnectedStoreys connected) {
        FloorGeometry geometry = selected.floor();
        Set<BlockPos> footprint = geometry.projection().cells();
        int minX = footprint.stream().mapToInt(BlockPos::getX).min().orElse(seed.getX());
        int minZ = footprint.stream().mapToInt(BlockPos::getZ).min().orElse(seed.getZ());
        int maxX = footprint.stream().mapToInt(BlockPos::getX).max().orElse(seed.getX());
        int maxZ = footprint.stream().mapToInt(BlockPos::getZ).max().orElse(seed.getZ());
        int minY = geometry.cells().stream().mapToInt(cell -> cell.feet().getY() - 1)
                .min().orElse(seed.getY() - 1);
        int maxY = geometry.cells().stream().mapToInt(cell -> cell.ceilingY() - 1)
                .max().orElse(seed.getY());
        return new Result(Building.validationResult.SUCCESS, geometry,
                new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ),
                selected.transitions(), selected.storeyEdgeCells(),
                connected.storeys(), connected.links());
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
            return anchorY - STOREY_HEIGHT_RADIUS;
        }

        int maxOwnedY() {
            return anchorY + STOREY_HEIGHT_RADIUS;
        }
    }

    private static final class StoreyClassifier {
        private final Level world;
        private final StoreyContext context;
        private final StepProvider provider;
        private final Map<BlockPos, StoreyRole> roles = new HashMap<>();
        private final Map<BlockPos, Boolean> exterior = new HashMap<>();

        private StoreyClassifier(Level world, StoreyContext context, StepProvider provider) {
            this.world = world;
            this.context = context;
            this.provider = provider;
        }

        private StoreyRole role(SurfaceCell cell) {
            return roles.computeIfAbsent(
                    cell.feet(), ignored -> storeyRole(world, context, cell, provider));
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
                      Set<BlockPos> transitionSeeds,
                      Set<BlockPos> connectors) {
        StoreyScan {
            transitions = transitions == null ? Set.of() : Set.copyOf(transitions);
            storeyEdgeCells = storeyEdgeCells == null ? Set.of() : Set.copyOf(storeyEdgeCells);
            transitionSeeds = transitionSeeds == null ? Set.of() : Set.copyOf(transitionSeeds);
            connectors = connectors == null ? Set.of() : Set.copyOf(connectors);
        }

        static StoreyScan failure(Building.validationResult result) {
            return new StoreyScan(result, null, Set.of(), Set.of(), Set.of(), Set.of());
        }
    }

    private record SurfaceProbe(BlockPos feet, double surfaceY) {
    }

    private record EnclosedVolume(
            Building.validationResult result,
            Set<BlockPos> cells,
            Map<BlockPos, SurfaceCell> supportedCells) {

        private static EnclosedVolume failure(Building.validationResult result) {
            return new EnclosedVolume(result, Set.of(), Map.of());
        }

        private static EnclosedVolume success(
                Set<BlockPos> cells, Map<BlockPos, SurfaceCell> supportedCells) {
            return new EnclosedVolume(
                    Building.validationResult.SUCCESS,
                    Set.copyOf(cells),
                    Map.copyOf(supportedCells));
        }
    }

    private record StoreyResolution(Building.validationResult result,
                                    DiscoveredStorey storey) {
        static StoreyResolution success(DiscoveredStorey storey) {
            return new StoreyResolution(Building.validationResult.SUCCESS, storey);
        }

        static StoreyResolution failure(Building.validationResult result) {
            return new StoreyResolution(result, null);
        }
    }

    private record PendingTransition(DiscoveredStorey from, BlockPos seed) {
        PendingTransition {
            seed = seed.immutable();
        }
    }

    record StoreyLink(FloorGeometry from, FloorGeometry to, BlockPos transitionSeed) {
        StoreyLink {
            transitionSeed = transitionSeed.immutable();
        }

        FloorGeometry other(FloorGeometry floor) {
            if (from.sameCellPositions(floor)) return to;
            if (to.sameCellPositions(floor)) return from;
            return null;
        }
    }

    private record ConnectedStoreys(List<DiscoveredStorey> storeys,
                                    List<StoreyLink> links) {
        ConnectedStoreys {
            storeys = List.copyOf(storeys);
            links = List.copyOf(links);
        }
    }

    record DiscoveredStorey(FloorGeometry floor,
                            Set<Transition> transitions,
                            Set<BlockPos> storeyEdgeCells,
                            Set<BlockPos> transitionSeeds) {
        DiscoveredStorey {
            transitions = transitions == null ? Set.of() : Set.copyOf(transitions);
            storeyEdgeCells = storeyEdgeCells == null ? Set.of() : Set.copyOf(storeyEdgeCells);
            transitionSeeds = transitionSeeds == null ? Set.of() : Set.copyOf(transitionSeeds);
        }
    }

    record Result(Building.validationResult result,
                  FloorGeometry floor,
                  BlockPos min,
                  BlockPos max,
                  Set<Transition> transitions,
                  Set<BlockPos> storeyEdgeCells,
                  List<DiscoveredStorey> connectedStoreys,
                  List<StoreyLink> storeyLinks) {
        Result {
            transitions = transitions == null ? Set.of() : Set.copyOf(transitions);
            storeyEdgeCells = storeyEdgeCells == null ? Set.of() : Set.copyOf(storeyEdgeCells);
            connectedStoreys = connectedStoreys == null ? List.of() : List.copyOf(connectedStoreys);
            storeyLinks = storeyLinks == null ? List.of() : List.copyOf(storeyLinks);
        }

        List<FloorGeometry> directlyConnectedFloors(FloorGeometry selected) {
            List<FloorGeometry> direct = new ArrayList<>();
            for (StoreyLink link : storeyLinks) {
                FloorGeometry other = link.other(selected);
                if (other != null && direct.stream()
                        .noneMatch(existing -> existing.sameCellPositions(other))) {
                    direct.add(other);
                }
            }
            return List.copyOf(direct);
        }

        Optional<Result> storeyScan(BlockPos seed, FloorGeometry target) {
            if (target == null) return Optional.empty();
            if (connectedStoreys.isEmpty()) {
                return floor != null && floor.sameCellPositions(target)
                        ? Optional.of(this) : Optional.empty();
            }
            ConnectedStoreys connected = new ConnectedStoreys(connectedStoreys, storeyLinks);
            return connectedStoreys.stream()
                    .filter(storey -> storey.floor().sameCellPositions(target))
                    .findFirst()
                    .map(storey -> success(seed, storey, connected));
        }

        static Result failure(Building.validationResult result, BlockPos source) {
            return new Result(result, null, source, source,
                    Set.of(), Set.of(), List.of(), List.of());
        }
    }
}
