package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Produces geometry candidates for configured Room POIs after topology has been decided. */
final class RoomPoiEvidence {
    private RoomPoiEvidence() {
    }

    static Set<BlockPos> candidates(Collection<RoomPartitioner.Component> components,
                                    RoomPartitioner.Component component) {
        Set<BlockPos> result = new HashSet<>();
        index(components).forEachCandidate(component, pos -> result.add(pos.immutable()));
        return Set.copyOf(result);
    }

    static Index index(Collection<RoomPartitioner.Component> components) {
        return Index.create(components);
    }

    private static boolean overlapsPoiInterval(FloorGeometry.Cell sourceCell,
                                               FloorGeometry.Cell candidate) {
        int sourceMinY = sourceCell.feet().getY() - 1;
        int sourceMaxY = sourceCell.ceilingY();
        return candidate.feet().getY() - 1 < sourceMaxY
                && sourceMinY < candidate.ceilingY();
    }

    private record Column(int x, int z) {
    }

    private record IndexedCell(int componentIndex, FloorGeometry.Cell cell) {
    }

    /** Index the complete partition for perimeter ownership; bulk callers can reuse it. */
    static record Index(
            List<RoomPartitioner.Component> components,
            Map<Column, List<IndexedCell>> cellsByColumn,
            int[] ownerRanks,
            Map<RoomPartitioner.Component, Integer> componentIndices) {

        private static Index create(Collection<RoomPartitioner.Component> components) {
            List<RoomPartitioner.Component> orderedComponents = new ArrayList<>(components.size());
            Map<Column, List<IndexedCell>> cellsByColumn = new HashMap<>();
            Map<RoomPartitioner.Component, Integer> componentIndices = new IdentityHashMap<>();
            int componentIndex = 0;
            for (RoomPartitioner.Component candidate : components) {
                orderedComponents.add(candidate);
                componentIndices.put(candidate, componentIndex);
                for (FloorGeometry.Cell cell : candidate.cells()) {
                    cellsByColumn.computeIfAbsent(column(cell), ignored -> new ArrayList<>())
                            .add(new IndexedCell(componentIndex, cell));
                }
                componentIndex++;
            }
            List<Integer> indices = new ArrayList<>(orderedComponents.size());
            for (int i = 0; i < orderedComponents.size(); i++) indices.add(i);
            indices.sort((left, right) -> RoomPartitioner.OWNER_ORDER.compare(
                    orderedComponents.get(left), orderedComponents.get(right)));
            int[] ownerRanks = new int[indices.size()];
            for (int rank = 0; rank < indices.size(); rank++) ownerRanks[indices.get(rank)] = rank;

            return new Index(List.copyOf(orderedComponents), cellsByColumn, ownerRanks, componentIndices);
        }

        /** The visitor sees a reused mutable cursor and must copy positions it retains. */
        void forEachCandidate(RoomPartitioner.Component component, Consumer<BlockPos> visit) {
            Integer knownIndex = componentIndices.get(component);
            int componentIndex = knownIndex == null ? components.indexOf(component) : knownIndex;
            int baseY = component.cells().stream().mapToInt(cell -> cell.feet().getY() - 1)
                    .min().orElse(0);
            Map<Column, BitSet> occupiedHeights = new HashMap<>();
            for (FloorGeometry.Cell cell : component.cells()) {
                int from = cell.feet().getY() - 1;
                int to = cell.ceilingY();
                addInterval(occupiedHeights, cell.feet().getX(), cell.feet().getZ(), from, to, baseY);
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    int x = cell.feet().getX() + direction.getStepX();
                    int z = cell.feet().getZ() + direction.getStepZ();
                    if (ownsPerimeterColumn(componentIndex, cell, x, z)) {
                        addInterval(occupiedHeights, x, z, from, to, baseY);
                    }
                }
            }

            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (Map.Entry<Column, BitSet> entry : occupiedHeights.entrySet()) {
                Column column = entry.getKey();
                BitSet heights = entry.getValue();
                for (int bit = heights.nextSetBit(0); bit >= 0; bit = heights.nextSetBit(bit + 1)) {
                    cursor.set(column.x(), baseY + bit, column.z());
                    visit.accept(cursor);
                }
            }
        }

        private static void addInterval(Map<Column, BitSet> heights, int x, int z,
                                        int from, int to, int baseY) {
            if (from >= to) return;
            heights.computeIfAbsent(new Column(x, z), ignored -> new BitSet())
                    .set(from - baseY, to - baseY);
        }

        private boolean ownsPerimeterColumn(int componentIndex,
                                            FloorGeometry.Cell sourceCell,
                                            int x,
                                            int z) {
            List<IndexedCell> occupants = cellsByColumn.getOrDefault(new Column(x, z), List.of());
            if (occupants.stream().anyMatch(candidate -> overlapsPoiInterval(sourceCell, candidate.cell()))) {
                return false;
            }
            return componentIndex >= 0 && componentIndex == adjacentOwner(sourceCell, x, z);
        }

        private int adjacentOwner(FloorGeometry.Cell sourceCell, int x, int z) {
            int owner = -1;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                Column neighbor = new Column(x + direction.getStepX(), z + direction.getStepZ());
                for (IndexedCell candidate : cellsByColumn.getOrDefault(neighbor, List.of())) {
                    if (Math.abs(candidate.cell().feet().getY() - sourceCell.feet().getY()) <= 1) {
                        int index = candidate.componentIndex();
                        if (owner < 0 || ownerRanks[index] < ownerRanks[owner]) owner = index;
                    }
                }
            }
            return owner;
        }

        private static Column column(FloorGeometry.Cell cell) {
            return new Column(cell.feet().getX(), cell.feet().getZ());
        }

    }
}
