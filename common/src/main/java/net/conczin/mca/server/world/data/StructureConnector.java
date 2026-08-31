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

    /** Vertical connector interactions always resolve from the bottom of the connected column. */
    static Optional<BlockPos> bottomVerticalConnector(Level world, BlockPos pos) {
        BlockPos connector = verticalConnectorAt(world, pos);
        if (connector == null) return Optional.empty();

        while (isVertical(world.getBlockState(connector.below()))) {
            connector = connector.below();
        }
        return Optional.of(connector.immutable());
    }

    private static BlockPos verticalConnectorAt(Level world, BlockPos pos) {
        if (isVertical(world.getBlockState(pos))) return pos;
        for (Direction direction : HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            if (isVertical(world.getBlockState(side))) return side;
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
        if (!state.getFluidState().isEmpty() || isConnector(state)) return false;
        return state.isAir() || state.canBeReplaced()
                || state.getCollisionShape(world, pos).isEmpty();
    }

    static BlockPos resolveFloorCell(Level world,
                                     Structure structure,
                                     StructureFloor floor,
                                     BlockPos pos) {
        BlockPos connector = bottomVerticalConnector(world, pos).orElse(null);
        if (connector != null) {
            BlockPos handoff = floorHandoff(floor, connector);
            if (handoff != null) {
                return new BlockPos(handoff.getX(), floor.anchorY(), handoff.getZ());
            }
        }

        BlockState state = world.getBlockState(pos);
        if (isConnector(state)) {
            BlockPos handoff = floorHandoff(floor, normalize(pos, state));
            if (handoff != null) {
                return new BlockPos(handoff.getX(), floor.anchorY(), handoff.getZ());
            }
        }

        if (!isPassageCell(world, pos) || !structure.containsEnvelope(pos)) {
            return null;
        }
        for (Direction direction : HORIZONTAL) {
            int x = pos.getX() + direction.getStepX();
            int z = pos.getZ() + direction.getStepZ();
            if (floor.contains(x, z)) return new BlockPos(x, floor.anchorY(), z);
        }
        return null;
    }

    private static BlockPos floorHandoff(StructureFloor floor, BlockPos connector) {
        return handoffs(connector).stream()
                .filter(candidate -> candidate.getY() >= floor.anchorY()
                        && candidate.getY() < floor.ceilingY())
                .filter(candidate -> floor.contains(candidate.getX(), candidate.getZ()))
                .findFirst().orElse(null);
    }

    static Optional<FloorHandoff> resolveVerticalFloorHandoff(
            Level world, BlockPos source, Config config) {
        BlockPos connector = bottomVerticalConnector(world, source).orElse(null);
        if (connector == null) return Optional.empty();

        FloorHandoff selected = null;
        Set<BlockPos> selectedFloor = null;
        for (Direction direction : HORIZONTAL) {
            BlockPos candidate = connector.relative(direction);
            SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(
                    world, candidate, config.maxBuildingSize, config.maxBuildingRadius);
            if (scan.result() != Building.validationResult.SUCCESS || scan.surface() == null) continue;

            Set<BlockPos> candidateFloor = scan.surface().projectedCells();
            if (selected == null) {
                selected = new FloorHandoff(candidate.immutable(), scan.surface());
                selectedFloor = candidateFloor;
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
}
