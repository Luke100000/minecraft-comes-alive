package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.*;

/** Discovers only physical Structure geometry. It never creates or classifies Rooms. */
final class StructureScanner {
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final int ROOF_SEARCH = 16;
    private static final int SOURCE_HORIZONTAL_SEARCH = 2;

    private StructureScanner() {
    }

    static Result scan(Level world, BlockPos source, Collection<Structure> existing, int ignoredStructureId) {
        int maxSize = Config.getInstance().maxBuildingSize;
        int maxRadius = Config.getInstance().maxBuildingRadius;
        int minSize = Config.getInstance().minBuildingSize;

        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> volume = new HashSet<>();
        Set<BuildingFloorRegionDetector.SupportedCell> supported = new HashSet<>();
        Set<BlockPos> normalDoors = new HashSet<>();
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
            boolean connector = isConnector(currentState);
            if (connector) {
                volume.add(current);
            } else if (isWalkableAnchor(world, current, currentState, roof)) {
                recordSupported(world, supported, current);
                addVerticalInteriorColumn(world, current, volume, roof, maxSize);
            } else {
                continue;
            }
            if (volume.size() > maxSize) {
                return Result.failure(Building.validationResult.BLOCK_LIMIT, source);
            }

            for (Direction direction : HORIZONTAL) {
                BlockPos next = current.relative(direction);
                BlockState nextState = world.getBlockState(next);
                if (nextState.getBlock() instanceof DoorBlock) {
                    normalDoors.add(next);
                }

                if (!connector && isFloorOccupyingObstacle(world, next, nextState, roof)) {
                    recordSupported(world, supported, next);
                    addObstacleInteriorColumn(world, next, volume, roof, maxSize);
                }

                if (currentState.getBlock() instanceof DoorBlock
                        && isExteriorDoorSide(world, current, next, roof)) {
                    continue;
                }

                if (isConnector(nextState) || isWalkableAnchor(world, next, nextState, roof)) {
                    enqueueTraversal(seed, next, visited, queue, maxRadius);
                }
            }

            // Explicit connector handoff handles real ladder/trapdoor floor openings. A ladder
            // usually occupies the hole below a storey, so the air directly above it is not a
            // supported walkable anchor; entering/exiting therefore happens diagonally between
            // the connector column and an adjacent supported cell one block up/down.
            enqueueConnectorHandoffs(world, seed, current, currentState, visited, queue, roof, maxRadius);

            if (!connector) {
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
                if (!(isVerticalConnector(currentState) || isVerticalConnector(nextState))) {
                    continue;
                }
                if (isConnector(nextState) || isWalkableAnchor(world, next, nextState, roof)) {
                    enqueueTraversal(seed, next, visited, queue, maxRadius);
                }
            }
        }

        List<BuildingFloorRegion> regions = BuildingFloorRegionDetector.detect(supported);
        if (regions.isEmpty() || volume.size() <= minSize) {
            return Result.failure(Building.validationResult.TOO_SMALL, source);
        }

        List<StructureFloor> floors = toFloors(world, regions);
        List<BuildingFloorRegion> volumeSlices = toVolumeSlices(volume);
        GroundChoice groundChoice = chooseGroundFloor(world, floors, normalDoors, volume);
        StructureFloor ground = floors.stream()
                .min(Comparator.comparingInt((StructureFloor floor) -> Math.abs(floor.anchorY() - groundChoice.floorY()))
                        .thenComparingInt(StructureFloor::anchorY))
                .orElse(floors.getFirst());
        BlockPos groundSeed = bestSeed(ground, groundChoice.entranceInterior());

        BlockPos min = new BlockPos(
                volume.stream().mapToInt(BlockPos::getX).min().orElse(seed.getX()),
                volume.stream().mapToInt(BlockPos::getY).min().orElse(seed.getY()),
                volume.stream().mapToInt(BlockPos::getZ).min().orElse(seed.getZ()));
        BlockPos max = new BlockPos(
                volume.stream().mapToInt(BlockPos::getX).max().orElse(seed.getX()),
                volume.stream().mapToInt(BlockPos::getY).max().orElse(seed.getY()),
                volume.stream().mapToInt(BlockPos::getZ).max().orElse(seed.getZ()));

