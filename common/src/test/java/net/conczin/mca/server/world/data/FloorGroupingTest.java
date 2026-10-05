package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class FloorGroupingTest {
    @Test
    void groupRangeDoesNotChainAndIgnoresInputOrder() {
        assertEquals(List.of(new FloorGrouping.Band(76, 78), new FloorGrouping.Band(80, 80)),
                FloorGrouping.bands(List.of(78, 80, 76, 78)));
        assertEquals(FloorGrouping.bands(List.of(67, 72, 76, 78)),
                FloorGrouping.bands(List.of(78, 76, 72, 67)));
    }

    @Test
    void candidateReusesLandingStoreyWithoutMutatingInputs() {
        var ground = floor(0, 67, 0);
        var landing = floor(2, 76, 2);
        var decision = FloorGrouping.prospectiveNumber(List.of(ground, floor(1, 72, 1), landing), ground, 78);
        assertEquals(Building.validationResult.SUCCESS, decision.result());
        assertEquals(OptionalInt.of(2), decision.number());
        assertEquals(2, landing.floorNumber());
    }

    @Test
    void conflictingSavedNumbersAndOccupiedGapAreAmbiguous() {
        var ground = floor(0, 67, 0);
        var conflict = FloorGrouping.prospectiveNumber(List.of(ground, floor(1, 76, 2), floor(2, 78, 3)), ground, 78);
        assertEquals(Building.validationResult.AMBIGUOUS_STRUCTURE, conflict.result());
        assertTrue(conflict.number().isEmpty());
        var insertion = FloorGrouping.prospectiveNumber(List.of(ground, floor(1, 78, 1)), ground, 72);
        assertEquals(Building.validationResult.AMBIGUOUS_STRUCTURE, insertion.result());
        assertTrue(insertion.number().isEmpty());
    }

    @Test
    void basementUsesNegativeNumber() {
        var ground = floor(0, 67, 0);
        assertEquals(OptionalInt.of(-1), FloorGrouping.prospectiveNumber(List.of(ground), ground, 62).number());
    }

    @Test
    void candidateCannotExtendSavedGroupBeyondTolerance() {
        var ground = floor(0, 67, 0);
        var lower = floor(1, 76, 2);
        var upper = floor(2, 78, 2);
        var downward = FloorGrouping.prospectiveNumber(List.of(ground, lower, upper), ground, 74);
        assertEquals(Building.validationResult.AMBIGUOUS_STRUCTURE, downward.result());
        var upward = FloorGrouping.prospectiveNumber(List.of(ground, lower, upper), ground, 80);
        assertEquals(OptionalInt.of(3), upward.number());
    }

    @Test
    void unavailableSavedNumberDoesNotBecomeSuccessfulAttachment() {
        var ground = floor(0, 67, 0);
        var invalid = floor(1, 76, Integer.MIN_VALUE);
        assertEquals(Building.validationResult.AMBIGUOUS_STRUCTURE,
                FloorGrouping.prospectiveNumber(List.of(ground, invalid), ground, 78).result());
    }

    private static StructureFloor floor(int id, int height, int number) {
        return new StructureFloor(id, number, new FloorGeometry(List.of(
                new FloorGeometry.Cell(new BlockPos(id, height, 0), height + 4)), Map.of()));
    }
}
