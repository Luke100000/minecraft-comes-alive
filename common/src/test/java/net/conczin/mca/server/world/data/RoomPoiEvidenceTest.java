package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.AbstractCollection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomPoiEvidenceTest {
    @Test
    void perimeterIncludesWallColumnWithoutAddingItToRoomFootprint() {
        FloorGeometry.Cell interior = new FloorGeometry.Cell(new BlockPos(1, 64, 1), 68);
        var component = new RoomPartitioner.Component(Set.of(interior));

        Set<BlockPos> candidates = RoomPoiEvidence.candidates(Set.of(component), component);

        assertTrue(candidates.contains(new BlockPos(0, 65, 1)));
        assertTrue(candidates.contains(new BlockPos(1, 63, 1)));
        assertFalse(component.floorCells().contains(new BlockPos(0, 64, 1)));
    }

    @Test
    void perimeterEvidenceStopsBeforePhysicalCeiling() {
        FloorGeometry.Cell cell = new FloorGeometry.Cell(new BlockPos(1, 64, 1), 68);
        var room = new RoomPartitioner.Component(Set.of(cell));

        Set<BlockPos> candidates = RoomPoiEvidence.candidates(Set.of(room), room);

        assertTrue(candidates.contains(new BlockPos(0, 67, 1)));
        assertFalse(candidates.contains(new BlockPos(0, 68, 1)));
    }

    @Test
    void perimeterEvidenceDoesNotExpandThroughThickWall() {
        FloorGeometry.Cell cell = new FloorGeometry.Cell(new BlockPos(1, 64, 1), 68);
        var room = new RoomPartitioner.Component(Set.of(cell));

        Set<BlockPos> candidates = RoomPoiEvidence.candidates(Set.of(room), room);

        assertTrue(candidates.contains(new BlockPos(0, 65, 1)));
        assertFalse(candidates.contains(new BlockPos(-1, 65, 1)));
    }

    @Test
    void unevenWalkableRoomKeepsPoiEvidenceThroughFloorCeiling() {
        FloorGeometry.Cell low = new FloorGeometry.Cell(new BlockPos(0, 64, 0), 68);
        FloorGeometry.Cell middle = new FloorGeometry.Cell(new BlockPos(1, 65, 0), 70);
        FloorGeometry.Cell high = new FloorGeometry.Cell(new BlockPos(2, 66, 0), 72);
        var component = new RoomPartitioner.Component(Set.of(low, middle, high));

        Set<BlockPos> candidates = RoomPoiEvidence.candidates(Set.of(component), component);

        assertTrue(candidates.contains(new BlockPos(2, 71, 1)));
    }

    @Test
    void adjacentCellInSameComponentBlocksPerimeterExpansion() {
        FloorGeometry.Cell low = new FloorGeometry.Cell(new BlockPos(0, 64, 0), 68);
        FloorGeometry.Cell raised = new FloorGeometry.Cell(new BlockPos(1, 65, 0), 69);
        var component = new RoomPartitioner.Component(Set.of(low, raised));

        Set<BlockPos> candidates = RoomPoiEvidence.candidates(Set.of(component), component);

        assertFalse(candidates.contains(new BlockPos(1, 63, 0)),
                "an occupied Room column must not receive perimeter evidence from its neighbor");
    }

    @Test
    void raisedWalkableSurfaceUsesItsOwnPhysicalPoiInterval() {
        FloorGeometry.Cell raised = new FloorGeometry.Cell(new BlockPos(1, 66, 1), 72);
        var component = new RoomPartitioner.Component(Set.of(raised));

        Set<BlockPos> candidates = RoomPoiEvidence.candidates(Set.of(component), component);

        assertTrue(candidates.contains(new BlockPos(1, 65, 1)));
        assertFalse(candidates.contains(new BlockPos(1, 64, 1)),
                "exact Room POI evidence must not extend below the physical cell interval");
    }

    @Test
    void sharedPerimeterColumnBelongsToOneDeterministicRoom() {
        FloorGeometry.Cell left = new FloorGeometry.Cell(new BlockPos(0, 64, 0), 68);
        FloorGeometry.Cell right = new FloorGeometry.Cell(new BlockPos(2, 64, 0), 68);
        var leftRoom = new RoomPartitioner.Component(Set.of(left));
        var rightRoom = new RoomPartitioner.Component(Set.of(right));
        BlockPos sharedWallPoi = new BlockPos(1, 65, 0);

        List<RoomPartitioner.Component> components = List.of(leftRoom, rightRoom);
        Set<BlockPos> leftCandidates = RoomPoiEvidence.candidates(components, leftRoom);
        Set<BlockPos> rightCandidates = RoomPoiEvidence.candidates(components, rightRoom);

        assertTrue(leftCandidates.contains(sharedWallPoi),
                "stable bounds should make the left Room the deterministic owner for equal-sized Rooms");
        assertFalse(rightCandidates.contains(sharedWallPoi),
                "the same wall POI must not be counted by both Rooms");
    }

    @Test
    void sharedPerimeterOwnershipKeepsOneBlockHeightTolerance() {
        FloorGeometry.Cell left = new FloorGeometry.Cell(new BlockPos(0, 64, 0), 68);
        FloorGeometry.Cell right = new FloorGeometry.Cell(new BlockPos(2, 65, 0), 69);
        var leftRoom = new RoomPartitioner.Component(Set.of(left));
        var rightRoom = new RoomPartitioner.Component(Set.of(right));
        BlockPos sharedWallPoi = new BlockPos(1, 65, 0);

        List<RoomPartitioner.Component> components = List.of(leftRoom, rightRoom);

        assertTrue(RoomPoiEvidence.candidates(components, leftRoom).contains(sharedWallPoi));
        assertFalse(RoomPoiEvidence.candidates(components, rightRoom).contains(sharedWallPoi));
    }

    @Test
    void largeSharedPerimeterHasOneOwner() {
        Set<FloorGeometry.Cell> leftCells = new HashSet<>();
        Set<FloorGeometry.Cell> rightCells = new HashSet<>();
        for (int z = 0; z < 2_000; z++) {
            leftCells.add(new FloorGeometry.Cell(new BlockPos(0, 64, z), 68));
            rightCells.add(new FloorGeometry.Cell(new BlockPos(2, 64, z), 68));
        }
        var left = new RoomPartitioner.Component(leftCells);
        var right = new RoomPartitioner.Component(rightCells);
        List<RoomPartitioner.Component> rooms = List.of(left, right);
        BlockPos sharedWall = new BlockPos(1, 65, 1_000);

        assertTrue(RoomPoiEvidence.candidates(rooms, left).contains(sharedWall));
        assertFalse(RoomPoiEvidence.candidates(rooms, right).contains(sharedWall));
    }

    @Test
    void stackedRoomDoesNotHideLowerPerimeterPoiEvidence() {
        FloorGeometry.Cell lowerCell = new FloorGeometry.Cell(new BlockPos(0, 64, 0), 68);
        FloorGeometry.Cell upperCell = new FloorGeometry.Cell(new BlockPos(1, 70, 0), 74);
        var lower = new RoomPartitioner.Component(Set.of(lowerCell));
        var upper = new RoomPartitioner.Component(Set.of(upperCell));

        Set<BlockPos> candidates = RoomPoiEvidence.candidates(List.of(lower, upper), lower);

        assertTrue(candidates.contains(new BlockPos(1, 64, 0)),
                "an unrelated upper Room must not suppress lower-Room perimeter evidence");
    }

    @Test
    void sameHeightRoomStillOwnsItsPoiColumn() {
        FloorGeometry.Cell leftCell = new FloorGeometry.Cell(new BlockPos(0, 64, 0), 68);
        FloorGeometry.Cell rightCell = new FloorGeometry.Cell(new BlockPos(1, 64, 0), 68);
        var left = new RoomPartitioner.Component(Set.of(leftCell));
        var right = new RoomPartitioner.Component(Set.of(rightCell));

        Set<BlockPos> candidates = RoomPoiEvidence.candidates(List.of(left, right), left);

        assertFalse(candidates.contains(new BlockPos(1, 64, 0)));
    }

    @Test
    void materializationIndexesComponentsOnce() {
        var room = new RoomPartitioner.Component(Set.of(
                new FloorGeometry.Cell(new BlockPos(0, 64, 0), 68),
                new FloorGeometry.Cell(new BlockPos(1, 64, 0), 68)));
        var neighbor = new RoomPartitioner.Component(Set.of(
                new FloorGeometry.Cell(new BlockPos(0, 64, 2), 68)));
        var components = new CountingComponents(List.of(room, neighbor));

        Set<BlockPos> candidates = RoomPoiEvidence.candidates(components, room);

        assertTrue(candidates.contains(new BlockPos(0, 65, 1)));
        assertEquals(1, components.iteratorCalls,
                "Room POI materialization should build one operation-local component index");
    }

    @Test
    void sharedEvidenceIndexDoesNotRevisitComponentsForEachRoom() {
        var left = new RoomPartitioner.Component(Set.of(
                new FloorGeometry.Cell(new BlockPos(0, 64, 0), 68)));
        var right = new RoomPartitioner.Component(Set.of(
                new FloorGeometry.Cell(new BlockPos(2, 64, 0), 68)));
        var components = new CountingComponents(List.of(left, right));

        var index = RoomPoiEvidence.index(components);
        Set<BlockPos> leftCandidates = new HashSet<>();
        Set<BlockPos> rightCandidates = new HashSet<>();
        index.forEachCandidate(left, pos -> leftCandidates.add(pos.immutable()));
        index.forEachCandidate(right, pos -> rightCandidates.add(pos.immutable()));
        assertTrue(leftCandidates.contains(new BlockPos(1, 65, 0)));
        assertFalse(rightCandidates.contains(new BlockPos(1, 65, 0)));
        assertEquals(1, components.iteratorCalls);
    }

    @Test
    void visitingCandidatesUsesOneCursorWithoutChangingPoiGeometry() {
        var room = new RoomPartitioner.Component(Set.of(
                new FloorGeometry.Cell(new BlockPos(0, -58, 0), -20),
                new FloorGeometry.Cell(new BlockPos(1, -57, 0), -18)));
        var neighbor = new RoomPartitioner.Component(Set.of(
                new FloorGeometry.Cell(new BlockPos(3, -58, 0), -20)));
        List<RoomPartitioner.Component> rooms = List.of(room, neighbor);
        Set<BlockPos> expected = new HashSet<>();
        for (int y = -59; y < -20; y++) {
            expected.add(new BlockPos(-1, y, 0));
            expected.add(new BlockPos(0, y, -1));
            expected.add(new BlockPos(0, y, 0));
            expected.add(new BlockPos(0, y, 1));
        }
        for (int y = -58; y < -18; y++) {
            expected.add(new BlockPos(1, y, -1));
            expected.add(new BlockPos(1, y, 0));
            expected.add(new BlockPos(1, y, 1));
            expected.add(new BlockPos(2, y, 0));
        }
        Set<BlockPos> visited = new HashSet<>();
        BlockPos[] cursor = {null};

        RoomPoiEvidence.index(rooms).forEachCandidate(room, pos -> {
            if (cursor[0] == null) cursor[0] = pos;
            else assertTrue(cursor[0] == pos, "POI traversal should reuse one mutable cursor");
            assertTrue(visited.add(pos.immutable()), "each POI must be visited exactly once");
        });

        assertEquals(expected, visited);
    }

    private static final class CountingComponents
            extends AbstractCollection<RoomPartitioner.Component> {
        private final List<RoomPartitioner.Component> components;
        private int iteratorCalls;

        private CountingComponents(List<RoomPartitioner.Component> components) {
            this.components = List.copyOf(components);
        }

        @Override
        public Iterator<RoomPartitioner.Component> iterator() {
            iteratorCalls++;
            return components.iterator();
        }

        @Override
        public int size() {
            return components.size();
        }
    }
}
