package net.conczin.mca.server.world.data;

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

    /** Room ownership follows the door's Minecraft FACING side, regardless of open state or hinge. */
    static Direction doorOwnerSide(BlockState state) {
        return state.getBlock() instanceof DoorBlock
                ? state.getValue(DoorBlock.FACING)
                : null;
    }

    static Map<BlockPos, Direction> doorOwnerSides(Level world, FloorGeometry geometry) {
        if (world == null || geometry == null) return Map.of();

        LinkedHashMap<BlockPos, Direction> ownerSides = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, FloorConnector.Type> entry : geometry.connectorTypesByCell().entrySet()) {
            if (entry.getValue() != FloorConnector.Type.DOOR) continue;
            Direction ownerSide = doorOwnerSide(world.getBlockState(entry.getKey()));
            if (ownerSide != null) ownerSides.put(entry.getKey(), ownerSide);
        }
        return Map.copyOf(ownerSides);
    }

    /** Returns connector metadata keyed only by exact cells already owned by this Floor. */
    static Map<BlockPos, FloorConnector.Type> connectorTypesForFloor(
            Level world, Collection<BlockPos> connectors, FloorGeometry geometry) {
        if (connectors.isEmpty() || geometry.cells().isEmpty()) return Map.of();

        LinkedHashMap<BlockPos, FloorConnector.Type> result = new LinkedHashMap<>();
        for (BlockPos rawConnector : connectors) {
            BlockState rawState = world.getBlockState(rawConnector);
            BlockPos connector = normalize(rawConnector, rawState);
            BlockState state = world.getBlockState(connector);
            FloorConnector.Type type = FloorConnector.Type.fromBlockState(state);
            if (type == null) continue;
            if (type.vertical()) {
                verticalFloorMembershipCells(connector, geometry)
                        .forEach(floorCell -> result.putIfAbsent(floorCell, type));
            } else if (geometry.cellAt(connector).isPresent()) {
                result.putIfAbsent(connector.immutable(), type);
            }
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

    static List<BlockPos> verticalHandoffCandidates(Level world, BlockPos source) {
        return verticalColumn(world, source).stream()
                .flatMap(connector -> handoffs(connector).stream())
                .distinct()
                .toList();
    }

    static List<BlockPos> horizontalHandoffCandidates(Level world, BlockPos source) {
        BlockState sourceState = world.getBlockState(source);
        if (!isHorizontalBoundary(sourceState)) return List.of();

        BlockPos connector = normalize(source, sourceState);
        return Arrays.stream(HORIZONTAL)
                .map(connector::relative)
                .toList();
    }

    private static List<BlockPos> handoffs(BlockPos pos) {
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

    record VerticalConnection(Structure structure, StructureFloor floor) {
        VerticalConnection {
            Objects.requireNonNull(structure, "structure");
            Objects.requireNonNull(floor, "floor");
        }
    }
}
