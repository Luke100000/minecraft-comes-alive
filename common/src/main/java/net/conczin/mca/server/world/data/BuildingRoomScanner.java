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
        BuildingFloorResolver.ResolvedFloor floor = BuildingFloorResolver.resolve(
                world, source, structureRoot).orElse(null);
        if (floor == null) {
            return Result.failure(Status.TOO_SMALL, source);
        }
        return scanResolved(world, source, blocked, maxSize, maxRadius, floor);
    }

    private static Result scanResolved(Level world,
                                       BlockPos source,
                                       Set<BlockPos> blocked,
                                       int maxSize,
                                       int maxRadius,
                                       BuildingFloorResolver.ResolvedFloor floor) {
        Optional<BlockPos> resolvedSeed = findInteriorSeedAtY(
                world, source, floor.physicalY(), floor.ceilingY());
        if (resolvedSeed.isEmpty()) {
            return Result.failure(Status.TOO_SMALL, source);
        }

        BlockPos seed = resolvedSeed.get();
        int ceilingY = floor.ceilingY();
        Optional<BlockPos> openSeed = findOpenCellInColumn(
                world, seed.getX(), seed.getZ(), seed.getY(), ceilingY);
        if (openSeed.isEmpty()) {
            return Result.failure(Status.TOO_SMALL, seed);
        }
        return scanFromOpenSeed(
                world, seed, openSeed.get(), floor.semanticY(), ceilingY,
                blocked, maxSize, maxRadius);
    }

    private static Result scanFromOpenSeed(Level world,
                                           BlockPos seed,
                                           BlockPos openSeed,
                                           int floorY,
                                           int ceilingY,
                                           Set<BlockPos> blocked,
                                           int maxSize,
                                           int maxRadius) {
        int scanFloorY = seed.getY();
        if (ceilingY <= scanFloorY) {
            return Result.failure(Status.TOO_SMALL, seed);
        }

        int bandHeight = Math.max(1, ceilingY - scanFloorY);
        long maxVolume = Math.max(1L, (long) maxSize * bandHeight);
        Set<BlockPos> blockedCells = blocked == null ? Set.of() : blocked;

        HashSet<Long> visited = new HashSet<>();
        HashSet<Long> openColumns = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        long start = openSeed.asLong();
        visited.add(start);
        queue.add(start);

        boolean hasEntrance = false;

        while (!queue.isEmpty()) {
            long current = queue.removeFirst();
            int x = BlockPos.getX(current);
            int y = BlockPos.getY(current);
            int z = BlockPos.getZ(current);
            openColumns.add(packColumn(x, z));

            cursor.set(x, y, z);
            BlockState currentState = world.getBlockState(cursor);

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
                if (blocksVerticalConnectorTraversal(direction, currentState, state)
                        || !isOpenVolumeCell(world, cursor, state)) {
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
            if (!BuildingFloorResolver.isSupported(world, x, scanFloorY, z, supportCursor)) {
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

        return BuildingFloorResolver.isSupported(world, pos.getX(), pos.getY(), pos.getZ())
                && findOpenCellInColumn(
                world, pos.getX(), pos.getZ(), pos.getY(), ceilingY).isPresent();
    }

    /**
     * Discovers the disconnected strict-room components still occupying the previous
     * room footprint. Connectivity belongs here so callers never probe columns and
     * repeatedly drive independent room scans themselves.
     *
     * <p>The already-scanned requested component is reused as the first result. Each
     * additional connected component is flooded once, and scanning stops as soon as
     * more than {@code maxComponents} valid components are found.</p>
     */
    static ComponentScan scanComponents(Level world,
                                        Building previousRoom,
                                        Building structureRoot,
                                        Result requested,
                                        int maxSize,
                                        int maxRadius,
                                        int maxComponents) {
        if (previousRoom == null || structureRoot == null || requested == null
                || requested.status() != Status.SUCCESS || maxComponents < 1) {
            return new ComponentScan(List.of(), false);
        }

        int canonicalFloorY = structureRoot.getCanonicalFloorY(previousRoom.getFloorY());
        List<Result> components = new ArrayList<>();
        Set<Long> coveredColumns = new HashSet<>();

        coverColumns(coveredColumns, requested);
        if (isComponentInsidePreviousRoom(requested, previousRoom, canonicalFloorY)) {
            components.add(requested);
        }

        int probeY = canonicalFloorY + BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE;
        BlockPos min = previousRoom.getRawPos0();
        BlockPos max = previousRoom.getRawPos1();

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                long columnKey = packColumn(x, z);
                if (!previousRoom.containsFloorColumn(x, z) || coveredColumns.contains(columnKey)) {
                    continue;
                }

                BuildingFloorResolver.ResolvedFloor floor = BuildingFloorResolver.resolve(
                        world, new BlockPos(x, probeY, z), structureRoot).orElse(null);
                if (floor == null || floor.semanticY() != canonicalFloorY) {
                    continue;
                }

                Optional<BlockPos> openSeed = findOpenCellInColumn(
                        world, x, z, floor.physicalY(), floor.ceilingY());
                if (openSeed.isEmpty()) {
                    continue;
                }

                Result component = scanFromOpenSeed(
                        world,
                        new BlockPos(x, floor.physicalY(), z),
                        openSeed.get(),
                        canonicalFloorY,
                        floor.ceilingY(),
                        Set.of(),
                        maxSize,
                        maxRadius
                );
                if (component.status() != Status.SUCCESS) {
                    continue;
                }

                // A successful flood identifies the complete connected component. Mark its
                // columns even when it extends outside the old room so another old-footprint
                // cell cannot trigger the same expensive flood again.
                coverColumns(coveredColumns, component);

                if (!isComponentInsidePreviousRoom(component, previousRoom, canonicalFloorY)
                        || containsSameComponent(components, component)) {
                    continue;
                }

                components.add(component);
                if (components.size() > maxComponents) {
                    return new ComponentScan(components, true);
                }
            }
        }

        return new ComponentScan(components, false);
    }

    private static void coverColumns(Set<Long> coveredColumns, Result component) {
        for (BlockPos cell : component.footprintCells()) {
            coveredColumns.add(packColumn(cell.getX(), cell.getZ()));
        }
    }

    private static boolean isComponentInsidePreviousRoom(Result component,
                                                         Building previousRoom,
                                                         int canonicalFloorY) {
        return component.floorY() == canonicalFloorY
                && component.footprintCells().stream().allMatch(cell ->
                previousRoom.containsFloorColumn(cell.getX(), cell.getZ()));
    }

    private static boolean containsSameComponent(List<Result> components, Result candidate) {
        return components.stream().anyMatch(existing ->
                existing.floorY() == candidate.floorY()
                        && existing.footprintCells().equals(candidate.footprintCells()));
    }

    private static boolean hasOpenCellInColumn(Level world,
                                       int x,
                                       int z,
                                       int floorY,
                                       int ceilingY) {
        return findOpenCellInColumn(world, x, z, floorY, ceilingY).isPresent();
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

    private static boolean isOpenVolumeCell(Level world, BlockPos pos, BlockState state) {
        if (!state.getFluidState().isEmpty() || isBoundaryConnector(state)) {
            return false;
        }
        if (isVerticalConnector(state)) {
            return true;
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
                || state.getBlock() instanceof FenceGateBlock;
    }

    private static boolean isVerticalConnector(BlockState state) {
        return state.getBlock() instanceof LadderBlock;
    }

    private static boolean blocksVerticalConnectorTraversal(Direction direction,
                                                             BlockState currentState,
                                                             BlockState nextState) {
        return direction.getAxis() == Direction.Axis.Y
                && (isVerticalConnector(currentState) || isVerticalConnector(nextState));
    }

    private static boolean isEntrance(BlockState state) {
        return isBoundaryConnector(state);
    }

    enum Status {
        SUCCESS,
        OVERLAP,
        BLOCK_LIMIT,
        SIZE_LIMIT,
        TOO_SMALL
    }

    record ComponentScan(List<Result> components, boolean tooMany) {
        ComponentScan {
            components = List.copyOf(components);
        }
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
