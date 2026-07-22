package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.*;

/** Selects one connected Room component from one canonical Structure Floor. */
final class BuildingRoomScanner {
    private static final int MIN_INTERIOR_AREA = 4;
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private BuildingRoomScanner() {
    }

    static Result scan(Level world, BlockPos source, Set<BlockPos> blocked,
                       int maxSize, int maxRadius, Structure structure) {
        StructureFloor floor = structure == null ? null : structure.resolveFloor(source.getY()).orElse(null);
        return floor == null ? Result.failure(Status.TOO_SMALL, source)
                : scan(world, source, blocked, maxSize, maxRadius, structure, floor);
    }

    static Result scan(Level world, BlockPos source, Set<BlockPos> blocked,
                       int maxSize, int maxRadius, Structure structure, StructureFloor floor) {
        if (structure == null || floor == null || floor.region() == null) {
            return Result.failure(Status.TOO_SMALL, source);
        }

        PartitionData partition = partitionData(world, floor);
        BuildingFloorRegion.Component selected = selectComponent(
                source, floor, partition.floorConnectors().keySet(), partition.components());
        return selected == null ? Result.failure(Status.TOO_SMALL, source)
                : materializeComponent(world, source, blocked, maxSize, maxRadius, structure, floor, partition, selected);
    }

    /** Returns every valid current Room component on one persisted physical Floor using one partition pass. */
    static FloorPartition partition(Level world, BlockPos source, int maxSize, int maxRadius,
                                    Structure structure, StructureFloor floor) {
        if (structure == null || floor == null || floor.region() == null) return new FloorPartition(List.of());
        PartitionData partition = partitionData(world, floor);
        List<Result> components = partition.components().stream()
                .map(component -> materializeComponent(world, source, Set.of(), maxSize, maxRadius,
                        structure, floor, partition, component))
                .filter(result -> result.status() == Status.SUCCESS)
                .sorted(Comparator.comparingInt((Result result) -> result.min().getX())
                        .thenComparingInt(result -> result.min().getZ()))
                .toList();
        return new FloorPartition(components);
    }

    private static PartitionData partitionData(Level world, StructureFloor floor) {
        Set<BlockPos> partitionCells = partitionCells(world, floor);
        Map<BlockPos, BlockPos> floorConnectors = floorConnectors(world, floor, partitionCells);
        BuildingFloorRegion ordinary = BuildingFloorRegion.fromFootprint(floor.anchorY(),
                partitionCells.stream()
                        .filter(cell -> !floorConnectors.containsKey(cell))
                        .filter(cell -> isRoomPassageColumn(world, floor, cell))
                        .toList());
        return new PartitionData(floorConnectors, ordinary.components());
    }

    private static Result materializeComponent(Level world, BlockPos source, Set<BlockPos> blocked,
                                               int maxSize, int maxRadius, Structure structure, StructureFloor floor,
                                               PartitionData partition, BuildingFloorRegion.Component component) {
        LinkedHashSet<BlockPos> footprint = new LinkedHashSet<>(component.cells(floor.anchorY()));
        for (BlockPos connectorCell : partition.floorConnectors().keySet()) {
            List<BuildingFloorRegion.Component> adjacent = adjacentComponents(connectorCell, partition.components());
            if (component.equals(connectorOwner(adjacent))) footprint.add(connectorCell);
        }

        if (footprint.stream().anyMatch(cell ->
                Math.abs(cell.getX() - source.getX()) + Math.abs(cell.getZ() - source.getZ()) >= maxRadius)) {
            return Result.failure(Status.SIZE_LIMIT, source);
        }
        if (footprint.size() > maxSize) return Result.failure(Status.BLOCK_LIMIT, source);
        Set<BlockPos> blockedCells = blocked == null ? Set.of() : blocked;
        if (footprint.stream().anyMatch(blockedCells::contains)) return Result.failure(Status.OVERLAP, source);
        if (footprint.size() < MIN_INTERIOR_AREA) return Result.failure(Status.TOO_SMALL, source);

        boolean hasEntrance = hasHorizontalEntrance(world, partition.floorConnectors(), component)
                || hasVerticalEntrance(world, structure, floor, partition.floorConnectors(), component);
        BlockPos seed = nearestCell(source, component.cells(floor.anchorY()));
        Set<BlockPos> poi = collectPoiCells(world, footprint, floor.anchorY(), floor.ceilingY());
        int minX = footprint.stream().mapToInt(BlockPos::getX).min().orElse(source.getX());
        int minZ = footprint.stream().mapToInt(BlockPos::getZ).min().orElse(source.getZ());
        int maxX = footprint.stream().mapToInt(BlockPos::getX).max().orElse(source.getX());
        int maxZ = footprint.stream().mapToInt(BlockPos::getZ).max().orElse(source.getZ());
        return new Result(Status.SUCCESS, seed, floor.id(), floor.anchorY(), Set.copyOf(footprint), poi,
                hasEntrance, new BlockPos(minX, floor.anchorY(), minZ),
                new BlockPos(maxX, Math.max(floor.anchorY(), floor.ceilingY() - 1), maxZ));
    }

