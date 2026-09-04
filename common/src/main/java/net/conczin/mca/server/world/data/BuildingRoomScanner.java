package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Materializes Room geometry from one exact selected FloorSurface. */
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
        if (floor == null || floor.surface().cells().isEmpty()) {
            return Result.failure(Building.validationResult.TOO_SMALL, source);
        }
        FloorSurface surface = floor.surface();
        List<FloorSurfacePartitioner.Component> components = FloorSurfacePartitioner.partition(surface);
        FloorSurfacePartitioner.Component selected = FloorSurfacePartitioner.select(source, surface, components);
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
        if (floor == null || floor.surface().cells().isEmpty()) return List.of();
        FloorSurface surface = floor.surface();
        List<FloorSurfacePartitioner.Component> components = FloorSurfacePartitioner.partition(surface);
        return components.stream()
                .map(component -> materializeComponent(
                        world, source, Set.of(), maxSize, floorId, floor, components, component))
                .sorted(Comparator.comparingInt((Result result) -> result.min().getX())
                        .thenComparingInt(result -> result.min().getZ()))
                .toList();
    }

    static Set<BlockPos> footprintForComponent(FloorSurfacePartitioner.Component selected, int anchorY) {
        return selected.projectedCells(anchorY);
    }

    private static Result materializeComponent(
            Level world,
            BlockPos source,
            Set<BlockPos> blocked,
            int maxSize,
            int floorId,
            ScannedFloor floor,
            List<FloorSurfacePartitioner.Component> components,
            FloorSurfacePartitioner.Component component) {
        FloorSurface surface = floor.surface();
        Set<BlockPos> footprint = footprintForComponent(component, floor.anchorY());
        if (footprint.size() > maxSize) return Result.failure(Building.validationResult.BLOCK_LIMIT, source);
        Set<BlockPos> blockedCells = blocked == null ? Set.of() : blocked;
        if (footprint.stream().anyMatch(blockedCells::contains)) return Result.failure(Building.validationResult.OVERLAP, source);
        if (footprint.size() < MIN_INTERIOR_AREA) return Result.failure(Building.validationResult.TOO_SMALL, source);

        BlockPos seed = nearestCell(source, component.cells());
        Set<BlockPos> poi = RoomPoiEvidence.candidates(surface, components, component);
        int minX = footprint.stream().mapToInt(BlockPos::getX).min().orElse(source.getX());
        int minZ = footprint.stream().mapToInt(BlockPos::getZ).min().orElse(source.getZ());
        int maxX = footprint.stream().mapToInt(BlockPos::getX).max().orElse(source.getX());
        int maxZ = footprint.stream().mapToInt(BlockPos::getZ).max().orElse(source.getZ());
        int maxY = component.cells().stream().mapToInt(cell -> cell.ceilingY() - 1)
                .max().orElse(Math.max(floor.anchorY(), floor.semanticCeilingY() - 1));
        return new Result(Building.validationResult.SUCCESS, seed, floorId, floor.anchorY(), footprint, poi,
                new BlockPos(minX, floor.anchorY(), minZ),
                new BlockPos(maxX, maxY, maxZ));
    }

    private static BlockPos nearestCell(BlockPos source, Collection<FloorSurface.Cell> cells) {
        return cells.stream()
                .map(FloorSurface.Cell::feet)
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
                  Set<BlockPos> footprintCells,
                  Set<BlockPos> poiCells,
                  BlockPos min,
                  BlockPos max) {
        Result {
            footprintCells = Set.copyOf(footprintCells);
            poiCells = Set.copyOf(poiCells);
        }

        static Result failure(Building.validationResult status, BlockPos seed) {
            return new Result(status, seed, -1, seed.getY(), Set.of(), Set.of(), seed, seed);
        }
    }
}
