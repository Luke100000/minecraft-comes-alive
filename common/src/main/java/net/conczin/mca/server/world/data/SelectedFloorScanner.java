package net.conczin.mca.server.world.data;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
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

/** Discovers one exact selected Floor from surface-height traversal and canonical integer cells. */
final class SelectedFloorScanner {
    private static final double MAX_STEP_HEIGHT = 1.125D;
    private static final int FLOOR_BAND_RADIUS = 2;
    // Per door-separated region; canonical Floor cells still share the global maxSize.
    private static final int MAX_EXPOSED_SURFACE_CELLS = 64;
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

    /** Loaded-only block views must distinguish an unknown column from empty air. */
    interface LoadedColumns extends BlockGetter {
        boolean hasColumn(BlockPos pos);

        /** Frame collision proof may depend on state normally ignored by geometry caching. */
        default void observeFrameState(BlockPos pos, Direction face) { }
    }

    /** Resolves exactly one selected Floor. */
    static Result scan(BlockGetter world, BlockPos seed, int maxSize, int maxRadius) {
        return new Observation(world, maxSize, maxRadius).selected(seed);
    }

    /** All discovery evidence lives for one operation; none is retained across world changes. */
    static final class Observation {
        private final BlockGetter world;
        private final int maxSize;
        private final int maxRadius;
        private final FloorCeilingResolver ceilings;
        private final StepProvider provider;
        private final TransitionOwnership transitionsOwner;
        private final RegionDiscovery regions;
        private final Map<BlockPos, Result> resolutions = new HashMap<>();
        private final Map<BlockPos, OptionalDouble> surfaces = new HashMap<>();
        private final Map<FrameKey, Optional<FramedDoorway>> frames = new HashMap<>();

        Observation(BlockGetter world, int maxSize, int maxRadius) {
            this.world = world;
            this.maxSize = maxSize;
            this.maxRadius = maxRadius;
            this.ceilings = new FloorCeilingResolver(world);
            Map<BlockPos, List<HorizontalStep>> steps = new HashMap<>();
            Map<BlockPos, Optional<BlockPos>> boundaries = new HashMap<>();
            Map<BlockPos, Boolean> stairCells = new HashMap<>();
            Map<BlockPos, Boolean> throughLandings = new HashMap<>();
            this.provider = new StepProvider() {
                @Override
                public List<HorizontalStep> steps(SurfaceCell cell) {
                    return steps.computeIfAbsent(cell.feet(),
                            ignored -> worldSteps(world, cell, ceilings, this));
                }

                @Override
                public Optional<FramedDoorway> framedDoorway(BlockPos feet, Direction.Axis normalAxis) {
                    return Observation.this.framedDoorway(feet, normalAxis);
                }

                @Override
                public OptionalDouble surfaceY(BlockGetter world, BlockPos feet) {
                    if (!isLoadedColumn(world, feet)) return OptionalDouble.empty();
                    // Candidate support/headroom does not depend on the approaching
                    // cell. Cache it only for this observation, not its directional edge.
                    return surfaces.computeIfAbsent(feet.immutable(), pos -> floorSurfaceY(world, pos));
                }

                @Override
                public BlockPos horizontalConnector(BlockGetter world, BlockPos horizontal) {
                    if (!isLoadedColumn(world, horizontal)) return null;
                    return boundaries.computeIfAbsent(horizontal.immutable(),
                            pos -> Optional.ofNullable(SelectedFloorScanner.horizontalConnector(world, pos)))
                            .orElse(null);
                }

                @Override
                public boolean explicitStairCell(BlockGetter world, BlockPos feet) {
                    return stairCells.computeIfAbsent(feet.immutable(),
                            pos -> StepProvider.super.explicitStairCell(world, pos));
                }

                @Override
                public boolean stableLanding(SurfaceCell cell) {
                    return StepProvider.super.stableLanding(cell) && !throughLanding(cell);
                }

                @Override
                public boolean throughLanding(SurfaceCell cell) {
                    return isThroughStairLanding(world, cell, this, maxSize, maxRadius, throughLandings);
                }
            };
            this.regions = new RegionDiscovery(world, ceilings, provider, maxSize, maxRadius);
            // Proving a transition observes both floor halves. Only the selected half is
            // materialized and charged against maxSize by scanSelectedFloor.
            int maxTransitionSize = (int) Math.min(2L * maxSize, Integer.MAX_VALUE);
            this.transitionsOwner = new TransitionOwnership(world, provider, regions, maxTransitionSize, maxRadius);
        }

        private Result resolve(BlockPos seed) {
            return resolutions.computeIfAbsent(seed.immutable(), requested -> {
                if (!isLoadedColumn(world, requested)) {
                    return Result.failure(Building.validationResult.NOT_IN_BUILDING, requested);
                }
                SurfaceCell supported = resolveSeedCell(world, requested, ceilings).orElse(null);
                SurfaceCell traversalSeed = supported == null
                        ? adjacentTraversalSeed(world, requested, ceilings, provider).orElse(null) : supported;
                if (traversalSeed == null) {
                    return Result.failure(Building.validationResult.NOT_IN_BUILDING, requested);
                }

                return resolveSelectedFloor(
                        world, traversalSeed, supported == null ? null : requested,
                        ceilings, provider, transitionsOwner, regions, maxSize, maxRadius);
            });
        }

        Result selected(BlockPos seed) {
            return resolve(seed);
        }

        Optional<SurfaceCell> surfaceCell(BlockPos feet) {
            if (!isLoadedColumn(world, feet)) return Optional.empty();
            OptionalDouble surface = surfaces.computeIfAbsent(feet.immutable(), pos -> floorSurfaceY(world, pos));
            if (surface.isEmpty()) return Optional.empty();
            OptionalInt ceiling = ceilings.ceilingY(feet);
            return ceiling.isEmpty() ? Optional.empty()
                    : Optional.of(new SurfaceCell(feet, surface.getAsDouble(), ceiling.getAsInt()));
        }

        boolean explicitStairCell(BlockPos feet) {
            return provider.explicitStairCell(world, feet);
        }

        Optional<FramedDoorway> framedDoorway(BlockPos feet, Direction.Axis normalAxis) {
            FrameKey key = new FrameKey(feet.immutable(), normalAxis);
            Optional<FramedDoorway> known = frames.get(key);
            if (known != null) return known;
            Optional<FramedDoorway> frame = findFrame(feet, normalAxis);
            frames.put(key, frame);
            frame.ifPresent(span -> span.cells().forEach(cell -> frames.put(new FrameKey(cell, normalAxis), frame)));
            return frame;
        }

        private Optional<FramedDoorway> findFrame(BlockPos feet, Direction.Axis normalAxis) {
            if (normalAxis == Direction.Axis.Y || !isLoadedColumn(world, feet)) return Optional.empty();
            Direction width = normalAxis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;
            // Already-proven walking surfaces cannot supply either solid side jamb.
            // Reuse that observation instead of rereading open platforms for each axis.
            if (hasWalkingRun(feet, width) || hasWalkingRun(feet, width.getOpposite())) return Optional.empty();
            // Most candidates are open floor, not frames. A nearby jamb must exist before
            // reading the opening's headroom or walking either side of a possible span.
            if (!fullFrameFace(feet.relative(width.getOpposite()), width)
                    && !fullFrameFace(feet.relative(width.getOpposite(), 2), width)) return Optional.empty();
            if (!openingCell(feet, normalAxis)) return Optional.empty();
            BlockPos first = feet;
            int length = 1;
            while (openingCell(first.relative(width.getOpposite()), normalAxis)) {
                if (++length > 2) return Optional.empty();
                first = first.relative(width.getOpposite());
            }
            BlockPos last = feet;
            while (openingCell(last.relative(width), normalAxis)) {
                if (++length > 2) return Optional.empty();
                last = last.relative(width);
            }
            for (int dy = 0; dy < 2; dy++) {
                if (!fullFrameFace(first.relative(width.getOpposite()).above(dy), width)
                        || !fullFrameFace(last.relative(width).above(dy), width.getOpposite())) return Optional.empty();
            }
            Direction normal = normalAxis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
            List<BlockPos> cells = new ArrayList<>();
            for (int i = 0; i < length; i++) {
                BlockPos cell = first.relative(width, i);
                if (!isLoadedColumn(world, cell.relative(normal))
                        || !isLoadedColumn(world, cell.relative(normal.getOpposite()))
                        || !isTraversalOccupancyAllowed(world, cell.relative(normal))
                        || !hasInteriorHeadroom(world, cell.relative(normal))
                        || !isTraversalOccupancyAllowed(world, cell.relative(normal.getOpposite()))
                        || !hasInteriorHeadroom(world, cell.relative(normal.getOpposite()))
                        || provider.surfaceY(world, cell).isEmpty()
                        || !fullFrameFace(cell.above(2), Direction.DOWN)) return Optional.empty();
                cells.add(cell.immutable());
            }
            return Optional.of(new FramedDoorway(normalAxis, cells));
        }

