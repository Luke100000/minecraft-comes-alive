package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.function.Predicate;

/** Connector mechanics: classification, Floor-cell projection, vertical chains and Structure attachment. */
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

    static boolean isAssociatedWithFloor(Structure structure, BlockState state, BlockPos connector, StructureFloor floor) {
        if (!isConnector(state)) return false;
        boolean sameFloor = structure.resolveFloor(connector.getY())
                .map(candidate -> candidate.id() == floor.id()).orElse(false);
        if (sameFloor) return true;
        return isVertical(state) && structure.resolveFloor(connector.getY() + 1)
                .map(candidate -> candidate.id() == floor.id()).orElse(false);
    }

    static boolean isHorizontalBoundary(BlockState state) {
        return state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof TrapDoorBlock;
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

    /** Projects connector columns onto the ordinary Floor Y levels they attach to before Floor detection. */
    static Set<BuildingFloorRegionDetector.FloorCell> associatedFloorCells(
            Level world,
            Collection<BlockPos> connectors,
            Collection<BuildingFloorRegionDetector.FloorCell> ordinaryFloorCells) {
        if (connectors.isEmpty() || ordinaryFloorCells.isEmpty()) return Set.of();

        Set<BlockPos> ordinary = ordinaryFloorCells.stream()
                .map(cell -> new BlockPos(cell.x(), cell.y(), cell.z()))
                .collect(java.util.stream.Collectors.toSet());
        LinkedHashSet<BuildingFloorRegionDetector.FloorCell> result = new LinkedHashSet<>();

        for (BlockPos connector : connectors) {
            BlockState state = world.getBlockState(connector);
            if (!isConnector(state)) continue;

            if (!isVertical(state)) {
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
                continue;
            }

            for (BlockPos handoff : handoffs(connector)) {
                if (ordinary.contains(handoff)) {
                    result.add(new BuildingFloorRegionDetector.FloorCell(
                            connector.getX(), handoff.getY(), connector.getZ()));
                }
            }
        }

        return Set.copyOf(result);
    }

    static boolean attachesToStructure(Level world, Structure structure, BlockPos pos) {
        if (structure.containsPos(pos)) return true;
        BlockPos seed = verticalSeed(world, structure, pos);
        if (seed == null) return false;
        return floodWorldChain(world, structure, seed).stream()
                .flatMap(connector -> handoffs(connector).stream())
                .anyMatch(structure::containsPos);
    }

    /** Gravity-like lookup: current Y first, then downward only. */
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

    private static Set<BlockPos> floodWorldChain(Level world, Structure structure, BlockPos seed) {
        int minY = structure.getFloors().stream().mapToInt(StructureFloor::anchorY).min().orElse(seed.getY()) - 2;
        int maxY = structure.getFloors().stream().mapToInt(StructureFloor::ceilingY).max().orElse(seed.getY()) + 2;
        return flood(seed, next -> next.getY() >= minY && next.getY() <= maxY
                && isVertical(world.getBlockState(next)));
    }

    private static Set<BlockPos> floodKnownChain(BlockPos seed, Set<BlockPos> remaining) {
        return flood(seed, remaining::remove);
    }

    private static Set<BlockPos> flood(BlockPos seed, Predicate<BlockPos> accept) {
        LinkedHashSet<BlockPos> chain = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        chain.add(seed.immutable());
        queue.add(seed.immutable());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!chain.contains(next) && accept.test(next)) {
                    chain.add(next.immutable());
                    queue.addLast(next.immutable());
                }
            }
        }
        return Set.copyOf(chain);
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

    static boolean connectsDifferentFloor(Level world, Structure structure, StructureFloor floor, BlockPos connector) {
        if (!isVertical(world.getBlockState(connector))) return false;
        return floodWorldChain(world, structure, connector).stream()
                .flatMap(cell -> handoffs(cell).stream())
                .map(structure::resolvePhysicalFloor)
                .flatMap(optional -> optional.stream())
                .anyMatch(candidate -> candidate.id() != floor.id());
    }

    static boolean isPassageCell(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (isHorizontalBoundary(state)) return false;
        return state.getBlock() instanceof LadderBlock || state.isAir() || state.canBeReplaced()
                || state.getCollisionShape(world, pos).isEmpty();
    }

    /** Returns actual Structure landings for vertical chains that also touch exterior supported space. */
    static Set<ExteriorLanding> findVerticalExteriorEntrances(Level world, Collection<BlockPos> connectorCells,
                                                               Structure structure, Set<BlockPos> interior,
                                                               Predicate<BlockPos> exteriorLike) {
        Set<BlockPos> remaining = connectorCells.stream().filter(pos -> isVertical(world.getBlockState(pos)))
                .map(BlockPos::immutable)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<ExteriorLanding> result = new LinkedHashSet<>();
        while (!remaining.isEmpty()) {
            BlockPos seed = remaining.iterator().next();
            remaining.remove(seed);
            Set<BlockPos> chain = floodKnownChain(seed, remaining);
            List<BlockPos> inside = chain.stream().flatMap(pos -> handoffs(pos).stream())
                    .filter(interior::contains)
                    .filter(pos -> isPassageCell(world, pos))
                    .distinct()
                    .toList();
            List<BlockPos> outside = chain.stream().flatMap(pos -> handoffs(pos).stream())
                    .filter(pos -> !interior.contains(pos) && isPassageCell(world, pos))
                    .filter(pos -> StructureScanner.isSupported(world, pos.getX(), pos.getY(), pos.getZ()))
                    .filter(exteriorLike)
                    .sorted(Comparator.comparingInt((BlockPos pos) -> pos.getY())
                    .thenComparingInt(Vec3i::getX)
                    .thenComparingInt(Vec3i::getZ))
                    .toList();
            if (inside.isEmpty() || outside.isEmpty()) continue;
            BlockPos out = outside.getFirst();
            Set<StructureFloor> insideFloors = inside.stream()
                    .map(structure::resolvePhysicalFloor)
                    .flatMap(optional -> optional.stream())
                    .collect(java.util.stream.Collectors.toSet());
            StructureFloor floor = insideFloors.stream()
                    .filter(candidate -> candidate.anchorY() <= out.getY())
                    .max(Comparator.comparingInt(StructureFloor::anchorY))
                    .orElse(null);
            if (floor == null) continue;
            BlockPos in = inside.stream()
                    .filter(pos -> structure.resolvePhysicalFloor(pos)
                            .map(candidate -> candidate.id() == floor.id()).orElse(false))
                    .min(Comparator.comparingInt((BlockPos pos) -> Math.abs(pos.getY() - out.getY()))
                            .thenComparingInt(Vec3i::getY).thenComparingInt(Vec3i::getX)
                            .thenComparingInt(Vec3i::getZ))
                    .orElse(null);
            if (in != null) result.add(new ExteriorLanding(in, out));
        }
        return Set.copyOf(result);
    }

    record ExteriorLanding(BlockPos inside, BlockPos outside) {
    }
}
