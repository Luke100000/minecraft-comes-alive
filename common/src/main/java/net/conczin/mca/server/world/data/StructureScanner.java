package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Orchestrates one selected physical Floor scan. It never discovers other storeys implicitly. */
final class StructureScanner {
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private StructureScanner() {
    }

    static Result scanNewStructure(Level world,
                                   BlockPos source,
                                   Collection<Structure> existing) {
        Result exact = scanAtSeed(world, source, source, existing, -1);
        if (exact.result() == Building.validationResult.SUCCESS) return exact;

        StructureConnector.FloorHandoff handoff = StructureConnector.resolveVerticalFloorHandoff(
                world, source, Config.getInstance()).orElse(null);
        return handoff == null
                ? exact
                : scanAtSeed(world, source, handoff.seed(), existing, -1);
    }

    static Result scanReportedStructure(Level world,
                                        BlockPos source,
                                        Collection<Structure> existing) {
        Result exact = scanAtSeed(world, source, source, existing, -1);
        if (exact.result() == Building.validationResult.SUCCESS) return exact;

        for (Direction direction : HORIZONTAL) {
            BlockPos candidate = source.relative(direction);
            Result adjacent = scanAtSeed(world, source, candidate, existing, -1);
            if (adjacent.result() == Building.validationResult.SUCCESS) return adjacent;
        }
        return Result.failure(exact.result(), source);
    }

    static Result scanPlannedStructure(Level world,
                                       RoomScanPlan plan,
                                       Collection<Structure> existing) {
        return scanAtSeed(world, plan.interactionSource(), plan.scanSeed(), existing, -1);
    }

    static Result scanExistingFloor(Level world,
                                    Structure structure,
                                    StructureFloor floor,
                                    BlockPos source,
                                    Collection<Structure> existing) {
        if (structure == null || floor == null || floor.region() == null) {
            return Result.failure(Building.validationResult.NOT_IN_BUILDING, source);
        }

        BlockPos seed = resolveExistingSeed(world, floor, source).orElse(null);
        if (seed == null) return Result.failure(Building.validationResult.NOT_IN_BUILDING, source);
        return scanAtSeed(world, source, seed, existing, structure.getId());
    }

    static Optional<AttachmentSeed> resolveAttachmentSeed(Level world, BlockPos source) {
        Config config = Config.getInstance();
        SelectedFloorScanner.Result exact = SelectedFloorScanner.scan(
                world, source, config.maxBuildingSize, config.maxBuildingRadius);
        if (exact.result() == Building.validationResult.SUCCESS && exact.surface() != null) {
            return Optional.of(new AttachmentSeed(source, exact.surface()));
        }

        Optional<StructureConnector.FloorHandoff> vertical =
                StructureConnector.resolveVerticalFloorHandoff(world, source, config);
        if (vertical.isPresent()) {
            return Optional.of(new AttachmentSeed(vertical.get().seed(), vertical.get().surface()));
        }

        List<AttachmentSeed> candidates = new ArrayList<>();
        for (Direction direction : HORIZONTAL) {
            BlockPos connector = source.relative(direction);
            BlockState state = world.getBlockState(connector);
            if (!StructureConnector.isHorizontalBoundary(state)) continue;
            BlockPos candidate = StructureConnector.normalize(connector, state).relative(direction);
            SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(
                    world, candidate, config.maxBuildingSize, config.maxBuildingRadius);
            if (scan.result() == Building.validationResult.SUCCESS && scan.surface() != null) {
                candidates.add(new AttachmentSeed(candidate, scan.surface()));
            }
        }
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    static boolean isWalkableAnchor(Level world, BlockPos pos) {
        return SelectedFloorScanner.inspectSurfaceCell(
                world, pos, new FloorCeilingResolver(world)).isPresent();
    }

    static StructureFloor persistedFloor(FloorSurface surface) {
        return new StructureFloor(0, surface.anchorY(), surface.maxCeilingY(), surface.persistedRegion());
    }

    private static Result scanAtSeed(Level world,
                                     BlockPos interactionSource,
                                     BlockPos scanSeed,
                                     Collection<Structure> existing,
                                     int ignoredStructureId) {
        Config config = Config.getInstance();
        SelectedFloorScanner.Result selected = SelectedFloorScanner.scan(
                world, scanSeed, config.maxBuildingSize, config.maxBuildingRadius);
        if (selected.result() != Building.validationResult.SUCCESS || selected.surface() == null) {
            return Result.failure(selected.result(), interactionSource);
        }

        FloorSurface surface = selected.surface();
        StructureFloor floor = persistedFloor(surface);
        Structure candidate = new Structure(
                ignoredStructureId, scanSeed.immutable(), selected.min(), selected.max(), List.of(floor));
        for (Structure other : existing) {
            if (other.getId() != ignoredStructureId && candidate.intersects(other)) {
                return Result.failure(Building.validationResult.OVERLAP, interactionSource);
            }
        }
        return new Result(Building.validationResult.SUCCESS, scanSeed.immutable(),
                selected.min(), selected.max(), floor, surface);
    }

    private static Optional<BlockPos> resolveExistingSeed(
            Level world, StructureFloor floor, BlockPos source) {
        if (isWalkableAnchor(world, source)
                && floor.contains(source.getX(), source.getZ())) {
            return Optional.of(source.immutable());
        }

        return floor.region().cells().stream()
                .sorted(Comparator
                        .comparingInt((BlockPos candidate) -> manhattanDistance(candidate, source))
                        .thenComparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getZ))
                .filter(candidate -> isWalkableAnchor(world, candidate))
                .map(BlockPos::immutable)
                .findFirst();
    }

    private static int manhattanDistance(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX())
                + Math.abs(first.getY() - second.getY())
                + Math.abs(first.getZ() - second.getZ());
    }

    record AttachmentSeed(BlockPos seed, FloorSurface surface) {
        AttachmentSeed {
            seed = seed.immutable();
        }
    }

    record Result(Building.validationResult result,
                  BlockPos source,
                  BlockPos min,
                  BlockPos max,
                  StructureFloor floor,
                  FloorSurface surface) {
        static Result failure(Building.validationResult result, BlockPos source) {
            return new Result(result, source, source, source, null,
                    new FloorSurface(Set.of(), Map.of()));
        }

        Structure toStructure(int id) {
            if (floor == null) throw new IllegalStateException("Cannot materialize a failed Structure scan");
            StructureFloor assigned = new StructureFloor(
                    0, floor.anchorY(), floor.ceilingY(), floor.region());
            return new Structure(id, source, min, max, List.of(assigned));
        }
    }
}