        private boolean openingCell(BlockPos feet, Direction.Axis normalAxis) {
            if (!isLoadedColumn(world, feet)) return false;
            BlockState lower = world.getBlockState(feet), upper = world.getBlockState(feet.above());
            if (lower.getBlock() instanceof DoorBlock) {
                return lower.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                        && lower.getValue(DoorBlock.FACING).getAxis() == normalAxis
                        && upper.equals(lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            }
            return lower.getFluidState().isEmpty() && upper.getFluidState().isEmpty()
                    && lower.getCollisionShape(world, feet).isEmpty()
                    && upper.getCollisionShape(world, feet.above()).isEmpty();
        }

        private boolean hasWalkingRun(BlockPos feet, Direction direction) {
            OptionalDouble near = surfaces.get(feet.relative(direction));
            OptionalDouble far = surfaces.get(feet.relative(direction, 2));
            if (near == null || near.isEmpty() || far == null || far.isEmpty()) return false;
            observeFrameState(feet.relative(direction), direction.getOpposite());
            observeFrameState(feet.relative(direction, 2), direction.getOpposite());
            return true;
        }

        private boolean fullFrameFace(BlockPos pos, Direction face) {
            if (!isLoadedColumn(world, pos)) return false;
            OptionalDouble surface = surfaces.get(pos);
            if (face.getAxis() != Direction.Axis.Y && surface != null && surface.isPresent()) {
                observeFrameState(pos, face);
                return false;
            }
            BlockState state = world.getBlockState(pos);
            observeFrameState(pos, face);
            return !state.is(BlockTags.LEAVES) && state.getFluidState().isEmpty()
                    && !(state.getBlock() instanceof LeavesBlock)
                    && !(state.getBlock() instanceof DoorBlock)
                    && Block.isFaceFull(state.getCollisionShape(world, pos), face);
        }

        private void observeFrameState(BlockPos pos, Direction face) {
            if (world instanceof LoadedColumns loaded) loaded.observeFrameState(pos, face);
        }

        /** New decks can replace a saved lower ceiling without changing the registered Floor. */
        boolean shellsTouch(FloorGeometry.Cell first, FloorGeometry.Cell second) {
            FloorGeometry.Cell lower = first.feet().getY() <= second.feet().getY() ? first : second;
            FloorGeometry.Cell upper = lower == first ? second : first;
            return ceilings.ceilingY(lower.feet()).orElse(Integer.MIN_VALUE) == upper.feet().getY() - 1
                    && surfaceCell(lower.feet()).isPresent();
        }
    }

    private static Result resolveSelectedFloor(
            BlockGetter world,
            SurfaceCell requestedSurface,
            BlockPos supportedSource,
            FloorCeilingResolver ceilings,
            StepProvider provider,
            TransitionOwnership transitionsOwner,
            RegionDiscovery regions,
            int maxSize,
            int maxRadius) {
        SurfaceRegion selectedRegion = transitionsOwner.owner(requestedSurface);
        if (selectedRegion == null) selectedRegion = regions.region(requestedSurface);
        SurfaceCell traversalSeed = selectedRegion.surface();
        return scanSelectedFloor(world, requestedSurface, traversalSeed, supportedSource,
                ceilings, provider, transitionsOwner, selectedRegion, maxSize, maxRadius);
    }

    private static Result scanSelectedFloor(
            BlockGetter world,
            SurfaceCell requestedSurface,
            SurfaceCell traversalSeed,
            BlockPos supportedSource,
            FloorCeilingResolver ceilings,
            StepProvider provider,
            TransitionOwnership transitionsOwner,
            SurfaceRegion selectedRegion,
            int maxSize,
            int maxRadius) {
        FloorBandClassifier classifier = new FloorBandClassifier(
                world, new FloorBand(traversalSeed.feet().getY()), provider, transitionsOwner, selectedRegion);
        TraversalScan traversal = traverseSelectedFloor(
                world, traversalSeed, ceilings, maxSize, maxRadius, provider, classifier);
        if (traversal.result() != Building.validationResult.SUCCESS) {
            return Result.failure(traversal.result(), traversalSeed.feet());
        }
        traversal = retainEnclosedRegions(
                world, traversalSeed, traversal, ceilings, maxSize, maxRadius, provider, classifier);
        if (traversal.result() != Building.validationResult.SUCCESS) {
            return Result.failure(traversal.result(), traversalSeed.feet());
        }
        MaterializedFloor materialized = materializeStructuralFloor(world, traversal, ceilings, classifier);

        LinkedHashSet<BlockPos> connectors = new LinkedHashSet<>(traversal.connectors());
        collectVerticalConnectors(world, materialized.floor(), connectors);
        if (materialized.floor().cells().size() + connectors.size() > maxSize) {
            return Result.failure(Building.validationResult.BLOCK_LIMIT, traversalSeed.feet());
        }
        FloorGeometry floor = new FloorGeometry(materialized.floor().cells(),
                StructureConnector.connectorMarkersForFloor(world, connectors, materialized.floor()));
        BlockPos selectedCell = floor.cellAt(requestedSurface.feet()).isPresent()
                ? requestedSurface.feet() : traversalSeed.feet();
        // Owned stair rows extend below a landing; they must not move its storey anchor.
        return success(selectedCell, supportedSource, selectedRegion.anchorY(), floor,
                materialized.transitions(), traversal.verticalBoundaryCells(), traversal.adjacentFloorSeeds());
    }

    private static Optional<SurfaceCell> resolveSeedCell(
            BlockGetter world, BlockPos seed, FloorCeilingResolver ceilings) {
        if (world.isOutsideBuildHeight(seed) || !isLoadedColumn(world, seed)) return Optional.empty();
        // Jumping or flying selects the surface below in this column, without crossing
        // collision shapes or fluids into another floor. Keep the original source in Result.
        while (seed.getY() > world.getMinY()
                && isOpen(world, seed) && isOpen(world, seed.below())) {
            seed = seed.below();
        }
        BlockPos below = seed.below();
        if (isLowObstacle(world, below)) {
            Optional<SurfaceCell> underlying = inspectSurfaceCell(world, below, ceilings);
            if (underlying.isPresent()) return underlying;
        }
        Optional<SurfaceCell> exact = inspectSurfaceCell(world, seed, ceilings);
        if (exact.isPresent()) return exact;
        BlockState state = world.getBlockState(seed);
        var shape = state.getCollisionShape(world, seed);
        if (state.getFluidState().isEmpty() && !shape.isEmpty()
                && shape.max(Direction.Axis.Y) <= 1.0D && !state.isCollisionShapeFullBlock(world, seed)) {
            return inspectSurfaceCell(world, seed.above(), ceilings);
        }
        return Optional.empty();
    }