        Structure candidate = new Structure(ignoredStructureId, seed, min, max, floors, volumeSlices);
        for (Structure other : existing) {
            if (other.getId() != ignoredStructureId && candidate.intersects(other)) {
                return Result.failure(Building.validationResult.OVERLAP, source);
            }
        }
        return new Result(Building.validationResult.SUCCESS, seed, min, max,
                floors, volumeSlices, ground.id(), groundSeed);
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
                        if (isEnclosedInterior(world, candidate, roof)) {
                            return Optional.of(candidate);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isEnclosedInterior(Level world,
                                              BlockPos pos,
                                              Map<BlockPos, Boolean> roof) {
        return isWalkableAnchor(world, pos, world.getBlockState(pos), roof)
                && !canReachUncoveredSupportedSpace(world, pos, null, roof);
    }

    private static boolean isWalkableAnchor(Level world,
                                            BlockPos pos,
                                            BlockState state,
                                            Map<BlockPos, Boolean> roof) {
        return isOpen(world, pos, state)
                && isSupported(world, pos.getX(), pos.getY(), pos.getZ())
                && hasRoof(world, pos, roof);
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
            if (isConnector(state)) {
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
        for (BlockPos cell : floorFootprintCells(region)) {
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

    private static Set<BlockPos> floorFootprintCells(BuildingFloorRegion region) {
        if (region == null) return Set.of();
        LinkedHashSet<BlockPos> cells = new LinkedHashSet<>();
        for (BuildingFloorRegion.Component component : region.components()) {
            if (component.spans().isEmpty()) {
                for (int z = component.minZ(); z <= component.maxZ(); z++) {
                    for (int x = component.minX(); x <= component.maxX(); x++) {
                        cells.add(new BlockPos(x, region.anchorY(), z));
                    }
                }
                continue;
            }
            for (BuildingFloorRegion.Span span : component.spans()) {
                for (int x = span.minX(); x <= span.maxX(); x++) {
                    cells.add(new BlockPos(x, region.anchorY(), span.z()));
                }
            }
        }
        return cells;
    }

    private static List<BuildingFloorRegion> toVolumeSlices(Collection<BlockPos> structuralCells) {
        Map<Integer, List<BlockPos>> byY = new TreeMap<>();
        for (BlockPos cell : structuralCells) {
            byY.computeIfAbsent(cell.getY(), ignored -> new ArrayList<>()).add(cell);
        }
        List<BuildingFloorRegion> slices = new ArrayList<>(byY.size());
        for (Map.Entry<Integer, List<BlockPos>> entry : byY.entrySet()) {
            slices.add(BuildingFloorRegion.fromFootprint(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(slices);
    }

    /**
     * Normal doors may connect internal Rooms or open to the exterior. A roof overhang can make
     * exterior terrain look like a roofed walkable Floor, so do not cross a door into a side that
     * can escape horizontally to uncovered open space without crossing another boundary block.
     */
    private static boolean isExteriorDoorSide(Level world,
                                              BlockPos door,
                                              BlockPos side,
                                              Map<BlockPos, Boolean> roof) {
        BlockState state = world.getBlockState(side);
        if (!isOpen(world, side, state)
                || !isSupported(world, side.getX(), side.getY(), side.getZ())) {
            return false;
        }
        return !hasRoof(world, side, roof)
                || canReachUncoveredSupportedSpace(world, side, door, roof);
    }

    /**
     * Follows supported open terrain horizontally until roof cover ends. Boundary connectors are
     * treated as barriers, so an enclosed Room cannot be classified as exterior merely because a
     * different door elsewhere in the building eventually leads outdoors.
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
            for (Direction direction : HORIZONTAL) {
                BlockPos next = current.relative(direction);
                if ((blockedBoundary != null && next.equals(blockedBoundary)) || !visited.add(next)) {
                    continue;
                }
                BlockState nextState = world.getBlockState(next);
                if (isConnector(nextState) || !isOpen(world, next, nextState)
                        || !isSupported(world, next.getX(), next.getY(), next.getZ())) {
                    continue;
                }
                if (!hasRoof(world, next, roof)) {
                    return true;
                }
                queue.addLast(next);
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
        boolean fromVerticalConnector = isVerticalConnector(currentState);
        for (Direction direction : HORIZONTAL) {
            BlockPos horizontal = current.relative(direction);
            for (int dy : new int[]{-1, 1}) {
                BlockPos candidate = horizontal.offset(0, dy, 0);
                BlockState state = world.getBlockState(candidate);
                if (fromVerticalConnector) {
                    if (isWalkableAnchor(world, candidate, state, roof)) {
                        enqueueTraversal(source, candidate, visited, queue, maxRadius);
                    }
                } else if (isVerticalConnector(state)) {
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
            double delta = floorSurface(world, candidate) - fromFloor;
            if (delta > 1.125D || delta < -1.125D) continue;
            if (!hasRoof(world, candidate, roof)) continue;
            visited.add(candidate);
            queue.addLast(candidate);
            return;
        }
    }

    private static boolean canTraverseVolume(Level world,
                                             BlockPos next,
                                             BlockState nextState,
                                             Map<BlockPos, Boolean> roof) {
        if (isConnector(nextState)) {
            return true;
        }
        return isOpen(world, next, nextState) && hasRoof(world, next, roof);
    }

    private static boolean isConnector(BlockState state) {
        return state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof LadderBlock;
    }

    private static boolean isVerticalConnector(BlockState state) {
        // Trapdoors are explicit floor connectors too; unlike normal doors they can bridge Y.
        return state.getBlock() instanceof LadderBlock || state.getBlock() instanceof TrapDoorBlock;
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
        if (isConnector(state) || isOpen(world, pos, state)
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
            if (isConnector(probeState)) {
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

    private static void recordSupported(Level world,
                                        Set<BuildingFloorRegionDetector.SupportedCell> supported,
                                        BlockPos pos) {
        if (isSupported(world, pos.getX(), pos.getY(), pos.getZ())) {
            supported.add(new BuildingFloorRegionDetector.SupportedCell(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    private static GroundChoice chooseGroundFloor(Level world,
                                                   List<StructureFloor> floors,
                                                   Set<BlockPos> doors,
                                                   Set<BlockPos> interior) {
        List<EntranceCandidate> candidates = new ArrayList<>();
        for (BlockPos door : doors) {
            for (Direction direction : HORIZONTAL) {
                BlockPos inside = door.relative(direction);
                if (!interior.contains(inside)) continue;
                BlockPos outside = door.relative(direction.getOpposite());
                if (interior.contains(outside)) continue;
                int terrainY = world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        outside.getX(), outside.getZ());
                int terrainDelta = Math.abs(inside.getY() - terrainY);
                if (terrainDelta <= BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE) {
                    candidates.add(new EntranceCandidate(inside, terrainDelta));
                }
            }
        }
        if (!candidates.isEmpty()) {
            EntranceCandidate strongest = candidates.stream().min(Comparator
                    .comparingInt(EntranceCandidate::terrainDelta)
                    .thenComparing(Comparator.comparingInt((EntranceCandidate candidate) -> candidate.inside().getY()).reversed())
                    .thenComparingInt(candidate -> candidate.inside().getX())
                    .thenComparingInt(candidate -> candidate.inside().getZ())).orElseThrow();
            StructureFloor floor = floors.stream().min(Comparator
                    .comparingInt((StructureFloor candidate) -> Math.abs(candidate.anchorY() - strongest.inside().getY()))
                    .thenComparingInt(StructureFloor::anchorY)).orElse(floors.getFirst());
            return new GroundChoice(floor.anchorY(), strongest.inside());
        }

        // No reliable normal exterior door: recover the mature floor-system behaviour and use
        // surrounding terrain to distinguish an underground basement from the surface storey.
        // Trapdoors remain connectors only and are never promoted to Ground Floor evidence.
        int terrainY = medianTerrainY(sampleTerrainPerimeter(world, interior));
        StructureFloor terrainFloor = floors.stream().min(Comparator
                .comparingInt((StructureFloor floor) -> Math.abs(floor.anchorY() - terrainY))
                .thenComparing(Comparator.comparingInt(StructureFloor::anchorY).reversed()))
                .orElse(floors.getFirst());
        return new GroundChoice(terrainFloor.anchorY(), null);
    }

    private static List<Integer> sampleTerrainPerimeter(Level world, Collection<BlockPos> interior) {
        int minX = interior.stream().mapToInt(BlockPos::getX).min().orElse(0) - 1;
        int maxX = interior.stream().mapToInt(BlockPos::getX).max().orElse(0) + 1;
        int minZ = interior.stream().mapToInt(BlockPos::getZ).min().orElse(0) - 1;
        int maxZ = interior.stream().mapToInt(BlockPos::getZ).max().orElse(0) + 1;
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

    private static int horizontalDistance(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());
    }

    private record GroundChoice(int floorY, BlockPos entranceInterior) {
    }

    private record EntranceCandidate(BlockPos inside, int terrainDelta) {
    }

    record Result(Building.validationResult result,
                  BlockPos source,
                  BlockPos min,
                  BlockPos max,
                  List<StructureFloor> floors,
                  List<BuildingFloorRegion> volumeSlices,
                  int groundFloorId,
                  BlockPos groundSeed) {
        Result {
            floors = List.copyOf(floors);
            volumeSlices = List.copyOf(volumeSlices);
        }

        static Result failure(Building.validationResult result, BlockPos source) {
            return new Result(result, source, source, source, List.of(), List.of(), -1, source);
        }

        Structure toStructure(int id) {
            List<StructureFloor> assigned = new ArrayList<>();
            for (int i = 0; i < floors.size(); i++) {
                StructureFloor floor = floors.get(i);
                assigned.add(new StructureFloor(i, floor.anchorY(), floor.ceilingY(), floor.region()));
            }
            return new Structure(id, source, min, max, assigned, volumeSlices);
        }
    }
}
