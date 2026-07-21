package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.stream.Collectors;

/** Bounded single-Floor Room component scan. It never creates split Rooms or changes Floor identity. */
final class BuildingRoomScanner {
    private static final int MIN_INTERIOR_AREA = 4;
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private BuildingRoomScanner() {
    }

    static Result scan(Level world,
                       BlockPos source,
                       Set<BlockPos> blocked,
                       int maxSize,
                       int maxRadius,
                       Structure structure) {
        StructureFloor floor = structure == null ? null : structure.resolveFloor(source.getY()).orElse(null);
        if (floor == null) {
            return Result.failure(Status.TOO_SMALL, source);
        }
        return scan(world, source, blocked, maxSize, maxRadius, structure, floor);
    }

    static Result scan(Level world,
                       BlockPos source,
                       Set<BlockPos> blocked,
                       int maxSize,
                       int maxRadius,
                       Structure structure,
                       StructureFloor floor) {
        if (structure == null || floor == null || floor.region() == null) {
            return Result.failure(Status.TOO_SMALL, source);
        }

        Set<BlockPos> connectorBlocks = connectorBlocks(world, structure);
        Set<BlockPos> floorConnectorBlocks = connectorBlocks.stream()
                .filter(pos -> StructureConnector.isAssociatedWithFloor(
                        structure, world.getBlockState(pos), pos, floor))
                .collect(Collectors.toUnmodifiableSet());
        Set<BlockPos> connectorFloorCells = connectorFloorCells(floor, floorConnectorBlocks);
        Set<Long> ordinaryCells = floor.region().cells().stream()
                .filter(cell -> !connectorFloorCells.contains(cell))
                .map(cell -> packColumn(cell.getX(), cell.getZ()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Integer> componentByCell = findComponents(ordinaryCells);
        Integer selectedComponent = findSelectedComponent(source, floor, connectorFloorCells, componentByCell);
        if (selectedComponent == null) {
            return Result.failure(Status.TOO_SMALL, source);
        }

        LinkedHashSet<BlockPos> footprint = componentByCell.entrySet().stream()
                .filter(entry -> entry.getValue().equals(selectedComponent))
                .map(Map.Entry::getKey)
                .map(column -> new BlockPos(unpackX(column), floor.anchorY(), unpackZ(column)))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (BlockPos connectorFloorCell : connectorFloorCells) {
            Set<Integer> ids = adjacentComponentIds(connectorFloorCell, componentByCell);
            if (ids.size() == 1 && ids.contains(selectedComponent)) {
                footprint.add(connectorFloorCell);
            }
        }

        for (BlockPos cell : footprint) {
            if (Math.abs(cell.getX() - source.getX()) + Math.abs(cell.getZ() - source.getZ()) >= maxRadius) {
                return Result.failure(Status.SIZE_LIMIT, source);
            }
        }
        if (footprint.size() > maxSize) {
            return Result.failure(Status.BLOCK_LIMIT, source);
        }

        Set<BlockPos> blockedCells = blocked == null ? Set.of() : blocked;
        if (footprint.stream().anyMatch(blockedCells::contains)) {
            return Result.failure(Status.OVERLAP, source);
        }
        if (footprint.size() < MIN_INTERIOR_AREA) {
            return Result.failure(Status.TOO_SMALL, source);
        }

        boolean hasEntrance = hasHorizontalEntrance(
                world, connectorFloorCells, floorConnectorBlocks, selectedComponent, componentByCell)
                || hasVerticalEntrance(
                world, structure, floor, connectorFloorCells, floorConnectorBlocks, selectedComponent, componentByCell);

        BlockPos seed = nearestSelectedCell(source, floor, selectedComponent, componentByCell);
        Set<BlockPos> poi = collectPoiCells(world, footprint, floor.anchorY(), floor.ceilingY());
        int minX = footprint.stream().mapToInt(BlockPos::getX).min().orElse(source.getX());
        int minZ = footprint.stream().mapToInt(BlockPos::getZ).min().orElse(source.getZ());
        int maxX = footprint.stream().mapToInt(BlockPos::getX).max().orElse(source.getX());
        int maxZ = footprint.stream().mapToInt(BlockPos::getZ).max().orElse(source.getZ());
        return new Result(Status.SUCCESS, seed, floor.id(), floor.anchorY(), Set.copyOf(footprint), poi,
                hasEntrance, new BlockPos(minX, floor.anchorY(), minZ),
                new BlockPos(maxX, Math.max(floor.anchorY(), floor.ceilingY() - 1), maxZ));
    }

    private static Set<BlockPos> connectorBlocks(Level world, Structure structure) {
        return structure.getVolumeSlices().stream()
                .flatMap(slice -> slice.cells().stream())
                .filter(pos -> StructureConnector.isConnector(world.getBlockState(pos)))
                .map(BlockPos::immutable)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Canonical connector Floor cells are the persisted Floor cells sharing a connector column. */
    private static Set<BlockPos> connectorFloorCells(StructureFloor floor, Set<BlockPos> connectorBlocks) {
        Set<Long> connectorColumns = connectorBlocks.stream()
                .map(pos -> packColumn(pos.getX(), pos.getZ()))
                .collect(Collectors.toSet());
        return floor.region().cells().stream()
                .filter(cell -> connectorColumns.contains(packColumn(cell.getX(), cell.getZ())))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Map<Long, Integer> findComponents(Set<Long> cells) {
        HashSet<Long> remaining = new HashSet<>(cells);
        HashMap<Long, Integer> componentByCell = new HashMap<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        int componentId = 0;

        while (!remaining.isEmpty()) {
            long seed = remaining.iterator().next();
            remaining.remove(seed);
            componentByCell.put(seed, componentId);
            queue.add(seed);

            while (!queue.isEmpty()) {
                long packed = queue.removeFirst();
                int x = unpackX(packed);
                int z = unpackZ(packed);
                for (Direction direction : HORIZONTAL) {
                    long next = packColumn(x + direction.getStepX(), z + direction.getStepZ());
                    if (remaining.remove(next)) {
                        componentByCell.put(next, componentId);
                        queue.addLast(next);
                    }
                }
            }
            componentId++;
        }
        return Map.copyOf(componentByCell);
    }

    private static Integer findSelectedComponent(BlockPos source,
                                                 StructureFloor floor,
                                                 Set<BlockPos> connectorFloorCells,
                                                 Map<Long, Integer> componentByCell) {
        long sourceColumn = packColumn(source.getX(), source.getZ());
        Integer direct = componentByCell.get(sourceColumn);
        if (direct != null) return direct;

        BlockPos sourceFloorCell = new BlockPos(source.getX(), floor.anchorY(), source.getZ());
        if (connectorFloorCells.contains(sourceFloorCell)) {
            Set<Integer> adjacent = adjacentComponentIds(sourceFloorCell, componentByCell);
            if (adjacent.size() == 1) return adjacent.iterator().next();
        }

        for (Direction direction : HORIZONTAL) {
            Integer nearby = componentByCell.get(packColumn(
                    source.getX() + direction.getStepX(), source.getZ() + direction.getStepZ()));
            if (nearby != null) return nearby;
        }
        return null;
    }

    private static BlockPos nearestSelectedCell(BlockPos source,
                                                StructureFloor floor,
                                                int selectedComponent,
                                                Map<Long, Integer> componentByCell) {
        return componentByCell.entrySet().stream()
                .filter(entry -> entry.getValue() == selectedComponent)
                .map(Map.Entry::getKey)
                .map(column -> new BlockPos(unpackX(column), floor.anchorY(), unpackZ(column)))
                .min(Comparator.comparingInt((BlockPos cell) ->
                                Math.abs(cell.getX() - source.getX()) + Math.abs(cell.getZ() - source.getZ()))
                        .thenComparingInt(cell -> cell.getX())
                        .thenComparingInt(cell -> cell.getZ()))
                .orElse(new BlockPos(source.getX(), floor.anchorY(), source.getZ()));
    }

    private static Set<Integer> adjacentComponentIds(BlockPos connectorFloorCell,
                                                     Map<Long, Integer> componentByCell) {
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        for (Direction direction : HORIZONTAL) {
            Integer id = componentByCell.get(packColumn(
                    connectorFloorCell.getX() + direction.getStepX(),
                    connectorFloorCell.getZ() + direction.getStepZ()));
            if (id != null) ids.add(id);
        }
        return Set.copyOf(ids);
    }

    private static boolean hasHorizontalEntrance(Level world,
                                                 Set<BlockPos> connectorFloorCells,
                                                 Set<BlockPos> connectorBlocks,
                                                 int selectedComponent,
                                                 Map<Long, Integer> componentByCell) {
        for (BlockPos floorCell : connectorFloorCells) {
            BlockPos connector = connectorBlocks.stream()
                    .filter(pos -> pos.getX() == floorCell.getX() && pos.getZ() == floorCell.getZ())
                    .filter(pos -> StructureConnector.horizontalFacing(world.getBlockState(pos)).isPresent())
                    .min(Comparator.comparingInt(pos -> Math.abs(pos.getY() - floorCell.getY())))
                    .orElse(null);
            if (connector == null) continue;
            BlockState state = world.getBlockState(connector);
            Direction facing = StructureConnector.horizontalFacing(state).orElse(null);
            if (facing == null) continue;

            Integer first = componentByCell.get(packColumn(
                    floorCell.getX() + facing.getStepX(), floorCell.getZ() + facing.getStepZ()));
            Integer second = componentByCell.get(packColumn(
                    floorCell.getX() - facing.getStepX(), floorCell.getZ() - facing.getStepZ()));
            boolean firstSelected = Objects.equals(first, selectedComponent);
            boolean secondSelected = Objects.equals(second, selectedComponent);
            if (firstSelected != secondSelected) return true;
        }
        return false;
    }

    private static boolean hasVerticalEntrance(Level world,
                                               Structure structure,
                                               StructureFloor floor,
                                               Set<BlockPos> connectorFloorCells,
                                               Set<BlockPos> connectorBlocks,
                                               int selectedComponent,
                                               Map<Long, Integer> componentByCell) {
        Set<Long> selectedConnectorColumns = connectorFloorCells.stream()
                .filter(cell -> adjacentComponentIds(cell, componentByCell).contains(selectedComponent))
                .map(cell -> packColumn(cell.getX(), cell.getZ()))
                .collect(Collectors.toSet());
        return connectorBlocks.stream()
                .filter(pos -> selectedConnectorColumns.contains(packColumn(pos.getX(), pos.getZ())))
                .filter(pos -> StructureConnector.isVertical(world.getBlockState(pos)))
                .anyMatch(pos -> StructureConnector.connectsDifferentFloor(world, structure, floor, pos));
    }

    private static Set<BlockPos> collectPoiCells(Level world,
                                                  Set<BlockPos> footprint,
                                                  int floorY,
                                                  int ceilingY) {
        LinkedHashSet<BlockPos> poi = new LinkedHashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (BlockPos cell : footprint) {
            cursor.set(cell.getX(), floorY - 1, cell.getZ());
            if (!world.getBlockState(cursor).isAir()) poi.add(cursor.immutable());
            for (int y = floorY; y < ceilingY; y++) {
                cursor.set(cell.getX(), y, cell.getZ());
                if (!world.getBlockState(cursor).isAir()) poi.add(cursor.immutable());
            }
        }
        return Set.copyOf(poi);
    }

    private static long packColumn(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }

    enum Status {
        SUCCESS,
        OVERLAP,
        BLOCK_LIMIT,
        SIZE_LIMIT,
        TOO_SMALL
    }

    record Result(Status status,
                  BlockPos seed,
                  int floorId,
                  int floorY,
                  Set<BlockPos> footprintCells,
                  Set<BlockPos> poiCells,
                  boolean hasEntrance,
                  BlockPos min,
                  BlockPos max) {
        Result {
            footprintCells = Set.copyOf(footprintCells);
            poiCells = Set.copyOf(poiCells);
        }

        static Result failure(Status status, BlockPos seed) {
            return new Result(status, seed, -1, seed.getY(), Set.of(), Set.of(), false, seed, seed);
        }
    }
}
