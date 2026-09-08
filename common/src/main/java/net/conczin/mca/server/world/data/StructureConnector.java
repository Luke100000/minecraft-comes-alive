package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
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
        return FloorConnector.Type.fromBlockState(state) != null;
    }

    static boolean isHorizontalBoundary(BlockState state) {
        FloorConnector.Type type = FloorConnector.Type.fromBlockState(state);
        return type != null && type.roomBoundary();
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

    static BlockPos normalize(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof DoorBlock) {
            return normalizeDoorHalf(pos, state.getValue(DoorBlock.HALF));
        }
        return pos;
    }

    static BlockPos normalizeDoorHalf(BlockPos pos, DoubleBlockHalf half) {
        return half == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    /** Associates connector positions with exact handoff heights on the selected semantic floor. */
    static Map<BlockPos, FloorConnector.Type> associatedFloorCells(
            Level world, Collection<BlockPos> connectors, FloorGeometry geometry) {
        if (connectors.isEmpty() || geometry.cells().isEmpty()) return Map.of();

        LinkedHashMap<BlockPos, FloorConnector.Type> result = new LinkedHashMap<>();
        for (BlockPos rawConnector : connectors) {
            BlockState rawState = world.getBlockState(rawConnector);
            BlockPos connector = normalize(rawConnector, rawState);
            BlockState state = world.getBlockState(connector);
            FloorConnector.Type type = FloorConnector.Type.fromBlockState(state);
            if (type == null) continue;
            (type.vertical() ? verticalFloorMembershipCells(connector, geometry)
                    : floorMembershipCells(connector, geometry))
                    .forEach(floorCell -> result.putIfAbsent(floorCell, type));
        }
        return Map.copyOf(result);
    }

    private static Set<BlockPos> verticalFloorMembershipCells(BlockPos connector, FloorGeometry geometry) {
        LinkedHashSet<BlockPos> cells = new LinkedHashSet<>();
        for (BlockPos handoff : handoffs(connector)) {
            geometry.interactionCellAt(handoff.getX(), handoff.getY(), handoff.getZ())
                    .map(FloorGeometry.Cell::feet)
                    .ifPresent(cells::add);
        }
        return Set.copyOf(cells);
    }

    static Set<BlockPos> floorMembershipCells(BlockPos connector, FloorGeometry geometry) {
        if (connector == null || geometry == null || geometry.cells().isEmpty()) return Set.of();
        LinkedHashSet<BlockPos> cells = new LinkedHashSet<>();
        for (BlockPos handoff : handoffs(connector)) {
            geometry.cellsAtColumn(handoff.getX(), handoff.getZ()).stream()
                    .filter(landing -> handoff.getY() == landing.feet().getY()
                            || handoff.getY() == landing.feet().getY() - 1)
                    .forEach(landing -> cells.add(new BlockPos(
                            connector.getX(), landing.feet().getY(), connector.getZ())));
        }
        return Set.copyOf(cells);
    }

    /** Attaches connector metadata only to exact Floor cells already discovered by the scanner. */
    static FloorGeometry withConnectorAssociations(
            FloorGeometry geometry,
            Map<BlockPos, FloorConnector.Type> connectorTypesByCell) {
        Objects.requireNonNull(geometry, "geometry");
        if (connectorTypesByCell == null || connectorTypesByCell.isEmpty()) return geometry;

        LinkedHashMap<BlockPos, FloorConnector.Type> connectors =
                new LinkedHashMap<>(geometry.connectorTypesByCell());

        for (Map.Entry<BlockPos, FloorConnector.Type> entry : connectorTypesByCell.entrySet()) {
            BlockPos floorCell = entry.getKey().immutable();
            if (geometry.cellAt(floorCell).isPresent()) connectors.putIfAbsent(floorCell, entry.getValue());
        }
        return new FloorGeometry(geometry.cells(), connectors);
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
            traversable |= FloorConnector.Type.fromBlockState(world.getBlockState(element))
                    == FloorConnector.Type.LADDER;
        }
        return traversable ? List.copyOf(column) : List.of();
    }

    private static boolean isVerticalElement(BlockState state) {
        FloorConnector.Type type = FloorConnector.Type.fromBlockState(state);
        return type != null && type.vertical();
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

    private static BlockPos floorHandoff(StructureFloor floor,
                                         StructureFloor other,
                                         BlockPos connector) {
        for (BlockPos candidate : handoffs(connector)) {
            FloorGeometry.Cell cell = floor.geometry().interactionCellAt(
                    candidate.getX(), candidate.getY(), candidate.getZ()).orElse(null);
            if (cell == null) continue;

            // A stale flat approximation may physically overlap the next semantic Floor. Do not
            // let that overlap manufacture a connector handoff above the next Floor anchor. An
            // exact transition cell whose own feet are at that height is still legitimate lower-
            // Floor geometry and remains eligible.
            if (floor.anchorY() < other.anchorY()
                    && candidate.getY() >= other.anchorY()
                    && cell.feet().getY() < other.anchorY()) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    static boolean connectsFloors(List<BlockPos> connectorColumn,
                                  StructureFloor first,
                                  StructureFloor second) {
        if (connectorColumn == null || connectorColumn.isEmpty() || first == null || second == null
                || first.attachmentGapTo(second) < 0) {
            return false;
        }
        boolean touchesFirst = connectorColumn.stream()
                .anyMatch(connector -> floorHandoff(first, second, connector) != null);
        boolean touchesSecond = connectorColumn.stream()
                .anyMatch(connector -> floorHandoff(second, first, connector) != null);
        return touchesFirst && touchesSecond;
    }

    static List<VerticalConnection> verticalConnections(Level world,
                                                        StructureFloor candidate,
                                                        Collection<Structure> existing) {
        if (world == null || candidate == null || existing == null) {
            return List.of();
        }

        LinkedHashSet<Long> visitedColumns = new LinkedHashSet<>();
        LinkedHashSet<VerticalConnection> connections = new LinkedHashSet<>();
        for (FloorConnector.Marker marker : candidate.connectors()) {
            if (!marker.type().vertical()) continue;
            List<BlockPos> column = verticalColumnAtFloorCell(world, candidate, marker.pos());
            if (column.isEmpty()) continue;
            BlockPos connector = column.getFirst();
            long columnKey = FloorGeometry.columnKey(connector.getX(), connector.getZ());
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

    static List<BlockPos> verticalProbePositions(StructureFloor floor, BlockPos floorCell) {
        if (floor == null || floorCell == null) return List.of();
        FloorGeometry.Cell cell = floor.geometry().cellAt(floorCell).orElse(null);
        if (cell == null) return List.of();
        List<BlockPos> probes = new ArrayList<>(Math.max(0, cell.ceilingY() - cell.feet().getY() + 1));
        for (int y = cell.feet().getY() - 1; y < cell.ceilingY(); y++) {
            probes.add(new BlockPos(floorCell.getX(), y, floorCell.getZ()));
        }
        return List.copyOf(probes);
    }

    private static List<BlockPos> verticalColumnAtFloorCell(Level world,
                                                             StructureFloor floor,
                                                             BlockPos floorCell) {
        for (BlockPos probe : verticalProbePositions(floor, floorCell)) {
            List<BlockPos> column = verticalColumnFromConnector(world, probe);
            if (!column.isEmpty()) return column;
            for (Direction direction : HORIZONTAL) {
                column = verticalColumnFromConnector(world, probe.relative(direction));
                if (!column.isEmpty()) return column;
            }
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
            if (scan.result() != Building.validationResult.SUCCESS || scan.floor() == null) continue;

            if (selected == null) {
                selected = new FloorHandoff(candidate, scan);
                selectedDistance = distance;
                selectedY = candidate.getY();
            } else if (!selected.scan().floor().sameFootprint(scan.floor())) {
                return Optional.empty();
            }
        }
        return Optional.ofNullable(selected);
    }

    record FloorHandoff(BlockPos seed,
                        SelectedFloorScanner.Result scan) {
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
