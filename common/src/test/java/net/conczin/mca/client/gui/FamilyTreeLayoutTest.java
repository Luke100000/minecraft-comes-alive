package net.conczin.mca.client.gui;

import net.conczin.mca.FamilyTreeTestSupport;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.FamilyTreeView;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.Util;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static net.conczin.mca.FamilyTreeTestSupport.uuid;
import static net.conczin.mca.network.FamilyTreeView.Direction.ANCESTORS;
import static net.conczin.mca.network.FamilyTreeView.Direction.DESCENDANTS;
import static org.junit.jupiter.api.Assertions.*;

class FamilyTreeLayoutTest {
    private static final UUID ROOT = uuid(1);
    private static final UUID FATHER = uuid(2);
    private static final UUID MOTHER = uuid(3);
    private static final UUID PATERNAL_GRANDFATHER = uuid(4);
    private static final UUID SIBLING_A = uuid(5);
    private static final UUID SIBLING_B = uuid(6);
    private static final UUID PARTNER = uuid(7);
    private static final UUID CHILD_A = uuid(8);
    private static final UUID CHILD_B = uuid(9);
    private static final UUID GRANDCHILD = uuid(10);
    private static final UUID SIBLING_CHILD = uuid(11);
    private static final UUID PARTNER_A = uuid(12);
    private static final UUID PARTNER_B = uuid(13);

    @BeforeAll
    static void bootstrapMinecraft() {
        FamilyTreeTestSupport.bootstrapMinecraft();
    }

    @Test
    void singlePersonCentersAtOrigin() {
        FamilyTreeLayout.Result result = layout(Map.of(ROOT, node(ROOT)), Set.of());

        FamilyTreeLayout.Card root = card(result, ROOT);
        assertEquals(0, root.bounds().centerX());
        assertEquals(0, root.bounds().centerY());
    }

    @Test
    void twoParentsOccupyGenerationAboveAnchor() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode father = node(FATHER);
        FamilyTreeNode mother = node(MOTHER);
        root.setFather(father);
        root.setMother(mother);

        FamilyTreeLayout.Result result = layout(nodes(root, father, mother), Set.of());