    private static Optional<SurfaceCell> adjacentTraversalSeed(
            BlockGetter world, BlockPos membershipCell, FloorCeilingResolver ceilings, StepProvider provider) {
        OptionalDouble handoffY = interactionHandoffY(world, membershipCell, ceilings);
        if (handoffY.isEmpty()) {
            // A selected bed/furniture column can still belong to the room when a
            // standing player cannot traverse its top under the local ceiling.
            BlockPos obstacle = isLowObstacle(world, membershipCell.below())
                    ? membershipCell.below() : membershipCell;
            if (isLowObstacle(world, obstacle)
                    && !StructureConnector.isConnector(world.getBlockState(obstacle))
                    && hasInteriorHeadroom(world, obstacle)) {
                handoffY = floorSurfaceLevel(world, obstacle);
            }
        }
        if (handoffY.isEmpty()) return Optional.empty();

        for (Direction direction : HORIZONTAL) {
            BlockPos horizontal = membershipCell.relative(direction);
            for (SurfaceProbe landing : findLandings(world, handoffY.getAsDouble(), horizontal, provider)) {
                OptionalInt ceiling = ceilings.ceilingY(landing.feet());
                if (ceiling.isPresent()) {
                    return Optional.of(new SurfaceCell(
                            landing.feet(), landing.surfaceY(), ceiling.getAsInt()));
                }
            }
        }
        return Optional.empty();
    }

    private static TraversalScan traverseSelectedFloor(BlockGetter world,
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
        LinkedHashSet<BlockPos> connectors = new LinkedHashSet<>();
        queue.addLast(anchor);
        queued.add(anchor.feet());

        while (!queue.isEmpty()) {
            SurfaceCell current = queue.removeFirst();
            if (horizontalDistance(current.feet(), anchor.feet()) >= maxRadius) {
                return TraversalScan.failure(Building.validationResult.SIZE_LIMIT);
            }
            cells.put(current.feet(), current);
            List<HorizontalStep> steps = isDiscoveryBoundary(world, current.feet(), provider)
                    ? enclosedBoundaryContinuations(
                    world, current, anchor.feet(), ceilings, maxSize, maxRadius, provider, classifier, queued)
                    : provider.steps(current);
            for (HorizontalStep step : steps) {
                if (step.connector() != null) connectors.add(step.connector());
                SurfaceCell landing = step.landing();
                FloorBandDecision decision = classifier.decision(landing);
                if (!decision.owned()) {
                    continue;
                }
                transitions.add(new Transition(current.feet(), landing.feet()));
                if (!decision.continueTraversal()) {
                    cells.putIfAbsent(landing.feet(), landing);
                    verticalBoundaryCells.add(landing.feet());
                } else if (queued.add(landing.feet())) {
                    queue.addLast(landing);
                }
            }
            if (cells.size() + connectors.size() > maxSize) {
                return TraversalScan.failure(Building.validationResult.BLOCK_LIMIT);
            }
        }

        // Adjacent floors are derived once, after exterior cells have been removed.
        return new TraversalScan(Building.validationResult.SUCCESS, cells,
                Set.copyOf(transitions), Set.copyOf(verticalBoundaryCells),
                Set.of(), Set.copyOf(connectors));
    }

    private static List<HorizontalStep> enclosedBoundaryContinuations(
            BlockGetter world,
            SurfaceCell boundary,
            BlockPos scanAnchor,
            FloorCeilingResolver ceilings,
            int maxSize,
            int maxRadius,
            StepProvider provider,
            FloorBandClassifier classifier,
            Set<BlockPos> queued) {
        boolean realBoundary = StructureConnector.isHorizontalBoundary(world.getBlockState(boundary.feet()));
        return provider.steps(boundary).stream()
                .filter(step -> !realBoundary || step.connector() == null)
                .filter(step -> !queued.contains(step.landing().feet()))
                .filter(step -> hasAcceptedExposure(
                        world, step.landing(), scanAnchor, ceilings, maxSize, maxRadius, provider, classifier))
                .toList();
    }

    private static boolean isDescendingFloorBoundary(
            SurfaceCell cell, FloorBand band, StepProvider provider) {
        // A stable landing already rules out this boundary; avoid exploring its whole plateau.
        return hasDescendingStep(cell, provider)
                && !hasStableSameHeightPeer(cell, provider)
                && reachesVerticalLevel(cell, band.minOwnedY() - 1, provider, new HashSet<>());
    }

    // One stable same-height neighbor proves a landing, including full-block stairs.
    private static boolean hasStableSameHeightPeer(SurfaceCell cell, StepProvider provider) {
        return provider.stableLanding(cell);
    }

    /** A narrow stair turn connects flights; its flat outer corner is not another storey. */
    private static boolean isThroughStairLanding(BlockGetter world, SurfaceCell seed, StepProvider provider,
                                                   int maxSize, int maxRadius, Map<BlockPos, Boolean> cache) {
        Boolean cached = cache.get(seed.feet());
        if (cached != null) return cached;
        if (!canBeStairLanding(world, seed, provider)) {
            cache.put(seed.feet(), false);
            return false;
        }
        ArrayDeque<SurfaceCell> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(seed);
        visited.add(seed.feet());
        Map<BlockPos, Set<Direction>> levelDepthCells = new HashMap<>();
        boolean rises = false;
        boolean drops = false;
        boolean through = true;
        while (!queue.isEmpty()) {
            SurfaceCell cell = queue.removeFirst();
            if (visited.size() > maxSize || horizontalDistance(cell.feet(), seed.feet()) >= maxRadius) {
                // This seed's incomplete proof says nothing about the other cells.
                // A later central seed may prove the whole patch within its radius.
                return false;
            }
            if (!canBeStairLanding(world, cell, provider)) {
                through = false;
                break;
            }
            Set<Direction> flatDirections = new HashSet<>();
            boolean changesHeight = false;
            for (HorizontalStep step : provider.steps(cell)) {
                if (step.connector() != null) {
                    through = false; // Even a tiny doorway-defined Room keeps its landing.
                    break;
                }
                SurfaceCell next = step.landing();
                double delta = next.surfaceY() - seed.surfaceY();
                if (delta == 0) {
                    flatDirections.add(Direction.getNearest(next.feet().getX() - cell.feet().getX(), 0,
                            next.feet().getZ() - cell.feet().getZ(), null));
                    if (visited.add(next.feet())) queue.addLast(next);
                } else {
                    changesHeight = true;
                    rises |= delta > 0 && delta <= MAX_STEP_HEIGHT;
                    drops |= delta < 0 && delta >= -MAX_STEP_HEIGHT;
                }
            }
            // A narrow hall also has usable level depth: its middle can be crossed
            // without bordering any rise/drop. A stair turn's outer corner cannot.
            boolean levelDepth = !changesHeight && flatDirections.stream()
                    .anyMatch(direction -> flatDirections.contains(direction.getOpposite()));
            boolean levelRun = levelDepth && flatDirections.stream().anyMatch(direction -> {
                Set<Direction> neighbor = levelDepthCells.get(cell.feet().relative(direction));
                return flatDirections.contains(direction.getOpposite()) && neighbor != null
                        && neighbor.contains(direction) && neighbor.contains(direction.getOpposite());
            });
            if (levelDepth) levelDepthCells.put(cell.feet(), flatDirections);
            if (!through || flatDirections.size() == HORIZONTAL.length || levelRun) {
                through = false;
                break;
            }
        }
        through &= rises && drops;
        for (BlockPos pos : visited) cache.put(pos, through);
        return through;
    }

    private static boolean canBeStairLanding(BlockGetter world, SurfaceCell cell, StepProvider provider) {
        if (provider.explicitStairCell(world, cell.feet())) return true;
        // Mixed stair supports (for example an embedded light) still carry a half-step flight.
        if (provider.steps(cell).stream().anyMatch(step -> step.connector() == null
                && (Math.abs(step.landing().surfaceY() - cell.surfaceY()) == 0.5D
                || provider.explicitStairCell(world, step.landing().feet())))) return true;
        // A bottom slab may be the low obstacle in the feet cell, not the block underneath it.
        return world.getBlockState(cell.feet().below()).getBlock() instanceof SlabBlock
                || world.getBlockState(cell.feet()).getBlock() instanceof SlabBlock;
    }

