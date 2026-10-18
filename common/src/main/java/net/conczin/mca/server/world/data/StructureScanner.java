package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Validates one selected Floor and gathers only local attachment evidence. */
final class StructureScanner {
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private StructureScanner() {
    }

    private static SelectedFloorScanner.Observation newObservation(Level world) {
        Config config = Config.getInstance();
        return new SelectedFloorScanner.Observation(
                world, config.maxBuildingSize, config.maxBuildingRadius);
    }

    static Result scanNewStructure(Level world,
                                   BlockPos source,
                                   Collection<Structure> existing) {
        SelectedFloorScanner.Observation observation = newObservation(world);
        Result exact = resultFromObservedFloor(source, observation.selected(source), existing, -1, -1);
        if (exact.result() == Building.validationResult.SUCCESS) return exact;

        SelectedFloorScanner.Result handoff = resolveFloorHandoff(source,
                StructureConnector.verticalHandoffCandidates(world, source), observation).orElse(null);
        if (handoff != null) {
            return resultFromObservedFloor(source, handoff, existing, -1, -1);
        }

        handoff = resolveFloorHandoff(source,
                StructureConnector.horizontalHandoffCandidates(world, source), observation).orElse(null);
        if (handoff != null) {
            return resultFromObservedFloor(source, handoff, existing, -1, -1);
        }

        BlockPos standingSeed = resolveStandingSurfaceSeed(world, source, observation).orElse(null);
        return standingSeed == null || standingSeed.equals(source)
                ? exact
                : resultFromObservedFloor(standingSeed, observation.selected(standingSeed), existing, -1, -1);
    }

