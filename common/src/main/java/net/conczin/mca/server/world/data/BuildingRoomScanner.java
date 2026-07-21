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

        Map<BlockPos, BlockPos> floorConnectors = floorConnectors(world, floor);
        BuildingFloorRegion ordinary = BuildingFloorRegion.fromFootprint(floor.anchorY(),
                floor.region().cells().stream().filter(cell -> !floorConnectors.containsKey(cell)).toList());
        BuildingFloorRegion.Component selected = selectComponent(source, floor, floorConnectors.keySet(), ordinary.components());
        if (selected == null) return Result.failure(Status.TOO_SMALL, source);

        LinkedHashSet<BlockPos> footprint = new LinkedHashSet<>(selected.cells(floor.anchorY()));
        for (BlockPos connectorCell : floorConnectors.keySet()) {
            List<BuildingFloorRegion.Component> adjacent = adjacentComponents(connectorCell, ordinary.components());
            if (adjacent.size() == 1 && adjacent.getFirst().equals(selected)) footprint.add(connectorCell);
        }

        if (footprint.stream().anyMatch(cell ->
                Math.abs(cell.getX() - source.getX()) + Math.abs(cell.getZ() - source.getZ()) >= maxRadius)) {
            return Result.failure(Status.SIZE_LIMIT, source);
        }
        if (footprint.size() > maxSize) return Result.failure(Status.BLOCK_LIMIT, source);
        Set<BlockPos> blockedCells = blocked == null ? Set.of() : blocked;
        if (footprint.stream().anyMatch(blockedCells::contains)) return Result.failure(Status.OVERLAP, source);
        if (footprint.size() < MIN_INTERIOR_AREA) return Result.failure(Status.TOO_SMALL, source);

        boolean hasEntrance = hasHorizontalEntrance(world, floorConnectors, selected)
                || hasVerticalEntrance(world, structure, floor, floorConnectors, selected);
        BlockPos seed = nearestCell(source, selected.cells(floor.anchorY()));
        Set<BlockPos> poi = collectPoiCells(world, footprint, floor.anchorY(), floor.ceilingY());
        int minX = footprint.stream().mapToInt(BlockPos::getX).min().orElse(source.getX());
        int minZ = footprint.stream().mapToInt(BlockPos::getZ).min().orElse(source.getZ());
        int maxX = footprint.stream().mapToInt(BlockPos::getX).max().orElse(source.getX());
        int maxZ = footprint.stream().mapToInt(BlockPos::getZ).max().orElse(source.getZ());
        return new Result(Status.SUCCESS, seed, floor.id(), floor.anchorY(), Set.copyOf(footprint), poi,
                hasEntrance, new BlockPos(minX, floor.anchorY(), minZ),
                new BlockPos(maxX, Math.max(floor.anchorY(), floor.ceilingY() - 1), maxZ));
    }

    private static Map<BlockPos, BlockPos> floorConnectors(Level world, StructureFloor floor) {
        LinkedHashMap<BlockPos, BlockPos> result = new LinkedHashMap<>();
        int minY = floor.anchorY() - BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE;
        for (BlockPos floorCell : floor.region().cells()) {
            for (int y = minY; y < floor.ceilingY(); y++) {
                BlockPos pos = new BlockPos(floorCell.getX(), y, floorCell.getZ());
                if (StructureConnector.isConnector(world.getBlockState(pos))) {
                    result.put(floorCell, pos);
                    break;
                }
            }
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
