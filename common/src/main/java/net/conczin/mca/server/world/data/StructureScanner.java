package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.*;

/** Discovers only physical Structure geometry. It never creates or classifies Rooms. */
final class StructureScanner {
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final int ROOF_SEARCH = 16;
    private static final int SURFACE_SAMPLE_MARGIN = 3;

    private StructureScanner() {
    }

    static Result scan(Level world, BlockPos source, Collection<Structure> existing, int ignoredStructureId) {
        int maxSize = Config.getInstance().maxBuildingSize;
        int maxRadius = Config.getInstance().maxBuildingRadius;
        int minSize = Config.getInstance().minBuildingSize;

        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> volume = new HashSet<>();
        Set<BlockPos> connectorCells = new HashSet<>();
        Set<BuildingFloorRegionDetector.FloorCell> floorCells = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, Boolean> roof = new HashMap<>();

        BlockPos seed = resolveScanSeed(world, source, roof, maxRadius).orElse(null);
        if (seed == null) {
            return Result.failure(Building.validationResult.TOO_SMALL, source);
        }

        queue.add(seed);
        visited.add(seed);

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (horizontalDistance(current, seed) >= maxRadius) {
                return Result.failure(Building.validationResult.SIZE_LIMIT, source);
            }

            BlockState currentState = world.getBlockState(current);
            boolean connector = StructureConnector.isConnector(currentState);
            boolean walkable = !connector && isWalkableAnchor(world, current, currentState, roof);
            boolean floorObstacle = !connector && !walkable
                    && isFloorOccupyingObstacle(world, current, currentState, roof);
            if (connector) {
                volume.add(current);
                connectorCells.add(current);
            } else if (walkable) {
                recordFloorCell(world, floorCells, current);
                addVerticalInteriorColumn(world, current, volume, roof, maxSize);
            } else if (floorObstacle) {
                // Furniture is part of the Floor footprint, even when several occupied cells are
                // adjacent. Traverse the obstacle chain horizontally without promoting its top
                // surface into a separate Floor.
                recordFloorCell(world, floorCells, current);
                addObstacleInteriorColumn(world, current, volume, roof, maxSize);
            } else {
                continue;
            }
            if (volume.size() > maxSize) {
                return Result.failure(Building.validationResult.BLOCK_LIMIT, source);
            }

            for (Direction direction : HORIZONTAL) {
                BlockPos next = current.relative(direction);
                BlockState nextState = world.getBlockState(next);

                boolean nextFloorObstacle = !connector
                        && isFloorOccupyingObstacle(world, next, nextState, roof);
                if (nextFloorObstacle) {
                    recordFloorCell(world, floorCells, next);
                    addObstacleInteriorColumn(world, next, volume, roof, maxSize);
                }

                if (StructureConnector.isHorizontalBoundary(currentState)
                        && isExteriorSide(world, current, next, roof, maxRadius)) {
                    continue;
                }

                if (StructureConnector.isConnector(nextState)
                        || isWalkableAnchor(world, next, nextState, roof)
                        || nextFloorObstacle) {
                    enqueueTraversal(seed, next, visited, queue, maxRadius);
                }
            }

            // Explicit connector handoff handles real ladder/trapdoor floor openings. A ladder
            // usually occupies the hole below a storey, so the air directly above it is not a
            // supported walkable anchor; entering/exiting therefore happens diagonally between
            // the connector column and an adjacent supported cell one block up/down.
            enqueueConnectorHandoffs(world, seed, current, currentState, visited, queue, roof, maxRadius);

            if (walkable) {
                // Collision-aware one-block diagonal transitions connect stair/slab-like geometry
                // without making individual stair steps into physical Floors.
                for (Direction direction : HORIZONTAL) {
                    enqueueStep(world, seed, current, direction, visited, queue, roof, maxRadius);
                }
            }

            // Bare vertical air is volume, not traversal. Only ladders/trapdoors explicitly
            // bridge storeys; this prevents an open shaft from merging unrelated Floors.
            for (Direction direction : List.of(Direction.UP, Direction.DOWN)) {
                BlockPos next = current.relative(direction);
                BlockState nextState = world.getBlockState(next);
                if (!(StructureConnector.isVertical(currentState) || StructureConnector.isVertical(nextState))) {
                    continue;
                }
                if (StructureConnector.isConnector(nextState) || isWalkableAnchor(world, next, nextState, roof)) {
                    enqueueTraversal(seed, next, visited, queue, maxRadius);
                }
            }
        }

