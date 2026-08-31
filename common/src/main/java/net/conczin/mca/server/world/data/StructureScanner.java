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

        BlockPos connectorSeed = resolveVerticalConnectorFloorSeed(
                world, source, Config.getInstance()).orElse(null);
        return connectorSeed == null
                ? exact
                : scanAtSeed(world, source, connectorSeed, existing, -1);
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
        if (SelectedFloorScanner.scan(world, source, config.maxBuildingSize, config.maxBuildingRadius)
                .result() == Building.validationResult.SUCCESS) {
            return Optional.of(new AttachmentSeed(source, false));
        }

        Optional<BlockPos> vertical = resolveVerticalConnectorFloorSeed(world, source, config);
        if (vertical.isPresent()) return Optional.of(new AttachmentSeed(vertical.get(), false));

        List<AttachmentSeed> candidates = new ArrayList<>();
        for (Direction direction : HORIZONTAL) {
            BlockPos connector = source.relative(direction);
            BlockState state = world.getBlockState(connector);
            if (!StructureConnector.isHorizontalBoundary(state)) continue;
            BlockPos candidate = StructureConnector.normalize(connector, state).relative(direction);
            SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(
                    world, candidate, config.maxBuildingSize, config.maxBuildingRadius);
            if (scan.result() == Building.validationResult.SUCCESS) {
                candidates.add(new AttachmentSeed(candidate, true));
            }
        }
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    private static Optional<BlockPos> resolveVerticalConnectorFloorSeed(
            Level world, BlockPos source, Config config) {
        if (!StructureConnector.isVertical(world.getBlockState(source))) return Optional.empty();

        BlockPos selectedSeed = null;
        Set<BlockPos> selectedFloor = null;
        for (Direction direction : HORIZONTAL) {
            BlockPos candidate = source.relative(direction);
            SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(
                    world, candidate, config.maxBuildingSize, config.maxBuildingRadius);
            if (scan.result() != Building.validationResult.SUCCESS || scan.surface() == null) continue;

            Set<BlockPos> candidateFloor = scan.surface().projectedCells();
            if (selectedSeed == null) {
                selectedSeed = candidate.immutable();
                selectedFloor = candidateFloor;
            } else if (!selectedFloor.equals(candidateFloor)) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(selectedSeed);
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
        List<StructureFloor> floors = List.of(floor);
        Structure candidate = new Structure(
                ignoredStructureId, scanSeed.immutable(), selected.min(), selected.max(), floors);
        for (Structure other : existing) {
            if (other.getId() != ignoredStructureId && candidate.intersects(other)) {
                return Result.failure(Building.validationResult.OVERLAP, interactionSource);
            }
        }
        return new Result(Building.validationResult.SUCCESS, scanSeed.immutable(),
                selected.min(), selected.max(), floors, surface);
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

    record AttachmentSeed(BlockPos seed, boolean crossedHorizontalConnector) {
        AttachmentSeed {
            seed = seed.immutable();
        }
    }

    record Result(Building.validationResult result,
                  BlockPos source,
                  BlockPos min,
                  BlockPos max,
                  List<StructureFloor> floors,
                  FloorSurface surface) {
        Result {
            floors = List.copyOf(floors);
        }

        static Result failure(Building.validationResult result, BlockPos source) {
            return new Result(result, source, source, source, List.of(),
                    new FloorSurface(Set.of(), Map.of()));
        }

        Structure toStructure(int id) {
            List<StructureFloor> assigned = new ArrayList<>();
            for (int i = 0; i < floors.size(); i++) {
                StructureFloor floor = floors.get(i);
                assigned.add(new StructureFloor(i, floor.anchorY(), floor.ceilingY(), floor.region()));
            }
            return new Structure(id, source, min, max, assigned);
        }
    }
}
