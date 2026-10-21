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
        assertTrue(decision.shiftedNumbers().isEmpty());
        assertEquals(2, landing.floorNumber());
    }

    @Test
    void conflictingSavedNumbersRemainAmbiguous() {
        var ground = floor(0, 67, 0);
        var conflict = FloorGrouping.prospectiveNumber(List.of(ground, floor(1, 76, 2), floor(2, 78, 3)), ground, 78);
        assertEquals(Building.validationResult.AMBIGUOUS_STRUCTURE, conflict.result());
        assertTrue(conflict.number().isEmpty());
    }

    @Test
    void missingIntermediateStoreyCanBeInsertedWithoutChangingPreviewInputs() {
        var ground = floor(0, 73, 0);
        var upper = floor(1, 85, 1);
        var top = floor(2, 90, 2);
        var insertion = FloorGrouping.prospectiveNumber(List.of(ground, upper, top), ground, 79);
        assertEquals(Building.validationResult.SUCCESS, insertion.result());
        assertEquals(OptionalInt.of(1), insertion.number());
        assertEquals(Map.of(1, 2, 2, 3), insertion.shiftedNumbers());
        assertEquals(1, upper.floorNumber());
        assertEquals(2, top.floorNumber());
    }

    @Test
    void missingBasementCanBeInsertedBetweenGroundAndExistingBasement() {
        var ground = floor(0, 73, 0);
        var basement = floor(1, 61, -1);
        var insertion = FloorGrouping.prospectiveNumber(List.of(ground, basement), ground, 67);
        assertEquals(Building.validationResult.SUCCESS, insertion.result());
        assertEquals(OptionalInt.of(-1), insertion.number());
        assertEquals(Map.of(-1, -2), insertion.shiftedNumbers());
        assertEquals(-1, basement.floorNumber());
    }

    @Test
    void insertionCannotOverflowAnExistingStoreyLabel() {
        var ground = floor(0, 73, 0);
        assertEquals(Building.validationResult.AMBIGUOUS_STRUCTURE,
                FloorGrouping.prospectiveNumber(List.of(ground, floor(1, 85, 1),
                        floor(2, 90, Integer.MAX_VALUE)), ground, 79).result());
        assertEquals(Building.validationResult.AMBIGUOUS_STRUCTURE,
                FloorGrouping.prospectiveNumber(List.of(ground, floor(1, 61, -1),
                        floor(2, 55, Integer.MIN_VALUE + 1)), ground, 67).result());
    }

    @Test
    void insertionShiftsWholeSharedStoreysButDoesNotCloseUnrelatedNumberGaps() {
        var ground = floor(0, 73, 0);
        var upper = floor(1, 85, 1);
        var sameStorey = floor(2, 86, 1);
        var top = floor(3, 90, 4);
        var insertion = FloorGrouping.prospectiveNumber(List.of(ground, upper, sameStorey, top), ground, 79);
        assertEquals(Map.of(1, 2, 4, 5), insertion.shiftedNumbers());
        var unusedNumber = FloorGrouping.prospectiveNumber(List.of(ground, floor(1, 85, 3)), ground, 79);
        assertEquals(OptionalInt.of(1), unusedNumber.number());
        assertTrue(unusedNumber.shiftedNumbers().isEmpty());
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

    @Test
    void semanticAnchorDoesNotMoveWhenRaisedGeometryBecomesTheModalHeight() {
        StructureFloor ground = new StructureFloor(0, 0, 67, new FloorGeometry(List.of(
                new FloorGeometry.Cell(new BlockPos(0, 67, 0), 71),
                new FloorGeometry.Cell(new BlockPos(1, 69, 0), 73),
                new FloorGeometry.Cell(new BlockPos(2, 69, 0), 73)), Map.of()));

        assertEquals(69, ground.geometry().anchorY());
        assertEquals(67, ground.anchorY());
        assertEquals(OptionalInt.of(1), FloorGrouping.prospectiveNumber(
                List.of(ground), ground, 73).number());
    }

    private static StructureFloor floor(int id, int height, int number) {
        return new StructureFloor(id, number, new FloorGeometry(List.of(
                new FloorGeometry.Cell(new BlockPos(id, height, 0), height + 4)), Map.of()));
    }
}
