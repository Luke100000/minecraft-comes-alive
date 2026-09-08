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

/** Discovers one selected semantic floor while retaining exact Minecraft surface heights. */
final class SelectedFloorScanner {
    private static final int MIN_MEANINGFUL_HEIGHT_SLICE_AREA = 4;
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
        BlockPos floorSeed = seedCell.feet();
        if (reachesExterior(world, seedCell, floorSeed.getY(), ceilings, maxRadius, null)) {
            return Result.failure(Building.validationResult.NOT_IN_BUILDING, seed);
        }

        ArrayDeque<SurfaceCell> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        LinkedHashMap<BlockPos, SurfaceCell> cells = new LinkedHashMap<>();
        LinkedHashSet<BlockPos> connectors = new LinkedHashSet<>();
        LinkedHashSet<Transition> transitions = new LinkedHashSet<>();
        queue.addLast(seedCell);
        visited.add(seedCell.feet());

        while (!queue.isEmpty()) {
            SurfaceCell current = queue.removeFirst();
            if (horizontalDistance(current.feet(), floorSeed) >= maxRadius) {
                return Result.failure(Building.validationResult.SIZE_LIMIT, seed);
            }
            cells.put(current.feet(), current);
            collectVerticalConnectors(world, floorSeed.getY(), current.feet(), connectors);

            for (Direction direction : HORIZONTAL) {
                enqueueHorizontalLanding(world, floorSeed, current, direction, maxRadius,
                        visited, queue, connectors, transitions, ceilings);
            }

            if (cells.size() + connectors.size() > maxSize) {
                return Result.failure(Building.validationResult.BLOCK_LIMIT, seed);
            }
        }

