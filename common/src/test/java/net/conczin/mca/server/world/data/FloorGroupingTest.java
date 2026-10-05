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
        var ground = floor(0, 67, 0);
        var lower = floor(1, 76, 2);
        var upper = floor(2, 78, 2);
        var candidate = floor(3, 80, 0);
        var first = FloorGrouping.prospectiveNumber(List.of(ground, lower, upper), ground, candidate);
        var reversed = FloorGrouping.prospectiveNumber(List.of(upper, lower, ground), ground, candidate);
        assertEquals(OptionalInt.of(3), first.number());
        assertEquals(first, reversed);
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
    void descendingStoreysDoNotRepartitionAnAlreadyRegisteredGroup() {
        var ground = floor(0, 12, 0);
        var landing = floor(1, 10, -1);
        var nearby = floor(2, 8, -1);
        var next = floor(3, 6, 0);
        assertEquals(OptionalInt.of(-2), FloorGrouping.prospectiveNumber(
                List.of(ground, landing, nearby), ground, next).number());
        assertEquals(-1, landing.floorNumber());
        assertEquals(-1, nearby.floorNumber());
    }

    @Test
    void basementUsesNegativeNumber() {
        var ground = floor(0, 67, 0);
        assertEquals(OptionalInt.of(-1), FloorGrouping.prospectiveNumber(List.of(ground), ground, 62).number());
    }

    @Test
    void equalHeightRoomsReuseTheirStoreyButStackedFloorsDoNot() {
        var ground = floor(0, 67, 0);
        var landing = floor(1, 76, 1);
        assertEquals(OptionalInt.of(1), FloorGrouping.prospectiveNumber(
                List.of(ground, landing), ground, floor(2, 76, 0)).number());
        var stacked = new StructureFloor(2, 0, new FloorGeometry(List.of(
                new FloorGeometry.Cell(new BlockPos(1, 78, 0), 82)), Map.of()));
        assertEquals(OptionalInt.of(2), FloorGrouping.prospectiveNumber(
                List.of(ground, landing), ground, stacked).number());
    }

    @Test
    void nonzeroSavedGroundRequiresAnExplicitGroundChange() {
        var ground = floor(0, 67, 4);
        assertEquals(Building.validationResult.AMBIGUOUS_STRUCTURE,
                FloorGrouping.prospectiveNumber(List.of(ground), ground, 72).result());
        assertEquals(4, ground.floorNumber());
    }

    @Test
    void candidateCannotExtendSavedGroupBeyondTolerance() {
        var ground = floor(0, 67, 0);
        var lower = floor(1, 76, 2);
        var upper = floor(2, 78, 2);
        var downward = FloorGrouping.prospectiveNumber(List.of(ground, lower, upper), ground, 74);
        assertEquals(OptionalInt.of(1), downward.number());
        assertEquals(2, lower.floorNumber());
        assertEquals(2, upper.floorNumber());
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
