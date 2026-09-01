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

    static boolean isVerticalInteraction(Level world, BlockPos pos) {
        return !verticalColumn(world, pos).isEmpty();
    }

    static List<StructureFloor.ConnectorMarker> floorMarkers(Level world, FloorSurface surface) {
        if (world == null || surface == null || surface.connectorByFloorCell().isEmpty()) return List.of();
        LinkedHashSet<StructureFloor.ConnectorMarker> markers = new LinkedHashSet<>();
        int anchorY = surface.anchorY();
        for (Map.Entry<BlockPos, BlockPos> entry : surface.connectorByFloorCell().entrySet()) {
            StructureFloor.ConnectorType type = connectorType(world.getBlockState(entry.getValue()));
            if (type == null) continue;
            BlockPos floorCell = entry.getKey();
            markers.add(new StructureFloor.ConnectorMarker(
                    new BlockPos(floorCell.getX(), anchorY, floorCell.getZ()), type));
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

            if (isVertical(world, connector)) {
                for (BlockPos handoff : handoffs(connector)) {
                    if (!surfaceFeet.contains(handoff)) continue;
                    result.putIfAbsent(
                            new BlockPos(connector.getX(), handoff.getY(), connector.getZ()), connector);
                }
                continue;
            }

            if (!isHorizontalBoundary(state)) continue;

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

    /** Returns the vertical connector column for an occupied connector or its immediate open top-exit cell. */
    private static List<BlockPos> verticalColumn(Level world, BlockPos pos) {
        BlockPos connector = verticalInteractionConnector(world, pos);
        return connector == null ? List.of() : verticalColumnFromConnector(world, connector);
    }

    private static BlockPos verticalInteractionConnector(Level world, BlockPos pos) {
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

    /**
     * Projects an occupied vertical connector onto one registered Floor, but only while the
     * interaction Y belongs to that Floor's storey band. The whole connected column is used only
     * to prove/find the handoff; it never changes the player's Y to the column bottom.
     */
    static BlockPos resolveVerticalFloorCell(Level world, StructureFloor floor, BlockPos source) {
        if (source.getY() < floor.anchorY() - 1 || source.getY() >= floor.ceilingY()) return null;
        return verticalHandoffCandidates(world, source).stream()
                .filter(candidate -> floor.contains(candidate.getX(), candidate.getZ()))
                .min(Comparator
                        .comparingInt((BlockPos candidate) -> Math.abs(candidate.getY() - floor.anchorY()))
                        .thenComparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getZ))
                .map(candidate -> new BlockPos(candidate.getX(), floor.anchorY(), candidate.getZ()))
                .orElse(null);
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
        FloorCeilingResolver ceilings = new FloorCeilingResolver(world);
        List<BlockPos> candidates = verticalHandoffCandidates(world, source).stream()
                .filter(candidate -> SelectedFloorScanner.inspectSurfaceCell(world, candidate, ceilings).isPresent())
                .sorted(Comparator
                        .comparingInt((BlockPos candidate) -> Math.abs(candidate.getY() - source.getY()))
                        .thenComparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getZ))
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
}
