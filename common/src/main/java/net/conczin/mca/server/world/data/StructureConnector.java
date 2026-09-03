package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.*;

/** Connector mechanics: classification, Floor-cell projection and connector-relative Floor handoff. */
final class StructureConnector {
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private StructureConnector() {
    }

    static boolean isConnector(BlockState state) {
        return state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof LadderBlock;
    }

    static boolean isHorizontalBoundary(BlockState state) {
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock;
    }

    /**
     * A ladder is inherently traversable vertically. A trapdoor only joins that semantic connector
     * when it is part of the same contiguous column as a ladder; standalone decorative trapdoors
     * remain ordinary boundaries and cannot create a Floor transition by themselves.
     */
    static boolean isVertical(Level world, BlockPos pos) {
        return isVerticalElement(world.getBlockState(pos))
                && !verticalColumnFromConnector(world, pos).isEmpty();
    }

    static List<StructureFloor.ConnectorMarker> floorMarkers(FloorSurface surface) {
        if (surface == null || surface.connectorTypesByFloorCell().isEmpty()) return List.of();
        LinkedHashSet<StructureFloor.ConnectorMarker> markers = new LinkedHashSet<>();
        int anchorY = surface.anchorY();
        for (Map.Entry<BlockPos, StructureFloor.ConnectorType> entry : surface.connectorTypesByFloorCell().entrySet()) {
            BlockPos floorCell = entry.getKey();
            markers.add(new StructureFloor.ConnectorMarker(
                    new BlockPos(floorCell.getX(), anchorY, floorCell.getZ()), entry.getValue()));
        }
        return markers.stream()
                .sorted(Comparator.comparingInt((StructureFloor.ConnectorMarker marker) -> marker.pos().getX())
                        .thenComparingInt(marker -> marker.pos().getZ())
                        .thenComparing(marker -> marker.type().serializedName()))
                .toList();
    }

    private static StructureFloor.ConnectorType connectorType(BlockState state) {
        if (state.getBlock() instanceof LadderBlock) return StructureFloor.ConnectorType.LADDER;
        if (state.getBlock() instanceof TrapDoorBlock) return StructureFloor.ConnectorType.TRAPDOOR;
        if (state.getBlock() instanceof DoorBlock) return StructureFloor.ConnectorType.DOOR;
        if (state.getBlock() instanceof FenceGateBlock) return StructureFloor.ConnectorType.GATE;
        return null;
    }

