package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

/** Discovers one selected semantic floor while retaining exact Minecraft surface heights. */
final class SelectedFloorScanner {
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

        FloorSurface surface = new FloorSurface(Set.copyOf(cells.values()), Map.of());
        surface = surface.withConnectors(StructureConnector.associatedFloorCells(
                world, connectors, surface));
        return success(seed, surface);
    }

    static boolean canStep(double fromSurfaceY, double toSurfaceY) {
        return FloorSurface.canStep(fromSurfaceY, toSurfaceY);
    }

    static boolean withinSelectedFloorBand(int seedY, int candidateY) {
        return FloorSurface.withinBand(seedY, candidateY);
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
                    world, seed.getY(), current.surfaceY(), connector.relative(direction), ceilings)
                    .orElse(null);
            if (farSide == null || visited.contains(farSide.feet())) return;
            if (horizontalDistance(farSide.feet(), seed) >= maxRadius) return;
            if (reachesExterior(world, farSide, seed.getY(), ceilings, maxRadius, connector)) return;
            visited.add(farSide.feet());
            queue.addLast(farSide);
            return;
        }

        FloorSurface.Cell landing = findLanding(
                world, seed.getY(), current.surfaceY(), horizontal, ceilings).orElse(null);
        if (landing == null || visited.contains(landing.feet())) return;
        if (horizontalDistance(landing.feet(), seed) >= maxRadius) return;
        visited.add(landing.feet());
        queue.addLast(landing);
    }

    private static Optional<FloorSurface.Cell> findLanding(
            Level world,
            int seedY,
            double currentSurfaceY,
            BlockPos horizontal,
            FloorCeilingResolver ceilings) {
        for (int dy : LANDING_Y_OFFSETS) {
            BlockPos candidate = horizontal.offset(0, dy, 0);
            if (!withinSelectedFloorBand(seedY, candidate.getY())) continue;
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

    private static Result success(BlockPos seed, FloorSurface surface) {
        Set<BlockPos> footprint = new LinkedHashSet<>(surface.projectedCells());
        footprint.addAll(surface.connectorByFloorCell().keySet());
        int minX = footprint.stream().mapToInt(BlockPos::getX).min().orElse(seed.getX());
        int minZ = footprint.stream().mapToInt(BlockPos::getZ).min().orElse(seed.getZ());
        int maxX = footprint.stream().mapToInt(BlockPos::getX).max().orElse(seed.getX());
        int maxZ = footprint.stream().mapToInt(BlockPos::getZ).max().orElse(seed.getZ());
        int minY = surface.cells().stream().mapToInt(cell -> cell.feet().getY() - 1)
                .min().orElse(seed.getY() - 1);
        int maxY = surface.cells().stream().mapToInt(cell -> cell.ceilingY() - 1)
                .max().orElse(seed.getY());
        return new Result(Building.validationResult.SUCCESS, surface,
                new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ));
    }

    private record SurfaceProbe(BlockPos feet, double surfaceY) {
    }

    record Result(Building.validationResult result, FloorSurface surface, BlockPos min, BlockPos max) {
        static Result failure(Building.validationResult result, BlockPos source) {
            return new Result(result, null, source, source);
        }
    }
}
