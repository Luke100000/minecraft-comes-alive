package net.conczin.mca.server.world.data;

import net.conczin.mca.resources.BuildingTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.*;

/** Selects one connected Room component from one canonical Structure Floor. */
final class BuildingRoomScanner {
    private static final int MIN_INTERIOR_AREA = 4;
    private static final int MAX_PARTITION_GAP = 4;
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private BuildingRoomScanner() {
    }

    static Result scan(Level world, BlockPos source, Set<BlockPos> blocked,
                       int maxSize, StructureFloor floor) {
        if (floor == null || floor.region() == null) {
            return Result.failure(Status.TOO_SMALL, source);
        }

        PartitionData partition = partitionData(world, floor);
        BuildingFloorRegion.Component selected = selectComponent(
                source, floor, partition.floorConnectors().keySet(), partition.components());
        return selected == null ? Result.failure(Status.TOO_SMALL, source)
                : materializeComponent(world, source, blocked, maxSize, floor, partition, selected);
    }

    /** Re-partitions the complete persisted Floor, retaining only components touching registered cells. */
    static List<Result> partitionRegistered(Level world, BlockPos source, int maxSize,
                                            StructureFloor floor, Set<BlockPos> registeredCells) {
        if (floor == null || floor.region() == null || registeredCells == null || registeredCells.isEmpty()) {
            return List.of();
        }
        PartitionData partition = partitionData(world, floor);
        Set<Long> registeredColumns = registeredCells.stream()
                .map(BuildingRoomScanner::columnKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<BuildingFloorRegion.Component> selected = partition.components().stream()
                .filter(component -> component.cells(floor.anchorY()).stream()
                        .map(BuildingRoomScanner::columnKey)
                        .anyMatch(registeredColumns::contains))
                .toList();
        return selected.stream()
                .map(component -> materializeComponent(world, source, Set.of(), maxSize,
                        floor, partition, component))
                .sorted(Comparator.comparingInt((Result result) -> result.min().getX())
                        .thenComparingInt(result -> result.min().getZ()))
                .toList();
    }

    private static long columnKey(BlockPos pos) {
        return ((long) pos.getX() << 32) ^ (pos.getZ() & 0xffffffffL);
    }

    private static PartitionData partitionData(Level world, StructureFloor floor) {
        Set<BlockPos> partitionCells = partitionCells(world, floor);
        Map<BlockPos, BlockPos> floorConnectors = floorConnectors(world, floor, partitionCells);
        List<BlockPos> passageCells = new ArrayList<>();
        List<BlockPos> functionalPoiCells = new ArrayList<>();
        for (BlockPos cell : partitionCells) {
            if (floorConnectors.containsKey(cell)) continue;
            PassageDecision decision = roomPassageDecision(world, floor,
                    new BlockPos(cell.getX(), floor.anchorY(), cell.getZ()));
            if (decision == PassageDecision.FUNCTIONAL_POI) {
                functionalPoiCells.add(cell);
            } else if (decision.passable()) {
                passageCells.add(cell);
            }
        }

        BuildingFloorRegion ordinary = BuildingFloorRegion.fromFootprint(floor.anchorY(), passageCells);
        List<BuildingFloorRegion.Component> components = ordinary.components();
        return new PartitionData(floorConnectors,
                attachFunctionalPoiCells(floor.anchorY(), functionalPoiCells, components),
                components);
    }

    private static Result materializeComponent(Level world, BlockPos source, Set<BlockPos> blocked,
                                               int maxSize, StructureFloor floor,
                                               PartitionData partition, BuildingFloorRegion.Component component) {
        Set<BlockPos> componentCells = component.cells(floor.anchorY());
        LinkedHashSet<BlockPos> footprint = new LinkedHashSet<>(componentCells);
        footprint.addAll(partition.functionalPoiCells().getOrDefault(component, Set.of()));
        for (BlockPos connectorCell : partition.floorConnectors().keySet()) {
            List<BuildingFloorRegion.Component> adjacent = adjacentComponents(connectorCell, partition.components());
            if (component.equals(connectorOwner(adjacent))) footprint.add(connectorCell);
        }

        if (footprint.size() > maxSize) return Result.failure(Status.BLOCK_LIMIT, source);
        Set<BlockPos> blockedCells = blocked == null ? Set.of() : blocked;
        if (footprint.stream().anyMatch(blockedCells::contains)) return Result.failure(Status.OVERLAP, source);
        if (footprint.size() < MIN_INTERIOR_AREA) return Result.failure(Status.TOO_SMALL, source);

        BlockPos seed = nearestCell(source, componentCells);
        Set<BlockPos> poi = collectPoiCells(world, footprint, floor.anchorY(), floor.ceilingY());
        int minX = footprint.stream().mapToInt(BlockPos::getX).min().orElse(source.getX());
        int minZ = footprint.stream().mapToInt(BlockPos::getZ).min().orElse(source.getZ());
        int maxX = footprint.stream().mapToInt(BlockPos::getX).max().orElse(source.getX());
        int maxZ = footprint.stream().mapToInt(BlockPos::getZ).max().orElse(source.getZ());
        return new Result(Status.SUCCESS, seed, floor.id(), floor.anchorY(), Set.copyOf(footprint), poi,
                new BlockPos(minX, floor.anchorY(), minZ),
                new BlockPos(maxX, Math.max(floor.anchorY(), floor.ceilingY() - 1), maxZ));
    }

    /** Re-partitions a persisted Floor and admits only short enclosed gaps inside its footprint. */
    private static Set<BlockPos> partitionCells(Level world, StructureFloor floor) {
        Set<BlockPos> canonical = floor.region().cells();
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>(canonical);
        List<BuildingFloorRegion.Component> components = floor.region().components();
        int minX = components.stream().mapToInt(BuildingFloorRegion.Component::minX).min().orElse(0);
        int minZ = components.stream().mapToInt(BuildingFloorRegion.Component::minZ).min().orElse(0);
        int maxX = components.stream().mapToInt(BuildingFloorRegion.Component::maxX).max().orElse(-1);
        int maxZ = components.stream().mapToInt(BuildingFloorRegion.Component::maxZ).max().orElse(-1);
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                BlockPos candidate = new BlockPos(x, floor.anchorY(), z);
                if (canonical.contains(candidate) || !bridgesCanonicalGap(candidate, canonical)) continue;
                if (connectorInColumn(world, floor, candidate) != null
                        || isRoomPassageColumn(world, floor, candidate)) {
                    result.add(candidate);
                }
            }
        }
        return Set.copyOf(result);
    }

    private static boolean bridgesCanonicalGap(BlockPos candidate, Set<BlockPos> canonical) {
        return boundedByCanonical(candidate, canonical, Direction.EAST, Direction.WEST)
                || boundedByCanonical(candidate, canonical, Direction.NORTH, Direction.SOUTH);
    }

    private static boolean boundedByCanonical(BlockPos candidate,
                                              Set<BlockPos> canonical,
                                              Direction first,
                                              Direction second) {
        return reachesCanonical(candidate, canonical, first)
                && reachesCanonical(candidate, canonical, second);
    }

    private static boolean reachesCanonical(BlockPos candidate,
                                            Set<BlockPos> canonical,
                                            Direction direction) {
        BlockPos cursor = candidate;
        for (int distance = 1; distance <= MAX_PARTITION_GAP; distance++) {
            cursor = cursor.relative(direction);
            if (canonical.contains(cursor)) return true;
        }
        return false;
    }

    /** Low furniture remains traversable for topology; functional POIs are attached afterwards. */
    private static boolean isRoomPassageColumn(Level world, StructureFloor floor, BlockPos floorCell) {
        BlockPos base = new BlockPos(floorCell.getX(), floor.anchorY(), floorCell.getZ());
        return roomPassageDecision(world, floor, base).passable();
    }

    static PassageDecision roomPassageDecision(Level world, StructureFloor floor, BlockPos base) {
        if (base.getY() + 1 >= floor.ceilingY()) return PassageDecision.BLOCKED_AT_CEILING;
        boolean feetPassable = StructureConnector.isPassageCell(world, base);
        boolean headPassable = StructureConnector.isPassageCell(world, base.above());
        if (feetPassable && headPassable) return PassageDecision.PASSAGE;
        if (headPassable) {
            var shape = world.getBlockState(base).getCollisionShape(world, base);
            if (shape.isEmpty() || shape.max(Direction.Axis.Y) <= 1.0D) {
                return PassageDecision.LOW_OBSTACLE;
            }
        }
        if (hasFunctionalPoiObstacle(world, base, floor.ceilingY())) return PassageDecision.FUNCTIONAL_POI;
        return feetPassable ? PassageDecision.BLOCKED_TWO_HIGH : PassageDecision.BLOCKED_COLLISION;
    }

    private static boolean hasFunctionalPoiObstacle(Level world, BlockPos base, int ceilingY) {
        for (int y = base.getY(); y < ceilingY; y++) {
            BlockPos pos = new BlockPos(base.getX(), y, base.getZ());
            if (StructureConnector.isPassageCell(world, pos)) break;
            var state = world.getBlockState(pos);
            for (var type : BuildingTypes.getInstance()) {
                if (!type.grouped() && type.matchesBlock(state)) return true;
            }
        }
        return false;
    }

    /**
     * Keeps solid furniture/POI columns in one adjacent Room without allowing those columns
     * to connect otherwise separate Room components.
     */
    private static Map<BuildingFloorRegion.Component, Set<BlockPos>> attachFunctionalPoiCells(
            int floorY,
            Collection<BlockPos> poiCells,
            List<BuildingFloorRegion.Component> roomComponents) {
        if (poiCells.isEmpty() || roomComponents.isEmpty()) return Map.of();

        BuildingFloorRegion poiRegion = BuildingFloorRegion.fromFootprint(floorY, poiCells);
        Map<BuildingFloorRegion.Component, LinkedHashSet<BlockPos>> attached = new LinkedHashMap<>();
        for (BuildingFloorRegion.Component poiComponent : poiRegion.components()) {
            Set<BlockPos> cells = poiComponent.cells(floorY);
            Set<BuildingFloorRegion.Component> adjacentRooms = new LinkedHashSet<>();
            for (BlockPos cell : cells) {
                adjacentRooms.addAll(adjacentComponents(cell, roomComponents));
            }
            BuildingFloorRegion.Component owner = connectorOwner(adjacentRooms);
            if (owner != null) {
                attached.computeIfAbsent(owner, ignored -> new LinkedHashSet<>()).addAll(cells);
            }
        }
        return attached.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> Set.copyOf(entry.getValue())));
    }

    private static BlockPos connectorInColumn(Level world, StructureFloor floor, BlockPos floorCell) {
        int minY = floor.anchorY() - BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE;
        for (int y = minY; y < floor.ceilingY(); y++) {
            BlockPos pos = new BlockPos(floorCell.getX(), y, floorCell.getZ());
            if (StructureConnector.isConnector(world.getBlockState(pos))) return pos;
        }
        return null;
    }

    private static Map<BlockPos, BlockPos> floorConnectors(Level world,
                                                            StructureFloor floor,
                                                            Collection<BlockPos> floorCells) {
        LinkedHashMap<BlockPos, BlockPos> result = new LinkedHashMap<>();
        for (BlockPos floorCell : floorCells) {
            BlockPos connector = connectorInColumn(world, floor, floorCell);
            if (connector != null) result.put(floorCell, connector);
        }
        return Map.copyOf(result);
    }

    private static BuildingFloorRegion.Component selectComponent(BlockPos source, StructureFloor floor,
                                                                  Set<BlockPos> connectorCells,
                                                                  List<BuildingFloorRegion.Component> components) {
        BuildingFloorRegion.Component direct = components.stream()
                .filter(component -> component.containsHorizontally(source.getX(), source.getZ()))
                .findFirst().orElse(null);
        if (direct != null) return direct;

        BlockPos floorCell = new BlockPos(source.getX(), floor.anchorY(), source.getZ());
        List<BuildingFloorRegion.Component> adjacent = adjacentComponents(floorCell, components);
        if (connectorCells.contains(floorCell) && adjacent.size() == 1) return adjacent.getFirst();
        return adjacent.stream().min(Comparator.comparingInt(BuildingFloorRegion.Component::minX)
                .thenComparingInt(BuildingFloorRegion.Component::minZ)).orElse(null);
    }

    private static List<BuildingFloorRegion.Component> adjacentComponents(
            BlockPos cell, List<BuildingFloorRegion.Component> components) {
        return components.stream().filter(component -> Arrays.stream(HORIZONTAL).anyMatch(direction ->
                component.containsHorizontally(cell.getX() + direction.getStepX(),
                        cell.getZ() + direction.getStepZ()))).toList();
    }

    /** Every connector Floor cell has at most one Room owner so Room footprints stay disjoint. */
    private static BuildingFloorRegion.Component connectorOwner(Collection<BuildingFloorRegion.Component> adjacent) {
        return adjacent.stream().min(Comparator.comparingInt(BuildingFloorRegion.Component::minX)
                .thenComparingInt(BuildingFloorRegion.Component::minZ)
                .thenComparingInt(BuildingFloorRegion.Component::maxX)
                .thenComparingInt(BuildingFloorRegion.Component::maxZ)).orElse(null);
    }

    private static BlockPos nearestCell(BlockPos source, Collection<BlockPos> cells) {
        return cells.stream().min(Comparator.comparingInt((BlockPos cell) ->
                        Math.abs(cell.getX() - source.getX()) + Math.abs(cell.getZ() - source.getZ()))
                .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ))
                .orElse(source);
    }

    private static Set<BlockPos> collectPoiCells(Level world, Set<BlockPos> footprint, int floorY, int ceilingY) {
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

    private record PartitionData(Map<BlockPos, BlockPos> floorConnectors,
                                 Map<BuildingFloorRegion.Component, Set<BlockPos>> functionalPoiCells,
                                 List<BuildingFloorRegion.Component> components) {
    }

    enum PassageDecision {
        PASSAGE, FUNCTIONAL_POI, LOW_OBSTACLE, BLOCKED_AT_CEILING, BLOCKED_TWO_HIGH, BLOCKED_COLLISION;

        boolean passable() {
            return this == PASSAGE || this == LOW_OBSTACLE;
        }
    }

    enum Status { SUCCESS, OVERLAP, BLOCK_LIMIT, SIZE_LIMIT, TOO_SMALL }

    record Result(Status status, BlockPos seed, int floorId, int floorY,
                  Set<BlockPos> footprintCells, Set<BlockPos> poiCells, BlockPos min, BlockPos max) {
        Result {
            footprintCells = Set.copyOf(footprintCells);
            poiCells = Set.copyOf(poiCells);
        }

        static Result failure(Status status, BlockPos seed) {
            return new Result(status, seed, -1, seed.getY(), Set.of(), Set.of(), seed, seed);
        }
    }
}