        FloorSelection selection;
        try {
            selection = floorSelection(cells.values(), seedCell.feet());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("SelectedFloorScanner seed=" + seed
                    + " discoveredCells=" + cells.size()
                    + " connectors=" + connectors.size()
                    + ": " + e.getMessage(), e);
        }
        FloorGeometry floor = selection.selected();
        floor = StructureConnector.withConnectorAssociations(
                floor, StructureConnector.associatedFloorCells(world, connectors, floor));
        List<FloorGeometry> connectedFloors = new ArrayList<>(selection.connected().size());
        for (FloorGeometry connected : selection.connected()) {
            connectedFloors.add(connected == selection.selected() ? floor : connected);
        }
        Set<BlockPos> selectedCells = floor.cells().stream()
                .map(FloorGeometry.Cell::feet)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<Transition> selectedTransitions = transitions.stream()
                .filter(edge -> selectedCells.contains(edge.first()) && selectedCells.contains(edge.second()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return success(seed, floor, selectedTransitions, connectedFloors);
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

    static FloorSelection floorSelection(Collection<SurfaceCell> discovered, BlockPos seed) {
        if (discovered == null || discovered.isEmpty() || seed == null) {
            return new FloorSelection(null, List.of());
        }
        SemanticBands semantic = semanticBands(discovered);
        HeightBand selectedBand = semantic.owner(seed).orElseGet(() ->
                selectHeightBand(semantic.bands(), semantic.discoveredHeights(), seed.getY()));
        if (selectedBand == null) return new FloorSelection(null, List.of());

        LinkedHashMap<HeightBand, FloorGeometry> floors = new LinkedHashMap<>();
        for (HeightBand band : semantic.bands()) {
            Set<FloorGeometry.Cell> cells = discovered.stream()
                    .filter(cell -> band.equals(semantic.ownerByCell().get(cell.feet())))
                    .map(SurfaceCell::canonical)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (cells.isEmpty()) continue;
            floors.put(band, new FloorGeometry(cells, Map.of()));
        }
        return new FloorSelection(floors.get(selectedBand), List.copyOf(floors.values()));
    }

    private static SemanticBands semanticBands(Collection<SurfaceCell> discovered) {
        TreeMap<Integer, List<SurfaceCell>> cellsByY = cellsByHeight(discovered);
        Map<Integer, List<Set<SurfaceCell>>> componentsByY = sliceComponentsByHeight(cellsByY);
        List<HeightBand> bands = heightBands(componentsByY);
        if (bands.isEmpty()) {
            int fallbackY = cellsByY.firstKey();
            bands = List.of(new HeightBand(fallbackY));
        }

        Map<BlockPos, HeightBand> owners = new LinkedHashMap<>();
        Map<Long, List<SurfaceCell>> byColumn = cellsByColumn(discovered);

        for (Map.Entry<Integer, List<Set<SurfaceCell>>> entry : componentsByY.entrySet()) {
            int y = entry.getKey();
            HeightBand nominal = selectHeightBand(bands, cellsByY.keySet(), y);
            for (Set<SurfaceCell> component : entry.getValue()) {
                if (component.size() >= MIN_MEANINGFUL_HEIGHT_SLICE_AREA) {
                    component.forEach(cell -> owners.put(cell.feet(), nominal));
                }
            }
        }

        for (Map.Entry<Integer, List<Set<SurfaceCell>>> entry : componentsByY.entrySet()) {
            int y = entry.getKey();
            HeightBand nominal = selectHeightBand(bands, cellsByY.keySet(), y);
            for (Set<SurfaceCell> component : entry.getValue()) {
                if (component.stream().allMatch(cell -> owners.containsKey(cell.feet()))) continue;

                HeightBand lowerOwner = component.stream()
                        .flatMap(cell -> adjacentWalkableCells(cell, byColumn).stream())
                        .filter(neighbor -> neighbor.feet().getY() < y)
                        .map(neighbor -> owners.get(neighbor.feet()))
                        .filter(java.util.Objects::nonNull)
                        .max(Comparator.comparingInt(HeightBand::minY))
                        .orElse(null);
                HeightBand owner = lowerOwner != null && nominal != null && y == nominal.minY()
                        && lowerOwner.minY() < nominal.minY()
                        ? lowerOwner : nominal;
                if (owner == null) owner = bands.getFirst();
                HeightBand finalOwner = owner;
                component.forEach(cell -> owners.put(cell.feet(), finalOwner));
            }
        }

        return new SemanticBands(bands, Set.copyOf(cellsByY.keySet()), Map.copyOf(owners));
    }

    private static Map<Integer, List<Set<SurfaceCell>>> sliceComponentsByHeight(
            Map<Integer, List<SurfaceCell>> cellsByY) {
        Map<Integer, List<Set<SurfaceCell>>> componentsByY = new TreeMap<>();
        cellsByY.forEach((y, cells) -> componentsByY.put(y, sliceComponents(cells)));
        return componentsByY;
    }

    private static Map<Long, List<SurfaceCell>> cellsByColumn(Collection<SurfaceCell> cells) {
        Map<Long, List<SurfaceCell>> indexed = new LinkedHashMap<>();
        for (SurfaceCell cell : cells) {
            indexed.computeIfAbsent(FloorGeometry.columnKey(cell.feet().getX(), cell.feet().getZ()),
                    ignored -> new ArrayList<>()).add(cell);
        }
        indexed.replaceAll((ignored, column) -> List.copyOf(column));
        return Map.copyOf(indexed);
    }

    private static List<Set<SurfaceCell>> sliceComponents(Collection<SurfaceCell> cells) {
        Map<Long, SurfaceCell> byColumn = cells.stream().collect(java.util.stream.Collectors.toMap(
                cell -> FloorGeometry.columnKey(cell.feet().getX(), cell.feet().getZ()),
                cell -> cell,
                (first, ignored) -> first));
        Set<BlockPos> visited = new HashSet<>();
        List<Set<SurfaceCell>> components = new ArrayList<>();
        for (SurfaceCell seed : cells) {
            if (!visited.add(seed.feet())) continue;
            LinkedHashSet<SurfaceCell> component = new LinkedHashSet<>();
            ArrayDeque<SurfaceCell> queue = new ArrayDeque<>();
            queue.addLast(seed);
            while (!queue.isEmpty()) {
                SurfaceCell current = queue.removeFirst();
                component.add(current);
                for (Direction direction : HORIZONTAL) {
                    SurfaceCell next = byColumn.get(FloorGeometry.columnKey(
                            current.feet().getX() + direction.getStepX(),
                            current.feet().getZ() + direction.getStepZ()));
                    if (next == null || visited.contains(next.feet())
                            || !canStep(current.surfaceY(), next.surfaceY())) continue;
                    visited.add(next.feet());
                    queue.addLast(next);
                }
            }
            components.add(Set.copyOf(component));
        }
        return List.copyOf(components);
    }

    private static List<SurfaceCell> adjacentWalkableCells(
            SurfaceCell cell,
            Map<Long, List<SurfaceCell>> byColumn) {
        List<SurfaceCell> adjacent = new ArrayList<>();
        for (Direction direction : HORIZONTAL) {
            long key = FloorGeometry.columnKey(
                    cell.feet().getX() + direction.getStepX(),
                    cell.feet().getZ() + direction.getStepZ());
            for (SurfaceCell candidate : byColumn.getOrDefault(key, List.of())) {
                if (canStep(cell.surfaceY(), candidate.surfaceY())) adjacent.add(candidate);
            }
        }
        return List.copyOf(adjacent);
    }

    private static TreeMap<Integer, List<SurfaceCell>> cellsByHeight(
            Collection<SurfaceCell> discovered) {
        TreeMap<Integer, List<SurfaceCell>> cellsByY = new TreeMap<>();
        for (SurfaceCell cell : new LinkedHashSet<>(discovered)) {
            cellsByY.computeIfAbsent(cell.feet().getY(), ignored -> new ArrayList<>()).add(cell);
        }
        return cellsByY;
    }

    private static List<HeightBand> heightBands(Map<Integer, List<Set<SurfaceCell>>> componentsByY) {
        List<HeightBand> bands = new ArrayList<>();
        for (Map.Entry<Integer, List<Set<SurfaceCell>>> entry : componentsByY.entrySet()) {
            if (!meaningfulHeightSlice(entry.getValue())) continue;
            HeightBand current = bands.isEmpty() ? null : bands.getLast();
            if (current == null || entry.getKey() - current.minY() > StructureFloor.BAND_TOLERANCE) {
                bands.add(new HeightBand(entry.getKey()));
            }
        }
        return List.copyOf(bands);
    }

    private static HeightBand selectHeightBand(List<HeightBand> bands, Set<Integer> discoveredHeights, int seedY) {
        HeightBand selected = bands.stream()
                .filter(band -> band.contains(seedY))
                .findFirst()
                .orElseGet(() -> bands.stream()
                        .min(Comparator.comparingInt((HeightBand band) -> band.distanceTo(seedY))
                                .thenComparingInt(HeightBand::minY))
                        .orElse(null));
        if (selected != null) return selected;
        int fallbackMinY = discoveredHeights.stream()
                .filter(y -> y <= seedY && seedY - y <= StructureFloor.BAND_TOLERANCE)
                .min(Integer::compareTo)
                .orElse(seedY);
        return new HeightBand(fallbackMinY);
    }

    private static boolean meaningfulHeightSlice(Collection<Set<SurfaceCell>> components) {
        return components.stream()
                .anyMatch(component -> component.size() >= MIN_MEANINGFUL_HEIGHT_SLICE_AREA);
    }

    static Optional<SurfaceCell> inspectSurfaceCell(
            Level world, BlockPos feet, FloorCeilingResolver ceilings) {
        OptionalDouble surfaceY = supportedSurfaceY(world, feet);
        if (surfaceY.isEmpty()) return Optional.empty();
        OptionalInt ceiling = ceilings.ceilingY(feet);
        if (ceiling.isEmpty()) return Optional.empty();
        return Optional.of(new SurfaceCell(feet, surfaceY.getAsDouble(), ceiling.getAsInt()));
    }

    private static void enqueueHorizontalLanding(
            Level world,
            BlockPos seed,
            SurfaceCell current,
            Direction direction,
            int maxRadius,
            Set<BlockPos> visited,
            ArrayDeque<SurfaceCell> queue,
            Set<BlockPos> connectors,
            Set<Transition> transitions,
            FloorCeilingResolver ceilings) {
        BlockPos horizontal = current.feet().relative(direction);

        for (int dy : LANDING_Y_OFFSETS) {
            BlockPos candidate = horizontal.offset(0, dy, 0);
            BlockState state = world.getBlockState(candidate);
            if (!StructureConnector.isConnector(state)) continue;

            BlockPos connector = StructureConnector.normalize(candidate, state);
            connectors.add(connector);
            if (!StructureConnector.isHorizontalBoundary(state)) return;

            SurfaceCell farSide = findLanding(
                    world, current.surfaceY(), connector.relative(direction), ceilings)
                    .orElse(null);
            if (farSide == null || visited.contains(farSide.feet())) return;
            if (horizontalDistance(farSide.feet(), seed) >= maxRadius) return;
            if (reachesExterior(world, farSide, farSide.feet().getY(), ceilings, maxRadius, connector)) return;
            visited.add(farSide.feet());
            queue.addLast(farSide);
            return;
        }

        SurfaceCell landing = findLanding(
                world, current.surfaceY(), horizontal, ceilings).orElse(null);
        if (landing == null || visited.contains(landing.feet())) return;
        if (horizontalDistance(landing.feet(), seed) >= maxRadius) return;
        visited.add(landing.feet());
        transitions.add(new Transition(current.feet(), landing.feet()));
        queue.addLast(landing);
    }

    private static Optional<SurfaceCell> findLanding(
            Level world,
            double currentSurfaceY,
            BlockPos horizontal,
            FloorCeilingResolver ceilings) {
        for (int dy : LANDING_Y_OFFSETS) {
            BlockPos candidate = horizontal.offset(0, dy, 0);
            SurfaceCell cell = inspectSurfaceCell(world, candidate, ceilings).orElse(null);
            if (cell != null && canStep(currentSurfaceY, cell.surfaceY())) return Optional.of(cell);
        }
        return Optional.empty();
    }

    private static boolean reachesExterior(
            Level world,
            SurfaceCell start,
            int seedY,
            FloorCeilingResolver ceilings,
            int maxRadius,
            BlockPos blockedBoundary) {
        ArrayDeque<SurfaceProbe> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.addLast(new SurfaceProbe(start.feet(), start.surfaceY()));
        visited.add(start.feet());

        while (!queue.isEmpty()) {
            SurfaceProbe current = queue.removeFirst();
            if (horizontalDistance(current.feet(), start.feet()) >= maxRadius - 1) return true;

            for (Direction direction : HORIZONTAL) {
                BlockPos horizontal = current.feet().relative(direction);
                for (int dy : LANDING_Y_OFFSETS) {
                    BlockPos next = horizontal.offset(0, dy, 0);
                    if (blockedBoundary != null && sameConnectorColumn(next, blockedBoundary)) continue;
                    if (!StructureFloor.sameSemanticBand(seedY, next.getY()) || visited.contains(next)) continue;

                    BlockState state = world.getBlockState(next);
                    if (StructureConnector.isConnector(state)) continue;
                    OptionalDouble surfaceY = supportedSurfaceY(world, next);
                    if (surfaceY.isEmpty()
                            || !canStep(current.surfaceY(), surfaceY.getAsDouble())) continue;

                    visited.add(next);
                    if (ceilings.ceilingY(next).isEmpty()) return true;
                    queue.addLast(new SurfaceProbe(next, surfaceY.getAsDouble()));
                    break;
                }
            }
        }
        return false;
    }

    private static void collectVerticalConnectors(
            Level world, int seedY, BlockPos floorCell, Set<BlockPos> connectors) {
        for (Direction direction : HORIZONTAL) {
            collectVerticalConnectorColumn(world, seedY, floorCell.relative(direction), connectors);
        }
        collectVerticalConnectorColumn(world, seedY, floorCell, connectors);
    }

    private static void collectVerticalConnectorColumn(
            Level world, int seedY, BlockPos base, Set<BlockPos> connectors) {
        for (int dy = -1; dy <= 2; dy++) {
            BlockPos candidate = base.offset(0, dy, 0);
            if (!StructureFloor.sameSemanticBand(seedY, candidate.getY())) continue;
            BlockPos connector = StructureConnector.verticalInteractionConnector(world, candidate);
            if (connector == null || !StructureConnector.isVertical(world, connector)) continue;
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

    private static boolean sameConnectorColumn(BlockPos candidate, BlockPos connector) {
        return candidate.getX() == connector.getX() && candidate.getZ() == connector.getZ();
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

    private record SurfaceProbe(BlockPos feet, double surfaceY) {
    }

    private record HeightBand(int minY) {
        boolean contains(int y) {
            return y >= minY && y <= minY + StructureFloor.BAND_TOLERANCE;
        }

        int distanceTo(int y) {
            if (contains(y)) return 0;
            return y < minY ? minY - y : y - (minY + StructureFloor.BAND_TOLERANCE);
        }
    }

    private record SemanticBands(List<HeightBand> bands,
                                 Set<Integer> discoveredHeights,
                                 Map<BlockPos, HeightBand> ownerByCell) {
        Optional<HeightBand> owner(BlockPos feet) {
            return Optional.ofNullable(ownerByCell.get(feet));
        }
    }

    record FloorSelection(FloorGeometry selected, List<FloorGeometry> connected) {
        FloorSelection {
            connected = connected == null ? List.of() : List.copyOf(connected);
        }
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