    private static TraversalScan retainEnclosedRegions(
            BlockGetter world,
            SurfaceCell seed,
            TraversalScan traversal,
            FloorCeilingResolver ceilings,
            int maxSize,
            int maxRadius,
            StepProvider provider,
            FloorBandClassifier classifier) {
        Set<BlockPos> traversalCells = traversal.cells().keySet();
        Set<BlockPos> boundaryCells = traversal.connectors().stream()
                .filter(traversalCells::contains)
                .collect(Collectors.toSet());
        Set<BlockPos> frameCells = traversalCells.stream()
                .filter(pos -> !boundaryCells.contains(pos) && isFramedBoundary(pos, provider))
                .collect(Collectors.toSet());
        boundaryCells.addAll(frameCells);
        Map<BlockPos, List<BlockPos>> neighbors = Transition.neighborIndex(traversal.transitions());
        List<Set<BlockPos>> regions = connectedRegions(traversalCells, boundaryCells, neighbors);
        Set<BlockPos> exteriorCells = new HashSet<>();

        for (Set<BlockPos> region : regions) {
            BlockPos representative = region.stream()
                    .min(CELL_ORDER)
                    .orElseThrow();
            SurfaceCell regionSeed = traversal.cells().get(representative);
            // Every door-separated region uses the same exposure rule; the selected
            // side of a door must not change the shared physical Floor geometry.
            if (!hasAcceptedExposure(world, regionSeed, seed.feet(), ceilings,
                    maxSize, maxRadius, provider, classifier)) {
                exteriorCells.addAll(region);
            }
        }

        // A frame inherits an accepted ordinary side. A frame-only fragment must
        // prove physical enclosure without treating its own inferred cuts as walls;
        // this preserves enclosed passages without inventing exterior threshold Rooms.
        Set<BlockPos> visitedFrames = new HashSet<>();
        for (BlockPos frame : frameCells) {
            Set<BlockPos> cluster = connectedCells(frame, frameCells, neighbors, visitedFrames);
            if (cluster.isEmpty()) continue;
            boolean acceptedSide = cluster.stream().anyMatch(pos -> neighbors.getOrDefault(pos, List.of()).stream()
                    .anyMatch(next -> !boundaryCells.contains(next) && !exteriorCells.contains(next)));
            if (acceptedSide) continue;
            SurfaceCell fragment = traversal.cells().get(cluster.stream().min(CELL_ORDER).orElseThrow());
            if (!hasBoundedExteriorSurface(world, fragment, seed.feet(), ceilings,
                    maxSize, maxRadius, provider, classifier, false)
                    && reachesExterior(world, fragment, seed.feet(), ceilings,
                    maxRadius, provider, classifier, false)) exteriorCells.addAll(cluster);
        }

        if (exteriorCells.contains(seed.feet())) {
            return TraversalScan.failure(Building.validationResult.NOT_IN_BUILDING);
        }

        // The original traversal is connected by construction. Only pruning an
        // exterior region can disconnect it; otherwise retain its existing evidence.
        Set<BlockPos> reachable = traversalCells;
        if (!exteriorCells.isEmpty()) {
            Set<BlockPos> allowed = new HashSet<>(traversalCells);
            allowed.removeAll(exteriorCells);
            reachable = connectedCells(seed.feet(), allowed, neighbors, new HashSet<>());
        }
        if (reachable.isEmpty()) {
            return TraversalScan.failure(Building.validationResult.NOT_IN_BUILDING);
        }
        Map<BlockPos, SurfaceCell> retainedCells = traversal.cells();
        Set<Transition> transitions = traversal.transitions();
        Set<BlockPos> verticalBoundaryCells = traversal.verticalBoundaryCells();
        Set<BlockPos> retained = reachable;
        if (retained.size() != traversalCells.size()) {
            retainedCells = new HashMap<>();
            for (BlockPos pos : retained) {
                retainedCells.put(pos, traversal.cells().get(pos));
            }
            transitions = traversal.transitions().stream()
                    .filter(transition -> retained.contains(transition.first())
                            && retained.contains(transition.second()))
                    .collect(Collectors.toSet());
            verticalBoundaryCells = traversal.verticalBoundaryCells().stream()
                    .filter(retained::contains)
                    .collect(Collectors.toSet());
        }
        Set<BlockPos> connectors = traversal.connectors().stream()
                .filter(connector -> retained.contains(StructureConnector.normalize(
                        connector, world.getBlockState(connector))))
                .collect(Collectors.toSet());
        Set<BlockPos> adjacentFloorSeeds = adjacentFloorSeedsForRetainedFloor(
                retainedCells, provider, classifier);
        return new TraversalScan(
                Building.validationResult.SUCCESS,
                retainedCells,
                transitions,
                verticalBoundaryCells,
                adjacentFloorSeeds,
                connectors);
    }

    private static boolean hasAcceptedExposure(
            BlockGetter world, SurfaceCell start, BlockPos scanAnchor, FloorCeilingResolver ceilings,
            int maxSize, int maxRadius, StepProvider provider, FloorBandClassifier classifier) {
        // A complete supported-surface proof must short-circuit before the strict
        // empty-air flood, whose work otherwise grows with radius even for a tiny balcony.
        return classifier.boundedExterior.computeIfAbsent(start.feet(), ignored ->
                hasBoundedExteriorSurface(world, start, scanAnchor, ceilings,
                        maxSize, maxRadius, provider, classifier, true))
                || !reachesExterior(world, start, scanAnchor, ceilings, maxRadius, provider, classifier, true);
    }

