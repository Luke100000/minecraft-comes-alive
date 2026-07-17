package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

final class BuildingRoomScanner {
    private static final int MIN_INTERIOR_AREA = 4;
    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private BuildingRoomScanner() {
    }

    static Result scan(Level world,
                       BlockPos source,
                       Set<BlockPos> blocked,
                       int maxSize,
                       int maxRadius) {
        Optional<BlockPos> resolvedSeed = resolveInteriorSeed(world, source);
        if (resolvedSeed.isEmpty()) {
            return Result.failure(Status.TOO_SMALL, source);
        }

        BlockPos seed = resolvedSeed.get();
        Set<BlockPos> blockedCells = blocked == null ? Set.of() : blocked;
        LinkedHashSet<BlockPos> interior = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> raisedFloorCells = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> inspected = new LinkedHashSet<>();
        HashSet<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();

        interior.add(seed);
        inspected.add(seed);
        visited.add(seed);
        queue.add(seed);

        boolean hasEntrance = false;

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();

            if (!current.equals(seed) && blockedCells.contains(current)) {
                return Result.failure(Status.OVERLAP, seed, interior, Set.of(), inspected, hasEntrance);
            }

            for (Direction direction : HORIZONTAL_DIRECTIONS) {
                BlockPos candidate = current.relative(direction);
                if (!visited.add(candidate)) {
                    continue;
                }

                BlockState state = world.getBlockState(candidate);
                CellKind kind = classify(world, candidate, state);
                inspected.add(candidate);

                if (kind == CellKind.BARRIER) {
                    if (isEntrance(state)) {
                        hasEntrance = true;
                    }
                    continue;
                }

                boolean ordinaryFloorCell =
                        kind == CellKind.PASSABLE && isTraversableFloorCell(world, candidate);
                boolean raisedFloorCell =
                        kind == CellKind.OBSTACLE && isRaisedFloorCell(world, candidate, state);
                if (!ordinaryFloorCell && !raisedFloorCell) {
                    continue;
                }

                if (candidate.distManhattan(seed) >= maxRadius) {
                    return Result.failure(Status.SIZE_LIMIT, seed, interior, Set.of(), inspected, hasEntrance);
                }
                if (interior.size() + raisedFloorCells.size() >= maxSize) {
                    return Result.failure(Status.BLOCK_LIMIT, seed, interior, Set.of(), inspected, hasEntrance);
                }
                if (blockedCells.contains(candidate)) {
                    return Result.failure(Status.OVERLAP, seed, interior, Set.of(), inspected, hasEntrance);
                }

                if (raisedFloorCell) {
                    // Keep semantic floorY fixed. A chest/slab-like obstacle is represented
                    // by its X/Z cell on this floor instead of moving the room up to Y+1.
                    raisedFloorCells.add(candidate);
                } else {
                    interior.add(candidate);
                }
                queue.addLast(candidate);
            }
        }

        if (interior.size() + raisedFloorCells.size() < MIN_INTERIOR_AREA) {
            return Result.failure(Status.TOO_SMALL, seed, interior, raisedFloorCells, inspected, hasEntrance);
        }

        LinkedHashSet<BlockPos> footprint = new LinkedHashSet<>(interior);
        footprint.addAll(raisedFloorCells);
        footprint.addAll(projectEnclosedObstacles(world, interior, raisedFloorCells, seed.getY()));

        LinkedHashSet<BlockPos> poiCells = new LinkedHashSet<>(inspected);
        poiCells.addAll(footprint);
        for (BlockPos cell : interior) {
            poiCells.add(cell.below());
        }

        int minX = footprint.stream().mapToInt(BlockPos::getX).min().orElse(seed.getX());
        int minZ = footprint.stream().mapToInt(BlockPos::getZ).min().orElse(seed.getZ());
        int maxX = footprint.stream().mapToInt(BlockPos::getX).max().orElse(seed.getX());
        int maxZ = footprint.stream().mapToInt(BlockPos::getZ).max().orElse(seed.getZ());

