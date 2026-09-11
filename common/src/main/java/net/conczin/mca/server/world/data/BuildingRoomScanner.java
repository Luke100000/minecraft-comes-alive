package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Materializes Room geometry from one exact selected FloorGeometry. */
final class BuildingRoomScanner {
    private static final int MIN_INTERIOR_AREA = 4;

    private BuildingRoomScanner() {
    }

    static Result scan(Level world,
                       BlockPos source,
                       int maxSize,
                       int floorId,
                       FloorGeometry floor,
                       Collection<SelectedFloorScanner.Transition> transitions) {
        if (floor == null || floor.cells().isEmpty()) {
            return Result.failure(Building.validationResult.TOO_SMALL, source);
        }
        List<RoomPartitioner.Component> components = components(world, floor, transitions);
        RoomPartitioner.Component selected = RoomPartitioner.select(source, floor, components);
        return selected == null
                ? Result.failure(Building.validationResult.TOO_SMALL, source)
                : materialize(source, maxSize,
                floorId, floor, components, selected);
    }

    /** Materializes every fresh topology component without assigning persistence identity. */
    static List<Result> partition(Level world,
                                  BlockPos source,
                                  int maxSize,
                                  int floorId,
                                  FloorGeometry floor,
                                  Collection<SelectedFloorScanner.Transition> transitions) {
        if (floor == null || floor.cells().isEmpty()) return List.of();
        List<RoomPartitioner.Component> components = components(world, floor, transitions);
        return components.stream()
                .map(component -> materialize(
                        source, maxSize, floorId, floor, components, component))
                .toList();
    }

    /** One world-aware Room partition entry point so door ownership cannot drift between callers. */
    static List<RoomPartitioner.Component> components(
            Level world,
            FloorGeometry floor,
            Collection<SelectedFloorScanner.Transition> transitions) {
        if (floor == null || floor.cells().isEmpty()) return List.of();
        return RoomPartitioner.partition(
                floor, transitions, StructureConnector.doorOwnerSides(world, floor));
    }

    static Result materialize(
            BlockPos source,
            int maxSize,
            int floorId,
            FloorGeometry floor,
            List<RoomPartitioner.Component> components,
            RoomPartitioner.Component component) {
        Set<BlockPos> floorCells = component.floorCells();
        if (floorCells.size() > maxSize) return Result.failure(Building.validationResult.BLOCK_LIMIT, source);
        if (floorCells.size() < MIN_INTERIOR_AREA) return Result.failure(Building.validationResult.TOO_SMALL, source);

        BlockPos seed = component.nearestCell(source);
        Set<BlockPos> poi = RoomPoiEvidence.candidates(components, component);
        int minX = floorCells.stream().mapToInt(BlockPos::getX).min().orElse(source.getX());
        int minY = floorCells.stream().mapToInt(BlockPos::getY).min().orElse(source.getY());
        int minZ = floorCells.stream().mapToInt(BlockPos::getZ).min().orElse(source.getZ());
        int maxX = floorCells.stream().mapToInt(BlockPos::getX).max().orElse(source.getX());
        int maxZ = floorCells.stream().mapToInt(BlockPos::getZ).max().orElse(source.getZ());
        int maxY = component.cells().stream().mapToInt(cell -> cell.ceilingY() - 1)
                .max().orElse(floor.maxPhysicalCeilingY() - 1);
        return new Result(Building.validationResult.SUCCESS, seed, floorId, floor.anchorY(), floorCells, poi,
                new BlockPos(minX, minY, minZ),
                new BlockPos(maxX, maxY, maxZ));
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

        static Result failure(Building.validationResult status, BlockPos seed) {
            return new Result(status, seed, -1, seed.getY(), Set.of(), Set.of(), seed, seed);
        }
    }
}
