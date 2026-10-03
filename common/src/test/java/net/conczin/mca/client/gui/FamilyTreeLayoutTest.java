package net.conczin.mca.client.gui;

import net.conczin.mca.FamilyTreeTestSupport;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.FamilyTreeView;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.util.Util;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.conczin.mca.network.FamilyTreeView.Direction.ANCESTORS;
import static net.conczin.mca.network.FamilyTreeView.Direction.DESCENDANTS;
import static net.conczin.mca.FamilyTreeTestSupport.uuid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void changingFocusDoesNotReorientTheBranch() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode partner = node(PARTNER);
        FamilyTreeNode father = node(FATHER);
        root.updatePartner(partner);
        root.setFather(father);
        Map<UUID, FamilyTreeNode> nodes = nodes(root, partner, father);

        FamilyTreeLayout.Result rootFocused = layout(ROOT, ROOT, nodes, Set.of());
        FamilyTreeLayout.Result partnerFocused = layout(ROOT, PARTNER, nodes, Set.of());

        for (UUID id : nodes.keySet()) {
            assertEquals(card(rootFocused, id).bounds(), card(partnerFocused, id).bounds());
        }
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

        FamilyTreeLayout.Result result = layout(nodes(root, father, siblingA, siblingB, siblingChild), Set.of());

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
    void continuationControlSitsBeyondItsBoundaryGeneration() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode father = node(FATHER);
        root.setFather(father);
        FamilyTreeView.Continuation ancestors = new FamilyTreeView.Continuation(FATHER, ANCESTORS);
        FamilyTreeView.Continuation descendants = new FamilyTreeView.Continuation(ROOT, DESCENDANTS);

        FamilyTreeLayout.Result result = layout(nodes(root, father), Set.of(ancestors, descendants));

        FamilyTreeLayout.ContinuationControl up = continuation(result, ancestors);
        FamilyTreeLayout.ContinuationControl down = continuation(result, descendants);
        assertTrue(up.bounds().centerY() < card(result, FATHER).bounds().centerY());
        assertTrue(down.bounds().centerY() > card(result, ROOT).bounds().centerY());
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
    void contentBoundsContainEveryCardAndContinuation() {
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
        for (FamilyTreeLayout.ContinuationControl control : result.continuations()) {
            assertTrue(result.contentBounds().contains(control.bounds()));
        }
    }

    private static FamilyTreeLayout.Result layout(
            Map<UUID, FamilyTreeNode> nodes,
            Set<FamilyTreeView.Continuation> continuations
    ) {
        return layout(ROOT, ROOT, nodes, continuations);
    }

    private static FamilyTreeLayout.Result layout(
            UUID layoutRoot,
            UUID focus,
            Map<UUID, FamilyTreeNode> nodes,
            Set<FamilyTreeView.Continuation> continuations
    ) {
        FamilyTreeViewModel.Snapshot snapshot =
                new FamilyTreeViewModel.Snapshot(layoutRoot, nodes, continuations);
        return FamilyTreeLayout.layout(snapshot);
    }

    private static FamilyTreeLayout.Card card(FamilyTreeLayout.Result result, UUID uuid) {
        return result.cards().stream().filter(card -> card.uuid().equals(uuid)).findFirst().orElseThrow();
    }

    private static FamilyTreeLayout.ContinuationControl continuation(
            FamilyTreeLayout.Result result,
            FamilyTreeView.Continuation continuation
    ) {
        FamilyTreeLayout.ContinuationControl control = result.continuations().stream()
                .filter(item -> item.anchor().equals(continuation.anchor()) && item.direction() == continuation.direction())
                .findFirst()
                .orElse(null);
        assertNotNull(control);
        return control;
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