        return new Result(
                Status.SUCCESS,
                seed,
                Set.copyOf(interior),
                Set.copyOf(footprint),
                Set.copyOf(poiCells),
                hasEntrance,
                new BlockPos(minX, seed.getY(), minZ),
                new BlockPos(maxX, seed.getY() + 1, maxZ)
        );
    }

    private static Optional<BlockPos> resolveInteriorSeed(Level world, BlockPos source) {
        LinkedHashSet<BlockPos> candidates = new LinkedHashSet<>();
        candidates.add(source);

        // Prefer same-floor interior before considering air above furniture or a partial block.
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            candidates.add(source.relative(direction));
        }

        BlockPos above = source.above();
        candidates.add(above);
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            candidates.add(above.relative(direction));
        }

        BlockPos below = source.below();
        candidates.add(below);
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            candidates.add(below.relative(direction));
        }

        return candidates.stream()
                .filter(candidate -> isTraversableFloorCell(world, candidate))
                .findFirst();
    }

    private static boolean isTraversableFloorCell(Level world, BlockPos pos) {
        if (classify(world, pos, world.getBlockState(pos)) != CellKind.PASSABLE) {
            return false;
        }
        if (classify(world, pos.above(), world.getBlockState(pos.above())) != CellKind.PASSABLE) {
            return false;
        }
        return isSupported(world, pos);
    }

    private static boolean isSupported(Level world, BlockPos interiorPos) {
        BlockPos supportPos = interiorPos.below();
        BlockState supportState = world.getBlockState(supportPos);
        var collisionShape = supportState.getCollisionShape(world, supportPos);
        if (collisionShape.isEmpty()) {
            return false;
        }

        double width = collisionShape.max(Direction.Axis.X) - collisionShape.min(Direction.Axis.X);
        double depth = collisionShape.max(Direction.Axis.Z) - collisionShape.min(Direction.Axis.Z);
        return width * depth >= 0.25D;
    }

    private static boolean isRaisedFloorCell(Level world, BlockPos pos, BlockState state) {
        if (!state.getFluidState().isEmpty()) {
            return false;
        }

        var collisionShape = state.getCollisionShape(world, pos);
        if (collisionShape.isEmpty()) {
            return false;
        }

        // Partial-height blocks such as vanilla chests (14/16 high) and slabs are usable
        // as raised floor cells. Full-height walls/blocks remain hard room obstacles.
        double height = collisionShape.max(Direction.Axis.Y) - collisionShape.min(Direction.Axis.Y);
        double width = collisionShape.max(Direction.Axis.X) - collisionShape.min(Direction.Axis.X);
        double depth = collisionShape.max(Direction.Axis.Z) - collisionShape.min(Direction.Axis.Z);
        if (height >= 1.0D || width * depth < 0.25D) {
            return false;
        }

        return classify(world, pos.above(), world.getBlockState(pos.above())) == CellKind.PASSABLE;
    }

    private static Set<BlockPos> projectEnclosedObstacles(Level world,
                                                          Set<BlockPos> interior,
                                                          Set<BlockPos> raisedFloorCells,
                                                          int floorY) {
        int minX = interior.stream().mapToInt(BlockPos::getX).min().orElse(0);
        int minZ = interior.stream().mapToInt(BlockPos::getZ).min().orElse(0);
        int maxX = interior.stream().mapToInt(BlockPos::getX).max().orElse(0);
        int maxZ = interior.stream().mapToInt(BlockPos::getZ).max().orElse(0);

        Set<BlockPos> candidates = new HashSet<>();
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                BlockPos candidate = new BlockPos(x, floorY, z);
                if (interior.contains(candidate) || raisedFloorCells.contains(candidate)) {
                    continue;
                }

                BlockState state = world.getBlockState(candidate);
                if (classify(world, candidate, state) == CellKind.OBSTACLE
                        && isSupported(world, candidate)) {
                    candidates.add(candidate);
                }
            }
        }

        LinkedHashSet<BlockPos> projected = new LinkedHashSet<>();
        HashSet<BlockPos> unvisited = new HashSet<>(candidates);
        while (!unvisited.isEmpty()) {
            BlockPos start = unvisited.iterator().next();
            unvisited.remove(start);

            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            LinkedHashSet<BlockPos> component = new LinkedHashSet<>();
            boolean touchesEnvelopeEdge = false;
            queue.add(start);

            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst();
                component.add(current);
                if (current.getX() == minX || current.getX() == maxX
                        || current.getZ() == minZ || current.getZ() == maxZ) {
                    touchesEnvelopeEdge = true;
                }

                for (Direction direction : HORIZONTAL_DIRECTIONS) {
                    BlockPos neighbour = current.relative(direction);
                    if (unvisited.remove(neighbour)) {
                        queue.addLast(neighbour);
                    }
                }
            }

            // Furniture and other isolated interior obstructions become part of the room mask.
            // Walls and exterior terrain remain connected to the scan envelope and are excluded.
            if (!touchesEnvelopeEdge) {
                projected.addAll(component);
            }
        }

        return Set.copyOf(projected);
    }

    private static CellKind classify(Level world, BlockPos pos, BlockState state) {
        if (isBoundaryConnector(state)) {
            return CellKind.BARRIER;
        }
        if (state.isAir() || state.canBeReplaced() || state.getCollisionShape(world, pos).isEmpty()) {
            return state.getFluidState().isEmpty() ? CellKind.PASSABLE : CellKind.OBSTACLE;
        }
        return CellKind.OBSTACLE;
    }

    private static boolean isBoundaryConnector(BlockState state) {
        return state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof LadderBlock;
    }

    private static boolean isEntrance(BlockState state) {
        return state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock;
    }

    enum Status {
        SUCCESS,
        OVERLAP,
        BLOCK_LIMIT,
        SIZE_LIMIT,
        TOO_SMALL
    }

    private enum CellKind {
        PASSABLE,
        BARRIER,
        OBSTACLE
    }

    record Result(Status status,
                  BlockPos seed,
                  Set<BlockPos> interiorCells,
                  Set<BlockPos> footprintCells,
                  Set<BlockPos> poiCells,
                  boolean hasEntrance,
                  BlockPos min,
                  BlockPos max) {
        Result {
            interiorCells = Set.copyOf(interiorCells);
            footprintCells = Set.copyOf(footprintCells);
            poiCells = Set.copyOf(poiCells);
        }

        private static Result failure(Status status, BlockPos seed) {
            return failure(status, seed, Set.of(), Set.of(), Set.of(), false);
        }

        private static Result failure(Status status,
                                      BlockPos seed,
                                      Collection<BlockPos> interior,
                                      Collection<BlockPos> footprint,
                                      Collection<BlockPos> inspected,
                                      boolean hasEntrance) {
            LinkedHashSet<BlockPos> geometry = new LinkedHashSet<>(footprint);
            geometry.addAll(interior);

            int minX = geometry.stream().mapToInt(BlockPos::getX).min().orElse(seed.getX());
            int minZ = geometry.stream().mapToInt(BlockPos::getZ).min().orElse(seed.getZ());
            int maxX = geometry.stream().mapToInt(BlockPos::getX).max().orElse(seed.getX());
            int maxZ = geometry.stream().mapToInt(BlockPos::getZ).max().orElse(seed.getZ());

            return new Result(
                    status,
                    seed,
                    Set.copyOf(interior),
                    Set.copyOf(geometry),
                    Set.copyOf(inspected),
                    hasEntrance,
                    new BlockPos(minX, seed.getY(), minZ),
                    new BlockPos(maxX, seed.getY() + 1, maxZ)
            );
        }
    }
}