        floorCells.addAll(StructureConnector.associatedFloorCells(world, connectorCells, floorCells));
        List<BuildingFloorRegion> regions = BuildingFloorRegionDetector.detect(floorCells);
        int scannedEnvelopeSize = scannedEnvelopeSize(volume);
        if (regions.isEmpty() || scannedEnvelopeSize <= minSize) {
            return Result.failure(Building.validationResult.TOO_SMALL, source);
        }

        List<StructureFloor> floors = toFloors(world, regions);
        BlockPos min = new BlockPos(
                volume.stream().mapToInt(BlockPos::getX).min().orElse(seed.getX()),
                volume.stream().mapToInt(BlockPos::getY).min().orElse(seed.getY()),
                volume.stream().mapToInt(BlockPos::getZ).min().orElse(seed.getZ()));
        BlockPos max = new BlockPos(
                volume.stream().mapToInt(BlockPos::getX).max().orElse(seed.getX()),
                volume.stream().mapToInt(BlockPos::getY).max().orElse(seed.getY()),
                volume.stream().mapToInt(BlockPos::getZ).max().orElse(seed.getZ()));
        Structure candidate = new Structure(ignoredStructureId, seed, min, max, floors);
        int surfaceReferenceY = detectSurfaceReferenceY(world, volume, floors, seed);