        assertTrue(card(result, FATHER).bounds().centerY() < card(result, ROOT).bounds().centerY());
        assertEquals(card(result, FATHER).bounds().centerY(), card(result, MOTHER).bounds().centerY());
        assertFalse(card(result, FATHER).bounds().intersects(card(result, MOTHER).bounds()));
    }

    @Test
    void grandparentsRemainAboveTheirChildBranch() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode father = node(FATHER);
        FamilyTreeNode grandfather = node(PATERNAL_GRANDFATHER);
        root.setFather(father);
        father.setFather(grandfather);

        FamilyTreeLayout.Result result = layout(nodes(root, father, grandfather), Set.of());

        assertTrue(card(result, PATERNAL_GRANDFATHER).bounds().centerY() < card(result, FATHER).bounds().centerY());
    }

    @Test
    void exploringAGrandparentsDescendantsPlacesTheirOtherChildOnTheParentsRow() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode father = node(FATHER);
        FamilyTreeNode grandfather = node(PATERNAL_GRANDFATHER);
        FamilyTreeNode uncle = node(SIBLING_A);
        root.setFather(father);
        father.setFather(grandfather);
        uncle.setFather(grandfather);
        grandfather.addChild(father.id());
        grandfather.addChild(uncle.id());
        FamilyTreeLayout.Result before = layout(nodes(root, father, grandfather), Set.of(
                new FamilyTreeView.Continuation(grandfather.id(), DESCENDANTS)));

        FamilyTreeLayout.Result expanded = FamilyTreeLayout.layout(
                new FamilyTreeViewModel.Snapshot(ROOT, nodes(root, father, grandfather, uncle), Set.of()), before);

        assertTrue(expanded.cards().stream().anyMatch(card -> card.uuid().equals(uncle.id())),
                "A fetched child of a visible grandparent must appear even without a partner branch");
        assertEquals(card(before, FATHER).bounds().centerY(), card(expanded, uncle.id()).bounds().centerY());
        assertTrue(expanded.edges().stream().anyMatch(edge -> edge.from().equals(grandfather.id())
                && edge.to().equals(uncle.id())));
        assertEquals(card(before, ROOT).bounds(), card(expanded, ROOT).bounds());
    }

    @Test
    void partnerSharesAnchorGenerationWithoutOverlap() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode partner = node(PARTNER);
        root.updatePartner(partner);

        FamilyTreeLayout.Result result = layout(nodes(root, partner), Set.of());

        assertEquals(card(result, ROOT).bounds().centerY(), card(result, PARTNER).bounds().centerY());
        assertFalse(card(result, ROOT).bounds().intersects(card(result, PARTNER).bounds()));
        assertTrue(result.edges().stream().anyMatch(edge -> edge.type() == FamilyTreeLayout.EdgeType.PARTNER));
    }

    @Test
    void immediateParentalFamiliesGiveCouplesBoundedBreathingRoom() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode partner = node(PARTNER);
        root.updatePartner(partner);
        FamilyTreeLayout.Result sparse = layout(nodes(root, partner), Set.of());
        assertEquals(44, partnerGap(sparse, ROOT, PARTNER));

        FamilyTreeNode father = node(FATHER);
        FamilyTreeNode mother = node(MOTHER);
        root.setFather(father);
        root.setMother(mother);
        FamilyTreeLayout.Result crowded = layout(nodes(root, partner, father, mother), Set.of());
        FamilyTreeLayout.Bounds parents = card(crowded, FATHER).bounds().union(card(crowded, MOTHER).bounds());
        assertTrue(parents.right() - parents.left() > 128,
                "The displayed parental footprint is wider than its child's card");
        assertTrue(partnerGap(crowded, ROOT, PARTNER) > partnerGap(sparse, ROOT, PARTNER));
        assertTrue(partnerGap(crowded, ROOT, PARTNER) <= 72);
    }

    @Test
    void immediateChildrenWidenSiblingGuttersWithoutReservingGrandchildrenRecursively() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode father = node(FATHER);
        FamilyTreeNode sibling = node(SIBLING_A);
        root.setFather(father);
        sibling.setFather(father);
        Map<UUID, FamilyTreeNode> family = nodes(root, father, sibling);
        FamilyTreeLayout.Result sparse = layout(family, Set.of());
        assertEquals(64, partnerGap(sparse, ROOT, SIBLING_A));

        for (int i = 0; i < 4; i++) {
            FamilyTreeNode child = node(uuid(200 + i));
            child.setFather(i < 2 ? root : sibling);
            family.put(child.id(), child);
        }
        FamilyTreeLayout.Result crowded = layout(family, Set.of());
        assertTrue(partnerGap(crowded, ROOT, SIBLING_A) > 64);
        assertTrue(partnerGap(crowded, ROOT, SIBLING_A) <= 84);
        assertDistinctBranchSpacing(crowded, Set.of(uuid(200), uuid(201)), Set.of(uuid(202), uuid(203)));

        for (int i = 0; i < 4; i++) {
            FamilyTreeNode grandchild = node(uuid(210 + i));
            grandchild.setFather(family.get(uuid(200)));
            family.put(grandchild.id(), grandchild);
        }
        FamilyTreeLayout.Result deeper = layout(family, Set.of());
        assertEquals(partnerGap(crowded, ROOT, SIBLING_A), partnerGap(deeper, ROOT, SIBLING_A),
                "A deeper row must not reserve width recursively in the root generation");
        assertTrue(partnerGap(deeper, uuid(200), uuid(201)) > 64);
        assertTrue(partnerGap(deeper, uuid(200), uuid(201)) <= 84);
    }

    @Test
    void distinctFamiliesUseAdaptiveGuttersEvenWithoutSharedTargetCoordinates() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode sibling = node(SIBLING_A);
        FamilyTreeNode father = node(FATHER);
        root.setFather(father);
        sibling.setFather(father);
        FamilyTreeNode child = node(CHILD_A);
        FamilyTreeNode cousin = node(CHILD_B);
        child.setFather(root);
        cousin.setFather(sibling);
        Map<UUID, FamilyTreeNode> family = nodes(root, sibling, father, child, cousin);
        assertEquals(112, partnerGap(layout(family, Set.of()), CHILD_A, CHILD_B));
        for (int i = 0; i < 4; i++) {
            FamilyTreeNode grandchild = node(uuid(220 + i));
            grandchild.setFather(i < 2 ? child : cousin);
            family.put(grandchild.id(), grandchild);
        }
        FamilyTreeLayout.Result crowded = layout(family, Set.of());
        assertTrue(partnerGap(crowded, CHILD_A, CHILD_B) > 112);
        assertTrue(partnerGap(crowded, CHILD_A, CHILD_B) <= 160);
        assertSeparatedFamilyCards(crowded);
    }

    private static int partnerGap(FamilyTreeLayout.Result result, UUID first, UUID second) {
        return Math.abs(card(result, first).bounds().centerX() - card(result, second).bounds().centerX()) - 128;
    }

    @Test
    void revealingBothSpousesParentsUpdatesExistingCoupleAndDoesNotDrift() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode partner = node(PARTNER);
        root.updatePartner(partner);
        Map<UUID, FamilyTreeNode> family = nodes(root, partner);
        FamilyTreeLayout.Result before = layout(family, Set.of());
        FamilyTreeNode father = node(FATHER);
        FamilyTreeNode mother = node(MOTHER);
        FamilyTreeNode partnerFather = node(uuid(230));
        FamilyTreeNode partnerMother = node(uuid(231));
        root.setFather(father);
        root.setMother(mother);
        partner.setFather(partnerFather);
        partner.setMother(partnerMother);
        family.putAll(nodes(father, mother, partnerFather, partnerMother));
        FamilyTreeViewModel.Snapshot snapshot = new FamilyTreeViewModel.Snapshot(ROOT, family, Set.of());
        FamilyTreeLayout.Result after = FamilyTreeLayout.layout(snapshot, before);
        assertTrue(partnerGap(after, ROOT, PARTNER) > partnerGap(before, ROOT, PARTNER));
        assertCouplesAreAdjacent(after);
        assertEquals(card(before, ROOT).bounds(), card(after, ROOT).bounds());
        assertSeparatedFamilyCards(after);
        assertEquals(after, FamilyTreeLayout.layout(snapshot, after));

        FamilyTreeTransition transition = new FamilyTreeTransition(before, after, PARTNER);
        for (double progress : new double[]{0, 0.25, 0.5, 0.75, 1}) {
            FamilyTreeLayout.Result frame = transition.at(progress);
            assertEquals(card(before, PARTNER).bounds(), card(frame, PARTNER).bounds());
            for (FamilyTreeLayout.FamilyConnector connector : frame.connectors()) {
                card(frame, connector.parent());
                connector.children().forEach(child -> card(frame, child));
            }
        }
    }

    @Test
    void neighboringFamiliesCanExpandInEitherOrderWithStableAdaptiveSpacing() {
        for (boolean reverse : new boolean[]{false, true}) {
            FamilyTreeNode root = node(ROOT);
            FamilyTreeNode sibling = node(SIBLING_A);
            FamilyTreeNode father = node(FATHER);
            root.setFather(father);
            sibling.setFather(father);
            Map<UUID, FamilyTreeNode> family = nodes(root, sibling, father);
            FamilyTreeLayout.Result result = layout(family, Set.of());
            FamilyTreeLayout.Bounds fixedRoot = card(result, ROOT).bounds();
            for (FamilyTreeNode parent : reverse ? List.of(sibling, root) : List.of(root, sibling)) {
                for (int i = 0; i < 3; i++) {
                    FamilyTreeNode child = node(uuid((parent == root ? 240 : 250) + i));
                    child.setFather(parent);
                    FamilyTreeNode spouse = node(uuid((parent == root ? 260 : 270) + i));
                    child.updatePartner(spouse);
                    family.putAll(nodes(child, spouse));
                }
                FamilyTreeViewModel.Snapshot snapshot = new FamilyTreeViewModel.Snapshot(ROOT, family, Set.of());
                result = FamilyTreeLayout.layout(snapshot, result);
                assertEquals(fixedRoot, card(result, ROOT).bounds());
                assertTrue(partnerGap(result, ROOT, SIBLING_A) > 64);
                assertTrue(partnerGap(result, ROOT, SIBLING_A) <= 84);
                assertSeparatedFamilyCards(result);
                assertCouplesAreAdjacent(result);
                assertEquals(result, FamilyTreeLayout.layout(snapshot, result));
            }
        }
    }

    @Test
    void newlyDiscoveredSpouseStaysBesideTheirExistingCardWithBoundedGap() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode sibling = node(SIBLING_A);
        FamilyTreeNode father = node(FATHER);
        root.setFather(father);
        sibling.setFather(father);
        Map<UUID, FamilyTreeNode> family = nodes(root, sibling, father);
        FamilyTreeLayout.Result before = layout(family, Set.of());
        FamilyTreeNode spouse = node(PARTNER_A);
        sibling.updatePartner(spouse);
        family.put(spouse.id(), spouse);
        FamilyTreeViewModel.Snapshot snapshot = new FamilyTreeViewModel.Snapshot(ROOT, family, Set.of());
        FamilyTreeLayout.Result after = FamilyTreeLayout.layout(snapshot, before);
        assertEquals(card(before, ROOT).bounds(), card(after, ROOT).bounds());
        assertCouplesAreAdjacent(after);
        assertSeparatedFamilyCards(after);
        assertEquals(after, FamilyTreeLayout.layout(snapshot, after));
    }

    @Test
    void manyJointChildrenNeedARealWideRailWithoutWideningTheirParentsLink() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode partner = node(PARTNER);
        root.updatePartner(partner);
        Map<UUID, FamilyTreeNode> family = nodes(root, partner);
        for (int i = 0; i < 24; i++) {
            FamilyTreeNode child = node(uuid(300 + i));
            child.setFather(root);
            child.setMother(partner);
            family.put(child.id(), child);
        }
        FamilyTreeLayout.Result result = layout(family, Set.of());
        assertEquals(26, result.cards().size());
        assertEquals(44, partnerGap(result, ROOT, PARTNER));
        assertEquals(24, result.connectors().get(0).children().size());
        assertTrue(result.contentBounds().right() - result.contentBounds().left() > 4_000);
        assertSeparatedFamilyCards(result);
    }

    @Test
    void deepAncestryKeepsCouplesAdjacentAndGraphCompact() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode partner = node(PARTNER);
        root.updatePartner(partner);

        Map<UUID, FamilyTreeNode> family = nodes(root, partner);
        FamilyTreeNode child = root;
        for (int generation = 0; generation < 8; generation++) {
            FamilyTreeNode father = node(uuid(100 + generation * 2));
            FamilyTreeNode mother = node(uuid(101 + generation * 2));
            father.updatePartner(mother);
            child.setFather(father);
            child.setMother(mother);
            family.put(father.id(), father);
            family.put(mother.id(), mother);
            child = father;
        }

        FamilyTreeLayout.Result result = layout(family, Set.of());

        assertBoundedPartnerGap(result, ROOT, PARTNER);
        for (int generation = 0; generation < 8; generation++) {
            UUID fatherId = uuid(100 + generation * 2);
            UUID motherId = uuid(101 + generation * 2);
            assertBoundedPartnerGap(result, fatherId, motherId);
        }
        assertTrue(result.contentBounds().right() - result.contentBounds().left() < 2_000,
                "A single ancestral line must not reserve exponentially wide empty lanes");
        for (int first = 0; first < result.cards().size(); first++) {
            for (int second = first + 1; second < result.cards().size(); second++) {
                assertFalse(result.cards().get(first).bounds().intersects(result.cards().get(second).bounds()));
            }
        }
    }

    @Test
    void partnerConnectionDoesNotRunThroughSiblingCard() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode father = node(FATHER);
        FamilyTreeNode siblingA = node(SIBLING_A);
        FamilyTreeNode siblingB = node(SIBLING_B);
        FamilyTreeNode partner = node(PARTNER);
        root.setFather(father);
        siblingA.setFather(father);
        siblingB.setFather(father);
        root.updatePartner(partner);

        FamilyTreeLayout.Result result = layout(nodes(root, father, siblingA, siblingB, partner), Set.of());
        FamilyTreeLayout.Card rootCard = card(result, ROOT);
        FamilyTreeLayout.Card partnerCard = card(result, PARTNER);
        int midpointX = (rootCard.bounds().centerX() + partnerCard.bounds().centerX()) / 2;
        int midpointY = rootCard.bounds().centerY();

        assertTrue(result.cards().stream()
                .filter(card -> !card.uuid().equals(ROOT) && !card.uuid().equals(PARTNER))
                .noneMatch(card -> card.bounds().contains(midpointX, midpointY)));
    }

    @Test
    void siblingsAndTheirPartnersStayAdjacentThroughSeveralBranchExpansions() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode father = node(FATHER);
        FamilyTreeNode siblingA = node(SIBLING_A);
        FamilyTreeNode siblingB = node(SIBLING_B);
        FamilyTreeNode partnerA = node(PARTNER_A);
        FamilyTreeNode partnerB = node(PARTNER_B);
        root.setFather(father);
        siblingA.setFather(father);
        siblingB.setFather(father);
        siblingA.updatePartner(partnerA);
        partnerA.updatePartner(siblingA);
        siblingB.updatePartner(partnerB);
        partnerB.updatePartner(siblingB);
        Map<UUID, FamilyTreeNode> family = nodes(root, father, siblingA, siblingB, partnerA, partnerB);
        FamilyTreeLayout.Result result = layout(family, Set.of());
        assertCouplesAreAdjacent(result);
        FamilyTreeLayout.Bounds rootBounds = card(result, ROOT).bounds();

        for (FamilyTreeNode parent : List.of(siblingA, siblingB)) {
            for (int i = 0; i < 3; i++) {
                FamilyTreeNode child = node(uuid(100 + family.size()));
                child.setFather(parent);
                parent.addChild(child.id());
                family.put(child.id(), child);
            }
            result = FamilyTreeLayout.layout(new FamilyTreeViewModel.Snapshot(ROOT, family, Set.of()), result);
            assertCouplesAreAdjacent(result);
            assertSeparatedFamilyCards(result);
            assertEquals(rootBounds, card(result, ROOT).bounds());
        }
    }

    private static void assertCouplesAreAdjacent(FamilyTreeLayout.Result result) {
        for (FamilyTreeLayout.Edge edge : result.edges()) {
            if (edge.type() == FamilyTreeLayout.EdgeType.PARTNER) {
                assertBoundedPartnerGap(result, edge.from(), edge.to());
            }
        }
    }

    private static void assertBoundedPartnerGap(FamilyTreeLayout.Result result, UUID first, UUID second) {
        int gap = partnerGap(result, first, second);
        assertTrue(gap >= 44 && gap <= 72, "Couples must remain adjacent with a 44–72 unit gap, got " + gap);
        int midpoint = (card(result, first).bounds().centerX() + card(result, second).bounds().centerX()) / 2;
        int row = card(result, first).bounds().centerY();
        assertTrue(result.cards().stream().filter(card -> !card.uuid().equals(first) && !card.uuid().equals(second))
                .noneMatch(card -> card.bounds().contains(midpoint, row)), "Another person must not separate spouses");
    }

    @Test
    void partnerAncestorsArePlacedWithoutChangingTheLayoutRoot() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode partner = node(PARTNER);
        FamilyTreeNode partnerFather = node(FATHER);
        root.updatePartner(partner);
        partner.setFather(partnerFather);

        FamilyTreeLayout.Result result = layout(nodes(root, partner, partnerFather), Set.of());

        assertEquals(0, card(result, ROOT).bounds().centerX());
        assertEquals(card(result, ROOT).bounds().centerY(), card(result, PARTNER).bounds().centerY());
        assertTrue(card(result, FATHER).bounds().centerY() < card(result, PARTNER).bounds().centerY());
    }

    @Test
    void nonAnchorGenerationKeepsCouplesAdjacent() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode childA = node(CHILD_A);
        FamilyTreeNode childB = node(CHILD_B);
        FamilyTreeNode partnerA = node(PARTNER_A);
        FamilyTreeNode partnerB = node(PARTNER_B);
        childA.setFather(root);
        childB.setFather(root);
        childA.updatePartner(partnerA);
        childB.updatePartner(partnerB);

        FamilyTreeLayout.Result result = layout(nodes(root, childA, childB, partnerA, partnerB), Set.of());

        assertEquals(
                FamilyTreeLayout.CARD_WIDTH + FamilyTreeLayout.PARTNER_GAP,
                Math.abs(card(result, CHILD_A).bounds().centerX() - card(result, PARTNER_A).bounds().centerX())
        );
        assertEquals(
                FamilyTreeLayout.CARD_WIDTH + FamilyTreeLayout.PARTNER_GAP,
                Math.abs(card(result, CHILD_B).bounds().centerX() - card(result, PARTNER_B).bounds().centerX())
        );
    }

    @Test
    void marriedChildrenStayUnderTheirOwnParentsWithoutOverlappingParentBranches() {
        FamilyTreeNode damian = node(ROOT);
        FamilyTreeNode than = node(PARTNER);
        FamilyTreeNode lilija = node(PARTNER_A); // Higher UUID than Alexander: UUID order must not decide their sides.
        FamilyTreeNode alexander = node(CHILD_A);
        FamilyTreeNode natalia = node(FATHER);
        FamilyTreeNode alihan = node(MOTHER);

        damian.updatePartner(than);
        than.updatePartner(damian);
        natalia.updatePartner(alihan);
        alihan.updatePartner(natalia);
        lilija.setFather(damian);
        lilija.setMother(than);
        alexander.setFather(natalia);
        alexander.setMother(alihan);
        lilija.updatePartner(alexander);
        alexander.updatePartner(lilija);

        FamilyTreeLayout.Result result = layout(nodes(damian, than, lilija, alexander, natalia, alihan), Set.of());

        int lilijaX = card(result, lilija.id()).bounds().centerX();
        int alexanderX = card(result, alexander.id()).bounds().centerX();
        int lilijaParentsX = (card(result, damian.id()).bounds().centerX()
                + card(result, than.id()).bounds().centerX()) / 2;
        int alexanderParentsX = (card(result, natalia.id()).bounds().centerX()
                + card(result, alihan.id()).bounds().centerX()) / 2;

        assertTrue(lilijaParentsX < alexanderParentsX);
        assertTrue(lilijaX < alexanderX, "Spouses must follow the order of their parental branches");
        assertTrue(Math.abs(lilijaParentsX - lilijaX) < Math.abs(lilijaParentsX - alexanderX));
        assertTrue(Math.abs(alexanderParentsX - alexanderX) < Math.abs(alexanderParentsX - lilijaX));

        assertTrue(result.edges().stream().anyMatch(edge -> edge.type() == FamilyTreeLayout.EdgeType.PARENT_CHILD
                && edge.to().equals(lilija.id()) && edge.secondParent() != null));
        assertTrue(result.edges().stream().anyMatch(edge -> edge.type() == FamilyTreeLayout.EdgeType.PARENT_CHILD
                && edge.to().equals(alexander.id()) && edge.secondParent() != null));

        int leftSegmentRight = Math.max(lilijaParentsX, lilijaX);
        int rightSegmentLeft = Math.min(alexanderParentsX, alexanderX);
        assertTrue(leftSegmentRight < rightSegmentLeft,
                "Unrelated parent-child horizontal connectors should not look like one continuous line");
    }

    @Test
    void siblingsSurroundAnchorWithoutExpandingTheirChildren() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode father = node(FATHER);
        FamilyTreeNode siblingA = node(SIBLING_A);
        FamilyTreeNode siblingB = node(SIBLING_B);
        FamilyTreeNode siblingChild = node(SIBLING_CHILD);
        root.setFather(father);
        siblingA.setFather(father);
        siblingB.setFather(father);
        siblingChild.setFather(siblingA);

        // The bounded initial response contains the sibling but not its descendants.
        // A subsequent expansion response adds the child node (covered below).
        FamilyTreeLayout.Result result = layout(nodes(root, father, siblingA, siblingB), Set.of());

        assertEquals(card(result, ROOT).bounds().centerY(), card(result, SIBLING_A).bounds().centerY());
        assertEquals(card(result, ROOT).bounds().centerY(), card(result, SIBLING_B).bounds().centerY());
        assertTrue(result.cards().stream().noneMatch(card -> card.uuid().equals(SIBLING_CHILD)));
        int anchorX = card(result, ROOT).bounds().centerX();
        int minSiblingX = Math.min(card(result, SIBLING_A).bounds().centerX(), card(result, SIBLING_B).bounds().centerX());
        int maxSiblingX = Math.max(card(result, SIBLING_A).bounds().centerX(), card(result, SIBLING_B).bounds().centerX());
        assertTrue(minSiblingX < anchorX);
        assertTrue(maxSiblingX > anchorX);
    }

    @Test
    void expandedSiblingDescendantsAppearBelowSiblingWithParentEdgeAndNoCardOverlap() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode father = node(FATHER);
        FamilyTreeNode siblingA = node(SIBLING_A);
        FamilyTreeNode siblingB = node(SIBLING_B);
        FamilyTreeNode niece = node(SIBLING_CHILD);
        FamilyTreeNode child = node(CHILD_A);
        root.setFather(father);
        siblingA.setFather(father);
        siblingB.setFather(father);
        niece.setFather(siblingA);
        child.setFather(root);

        FamilyTreeLayout.Result result = layout(nodes(root, father, siblingA, siblingB, niece, child), Set.of());

        assertTrue(card(result, SIBLING_CHILD).bounds().centerY() > card(result, SIBLING_A).bounds().centerY());
        assertTrue(result.edges().stream().anyMatch(edge -> edge.type() == FamilyTreeLayout.EdgeType.PARENT_CHILD
                && edge.from().equals(SIBLING_A) && edge.to().equals(SIBLING_CHILD)));
        assertEquals(0, card(result, ROOT).bounds().centerX());
        for (int i = 0; i < result.cards().size(); i++) {
            for (int j = i + 1; j < result.cards().size(); j++) {
                assertFalse(result.cards().get(i).bounds().intersects(result.cards().get(j).bounds()),
                        result.cards().get(i).uuid() + " overlaps " + result.cards().get(j).uuid());
            }
        }
    }

    @Test
    void childrenAndGrandchildrenOccupyLowerGenerations() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode child = node(CHILD_A);
        FamilyTreeNode grandchild = node(GRANDCHILD);
        child.setFather(root);
        grandchild.setFather(child);

        FamilyTreeLayout.Result result = layout(nodes(root, child, grandchild), Set.of());

        assertTrue(card(result, CHILD_A).bounds().centerY() > card(result, ROOT).bounds().centerY());
        assertTrue(card(result, GRANDCHILD).bounds().centerY() > card(result, CHILD_A).bounds().centerY());
    }

    @Test
    void layoutContentBoundsDoNotDependOnUnrenderedExpansionFrontiers() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode father = node(FATHER);
        root.setFather(father);
        FamilyTreeView.Continuation ancestors = new FamilyTreeView.Continuation(FATHER, ANCESTORS);
        FamilyTreeView.Continuation descendants = new FamilyTreeView.Continuation(ROOT, DESCENDANTS);

        FamilyTreeLayout.Result result = layout(nodes(root, father), Set.of(ancestors, descendants));

        assertTrue(result.contentBounds().contains(card(result, FATHER).bounds()));
        assertTrue(result.contentBounds().contains(card(result, ROOT).bounds()));
    }

    @Test
    void wideFamilyCardsDoNotOverlap() {
        FamilyTreeNode root = node(ROOT);
        Map<UUID, FamilyTreeNode> nodes = new LinkedHashMap<>();
        nodes.put(ROOT, root);
        for (int i = 20; i < 30; i++) {
            FamilyTreeNode child = node(uuid(i));
            child.setFather(root);
            nodes.put(child.id(), child);
        }

        FamilyTreeLayout.Result result = layout(nodes, Set.of());
        List<FamilyTreeLayout.Card> cards = result.cards();

        for (int i = 0; i < cards.size(); i++) {
            for (int j = i + 1; j < cards.size(); j++) {
                assertFalse(cards.get(i).bounds().intersects(cards.get(j).bounds()),
                        cards.get(i).uuid() + " overlaps " + cards.get(j).uuid());
            }
        }
    }

    @Test
    void revealingUnpartneredSiblingBesideExistingCardKeepsLayoutUsable() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode firstChild = node(CHILD_A);
        firstChild.setFather(root);
        FamilyTreeLayout.Result before = layout(nodes(root, firstChild), Set.of());

        FamilyTreeNode newChild = node(CHILD_B);
        newChild.setFather(root);
        FamilyTreeLayout.Result after = FamilyTreeLayout.layout(
                new FamilyTreeViewModel.Snapshot(ROOT, nodes(root, firstChild, newChild), Set.of()), before);

        assertEquals(3, after.cards().size());
        assertEquals(card(before, ROOT).bounds(), card(after, ROOT).bounds());
        assertSeparatedFamilyCards(after);
    }

    @Test
    void repeatedExpansionAnchorsSelectedGenerationAndMovesOnlyNearbyBranches() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode father = node(FATHER);
        FamilyTreeNode siblingA = node(SIBLING_A);
        FamilyTreeNode siblingB = node(SIBLING_B);
        FamilyTreeNode childA = node(CHILD_A);
        FamilyTreeNode siblingChild = node(SIBLING_CHILD);
        root.setFather(father);
        siblingA.setFather(father);
        siblingB.setFather(father);
        childA.setFather(root);
        siblingChild.setFather(siblingB);
        Map<UUID, FamilyTreeNode> family = nodes(root, father, siblingA, siblingB, childA, siblingChild);

        FamilyTreeLayout.Result before = layout(family, Set.of());
        Map<UUID, FamilyTreeLayout.Bounds> initial = cardBoundsById(before);
        // A distant sibling gains a large new branch while the original cards stay known.
        for (int i = 100; i < 106; i++) {
            FamilyTreeNode cousin = node(uuid(i));
            cousin.setFather(siblingA);
            family.put(cousin.id(), cousin);
        }
        FamilyTreeViewModel.Snapshot snapshot = new FamilyTreeViewModel.Snapshot(ROOT, family, Set.of());
        FamilyTreeLayout.Result firstExpansion = FamilyTreeLayout.layout(snapshot, before);
        assertEquals(initial.get(ROOT), card(firstExpansion, ROOT).bounds());
        assertEquals(initial.get(SIBLING_B), card(firstExpansion, SIBLING_B).bounds(),
                "The branch on the opposite side of the root has no new clearance demand");
        assertTrue(partnerGap(firstExpansion, SIBLING_A, ROOT) <= 84);
        assertBoundedReflow(initial, firstExpansion, 6 * 200);
        assertSeparatedFamilyCards(firstExpansion);

        for (int i = 110; i < 115; i++) {
            FamilyTreeNode cousin = node(uuid(i));
            cousin.setFather(siblingB);
            family.put(cousin.id(), cousin);
        }
        FamilyTreeLayout.Result secondExpansion = FamilyTreeLayout.layout(
                new FamilyTreeViewModel.Snapshot(ROOT, family, Set.of()), firstExpansion);
        assertEquals(initial.get(ROOT), card(secondExpansion, ROOT).bounds());
        assertEquals(card(firstExpansion, SIBLING_A).bounds(), card(secondExpansion, SIBLING_A).bounds(),
                "Expanding the other branch must not shift the branch that already made room");
        assertTrue(partnerGap(secondExpansion, ROOT, SIBLING_B) <= 84);
        assertBoundedReflow(cardBoundsById(firstExpansion), secondExpansion, 5 * 200);
        assertSeparatedFamilyCards(secondExpansion);
        assertEquals(family.size(), secondExpansion.cards().size());
    }

    @Test
    void addingAWideBranchMakesLocalRoomInsteadOfSendingAllNewChildrenToOneSide() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode father = node(FATHER);
        FamilyTreeNode sibling = node(SIBLING_A);
        FamilyTreeNode originalChild = node(CHILD_A);
        FamilyTreeNode cousin = node(SIBLING_CHILD);
        root.setFather(father);
        sibling.setFather(father);
        originalChild.setFather(root);
        cousin.setFather(sibling);
        Map<UUID, FamilyTreeNode> family = nodes(root, father, sibling, originalChild, cousin);
        FamilyTreeLayout.Result before = layout(family, Set.of());

        Set<UUID> added = new HashSet<>();
        for (int i = 100; i < 108; i++) {
            FamilyTreeNode child = node(uuid(i));
            child.setFather(root);
            added.add(child.id());
            family.put(child.id(), child);
        }
        FamilyTreeLayout.Result after = FamilyTreeLayout.layout(
                new FamilyTreeViewModel.Snapshot(ROOT, family, Set.of()), before);

        assertEquals(card(before, ROOT).bounds(), card(after, ROOT).bounds(),
                "The selected root must be anchored during expansion");
        assertTrue(!card(before, CHILD_A).bounds().equals(card(after, CHILD_A).bounds())
                        || !card(before, SIBLING_CHILD).bounds().equals(card(after, SIBLING_CHILD).bounds()),
                "Nearby existing branches should slide to create a lane for new relatives");
        int parentX = card(after, ROOT).bounds().centerX();
        assertTrue(added.stream().mapToInt(id -> card(after, id).bounds().centerX()).min().orElseThrow() < parentX);
        assertTrue(added.stream().mapToInt(id -> card(after, id).bounds().centerX()).max().orElseThrow() > parentX,
                "New siblings should fan around their parent, rather than trail off one side");
        assertSeparatedFamilyCards(after);

        FamilyTreeTransition transition = new FamilyTreeTransition(before, after, ROOT);
        FamilyTreeLayout.Result halfway = transition.at(0.5);
        for (UUID id : List.of(CHILD_A, SIBLING_CHILD)) {
            int start = card(before, id).bounds().centerX();
            int end = card(after, id).bounds().centerX();
            if (start != end) {
                int middle = card(halfway, id).bounds().centerX();
                assertTrue(middle > Math.min(start, end) && middle < Math.max(start, end),
                        "An already-visible neighbor should move through intermediate positions");
            }
        }
    }

    @Test
    void switchingToAnAlreadyVisibleRootKeepsSharedRelativesInPlace() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode father = node(FATHER);
        FamilyTreeNode child = node(CHILD_A);
        FamilyTreeNode grandchild = node(SIBLING_CHILD);
        root.setFather(father);
        child.setFather(root);
        grandchild.setFather(child);
        Map<UUID, FamilyTreeNode> family = nodes(root, father, child, grandchild);
        FamilyTreeLayout.Result before = layout(family, Set.of());

        FamilyTreeLayout.Result after = FamilyTreeLayout.layout(
                new FamilyTreeViewModel.Snapshot(CHILD_A, family, Set.of()), before);

        assertEquals(card(before, CHILD_A).bounds(), card(after, CHILD_A).bounds(),
                "Search navigation to a visible child should preserve its screen location");
        assertEquals(card(before, ROOT).bounds(), card(after, ROOT).bounds(),
                "A newly selected root should not displace an already visible parent");
        assertEquals(card(before, SIBLING_CHILD).bounds(), card(after, SIBLING_CHILD).bounds());
    }

    @Test
    void expandedSiblingsFormOneClusterWithoutUnrelatedCardsBetweenThem() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode father = node(FATHER);
        FamilyTreeNode sibling = node(SIBLING_A);
        FamilyTreeNode originalChild = node(CHILD_A);
        root.setFather(father);
        sibling.setFather(father);
        originalChild.setFather(root);
        Map<UUID, FamilyTreeNode> family = nodes(root, father, sibling, originalChild);
        FamilyTreeLayout.Result before = layout(family, Set.of());

        Set<UUID> newChildren = new HashSet<>();
        for (int i = 100; i < 132; i++) {
            FamilyTreeNode child = node(uuid(i));
            child.setFather(sibling);
            family.put(child.id(), child);
            newChildren.add(child.id());
        }
        FamilyTreeLayout.Result after = FamilyTreeLayout.layout(
                new FamilyTreeViewModel.Snapshot(ROOT, family, Set.of()), before);

        assertEquals(card(before, ROOT).bounds(), card(after, ROOT).bounds());
        List<FamilyTreeLayout.Card> row = after.cards().stream()
                .filter(card -> card.bounds().centerY() == card(after, CHILD_A).bounds().centerY())
                .sorted(Comparator.comparingInt(card -> card.bounds().centerX()))
                .toList();
        int first = -1;
        int last = -1;
        for (int i = 0; i < row.size(); i++) {
            if (newChildren.contains(row.get(i).uuid())) {
                if (first < 0) first = i;
                last = i;
            }
        }
        assertEquals(newChildren.size(), last - first + 1,
                "New siblings should remain a contiguous branch rather than surround unrelated cards");
        int parentX = card(after, SIBLING_A).bounds().centerX();
        for (UUID child : newChildren) {
            assertTrue(Math.abs(card(after, child).bounds().centerX() - parentX) < 3700,
                    "Children should stay within a reasonable span of their parent");
        }
    }

    @Test
    void expandedSiblingGroupKeepsNewlyDiscoveredCoupleAdjacent() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode existingChild = node(CHILD_A);
        existingChild.setFather(root);
        Map<UUID, FamilyTreeNode> family = nodes(root, existingChild);
        FamilyTreeLayout.Result before = layout(family, Set.of());

        UUID partneredChildId = uuid(102);
        UUID partnerId = uuid(200);
        for (int i = 100; i < 105; i++) {
            FamilyTreeNode child = node(uuid(i));
            child.setFather(root);
            family.put(child.id(), child);
        }
        FamilyTreeNode partner = node(partnerId);
        family.get(partneredChildId).updatePartner(partner);
        partner.updatePartner(family.get(partneredChildId));
        family.put(partnerId, partner);
        FamilyTreeLayout.Result after = FamilyTreeLayout.layout(
                new FamilyTreeViewModel.Snapshot(ROOT, family, Set.of()), before);

        assertEquals(card(before, CHILD_A).bounds(), card(after, CHILD_A).bounds());
        assertEquals(FamilyTreeLayout.CARD_WIDTH + FamilyTreeLayout.PARTNER_GAP,
                Math.abs(card(after, partneredChildId).bounds().centerX()
                        - card(after, partnerId).bounds().centerX()),
                "Spouses should remain directly adjacent after a generation expansion");
    }

    @Test
    void separateDescendantBranchesHaveMoreRoomThanSiblingsBeforeAndAfterExpansion() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode father = node(FATHER);
        FamilyTreeNode sibling = node(SIBLING_A);
        FamilyTreeNode childA = node(CHILD_A);
        FamilyTreeNode childB = node(CHILD_B);
        FamilyTreeNode cousinA = node(uuid(70));
        FamilyTreeNode cousinB = node(uuid(71));
        root.setFather(father);
        sibling.setFather(father);
        childA.setFather(root);
        childB.setFather(root);
        cousinA.setFather(sibling);
        cousinB.setFather(sibling);
        Map<UUID, FamilyTreeNode> family = nodes(root, father, sibling, childA, childB, cousinA, cousinB);

        FamilyTreeLayout.Result before = layout(family, Set.of());
        assertDistinctBranchSpacing(before, Set.of(CHILD_A, CHILD_B), Set.of(cousinA.id(), cousinB.id()));

        Set<UUID> cousins = new HashSet<>(Set.of(cousinA.id(), cousinB.id()));
        for (int i = 72; i < 76; i++) {
            FamilyTreeNode cousin = node(uuid(i));
            cousin.setFather(sibling);
            family.put(cousin.id(), cousin);
            cousins.add(cousin.id());
        }
        FamilyTreeLayout.Result after = FamilyTreeLayout.layout(
                new FamilyTreeViewModel.Snapshot(ROOT, family, Set.of()), before);
        assertEquals(card(before, ROOT).bounds(), card(after, ROOT).bounds());
        assertDistinctBranchSpacing(after, Set.of(CHILD_A, CHILD_B), cousins);
    }

    private static void assertDistinctBranchSpacing(FamilyTreeLayout.Result result,
                                                    Set<UUID> firstBranch, Set<UUID> secondBranch) {
        int firstLeft = firstBranch.stream().mapToInt(id -> card(result, id).bounds().left()).min().orElseThrow();
        int firstRight = firstBranch.stream().mapToInt(id -> card(result, id).bounds().right()).max().orElseThrow();
        int secondLeft = secondBranch.stream().mapToInt(id -> card(result, id).bounds().left()).min().orElseThrow();
        int secondRight = secondBranch.stream().mapToInt(id -> card(result, id).bounds().right()).max().orElseThrow();
        int between = firstLeft < secondLeft ? secondLeft - firstRight : firstLeft - secondRight;
        assertTrue(between >= 112, "Separate descendant branches should have a clear visual gutter. "
                + "First: [" + firstLeft + ", " + firstRight + "], second: ["
                + secondLeft + ", " + secondRight + "], actual gap: " + between);
    }

    private static void assertBoundedReflow(Map<UUID, FamilyTreeLayout.Bounds> before,
                                             FamilyTreeLayout.Result after, int maxMovement) {
        for (FamilyTreeLayout.Card card : after.cards()) {
            FamilyTreeLayout.Bounds old = before.get(card.uuid());
            if (old != null) {
                assertEquals(old.centerY(), card.bounds().centerY(), "Existing generation changed");
                assertTrue(Math.abs(old.centerX() - card.bounds().centerX()) <= maxMovement,
                        "An existing branch moved farther than the width of the revealed branch");
            }
        }
    }

    private static void assertSeparatedFamilyCards(FamilyTreeLayout.Result result) {
        for (int i = 0; i < result.cards().size(); i++) {
            for (int j = i + 1; j < result.cards().size(); j++) {
                FamilyTreeLayout.Bounds a = result.cards().get(i).bounds();
                FamilyTreeLayout.Bounds b = result.cards().get(j).bounds();
                if (a.centerY() == b.centerY() && !a.intersects(b)) {
                    assertTrue(Math.abs(a.centerX() - b.centerX()) >= FamilyTreeLayout.CARD_WIDTH
                            + FamilyTreeLayout.PARTNER_GAP, "Cards are visually cramped");
                }
                assertFalse(a.intersects(b), "Overlapping cards");
            }
        }
    }

    private static Map<UUID, FamilyTreeLayout.Bounds> cardBoundsById(FamilyTreeLayout.Result result) {
        Map<UUID, FamilyTreeLayout.Bounds> bounds = new HashMap<>();
        for (FamilyTreeLayout.Card card : result.cards()) {
            bounds.put(card.uuid(), card.bounds());
        }
        return bounds;
    }

    @Test
    void cyclicReferencesDoNotDuplicateCardsIndefinitely() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode child = node(CHILD_A);
        root.addChild(CHILD_A);
        child.addChild(ROOT);

        FamilyTreeLayout.Result result = layout(nodes(root, child), Set.of());

        assertEquals(2, result.cards().size());
        assertEquals(2, result.cards().stream().map(FamilyTreeLayout.Card::uuid).distinct().count());
    }

    @Test
    void aSelfPartnerRecordStillDisplaysThePersonOnce() {
        FamilyTreeNode root = node(ROOT);
        root.updatePartner(root);

        FamilyTreeLayout.Result result = layout(nodes(root), Set.of());

        assertEquals(List.of(ROOT), result.cards().stream().map(FamilyTreeLayout.Card::uuid).toList());
        assertTrue(result.edges().isEmpty(), "A card cannot have a partner link to itself");
    }

    @Test
    void contentBoundsContainEveryCardWithoutHiddenExpansionControls() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode childA = node(CHILD_A);
        FamilyTreeNode childB = node(CHILD_B);
        childA.setFather(root);
        childB.setFather(root);
        FamilyTreeView.Continuation continuation = new FamilyTreeView.Continuation(CHILD_A, DESCENDANTS);

        FamilyTreeLayout.Result result = layout(nodes(root, childA, childB), Set.of(continuation));

        for (FamilyTreeLayout.Card card : result.cards()) {
            assertTrue(result.contentBounds().contains(card.bounds()));
        }
    }

    private static FamilyTreeLayout.Result layout(
            Map<UUID, FamilyTreeNode> nodes,
            Set<FamilyTreeView.Continuation> continuations
    ) {
        FamilyTreeViewModel.Snapshot snapshot =
                new FamilyTreeViewModel.Snapshot(ROOT, nodes, continuations);
        return FamilyTreeLayout.layout(snapshot);
    }

    private static FamilyTreeLayout.Card card(FamilyTreeLayout.Result result, UUID uuid) {
        return result.cards().stream().filter(card -> card.uuid().equals(uuid)).findFirst().orElseThrow();
    }

    private static Map<UUID, FamilyTreeNode> nodes(FamilyTreeNode... values) {
        Map<UUID, FamilyTreeNode> nodes = new LinkedHashMap<>();
        for (FamilyTreeNode value : values) {
            nodes.put(value.id(), value);
        }
        return nodes;
    }

    private static FamilyTreeNode node(UUID id) {
        return new FamilyTreeNode(null, id, id.toString(), false, Gender.MALE, Util.NIL_UUID, Util.NIL_UUID);
    }

}
