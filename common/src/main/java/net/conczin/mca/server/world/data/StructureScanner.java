package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.*;
import java.util.function.Predicate;

/** Discovers only physical Structure geometry. It never creates or classifies Rooms. */
final class StructureScanner {
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final int ROOF_SEARCH = 16;
    private static final int SOURCE_HORIZONTAL_SEARCH = 2;
    private static final int TERRAIN_SAMPLE_MARGIN = 3;

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
        Set<BlockPos> horizontalEntrances = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, Boolean> roof = new HashMap<>();

        BlockPos seed = resolveScanSeed(world, source, roof).orElse(null);
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
                if (StructureConnector.isGroundEntrance(nextState)) {
                    horizontalEntrances.add(next);
                }

                boolean nextFloorObstacle = !connector
                        && isFloorOccupyingObstacle(world, next, nextState, roof);
                if (nextFloorObstacle) {
                    recordFloorCell(world, floorCells, next);
                    addObstacleInteriorColumn(world, next, volume, roof, maxSize);
                }

                if (StructureConnector.isGroundEntrance(currentState)
                        && isExteriorEntranceSide(world, current, next, roof)) {
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

        Set<ExteriorEntrance> exteriorEntrances = horizontalEntrances.stream()
                .map(entrance -> findExteriorEntrance(world, entrance, volume, roof).orElse(null))
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        exteriorEntrances.addAll(findVerticalExteriorEntrances(
                world, connectorCells, candidate, volume,
                outside -> !hasRoof(world, outside, roof)
                        || canReachUncoveredSupportedSpace(world, outside, null, roof)));
        GroundChoice groundChoice = chooseGroundFloor(world, floors, exteriorEntrances, volume);
        StructureFloor ground = floors.stream()
                .min(Comparator.comparingInt((StructureFloor floor) -> Math.abs(floor.anchorY() - groundChoice.floorY()))
                        .thenComparingInt(StructureFloor::anchorY))
                .orElse(floors.getFirst());
        BlockPos groundSeed = bestSeed(ground, groundChoice.entranceInterior());

        for (Structure other : existing) {
            if (other.getId() != ignoredStructureId && candidate.intersects(other)) {
                return Result.failure(Building.validationResult.OVERLAP, source);
            }
        }
        return new Result(Building.validationResult.SUCCESS, seed, min, max,
                floors, ground.id(), groundSeed,
                groundChoice.referenceY(), groundChoice.entranceCount());
    }

