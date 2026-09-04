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
import java.util.Optional;

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
        Result exact = scanAtSeed(world, source, source, existing, -1, -1);
        if (exact.result() == Building.validationResult.SUCCESS) return exact;

        StructureConnector.FloorHandoff handoff = StructureConnector.resolveVerticalFloorHandoff(
                world, source, Config.getInstance()).orElse(null);
        if (handoff != null) {
            return scanAtSeed(world, source, handoff.seed(), existing, -1, -1);
        }

        handoff = StructureConnector.resolveHorizontalFloorHandoff(
                world, source, Config.getInstance()).orElse(null);
        if (handoff != null) {
            return scanAtSeed(world, source, handoff.seed(), existing, -1, -1);
        }

        BlockPos standingSeed = resolveStandingSurfaceSeed(world, source).orElse(null);
        return standingSeed == null || standingSeed.equals(source)
                ? exact
                : scanAtSeed(world, source, standingSeed, existing, -1, -1);
    }

    static Result scanReportedStructure(Level world,
                                        BlockPos source,
                                        Collection<Structure> existing) {
        Result exact = scanAtSeed(world, source, source, existing, -1, -1);
        if (exact.result() == Building.validationResult.SUCCESS) return exact;

        for (Direction direction : HORIZONTAL) {
            BlockPos candidate = source.relative(direction);
            Result adjacent = scanAtSeed(world, source, candidate, existing, -1, -1);
            if (adjacent.result() == Building.validationResult.SUCCESS) return adjacent;
        }
        return Result.failure(exact.result(), source);
    }

    static Result scanPlannedStructure(Level world,
                                       RoomScanPlan plan,
                                       Collection<Structure> existing) {
        return scanAtSeed(world, plan.interactionSource(), plan.scanSeed(), existing, -1,
                plan.targetBuildingId());
    }

    static Result scanExistingFloor(Level world,
                                    Structure structure,
                                    StructureFloor floor,
                                    BlockPos source,
                                    Collection<Structure> existing) {
        if (structure == null || floor == null) {
            return Result.failure(Building.validationResult.NOT_IN_BUILDING, source);
        }

        BlockPos seed = resolveExistingSeed(world, floor, source).orElse(null);
        if (seed == null) return Result.failure(Building.validationResult.NOT_IN_BUILDING, source);
        return scanAtSeed(world, source, seed, existing, structure.getId(), -1);
    }

    static Optional<AttachmentSeed> resolveAttachmentSeed(Level world, BlockPos source) {
        Config config = Config.getInstance();
        SelectedFloorScanner.Result exact = SelectedFloorScanner.scan(
                world, source, config.maxBuildingSize, config.maxBuildingRadius);
        if (exact.result() == Building.validationResult.SUCCESS && exact.floor() != null) {
            return Optional.of(new AttachmentSeed(source, exact.floor(), exact.connectedFloors()));
        }

        Optional<StructureConnector.FloorHandoff> vertical =
                StructureConnector.resolveVerticalFloorHandoff(world, source, config);
        if (vertical.isPresent()) {
            return Optional.of(new AttachmentSeed(vertical.get().seed(), vertical.get().floor(),
                    vertical.get().connectedFloors()));
        }

        BlockPos standingSeed = resolveStandingSurfaceSeed(world, source).orElse(null);
        if (standingSeed != null && !standingSeed.equals(source)) {
            SelectedFloorScanner.Result standing = SelectedFloorScanner.scan(
                    world, standingSeed, config.maxBuildingSize, config.maxBuildingRadius);
            if (standing.result() == Building.validationResult.SUCCESS && standing.floor() != null) {
                return Optional.of(new AttachmentSeed(standingSeed, standing.floor(), standing.connectedFloors()));
            }
        }

        List<AttachmentSeed> candidates = new ArrayList<>();
        for (Direction direction : HORIZONTAL) {
            BlockPos connector = source.relative(direction);
            BlockState state = world.getBlockState(connector);
            if (!StructureConnector.isHorizontalBoundary(state)) continue;
            BlockPos candidate = StructureConnector.normalize(connector, state).relative(direction);
            SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(
                    world, candidate, config.maxBuildingSize, config.maxBuildingRadius);
            if (scan.result() == Building.validationResult.SUCCESS && scan.floor() != null) {
                candidates.add(new AttachmentSeed(candidate, scan.floor(), scan.connectedFloors()));
            }
        }
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    /** Captures one fresh selected-Floor scan together with all attachment evidence derived from it. */
    static Optional<FloorObservation> observeFloor(Level world,
                                                   BlockPos source,
                                                   Collection<Structure> existing) {
        AttachmentSeed seed = resolveAttachmentSeed(world, source).orElse(null);
        if (seed == null) return Optional.empty();
        StructureFloor candidate = new StructureFloor(0, 0, seed.floor());
        return Optional.of(new FloorObservation(
                seed.seed(), seed.floor(), seed.connectedFloors(),
                StructureConnector.verticalConnections(world, candidate, existing)));
    }

    static Building.validationResult validateObservation(FloorObservation observation,
                                                          Collection<Structure> existing,
                                                          int ignoredStructureId,
                                                          int attachmentBuildingId) {
        if (observation == null) return Building.validationResult.NOT_IN_BUILDING;
        StructureFloor floor = new StructureFloor(0, 0, observation.floor());
        Structure candidate = new Structure(ignoredStructureId, observation.seed(), List.of(floor));
        return validateCandidate(candidate, floor, existing, ignoredStructureId, attachmentBuildingId);
    }

    static boolean isWalkableAnchor(Level world, BlockPos pos) {
        return SelectedFloorScanner.inspectSurfaceCell(
                world, pos, new FloorCeilingResolver(world)).isPresent();
    }

    /**
     * Player block positions can lie inside partial-height collision blocks such as slabs or stairs.
     * Fresh discovery still needs an open feet cell, so normalize that interaction to the supported
     * cell immediately above without teaching the scanner about individual block classes.
     */
    static Optional<BlockPos> resolveStandingSurfaceSeed(Level world, BlockPos source) {
        if (isWalkableAnchor(world, source)) return Optional.of(source.immutable());
        if (world.getBlockState(source).getCollisionShape(world, source).isEmpty()) return Optional.empty();

        BlockPos above = source.above();
        return isWalkableAnchor(world, above)
                ? Optional.of(above.immutable())
                : Optional.empty();
    }

    private static Result scanAtSeed(Level world,
                                     BlockPos interactionSource,
                                     BlockPos scanSeed,
                                     Collection<Structure> existing,
                                     int ignoredStructureId,
                                     int attachmentBuildingId) {
        Config config = Config.getInstance();
        SelectedFloorScanner.Result selected = SelectedFloorScanner.scan(
                world, scanSeed, config.maxBuildingSize, config.maxBuildingRadius);
        if (selected.result() != Building.validationResult.SUCCESS || selected.floor() == null) {
            return Result.failure(selected.result(), interactionSource);
        }

        FloorGeometry scannedFloor = selected.floor();
        StructureFloor floor = new StructureFloor(0, 0, scannedFloor);
        Structure candidate = new Structure(ignoredStructureId, scanSeed.immutable(), List.of(floor));
        Building.validationResult validation = validateCandidate(
                candidate, floor, existing, ignoredStructureId, attachmentBuildingId);
        if (validation != Building.validationResult.SUCCESS) return Result.failure(validation, interactionSource);
        return new Result(Building.validationResult.SUCCESS, scanSeed.immutable(),
                selected.min(), selected.max(), scannedFloor, selected.connectedFloors());
    }

    private static Building.validationResult validateCandidate(Structure candidate,
                                                                StructureFloor floor,
                                                                Collection<Structure> existing,
                                                                int ignoredStructureId,
                                                                int attachmentBuildingId) {
        for (Structure other : existing) {
            if (other.getId() == ignoredStructureId || !candidate.intersects(other)) continue;
            boolean permittedAttachmentStack = attachmentBuildingId >= 0
                    && other.getLogicalBuildingId() == attachmentBuildingId
                    && !hasSameBandOverlap(floor, other);
            if (!permittedAttachmentStack) return Building.validationResult.OVERLAP;
        }
        return Building.validationResult.SUCCESS;
    }

    private static boolean hasSameBandOverlap(StructureFloor candidate, Structure structure) {
        return structure.getFloors().stream()
                .anyMatch(candidate::overlapsSameSemanticBand);
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

    record AttachmentSeed(BlockPos seed,
                          FloorGeometry floor,
                          List<FloorGeometry> connectedFloors) {
        AttachmentSeed {
            seed = seed.immutable();
            connectedFloors = connectedFloors == null ? List.of() : List.copyOf(connectedFloors);
        }
    }

    record FloorObservation(BlockPos seed,
                            FloorGeometry floor,
                            List<FloorGeometry> connectedFloors,
                            List<StructureConnector.VerticalConnection> verticalConnections) {
        FloorObservation {
            seed = seed.immutable();
            connectedFloors = connectedFloors == null ? List.of() : List.copyOf(connectedFloors);
            verticalConnections = verticalConnections == null ? List.of() : List.copyOf(verticalConnections);
        }
    }

    record Result(Building.validationResult result,
                  BlockPos source,
                  BlockPos min,
                  BlockPos max,
                  FloorGeometry scannedFloor,
                  List<FloorGeometry> connectedFloors) {
        Result {
            connectedFloors = connectedFloors == null ? List.of() : List.copyOf(connectedFloors);
        }

        static Result failure(Building.validationResult result, BlockPos source) {
            return new Result(result, source, source, source, null, List.of());
        }

        StructureFloor floor() {
            return scannedFloor == null ? null : new StructureFloor(0, 0, scannedFloor);
        }

        Structure toStructure(int id) {
            StructureFloor floor = floor();
            if (floor == null) throw new IllegalStateException("Cannot materialize a failed Structure scan");
            StructureFloor assigned = new StructureFloor(0, floor.floorNumber(), floor.geometry());
            return new Structure(id, source, List.of(assigned));
        }
    }
}
