package net.conczin.mca.client.gui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FamilyTreeTransitionTest {
    private static final UUID FOCUS = new UUID(0, 1);
    private static final UUID OTHER = new UUID(0, 2);
    private static final UUID CHILD = new UUID(0, 3);
    private static final UUID PARTNER = new UUID(0, 4);

    @Test
    void anchoredTransitionKeepsFocusStillWhileNeighborSlidesAndEdgesFollow() {
        FamilyTreeLayout.Result before = result(
                card(FOCUS, 0), card(OTHER, 160));
        FamilyTreeLayout.Result after = new FamilyTreeLayout.Result(
                List.of(card(FOCUS, 80), card(OTHER, 360), card(CHILD, 160)),
                List.of(new FamilyTreeLayout.Edge(FOCUS, CHILD,
                        FamilyTreeLayout.EdgeType.PARENT_CHILD, null)),
                new FamilyTreeLayout.Bounds(16, 424, -32, 152));

        FamilyTreeTransition transition = new FamilyTreeTransition(before, after, FOCUS);
        FamilyTreeLayout.Result start = transition.at(0);
        FamilyTreeLayout.Result middle = transition.at(0.5);
        FamilyTreeLayout.Result end = transition.at(1);

        assertEquals(0, center(start, FOCUS));
        assertEquals(0, center(middle, FOCUS));
        assertEquals(0, center(end, FOCUS));
        assertEquals(160, center(start, OTHER));
        assertTrue(center(middle, OTHER) > 160 && center(middle, OTHER) < 280);
        assertEquals(280, center(end, OTHER));
        assertTrue(center(transition.at(0.25), OTHER) < 220,
                "A moving neighbor must not cover most of its travel in the first quarter");
        assertEquals(80, center(end, CHILD));
        assertTrue(middle.edges().stream().anyMatch(edge -> edge.from().equals(FOCUS)
                && edge.to().equals(CHILD)));
        assertEquals(Optional.of(new FamilyTreeScreen.PersonTarget(OTHER)),
                FamilyTreeScreen.hitTargetAt(middle, center(middle, OTHER), 0));

        FamilyTreeViewModel.ViewportState original = new FamilyTreeViewModel.ViewportState(24, 8, 0.5f);
        assertEquals(-16.0, transition.settledViewport(original).panX());
        assertEquals(8.0, transition.settledViewport(original).panY());
    }

    @Test
    void newlyExpandedChildSlidesIntoPositionWhileExistingCardsStayPut() {
        FamilyTreeLayout.Card parent = card(FOCUS, 0);
        FamilyTreeLayout.Card neighbor = card(OTHER, 192);
        FamilyTreeLayout.Card child = new FamilyTreeLayout.Card(CHILD,
                new FamilyTreeLayout.Bounds(128, 256, 88, 152));
        FamilyTreeLayout.Result before = result(parent, neighbor);
        FamilyTreeLayout.Result after = new FamilyTreeLayout.Result(
                List.of(parent, neighbor, child),
                List.of(new FamilyTreeLayout.Edge(FOCUS, CHILD,
                        FamilyTreeLayout.EdgeType.PARENT_CHILD, null)),
                new FamilyTreeLayout.Bounds(-64, 256, -32, 152));

        FamilyTreeTransition transition = new FamilyTreeTransition(before, after, FOCUS);
        FamilyTreeLayout.Result beginning = transition.at(0);
        FamilyTreeLayout.Result halfway = transition.at(0.5);
        FamilyTreeLayout.Result settled = transition.at(1);

        assertEquals(parent.bounds(), bounds(beginning, FOCUS));
        assertEquals(parent.bounds(), bounds(halfway, FOCUS));
        assertEquals(neighbor.bounds(), bounds(beginning, OTHER));
        assertEquals(neighbor.bounds(), bounds(halfway, OTHER));
        assertTrue(bounds(beginning, CHILD).centerY() < child.bounds().centerY(),
                "New descendant should visibly arrive from above instead of appearing in place");
        assertTrue(bounds(beginning, CHILD).centerY() < bounds(halfway, CHILD).centerY());
        assertTrue(bounds(halfway, CHILD).centerY() < bounds(settled, CHILD).centerY());
        assertTrue(bounds(settled, CHILD).centerY() - bounds(transition.at(0.75), CHILD).centerY()
                        < bounds(halfway, CHILD).centerY() - bounds(transition.at(0.25), CHILD).centerY(),
                "The entering child should slow down before it stops");
        assertEquals(104, bounds(halfway, CHILD).centerY(), 2,
                "At halfway, the card should have traveled about half the distance");
        assertEquals(child.bounds(), bounds(settled, CHILD));
    }

    @Test
    void newlyRevealedCardsRemainVisiblyAnimatedAtDifferentTreeZooms() {
        FamilyTreeLayout.Card parent = card(FOCUS, 0);
        FamilyTreeLayout.Card child = new FamilyTreeLayout.Card(CHILD,
                new FamilyTreeLayout.Bounds(-64, 64, 88, 152));
        FamilyTreeLayout.Result before = result(parent);
        FamilyTreeLayout.Result after = new FamilyTreeLayout.Result(
                List.of(parent, child),
                List.of(new FamilyTreeLayout.Edge(FOCUS, CHILD,
                        FamilyTreeLayout.EdgeType.PARENT_CHILD, null)),
                new FamilyTreeLayout.Bounds(-64, 64, -32, 152));

        for (float zoom : new float[]{0.01F, 0.25F, 1.0F, 2.0F}) {
            FamilyTreeTransition transition = new FamilyTreeTransition(before, after, FOCUS, zoom);
            int startY = bounds(transition.at(0), CHILD).centerY();
            int finishY = bounds(transition.at(1), CHILD).centerY();
            double visibleTravel = Math.abs(finishY - startY) * zoom;
            assertTrue(visibleTravel >= 11.9,
                    "At zoom " + zoom + " the child only traveled " + visibleTravel + " screen pixels");
            assertEquals(parent.bounds(), bounds(transition.at(0.5), FOCUS));
        }
    }

    @Test
    void newlyDiscoveredCoupleEntersTogetherAtOverviewAndNormalZoom() {
        FamilyTreeLayout.Card root = card(FOCUS, 0);
        FamilyTreeLayout.Card child = new FamilyTreeLayout.Card(CHILD,
                new FamilyTreeLayout.Bounds(-141, -13, 88, 152));
        FamilyTreeLayout.Card spouse = new FamilyTreeLayout.Card(PARTNER,
                new FamilyTreeLayout.Bounds(13, 141, 88, 152));
        FamilyTreeLayout.Result before = result(root);
        FamilyTreeLayout.Result after = new FamilyTreeLayout.Result(
                List.of(root, child, spouse),
                List.of(new FamilyTreeLayout.Edge(FOCUS, CHILD,
                                FamilyTreeLayout.EdgeType.PARENT_CHILD, null),
                        new FamilyTreeLayout.Edge(CHILD, PARTNER,
                                FamilyTreeLayout.EdgeType.PARTNER, null)),
                new FamilyTreeLayout.Bounds(-141, 141, -32, 152));

        for (float zoom : new float[]{0.01F, 1.0F}) {
            FamilyTreeTransition transition = new FamilyTreeTransition(before, after, FOCUS, zoom);
            int childStart = bounds(transition.at(0), CHILD).centerY();
            int partnerStart = bounds(transition.at(0), PARTNER).centerY();
            assertEquals(childStart, partnerStart, "A new couple should start from the same animated row");
            assertTrue(Math.abs(child.bounds().centerY() - childStart) * zoom >= 11.9,
                    "Both members of a newly discovered couple need visible entrance motion");
            assertEquals(bounds(transition.at(0.5), CHILD).centerY(),
                    bounds(transition.at(0.5), PARTNER).centerY());
            assertEquals(child.bounds(), bounds(transition.at(1), CHILD));
            assertEquals(spouse.bounds(), bounds(transition.at(1), PARTNER));
        }
    }

    @Test
    void descendantsSeveralHopsAwayAlsoAnimateWhenTheFocusLoadsAnEntireBranch() {
        UUID grandchild = new UUID(0, 5);
        FamilyTreeLayout.Card root = card(FOCUS, 0);
        FamilyTreeLayout.Card child = new FamilyTreeLayout.Card(CHILD,
                new FamilyTreeLayout.Bounds(-64, 64, 88, 152));
        FamilyTreeLayout.Card descendant = new FamilyTreeLayout.Card(grandchild,
                new FamilyTreeLayout.Bounds(-64, 64, 208, 272));
        FamilyTreeLayout.Result after = new FamilyTreeLayout.Result(
                List.of(root, child, descendant),
                List.of(new FamilyTreeLayout.Edge(FOCUS, CHILD, FamilyTreeLayout.EdgeType.PARENT_CHILD, null),
                        new FamilyTreeLayout.Edge(CHILD, grandchild, FamilyTreeLayout.EdgeType.PARENT_CHILD, null)),
                new FamilyTreeLayout.Bounds(-64, 64, -32, 272));

        FamilyTreeTransition transition = new FamilyTreeTransition(result(root), after, FOCUS);
        assertNotEquals(bounds(transition.at(0), grandchild), bounds(transition.at(1), grandchild),
                "A grandchild revealed through a newly loaded parent must not pop directly into place");
        assertNotEquals(bounds(transition.at(0.5), grandchild), bounds(transition.at(1), grandchild));
    }

    private static FamilyTreeLayout.Result result(FamilyTreeLayout.Card... cards) {
        return new FamilyTreeLayout.Result(List.of(cards), List.of(),
                new FamilyTreeLayout.Bounds(-64, 224, -32, 32));
    }

    private static FamilyTreeLayout.Card card(UUID id, int centerX) {
        return new FamilyTreeLayout.Card(id,
                new FamilyTreeLayout.Bounds(centerX - 64, centerX + 64, -32, 32));
    }

    private static int center(FamilyTreeLayout.Result result, UUID id) {
        return result.cards().stream().filter(card -> card.uuid().equals(id))
                .findFirst().orElseThrow().bounds().centerX();
    }

    private static FamilyTreeLayout.Bounds bounds(FamilyTreeLayout.Result result, UUID id) {
        return result.cards().stream().filter(card -> card.uuid().equals(id))
                .findFirst().orElseThrow().bounds();
    }
}