    /** Resolves a query point such as a flying player or roof position to nearby enclosed interior. */
    private static Optional<BlockPos> resolveScanSeed(Level world,
                                                      BlockPos source,
                                                      Map<BlockPos, Boolean> roof) {
        for (int yOffset = 0; yOffset >= -ROOF_SEARCH; yOffset--) {
            int y = source.getY() + yOffset;
            for (int radius = 0; radius <= SOURCE_HORIZONTAL_SEARCH; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.abs(dx) + Math.abs(dz) != radius) continue;
                        BlockPos candidate = new BlockPos(source.getX() + dx, y, source.getZ() + dz);
                        if (!isWalkableAnchor(world, candidate, world.getBlockState(candidate), roof)) {
                            continue;
                        }
                        boolean exteriorLike = canReachUncoveredSupportedSpace(world, candidate, null, roof);
                        if (!exteriorLike) {
                            return Optional.of(candidate);
                        }
                    }
                }
            }
        }
        return Optional.empty();
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
        if (!isSupported(world, pos.getX(), pos.getY(), pos.getZ())) return WalkableAnchorDecision.UNSUPPORTED;
        if (!hasRoof(world, pos, roof)) return WalkableAnchorDecision.NO_ROOF;
        return WalkableAnchorDecision.ACCEPTED;
    }

    /** Explains the exact walkable-anchor predicate used by connector handoffs without changing traversal. */
    static String explainWalkableAnchor(Level world, BlockPos pos) {
        return walkableAnchorDecision(world, pos).name();
    }

    enum WalkableAnchorDecision {
        ACCEPTED, BLOCKED, UNSUPPORTED, NO_ROOF
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
                if (!state.isAir()) {
                    if (!state.is(BlockTags.LEAVES)) {
                        ceiling = Math.max(ceiling, probe.getY());
                    }
                    break;
                }
            }
        }
        return ceiling;
    }


    /**
     * Normal doors may connect internal Rooms or open to the exterior. A roof overhang can make
     * exterior terrain look like a roofed walkable Floor, so do not cross a door into a side that
     * can escape horizontally to uncovered open space without crossing another boundary block.
     */
    private static boolean isExteriorEntranceSide(Level world,
                                                  BlockPos entrance,
                                              BlockPos side,
                                              Map<BlockPos, Boolean> roof) {
        BlockState state = world.getBlockState(side);
        if (!isOpen(world, side, state)
                || !isSupported(world, side.getX(), side.getY(), side.getZ())) {
            return false;
        }
        boolean roofed = hasRoof(world, side, roof);
        boolean reachesUncovered = roofed && canReachUncoveredSupportedSpace(world, side, entrance, roof);
        return !roofed || reachesUncovered;
    }

    private static Optional<ExteriorEntrance> findExteriorEntrance(Level world,
                                                                     BlockPos entrance,
                                                                     Set<BlockPos> interior,
                                                                     Map<BlockPos, Boolean> roof) {
        BlockState entranceState = world.getBlockState(entrance);
        Direction facing = StructureConnector.horizontalFacing(entranceState).orElse(null);
        if (facing == null || !StructureConnector.isGroundEntrance(entranceState)) {
            return Optional.empty();
        }
        BlockPos first = entrance.relative(facing);
        BlockPos second = entrance.relative(facing.getOpposite());
        boolean firstExterior = isExteriorEntranceSide(world, entrance, first, roof);
        boolean secondExterior = isExteriorEntranceSide(world, entrance, second, roof);
        if (firstExterior == secondExterior) {
            return Optional.empty();
        }
        BlockPos outside = firstExterior ? first : second;
        BlockPos inside = firstExterior ? second : first;
        if (!interior.contains(inside) || interior.contains(outside)) {
            return Optional.empty();
        }
        BlockState insideState = world.getBlockState(inside);
        if (!isOpen(world, inside, insideState)
                || !isSupported(world, inside.getX(), inside.getY(), inside.getZ())) {
            return Optional.empty();
        }
        return Optional.of(new ExteriorEntrance(inside, outside));
    }

    /** Returns actual Structure landings for vertical chains that also touch exterior supported space. */
    private static Set<ExteriorEntrance> findVerticalExteriorEntrances(
            Level world, Collection<BlockPos> connectorCells, Structure structure,
            Set<BlockPos> interior, Predicate<BlockPos> exteriorLike) {
        Set<BlockPos> remaining = connectorCells.stream()
                .filter(pos -> StructureConnector.isVertical(world.getBlockState(pos)))
                .map(BlockPos::immutable)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<ExteriorEntrance> result = new LinkedHashSet<>();
        while (!remaining.isEmpty()) {
            BlockPos seed = remaining.iterator().next();
            remaining.remove(seed);
            Set<BlockPos> chain = takeKnownChain(seed, remaining);
            List<BlockPos> inside = chain.stream().flatMap(pos -> StructureConnector.handoffs(pos).stream())
                    .filter(interior::contains).filter(pos -> StructureConnector.isPassageCell(world, pos))
                    .distinct().toList();
            List<BlockPos> outside = chain.stream().flatMap(pos -> StructureConnector.handoffs(pos).stream())
                    .filter(pos -> !interior.contains(pos) && StructureConnector.isPassageCell(world, pos))
                    .filter(pos -> isSupported(world, pos.getX(), pos.getY(), pos.getZ()))
                    .filter(exteriorLike)
                    .sorted(Comparator.comparingInt((BlockPos pos) -> pos.getY())
                            .thenComparingInt(Vec3i::getX).thenComparingInt(Vec3i::getZ))
                    .toList();
            if (inside.isEmpty() || outside.isEmpty()) continue;
            BlockPos out = outside.getFirst();
            StructureFloor floor = inside.stream().map(structure::physicalFloorAt)
                    .flatMap(optional -> optional.stream()).filter(candidate -> candidate.anchorY() <= out.getY())
                    .max(Comparator.comparingInt(StructureFloor::anchorY)).orElse(null);
            if (floor == null) continue;
            BlockPos in = inside.stream()
                    .filter(pos -> structure.physicalFloorAt(pos)
                            .map(candidate -> candidate.id() == floor.id()).orElse(false))
                    .min(Comparator.comparingInt((BlockPos pos) -> Math.abs(pos.getY() - out.getY()))
                            .thenComparingInt(Vec3i::getY).thenComparingInt(Vec3i::getX)
                            .thenComparingInt(Vec3i::getZ)).orElse(null);
            if (in != null) result.add(new ExteriorEntrance(in, out));
        }
        return Set.copyOf(result);
    }

    private static Set<BlockPos> takeKnownChain(BlockPos seed, Set<BlockPos> remaining) {
        LinkedHashSet<BlockPos> chain = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        chain.add(seed);
        queue.add(seed);
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (remaining.remove(next)) {
                    chain.add(next);
                    queue.addLast(next);
                }
            }
        }
        return Set.copyOf(chain);
    }

    /**
     * Follows the same one-block walkable transitions used by Structure traversal until roof cover
     * ends. Boundary connectors are barriers. This is deliberately step-aware: exterior terrain
     * is often one block below a doorway or porch, and a same-Y-only flood misclassifies that side
     * as enclosed before the main scanner immediately walks down onto it.
     */
    private static boolean canReachUncoveredSupportedSpace(Level world,
                                                            BlockPos start,
                                                            BlockPos blockedBoundary,
                                                            Map<BlockPos, Boolean> roof) {
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (horizontalDistance(current, start) >= ROOF_SEARCH) {
                continue;
            }
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
                            || !isSupported(world, next.getX(), next.getY(), next.getZ())) {
                        continue;
                    }
                    if (dy != 0 && !isOpen(world, next.above(), world.getBlockState(next.above()))) {
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
        return !state.getFluidState().isEmpty()
                || state.isAir()
                || state.canBeReplaced()
                || state.getCollisionShape(world, pos).isEmpty();
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
            if (!state.isAir()) {
                boolean result = !state.is(BlockTags.LEAVES);
                checked.forEach(cell -> cache.put(cell, result));
                return result;
            }
            cursor = above;
        }
        checked.forEach(cell -> cache.put(cell, false));
        return false;
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

    private static GroundChoice chooseGroundFloor(Level world,
                                                   List<StructureFloor> floors,
                                                   Set<ExteriorEntrance> entrances,
                                                   Set<BlockPos> interior) {
        List<Integer> terrainSamples = sampleTerrainPerimeter(world, interior);
        int terrainY = medianTerrainY(terrainSamples);

        // Exterior entrances are already proven to connect enclosed Structure volume to exterior
        // supported space. Do not reject that evidence with a heightmap probe beside the doorway:
        // roofs, overhangs and hills can make MOTION_BLOCKING_NO_LEAVES report an upper storey.
        // The Floor with the most confirmed exterior entrances is Ground; terrain only breaks ties.
        if (!entrances.isEmpty()) {
            Map<Integer, List<ExteriorEntrance>> entrancesByFloor = new LinkedHashMap<>();
            for (ExteriorEntrance entrance : entrances) {
                StructureFloor entranceFloor = floors.stream().min(Comparator
                        .comparingInt((StructureFloor candidate) ->
                                Math.abs(candidate.anchorY() - entrance.inside().getY()))
                        .thenComparingInt(StructureFloor::anchorY))
                        .orElse(floors.getFirst());
                entrancesByFloor.computeIfAbsent(entranceFloor.id(), ignored -> new ArrayList<>())
                        .add(entrance);
            }

            StructureFloor floor = floors.stream()
                    .filter(candidate -> entrancesByFloor.containsKey(candidate.id()))
                    .max(Comparator
                            .comparingInt((StructureFloor candidate) -> entrancesByFloor.get(candidate.id()).size())
                            .thenComparingInt(candidate -> -Math.abs(candidate.anchorY() - terrainY))
                            .thenComparingInt(StructureFloor::anchorY))
                    .orElse(floors.getFirst());
            ExteriorEntrance representative = entrancesByFloor.get(floor.id()).stream().min(Comparator
                    .comparingInt((ExteriorEntrance entrance) ->
                            Math.abs(entrance.inside().getY() - floor.anchorY()))
                    .thenComparingInt(entrance -> entrance.inside().getX())
                    .thenComparingInt(entrance -> entrance.inside().getZ()))
                    .orElseThrow();
            return new GroundChoice(floor.anchorY(), representative.inside(), terrainY,
                    entrancesByFloor.get(floor.id()).size());
        }

        // No exterior connector: fall back to terrain sampled several blocks outside the scanned
        // Structure envelope so roof edges and shallow overhangs do not masquerade as ground level.
        StructureFloor terrainFloor = floors.stream().min(Comparator
                .comparingInt((StructureFloor floor) -> Math.abs(floor.anchorY() - terrainY))
                .thenComparing(Comparator.comparingInt(StructureFloor::anchorY).reversed()))
                .orElse(floors.getFirst());
        return new GroundChoice(terrainFloor.anchorY(), null, terrainY, 0);
    }

    private static List<Integer> sampleTerrainPerimeter(Level world, Collection<BlockPos> interior) {
        int minX = interior.stream().mapToInt(BlockPos::getX).min().orElse(0) - TERRAIN_SAMPLE_MARGIN;
        int maxX = interior.stream().mapToInt(BlockPos::getX).max().orElse(0) + TERRAIN_SAMPLE_MARGIN;
        int minZ = interior.stream().mapToInt(BlockPos::getZ).min().orElse(0) - TERRAIN_SAMPLE_MARGIN;
        int maxZ = interior.stream().mapToInt(BlockPos::getZ).max().orElse(0) + TERRAIN_SAMPLE_MARGIN;
        List<Integer> terrain = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            terrain.add(world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, minZ));
            if (maxZ != minZ) {
                terrain.add(world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, maxZ));
            }
        }
        for (int z = minZ + 1; z < maxZ; z++) {
            terrain.add(world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, minX, z));
            if (maxX != minX) {
                terrain.add(world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, maxX, z));
            }
        }
        return terrain;
    }

    private static int medianTerrainY(Collection<Integer> terrainYs) {
        int[] sorted = terrainYs.stream().mapToInt(Integer::intValue).sorted().toArray();
        if (sorted.length == 0) return 0;
        int middle = sorted.length / 2;
        return sorted.length % 2 == 1
                ? sorted[middle]
                : Math.floorDiv(sorted[middle - 1] + sorted[middle], 2);
    }

    static BlockPos bestSeed(StructureFloor floor, BlockPos preferred) {
        if (preferred != null && floor.contains(preferred.getX(), preferred.getZ())) {
            return new BlockPos(preferred.getX(), floor.anchorY(), preferred.getZ());
        }
        return bestSeed(floor);
    }

    static BlockPos bestSeed(StructureFloor floor) {
        if (floor.region() == null || floor.region().components().isEmpty()) {
            return new BlockPos(0, floor.anchorY(), 0);
        }
        BuildingFloorRegion.Component component = floor.region().components().stream()
                .max(Comparator.comparingInt(BuildingFloorRegion.Component::area)
                        .thenComparingInt(component1 -> -component1.minX())
                        .thenComparingInt(component1 -> -component1.minZ()))
                .orElseThrow();
        int centerX = component.minX() + (component.maxX() - component.minX()) / 2;
        int centerZ = component.minZ() + (component.maxZ() - component.minZ()) / 2;
        return component.spans().stream()
                .min(Comparator.comparingInt((BuildingFloorRegion.Span span) -> Math.abs(span.z() - centerZ))
                        .thenComparingInt(span -> distanceToSpan(centerX, span)))
                .map(span -> new BlockPos(Math.max(span.minX(), Math.min(centerX, span.maxX())), floor.anchorY(), span.z()))
                .orElse(new BlockPos(centerX, floor.anchorY(), centerZ));
    }

    private static int distanceToSpan(int x, BuildingFloorRegion.Span span) {
        if (x < span.minX()) return span.minX() - x;
        if (x > span.maxX()) return x - span.maxX();
        return 0;
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

    private static int horizontalDistance(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());
    }

    private record ExteriorEntrance(BlockPos inside, BlockPos outside) {
    }

    private record GroundChoice(int floorY,
                                BlockPos entranceInterior,
                                int referenceY,
                                int entranceCount) {
    }

    record Result(Building.validationResult result,
                  BlockPos source,
                  BlockPos min,
                  BlockPos max,
                  List<StructureFloor> floors,
                  int groundFloorId,
                  BlockPos groundSeed,
                  int groundReferenceY,
                  int groundEntranceCount) {
        Result {
            floors = List.copyOf(floors);
        }

        static Result failure(Building.validationResult result, BlockPos source) {
            return new Result(result, source, source, source, List.of(), -1, source, source.getY(), 0);
        }

        Structure toStructure(int id) {
            List<StructureFloor> assigned = new ArrayList<>();
            int persistentGroundId = -1;
            for (int i = 0; i < floors.size(); i++) {
                StructureFloor floor = floors.get(i);
                assigned.add(new StructureFloor(i, floor.anchorY(), floor.ceilingY(), floor.region()));
                if (floor.id() == groundFloorId) persistentGroundId = i;
            }
            Structure structure = new Structure(id, source, min, max, assigned);
            structure.setGroundEvidence(persistentGroundId, groundReferenceY, groundEntranceCount);
            return structure;
        }
    }
}
