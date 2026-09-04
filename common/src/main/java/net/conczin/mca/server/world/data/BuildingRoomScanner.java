package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Materializes Room geometry from one exact selected FloorGeometry. */
final class BuildingRoomScanner {
    private static final int MIN_INTERIOR_AREA = 4;

    private BuildingRoomScanner() {
    }

    static Result scan(Level world,
                       BlockPos source,
                       Set<BlockPos> blocked,
                       int maxSize,
                       int floorId,
                       ScannedFloor floor) {
        if (floor == null || floor.geometry().cells().isEmpty()) {
            return Result.failure(Building.validationResult.TOO_SMALL, source);
        }
        FloorGeometry geometry = floor.geometry();
        List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry);
        RoomPartitioner.Component selected = RoomPartitioner.select(source, geometry, components);
        return selected == null
                ? Result.failure(Building.validationResult.TOO_SMALL, source)
                : materializeComponent(world, source, blocked, maxSize,
                floorId, floor, components, selected);
    }

    /** Materializes every fresh topology component without assigning persistence identity. */
    static List<Result> partition(Level world,
                                  BlockPos source,
                                  int maxSize,
                                  int floorId,
                                  ScannedFloor floor) {
        if (floor == null || floor.geometry().cells().isEmpty()) return List.of();
        FloorGeometry geometry = floor.geometry();
        List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry);
        return components.stream()
                .map(component -> materializeComponent(
                        world, source, Set.of(), maxSize, floorId, floor, components, component))
                .sorted(Comparator.comparingInt((Result result) -> result.min().getX())
                        .thenComparingInt(result -> result.min().getZ()))
                .toList();
    }

    static Set<BlockPos> floorCellsForComponent(RoomPartitioner.Component selected) {
        return selected.floorCells();
    }

    static Set<BlockPos> footprintForComponent(RoomPartitioner.Component selected, int anchorY) {
        return selected.projection(anchorY).cells();
    }

    private static Result materializeComponent(
            Level world,
            BlockPos source,
            Set<BlockPos> blocked,
            int maxSize,
            int floorId,
            ScannedFloor floor,
            List<RoomPartitioner.Component> components,
            RoomPartitioner.Component component) {
        FloorGeometry geometry = floor.geometry();
        Set<BlockPos> floorCells = floorCellsForComponent(component);
        if (floorCells.size() > maxSize) return Result.failure(Building.validationResult.BLOCK_LIMIT, source);
        Set<BlockPos> blockedCells = blocked == null ? Set.of() : blocked;
        if (floorCells.stream().anyMatch(blockedCells::contains)) return Result.failure(Building.validationResult.OVERLAP, source);
        if (floorCells.size() < MIN_INTERIOR_AREA) return Result.failure(Building.validationResult.TOO_SMALL, source);

        BlockPos seed = nearestCell(source, component.cells());
        Set<BlockPos> poi = RoomPoiEvidence.candidates(geometry, components, component);
        int minX = floorCells.stream().mapToInt(BlockPos::getX).min().orElse(source.getX());
        int minY = floorCells.stream().mapToInt(BlockPos::getY).min().orElse(source.getY());
        int minZ = floorCells.stream().mapToInt(BlockPos::getZ).min().orElse(source.getZ());
        int maxX = floorCells.stream().mapToInt(BlockPos::getX).max().orElse(source.getX());
        int maxZ = floorCells.stream().mapToInt(BlockPos::getZ).max().orElse(source.getZ());
        int maxY = component.cells().stream().mapToInt(cell -> cell.ceilingY() - 1)
                .max().orElse(Math.max(floor.anchorY(), floor.semanticCeilingY() - 1));
        return new Result(Building.validationResult.SUCCESS, seed, floorId, floor.anchorY(), floorCells, poi,
                new BlockPos(minX, minY, minZ),
                new BlockPos(maxX, maxY, maxZ));
    }

    private static BlockPos nearestCell(BlockPos source, Collection<FloorGeometry.Cell> cells) {
        return cells.stream()
                .map(FloorGeometry.Cell::feet)
                .min(Comparator.comparingInt((BlockPos cell) ->
                                Math.abs(cell.getX() - source.getX())
                                        + Math.abs(cell.getY() - source.getY())
                                        + Math.abs(cell.getZ() - source.getZ()))
                        .thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getZ))
                .orElse(source);
    }

    record Result(Building.validationResult status,
                  BlockPos seed,
                  int floorId,
                  int floorY,
                  Set<BlockPos> floorCells,
                  Set<BlockPos> poiCells,
                  BlockPos min,
                  BlockPos max) {
        Result {
            floorCells = Set.copyOf(floorCells);
            poiCells = Set.copyOf(poiCells);
        }

        /** Transitional alias for callers moved to exact floorCells in Task 5. */
        Set<BlockPos> footprintCells() {
            return floorCells;
        }

        static Result failure(Building.validationResult status, BlockPos seed) {
            return new Result(status, seed, -1, seed.getY(), Set.of(), Set.of(), seed, seed);
        }
    }
}
