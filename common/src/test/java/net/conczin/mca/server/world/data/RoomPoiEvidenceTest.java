package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

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
    void unevenWalkableRoomKeepsPoiEvidenceThroughFloorCeiling() {
        FloorGeometry.Cell low = new FloorGeometry.Cell(new BlockPos(0, 64, 0), 68);
        FloorGeometry.Cell middle = new FloorGeometry.Cell(new BlockPos(1, 65, 0), 70);
        FloorGeometry.Cell high = new FloorGeometry.Cell(new BlockPos(2, 66, 0), 72);
        var component = new RoomPartitioner.Component(Set.of(low, middle, high));

        Set<BlockPos> candidates = RoomPoiEvidence.candidates(Set.of(component), component);

        assertTrue(candidates.contains(new BlockPos(2, 71, 1)));
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
}