    /** Re-partitions a persisted physical Floor from current walls/doors without enlarging the Structure. */
    private static Set<BlockPos> partitionCells(Level world, StructureFloor floor) {
        Set<BlockPos> canonical = floor.region().cells();
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>(canonical);
        for (BlockPos cell : canonical) {
            for (Direction direction : HORIZONTAL) {
                BlockPos candidate = cell.relative(direction);
                if (canonical.contains(candidate)) continue;
                boolean bridge = canonical.contains(candidate.relative(Direction.NORTH))
                        && canonical.contains(candidate.relative(Direction.SOUTH))
                        || canonical.contains(candidate.relative(Direction.EAST))
                        && canonical.contains(candidate.relative(Direction.WEST));
                if (bridge && (connectorInColumn(world, floor, candidate) != null
                        || isRoomPassageColumn(world, floor, candidate))) {
                    result.add(candidate);
                }
            }
        }
        return Set.copyOf(result);
    }

    /** Low furniture stays inside a Room; a two-block solid wall remains a partition boundary. */
    private static boolean isRoomPassageColumn(Level world, StructureFloor floor, BlockPos floorCell) {
        BlockPos base = new BlockPos(floorCell.getX(), floor.anchorY(), floorCell.getZ());
        if (StructureConnector.isPassageCell(world, base)) return true;
        if (base.getY() + 1 >= floor.ceilingY()
                || !StructureConnector.isPassageCell(world, base.above())) return false;
        var shape = world.getBlockState(base).getCollisionShape(world, base);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 1.0D;
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
    private static BuildingFloorRegion.Component connectorOwner(List<BuildingFloorRegion.Component> adjacent) {
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

    private static boolean hasHorizontalEntrance(Level world, Map<BlockPos, BlockPos> floorConnectors,
                                                 BuildingFloorRegion.Component selected) {
        for (Map.Entry<BlockPos, BlockPos> entry : floorConnectors.entrySet()) {
            Direction facing = StructureConnector.horizontalFacing(world.getBlockState(entry.getValue())).orElse(null);
            if (facing == null) continue;
            BlockPos cell = entry.getKey();
            boolean first = selected.containsHorizontally(
                    cell.getX() + facing.getStepX(), cell.getZ() + facing.getStepZ());
            boolean second = selected.containsHorizontally(
                    cell.getX() - facing.getStepX(), cell.getZ() - facing.getStepZ());
            if (first != second) return true;
        }
        return false;
    }

    private static boolean hasVerticalEntrance(Level world, Structure structure, StructureFloor floor,
                                               Map<BlockPos, BlockPos> floorConnectors,
                                               BuildingFloorRegion.Component selected) {
        for (Map.Entry<BlockPos, BlockPos> entry : floorConnectors.entrySet()) {
            if (!StructureConnector.isVertical(world.getBlockState(entry.getValue()))) continue;
            if (adjacentComponents(entry.getKey(), List.of(selected)).isEmpty()) continue;
            if (StructureConnector.connectsDifferentFloor(world, structure, floor, entry.getValue())) return true;
        }
        return false;
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

    record FloorPartition(List<Result> components) {
        FloorPartition {
            components = List.copyOf(components);
        }
    }

    private record PartitionData(Map<BlockPos, BlockPos> floorConnectors,
                                 List<BuildingFloorRegion.Component> components) {
    }

    enum Status { SUCCESS, OVERLAP, BLOCK_LIMIT, SIZE_LIMIT, TOO_SMALL }

    record Result(Status status, BlockPos seed, int floorId, int floorY,
                  Set<BlockPos> footprintCells, Set<BlockPos> poiCells,
                  boolean hasEntrance, BlockPos min, BlockPos max) {
        Result {
            footprintCells = Set.copyOf(footprintCells);
            poiCells = Set.copyOf(poiCells);
        }

        static Result failure(Status status, BlockPos seed) {
            return new Result(status, seed, -1, seed.getY(), Set.of(), Set.of(), false, seed, seed);
        }
    }
}
