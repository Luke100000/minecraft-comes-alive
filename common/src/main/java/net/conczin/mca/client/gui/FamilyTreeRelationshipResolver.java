package net.conczin.mca.client.gui;

import net.conczin.mca.server.world.data.FamilyTreeNode;

import java.util.Map;
import java.util.UUID;

final class FamilyTreeRelationshipResolver {
    public enum Relation {
        SELF,
        FATHER,
        MOTHER,
        CHILD,
        SIBLING,
        GRANDPARENT,
        GRANDCHILD,
        PARTNER,
        OTHER
    }

    private FamilyTreeRelationshipResolver() {
    }

    public static Relation resolve(UUID focus, UUID target, Map<UUID, FamilyTreeNode> nodes) {
        if (focus.equals(target)) {
            return Relation.SELF;
        }

        FamilyTreeNode focusNode = nodes.get(focus);
        FamilyTreeNode targetNode = nodes.get(target);
        if (focusNode == null || targetNode == null) {
            return Relation.OTHER;
        }

        if (target.equals(focusNode.father())) {
            return Relation.FATHER;
        }
        if (target.equals(focusNode.mother())) {
            return Relation.MOTHER;
        }
        if (focusNode.children().contains(target)) {
            return Relation.CHILD;
        }
        if (target.equals(focusNode.partner())) {
            return Relation.PARTNER;
        }
        if (sharesRecordedParent(focusNode, targetNode)) {
            return Relation.SIBLING;
        }
        if (isGrandparent(focusNode, target, nodes)) {
            return Relation.GRANDPARENT;
        }
        if (isGrandchild(focusNode, target, nodes)) {
            return Relation.GRANDCHILD;
        }
        return Relation.OTHER;
    }

    private static boolean sharesRecordedParent(FamilyTreeNode one, FamilyTreeNode two) {
        return sameValid(one.father(), two.father())
                || sameValid(one.father(), two.mother())
                || sameValid(one.mother(), two.father())
                || sameValid(one.mother(), two.mother());
    }

    private static boolean sameValid(UUID one, UUID two) {
        return FamilyTreeNode.isValid(one) && one.equals(two);
    }

    private static boolean isGrandparent(FamilyTreeNode focus, UUID target, Map<UUID, FamilyTreeNode> nodes) {
        return focus.streamParents()
                .map(nodes::get)
                .filter(parent -> parent != null)
                .anyMatch(parent -> target.equals(parent.father()) || target.equals(parent.mother()));
    }

    private static boolean isGrandchild(FamilyTreeNode focus, UUID target, Map<UUID, FamilyTreeNode> nodes) {
        return focus.streamChildren()
                .map(nodes::get)
                .filter(child -> child != null)
                .anyMatch(child -> child.children().contains(target));
    }
}
