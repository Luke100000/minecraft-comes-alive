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

/** Bounded single-Floor Room flood. It never creates split Rooms or changes Floor identity. */
final class BuildingRoomScanner {
    private static final int MIN_INTERIOR_AREA = 4;
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private BuildingRoomScanner() {
    }

    static Result scan(Level world,
                       BlockPos source,
                       Set<BlockPos> blocked,
                       int maxSize,
                       int maxRadius,
                       Structure structure) {
        StructureFloor floor = structure == null ? null : structure.resolveFloor(world, source).orElse(null);
        if (floor == null) {
            return Result.failure(Status.TOO_SMALL, source);
        }
        return scan(world, source, blocked, maxSize, maxRadius, structure, floor);
    }

    static Result scan(Level world,
                       BlockPos source,
                       Set<BlockPos> blocked,
                       int maxSize,
                       int maxRadius,
                       Structure structure,
                       StructureFloor floor) {
        if (structure == null || floor == null) {
            return Result.failure(Status.TOO_SMALL, source);
        }
        Optional<BlockPos> seed = findSeed(world, source, structure, floor);
        if (seed.isEmpty()) {
            return Result.failure(Status.TOO_SMALL, source);
        }

        Set<Long> visited = new HashSet<>();
        Set<Long> columns = new HashSet<>();
        Set<BlockPos> boundaryConnectors = new HashSet<>();
        Set<BlockPos> verticalConnectors = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        long packedSeed = seed.get().asLong();
        visited.add(packedSeed);
        queue.add(packedSeed);
        long maxVolume = Math.max(1L, (long) maxSize * Math.max(1, floor.ceilingY() - floor.anchorY()));

        while (!queue.isEmpty()) {
            long packed = queue.removeFirst();
            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);
            columns.add(packColumn(x, z));

            cursor.set(x, y, z);
            BlockState currentState = world.getBlockState(cursor);
            collectVerticalConnectorCandidates(world, cursor, verticalConnectors);
            for (Direction direction : Direction.values()) {
                int nx = x + direction.getStepX();
                int ny = y + direction.getStepY();
                int nz = z + direction.getStepZ();

                cursor.set(nx, ny, nz);
                BlockState state = world.getBlockState(cursor);
                if (direction.getAxis() != Direction.Axis.Y && isBoundary(state)) {
                    boundaryConnectors.add(cursor.immutable());
                }
                if (ny < floor.anchorY() || ny >= floor.ceilingY()) continue;
                if (Math.abs(nx - source.getX()) + Math.abs(nz - source.getZ()) >= maxRadius) {
                    return Result.failure(Status.SIZE_LIMIT, source);
                }

                // Ladders connect Structure Floors but remain Room boundaries vertically.
                if (direction.getAxis() == Direction.Axis.Y
                        && (currentState.getBlock() instanceof LadderBlock
                        || state.getBlock() instanceof LadderBlock)) {
                    continue;
                }
                if (!structure.containsPos(cursor) || !isOpen(world, cursor, state)) continue;
                long next = BlockPos.asLong(nx, ny, nz);
                if (!visited.add(next)) continue;
                if (visited.size() > maxVolume) return Result.failure(Status.BLOCK_LIMIT, source);
                queue.addLast(next);
            }
        }

        boolean hasEntrance = hasValidEntrance(
                world, structure, floor, visited, boundaryConnectors, verticalConnectors);

        Set<BlockPos> blockedCells = blocked == null ? Set.of() : blocked;
        LinkedHashSet<BlockPos> footprint = new LinkedHashSet<>();
        for (long column : columns) {
            int x = unpackX(column);
            int z = unpackZ(column);
            // The Floor supplies stable semantic Y only. The connected Room volume determines
            // X/Z geometry so remodels and furniture-covered columns are not clipped by stale
            // persisted Floor footprints.
            if (!StructureScanner.isSupported(world, x, floor.anchorY(), z)) continue;
            BlockPos cell = new BlockPos(x, floor.anchorY(), z);
            if (blockedCells.contains(cell)) return Result.failure(Status.OVERLAP, source);
            if (footprint.size() >= maxSize) return Result.failure(Status.BLOCK_LIMIT, source);
            footprint.add(cell);
        }

