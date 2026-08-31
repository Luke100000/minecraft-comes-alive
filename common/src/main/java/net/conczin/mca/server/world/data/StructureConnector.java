package net.conczin.mca.server.world.data;

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

    static boolean isHorizontalBoundary(BlockState state) {
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock;
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

    /** Doors are traversal boundaries only; unlike other connectors, they never own a Floor cell. */
    static boolean ownsFloorCell(BlockState state) {
        return isConnector(state) && !(state.getBlock() instanceof DoorBlock);
    }

    /** Associates connector positions with exact cells on the already selected semantic floor. */
    static Map<BlockPos, BlockPos> associatedFloorCells(
            Level world, Collection<BlockPos> connectors, Collection<FloorSurface.Cell> surfaceCells) {
        if (connectors.isEmpty() || surfaceCells.isEmpty()) return Map.of();

        Set<BlockPos> surfaceFeet = surfaceCells.stream()
                .map(FloorSurface.Cell::feet)
                .collect(java.util.stream.Collectors.toSet());
        LinkedHashMap<BlockPos, BlockPos> result = new LinkedHashMap<>();
        for (BlockPos rawConnector : connectors) {
            BlockState rawState = world.getBlockState(rawConnector);
            BlockPos connector = normalize(rawConnector, rawState);
            BlockState state = world.getBlockState(connector);
            if (!isConnector(state)) continue;

            if (isVertical(state)) {
                for (BlockPos handoff : handoffs(connector)) {
                    if (!surfaceFeet.contains(handoff)) continue;
                    result.putIfAbsent(
                            new BlockPos(connector.getX(), handoff.getY(), connector.getZ()), connector);
                }
                continue;
            }

            for (Direction direction : HORIZONTAL) {
                BlockPos side = connector.relative(direction);
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos landing = side.offset(0, dy, 0);
                    if (!surfaceFeet.contains(landing)) continue;
                    result.putIfAbsent(
                            new BlockPos(connector.getX(), landing.getY(), connector.getZ()), connector);
                }
            }
        }
        return Map.copyOf(result);
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
        if (!state.getFluidState().isEmpty()) return false;
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof TrapDoorBlock) return false;
        return state.getBlock() instanceof LadderBlock || state.isAir() || state.canBeReplaced()
                || state.getCollisionShape(world, pos).isEmpty();
    }
}
