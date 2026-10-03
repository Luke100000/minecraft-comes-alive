package net.conczin.mca.server.world.data;

import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.FamilyTreeTestSupport;
import net.conczin.mca.network.FamilyTreeView;
import net.minecraft.util.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static net.conczin.mca.network.FamilyTreeView.Direction.ANCESTORS;
import static net.conczin.mca.network.FamilyTreeView.Direction.DESCENDANTS;
import static net.conczin.mca.FamilyTreeTestSupport.uuid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FamilyTreeViewBuilderTest {
    private static final UUID ROOT = uuid(1);
    private static final UUID FATHER = uuid(2);
    private static final UUID MOTHER = uuid(3);
    private static final UUID PATERNAL_GRANDFATHER = uuid(4);
    private static final UUID MATERNAL_GRANDMOTHER = uuid(5);
    private static final UUID SIBLING = uuid(6);
    private static final UUID PARTNER = uuid(7);
    private static final UUID CHILD = uuid(8);
    private static final UUID GRANDCHILD = uuid(9);
    private static final UUID DANGLING = uuid(10);
    private static final UUID DESCENDANT_2 = uuid(11);
    private static final UUID DESCENDANT_3 = uuid(12);
    private static final UUID DESCENDANT_4 = uuid(13);
    private static final UUID DESCENDANT_5 = uuid(14);
    private static final UUID DESCENDANT_6 = uuid(15);
    private static final UUID DESCENDANT_7 = uuid(16);
    private static final UUID DESCENDANT_8 = uuid(17);
    private static final UUID DESCENDANT_9 = uuid(18);

    @BeforeAll
    static void bootstrapMinecraft() {
        FamilyTreeTestSupport.bootstrapMinecraft();
    }

    @Test
    void defaultViewIncludesParentsGrandparentsPartnerChildrenGrandchildrenAndSiblings() {
        FamilyTree tree = tree();
        FamilyTreeNode root = node(tree, ROOT, Gender.MALE);
        FamilyTreeNode father = node(tree, FATHER, Gender.MALE);
        FamilyTreeNode mother = node(tree, MOTHER, Gender.FEMALE);
        FamilyTreeNode paternalGrandfather = node(tree, PATERNAL_GRANDFATHER, Gender.MALE);
        FamilyTreeNode maternalGrandmother = node(tree, MATERNAL_GRANDMOTHER, Gender.FEMALE);
        FamilyTreeNode sibling = node(tree, SIBLING, Gender.FEMALE);
        FamilyTreeNode partner = node(tree, PARTNER, Gender.FEMALE);
        FamilyTreeNode child = node(tree, CHILD, Gender.MALE);
        FamilyTreeNode grandchild = node(tree, GRANDCHILD, Gender.FEMALE);

        root.setFather(father);
        root.setMother(mother);
        father.setFather(paternalGrandfather);
        mother.setMother(maternalGrandmother);
        sibling.setFather(father);
        sibling.setMother(mother);
        root.updatePartner(partner);
        child.setFather(root);
        grandchild.setFather(child);

        FamilyTreeView view = FamilyTreeViewBuilder.build(tree, ROOT, 2, 2).orElseThrow();

        assertEquals(Set.of(
                ROOT,
                FATHER,
                MOTHER,
                PATERNAL_GRANDFATHER,
                MATERNAL_GRANDMOTHER,
                SIBLING,
                PARTNER,
                CHILD,
                GRANDCHILD
        ), view.nodes().keySet());
        assertEquals(Set.of(), view.continuations());
        assertEquals(Set.of(), view.unavailable());
    }

    @Test
    void partnerAncestorsExpandWithoutRefocusingOnThePartner() {
        FamilyTree tree = tree();
        FamilyTreeNode root = node(tree, ROOT, Gender.MALE);
        FamilyTreeNode partner = node(tree, PARTNER, Gender.FEMALE);
        FamilyTreeNode partnerFather = node(tree, FATHER, Gender.MALE);
        FamilyTreeNode partnerGrandfather = node(tree, PATERNAL_GRANDFATHER, Gender.MALE);
        root.updatePartner(partner);
        partner.setFather(partnerFather);
        partnerFather.setFather(partnerGrandfather);

        FamilyTreeView view = FamilyTreeViewBuilder.build(tree, ROOT, 2, 0).orElseThrow();

        assertEquals(Set.of(ROOT, PARTNER, FATHER, PATERNAL_GRANDFATHER), view.nodes().keySet());
        assertEquals(Set.of(), view.continuations());
    }

    @Test
    void boundaryWithResolvableParentsCreatesAncestorContinuation() {
        FamilyTree tree = tree();
        FamilyTreeNode root = node(tree, ROOT, Gender.MALE);
        FamilyTreeNode father = node(tree, FATHER, Gender.MALE);
        FamilyTreeNode grandfather = node(tree, PATERNAL_GRANDFATHER, Gender.MALE);
        root.setFather(father);
        father.setFather(grandfather);

        FamilyTreeView view = FamilyTreeViewBuilder.build(tree, ROOT, 1, 0).orElseThrow();

        assertEquals(Set.of(ROOT, FATHER), view.nodes().keySet());
        assertEquals(Set.of(new FamilyTreeView.Continuation(FATHER, ANCESTORS)), view.continuations());
        assertEquals(Set.of(), view.unavailable());
    }

    @Test
    void boundaryWithResolvableChildrenCreatesDescendantContinuation() {
        FamilyTree tree = tree();
        FamilyTreeNode root = node(tree, ROOT, Gender.MALE);
        FamilyTreeNode child = node(tree, CHILD, Gender.MALE);
        FamilyTreeNode grandchild = node(tree, GRANDCHILD, Gender.FEMALE);
        child.setFather(root);
        grandchild.setFather(child);

        FamilyTreeView view = FamilyTreeViewBuilder.build(tree, ROOT, 0, 1).orElseThrow();

        assertEquals(Set.of(ROOT, CHILD), view.nodes().keySet());
        assertEquals(Set.of(new FamilyTreeView.Continuation(CHILD, DESCENDANTS)), view.continuations());
        assertEquals(Set.of(), view.unavailable());
    }

    @Test
    void validDanglingReferenceIsUnavailableNotContinuation() {
        FamilyTree tree = tree();
        FamilyTreeNode root = node(tree, ROOT, Gender.MALE);
        root.addChild(DANGLING);

        FamilyTreeView view = FamilyTreeViewBuilder.build(tree, ROOT, 0, 0).orElseThrow();

        assertEquals(Set.of(ROOT), view.nodes().keySet());
        assertEquals(Set.of(), view.continuations());
        assertEquals(Set.of(DANGLING), view.unavailable());
    }

    @Test
    void cyclicReferencesTerminateWithinRequestedDepth() {
        FamilyTree tree = tree();
        FamilyTreeNode root = node(tree, ROOT, Gender.MALE);
        FamilyTreeNode child = node(tree, CHILD, Gender.MALE);
        root.addChild(CHILD);
        child.addChild(ROOT);

        FamilyTreeView view = FamilyTreeViewBuilder.build(tree, ROOT, 0, 4).orElseThrow();

        assertEquals(Set.of(ROOT, CHILD), view.nodes().keySet());
        assertEquals(Set.of(), view.continuations());
        assertEquals(Set.of(), view.unavailable());
    }

    @Test
    void requestedDepthIsClampedToEightAndStillOffersContinuation() {
        FamilyTree tree = tree();
        FamilyTreeNode root = node(tree, ROOT, Gender.MALE);
        FamilyTreeNode child1 = node(tree, CHILD, Gender.MALE);
        FamilyTreeNode child2 = node(tree, DESCENDANT_2, Gender.FEMALE);
        FamilyTreeNode child3 = node(tree, DESCENDANT_3, Gender.MALE);
        FamilyTreeNode child4 = node(tree, DESCENDANT_4, Gender.FEMALE);
        FamilyTreeNode child5 = node(tree, DESCENDANT_5, Gender.MALE);
        FamilyTreeNode child6 = node(tree, DESCENDANT_6, Gender.FEMALE);
        FamilyTreeNode child7 = node(tree, DESCENDANT_7, Gender.MALE);
        FamilyTreeNode child8 = node(tree, DESCENDANT_8, Gender.FEMALE);
        FamilyTreeNode child9 = node(tree, DESCENDANT_9, Gender.MALE);
        child1.setFather(root);
        child2.setFather(child1);
        child3.setFather(child2);
        child4.setFather(child3);
        child5.setFather(child4);
        child6.setFather(child5);
        child7.setFather(child6);
        child8.setFather(child7);
        child9.setFather(child8);

        FamilyTreeView view = FamilyTreeViewBuilder.build(tree, ROOT, -5, 99).orElseThrow();

        assertEquals(Set.of(
                ROOT,
                CHILD,
                DESCENDANT_2,
                DESCENDANT_3,
                DESCENDANT_4,
                DESCENDANT_5,
                DESCENDANT_6,
                DESCENDANT_7,
                DESCENDANT_8
        ), view.nodes().keySet());
        assertEquals(Set.of(new FamilyTreeView.Continuation(DESCENDANT_8, DESCENDANTS)), view.continuations());
        assertEquals(Set.of(), view.unavailable());
    }

    @Test
    void builderDoesNotMutateFamilyTree() {
        FamilyTree tree = tree();
        FamilyTreeNode root = node(tree, ROOT, Gender.MALE);
        FamilyTreeNode father = node(tree, FATHER, Gender.MALE);
        FamilyTreeNode partner = node(tree, PARTNER, Gender.FEMALE);
        FamilyTreeNode child = node(tree, CHILD, Gender.MALE);
        root.setFather(father);
        root.updatePartner(partner);
        child.setFather(root);

        Map<UUID, CompoundTag> before = snapshot(root, father, partner, child);

        FamilyTreeViewBuilder.build(tree, ROOT, 4, 4).orElseThrow();

        assertEquals(before, snapshot(root, father, partner, child));
    }

    @Test
    void wideDescendantGenerationStopsAtNodeBudgetAndOffersContinuation() {
        FamilyTree tree = tree();
        FamilyTreeNode root = node(tree, ROOT, Gender.MALE);
        for (int index = 0; index < 300; index++) {
            node(tree, uuid(1_000 + index), Gender.MALE).setFather(root);
        }

        FamilyTreeView view = FamilyTreeViewBuilder.build(tree, ROOT, 0, 1).orElseThrow();

        assertEquals(256, view.nodes().size());
        assertTrue(view.continuations().contains(new FamilyTreeView.Continuation(ROOT, DESCENDANTS)));
    }

    @Test
    void builtViewOwnsDetachedBoundedNodeSnapshots() {
        FamilyTree tree = tree();
        FamilyTreeNode root = node(tree, ROOT, Gender.MALE);
        for (int index = 0; index < 300; index++) {
            node(tree, uuid(2_000 + index), Gender.MALE).setFather(root);
        }

        FamilyTreeView view = FamilyTreeViewBuilder.build(tree, ROOT, 0, 0).orElseThrow();
        FamilyTreeNode snapshot = view.nodes().get(ROOT);
        root.setName("mutated after build");
        root.addChild(uuid(9_999));

        assertEquals(ROOT.toString(), snapshot.getName());
        assertTrue(snapshot.children().size() <= 1);
        assertTrue(!snapshot.children().contains(uuid(9_999)));
    }

    @Test
    void missingRootReturnsEmpty() {
        Optional<FamilyTreeView> view = FamilyTreeViewBuilder.build(tree(), ROOT, 2, 2);

        assertTrue(view.isEmpty());
    }

    @Test
    void builderIncludesOnlyGravesForReturnedNodes() {
        FamilyTree tree = tree();
        node(tree, ROOT, Gender.MALE);
        UUID unrelated = uuid(90_000);
        GlobalPos rootGrave = GlobalPos.of(Level.NETHER, new BlockPos(4, 70, 9));
        GlobalPos unrelatedGrave = GlobalPos.of(Level.END, new BlockPos(40, 80, 90));
        Map<UUID, GlobalPos> graves = Map.of(ROOT, rootGrave, unrelated, unrelatedGrave);

        FamilyTreeView view = FamilyTreeViewBuilder.build(
                tree,
                ROOT,
                0,
                0,
                id -> Optional.ofNullable(graves.get(id))
        ).orElseThrow();

        assertEquals(Map.of(ROOT, rootGrave), view.graves());
        assertEquals(Set.of(ROOT), view.nodes().keySet());
    }

    @Test
    void widowedRootRetainsDeceasedPartnerSoExactGraveRemainsReachable() {
        FamilyTree tree = tree();
        FamilyTreeNode root = tree.getOrCreate(ROOT, ROOT.toString(), Gender.MALE, true);
        FamilyTreeNode partner = node(tree, PARTNER, Gender.FEMALE);
        root.updatePartner(partner);
        partner.updatePartner(root);
        partner.setDeceased(true);
        GlobalPos grave = GlobalPos.of(Level.OVERWORLD, new BlockPos(12, 70, 8));

        root.updatePartner(null, RelationshipState.WIDOW);

        FamilyTreeView view = FamilyTreeViewBuilder.build(
                tree,
                ROOT,
                0,
                0,
                id -> PARTNER.equals(id) ? Optional.of(grave) : Optional.empty()
        ).orElseThrow();

        assertEquals(PARTNER, root.partner());
        assertEquals(ROOT, partner.partner());
        assertEquals(RelationshipState.WIDOW, root.getRelationshipState());
        assertEquals(Set.of(ROOT, PARTNER), view.nodes().keySet());
        assertEquals(Map.of(PARTNER, grave), view.graves());
    }

    @Test
    void endingRelationshipNormallyStillClearsPartnerFromBothSides() {
        FamilyTree tree = tree();
        FamilyTreeNode root = tree.getOrCreate(ROOT, ROOT.toString(), Gender.MALE, true);
        FamilyTreeNode partner = node(tree, PARTNER, Gender.FEMALE);
        root.updatePartner(partner);
        partner.updatePartner(root);

        root.updatePartner(null, RelationshipState.SINGLE);

        assertEquals(Util.NIL_UUID, root.partner());
        assertEquals(Util.NIL_UUID, partner.partner());
        assertEquals(RelationshipState.SINGLE, root.getRelationshipState());
        assertEquals(RelationshipState.SINGLE, partner.getRelationshipState());
    }

    private static FamilyTree tree() {
        return new FamilyTree(null);
    }

    private static FamilyTreeNode node(FamilyTree tree, UUID id, Gender gender) {
        return tree.getOrCreate(id, id.toString(), gender);
    }

    private static Map<UUID, CompoundTag> snapshot(FamilyTreeNode... nodes) {
        Map<UUID, CompoundTag> snapshot = new LinkedHashMap<>();
        for (FamilyTreeNode node : nodes) {
            snapshot.put(node.id(), node.save().copy());
        }
        return snapshot;
    }

}
