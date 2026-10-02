package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Set;

/** Materializes Room geometry from one exact selected FloorGeometry. */
final class BuildingRoomScanner {
    private BuildingRoomScanner() {
    }

    /** Materializes every fresh topology component without assigning persistence identity. */
    static List<Result> partition(Level world,
                                  BlockPos source,
                                  int maxSize,
                                  int floorId,
                                  SelectedFloorScanner.Result scan) {
        if (scan == null) return List.of();
        FloorGeometry floor = scan.floor();
        if (floor == null || floor.cells().isEmpty()) return List.of();
        List<RoomPartitioner.Component> components = components(world, scan);
        return components.stream()
                .map(component -> materialize(
                        source, maxSize, floorId, floor, components, component))
                .toList();
    }

    /** One world-aware Room partition entry point so door ownership cannot drift between callers. */
    static List<RoomPartitioner.Component> components(Level world, SelectedFloorScanner.Result scan) {
        if (scan == null) return List.of();
        FloorGeometry floor = scan.floor();
        if (floor == null || floor.cells().isEmpty()) return List.of();
        return RoomPartitioner.partition(
                floor, scan.transitions(), StructureConnector.doorOwnerSides(world, floor), scan.verticalBoundaryCells());
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
        if (floorCells.size() < RoomPartitioner.MIN_ROOM_AREA) {
            return Result.failure(Building.validationResult.TOO_SMALL, source);
        }

        BlockPos seed = component.nearestCell(source);
        Set<BlockPos> poi = RoomPoiEvidence.candidates(components, component);
        FloorGeometry.Bounds bounds = FloorGeometry.bounds(component.cells(), 0);
        return new Result(Building.validationResult.SUCCESS, seed, floorId, floor.anchorY(), floorCells, poi,
                bounds.min(), bounds.max());
    }

    static Result materializeSelected(
            BlockPos source,
            int maxSize,
            int floorId,
            FloorGeometry floor,
            List<RoomPartitioner.Component> components) {
        RoomPartitioner.Component component = RoomPartitioner.select(source, floor, components);
        return component == null
                ? Result.failure(Building.validationResult.TOO_SMALL, source)
                : materialize(source, maxSize, floorId, floor, components, component);
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
