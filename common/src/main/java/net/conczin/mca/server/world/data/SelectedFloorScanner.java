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
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final int[] LANDING_Y_OFFSETS = {0, 1, -1};

    private SelectedFloorScanner() {
    }

    static Result scan(Level world, BlockPos seed, int maxSize, int maxRadius) {
        FloorCeilingResolver ceilings = new FloorCeilingResolver(world);
        FloorSurface.Cell seedCell = inspectSurfaceCell(world, seed, ceilings).orElse(null);
        if (seedCell == null) return Result.failure(Building.validationResult.NOT_IN_BUILDING, seed);
        if (reachesExterior(world, seedCell, seed.getY(), ceilings, maxRadius, null)) {
            return Result.failure(Building.validationResult.NOT_IN_BUILDING, seed);
        }

        ArrayDeque<FloorSurface.Cell> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        LinkedHashMap<BlockPos, FloorSurface.Cell> cells = new LinkedHashMap<>();
        LinkedHashSet<BlockPos> connectors = new LinkedHashSet<>();
        queue.addLast(seedCell);
        visited.add(seedCell.feet());

        while (!queue.isEmpty()) {
            FloorSurface.Cell current = queue.removeFirst();
            if (horizontalDistance(current.feet(), seed) >= maxRadius) {
                return Result.failure(Building.validationResult.SIZE_LIMIT, seed);
            }
            cells.put(current.feet(), current);
            collectVerticalConnectors(world, seed.getY(), current.feet(), connectors);

            for (Direction direction : HORIZONTAL) {
                enqueueHorizontalLanding(world, seed, current, direction, maxRadius,
                        visited, queue, connectors, ceilings);
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
        ScannedFloor floor = selection.selected();
        FloorSurface surface = floor.surface();
        surface = surface.withConnectorTypes(StructureConnector.associatedFloorCells(
                world, connectors, surface));
        floor = new ScannedFloor(surface, floor.semanticCeilingY());
        List<ScannedFloor> connectedFloors = new ArrayList<>(selection.connected().size());
        for (ScannedFloor connected : selection.connected()) {
            connectedFloors.add(connected == selection.selected() ? floor : connected);
        }
        return success(seed, floor, connectedFloors);
    }

    static boolean canStep(double fromSurfaceY, double toSurfaceY) {
        return FloorSurface.canStep(fromSurfaceY, toSurfaceY);
    }

    static boolean withinSelectedFloorBand(int seedY, int candidateY) {
        return StructureFloor.sameSemanticBand(seedY, candidateY);
    }

    private static OptionalInt nextBandY(List<HeightBand> bands, HeightBand selected) {
        return bands.stream()
                .mapToInt(HeightBand::minY)
                .filter(y -> y > selected.minY())
                .min();
    }

    static FloorSelection floorSelection(Collection<FloorSurface.Cell> discovered, BlockPos seed) {
        if (discovered == null || discovered.isEmpty() || seed == null) {
            return new FloorSelection(null, List.of());
        }
        SemanticBands semantic = semanticBands(discovered);
        HeightBand selectedBand = semantic.owner(seed).orElseGet(() ->
                selectHeightBand(semantic.bands(), semantic.discoveredHeights(), seed.getY()));
        if (selectedBand == null) return new FloorSelection(null, List.of());

        LinkedHashMap<HeightBand, ScannedFloor> floors = new LinkedHashMap<>();
        for (HeightBand band : semantic.bands()) {
            Set<FloorSurface.Cell> cells = discovered.stream()
                    .filter(cell -> band.equals(semantic.ownerByCell().get(cell.feet())))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (cells.isEmpty()) continue;
            floors.put(band, createSelectedFloor(cells, nextBandY(semantic.bands(), band)));
        }
        return new FloorSelection(floors.get(selectedBand), List.copyOf(floors.values()));
    }

    private static ScannedFloor createSelectedFloor(Set<FloorSurface.Cell> selectedCells, OptionalInt nextBandY) {
        FloorSurface surface = new FloorSurface(selectedCells, Map.of());
        return nextBandY.isPresent()
                ? new ScannedFloor(surface, nextBandY.getAsInt())
                : ScannedFloor.physical(surface);
    }

    /**
     * The walkability flood may see both sides of a staircase. A semantic Floor still follows the
     * original storey rule: meaningful surface slices no more than two blocks above one another
     * belong to one band; the next meaningful slice starts the next Floor. Sparse stair steps are
     * retained inside the selected band but do not move its boundary by themselves.
     */
    static Set<FloorSurface.Cell> selectFloorBand(Collection<FloorSurface.Cell> discovered, int seedY) {
        if (discovered == null || discovered.isEmpty()) return Set.of();
        SemanticBands semantic = semanticBands(discovered);
        HeightBand selected = selectHeightBand(semantic.bands(), semantic.discoveredHeights(), seedY);
        if (selected == null) return Set.of();

        return cellsOwnedBy(discovered, semantic, selected);
    }

    static Set<FloorSurface.Cell> selectFloorBand(Collection<FloorSurface.Cell> discovered, BlockPos seed) {
        if (discovered == null || discovered.isEmpty() || seed == null) return Set.of();
        SemanticBands semantic = semanticBands(discovered);
        HeightBand selected = semantic.owner(seed).orElseGet(() ->
                selectHeightBand(semantic.bands(), semantic.discoveredHeights(), seed.getY()));
        if (selected == null) return Set.of();

        return cellsOwnedBy(discovered, semantic, selected);
    }

    private static Set<FloorSurface.Cell> cellsOwnedBy(Collection<FloorSurface.Cell> discovered,
                                                       SemanticBands semantic,
                                                       HeightBand selected) {
        return discovered.stream()
                .filter(cell -> selected.equals(semantic.ownerByCell().get(cell.feet())))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static SemanticBands semanticBands(Collection<FloorSurface.Cell> discovered) {
        TreeMap<Integer, List<FloorSurface.Cell>> cellsByY = cellsByHeight(discovered);
        List<HeightBand> bands = heightBands(cellsByY);
        if (bands.isEmpty()) {
            int fallbackY = cellsByY.firstKey();
            bands = List.of(new HeightBand(fallbackY));
        }

        Map<BlockPos, HeightBand> owners = new LinkedHashMap<>();
        Map<Long, List<FloorSurface.Cell>> byColumn = cellsByColumn(discovered);

        for (Map.Entry<Integer, List<FloorSurface.Cell>> entry : cellsByY.entrySet()) {
            int y = entry.getKey();
            HeightBand nominal = selectHeightBand(bands, cellsByY.keySet(), y);
            for (Set<FloorSurface.Cell> component : sliceComponents(entry.getValue())) {
                if (component.size() >= MIN_MEANINGFUL_HEIGHT_SLICE_AREA) {
                    component.forEach(cell -> owners.put(cell.feet(), nominal));
                }
            }
        }

        for (Map.Entry<Integer, List<FloorSurface.Cell>> entry : cellsByY.entrySet()) {
            int y = entry.getKey();
            HeightBand nominal = selectHeightBand(bands, cellsByY.keySet(), y);
            for (Set<FloorSurface.Cell> component : sliceComponents(entry.getValue())) {
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

    private static Map<Long, List<FloorSurface.Cell>> cellsByColumn(Collection<FloorSurface.Cell> cells) {
        Map<Long, List<FloorSurface.Cell>> indexed = new LinkedHashMap<>();
        for (FloorSurface.Cell cell : cells) {
            indexed.computeIfAbsent(FloorSurface.columnKey(cell.feet().getX(), cell.feet().getZ()),
                    ignored -> new ArrayList<>()).add(cell);
        }
        indexed.replaceAll((ignored, column) -> List.copyOf(column));
        return Map.copyOf(indexed);
    }

    private static List<Set<FloorSurface.Cell>> sliceComponents(Collection<FloorSurface.Cell> cells) {
        Map<Long, FloorSurface.Cell> byColumn = cells.stream().collect(java.util.stream.Collectors.toMap(
                cell -> FloorSurface.columnKey(cell.feet().getX(), cell.feet().getZ()),
                cell -> cell,
                (first, ignored) -> first));
        Set<BlockPos> visited = new HashSet<>();
        List<Set<FloorSurface.Cell>> components = new ArrayList<>();
        for (FloorSurface.Cell seed : cells) {
            if (!visited.add(seed.feet())) continue;
            LinkedHashSet<FloorSurface.Cell> component = new LinkedHashSet<>();
            ArrayDeque<FloorSurface.Cell> queue = new ArrayDeque<>();
            queue.addLast(seed);
            while (!queue.isEmpty()) {
                FloorSurface.Cell current = queue.removeFirst();
                component.add(current);
                for (Direction direction : HORIZONTAL) {
                    FloorSurface.Cell next = byColumn.get(FloorSurface.columnKey(
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

    private static List<FloorSurface.Cell> adjacentWalkableCells(
            FloorSurface.Cell cell,
            Map<Long, List<FloorSurface.Cell>> byColumn) {
        List<FloorSurface.Cell> adjacent = new ArrayList<>();
        for (Direction direction : HORIZONTAL) {
            long key = FloorSurface.columnKey(
                    cell.feet().getX() + direction.getStepX(),
                    cell.feet().getZ() + direction.getStepZ());
            for (FloorSurface.Cell candidate : byColumn.getOrDefault(key, List.of())) {
                if (canStep(cell.surfaceY(), candidate.surfaceY())) adjacent.add(candidate);
            }
        }
        return List.copyOf(adjacent);
    }

    private static TreeMap<Integer, List<FloorSurface.Cell>> cellsByHeight(
            Collection<FloorSurface.Cell> discovered) {
        TreeMap<Integer, List<FloorSurface.Cell>> cellsByY = new TreeMap<>();
        for (FloorSurface.Cell cell : new LinkedHashSet<>(discovered)) {
            cellsByY.computeIfAbsent(cell.feet().getY(), ignored -> new ArrayList<>()).add(cell);
        }
        return cellsByY;
    }

    private static List<HeightBand> heightBands(Map<Integer, List<FloorSurface.Cell>> cellsByY) {
        List<HeightBand> bands = new ArrayList<>();
        for (Map.Entry<Integer, List<FloorSurface.Cell>> entry : cellsByY.entrySet()) {
            if (!meaningfulHeightSlice(entry.getKey(), entry.getValue())) continue;
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

    private static boolean meaningfulHeightSlice(int y, Collection<FloorSurface.Cell> cells) {
        return sliceComponents(cells).stream()
                .anyMatch(component -> component.size() >= MIN_MEANINGFUL_HEIGHT_SLICE_AREA);
    }

    static Optional<FloorSurface.Cell> inspectSurfaceCell(
            Level world, BlockPos feet, FloorCeilingResolver ceilings) {
        OptionalDouble surfaceY = supportedSurfaceY(world, feet);
        if (surfaceY.isEmpty()) return Optional.empty();
        OptionalInt ceiling = ceilings.ceilingY(feet);
        if (ceiling.isEmpty()) return Optional.empty();
        return Optional.of(new FloorSurface.Cell(feet, surfaceY.getAsDouble(), ceiling.getAsInt()));
    }

    private static void enqueueHorizontalLanding(
            Level world,
            BlockPos seed,
            FloorSurface.Cell current,
            Direction direction,
            int maxRadius,
            Set<BlockPos> visited,
            ArrayDeque<FloorSurface.Cell> queue,
            Set<BlockPos> connectors,
            FloorCeilingResolver ceilings) {
        BlockPos horizontal = current.feet().relative(direction);

        for (int dy : LANDING_Y_OFFSETS) {
            BlockPos candidate = horizontal.offset(0, dy, 0);
            BlockState state = world.getBlockState(candidate);
            if (!StructureConnector.isConnector(state)) continue;

            BlockPos connector = StructureConnector.normalize(candidate, state);
            connectors.add(connector);
            if (!StructureConnector.isHorizontalBoundary(state)) return;

            FloorSurface.Cell farSide = findLanding(
                    world, current.surfaceY(), connector.relative(direction), ceilings)
                    .orElse(null);
            if (farSide == null || visited.contains(farSide.feet())) return;
            if (horizontalDistance(farSide.feet(), seed) >= maxRadius) return;
            if (reachesExterior(world, farSide, farSide.feet().getY(), ceilings, maxRadius, connector)) return;
            visited.add(farSide.feet());
            queue.addLast(farSide);
            return;
        }

        FloorSurface.Cell landing = findLanding(
                world, current.surfaceY(), horizontal, ceilings).orElse(null);
        if (landing == null || visited.contains(landing.feet())) return;
        if (horizontalDistance(landing.feet(), seed) >= maxRadius) return;
        visited.add(landing.feet());
        queue.addLast(landing);
    }

    private static Optional<FloorSurface.Cell> findLanding(
            Level world,
            double currentSurfaceY,
            BlockPos horizontal,
            FloorCeilingResolver ceilings) {
        for (int dy : LANDING_Y_OFFSETS) {
            BlockPos candidate = horizontal.offset(0, dy, 0);
            FloorSurface.Cell cell = inspectSurfaceCell(world, candidate, ceilings).orElse(null);
            if (cell != null && canStep(currentSurfaceY, cell.surfaceY())) return Optional.of(cell);
        }
        return Optional.empty();
    }

    private static boolean reachesExterior(
            Level world,
            FloorSurface.Cell start,
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
                    if (!withinSelectedFloorBand(seedY, next.getY()) || visited.contains(next)) continue;

                    BlockState state = world.getBlockState(next);
                    if (StructureConnector.isConnector(state)) continue;
                    OptionalDouble surfaceY = supportedSurfaceY(world, next);
                    if (surfaceY.isEmpty() || !canStep(current.surfaceY(), surfaceY.getAsDouble())) continue;

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
            if (!withinSelectedFloorBand(seedY, candidate.getY())) continue;
            BlockPos connector = StructureConnector.verticalInteractionConnector(world, candidate);
            if (connector == null || !StructureConnector.isVertical(world, connector)) continue;
            connectors.add(StructureConnector.normalize(connector, world.getBlockState(connector)));
        }
    }

    private static OptionalDouble supportedSurfaceY(Level world, BlockPos feet) {
        if (!isOpen(world, feet) || !isOpen(world, feet.above())) return OptionalDouble.empty();
        BlockPos support = feet.below();
        var shape = world.getBlockState(support).getCollisionShape(world, support);
        if (shape.isEmpty()) return OptionalDouble.empty();
        double width = shape.max(Direction.Axis.X) - shape.min(Direction.Axis.X);
        double depth = shape.max(Direction.Axis.Z) - shape.min(Direction.Axis.Z);
        if (width * depth < 0.25D) return OptionalDouble.empty();
        return OptionalDouble.of(WalkNodeEvaluator.getFloorLevel(world, feet));
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

    private static Result success(BlockPos seed,
                                  ScannedFloor floor,
                                  List<ScannedFloor> connectedFloors) {
        FloorSurface surface = floor.surface();
        Set<BlockPos> footprint = surface.projectedCells();
        int minX = footprint.stream().mapToInt(BlockPos::getX).min().orElse(seed.getX());
        int minZ = footprint.stream().mapToInt(BlockPos::getZ).min().orElse(seed.getZ());
        int maxX = footprint.stream().mapToInt(BlockPos::getX).max().orElse(seed.getX());
        int maxZ = footprint.stream().mapToInt(BlockPos::getZ).max().orElse(seed.getZ());
        int minY = surface.cells().stream().mapToInt(cell -> cell.feet().getY() - 1)
                .min().orElse(seed.getY() - 1);
        int maxY = surface.cells().stream().mapToInt(cell -> cell.ceilingY() - 1)
                .max().orElse(seed.getY());
        return new Result(Building.validationResult.SUCCESS, floor,
                new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), connectedFloors);
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

    record FloorSelection(ScannedFloor selected, List<ScannedFloor> connected) {
        FloorSelection {
            connected = connected == null ? List.of() : List.copyOf(connected);
        }
    }

    record Result(Building.validationResult result,
                  ScannedFloor floor,
                  BlockPos min,
                  BlockPos max,
                  List<ScannedFloor> connectedFloors) {
        Result {
            connectedFloors = connectedFloors == null ? List.of() : List.copyOf(connectedFloors);
        }

        static Result failure(Building.validationResult result, BlockPos source) {
            return new Result(result, null, source, source, List.of());
        }
    }
}
