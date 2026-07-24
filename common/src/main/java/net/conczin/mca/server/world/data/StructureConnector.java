package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/** Connector mechanics: classification, Floor-cell projection and downward Structure attachment. */
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
        return state.getBlock() instanceof LadderBlock || state.getBlock() instanceof TrapDoorBlock;
    }

    static boolean isGroundEntrance(BlockState state) {
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock;
    }

    static Optional<Direction> horizontalFacing(BlockState state) {
        if (state.getBlock() instanceof DoorBlock) return Optional.of(state.getValue(DoorBlock.FACING));
        if (state.getBlock() instanceof FenceGateBlock) return Optional.of(state.getValue(FenceGateBlock.FACING));
        if (state.getBlock() instanceof TrapDoorBlock && state.getValue(TrapDoorBlock.OPEN)) {
            return Optional.of(state.getValue(TrapDoorBlock.FACING));
        }
        return Optional.empty();
    }

    /** Projects connector columns onto ordinary Floor Y levels before storey detection. */
    static Set<BuildingFloorRegionDetector.FloorCell> associatedFloorCells(
            Level world, Collection<BlockPos> connectors,
            Collection<BuildingFloorRegionDetector.FloorCell> ordinaryFloorCells) {
        if (connectors.isEmpty() || ordinaryFloorCells.isEmpty()) return Set.of();
        Set<BlockPos> ordinary = ordinaryFloorCells.stream()
                .map(cell -> new BlockPos(cell.x(), cell.y(), cell.z()))
                .collect(java.util.stream.Collectors.toSet());
        LinkedHashSet<BuildingFloorRegionDetector.FloorCell> result = new LinkedHashSet<>();
        for (BlockPos connector : connectors) {
            BlockState state = world.getBlockState(connector);
            if (!isConnector(state)) continue;
            if (isVertical(state)) {
                for (BlockPos handoff : handoffs(connector)) {
                    if (ordinary.contains(handoff)) {
                        result.add(new BuildingFloorRegionDetector.FloorCell(
                                connector.getX(), handoff.getY(), connector.getZ()));
                    }
                }
            } else {
                for (Direction direction : HORIZONTAL) {
                    BlockPos side = connector.relative(direction);
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos landing = side.offset(0, dy, 0);
                        if (ordinary.contains(landing)) {
                            result.add(new BuildingFloorRegionDetector.FloorCell(
                                    connector.getX(), landing.getY(), connector.getZ()));
                        }
                    }
                }
            }
        }
        return Set.copyOf(result);
    }

    static boolean attachesToStructure(Level world, Structure structure, BlockPos pos) {
        if (structure.containsPos(pos)) return true;
        BlockPos connector = verticalSeed(world, structure, pos);
        return connector != null && structure.getFloors().stream()
                .anyMatch(floor -> floor.contains(connector.getX(), connector.getZ()));
    }

    /** Current Y first, then downward only. */
    private static BlockPos verticalSeed(Level world, Structure structure, BlockPos pos) {
        int minY = structure.getFloors().stream()
                .mapToInt(StructureFloor::anchorY).min().orElse(pos.getY()) - 2;
        for (int y = pos.getY(); y >= minY; y--) {
            BlockPos level = new BlockPos(pos.getX(), y, pos.getZ());
            if (isVertical(world.getBlockState(level))) return level;
            for (Direction direction : HORIZONTAL) {
                BlockPos side = level.relative(direction);
                if (isVertical(world.getBlockState(side))) return side;
            }
        }
        return null;
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

    static boolean isPassageCell(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof TrapDoorBlock) return false;
        return state.getBlock() instanceof LadderBlock || state.isAir() || state.canBeReplaced()
                || state.getCollisionShape(world, pos).isEmpty();
    }
}
