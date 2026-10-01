package net.conczin.mca.client.gui;

import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static net.conczin.mca.client.gui.FamilyTreeRelationshipResolver.Relation.CHILD;
import static net.conczin.mca.client.gui.FamilyTreeRelationshipResolver.Relation.FATHER;
import static net.conczin.mca.client.gui.FamilyTreeRelationshipResolver.Relation.GRANDCHILD;
import static net.conczin.mca.client.gui.FamilyTreeRelationshipResolver.Relation.GRANDPARENT;
import static net.conczin.mca.client.gui.FamilyTreeRelationshipResolver.Relation.MOTHER;
import static net.conczin.mca.client.gui.FamilyTreeRelationshipResolver.Relation.OTHER;
import static net.conczin.mca.client.gui.FamilyTreeRelationshipResolver.Relation.PARTNER;
import static net.conczin.mca.client.gui.FamilyTreeRelationshipResolver.Relation.SELF;
import static net.conczin.mca.client.gui.FamilyTreeRelationshipResolver.Relation.SIBLING;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FamilyTreeRelationshipResolverTest {
    private static final UUID ROOT = uuid(1);
    private static final UUID FATHER_ID = uuid(2);
    private static final UUID MOTHER_ID = uuid(3);
    private static final UUID SIBLING_ID = uuid(4);
    private static final UUID PARTNER_ID = uuid(5);
    private static final UUID CHILD_ID = uuid(6);
    private static final UUID GRANDPARENT_ID = uuid(7);
    private static final UUID GRANDCHILD_ID = uuid(8);
    private static final UUID OTHER_ID = uuid(9);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void resolvesSelf() {
        Fixture fixture = fixture();
        assertEquals(SELF, FamilyTreeRelationshipResolver.resolve(ROOT, ROOT, fixture.nodes));
    }

    @Test
    void resolvesFather() {
        Fixture fixture = fixture();
        assertEquals(FATHER, FamilyTreeRelationshipResolver.resolve(ROOT, FATHER_ID, fixture.nodes));
    }

    @Test
    void resolvesMother() {
        Fixture fixture = fixture();
        assertEquals(MOTHER, FamilyTreeRelationshipResolver.resolve(ROOT, MOTHER_ID, fixture.nodes));
    }

    @Test
    void resolvesChild() {
        Fixture fixture = fixture();
        assertEquals(CHILD, FamilyTreeRelationshipResolver.resolve(ROOT, CHILD_ID, fixture.nodes));
    }

    @Test
    void resolvesSibling() {
        Fixture fixture = fixture();
        assertEquals(SIBLING, FamilyTreeRelationshipResolver.resolve(ROOT, SIBLING_ID, fixture.nodes));
    }

    @Test
    void resolvesGrandparent() {
        Fixture fixture = fixture();
        assertEquals(GRANDPARENT, FamilyTreeRelationshipResolver.resolve(ROOT, GRANDPARENT_ID, fixture.nodes));
    }

    @Test
    void resolvesGrandchild() {
        Fixture fixture = fixture();
        assertEquals(GRANDCHILD, FamilyTreeRelationshipResolver.resolve(ROOT, GRANDCHILD_ID, fixture.nodes));
    }

    @Test
    void resolvesPartner() {
        Fixture fixture = fixture();
        assertEquals(PARTNER, FamilyTreeRelationshipResolver.resolve(ROOT, PARTNER_ID, fixture.nodes));
    }

    @Test
    void unrelatedFallsBackToOther() {
        Fixture fixture = fixture();
        assertEquals(OTHER, FamilyTreeRelationshipResolver.resolve(ROOT, OTHER_ID, fixture.nodes));
    }

    @Test
    void corruptCycleTerminatesAsOther() {
        FamilyTreeNode root = node(ROOT, Gender.MALE);
        FamilyTreeNode child = node(CHILD_ID, Gender.MALE);
        FamilyTreeNode other = node(OTHER_ID, Gender.FEMALE);
        root.addChild(CHILD_ID);
        child.addChild(ROOT);
        Map<UUID, FamilyTreeNode> nodes = Map.of(ROOT, root, CHILD_ID, child, OTHER_ID, other);

        assertEquals(OTHER, FamilyTreeRelationshipResolver.resolve(ROOT, OTHER_ID, nodes));
    }

    private static Fixture fixture() {
        FamilyTreeNode root = node(ROOT, Gender.MALE);
        FamilyTreeNode father = node(FATHER_ID, Gender.MALE);
        FamilyTreeNode mother = node(MOTHER_ID, Gender.FEMALE);
        FamilyTreeNode sibling = node(SIBLING_ID, Gender.FEMALE);
        FamilyTreeNode partner = node(PARTNER_ID, Gender.FEMALE);
        FamilyTreeNode child = node(CHILD_ID, Gender.MALE);
        FamilyTreeNode grandparent = node(GRANDPARENT_ID, Gender.MALE);
        FamilyTreeNode grandchild = node(GRANDCHILD_ID, Gender.FEMALE);
        FamilyTreeNode other = node(OTHER_ID, Gender.MALE);

        root.setFather(father);
        root.setMother(mother);
        sibling.setFather(father);
        root.updatePartner(partner);
        child.setFather(root);
        father.setFather(grandparent);
        grandchild.setFather(child);

        Map<UUID, FamilyTreeNode> nodes = new LinkedHashMap<>();
        for (FamilyTreeNode node : new FamilyTreeNode[]{
                root, father, mother, sibling, partner, child, grandparent, grandchild, other
        }) {
            nodes.put(node.id(), node);
        }
        return new Fixture(nodes);
    }

    private static FamilyTreeNode node(UUID id, Gender gender) {
        return new FamilyTreeNode(null, id, id.toString(), false, gender, Util.NIL_UUID, Util.NIL_UUID);
    }

    private record Fixture(Map<UUID, FamilyTreeNode> nodes) {
    }

    private static UUID uuid(int value) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", value));
    }
}
