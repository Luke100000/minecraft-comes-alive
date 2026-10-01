package net.conczin.mca.server.world.data;

import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.FamilyTreeView;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static net.conczin.mca.network.FamilyTreeView.Direction.ANCESTORS;
import static net.conczin.mca.network.FamilyTreeView.Direction.DESCENDANTS;
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

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
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
    void requestedDepthIsClampedToFour() {
        FamilyTree tree = tree();
        FamilyTreeNode root = node(tree, ROOT, Gender.MALE);
        FamilyTreeNode child1 = node(tree, CHILD, Gender.MALE);
        FamilyTreeNode child2 = node(tree, DESCENDANT_2, Gender.FEMALE);
        FamilyTreeNode child3 = node(tree, DESCENDANT_3, Gender.MALE);
        FamilyTreeNode child4 = node(tree, DESCENDANT_4, Gender.FEMALE);
        FamilyTreeNode child5 = node(tree, DESCENDANT_5, Gender.MALE);
        child1.setFather(root);
        child2.setFather(child1);
        child3.setFather(child2);
        child4.setFather(child3);
        child5.setFather(child4);

        FamilyTreeView view = FamilyTreeViewBuilder.build(tree, ROOT, -5, 99).orElseThrow();

        assertEquals(Set.of(ROOT, CHILD, DESCENDANT_2, DESCENDANT_3, DESCENDANT_4), view.nodes().keySet());
        assertEquals(Set.of(new FamilyTreeView.Continuation(DESCENDANT_4, DESCENDANTS)), view.continuations());
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
    void missingRootReturnsEmpty() {
        Optional<FamilyTreeView> view = FamilyTreeViewBuilder.build(tree(), ROOT, 2, 2);

        assertTrue(view.isEmpty());
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

    private static UUID uuid(int value) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", value));
    }
}