    /**
     * Traversal proves enclosure; it is not the canonical Floor. Build structural ownership once
     * from the retained traversal evidence, including supported interior columns occupied by blocks.
     * Ambiguous full-block head obstructions need independent surrounding floor evidence so a
     * one-block wall cavity cannot manufacture Floor ownership.
     */
    private static MaterializedFloor materializeStructuralFloor(
            BlockGetter world,
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
            BlockGetter world,
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
        if (FloorConnector.Type.fromBlockState(state) == FloorConnector.Type.LADDER
                && state.getFluidState().isEmpty()) {
            BlockPos head = feet.above();
            BlockState headState = world.getBlockState(head);
            OptionalDouble surfaceY = floorSurfaceLevel(world, feet);
            if (surfaceY.isPresent() && canStep(adjacent.surfaceY(), surfaceY.getAsDouble())
                    && feet.getY() + 1 < adjacent.ceilingY() && headState.getFluidState().isEmpty()
                    && (isOpen(world, head) || FloorConnector.Type.fromBlockState(headState) == FloorConnector.Type.LADDER)) {
                return Optional.of(new SurfaceCell(feet, surfaceY.getAsDouble(), adjacent.ceilingY()));
            }
            return Optional.empty();
        }
        // A stair placed inside a low-ceiling Room can occupy its structural cell even
        // when there is no traversable cell above it. Surrounding enclosed floor evidence
        // distinguishes this obstruction from a flight leading to another Floor.
        if (state.getBlock() instanceof StairBlock && state.getFluidState().isEmpty()
                && isOpen(world, feet.above()) && inspectSurfaceCell(world, feet.above(), ceilings).isEmpty()) {
            OptionalDouble support = floorSurfaceLevel(world, feet);
            if (support.isPresent() && canStep(adjacent.surfaceY(), support.getAsDouble())
                    && hasSurroundingFloorEvidence(world, feet, support.getAsDouble(), enclosedCells, classifier.provider)) {
                return Optional.of(new SurfaceCell(feet, support.getAsDouble(), adjacent.ceilingY()));
            }
        }
        if (!state.getFluidState().isEmpty()
                || StructureConnector.isConnector(state)
                || classifier.provider.explicitStairCell(world, feet)) {
            return Optional.empty();
        }

        OptionalDouble surfaceY = floorSurfaceLevel(world, feet);
        if (surfaceY.isEmpty() || !canStep(adjacent.surfaceY(), surfaceY.getAsDouble())) {
            return Optional.empty();
        }

        if (isTraversalOccupancyAllowed(world, feet)) {
            // Furniture that cannot be crossed must still retain the structural floor
            // below it; traversal clearance does not define the room footprint.
            if (floorSurfaceY(world, feet).isPresent()) return Optional.empty();
            BlockPos head = feet.above();
            if (world.getBlockState(head).isCollisionShapeFullBlock(world, head)
                    && !hasSurroundingFloorEvidence(
                    world, feet, surfaceY.getAsDouble(), enclosedCells, classifier.provider)) {
                return Optional.empty();
            }
            return Optional.of(new SurfaceCell(
                    feet, surfaceY.getAsDouble(), adjacent.ceilingY()));
        }

        for (int y = feet.getY() + 1; y < adjacent.ceilingY(); y++) {
            BlockPos interior = new BlockPos(feet.getX(), y, feet.getZ());
            if (!isOpen(world, interior)) continue;
            if (!hasInteriorHeadroom(world, interior)
                    && !hasSurroundingFloorEvidence(world, feet, surfaceY.getAsDouble(), enclosedCells, classifier.provider)) {
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
            BlockGetter world,
            BlockPos feet,
            double surfaceY,
            Map<BlockPos, SurfaceCell> enclosedCells,
            StepProvider provider) {
        int neighbors = 0;
        for (Direction direction : HORIZONTAL) {
            BlockPos horizontal = feet.relative(direction);
            boolean ownedNeighbor = false;
            for (SurfaceProbe landing : findLandings(world, surfaceY, horizontal, provider)) {
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
            Map<BlockPos, SurfaceCell> retained,
            StepProvider provider,
            FloorBandClassifier classifier) {
        LinkedHashSet<BlockPos> adjacentFloorSeeds = new LinkedHashSet<>();
        for (SurfaceCell current : retained.values()) {
            FloorBandDecision currentDecision = classifier.decision(current);
            for (HorizontalStep step : provider.steps(current)) {
                SurfaceCell candidate = step.landing();
                if (!currentDecision.continueTraversal()) {
                    if (!retained.containsKey(candidate.feet())) adjacentFloorSeeds.add(candidate.feet());
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
    private static FloorBandDecision floorBandDecision(BlockGetter world,
                                                       FloorBand band,
                                                       SurfaceCell candidate,
                                                       StepProvider provider) {
        int y = candidate.feet().getY();
        if (y < band.minOwnedY()
                || y > band.maxOwnedY()) return FloorBandDecision.ADJACENT;

        boolean stairTransition = stairTransitionReachesBelowBand(world, candidate, provider);
        if (y > band.anchorY()
                && stairTransition
                && hasStableSameHeightPeer(candidate, provider)) {
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
            BlockGetter world, SurfaceCell start, StepProvider provider) {
        return provider.explicitStairCell(world, start.feet())
                && reachesVerticalLevel(start, start.feet().getY() - FLOOR_BAND_RADIUS, provider, new HashSet<>());
    }

    private static boolean reachesVerticalLevel(
            SurfaceCell start, double targetY, StepProvider provider, Set<BlockPos> visited) {
        // Prove a local flight, not a route across level ground to a distant rise/drop.
        // Each step approaches targetY, bounding this search by the requested height change.
        boolean descending = targetY < start.surfaceY();
        ArrayDeque<SurfaceCell> queue = new ArrayDeque<>();
        queue.addLast(start);
        visited.add(start.feet());
        while (!queue.isEmpty()) {
            SurfaceCell current = queue.removeFirst();
            for (HorizontalStep step : provider.steps(current)) {
                if (step.connector() != null) continue;
                SurfaceCell next = step.landing();
                double nextY = next.surfaceY();
                if (descending ? nextY >= current.surfaceY() : nextY <= current.surfaceY()) continue;
                if (descending ? nextY <= targetY : nextY >= targetY) return true;
                if (visited.add(next.feet())) queue.addLast(next);
            }
        }
        return false;
    }

    private static boolean hasDescendingStep(SurfaceCell cell, StepProvider provider) {
        return provider.steps(cell).stream()
                .anyMatch(step -> step.connector() == null
                        && step.landing().surfaceY() < cell.surfaceY());
    }

    static Optional<SurfaceCell> inspectSurfaceCell(
            BlockGetter world, BlockPos feet, FloorCeilingResolver ceilings) {
        if (!isLoadedColumn(world, feet)) return Optional.empty();
        OptionalDouble surfaceY = floorSurfaceY(world, feet);
        if (surfaceY.isEmpty()) return Optional.empty();
        OptionalInt ceiling = ceilings.ceilingY(feet);
        if (ceiling.isEmpty()) return Optional.empty();
        return Optional.of(new SurfaceCell(feet, surfaceY.getAsDouble(), ceiling.getAsInt()));
    }

    private static List<HorizontalStep> worldSteps(
            BlockGetter world, SurfaceCell current, FloorCeilingResolver ceilings, StepProvider provider) {
        List<HorizontalStep> steps = new ArrayList<>();
        for (FloorStep step : floorSteps(world, new SurfaceProbe(current.feet(), current.surfaceY()), provider)) {
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

    private static List<FloorStep> floorSteps(BlockGetter world, SurfaceProbe current, StepProvider provider) {
        List<FloorStep> steps = new ArrayList<>();
        for (Direction direction : HORIZONTAL) {
            BlockPos horizontal = current.feet().relative(direction);
            BlockPos connector = provider.horizontalConnector(world, horizontal);
            if (connector != null) {
                OptionalDouble surfaceY = floorSurfaceY(world, connector);
                if (surfaceY.isPresent() && canStep(current.surfaceY(), surfaceY.getAsDouble())) {
                    steps.add(new FloorStep(new SurfaceProbe(connector, surfaceY.getAsDouble()), connector));
                }
            } else {
                for (SurfaceProbe landing : findLandings(world, current.surfaceY(), horizontal, provider)) {
                    steps.add(new FloorStep(landing, null));
                }
            }
        }
        return List.copyOf(steps);
    }

    private static BlockPos horizontalConnector(BlockGetter world, BlockPos horizontal) {
        for (int dy : LANDING_Y_OFFSETS) {
            BlockPos candidate = horizontal.offset(0, dy, 0);
            BlockState state = world.getBlockState(candidate);
            if (StructureConnector.isHorizontalBoundary(state)) {
                return StructureConnector.normalize(candidate, state);
            }
        }
        return null;
    }

    private static List<SurfaceProbe> findLandings(
            BlockGetter world, double currentSurfaceY, BlockPos horizontal, StepProvider provider) {
        List<SurfaceProbe> landings = new ArrayList<>();
        for (int dy : LANDING_Y_OFFSETS) {
            BlockPos feet = horizontal.offset(0, dy, 0);
            OptionalDouble surfaceY = provider.surfaceY(world, feet);
            if (surfaceY.isPresent() && canStep(currentSurfaceY, surfaceY.getAsDouble())) {
                landings.add(new SurfaceProbe(feet, surfaceY.getAsDouble()));
            }
        }
        return List.copyOf(landings);
    }

    private static boolean reachesExterior(
            BlockGetter world,
            SurfaceCell start,
            BlockPos scanAnchor,
            FloorCeilingResolver ceilings,
            int maxRadius,
            StepProvider provider,
            FloorBandClassifier classifier,
            boolean framedBoundaries) {
        Map<BlockPos, Boolean> exterior = framedBoundaries ? classifier.exterior : new HashMap<>();
        Boolean known = exterior.get(start.feet());
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
                    exterior.put(start.feet(), true);
                    return true;
                }
                for (Direction direction : HORIZONTAL) {
                    BlockPos next = open.relative(direction);
                    if (framedBoundaries && provider.frameCrossing(open, next)) continue;
                    if (!isLoadedColumn(world, next)) {
                        exterior.put(start.feet(), true);
                        return true;
                    }
                    if (visitedOpen.contains(next) || !isOpenPassage(world, next)) continue;
                    if (horizontalDistance(next, scanAnchor) >= maxRadius) continue;
                    visitedOpen.add(next);
                    openQueue.addLast(next);
                }
                continue;
            }

            SurfaceCell current = floorQueue.removeFirst();
            if (horizontalDistance(current.feet(), scanAnchor) >= maxRadius - 1) {
                exterior.put(start.feet(), true);
                return true;
            }
            Boolean reachable = exterior.get(current.feet());
            if (Boolean.TRUE.equals(reachable)) {
                exterior.put(start.feet(), true);
                return true;
            }
            if (Boolean.FALSE.equals(reachable)) continue;
            for (Direction direction : HORIZONTAL) {
                BlockPos opening = current.feet().relative(direction);
                if (framedBoundaries && provider.frameCrossing(current.feet(), opening)) continue;
                if (!isLoadedColumn(world, opening)) {
                    exterior.put(start.feet(), true);
                    return true;
                }
                // Opening detection and supported traversal use the same landing evidence.
                List<SurfaceProbe> landings = findLandings(world, current.surfaceY(), opening, provider);
                if (landings.isEmpty() && isOpenPassage(world, opening) && visitedOpen.add(opening)) {
                    openQueue.addLast(opening);
                }
                if (provider.horizontalConnector(world, opening) != null) continue;
                for (SurfaceProbe landing : landings) {
                    BlockPos next = landing.feet();
                    if (visitedFloor.contains(next)) continue;
                    SurfaceCell probe = new SurfaceCell(next, landing.surfaceY(), next.getY() + 2);
                    FloorBandDecision decision = classifier.decision(probe);
                    if (!decision.owned() || !decision.continueTraversal()) continue;
                    OptionalInt ceiling = ceilings.ceilingY(next);
                    if (ceiling.isEmpty()) {
                        // Edges can be directional: only the start is proven to reach this exit.
                        exterior.put(start.feet(), true);
                        return true;
                    }
                    SurfaceCell cell = new SurfaceCell(next, landing.surfaceY(), ceiling.getAsInt());
                    visitedFloor.add(next);
                    floorQueue.addLast(cell);
                }
            }
        }
        // Exhausting the traversal proves every visited cell has no route outside.
        visitedFloor.forEach(cell -> exterior.put(cell, false));
        return false;
    }

    /**
     * Proves a complete, naturally terminated supported surface, not a truncated air flood.
     * Uncovered cells are exposure evidence only; they never acquire synthetic ceilings or
     * become canonical Floor cells. Existing storey/connector boundaries remain in force.
     */
    private static boolean hasBoundedExteriorSurface(
            BlockGetter world, SurfaceCell start, BlockPos scanAnchor, FloorCeilingResolver ceilings,
            int maxSize, int maxRadius, StepProvider provider, FloorBandClassifier classifier,
            boolean framedBoundaries) {
        ArrayDeque<SurfaceProbe> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.addLast(new SurfaceProbe(start.feet(), start.surfaceY()));
        visited.add(start.feet());
        int roofed = 0;
        int exposed = 0;
        long proofLimit = (long) maxSize + MAX_EXPOSED_SURFACE_CELLS;
        while (!queue.isEmpty()) {
            SurfaceProbe current = queue.removeFirst();
            if (horizontalDistance(current.feet(), scanAnchor) >= maxRadius - 1) return false;
            // An unloaded neighbor is unknown, not a natural edge. Never load chunks
            // merely to prove that an exposed surface is small enough to accept.
            for (Direction direction : HORIZONTAL) {
                if (!isLoadedColumn(world, current.feet().relative(direction))) return false;
            }
            if (ceilings.ceilingY(current.feet()).isPresent()) {
                if (++roofed > maxSize) return false;
            } else if (++exposed > MAX_EXPOSED_SURFACE_CELLS) {
                return false;
            }
            for (FloorStep step : floorSteps(world, current, provider)) {
                if (step.connector() != null) continue;
                SurfaceProbe next = step.landing();
                if (framedBoundaries && provider.frameCrossing(current.feet(), next.feet())) continue;
                if (visited.contains(next.feet())) continue;
                OptionalInt ceiling = ceilings.ceilingY(next.feet());
                // Uncovered supports have no canonical storey owner. Probing a flight
                // from each outdoor cell would extend discovery beyond this bounded proof.
                if (ceiling.isEmpty() && (next.feet().getY() < classifier.band.minOwnedY()
                        || next.feet().getY() > classifier.band.maxOwnedY())) return false;
                FloorBandDecision decision = ceiling.isPresent() ? classifier.decision(new SurfaceCell(
                        next.feet(), next.surfaceY(), ceiling.getAsInt()))
                        : FloorBandDecision.OWNED;
                if (!decision.owned() || !decision.continueTraversal()) {
                    continue;
                }
                visited.add(next.feet());
                if (visited.size() > proofLimit) return false;
                queue.addLast(next);
            }
        }
        return roofed > 0;
    }

    private static boolean isOpenPassage(BlockGetter world, BlockPos feet) {
        return isOpen(world, feet) && isOpen(world, feet.above());
    }

    @SuppressWarnings("deprecation") // LevelReader's non-loading chunk availability contract.
    private static boolean isLoadedColumn(BlockGetter world, BlockPos pos) {
        if (world instanceof LoadedColumns loaded) return loaded.hasColumn(pos);
        return !(world instanceof LevelReader reader) || reader.hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static void collectVerticalConnectors(
            BlockGetter world, FloorGeometry floor, Set<BlockPos> connectors) {
        int baseY = floor.minFeetY();
        var heightsByColumn = new Long2ObjectOpenHashMap<BitSet>();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (FloorGeometry.Cell cell : floor.cells()) {
            probe.set(cell.feet().getX(), cell.feet().getY() - 1, cell.feet().getZ());
            collectVerticalConnectorAt(world, probe, true, connectors);
            heightsByColumn.computeIfAbsent(FloorGeometry.columnKey(probe.getX(), probe.getZ()),
                    ignored -> new BitSet()).set(cell.feet().getY() - baseY, cell.ceilingY() - baseY);
            for (Direction direction : HORIZONTAL) {
                probe.set(cell.feet().getX() + direction.getStepX(), cell.feet().getY() - 1,
                        cell.feet().getZ() + direction.getStepZ());
                // Each exact floor bottom retains its own short-gap check, including
                // uneven floors. Only direct upper probes can be safely unioned.
                collectVerticalConnectorAt(world, probe, true, connectors);
                heightsByColumn.computeIfAbsent(FloorGeometry.columnKey(probe.getX(), probe.getZ()),
                        ignored -> new BitSet()).set(cell.feet().getY() - baseY, cell.ceilingY() - baseY);
            }
        }
        for (var entry : heightsByColumn.long2ObjectEntrySet()) {
            long column = entry.getLongKey();
            BitSet heights = entry.getValue();
            for (int bit = heights.nextSetBit(0); bit >= 0; bit = heights.nextSetBit(bit + 1)) {
                probe.set((int) (column >> 32), baseY + bit, (int) column);
                collectVerticalConnectorAt(world, probe, false, connectors);
            }
        }
    }

    private static void collectVerticalConnectorAt(
            BlockGetter world, BlockPos candidate, boolean includeTopExit, Set<BlockPos> connectors) {
        BlockPos connector;
        if (includeTopExit) {
            connector = StructureConnector.verticalInteractionConnector(world, candidate);
        } else {
            FloorConnector.Type type = FloorConnector.Type.fromBlockState(world.getBlockState(candidate));
            connector = type != null && type.vertical() ? candidate : null;
        }
        if (connector != null && !connectors.contains(connector) && StructureConnector.isVertical(world, connector)) {
            connectors.add(connector.immutable());
        }
    }

    private static OptionalDouble floorSurfaceY(BlockGetter world, BlockPos feet) {
        if (!isTraversalOccupancyAllowed(world, feet) || !hasInteriorHeadroom(world, feet)) {
            return OptionalDouble.empty();
        }
        OptionalDouble structuralSurface = floorSurfaceLevel(world, feet);
        if (structuralSurface.isEmpty()) return structuralSurface;
        BlockState state = world.getBlockState(feet);
        var obstacle = state.getCollisionShape(world, feet);
        if (obstacle.isEmpty() || StructureConnector.isHorizontalBoundary(state)) return structuralSurface;

        // Structural membership stays at the underlying floor. Movement over furniture
        // must use its actual top, including the headroom above that raised surface.
        double walkingY = Math.max(structuralSurface.getAsDouble(),
                feet.getY() + obstacle.max(Direction.Axis.Y));
        double headY = walkingY + EntityType.PLAYER.getHeight();
        BlockPos head = BlockPos.containing(feet.getX(), headY - 1.0E-6D, feet.getZ());
        var headCollision = world.getBlockState(head).getCollisionShape(world, head);
        if (!headCollision.isEmpty() && head.getY() + headCollision.min(Direction.Axis.Y) < headY) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(walkingY);
    }

    private static OptionalDouble floorSurfaceLevel(BlockGetter world, BlockPos feet) {
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
    private static boolean isTraversalOccupancyAllowed(BlockGetter world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) return false;
        if (StructureConnector.isHorizontalBoundary(state)) return true;
        var shape = state.getCollisionShape(world, pos);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) < 1.0D;
    }

    private static OptionalDouble interactionHandoffY(
            BlockGetter world, BlockPos pos, FloorCeilingResolver ceilings) {
        if (!StructureConnector.isVerticalTopExit(world, pos)) return OptionalDouble.empty();
        if (inspectSurfaceCell(world, pos.above(), ceilings).isPresent()) return OptionalDouble.empty();
        return OptionalDouble.of(pos.getY());
    }

    private static boolean hasInteriorHeadroom(BlockGetter world, BlockPos feet) {
        BlockPos above = feet.above();
        if (isOpen(world, above)) return true;
        BlockState feetState = world.getBlockState(feet);
        BlockState aboveState = world.getBlockState(above);
        return StructureConnector.isHorizontalBoundary(feetState)
                && StructureConnector.isHorizontalBoundary(aboveState)
                && StructureConnector.normalize(feet, feetState)
                .equals(StructureConnector.normalize(above, aboveState));
    }

    private static boolean isLowObstacle(BlockGetter world, BlockPos pos) {
        return isTraversalOccupancyAllowed(world, pos) && !isOpen(world, pos);
    }

    private static boolean isOpen(BlockGetter world, BlockPos pos) {
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
                                  int anchorY,
                                  FloorGeometry geometry,
                                  Set<Transition> transitions,
                                  Set<BlockPos> verticalBoundaryCells,
                                  Set<BlockPos> adjacentFloorSeeds) {
        FloorGeometry.Bounds bounds = FloorGeometry.bounds(geometry.cells(), -1);
        return new Result(Building.validationResult.SUCCESS, geometry, seed, supportedSource, anchorY,
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
            // Room connectivity is undirected. Movement/exterior discovery keeps
            // its directional steps separately; do not store both room edge orders.
            if (CELL_ORDER.compare(first, second) > 0) {
                BlockPos previousFirst = first;
                first = second;
                second = previousFirst;
            }
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
            // This map and its immutable lists are locally owned. Keep its hashed
            // buckets rather than rebuilding dense coordinates in a linear-probe table.
            return Collections.unmodifiableMap(frozen);
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
        private final BlockGetter world;
        private final FloorBand band;
        private final StepProvider provider;
        private final TransitionOwnership transitionsOwner;
        private final SurfaceRegion selectedRegion;
        private final Map<BlockPos, FloorBandDecision> decisions = new HashMap<>();
        private final Map<BlockPos, Boolean> exterior = new HashMap<>();
        private final Map<BlockPos, Boolean> boundedExterior = new HashMap<>();

        private FloorBandClassifier(BlockGetter world, FloorBand band, StepProvider provider,
                                    TransitionOwnership transitionsOwner, SurfaceRegion selectedRegion) {
            this.selectedRegion = selectedRegion;
            this.world = world;
            this.band = band;
            this.provider = provider;
            this.transitionsOwner = transitionsOwner;
        }

        private FloorBandDecision decision(SurfaceCell cell) {
            return decisions.computeIfAbsent(cell.feet(), ignored -> {
                SurfaceRegion owner = transitionsOwner == null ? null : transitionsOwner.owner(cell);
                if (owner != null) {
                    return selectedRegion == owner
                            ? FloorBandDecision.OWNED : FloorBandDecision.ADJACENT;
                }
                return floorBandDecision(world, band, cell, provider);
            });
        }
    }

    /** Stable surface evidence; transition rows do not vote for the representative height. */
    private record SurfaceRegion(SurfaceCell surface) {
        int anchorY() {
            return surface.feet().getY();
        }
    }

    /** Local ordinary regions, without recursive discovery through another storey's flights. */
    private static final class RegionDiscovery {
        private final BlockGetter world;
        private final FloorCeilingResolver ceilings;
        private final StepProvider provider;
        private final int maxSize;
        private final int maxRadius;
        private final Map<BlockPos, SurfaceRegion> resolved = new HashMap<>();

        private RegionDiscovery(BlockGetter world, FloorCeilingResolver ceilings,
                                StepProvider provider, int maxSize, int maxRadius) {
            this.world = world;
            this.ceilings = ceilings;
            this.provider = provider;
            this.maxSize = maxSize;
            this.maxRadius = maxRadius;
        }

        private SurfaceRegion region(SurfaceCell seed) {
            SurfaceRegion cached = resolved.get(seed.feet());
            if (cached != null) return cached;
            Map<BlockPos, SurfaceCell> cells = new LinkedHashMap<>();
            ArrayDeque<SurfaceCell> queue = new ArrayDeque<>();
            Set<Transition> transitions = new HashSet<>();
            Set<BlockPos> doors = new HashSet<>();
            // Doors divide Rooms, not physical Floors. Only accepted continuations may vote
            // for the Floor anchor; unbounded terrain must not move an interior's anchor.
            FloorBandClassifier enclosure = new FloorBandClassifier(
                    world, new FloorBand(seed.feet().getY()), provider, null, null);
            if (!hasAcceptedExposure(world, seed, seed.feet(), ceilings,
                    maxSize, maxRadius, provider, enclosure)) {
                SurfaceRegion exterior = new SurfaceRegion(seed);
                resolved.put(seed.feet(), exterior);
                return exterior;
            }
            queue.add(seed);
            cells.put(seed.feet(), seed);
            while (!queue.isEmpty()) {
                SurfaceCell current = queue.removeFirst();
                boolean realBoundary = StructureConnector.isHorizontalBoundary(world.getBlockState(current.feet()));
                boolean boundary = realBoundary || isFramedBoundary(current.feet(), provider);
                // Empty frames only bound exposure; accepted roofed sides still vote as
                // one ordinary region. Only real connectors divide anchor representatives.
                if (realBoundary) doors.add(current.feet());
                for (HorizontalStep step : provider.steps(current)) {
                    SurfaceCell next = step.landing();
                    if (provider.explicitStairCell(world, next.feet())
                            || Math.abs(next.feet().getY() - seed.feet().getY()) > FLOOR_BAND_RADIUS
                            || horizontalDistance(next.feet(), seed.feet()) >= maxRadius
                            || cells.size() >= maxSize) continue;
                    if (boundary
                            && !hasAcceptedExposure(world, next, seed.feet(), ceilings,
                            maxSize, maxRadius, provider, enclosure)) continue;
                    transitions.add(new Transition(current.feet(), next.feet()));
                    if (cells.putIfAbsent(next.feet(), next) == null) queue.addLast(next);
                }
            }
            // An upstairs Room must not outvote a smaller, uneven continuation across its door.
            // Each enclosed Room contributes one representative; the lower representative
            // supplies the common traversal band, independently of final geometry's modal height.
            int anchorY = connectedRegions(cells.keySet(), doors, Transition.neighborIndex(transitions)).stream()
                    .mapToInt(region -> {
                        Map<Integer, Long> heights = region.stream().map(cells::get)
                                .collect(Collectors.groupingBy(cell -> cell.feet().getY(), Collectors.counting()));
                        return heights.entrySet().stream().max(Comparator.<Map.Entry<Integer, Long>>comparingLong(Map.Entry::getValue)
                                .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                                .map(Map.Entry::getKey).orElse(seed.feet().getY());
                    }).min().orElse(seed.feet().getY());
            SurfaceCell anchor = cells.values().stream().filter(cell -> cell.feet().getY() == anchorY)
                    .filter(cell -> !provider.explicitStairCell(world, cell.feet()))
                    .filter(cell -> !doors.contains(cell.feet()))
                    .min(Comparator.comparing(SurfaceCell::feet, CELL_ORDER)).orElse(seed);
            for (SurfaceCell cell : cells.values()) {
                SurfaceRegion existing = resolved.get(cell.feet());
                if (existing != null && existing.anchorY() == anchorY) {
                    resolved.put(seed.feet(), existing);
                    return existing;
                }
            }
            SurfaceRegion region = new SurfaceRegion(anchor);
            for (BlockPos pos : cells.keySet()) resolved.putIfAbsent(pos, region);
            return region;
        }
    }

    /** One operation's local flights; floor selection and exact geometry share this allocation. */
    private static final class TransitionOwnership {
        private final BlockGetter world;
        private final StepProvider provider;
        private final int maxSize;
        private final int maxRadius;
        private final RegionDiscovery regions;
        private final Map<BlockPos, SurfaceRegion> owners = new HashMap<>();
        private final Set<BlockPos> rejected = new HashSet<>();

        private TransitionOwnership(BlockGetter world, StepProvider provider, RegionDiscovery regions,
                                    int maxSize, int maxRadius) {
            this.regions = regions;
            this.world = world;
            this.provider = provider;
            this.maxSize = maxSize;
            this.maxRadius = maxRadius;
        }

        private SurfaceRegion owner(SurfaceCell seed) {
            if (owners.containsKey(seed.feet())) return owners.get(seed.feet());
            if (rejected.contains(seed.feet())) return null;

            Map<BlockPos, SurfaceRegion> flight = discover(seed);
            owners.putAll(flight);
            if (flight.isEmpty()) rejected.add(seed.feet());
            return owners.get(seed.feet());
        }

        private Map<BlockPos, SurfaceRegion> discover(SurfaceCell seed) {
            List<HorizontalStep> seedSteps = provider.steps(seed);
            boolean ascending = seedSteps.stream().anyMatch(step -> step.connector() == null
                    && step.landing().surfaceY() > seed.surfaceY());
            boolean descending = hasDescendingStep(seed, provider);
            // A flat corner inside a turn may have its rise/drop one tile away.
            if (!ascending && !descending && !provider.throughLanding(seed)) return reject(seed.feet());
            // A one-way ordinary step must continue vertically before it can be a flight.
            // Otherwise a long curb/porch explores its whole width to rediscover one height.
            if (ascending != descending && !provider.explicitStairCell(world, seed.feet())
                    && !provider.throughLanding(seed)) {
                Set<BlockPos> checked = new HashSet<>();
                double targetY = seed.surfaceY() + (ascending ? FLOOR_BAND_RADIUS : -FLOOR_BAND_RADIUS);
                if (!reachesVerticalLevel(seed, targetY, provider, checked)) return reject(checked);
            }
            boolean stableSeed = hasStableSameHeightPeer(seed, provider);
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
                boolean stable = hasStableSameHeightPeer(current, provider);
                if (stable) {
                    landings.add(current);
                    if (!current.feet().equals(seed.feet())) continue;
                }
                for (HorizontalStep step : provider.steps(current)) {
                    if (step.connector() != null) continue;
                    SurfaceCell next = step.landing();
                    if (stable && next.surfaceY() == current.surfaceY()) continue;
                    if (queued.add(next.feet())) queue.addLast(next);
                }
            }

            List<Double> landingHeights = landings.stream().map(SurfaceCell::surfaceY)
                    .distinct().sorted().toList();
            if (landingHeights.size() != 2
                    || landingHeights.getLast() - landingHeights.getFirst() < 2) {
                return reject(cells.keySet());
            }
            SurfaceCell lower = landings.stream()
                    .filter(cell -> cell.surfaceY() == landingHeights.getFirst())
                    .min(Comparator.comparing(SurfaceCell::feet, CELL_ORDER)).orElseThrow();
            SurfaceCell upper = landings.stream()
                    .filter(cell -> cell.surfaceY() == landingHeights.getLast())
                    .min(Comparator.comparing(SurfaceCell::feet, CELL_ORDER)).orElseThrow();
            boolean explicitStairs = cells.keySet().stream()
                    .anyMatch(pos -> provider.explicitStairCell(world, pos));
            // Ordinary supports within the existing uneven-floor range are not proof of stairs.
            if (!explicitStairs && upper.surfaceY() - lower.surfaceY() <= FLOOR_BAND_RADIUS) {
                return reject(cells.keySet());
            }
            if (cells.values().stream().anyMatch(cell -> cell.surfaceY() < lower.surfaceY()
                    || cell.surfaceY() > upper.surfaceY())) return reject(cells.keySet());
            List<Double> stepHeights = cells.values().stream()
                    .filter(cell -> !explicitStairs || provider.explicitStairCell(world, cell.feet())
                            || !hasStableSameHeightPeer(cell, provider))
                    .map(SurfaceCell::surfaceY).distinct().sorted().toList();
            if (stepHeights.size() < 2) return reject(cells.keySet());
            // Count rows rather than blocks across the width. The odd middle row belongs upstairs.
            double upperStartY = stepHeights.get(stepHeights.size() / 2);
            SurfaceRegion lowerRegion = regions.region(lower);
            SurfaceRegion upperRegion = regions.region(upper);
            Map<BlockPos, SurfaceRegion> allocation = new HashMap<>();
            for (SurfaceCell cell : cells.values()) {
                allocation.put(cell.feet(), cell.surfaceY() < upperStartY ? lowerRegion : upperRegion);
            }
            return allocation;
        }

        private Map<BlockPos, SurfaceRegion> reject(BlockPos cell) {
            rejected.add(cell);
            return Map.of();
        }

        private Map<BlockPos, SurfaceRegion> reject(Collection<BlockPos> cells) {
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

        default Optional<FramedDoorway> framedDoorway(BlockPos feet, Direction.Axis normalAxis) {
            return Optional.empty();
        }

        default boolean frameCrossing(BlockPos first, BlockPos second) {
            Direction.Axis axis = first.getX() != second.getX() ? Direction.Axis.X : Direction.Axis.Z;
            return framedDoorway(first, axis).isPresent() || framedDoorway(second, axis).isPresent();
        }

        default boolean stableLanding(SurfaceCell cell) {
            // Consecutive half-slab steps may share integer feet Y without forming a landing.
            return steps(cell).stream().filter(step -> step.connector() == null)
                    .map(HorizontalStep::landing).filter(peer -> peer.surfaceY() == cell.surfaceY())
                    .anyMatch(peer -> !hasDescendingStep(peer, this));
        }

        default boolean throughLanding(SurfaceCell cell) {
            return false;
        }

        default boolean explicitStairCell(BlockGetter world, BlockPos feet) {
            return world.getBlockState(feet).getBlock() instanceof StairBlock
                    || world.getBlockState(feet.below()).getBlock() instanceof StairBlock;
        }

        default OptionalDouble surfaceY(BlockGetter world, BlockPos feet) {
            return floorSurfaceY(world, feet);
        }

        default BlockPos horizontalConnector(BlockGetter world, BlockPos horizontal) {
            return SelectedFloorScanner.horizontalConnector(world, horizontal);
        }
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

    private static boolean isFramedBoundary(BlockPos feet, StepProvider provider) {
        return provider.framedDoorway(feet, Direction.Axis.X).isPresent()
                || provider.framedDoorway(feet, Direction.Axis.Z).isPresent();
    }

    private static boolean isDiscoveryBoundary(BlockGetter world, BlockPos feet, StepProvider provider) {
        return StructureConnector.isHorizontalBoundary(world.getBlockState(feet)) || isFramedBoundary(feet, provider);
    }

    private record FrameKey(BlockPos feet, Direction.Axis normalAxis) { }

    record FramedDoorway(Direction.Axis normalAxis, List<BlockPos> cells) {
        FramedDoorway { cells = List.copyOf(cells); }
    }

    private record SurfaceProbe(BlockPos feet, double surfaceY) {
    }

    /** The selected Floor cell and, when supported directly, the input that selected it. */
    record Result(Building.validationResult result,
                  FloorGeometry floor,
                  BlockPos seed,
                  BlockPos supportedSource,
                  int anchorY,
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
            return new Result(result, null, source, null, source.getY(), source, source,
                    Set.of(), Set.of(), Set.of());
        }
    }
}