    static Result scanReportedStructure(Level world,
                                        BlockPos source,
                                        Collection<Structure> existing) {
        SelectedFloorScanner.Observation observation = newObservation(world);
        Result exact = resultFromObservedFloor(source, observation.selected(source), existing, -1, -1);
        if (exact.result() == Building.validationResult.SUCCESS) return exact;

        for (Direction direction : HORIZONTAL) {
            BlockPos candidate = source.relative(direction);
            Result adjacent = resultFromObservedFloor(
                    candidate, observation.selected(candidate), existing, -1, -1);
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
        StructureFloor floor = new StructureFloor(0, 0, selected.anchorY(), selected.floor());
        Structure candidate = new Structure(-1, selected.seed(), List.of(floor));
        Building.validationResult validation = validateCandidate(
                candidate, floor, selected, existing, ignoredStructureId, attachmentBuildingId);
        return validation == Building.validationResult.SUCCESS
                ? new Result(Building.validationResult.SUCCESS, selected.seed(), selected)
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

        SelectedFloorScanner.Observation observation = newObservation(world);
        BlockPos seed = resolveExistingSeed(floor, source, observation).orElse(null);
        if (seed == null) return Result.failure(Building.validationResult.NOT_IN_BUILDING, source);
        SelectedFloorScanner.Result selected = observation.selected(seed);
        Result validated = resultFromObservedFloor(source, selected, existing,
                structure.getId(), structure.getLogicalBuildingId());
        if (validated.result() != Building.validationResult.SUCCESS) return validated;
        // The Floor's traversal anchor and the requested Room cell have different roles.
        BlockPos interactionCell = selected.floor().interactionCellAt(
                source.getX(), source.getY(), source.getZ()).map(FloorGeometry.Cell::feet).orElse(validated.source());
        return new Result(Building.validationResult.SUCCESS, interactionCell, selected);
    }

    private static Optional<SelectedFloorScanner.Result> resolveAttachmentSeed(
            Level world, BlockPos source, SelectedFloorScanner.Observation observation) {
        SelectedFloorScanner.Result exact = observation.selected(source);
        if (exact.result() == Building.validationResult.SUCCESS && exact.floor() != null) {
            return Optional.of(exact);
        }

        Optional<SelectedFloorScanner.Result> vertical = resolveFloorHandoff(source,
                StructureConnector.verticalHandoffCandidates(world, source), observation);
        if (vertical.isPresent()) return vertical;

        BlockPos standingSeed = resolveStandingSurfaceSeed(world, source, observation).orElse(null);
        if (standingSeed != null && !standingSeed.equals(source)) {
            SelectedFloorScanner.Result standing = observation.selected(standingSeed);
            if (standing.result() == Building.validationResult.SUCCESS && standing.floor() != null) {
                return Optional.of(standing);
            }
        }

        boolean subFullInteraction = isSubFullInteraction(world, source);
        if (subFullInteraction || hasInterruptedLandingSupport(world, source)) {
            Optional<SelectedFloorScanner.Result> adjacent = resolveFloorHandoff(
                    source,
                    java.util.Arrays.stream(HORIZONTAL).map(source::relative)
                            .filter(candidate -> subFullInteraction || world.getBlockState(candidate)
                                    .getCollisionShape(world, candidate).isEmpty())
                            .toList(),
                    observation);
            if (adjacent.isPresent()) return adjacent;
        }

        List<SelectedFloorScanner.Result> candidates = new ArrayList<>();
        for (Direction direction : HORIZONTAL) {
            BlockPos connector = source.relative(direction);
            BlockState state = world.getBlockState(connector);
            if (!StructureConnector.isHorizontalBoundary(state)) continue;
            BlockPos candidate = StructureConnector.normalize(connector, state).relative(direction);
            SelectedFloorScanner.Result scan = observation.selected(candidate);
            if (scan.result() == Building.validationResult.SUCCESS && scan.floor() != null) {
                candidates.add(scan);
            }
        }
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    private static Optional<SelectedFloorScanner.Result> resolveFloorHandoff(
            BlockPos source, Collection<BlockPos> rawCandidates, SelectedFloorScanner.Observation observation) {
        List<BlockPos> candidates = rawCandidates.stream()
                .filter(candidate -> observation.surfaceCell(candidate).isPresent())
                .sorted(Comparator
                        .comparingInt((BlockPos candidate) -> Math.abs(candidate.getY() - source.getY()))
                        .thenComparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getZ))
                .toList();

        SelectedFloorScanner.Result selected = null;
        int selectedDistance = Integer.MAX_VALUE;
        int selectedY = Integer.MAX_VALUE;
        for (BlockPos candidate : candidates) {
            int distance = Math.abs(candidate.getY() - source.getY());
            if (selected != null && (distance > selectedDistance
                    || distance == selectedDistance && candidate.getY() > selectedY)) {
                break;
            }

            SelectedFloorScanner.Result scan = observation.selected(candidate);
            if (scan.result() != Building.validationResult.SUCCESS || scan.floor() == null) continue;

            if (selected == null) {
                selected = scan;
                selectedDistance = distance;
                selectedY = candidate.getY();
            } else if (!selected.floor().sameCellPositions(scan.floor())) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(selected);
    }

    /** Captures one fresh selected-Floor scan together with all attachment evidence derived from it. */
    static Optional<FloorObservation> observeFloor(Level world,
                                                   BlockPos source,
                                                   Collection<Structure> existing) {
        SelectedFloorScanner.Observation observation = newObservation(world);
        SelectedFloorScanner.Result scan = resolveAttachmentSeed(world, source, observation).orElse(null);
        if (scan == null) return Optional.empty();
        StructureFloor candidate = new StructureFloor(0, 0, scan.anchorY(), scan.floor());
        List<StructureConnector.VerticalConnection> vertical = StructureConnector.verticalConnections(world, candidate, existing);
        Set<StructureConnector.VerticalConnection> contacts = new HashSet<>();
        if (attachmentConnections(existing, vertical, scan.adjacentFloorSeeds()).isEmpty()) {
            for (Structure structure : existing) {
                for (StructureFloor floor : structure.getFloors()) {
                    if (candidate.geometry().cells().stream().anyMatch(cell -> floor.geometry()
                            .cellsAtColumn(cell.feet().getX(), cell.feet().getZ()).stream()
                            .anyMatch(other -> observation.shellsTouch(cell, other)))) {
                        contacts.add(new StructureConnector.VerticalConnection(structure, floor));
                    }
                }
            }
        }
        return Optional.of(new FloorObservation(scan, vertical, contacts));
    }

    static Set<StructureConnector.VerticalConnection> attachmentConnections(
            Collection<Structure> existing,
            Collection<StructureConnector.VerticalConnection> verticalConnections,
            Collection<BlockPos> stairTransitions) {
        LinkedHashSet<StructureConnector.VerticalConnection> connections = new LinkedHashSet<>();
        if (verticalConnections != null) connections.addAll(verticalConnections);
        if (stairTransitions == null) return Set.copyOf(connections);

        for (BlockPos transition : stairTransitions) {
            for (Structure structure : existing) {
                for (StructureFloor floor : structure.getFloors()) {
                    if (floor.geometry().interactionCellAt(
                            transition.getX(), transition.getY(), transition.getZ()).isPresent()) {
                        connections.add(new StructureConnector.VerticalConnection(structure, floor));
                    }
                }
            }
        }
        return Set.copyOf(connections);
    }

    static Building.validationResult validateObservation(FloorObservation observation,
                                                          Collection<Structure> existing,
                                                          int ignoredStructureId,
                                                          int attachmentBuildingId) {
        if (observation == null) return Building.validationResult.NOT_IN_BUILDING;
        return resultFromObservedFloor(observation.seed(), observation.scan(),
                existing, ignoredStructureId, attachmentBuildingId).result();
    }

    /**
     * Player block positions can lie inside partial collision shapes or one block above the surface
     * while standing across a step's edge. Normalize to a supported feet cell in the same column.
     */
    private static Optional<BlockPos> resolveStandingSurfaceSeed(
            Level world, BlockPos source, SelectedFloorScanner.Observation observation) {
        if (observation.surfaceCell(source).isPresent()) return Optional.of(source.immutable());
        if (world.getBlockState(source).getCollisionShape(world, source).isEmpty()) {
            BlockPos below = source.below();
            return observation.surfaceCell(below).isPresent() ? Optional.of(below.immutable()) : Optional.empty();
        }

        BlockPos above = source.above();
        return observation.surfaceCell(above).isPresent()
                ? Optional.of(above.immutable())
                : Optional.empty();
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
                .anyMatch(candidate::overlapsNearbyFloorBand);
    }

    private static Optional<BlockPos> resolveExistingSeed(
            StructureFloor floor, BlockPos source, SelectedFloorScanner.Observation observation) {
        if (floor == null || source == null) return Optional.empty();
        FloorGeometry geometry = floor.geometry();
        // An explicit refresh targets the persisted Floor, even if an old stair cell now
        // belongs upstairs. Select its ordinary anchor before scanning, rather than rescan.
        return geometry.cells().stream()
                .map(FloorGeometry.Cell::feet)
                .sorted(Comparator
                        .comparingInt((BlockPos candidate) -> Math.abs(candidate.getY() - floor.anchorY()))
                        .thenComparingInt(candidate -> manhattanDistance(candidate, source))
                        .thenComparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getZ))
                .filter(candidate -> observation.surfaceCell(candidate).isPresent())
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

    /** Nonstandard platforms and a ladder just above a landing can defeat a direct Floor seed. */
    private static boolean hasInterruptedLandingSupport(Level world, BlockPos source) {
        BlockState state = world.getBlockState(source);
        BlockPos above = source.above();
        BlockState aboveState = world.getBlockState(above);
        boolean ladderAbove = FloorConnector.Type.fromBlockState(aboveState) == FloorConnector.Type.LADDER;
        if (!state.getFluidState().isEmpty() || !state.getCollisionShape(world, source).isEmpty()
                || (!ladderAbove && !aboveState.getCollisionShape(world, above).isEmpty())) return false;
        BlockPos support = source.below();
        BlockState supportState = world.getBlockState(support);
        return supportState.getFluidState().isEmpty()
                && !supportState.getCollisionShape(world, support).isEmpty()
                && (ladderAbove || !supportState.isCollisionShapeFullBlock(world, support));
    }

    private static int manhattanDistance(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX())
                + Math.abs(first.getY() - second.getY())
                + Math.abs(first.getZ() - second.getZ());
    }

    /** Snapshot facts only: live contact does not grant explicit connector/stair permissions. */
    record FloorObservation(SelectedFloorScanner.Result scan,
                            List<StructureConnector.VerticalConnection> verticalConnections,
                            Set<StructureConnector.VerticalConnection> liveContacts) {
        FloorObservation {
            verticalConnections = verticalConnections == null ? List.of() : List.copyOf(verticalConnections);
            liveContacts = Set.copyOf(liveContacts);
        }

        BlockPos seed() {
            return scan.seed();
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

        Structure toStructure(int id) {
            return toStructure(id, 0);
        }

        Structure toStructure(int id, int floorNumber) {
            FloorGeometry floor = scannedFloor();
            if (floor == null) throw new IllegalStateException("Cannot materialize a failed Structure scan");
            return new Structure(id, source, List.of(new StructureFloor(0, floorNumber, scan.anchorY(), floor)));
        }
    }
}