        if (footprint.size() < MIN_INTERIOR_AREA) {
            return Result.failure(Status.TOO_SMALL, source);
        }
        Set<BlockPos> poi = collectPoiCells(world, footprint, floor.anchorY(), floor.ceilingY());
        int minX = footprint.stream().mapToInt(BlockPos::getX).min().orElse(source.getX());
        int minZ = footprint.stream().mapToInt(BlockPos::getZ).min().orElse(source.getZ());
        int maxX = footprint.stream().mapToInt(BlockPos::getX).max().orElse(source.getX());
        int maxZ = footprint.stream().mapToInt(BlockPos::getZ).max().orElse(source.getZ());
        return new Result(Status.SUCCESS, seed.get(), floor.id(), floor.anchorY(), Set.copyOf(footprint), poi,
                hasEntrance, new BlockPos(minX, floor.anchorY(), minZ),
                new BlockPos(maxX, Math.max(floor.anchorY(), floor.ceilingY() - 1), maxZ));
    }

    private static Optional<BlockPos> findSeed(Level world, BlockPos source, Structure structure, StructureFloor floor) {
        List<BlockPos> candidates = new ArrayList<>();
        candidates.add(new BlockPos(source.getX(), floor.anchorY(), source.getZ()));
        for (Direction direction : HORIZONTAL) {
            candidates.add(candidates.getFirst().relative(direction));
        }
        for (BlockPos candidate : candidates) {
            Optional<BlockPos> open = findOpenCell(world, structure, candidate.getX(), candidate.getZ(),
                    floor.anchorY(), floor.ceilingY());
            if (open.isPresent()) return open;
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> findOpenCell(Level world, Structure structure, int x, int z, int floorY, int ceilingY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = floorY; y < ceilingY; y++) {
            cursor.set(x, y, z);
            BlockState state = world.getBlockState(cursor);
            if (structure.containsPos(cursor) && isOpen(world, cursor, state)) {
                return Optional.of(cursor.immutable());
            }
        }
        return Optional.empty();
    }

    private static boolean isOpen(Level world, BlockPos pos, BlockState state) {
        if (isBoundary(state)) return false;
        if (state.getBlock() instanceof LadderBlock) return true;
        return !state.getFluidState().isEmpty()
                || state.isAir()
                || state.canBeReplaced()
                || state.getCollisionShape(world, pos).isEmpty();
    }

    private static boolean isBoundary(BlockState state) {
        return state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock;
    }

    /**
     * Mirrors the Structure scanner's connector handoff neighborhood. A ladder/trapdoor column can
     * sit on a Room wall and end one block above or below the supported Floor cell, so looking only
     * at same-Y horizontal neighbours misses valid upper/basement entrances.
     */
    private static void collectVerticalConnectorCandidates(Level world,
                                                           BlockPos current,
                                                           Set<BlockPos> verticalConnectors) {
        if (isVerticalConnector(world.getBlockState(current))) {
            verticalConnectors.add(current.immutable());
        }
        for (Direction direction : List.of(Direction.UP, Direction.DOWN)) {
            BlockPos candidate = current.relative(direction);
            if (isVerticalConnector(world.getBlockState(candidate))) {
                verticalConnectors.add(candidate);
            }
        }
        for (Direction direction : HORIZONTAL) {
            BlockPos horizontal = current.relative(direction);
            for (int dy : new int[]{-1, 0, 1}) {
                BlockPos candidate = horizontal.offset(0, dy, 0);
                if (isVerticalConnector(world.getBlockState(candidate))) {
                    verticalConnectors.add(candidate);
                }
            }
        }
    }

    private static boolean hasValidEntrance(Level world,
                                            Structure structure,
                                            StructureFloor floor,
                                            Set<Long> visited,
                                            Set<BlockPos> boundaryConnectors,
                                            Set<BlockPos> verticalConnectors) {
        for (BlockPos connector : boundaryConnectors) {
            if (isSeparatingHorizontalEntrance(world, connector, visited)) {
                return true;
            }
        }
        for (BlockPos connector : verticalConnectors) {
            BlockState state = world.getBlockState(connector);
            if (isVerticalConnector(state)
                    && verticalConnectorConnectsDifferentFloor(world, structure, floor, connector)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSeparatingHorizontalEntrance(Level world,
                                                           BlockPos connector,
                                                           Set<Long> visited) {
        BlockState state = world.getBlockState(connector);
        Direction facing;
        if (state.getBlock() instanceof DoorBlock) {
            facing = state.getValue(DoorBlock.FACING);
        } else if (state.getBlock() instanceof FenceGateBlock) {
            facing = state.getValue(FenceGateBlock.FACING);
        } else if (state.getBlock() instanceof TrapDoorBlock
                && state.getValue(TrapDoorBlock.OPEN)) {
            facing = state.getValue(TrapDoorBlock.FACING);
        } else {
            return false;
        }

        BlockPos first = connector.relative(facing);
        BlockPos second = connector.relative(facing.getOpposite());
        boolean firstInside = visited.contains(first.asLong());
        boolean secondInside = visited.contains(second.asLong());
        if (firstInside == secondInside) {
            return false;
        }
        BlockPos outside = firstInside ? second : first;
        return isPassageCell(world, outside);
    }

    /**
     * Treats ladders and trapdoors as one vertical connector path. A common valid entrance is
     * Room -> wall-adjacent ladder -> trapdoor -> landing on another persistent Floor. Validating
     * ladders and trapdoors independently makes that path fall between both checks.
     */
    private static boolean verticalConnectorConnectsDifferentFloor(Level world,
                                                                   Structure structure,
                                                                   StructureFloor floor,
                                                                   BlockPos connector) {
        if (!isVerticalConnector(world.getBlockState(connector))) {
            return false;
        }
        return scanVerticalConnectorDirection(world, structure, floor, connector, Direction.UP)
                || scanVerticalConnectorDirection(world, structure, floor, connector, Direction.DOWN);
    }

    private static boolean scanVerticalConnectorDirection(Level world,
                                                          Structure structure,
                                                          StructureFloor floor,
                                                          BlockPos connector,
                                                          Direction direction) {
        int minY = structure.getFloors().stream()
                .mapToInt(StructureFloor::anchorY).min().orElse(floor.anchorY()) - 1;
        int maxY = structure.getFloors().stream()
                .mapToInt(StructureFloor::ceilingY).max().orElse(floor.ceilingY()) + 1;
        BlockPos cursor = connector;
        while (cursor.getY() >= minY && cursor.getY() <= maxY) {
            if (touchesDifferentFloorLanding(world, structure, floor, cursor)) {
                return true;
            }

            BlockState state = world.getBlockState(cursor);
            if (isVerticalConnector(state)) {
                // Connector blocks inside persisted Structure volume can themselves resolve to the
                // destination Floor. Wall-mounted connectors usually sit outside that volume, so
                // the landing-neighbour check above is the authoritative fallback for those.
                if (structure.resolveFloor(cursor)
                        .filter(candidate -> candidate.id() != floor.id())
                        .isPresent()) {
                    return true;
                }
                cursor = cursor.relative(direction);
                continue;
            }
            return isDifferentFloorLanding(world, structure, floor, cursor);
        }
        return false;
    }

    /** Uses the same same-level/one-block-diagonal handoff shape as Structure traversal. */
    private static boolean touchesDifferentFloorLanding(Level world,
                                                        Structure structure,
                                                        StructureFloor floor,
                                                        BlockPos connector) {
        for (Direction direction : HORIZONTAL) {
            BlockPos horizontal = connector.relative(direction);
            for (int dy : new int[]{-1, 0, 1}) {
                if (isDifferentFloorLanding(world, structure, floor, horizontal.offset(0, dy, 0))) {
                    return true;
                }
            }
        }
        return isDifferentFloorLanding(world, structure, floor, connector.above())
                || isDifferentFloorLanding(world, structure, floor, connector.below());
    }

    private static boolean isDifferentFloorLanding(Level world,
                                                   Structure structure,
                                                   StructureFloor floor,
                                                   BlockPos candidate) {
        return structure.containsPos(candidate)
                && isPassageCell(world, candidate)
                && structure.resolveFloor(candidate)
                .filter(candidateFloor -> candidateFloor.id() != floor.id())
                .isPresent();
    }

    private static boolean isPassageCell(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (isBoundary(state)) {
            return false;
        }
        return state.getBlock() instanceof LadderBlock
                || state.isAir()
                || state.canBeReplaced()
                || state.getCollisionShape(world, pos).isEmpty();
    }

    private static boolean isVerticalConnector(BlockState state) {
        return state.getBlock() instanceof LadderBlock
                || state.getBlock() instanceof TrapDoorBlock;
    }

    private static Set<BlockPos> collectPoiCells(Level world,
                                                  Set<BlockPos> footprint,
                                                  int floorY,
                                                  int ceilingY) {
        LinkedHashSet<BlockPos> poi = new LinkedHashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (BlockPos cell : footprint) {
            cursor.set(cell.getX(), floorY - 1, cell.getZ());
            if (!world.getBlockState(cursor).isAir()) poi.add(cursor.immutable());
            for (int y = floorY; y < ceilingY; y++) {
                cursor.set(cell.getX(), y, cell.getZ());
                if (!world.getBlockState(cursor).isAir()) poi.add(cursor.immutable());
            }
        }
        return Set.copyOf(poi);
    }

    private static long packColumn(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }

    enum Status {
        SUCCESS,
        OVERLAP,
        BLOCK_LIMIT,
        SIZE_LIMIT,
        TOO_SMALL
    }

    record Result(Status status,
                  BlockPos seed,
                  int floorId,
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

        static Result failure(Status status, BlockPos seed) {
            return new Result(status, seed, -1, seed.getY(), Set.of(), Set.of(), false, seed, seed);
        }
    }
}
