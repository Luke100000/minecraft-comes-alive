package net.conczin.mca.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetourHandoffTest {
    @Test
    void oneBlockOfForwardProgressCannotFinishFlankingAcrossLongObstacle() {
        BlockPos origin = new BlockPos(0, 64, 0);
        BlockPos destination = new BlockPos(100, 64, 0);
        Path shortPartial = new Path(List.of(new Node(-5, 64, 0), new Node(1, 64, 0)), destination, false);

        assertFalse(MCAGroundPathNavigation.isUsefulPartialBeyondDetourOrigin(shortPartial, origin, destination));
    }

    @Test
    void partialReturnToBlockedApproachDoesNotEndFlanking() {
        BlockPos origin = new BlockPos(0, 64, 0);
        BlockPos destination = new BlockPos(70, 64, 0);
        Path returnsToBlockage = new Path(List.of(new Node(-4, 64, -12), new Node(0, 64, 0)), destination, false);
        assertFalse(MCAGroundPathNavigation.isUsefulPartialBeyondDetourOrigin(returnsToBlockage, origin, destination));
    }

    @Test
    void sidewaysOrUnmovedEndpointCannotCrossDiagonalFrontier() {
        BlockPos origin = new BlockPos(0, 64, 0);
        BlockPos destination = new BlockPos(40, 64, 40);
        Path sideways = new Path(List.of(new Node(-10, 64, 0), new Node(10, 64, -10)), destination, false);
        Path noMotion = new Path(List.of(new Node(-10, 64, 0)), destination, false);
        assertFalse(MCAGroundPathNavigation.isUsefulPartialBeyondDetourOrigin(sideways, origin, destination));
        assertFalse(MCAGroundPathNavigation.isUsefulPartialBeyondDetourOrigin(noMotion, origin, destination));
    }

    @Test
    void endpointBeyondPlaneButFartherFromGoalDoesNotResumeJourney() {
        BlockPos origin = new BlockPos(0, 64, 0);
        BlockPos destination = new BlockPos(70, 64, 0);
        Path besideFrontier = new Path(List.of(new Node(0, 64, 24), new Node(1, 64, 20)), destination, false);
        assertFalse(MCAGroundPathNavigation.isUsefulPartialBeyondDetourOrigin(besideFrontier, origin, destination));
    }

    @Test
    void adjustedTargetHeightStillAllowsProgressBeyondFrontier() {
        BlockPos origin = new BlockPos(0, 64, 0);
        BlockPos destination = new BlockPos(70, 64, 0);
        Path raised = new Path(List.of(new Node(-4, 64, -12), new Node(30, 64, -7)), destination.above(), false);
        assertTrue(MCAGroundPathNavigation.isUsefulPartialBeyondDetourOrigin(raised, origin, destination));
    }

    @Test
    void partialRouteBeyondBlockedApproachCanResumeDestination() {
        BlockPos origin = new BlockPos(0, 64, 0);
        BlockPos destination = new BlockPos(70, 64, 0);
        Path crossesFrontier = new Path(List.of(new Node(-4, 64, -12), new Node(30, 64, -7)), destination, false);
        assertTrue(MCAGroundPathNavigation.isUsefulPartialBeyondDetourOrigin(crossesFrontier, origin, destination));
    }
}