    static BlockPos normalize(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof DoorBlock) {
            return normalizeDoorHalf(pos, state.getValue(DoorBlock.HALF));
        }
        return pos;
    }

    static BlockPos normalizeDoorHalf(BlockPos pos, DoubleBlockHalf half) {
        return half == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    /** Associates connector positions with exact cells on the already selected semantic floor. */
    static Map<BlockPos, StructureFloor.ConnectorType> associatedFloorCells(
            Level world, Collection<BlockPos> connectors, FloorSurface surface) {
        if (connectors.isEmpty() || surface.cells().isEmpty()) return Map.of();

        LinkedHashMap<BlockPos, StructureFloor.ConnectorType> result = new LinkedHashMap<>();
        for (BlockPos rawConnector : connectors) {
            BlockState rawState = world.getBlockState(rawConnector);
            BlockPos connector = normalize(rawConnector, rawState);
            BlockState state = world.getBlockState(connector);
            if (!isConnector(state)) continue;
            StructureFloor.ConnectorType type = connectorType(state);
            if (type == null) continue;
            floorMembershipCells(connector, surface)
                    .forEach(floorCell -> result.putIfAbsent(floorCell, type));
        }
        return Map.copyOf(result);
    }

    static Set<BlockPos> floorMembershipCells(BlockPos connector, FloorSurface surface) {
        if (connector == null || surface == null || surface.cells().isEmpty()) return Set.of();
        LinkedHashSet<BlockPos> cells = new LinkedHashSet<>();
        for (BlockPos handoff : handoffs(connector)) {
            FloorSurface.Cell landing = surface.cellAtColumn(handoff.getX(), handoff.getZ()).orElse(null);
            if (landing == null) continue;
            cells.add(new BlockPos(connector.getX(), landing.feet().getY(), connector.getZ()));
        }
        return Set.copyOf(cells);
    }

    /** Returns the vertical connector column for an occupied connector or its immediate open top-exit cell. */
    private static List<BlockPos> verticalColumn(Level world, BlockPos pos) {
        BlockPos connector = verticalInteractionConnector(world, pos);
        return connector == null ? List.of() : verticalColumnFromConnector(world, connector);
    }

    static BlockPos verticalInteractionConnector(Level world, BlockPos pos) {
        if (isVerticalElement(world.getBlockState(pos))) return pos;
        if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) return null;

        BlockPos below = pos.below();
        return isVerticalElement(world.getBlockState(below)) ? below : null;
    }

    private static List<BlockPos> verticalColumnFromConnector(Level world, BlockPos connector) {
        if (!isVerticalElement(world.getBlockState(connector))) return List.of();

        BlockPos bottom = connector;
        while (isVerticalElement(world.getBlockState(bottom.below()))) bottom = bottom.below();
        BlockPos top = connector;
        while (isVerticalElement(world.getBlockState(top.above()))) top = top.above();

        List<BlockPos> column = new ArrayList<>(top.getY() - bottom.getY() + 1);
        boolean traversable = false;
        for (int y = bottom.getY(); y <= top.getY(); y++) {
            BlockPos element = new BlockPos(connector.getX(), y, connector.getZ());
            column.add(element);
            traversable |= world.getBlockState(element).getBlock() instanceof LadderBlock;
        }
        return traversable ? List.copyOf(column) : List.of();
    }

    private static boolean isVerticalElement(BlockState state) {
        return state.getBlock() instanceof LadderBlock || state.getBlock() instanceof TrapDoorBlock;
    }

    private static List<BlockPos> verticalHandoffCandidates(Level world, BlockPos source) {
        return verticalColumn(world, source).stream()
                .flatMap(connector -> handoffs(connector).stream())
                .distinct()
                .toList();
    }

    static List<BlockPos> handoffs(BlockPos pos) {
        List<BlockPos> result = new ArrayList<>();
        result.add(pos.above());
        result.add(pos.below());
        for (Direction direction : HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            result.add(side);
            result.add(side.above());
            result.add(side.below());
        }
        return result;
    }

    private static BlockPos floorHandoff(StructureFloor floor, BlockPos connector) {
        return handoffs(connector).stream()
                .filter(candidate -> candidate.getY() >= floor.anchorY()
                        && candidate.getY() < floor.ceilingY())
                .filter(candidate -> floor.contains(candidate.getX(), candidate.getZ()))
                .findFirst().orElse(null);
    }

    static boolean connectsFloors(List<BlockPos> connectorColumn,
                                  StructureFloor first,
                                  StructureFloor second) {
        if (connectorColumn == null || connectorColumn.isEmpty() || first == null || second == null
                || first.verticalGapTo(second) < 0) {
            return false;
        }
        boolean touchesFirst = connectorColumn.stream().anyMatch(connector -> floorHandoff(first, connector) != null);
        boolean touchesSecond = connectorColumn.stream().anyMatch(connector -> floorHandoff(second, connector) != null);
        return touchesFirst && touchesSecond;
    }

    static List<VerticalConnection> verticalConnections(Level world,
                                                        StructureFloor candidate,
                                                        Collection<Structure> existing) {
        if (world == null || candidate == null || candidate.region() == null || existing == null) {
            return List.of();
        }

        LinkedHashSet<Long> visitedColumns = new LinkedHashSet<>();
        LinkedHashSet<VerticalConnection> connections = new LinkedHashSet<>();
        for (StructureFloor.ConnectorMarker marker : candidate.connectors()) {
            if (!marker.type().vertical()) continue;
            List<BlockPos> column = verticalColumnAtFloorCell(world, marker.pos());
            if (column.isEmpty()) continue;
            BlockPos connector = column.getFirst();
            long columnKey = FloorSurface.columnKey(connector.getX(), connector.getZ());
            if (!visitedColumns.add(columnKey)) continue;

            for (Structure structure : existing) {
                for (StructureFloor floor : structure.getFloors()) {
                    if (connectsFloors(column, candidate, floor)) {
                        connections.add(new VerticalConnection(structure, floor));
                    }
                }
            }
        }
        return connections.stream().sorted(Comparator
                .comparingInt((VerticalConnection connection) -> connection.structure().getId())
                .thenComparingInt(connection -> connection.floor().id()))
                .toList();
    }

    private static List<BlockPos> verticalColumnAtFloorCell(Level world, BlockPos floorCell) {
        for (BlockPos probe : List.of(floorCell, floorCell.below(), floorCell.above())) {
            List<BlockPos> column = verticalColumnFromConnector(world, probe);
            if (!column.isEmpty()) return column;
        }
        return List.of();
    }

    static Optional<FloorHandoff> resolveVerticalFloorHandoff(
            Level world, BlockPos source, Config config) {
        List<BlockPos> candidates = verticalHandoffCandidates(world, source).stream()
                .sorted(Comparator
                        .comparingInt((BlockPos candidate) -> Math.abs(candidate.getY() - source.getY()))
                        .thenComparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getZ))
                .toList();

        return resolveFloorHandoff(world, source, candidates, config);
    }

    static Optional<FloorHandoff> resolveHorizontalFloorHandoff(
            Level world, BlockPos source, Config config) {
        BlockState sourceState = world.getBlockState(source);
        if (!isHorizontalBoundary(sourceState)) return Optional.empty();

        BlockPos connector = normalize(source, sourceState);
        List<BlockPos> candidates = Arrays.stream(HORIZONTAL)
                .map(connector::relative)
                .sorted(Comparator
                        .comparingInt((BlockPos candidate) -> candidate.getY())
                        .thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getZ))
                .toList();

        return resolveFloorHandoff(world, source, candidates, config);
    }

    private static Optional<FloorHandoff> resolveFloorHandoff(
            Level world, BlockPos source, List<BlockPos> rawCandidates, Config config) {
        FloorCeilingResolver ceilings = new FloorCeilingResolver(world);
        List<BlockPos> candidates = rawCandidates.stream()
                .filter(candidate -> SelectedFloorScanner.inspectSurfaceCell(world, candidate, ceilings).isPresent())
                .toList();

        FloorHandoff selected = null;
        Set<BlockPos> selectedFloor = null;
        int selectedDistance = Integer.MAX_VALUE;
        int selectedY = Integer.MAX_VALUE;
        for (BlockPos candidate : candidates) {
            int distance = Math.abs(candidate.getY() - source.getY());
            if (selected != null && (distance > selectedDistance
                    || distance == selectedDistance && candidate.getY() > selectedY)) {
                break;
            }

            SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(
                    world, candidate, config.maxBuildingSize, config.maxBuildingRadius);
            if (scan.result() != Building.validationResult.SUCCESS || scan.surface() == null) continue;

            Set<BlockPos> candidateFloor = scan.surface().projectedCells();
            if (selected == null) {
                selected = new FloorHandoff(candidate.immutable(), scan.surface());
                selectedFloor = candidateFloor;
                selectedDistance = distance;
                selectedY = candidate.getY();
            } else if (!selectedFloor.equals(candidateFloor)) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(selected);
    }

    record FloorHandoff(BlockPos seed, FloorSurface surface) {
        FloorHandoff {
            seed = seed.immutable();
        }
    }

    record VerticalConnection(Structure structure, StructureFloor floor) {
        VerticalConnection {
            Objects.requireNonNull(structure, "structure");
            Objects.requireNonNull(floor, "floor");
        }
    }
}
