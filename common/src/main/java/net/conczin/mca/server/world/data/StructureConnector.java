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

/** Single source of truth for connector ownership, Room boundaries and Floor interaction handoffs. */
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

    /** A decorative connector is Room-owned when its two sides already connect around it. */
    static boolean isRoomBoundary(Level world, Structure structure, StructureFloor floor, BlockPos connector) {
        BlockState state = world.getBlockState(connector);
        if (!isConnector(state)) return false;
        if (state.getBlock() instanceof LadderBlock) return true;
        Direction facing = horizontalFacing(state).orElse(null);
        if (facing == null) return true;
        BlockPos a = passageInColumn(world, structure, floor, connector.relative(facing));
        BlockPos b = passageInColumn(world, structure, floor, connector.relative(facing.getOpposite()));
        return a == null || b == null || !reachableWithoutConnectors(world, structure, floor, a, b);
    }

    private static boolean reachableWithoutConnectors(Level world, Structure structure, StructureFloor floor,
                                                      BlockPos start, BlockPos target) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (pos.equals(target)) return true;
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (next.getY() < floor.anchorY() || next.getY() >= floor.ceilingY()
                        || !seen.add(next) || !structure.containsPos(next)) continue;
                if (!isConnector(world.getBlockState(next)) && isPassageCell(world, next)) queue.addLast(next);
            }
        }
        return false;
    }

    private static BlockPos passageInColumn(Level world, Structure structure, StructureFloor floor, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = floor.anchorY(); y < floor.ceilingY(); y++) {
            cursor.set(pos.getX(), y, pos.getZ());
            if (structure.containsPos(cursor) && !isConnector(world.getBlockState(cursor))
                    && isPassageCell(world, cursor)) return cursor.immutable();
        }
        return null;
    }

    static void collectNearbyVertical(Level world, BlockPos pos, Set<BlockPos> result) {
        for (BlockPos candidate : handoffs(pos)) {
            if (isVertical(world.getBlockState(candidate))) result.add(candidate.immutable());
        }
        if (isVertical(world.getBlockState(pos))) result.add(pos.immutable());
    }

    static Optional<StructureFloor> resolveInteractionFloor(Level world, Structure structure, BlockPos pos) {
        return resolveInteraction(world, structure, pos).map(Interaction::floor).or(() -> structure.resolveFloor(pos));
    }

    static Optional<Interaction> resolveInteraction(Level world, Structure structure, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (isHorizontalBoundary(state)) {
            StructureFloor floor = structure.resolveFloor(pos).orElse(null);
            if (floor != null) return Optional.of(new Interaction(floor, horizontalLandings(world, structure, floor, pos)));
        }

        BlockPos seed = verticalSeed(world, structure, pos);
        if (seed == null) return Optional.empty();
        Map<StructureFloor, Set<BlockPos>> landings = verticalLandings(
                world, structure, floodWorldChain(world, structure, seed));
        StructureFloor floor = floorAtOrBelow(landings.keySet(), pos.getY());
        return floor == null ? Optional.empty() : Optional.of(new Interaction(floor, landings.get(floor)));
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

    private static Map<StructureFloor, Set<BlockPos>> verticalLandings(Level world, Structure structure,
                                                                       Collection<BlockPos> chain) {
        Map<StructureFloor, Set<BlockPos>> result = new LinkedHashMap<>();
        for (BlockPos connector : chain) {
            for (BlockPos candidate : handoffs(connector)) {
                StructureFloor floor = structure.resolveFloor(candidate).orElse(null);
                if (floor != null && !isVertical(world.getBlockState(candidate)) && isPassageCell(world, candidate)) {
                    result.computeIfAbsent(floor, ignored -> new LinkedHashSet<>()).add(candidate.immutable());
                }
            }
        }
        return result;
    }

    private static Set<BlockPos> horizontalLandings(Level world, Structure structure, StructureFloor floor,
                                                     BlockPos connector) {
        Direction facing = horizontalFacing(world.getBlockState(connector)).orElse(null);
        if (facing == null) return Set.of();
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
        for (Direction side : List.of(facing, facing.getOpposite())) {
            BlockPos passage = passageInColumn(world, structure, floor, connector.relative(side));
            if (passage != null) result.add(passage);
        }
        return Set.copyOf(result);
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

    private static StructureFloor floorAtOrBelow(Collection<StructureFloor> floors, int y) {
        return floors.stream()
                .filter(floor -> floor.anchorY() <= y)
                .sorted(Comparator.comparingInt(StructureFloor::anchorY).reversed()
                        .thenComparingInt(StructureFloor::id))
                .findFirst().orElse(null);
    }

    static boolean connectsDifferentFloor(Level world, Structure structure, StructureFloor floor, BlockPos connector) {
        if (!isVertical(world.getBlockState(connector))) return false;
        return verticalLandings(world, structure, floodWorldChain(world, structure, connector)).keySet().stream()
                .anyMatch(candidate -> candidate.id() != floor.id());
    }

    static boolean isPassageCell(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (isHorizontalBoundary(state)) return false;
        return state.getBlock() instanceof LadderBlock || state.isAir() || state.canBeReplaced()
                || state.getCollisionShape(world, pos).isEmpty();
    }

    /** Adds connector X/Z cells directly to the physical Floor band that owns them. */
    static List<StructureFloor> withOwnedFloorCells(Collection<BlockPos> connectorCells, List<StructureFloor> floors) {
        if (connectorCells.isEmpty()) return floors;
        List<StructureFloor> result = new ArrayList<>(floors.size());
        for (StructureFloor floor : floors) {
            Set<BlockPos> base = footprintCells(floor.region());
            LinkedHashSet<BlockPos> cells = new LinkedHashSet<>(base);
            for (BlockPos connector : connectorCells) {
                BlockPos cell = new BlockPos(connector.getX(), floor.anchorY(), connector.getZ());
                if (connector.getY() >= floor.anchorY() && connector.getY() < floor.ceilingY()
                        && (base.contains(cell) || Arrays.stream(HORIZONTAL)
                        .anyMatch(direction -> base.contains(cell.relative(direction))))) cells.add(cell);
            }
            result.add(floor.withGeometry(floor.anchorY(), floor.ceilingY(),
                    BuildingFloorRegion.fromFootprint(floor.anchorY(), List.copyOf(cells))));
        }
        return List.copyOf(result);
    }

    private static Set<BlockPos> footprintCells(BuildingFloorRegion region) {
        if (region == null) return Set.of();
        LinkedHashSet<BlockPos> cells = new LinkedHashSet<>();
        for (BuildingFloorRegion.Component component : region.components()) {
            if (component.spans().isEmpty()) {
                for (int z = component.minZ(); z <= component.maxZ(); z++)
                    for (int x = component.minX(); x <= component.maxX(); x++)
                        cells.add(new BlockPos(x, region.anchorY(), z));
            } else {
                for (BuildingFloorRegion.Span span : component.spans())
                    for (int x = span.minX(); x <= span.maxX(); x++)
                        cells.add(new BlockPos(x, region.anchorY(), span.z()));
            }
        }
        return cells;
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
            Map<StructureFloor, Set<BlockPos>> inside = verticalLandings(world, structure, chain);
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
            StructureFloor floor = floorAtOrBelow(inside.keySet(), out.getY());
            if (floor == null) continue;
            BlockPos in = inside.get(floor).stream().min(Comparator
                    .comparingInt((BlockPos pos) -> Math.abs(pos.getY() - out.getY()))
                    .thenComparingInt(Vec3i::getY).thenComparingInt(Vec3i::getX)
                    .thenComparingInt(Vec3i::getZ)).orElseThrow();
            result.add(new ExteriorLanding(in, out));
        }
        return Set.copyOf(result);
    }

    record Interaction(StructureFloor floor, Set<BlockPos> landingCells) {
        Interaction { landingCells = Set.copyOf(landingCells); }
    }

    record ExteriorLanding(BlockPos inside, BlockPos outside) {
    }
}
