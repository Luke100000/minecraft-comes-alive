package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Shared physical connector rules used by Structure traversal, Floor geometry and Room entrances. */
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

    static boolean isVertical(BlockState state) {
        return state.getBlock() instanceof LadderBlock
                || state.getBlock() instanceof TrapDoorBlock;
    }

    static boolean isHorizontalBoundary(BlockState state) {
        return state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof TrapDoorBlock;
    }

    static boolean isGroundEntrance(BlockState state) {
        return state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof FenceGateBlock;
    }

    static Optional<Direction> horizontalFacing(BlockState state) {
        if (state.getBlock() instanceof DoorBlock) {
            return Optional.of(state.getValue(DoorBlock.FACING));
        }
        if (state.getBlock() instanceof FenceGateBlock) {
            return Optional.of(state.getValue(FenceGateBlock.FACING));
        }
        if (state.getBlock() instanceof TrapDoorBlock && state.getValue(TrapDoorBlock.OPEN)) {
            return Optional.of(state.getValue(TrapDoorBlock.FACING));
        }
        return Optional.empty();
    }

    static void collectNearbyVertical(Level world, BlockPos current, Set<BlockPos> verticalConnectors) {
        addVertical(world, current, verticalConnectors);
        addVertical(world, current.above(), verticalConnectors);
        addVertical(world, current.below(), verticalConnectors);
        for (Direction direction : HORIZONTAL) {
            BlockPos horizontal = current.relative(direction);
            for (int dy = -1; dy <= 1; dy++) {
                addVertical(world, horizontal.offset(0, dy, 0), verticalConnectors);
            }
        }
    }

    private static void addVertical(Level world, BlockPos pos, Set<BlockPos> verticalConnectors) {
        if (isVertical(world.getBlockState(pos))) {
            verticalConnectors.add(pos.immutable());
        }
    }

    static boolean connectsDifferentFloor(Level world,
                                           Structure structure,
                                           StructureFloor floor,
                                           BlockPos connector) {
        if (!isVertical(world.getBlockState(connector))) {
            return false;
        }
        return scanDirection(world, structure, floor, connector, Direction.UP)
                || scanDirection(world, structure, floor, connector, Direction.DOWN);
    }

    private static boolean scanDirection(Level world,
                                         Structure structure,
                                         StructureFloor floor,
                                         BlockPos connector,
                                         Direction direction) {
        int minY = structure.getFloors().stream()
                .mapToInt(StructureFloor::anchorY).min().orElse(floor.anchorY()) - 1;
        int maxY = structure.getFloors().stream()
                .mapToInt(StructureFloor::ceilingY).max().orElse(floor.ceilingY()) + 1;
        BlockPos cursor = connector;
        while (cursor.getY() >= minY && cursor.getY() <= maxY) {
            if (touchesDifferentFloorLanding(world, structure, floor, cursor)) {
                return true;
            }
            BlockState state = world.getBlockState(cursor);
            if (!isVertical(state)) {
                return isDifferentFloorLanding(world, structure, floor, cursor);
            }
            if (structure.resolveFloor(cursor)
                    .filter(candidate -> candidate.id() != floor.id())
                    .isPresent()) {
                return true;
            }
            cursor = cursor.relative(direction);
        }
        return false;
    }

    private static boolean touchesDifferentFloorLanding(Level world,
                                                        Structure structure,
                                                        StructureFloor floor,
                                                        BlockPos connector) {
        for (Direction direction : HORIZONTAL) {
            BlockPos horizontal = connector.relative(direction);
            for (int dy = -1; dy <= 1; dy++) {
                if (isDifferentFloorLanding(world, structure, floor, horizontal.offset(0, dy, 0))) {
                    return true;
                }
            }
        }
        return isDifferentFloorLanding(world, structure, floor, connector.above())
                || isDifferentFloorLanding(world, structure, floor, connector.below());
    }

    private static boolean isDifferentFloorLanding(Level world,
                                                   Structure structure,
                                                   StructureFloor floor,
                                                   BlockPos candidate) {
        return structure.containsPos(candidate)
                && isPassageCell(world, candidate)
                && structure.resolveFloor(candidate)
                .filter(candidateFloor -> candidateFloor.id() != floor.id())
                .isPresent();
    }

    static boolean isPassageCell(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (isHorizontalBoundary(state)) {
            return false;
        }
        return state.getBlock() instanceof LadderBlock
                || state.isAir()
                || state.canBeReplaced()
                || state.getCollisionShape(world, pos).isEmpty();
    }

    static List<StructureFloor> withOwnedFloorCells(Collection<BlockPos> connectorCells,
                                                    List<StructureFloor> floors) {
        List<StructureFloorConnectorGeometry.Connector> connectors = connectorCells.stream()
                .map(pos -> new StructureFloorConnectorGeometry.Connector(pos.getX(), pos.getY(), pos.getZ()))
                .toList();
        if (connectors.isEmpty()) {
            return floors;
        }

        java.util.ArrayList<StructureFloor> expanded = new java.util.ArrayList<>(floors.size());
        for (StructureFloor floor : floors) {
            Set<StructureFloorConnectorGeometry.Cell> baseCells = footprintCells(floor.region()).stream()
                    .map(pos -> new StructureFloorConnectorGeometry.Cell(pos.getX(), pos.getZ()))
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            Set<StructureFloorConnectorGeometry.Cell> floorCells = StructureFloorConnectorGeometry.expand(
                    baseCells, connectors, floor.anchorY(), floor.ceilingY());
            BuildingFloorRegion region = BuildingFloorRegion.fromFootprint(
                    floor.anchorY(),
                    floorCells.stream()
                            .map(cell -> new BlockPos(cell.x(), floor.anchorY(), cell.z()))
                            .toList());
            expanded.add(floor.withGeometry(floor.anchorY(), floor.ceilingY(), region));
        }
        return List.copyOf(expanded);
    }

    private static Set<BlockPos> footprintCells(BuildingFloorRegion region) {
        if (region == null) {
            return Set.of();
        }
        LinkedHashSet<BlockPos> cells = new LinkedHashSet<>();
        for (BuildingFloorRegion.Component component : region.components()) {
            if (component.spans().isEmpty()) {
                for (int z = component.minZ(); z <= component.maxZ(); z++) {
                    for (int x = component.minX(); x <= component.maxX(); x++) {
                        cells.add(new BlockPos(x, region.anchorY(), z));
                    }
                }
                continue;
            }
            for (BuildingFloorRegion.Span span : component.spans()) {
                for (int x = span.minX(); x <= span.maxX(); x++) {
                    cells.add(new BlockPos(x, region.anchorY(), span.z()));
                }
            }
        }
        return cells;
    }
}
