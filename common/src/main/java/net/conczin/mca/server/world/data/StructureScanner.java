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
import java.util.Set;
import java.util.function.Function;

/** Validates one selected Floor and gathers only local attachment evidence. */
final class StructureScanner {
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private StructureScanner() {
    }

    static Result scanNewStructure(Level world,
                                   BlockPos source,
                                   Collection<Structure> existing) {
        Config config = Config.getInstance();
        SelectedFloorScanner.Observation observation = new SelectedFloorScanner.Observation(
                world, config.maxBuildingSize, config.maxBuildingRadius);
        Result exact = resultFromObservedFloor(source, observation.selected(source), existing, -1, -1);
        if (exact.result() == Building.validationResult.SUCCESS) return exact;

        FloorHandoff handoff = resolveFloorHandoff(world, source,
                StructureConnector.verticalHandoffCandidates(world, source), observation::selected).orElse(null);
        if (handoff != null) {
            return resultFromObservedFloor(handoff.seed(), handoff.scan(), existing, -1, -1);
        }

        handoff = resolveFloorHandoff(world, source,
                StructureConnector.horizontalHandoffCandidates(world, source), observation::selected).orElse(null);
        if (handoff != null) {
            return resultFromObservedFloor(handoff.seed(), handoff.scan(), existing, -1, -1);
        }

        BlockPos standingSeed = resolveStandingSurfaceSeed(world, source).orElse(null);
        return standingSeed == null || standingSeed.equals(source)
                ? exact
                : resultFromObservedFloor(standingSeed, observation.selected(standingSeed), existing, -1, -1);
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

    static Result resultFromObservedFloor(BlockPos source,
                                          SelectedFloorScanner.Result selected,
                                          Collection<Structure> existing,
                                          int ignoredStructureId,
                                          int attachmentBuildingId) {
        if (selected == null || selected.result() != Building.validationResult.SUCCESS
                || selected.floor() == null) {
            return Result.failure(selected == null
                    ? Building.validationResult.NOT_IN_BUILDING : selected.result(), source);
        }
        StructureFloor floor = new StructureFloor(0, 0, selected.floor());
        Structure candidate = new Structure(-1, source.immutable(), List.of(floor));
        Building.validationResult validation = validateCandidate(
                candidate, floor, selected, existing, ignoredStructureId, attachmentBuildingId);
        return validation == Building.validationResult.SUCCESS
                ? new Result(Building.validationResult.SUCCESS, source.immutable(), selected)
                : Result.failure(validation, source);
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
        return scanAtSeed(world, source, seed, existing, structure.getId(), structure.getLogicalBuildingId());
    }

    private static Optional<FloorHandoff> resolveAttachmentSeed(Level world, BlockPos source) {
        Config config = Config.getInstance();
        SelectedFloorScanner.Observation observation = new SelectedFloorScanner.Observation(
                world, config.maxBuildingSize, config.maxBuildingRadius);
        SelectedFloorScanner.Result exact = observation.selected(source);
        if (exact.result() == Building.validationResult.SUCCESS && exact.floor() != null) {
            BlockPos resolvedSeed = resolveFloorSeed(world, exact.floor(), source).orElse(source);
            return Optional.of(new FloorHandoff(resolvedSeed, exact));
        }

        Optional<FloorHandoff> vertical = resolveFloorHandoff(world, source,
                StructureConnector.verticalHandoffCandidates(world, source), observation::selected);
        if (vertical.isPresent()) return vertical;

        BlockPos standingSeed = resolveStandingSurfaceSeed(world, source).orElse(null);
        if (standingSeed != null && !standingSeed.equals(source)) {
            SelectedFloorScanner.Result standing = observation.selected(standingSeed);
            if (standing.result() == Building.validationResult.SUCCESS && standing.floor() != null) {
                return Optional.of(new FloorHandoff(standingSeed, standing));
            }
        }

        if (isSubFullInteraction(world, source)) {
            Optional<FloorHandoff> adjacent = resolveFloorHandoff(
                    world,
                    source,
                    java.util.Arrays.stream(HORIZONTAL).map(source::relative).toList(),
                    observation::selected);
            if (adjacent.isPresent()) return adjacent;
        }

        List<FloorHandoff> candidates = new ArrayList<>();
        for (Direction direction : HORIZONTAL) {
            BlockPos connector = source.relative(direction);
            BlockState state = world.getBlockState(connector);
            if (!StructureConnector.isHorizontalBoundary(state)) continue;
            BlockPos candidate = StructureConnector.normalize(connector, state).relative(direction);
            SelectedFloorScanner.Result scan = observation.selected(candidate);
            if (scan.result() == Building.validationResult.SUCCESS && scan.floor() != null) {
                candidates.add(new FloorHandoff(candidate, scan));
            }
        }
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    private static Optional<FloorHandoff> resolveFloorHandoff(
            Level world, BlockPos source, Collection<BlockPos> rawCandidates,
            Function<BlockPos, SelectedFloorScanner.Result> scanFloor) {
        FloorCeilingResolver ceilings = new FloorCeilingResolver(world);
        List<BlockPos> candidates = rawCandidates.stream()
                .filter(candidate -> SelectedFloorScanner.inspectSurfaceCell(world, candidate, ceilings).isPresent())
                .sorted(Comparator
                        .comparingInt((BlockPos candidate) -> Math.abs(candidate.getY() - source.getY()))
                        .thenComparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getZ))
                .toList();

        FloorHandoff selected = null;
        int selectedDistance = Integer.MAX_VALUE;
        int selectedY = Integer.MAX_VALUE;
        for (BlockPos candidate : candidates) {
            int distance = Math.abs(candidate.getY() - source.getY());
            if (selected != null && (distance > selectedDistance
                    || distance == selectedDistance && candidate.getY() > selectedY)) {
                break;
            }

            SelectedFloorScanner.Result scan = scanFloor.apply(candidate);
            if (scan.result() != Building.validationResult.SUCCESS || scan.floor() == null) continue;

            if (selected == null) {
                selected = new FloorHandoff(candidate, scan);
                selectedDistance = distance;
                selectedY = candidate.getY();
            } else if (!selected.scan().floor().sameCellPositions(scan.floor())) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(selected);
    }

    /** Captures one fresh selected-Floor scan together with all attachment evidence derived from it. */
    static Optional<FloorObservation> observeFloor(Level world,
                                                   BlockPos source,
                                                   Collection<Structure> existing) {
        FloorHandoff seed = resolveAttachmentSeed(world, source).orElse(null);
        if (seed == null) return Optional.empty();
        StructureFloor candidate = new StructureFloor(0, 0, seed.scan().floor());
        return Optional.of(new FloorObservation(
                seed.seed(), seed.scan(),
                StructureConnector.verticalConnections(world, candidate, existing)));
    }

    static Building.validationResult validateObservation(FloorObservation observation,
                                                          Collection<Structure> existing,
                                                          int ignoredStructureId,
                                                          int attachmentBuildingId) {
        if (observation == null) return Building.validationResult.NOT_IN_BUILDING;
        return resultFromObservedFloor(observation.seed(), observation.scan(),
                existing, ignoredStructureId, attachmentBuildingId).result();
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
    private static Optional<BlockPos> resolveStandingSurfaceSeed(Level world, BlockPos source) {
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
        Result result = resultFromObservedFloor(
                scanSeed, selected, existing, ignoredStructureId, attachmentBuildingId);
        return result.result() == Building.validationResult.SUCCESS
                ? result : Result.failure(result.result(), interactionSource);
    }

    private static Building.validationResult validateCandidate(Structure candidate,
                                                                StructureFloor floor,
                                                                SelectedFloorScanner.Result selected,
                                                                Collection<Structure> existing,
                                                                int ignoredStructureId,
                                                                int attachmentBuildingId) {
        for (Structure other : existing) {
            if (other.getId() == ignoredStructureId || !candidate.intersects(other)) continue;
            boolean permittedAttachmentStack = attachmentBuildingId >= 0
                    && other.getLogicalBuildingId() == attachmentBuildingId
                    && (!hasSameBandOverlap(floor, other)
                    || hasDirectFloorConnection(selected, floor, other));
            if (!permittedAttachmentStack) return Building.validationResult.OVERLAP;
        }
        return Building.validationResult.SUCCESS;
    }

    private static boolean hasDirectFloorConnection(
            SelectedFloorScanner.Result selected, StructureFloor candidate, Structure structure) {
        if (selected == null || candidate == null || structure == null) return false;
        return selected.adjacentFloorSeeds().stream().anyMatch(seed ->
                structure.getFloors().stream().anyMatch(floor ->
                        floor.geometry().interactionCellAt(seed.getX(), seed.getY(), seed.getZ()).isPresent()));
    }

    private static boolean hasSameBandOverlap(StructureFloor candidate, Structure structure) {
        return structure.getFloors().stream()
                .anyMatch(candidate::overlapsSameSemanticBand);
    }

    private static Optional<BlockPos> resolveExistingSeed(
            Level world, StructureFloor floor, BlockPos source) {
        return floor == null ? Optional.empty() : resolveFloorSeed(world, floor.geometry(), source);
    }

    private static Optional<BlockPos> resolveFloorSeed(
            Level world, FloorGeometry geometry, BlockPos source) {
        if (geometry == null || source == null) return Optional.empty();
        if (geometry.physicalCellAt(source.getX(), source.getY(), source.getZ()).isPresent()
                && isWalkableAnchor(world, source)) {
            return Optional.of(source.immutable());
        }

        return geometry.cells().stream()
                .map(FloorGeometry.Cell::feet)
                .sorted(Comparator
                        .comparingInt((BlockPos candidate) -> manhattanDistance(candidate, source))
                        .thenComparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getZ))
                .filter(candidate -> isWalkableAnchor(world, candidate))
                .map(BlockPos::immutable)
                .findFirst();
    }

    private static boolean isSubFullInteraction(Level world, BlockPos source) {
        BlockState state = world.getBlockState(source);
        if (!state.getFluidState().isEmpty()) return false;
        var shape = state.getCollisionShape(world, source);
        return !shape.isEmpty()
                && shape.max(Direction.Axis.Y) <= 1.0D
                && !state.isCollisionShapeFullBlock(world, source);
    }

    private static int manhattanDistance(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX())
                + Math.abs(first.getY() - second.getY())
                + Math.abs(first.getZ() - second.getZ());
    }

    record FloorObservation(BlockPos seed,
                            SelectedFloorScanner.Result scan,
                            List<StructureConnector.VerticalConnection> verticalConnections) {
        FloorObservation {
            seed = seed.immutable();
            verticalConnections = verticalConnections == null ? List.of() : List.copyOf(verticalConnections);
        }
    }

    record Result(Building.validationResult result,
                  BlockPos source,
                  SelectedFloorScanner.Result scan) {
        FloorGeometry scannedFloor() {
            return scan == null ? null : scan.floor();
        }

        BlockPos min() {
            return scan == null ? source : scan.min();
        }

        BlockPos max() {
            return scan == null ? source : scan.max();
        }

        static Result failure(Building.validationResult result, BlockPos source) {
            return new Result(result, source, null);
        }

        StructureFloor floor() {
            FloorGeometry floor = scannedFloor();
            return floor == null ? null : new StructureFloor(0, 0, floor);
        }

        Structure toStructure(int id) {
            StructureFloor floor = floor();
            if (floor == null) throw new IllegalStateException("Cannot materialize a failed Structure scan");
            StructureFloor assigned = new StructureFloor(0, floor.floorNumber(), floor.geometry());
            return new Structure(id, source, List.of(assigned));
        }
    }

    private record FloorHandoff(BlockPos seed, SelectedFloorScanner.Result scan) {
        FloorHandoff {
            seed = seed.immutable();
        }
    }
}
