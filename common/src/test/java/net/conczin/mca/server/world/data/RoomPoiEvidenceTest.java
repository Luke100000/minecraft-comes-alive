package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomPoiEvidenceTest {
    @Test
    void perimeterIncludesWallColumnWithoutAddingItToRoomFootprint() {
        FloorSurface.Cell interior = new FloorSurface.Cell(new BlockPos(1, 64, 1), 64.0D, 68);
        FloorSurface surface = new FloorSurface(Set.of(interior), Map.of());
        var component = new FloorSurfacePartitioner.Component(Set.of(interior));

        Set<BlockPos> candidates = RoomPoiEvidence.candidates(surface, Set.of(component), component);

        assertTrue(candidates.contains(new BlockPos(0, 65, 1)));
        assertTrue(candidates.contains(new BlockPos(1, 63, 1)));
        assertFalse(component.projectedCells(surface.anchorY()).contains(new BlockPos(0, 64, 1)));
    }

    @Test
    void unevenWalkableRoomKeepsPoiEvidenceThroughFloorCeiling() {
        FloorSurface.Cell low = new FloorSurface.Cell(new BlockPos(0, 64, 0), 64.0D, 68);
        FloorSurface.Cell middle = new FloorSurface.Cell(new BlockPos(1, 65, 0), 65.0D, 70);
        FloorSurface.Cell high = new FloorSurface.Cell(new BlockPos(2, 66, 0), 66.0D, 72);
        var surface = new FloorSurface(Set.of(low, middle, high), Map.of());
        var component = new FloorSurfacePartitioner.Component(Set.of(low, middle, high));

        Set<BlockPos> candidates = RoomPoiEvidence.candidates(surface, Set.of(component), component);

        assertTrue(candidates.contains(new BlockPos(2, 71, 1)));
    }

    @Test
    void ownedColumnKeepsPoiEvidenceBelowRaisedWalkableSurfaceWithinFloorBand() {
        FloorSurface.Cell floorAnchor = new FloorSurface.Cell(new BlockPos(10, 64, 10), 64.0D, 68);
        FloorSurface.Cell raised = new FloorSurface.Cell(new BlockPos(1, 66, 1), 66.0D, 72);
        var surface = new FloorSurface(Set.of(floorAnchor, raised), Map.of());
        var component = new FloorSurfacePartitioner.Component(Set.of(raised));

        Set<BlockPos> candidates = RoomPoiEvidence.candidates(surface, Set.of(component), component);

        assertTrue(candidates.contains(new BlockPos(1, 64, 1)),
                "a POI below stairs should still belong to the Room column when Y is inside the selected Floor band");
    }

    @Test
    void sharedPerimeterColumnBelongsToOneDeterministicRoom() {
        FloorSurface.Cell left = new FloorSurface.Cell(new BlockPos(0, 64, 0), 64.0D, 68);
        FloorSurface.Cell right = new FloorSurface.Cell(new BlockPos(2, 64, 0), 64.0D, 68);
        var surface = new FloorSurface(Set.of(left, right), Map.of());
        var leftRoom = new FloorSurfacePartitioner.Component(Set.of(left));
        var rightRoom = new FloorSurfacePartitioner.Component(Set.of(right));
        BlockPos sharedWallPoi = new BlockPos(1, 65, 0);

        List<FloorSurfacePartitioner.Component> components = List.of(leftRoom, rightRoom);
        Set<BlockPos> leftCandidates = RoomPoiEvidence.candidates(surface, components, leftRoom);
        Set<BlockPos> rightCandidates = RoomPoiEvidence.candidates(surface, components, rightRoom);

        assertTrue(leftCandidates.contains(sharedWallPoi),
                "stable bounds should make the left Room the deterministic owner for equal-sized Rooms");
        assertFalse(rightCandidates.contains(sharedWallPoi),
                "the same wall POI must not be counted by both Rooms");
    }
}
