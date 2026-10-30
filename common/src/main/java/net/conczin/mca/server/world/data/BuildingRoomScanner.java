package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Materializes Room geometry from one exact selected FloorGeometry. */
final class BuildingRoomScanner {
    private BuildingRoomScanner() {
    }

    /** Materializes every fresh topology component without assigning persistence identity. */
    static List<Result> partition(BlockGetter world,
                                  BlockPos source,
                                  int maxSize,
                                  SelectedFloorScanner.Result scan) {
        if (scan == null) return List.of();
        FloorGeometry floor = scan.floor();
        if (floor == null || floor.cells().isEmpty()) return List.of();
        List<RoomPartitioner.Component> components = components(world, scan);
        RoomPoiEvidence.Index evidence = RoomPoiEvidence.index(components);
        List<Result> results = new ArrayList<>(components.size());
        for (RoomPartitioner.Component component : components) {
            results.add(materialize(source, maxSize, evidence, component));
        }
        return List.copyOf(results);
    }

    /** One world-aware Room partition entry point so door ownership cannot drift between callers. */
    static List<RoomPartitioner.Component> components(BlockGetter world, SelectedFloorScanner.Result scan) {
        if (scan == null) return List.of();
        FloorGeometry floor = scan.floor();
        if (floor == null || floor.cells().isEmpty()) return List.of();
        return RoomPartitioner.partition(
                floor, scan.transitions(), StructureConnector.doorOwnerSides(world, floor), scan.verticalBoundaryCells());
    }

    static Result materialize(
            BlockPos source,
            int maxSize,
            List<RoomPartitioner.Component> components,
            RoomPartitioner.Component component) {
        int area = component.area();
        if (area > maxSize) return Result.failure(Building.validationResult.BLOCK_LIMIT, source);
        if (area < RoomPartitioner.MIN_ROOM_AREA) {
            return Result.failure(Building.validationResult.TOO_SMALL, source);
        }
        return materialize(source, maxSize, RoomPoiEvidence.index(components), component);
    }

    /** Reuses whole-partition POI ownership evidence when materializing a batch. */
    static Result materialize(
            BlockPos source,
            int maxSize,
            RoomPoiEvidence.Index evidence,
            RoomPartitioner.Component component) {
        Set<BlockPos> floorCells = component.floorCells();
        if (floorCells.size() > maxSize) return Result.failure(Building.validationResult.BLOCK_LIMIT, source);
        if (floorCells.size() < RoomPartitioner.MIN_ROOM_AREA) {
            return Result.failure(Building.validationResult.TOO_SMALL, source);
        }

        BlockPos seed = component.nearestCell(source);
        FloorGeometry.Bounds bounds = FloorGeometry.bounds(component.cells(), 0);
        return new Result(Building.validationResult.SUCCESS, seed, floorCells, evidence, component,
                bounds.min(), bounds.max());
    }

    static Result materializeSelected(
            BlockPos source,
            int maxSize,
            FloorGeometry floor,
            List<RoomPartitioner.Component> components) {
        RoomPartitioner.Component component = RoomPartitioner.select(source, floor, components);
        return component == null
                ? Result.failure(Building.validationResult.TOO_SMALL, source)
                : materialize(source, maxSize, components, component);
    }

    record Result(Building.validationResult status,
                  BlockPos seed,
                  Set<BlockPos> floorCells,
                  RoomPoiEvidence.Index evidence,
                  RoomPartitioner.Component component,
                  BlockPos min,
                  BlockPos max) {
        Result {
            floorCells = Set.copyOf(floorCells);
        }

        void visitPoiCells(Consumer<BlockPos> visitor) {
            if (evidence != null && component != null) evidence.forEachCandidate(component, visitor);
        }

        static Result failure(Building.validationResult status, BlockPos seed) {
            return new Result(status, seed, Set.of(), null, null, seed, seed);
        }
    }
}
