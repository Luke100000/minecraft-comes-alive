package net.conczin.mca.network.c2s;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingScanContinuationTest {
    @Test
    void typeSelectionContinuationRejectsPlayerMovement() {
        BlockPos source = new BlockPos(4, 64, 8);

        assertTrue(ConfirmBuildingPolymorphMessage.matchesContinuationSource(source, source));
        assertFalse(ConfirmBuildingPolymorphMessage.matchesContinuationSource(source, source.east()));
        assertFalse(ConfirmBuildingPolymorphMessage.matchesContinuationSource(null, source));
    }

    @Test
    void floorRemovalRequiresCompletePhysicalIdentity() {
        ReportBuildingMessage.FloorRemovalTarget target = ReportBuildingMessage
                .parseFloorRemovalTarget("-2:7:3").orElseThrow();

        assertEquals(-2, target.floorNumber());
        assertEquals(7, target.structureId());
        assertEquals(3, target.floorId());
        assertTrue(ReportBuildingMessage.parseFloorRemovalTarget("-2").isEmpty());
        assertTrue(ReportBuildingMessage.parseFloorRemovalTarget("-2:7").isEmpty());
        assertTrue(ReportBuildingMessage.parseFloorRemovalTarget("-2:-1:3").isEmpty());
        assertTrue(ReportBuildingMessage.parseFloorRemovalTarget("-2:7:-1").isEmpty());
        assertTrue(ReportBuildingMessage.parseFloorRemovalTarget(Integer.MIN_VALUE + ":7:3").isEmpty());
        assertTrue(ReportBuildingMessage.parseFloorRemovalTarget("bad:7:3").isEmpty());
        assertTrue(ReportBuildingMessage.parseFloorRemovalTarget("-2:7:3:4").isEmpty());
        assertTrue(ReportBuildingMessage.parseFloorRemovalTarget(null).isEmpty());
    }
}
