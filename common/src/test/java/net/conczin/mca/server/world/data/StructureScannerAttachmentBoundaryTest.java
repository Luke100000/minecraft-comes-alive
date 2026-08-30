package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureScannerAttachmentBoundaryTest {
    @Test
    void attachmentBoundaryBlocksPersistedOtherStoreyButNotSameBand() {
        Structure persisted = structureWithFloor(3, 77, Set.of(
                new BlockPos(0, 77, 0), new BlockPos(1, 77, 0),
                new BlockPos(0, 77, 1), new BlockPos(1, 77, 1)));
        StructureScanner.PersistedFloorBoundary boundary =
                StructureScanner.PersistedFloorBoundary.forAttachment(74, List.of(persisted));

        assertTrue(boundary.blocks(new BlockPos(0, 77, 0)));
        assertFalse(boundary.blocks(new BlockPos(0, 76, 0)));
        assertFalse(boundary.blocks(new BlockPos(9, 77, 9)));
    }

    @Test
    void associatedCellsKeepSameStoreyOverlapEvidence() {
        Structure persisted = structureWithFloor(3, 74, Set.of(
                new BlockPos(0, 74, 0), new BlockPos(1, 74, 0),
                new BlockPos(0, 74, 1), new BlockPos(1, 74, 1)));
        StructureScanner.PersistedFloorBoundary boundary =
                StructureScanner.PersistedFloorBoundary.forAttachment(74, List.of(persisted));
        Set<BuildingFloorRegionDetector.FloorCell> discovered = new HashSet<>();
        BuildingFloorRegionDetector.FloorCell overlap =
                new BuildingFloorRegionDetector.FloorCell(0, 74, 0);

        boundary.addPermittedAssociated(discovered, Set.of(overlap));

        assertTrue(discovered.contains(overlap));
    }

    @Test
    void associatedCellsDoNotReintroducePersistedOtherStorey() {
        Structure persisted = structureWithFloor(3, 77, Set.of(
                new BlockPos(0, 77, 0), new BlockPos(1, 77, 0),
                new BlockPos(0, 77, 1), new BlockPos(1, 77, 1)));
        StructureScanner.PersistedFloorBoundary boundary =
                StructureScanner.PersistedFloorBoundary.forAttachment(74, List.of(persisted));
        Set<BuildingFloorRegionDetector.FloorCell> discovered = new HashSet<>();
        BuildingFloorRegionDetector.FloorCell targetFloor =
                new BuildingFloorRegionDetector.FloorCell(0, 77, 0);

        boundary.addPermittedAssociated(discovered, Set.of(targetFloor));

        assertFalse(discovered.contains(targetFloor));
    }

    private static Structure structureWithFloor(int id, int y, Set<BlockPos> cells) {
        BuildingFloorRegion region = BuildingFloorRegion.fromFootprint(y, cells);
        StructureFloor floor = new StructureFloor(0, y, y + 5, 0, region);
        return new Structure(id, cells.iterator().next(),
                new BlockPos(0, y, 0), new BlockPos(1, y + 4, 1), List.of(floor));
    }
}