        for (Structure other : existing) {
            if (other.getId() != ignoredStructureId && candidate.intersects(other)) {
                return Result.failure(Building.validationResult.OVERLAP, source);
            }
        }
        return new Result(Building.validationResult.SUCCESS, seed, min, max, floors, surfaceReferenceY);
    }

    /**
     * The supplied source is the single source of truth for scan identity. Player actions pass
     * the player's feet position; rescans pass the persisted interior seed. Searching downward
     * or sideways here can silently select a different room or Structure than the caller occupies.
     */
    private static Optional<BlockPos> resolveScanSeed(Level world,
                                                      BlockPos source,
                                                      Map<BlockPos, Boolean> roof,
                                                      int maxRadius) {
        if (!isWalkableAnchor(world, source, world.getBlockState(source), roof)
                || reachesExterior(world, source, null, roof, maxRadius)) {
            return Optional.empty();
        }
        return Optional.of(source.immutable());
    }

    private static boolean isWalkableAnchor(Level world,
                                            BlockPos pos,
                                            BlockState state,
                                            Map<BlockPos, Boolean> roof) {
        return walkableAnchorDecision(world, pos, state, roof) == WalkableAnchorDecision.ACCEPTED;
    }

    static boolean isWalkableAnchor(Level world, BlockPos pos) {
        return walkableAnchorDecision(world, pos) == WalkableAnchorDecision.ACCEPTED;
    }

    static WalkableAnchorDecision walkableAnchorDecision(Level world, BlockPos pos) {
        return walkableAnchorDecision(world, pos, world.getBlockState(pos), new HashMap<>());
    }

    private static WalkableAnchorDecision walkableAnchorDecision(Level world,
                                                                 BlockPos pos,
                                                                 BlockState state,
                                                                 Map<BlockPos, Boolean> roof) {
        if (!isOpen(world, pos, state)) return WalkableAnchorDecision.BLOCKED;
        BlockPos head = pos.above();
        if (!isOpen(world, head, world.getBlockState(head))) return WalkableAnchorDecision.BLOCKED_HEADROOM;
        if (!isSupported(world, pos.getX(), pos.getY(), pos.getZ())) return WalkableAnchorDecision.UNSUPPORTED;
        if (!hasRoof(world, pos, roof)) return WalkableAnchorDecision.NO_ROOF;
        return WalkableAnchorDecision.ACCEPTED;
    }

    enum WalkableAnchorDecision {
        ACCEPTED, BLOCKED, BLOCKED_HEADROOM, UNSUPPORTED, NO_ROOF
    }

    private static void enqueueTraversal(BlockPos source,
                                         BlockPos candidate,
                                         Set<BlockPos> visited,
                                         ArrayDeque<BlockPos> queue,
                                         int maxRadius) {
        if (horizontalDistance(candidate, source) >= maxRadius || !visited.add(candidate)) {
            return;
        }
        queue.addLast(candidate);
    }

    private static void addVerticalInteriorColumn(Level world,
                                                   BlockPos floorCell,
                                                   Set<BlockPos> volume,
                                                   Map<BlockPos, Boolean> roof,
                                                   int maxSize) {
        for (int rise = 0; rise < ROOF_SEARCH && volume.size() <= maxSize; rise++) {
            BlockPos cell = floorCell.offset(0, rise, 0);
            BlockState state = world.getBlockState(cell);
            if (!isOpen(world, cell, state) || !hasRoof(world, cell, roof)) {
                break;
            }
            volume.add(cell);
        }
    }

    private static void addObstacleInteriorColumn(Level world,
                                                   BlockPos obstacle,
                                                   Set<BlockPos> volume,
                                                   Map<BlockPos, Boolean> roof,
                                                   int maxSize) {
        for (int rise = 1; rise < ROOF_SEARCH && volume.size() <= maxSize; rise++) {
            BlockPos cell = obstacle.offset(0, rise, 0);
            BlockState state = world.getBlockState(cell);
            if (StructureConnector.isConnector(state)) {
                volume.add(cell);
                continue;
            }
            if (!isOpen(world, cell, state)) {
                continue;
            }
            addVerticalInteriorColumn(world, cell, volume, roof, maxSize);
            return;
        }
    }

    private static List<StructureFloor> toFloors(Level world, List<BuildingFloorRegion> regions) {
        List<BuildingFloorRegion> sorted = regions.stream()
                .sorted(Comparator.comparingInt(BuildingFloorRegion::anchorY))
                .toList();
        List<StructureFloor> floors = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            BuildingFloorRegion region = sorted.get(i);
            int ceiling = i + 1 < sorted.size()
                    ? sorted.get(i + 1).anchorY()
                    : resolveTopFloorCeiling(world, region);
            floors.add(new StructureFloor(i, region.anchorY(), ceiling, region));
        }
        return List.copyOf(floors);
    }

    private static int resolveTopFloorCeiling(Level world, BuildingFloorRegion region) {
        int ceiling = region.anchorY() + 2;
        for (BlockPos cell : region.cells()) {
            for (int rise = 1; rise <= ROOF_SEARCH; rise++) {
                BlockPos probe = new BlockPos(cell.getX(), region.anchorY() + rise, cell.getZ());
                BlockState state = world.getBlockState(probe);
                if (isRoofBlock(world, probe, state)) {
                    ceiling = Math.max(ceiling, probe.getY());
                    break;
                }
            }
        }
        return ceiling;
    }


    /** A horizontal connector is internal only when its far side remains enclosed. */
    private static boolean isExteriorSide(Level world,
                                          BlockPos connector,
                                          BlockPos side,
                                          Map<BlockPos, Boolean> roof,
                                          int maxRadius) {
        BlockState state = world.getBlockState(side);
        if (!isOpen(world, side, state)
                || !isOpen(world, side.above(), world.getBlockState(side.above()))
                || !isSupported(world, side.getX(), side.getY(), side.getZ())) {
            return false;
        }
        boolean roofed = hasRoof(world, side, roof);
        boolean reachesUncovered = roofed
                && reachesExterior(world, side, connector, roof, maxRadius);
        return !roofed || reachesUncovered;
    }

    /**
     * Follows ordinary walkable transitions without crossing another connector. Sky exposure or
     * escaping the configured Structure radius proves that this side is outside. No terrain
     * height, sea level or absolute Y assumption is involved, so floating buildings behave the
     * same as ground buildings.
     */
    private static boolean reachesExterior(Level world,
                                           BlockPos start,
                                           BlockPos blockedBoundary,
                                           Map<BlockPos, Boolean> roof,
                                           int maxRadius) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (horizontalDistance(current, start) >= maxRadius - 1) return true;
            double currentFloor = floorSurface(world, current);
            for (Direction direction : HORIZONTAL) {
                BlockPos horizontal = current.relative(direction);
                for (int dy : new int[]{0, 1, -1}) {
                    BlockPos next = horizontal.offset(0, dy, 0);
                    if ((blockedBoundary != null && next.equals(blockedBoundary)) || visited.contains(next)) {
                        continue;
                    }
                    BlockState nextState = world.getBlockState(next);
                    if (StructureConnector.isConnector(nextState) || !isOpen(world, next, nextState)
                            || !isOpen(world, next.above(), world.getBlockState(next.above()))
                            || !isSupported(world, next.getX(), next.getY(), next.getZ())) {
                        continue;
                    }
                    double delta = floorSurface(world, next) - currentFloor;
                    if (delta > 1.125D || delta < -1.125D) {
                        continue;
                    }
                    visited.add(next);
                    if (!hasRoof(world, next, roof)) {
                        return true;
                    }
                    queue.addLast(next);
                }
            }
        }
        return false;
    }

    private static void enqueueConnectorHandoffs(Level world,
                                                 BlockPos source,
                                                 BlockPos current,
                                                 BlockState currentState,
                                                 Set<BlockPos> visited,
                                                 ArrayDeque<BlockPos> queue,
                                                 Map<BlockPos, Boolean> roof,
                                                 int maxRadius) {
        boolean fromVerticalConnector = StructureConnector.isVertical(currentState);
        for (Direction direction : HORIZONTAL) {
            BlockPos horizontal = current.relative(direction);
            for (int dy : new int[]{-1, 1}) {
                BlockPos candidate = horizontal.offset(0, dy, 0);
                BlockState state = world.getBlockState(candidate);
                if (fromVerticalConnector) {
                    if (isWalkableAnchor(world, candidate, state, roof)) {
                        enqueueTraversal(source, candidate, visited, queue, maxRadius);
                    }
                } else if (StructureConnector.isVertical(state)) {
                    enqueueTraversal(source, candidate, visited, queue, maxRadius);
                }
            }
        }
    }

    private static void enqueueStep(Level world,
                                    BlockPos source,
                                    BlockPos current,
                                    Direction direction,
                                    Set<BlockPos> visited,
                                    ArrayDeque<BlockPos> queue,
                                    Map<BlockPos, Boolean> roof,
                                    int maxRadius) {
        double fromFloor = floorSurface(world, current);
        BlockPos horizontal = current.relative(direction);
        for (int dy : new int[]{1, -1}) {
            BlockPos candidate = horizontal.offset(0, dy, 0);
            if (visited.contains(candidate) || horizontalDistance(candidate, source) >= maxRadius) continue;
            if (!isOpen(world, candidate, world.getBlockState(candidate))
                    || !isOpen(world, candidate.above(), world.getBlockState(candidate.above()))) continue;
            if (!isSupported(world, candidate.getX(), candidate.getY(), candidate.getZ())) continue;
            double toFloor = floorSurface(world, candidate);
            double delta = toFloor - fromFloor;
            if (delta > 1.125D || delta < -1.125D) continue;
            if (!hasRoof(world, candidate, roof)) continue;
            visited.add(candidate);
            queue.addLast(candidate);
            return;
        }
    }

    private static boolean isOpen(Level world, BlockPos pos, BlockState state) {
        return state.getFluidState().isEmpty() && state.getCollisionShape(world, pos).isEmpty();
    }

    private static boolean isFloorOccupyingObstacle(Level world,
                                                    BlockPos pos,
                                                    BlockState state,
                                                    Map<BlockPos, Boolean> roof) {
        if (StructureConnector.isConnector(state) || isOpen(world, pos, state)
                || !isSupported(world, pos.getX(), pos.getY(), pos.getZ())) {
            return false;
        }
        // A one- or two-block-high obstacle (furniture, counters, stacked decorations) must not
        // punch a hole in an otherwise valid physical Floor. It only counts when usable enclosed
        // room volume exists above the obstruction before the roof; a pillar reaching the roof does not.
        for (int rise = 1; rise <= 2; rise++) {
            BlockPos probe = pos.offset(0, rise, 0);
            BlockState probeState = world.getBlockState(probe);
            if (isOpen(world, probe, probeState)) {
                return hasRoof(world, probe, roof);
            }
            if (StructureConnector.isConnector(probeState)) {
                return true;
            }
        }
        BlockPos aboveStack = pos.offset(0, 3, 0);
        return isOpen(world, aboveStack, world.getBlockState(aboveStack))
                && hasRoof(world, aboveStack, roof);
    }

    private static boolean hasRoof(Level world, BlockPos pos, Map<BlockPos, Boolean> cache) {
        Boolean known = cache.get(pos);
        if (known != null) return known;
        List<BlockPos> checked = new ArrayList<>();
        BlockPos cursor = pos;
        for (int i = 0; i < ROOF_SEARCH; i++) {
            checked.add(cursor);
            BlockPos above = cursor.above();
            BlockState state = world.getBlockState(above);
            if (isRoofBlock(world, above, state)) {
                checked.forEach(cell -> cache.put(cell, true));
                return true;
            }
            cursor = above;
        }
        checked.forEach(cell -> cache.put(cell, false));
        return false;
    }

    private static boolean isRoofBlock(Level world, BlockPos pos, BlockState state) {
        return state.getFluidState().isEmpty()
                && !state.is(BlockTags.LEAVES)
                && !state.getCollisionShape(world, pos).isEmpty();
    }

    static boolean isSupported(Level world, int x, int floorY, int z) {
        return isSupported(world, x, floorY, z, new BlockPos.MutableBlockPos());
    }

    static boolean isSupported(Level world, int x, int floorY, int z, BlockPos.MutableBlockPos cursor) {
        cursor.set(x, floorY - 1, z);
        var shape = world.getBlockState(cursor).getCollisionShape(world, cursor);
        if (shape.isEmpty()) return false;
        double width = shape.max(Direction.Axis.X) - shape.min(Direction.Axis.X);
        double depth = shape.max(Direction.Axis.Z) - shape.min(Direction.Axis.Z);
        return width * depth >= 0.25D;
    }

    private static double floorSurface(Level world, BlockPos interior) {
        BlockPos support = interior.below();
        var shape = world.getBlockState(support).getCollisionShape(world, support);
        return support.getY() + (shape.isEmpty() ? 0.0D : shape.max(Direction.Axis.Y));
    }

    private static void recordFloorCell(Level world,
                                        Set<BuildingFloorRegionDetector.FloorCell> floorCells,
                                        BlockPos pos) {
        if (world.getBlockState(pos.below()).is(BlockTags.STAIRS)) return;
        if (isSupported(world, pos.getX(), pos.getY(), pos.getZ())) {
            floorCells.add(new BuildingFloorRegionDetector.FloorCell(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    /**
     * Preserves the legacy meaning of minBuildingSize. The original building flood counted the
     * examined scan envelope, including the immediate enclosing boundary, rather than only the
     * reachable interior-air volume.
     */
    private static int scannedEnvelopeSize(Collection<BlockPos> volume) {
        Set<BlockPos> examined = new HashSet<>(volume);
        for (BlockPos cell : volume) {
            for (Direction direction : Direction.values()) {
                examined.add(cell.relative(direction));
            }
        }
        return examined.size();
    }

    private static int detectSurfaceReferenceY(Level world,
                                               Collection<BlockPos> volume,
                                               List<StructureFloor> floors,
                                               BlockPos fallback) {
        if (volume.isEmpty() || floors.isEmpty()) return fallback.getY();
        int minX = volume.stream().mapToInt(BlockPos::getX).min().orElse(fallback.getX())
                - SURFACE_SAMPLE_MARGIN;
        int maxX = volume.stream().mapToInt(BlockPos::getX).max().orElse(fallback.getX())
                + SURFACE_SAMPLE_MARGIN;
        int minZ = volume.stream().mapToInt(BlockPos::getZ).min().orElse(fallback.getZ())
                - SURFACE_SAMPLE_MARGIN;
        int maxZ = volume.stream().mapToInt(BlockPos::getZ).max().orElse(fallback.getZ())
                + SURFACE_SAMPLE_MARGIN;
        int minRelevantY = floors.stream().mapToInt(StructureFloor::anchorY).min().orElse(fallback.getY())
                - ROOF_SEARCH;
        int maxRelevantY = floors.stream().mapToInt(StructureFloor::ceilingY).max().orElse(fallback.getY())
                + ROOF_SEARCH;

        List<Integer> samples = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            addSurfaceSample(world, samples, x, minZ, minRelevantY, maxRelevantY);
            if (maxZ != minZ) addSurfaceSample(world, samples, x, maxZ, minRelevantY, maxRelevantY);
        }
        for (int z = minZ + 1; z < maxZ; z++) {
            addSurfaceSample(world, samples, minX, z, minRelevantY, maxRelevantY);
            if (maxX != minX) addSurfaceSample(world, samples, maxX, z, minRelevantY, maxRelevantY);
        }
        if (samples.isEmpty()) return fallback.getY();
        samples.sort(Integer::compareTo);
        int middle = samples.size() / 2;
        return samples.size() % 2 == 1
                ? samples.get(middle)
                : Math.floorDiv(samples.get(middle - 1) + samples.get(middle), 2);
    }

    private static void addSurfaceSample(Level world,
                                         Collection<Integer> samples,
                                         int x,
                                         int z,
                                         int minRelevantY,
                                         int maxRelevantY) {
        int y = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y >= minRelevantY && y <= maxRelevantY) samples.add(y);
    }

    private static int horizontalDistance(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());
    }

    record Result(Building.validationResult result,
                  BlockPos source,
                  BlockPos min,
                  BlockPos max,
                  List<StructureFloor> floors,
                  int surfaceReferenceY) {
        Result {
            floors = List.copyOf(floors);
        }

        static Result failure(Building.validationResult result, BlockPos source) {
            return new Result(result, source, source, source, List.of(), source.getY());
        }

        Structure toStructure(int id) {
            List<StructureFloor> assigned = new ArrayList<>();
            for (int i = 0; i < floors.size(); i++) {
                StructureFloor floor = floors.get(i);
                assigned.add(new StructureFloor(i, floor.anchorY(), floor.ceilingY(), floor.region()));
            }
            Structure structure = new Structure(id, source, min, max, assigned);
            structure.setSurfaceReferenceY(surfaceReferenceY);
            return structure;
        }
    }
}
