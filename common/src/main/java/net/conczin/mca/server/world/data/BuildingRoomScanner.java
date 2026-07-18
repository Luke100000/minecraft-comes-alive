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
    private static final int MAX_SEED_VERTICAL_SEARCH = 4;
    private static final int MAX_LOCAL_CEILING_SEARCH = 16;
    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final Direction[] VOLUME_DIRECTIONS = {
            Direction.UP, Direction.DOWN,
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private BuildingRoomScanner() {
    }

    static Result scan(Level world,
                       BlockPos source,
                       Set<BlockPos> blocked,
                       int maxSize,
                       int maxRadius,
                       Building structureRoot) {
        Optional<ResolvedSeed> resolvedSeed = resolveInteriorSeed(world, source, structureRoot);
        if (resolvedSeed.isEmpty()) {
            return Result.failure(Status.TOO_SMALL, source);
        }

        ResolvedSeed resolved = resolvedSeed.get();
        BlockPos seed = resolved.seed();
        int scanFloorY = seed.getY();
        int floorY = resolved.floorY();
        int ceilingY = resolved.ceilingY();
        if (ceilingY <= scanFloorY) {
            return Result.failure(Status.TOO_SMALL, seed);
        }

        Optional<BlockPos> openSeed = findOpenCellInColumn(
                world, seed.getX(), seed.getZ(), scanFloorY, ceilingY);
        if (openSeed.isEmpty()) {
            return Result.failure(Status.TOO_SMALL, seed);
        }

        int bandHeight = Math.max(1, ceilingY - scanFloorY);
        long maxVolume = Math.max(1L, (long) maxSize * bandHeight);
        Set<BlockPos> blockedCells = blocked == null ? Set.of() : blocked;

        HashSet<Long> visited = new HashSet<>();
        HashSet<Long> openColumns = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        long start = openSeed.get().asLong();
        visited.add(start);
        queue.add(start);

        boolean hasEntrance = false;

        while (!queue.isEmpty()) {
            long current = queue.removeFirst();
            int x = BlockPos.getX(current);
            int y = BlockPos.getY(current);
            int z = BlockPos.getZ(current);
            openColumns.add(packColumn(x, z));

            for (Direction direction : VOLUME_DIRECTIONS) {
                int nextX = x + direction.getStepX();
                int nextY = y + direction.getStepY();
                int nextZ = z + direction.getStepZ();
                if (nextY < scanFloorY || nextY >= ceilingY) {
                    continue;
                }

                cursor.set(nextX, nextY, nextZ);
                BlockState state = world.getBlockState(cursor);
                if (direction.getAxis() != Direction.Axis.Y && isEntrance(state)) {
                    hasEntrance = true;
                }
                if (!isOpenVolumeCell(world, cursor, state)) {
                    continue;
                }

                int horizontalDistance = Math.abs(nextX - seed.getX()) + Math.abs(nextZ - seed.getZ());
                if (horizontalDistance >= maxRadius) {
                    return Result.failure(Status.SIZE_LIMIT, seed);
                }

                long packed = BlockPos.asLong(nextX, nextY, nextZ);
                if (!visited.add(packed)) {
                    continue;
                }
                if (visited.size() > maxVolume) {
                    return Result.failure(Status.BLOCK_LIMIT, seed);
                }
                queue.addLast(packed);
            }
        }

        LinkedHashSet<BlockPos> footprint = new LinkedHashSet<>();
        BlockPos.MutableBlockPos supportCursor = new BlockPos.MutableBlockPos();
        for (long column : openColumns) {
            int x = unpackColumnX(column);
            int z = unpackColumnZ(column);
            if (!isSupported(world, x, scanFloorY, z, supportCursor)) {
                continue;
            }

            BlockPos floorCell = new BlockPos(x, floorY, z);
            if (blockedCells.contains(floorCell)) {
                return Result.failure(Status.OVERLAP, seed);
            }
            if (footprint.size() >= maxSize) {
                return Result.failure(Status.BLOCK_LIMIT, seed);
            }
            footprint.add(floorCell);
        }

        if (footprint.size() < MIN_INTERIOR_AREA) {
            return Result.failure(Status.TOO_SMALL, seed);
        }

        Set<BlockPos> poiCells = collectPoiCells(world, footprint, scanFloorY, ceilingY);

        int minX = footprint.stream().mapToInt(BlockPos::getX).min().orElse(seed.getX());
        int minZ = footprint.stream().mapToInt(BlockPos::getZ).min().orElse(seed.getZ());
        int maxX = footprint.stream().mapToInt(BlockPos::getX).max().orElse(seed.getX());
        int maxZ = footprint.stream().mapToInt(BlockPos::getZ).max().orElse(seed.getZ());

        return new Result(
                Status.SUCCESS,
                seed,
                floorY,
                Set.copyOf(footprint),
                poiCells,
                hasEntrance,
                new BlockPos(minX, scanFloorY, minZ),
                new BlockPos(maxX, Math.max(scanFloorY, ceilingY - 1), maxZ)
        );
    }

    private static Optional<ResolvedSeed> resolveInteriorSeed(Level world,
                                                              BlockPos source,
                                                              Building structureRoot) {
        if (structureRoot != null && !structureRoot.getFloorRegions().isEmpty()) {
            return resolveInteriorSeedInStructure(world, source, structureRoot);
        }
        return resolveInteriorSeedLocally(world, source);
    }

    private static Optional<ResolvedSeed> resolveInteriorSeedInStructure(Level world,
                                                                         BlockPos source,
                                                                         Building structureRoot) {
        int minY = structureRoot.getRawPos0().getY();
        for (int y = source.getY(); y >= minY; y--) {
            Building.FloorBand band = structureRoot.resolvePhysicalFloorBand(y).orElse(null);
            if (band == null) {
                continue;
            }

            Optional<BlockPos> candidate = findInteriorSeedAtY(world, source, y, band.ceilingY());
            if (candidate.isPresent()) {
                return Optional.of(new ResolvedSeed(candidate.get(), band.anchorY(), band.ceilingY()));
            }
        }
        return Optional.empty();
    }

    private static Optional<ResolvedSeed> resolveInteriorSeedLocally(Level world, BlockPos source) {
        for (int drop = 0; drop <= MAX_SEED_VERTICAL_SEARCH; drop++) {
            int y = source.getY() - drop;
            int ceilingY = y + MAX_LOCAL_CEILING_SEARCH;
            Optional<BlockPos> candidate = findInteriorSeedAtY(world, source, y, ceilingY);
            if (candidate.isPresent()) {
                BlockPos seed = candidate.get();
                return Optional.of(new ResolvedSeed(seed, seed.getY(), ceilingY));
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> findInteriorSeedAtY(Level world,
                                                           BlockPos source,
                                                           int y,
                                                           int ceilingY) {
        BlockPos center = new BlockPos(source.getX(), y, source.getZ());
        if (isFloorCandidate(world, center, ceilingY)) {
            return Optional.of(center);
        }

        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            BlockPos candidate = center.relative(direction);
            if (isFloorCandidate(world, candidate, ceilingY)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static boolean isFloorCandidate(Level world,
                                             BlockPos pos,
                                             int ceilingY) {
        if (ceilingY <= pos.getY()) {
            return false;
        }

        BlockPos.MutableBlockPos supportCursor = new BlockPos.MutableBlockPos();
        return isSupported(world, pos.getX(), pos.getY(), pos.getZ(), supportCursor)
                && findOpenCellInColumn(
                world, pos.getX(), pos.getZ(), pos.getY(), ceilingY).isPresent();
    }


    private static Optional<BlockPos> findOpenCellInColumn(Level world,
                                                            int x,
                                                            int z,
                                                            int floorY,
                                                            int ceilingY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, floorY, z);
        for (int y = floorY; y < ceilingY; y++) {
            cursor.setY(y);
            BlockState state = world.getBlockState(cursor);
            if (isOpenVolumeCell(world, cursor, state)) {
                return Optional.of(cursor.immutable());
            }
        }
        return Optional.empty();
    }

    private static boolean isSupported(Level world,
                                       int x,
                                       int floorY,
                                       int z,
                                       BlockPos.MutableBlockPos cursor) {
        cursor.set(x, floorY - 1, z);
        BlockState supportState = world.getBlockState(cursor);
        var collisionShape = supportState.getCollisionShape(world, cursor);
        if (collisionShape.isEmpty()) {
            return false;
        }

        double width = collisionShape.max(Direction.Axis.X) - collisionShape.min(Direction.Axis.X);
        double depth = collisionShape.max(Direction.Axis.Z) - collisionShape.min(Direction.Axis.Z);
        return width * depth >= 0.25D;
    }

    private static boolean isOpenVolumeCell(Level world, BlockPos pos, BlockState state) {
        if (isBoundaryConnector(state) || !state.getFluidState().isEmpty()) {
            return false;
        }
        return state.isAir() || state.canBeReplaced() || state.getCollisionShape(world, pos).isEmpty();
    }

    private static Set<BlockPos> collectPoiCells(Level world,
                                                  Set<BlockPos> footprint,
                                                  int floorY,
                                                  int ceilingY) {
        LinkedHashSet<BlockPos> poiCells = new LinkedHashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (BlockPos floorCell : footprint) {
            int x = floorCell.getX();
            int z = floorCell.getZ();

            cursor.set(x, floorY - 1, z);
            if (!world.getBlockState(cursor).isAir()) {
                poiCells.add(cursor.immutable());
            }

            cursor.set(x, floorY, z);
            for (int y = floorY; y < ceilingY; y++) {
                cursor.setY(y);
                if (!world.getBlockState(cursor).isAir()) {
                    poiCells.add(cursor.immutable());
                }
            }
        }
        return Set.copyOf(poiCells);
    }

    private static long packColumn(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int unpackColumnX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackColumnZ(long packed) {
        return (int) packed;
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

    private record ResolvedSeed(BlockPos seed, int floorY, int ceilingY) {
    }

    record Result(Status status,
                  BlockPos seed,
                  int floorY,
                  Set<BlockPos> footprintCells,
                  Set<BlockPos> poiCells,
                  boolean hasEntrance,
                  BlockPos min,
                  BlockPos max) {
        Result {
            footprintCells = Set.copyOf(footprintCells);
            poiCells = Set.copyOf(poiCells);
        }

        private static Result failure(Status status, BlockPos seed) {
            return new Result(
                    status,
                    seed,
                    seed.getY(),
                    Set.of(),
                    Set.of(),
                    false,
                    seed,
                    seed
            );
        }
    }
}
