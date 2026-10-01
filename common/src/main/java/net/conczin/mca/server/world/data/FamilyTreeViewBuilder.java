package net.conczin.mca.server.world.data;

import net.conczin.mca.network.FamilyTreeView;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class FamilyTreeViewBuilder {
    public static final int MAX_DEPTH = 8;

    private FamilyTreeViewBuilder() {
    }

    public static Optional<FamilyTreeView> build(FamilyTree tree, UUID root, int ancestorDepth, int descendantDepth) {
        Optional<FamilyTreeNode> rootNode = tree.getOrEmpty(root);
        if (rootNode.isEmpty()) {
            return Optional.empty();
        }

        Map<UUID, FamilyTreeNode> nodes = new LinkedHashMap<>();
        Set<FamilyTreeView.Continuation> continuations = new LinkedHashSet<>();
        Set<UUID> unavailable = new LinkedHashSet<>();
        Map<UUID, Integer> ancestorRemaining = new LinkedHashMap<>();
        Map<UUID, Integer> descendantRemaining = new LinkedHashMap<>();

        FamilyTreeNode rootEntry = rootNode.orElseThrow();
        nodes.put(root, rootEntry);
        includePartners(tree, nodes, unavailable);

        walkAncestors(
                tree,
                rootEntry,
                clampDepth(ancestorDepth),
                nodes,
                continuations,
                unavailable,
                ancestorRemaining
        );
        walkDescendants(
                tree,
                rootEntry,
                clampDepth(descendantDepth),
                nodes,
                continuations,
                unavailable,
                descendantRemaining
        );
        includePartnerLineage(
                tree,
                nodes,
                continuations,
                unavailable,
                ancestorRemaining,
                descendantRemaining
        );
        includeRootSiblings(tree, rootEntry, nodes, continuations, unavailable);
        includePartners(tree, nodes, unavailable);

        unavailable.removeAll(nodes.keySet());
        return Optional.of(new FamilyTreeView(snapshotNodes(tree, nodes, continuations), continuations, unavailable));
    }

    private static int clampDepth(int depth) {
        return Math.max(0, Math.min(MAX_DEPTH, depth));
    }

    private static void walkAncestors(
            FamilyTree tree,
            FamilyTreeNode node,
            int remainingDepth,
            Map<UUID, FamilyTreeNode> nodes,
            Set<FamilyTreeView.Continuation> continuations,
            Set<UUID> unavailable,
            Map<UUID, Integer> visitedRemaining
    ) {
        if (!shouldVisit(node.id(), remainingDepth, visitedRemaining)) {
            return;
        }
        if (remainingDepth == 0) {
            if (hasResolvableUnseenReference(tree, node.streamParents().iterator(), nodes, unavailable)) {
                addContinuation(continuations, node.id(), FamilyTreeView.Direction.ANCESTORS);
            }
            return;
        }

        for (UUID parentId : node.streamParents().toList()) {
            Optional<FamilyTreeNode> parentNode = tree.getOrEmpty(parentId);
            if (parentNode.isEmpty()) {
                addUnavailable(unavailable, parentId);
                continue;
            }
            FamilyTreeNode parent = parentNode.orElseThrow();
            if (!nodes.containsKey(parentId) && !tryAddNode(nodes, parentId, parent)) {
                addContinuation(continuations, node.id(), FamilyTreeView.Direction.ANCESTORS);
                continue;
            }
            walkAncestors(tree, parent, remainingDepth - 1, nodes, continuations, unavailable, visitedRemaining);
        }
    }

    private static void walkDescendants(
            FamilyTree tree,
            FamilyTreeNode node,
            int remainingDepth,
            Map<UUID, FamilyTreeNode> nodes,
            Set<FamilyTreeView.Continuation> continuations,
            Set<UUID> unavailable,
            Map<UUID, Integer> visitedRemaining
    ) {
        if (!shouldVisit(node.id(), remainingDepth, visitedRemaining)) {
            return;
        }
        if (remainingDepth == 0) {
            if (hasResolvableUnseenReference(tree, node.streamChildren().iterator(), nodes, unavailable)) {
                addContinuation(continuations, node.id(), FamilyTreeView.Direction.DESCENDANTS);
            }
            return;
        }

        Iterator<UUID> childIds = node.streamChildren().iterator();
        while (childIds.hasNext()) {
            UUID childId = childIds.next();
            Optional<FamilyTreeNode> childNode = tree.getOrEmpty(childId);
            if (childNode.isEmpty()) {
                addUnavailable(unavailable, childId);
                continue;
            }
            FamilyTreeNode child = childNode.orElseThrow();
            if (!nodes.containsKey(childId) && !tryAddNode(nodes, childId, child)) {
                addContinuation(continuations, node.id(), FamilyTreeView.Direction.DESCENDANTS);
                break;
            }
            walkDescendants(tree, child, remainingDepth - 1, nodes, continuations, unavailable, visitedRemaining);
        }
    }

    private static boolean shouldVisit(UUID id, int remainingDepth, Map<UUID, Integer> visitedRemaining) {
        Integer previousRemaining = visitedRemaining.get(id);
        if (previousRemaining != null && previousRemaining >= remainingDepth) {
            return false;
        }
        visitedRemaining.put(id, remainingDepth);
        return true;
    }

    private static boolean hasResolvableUnseenReference(
            FamilyTree tree,
            Iterator<UUID> references,
            Map<UUID, FamilyTreeNode> nodes,
            Set<UUID> unavailable
    ) {
        while (references.hasNext()) {
            UUID reference = references.next();
            Optional<FamilyTreeNode> related = tree.getOrEmpty(reference);
            if (related.isEmpty()) {
                addUnavailable(unavailable, reference);
            } else if (!nodes.containsKey(reference)) {
                return true;
            }
        }
        return false;
    }

    private static void includeRootSiblings(
            FamilyTree tree,
            FamilyTreeNode root,
            Map<UUID, FamilyTreeNode> nodes,
            Set<FamilyTreeView.Continuation> continuations,
            Set<UUID> unavailable
    ) {
        for (UUID parentId : root.streamParents().toList()) {
            Optional<FamilyTreeNode> parentNode = tree.getOrEmpty(parentId);
            if (parentNode.isEmpty()) {
                addUnavailable(unavailable, parentId);
                continue;
            }
            Iterator<UUID> siblings = parentNode.orElseThrow().streamChildren().iterator();
            while (siblings.hasNext()) {
                UUID childId = siblings.next();
                if (root.id().equals(childId)) {
                    continue;
                }
                Optional<FamilyTreeNode> siblingNode = tree.getOrEmpty(childId);
                if (siblingNode.isEmpty()) {
                    addUnavailable(unavailable, childId);
                } else if (!nodes.containsKey(childId) && !tryAddNode(nodes, childId, siblingNode.orElseThrow())) {
                    addContinuation(continuations, parentId, FamilyTreeView.Direction.DESCENDANTS);
                    break;
                }
            }
        }
    }

    private static void includePartnerLineage(
            FamilyTree tree,
            Map<UUID, FamilyTreeNode> nodes,
            Set<FamilyTreeView.Continuation> continuations,
            Set<UUID> unavailable,
            Map<UUID, Integer> ancestorRemaining,
            Map<UUID, Integer> descendantRemaining
    ) {
        for (FamilyTreeNode node : List.copyOf(nodes.values())) {
            UUID partnerId = node.partner();
            if (!FamilyTreeNode.isValid(partnerId)) {
                continue;
            }

            Optional<FamilyTreeNode> partnerNode = tree.getOrEmpty(partnerId);
            if (partnerNode.isEmpty()) {
                addUnavailable(unavailable, partnerId);
                continue;
            }

            FamilyTreeNode partner = partnerNode.orElseThrow();
            if (!nodes.containsKey(partnerId) && !tryAddNode(nodes, partnerId, partner)) {
                continue;
            }

            Integer ancestorDepth = ancestorRemaining.get(node.id());
            if (ancestorDepth != null) {
                walkAncestors(
                        tree,
                        partner,
                        ancestorDepth,
                        nodes,
                        continuations,
                        unavailable,
                        ancestorRemaining
                );
            }

            Integer descendantDepth = descendantRemaining.get(node.id());
            if (descendantDepth != null) {
                walkDescendants(
                        tree,
                        partner,
                        descendantDepth,
                        nodes,
                        continuations,
                        unavailable,
                        descendantRemaining
                );
            }
        }
    }

    private static void includePartners(
            FamilyTree tree,
            Map<UUID, FamilyTreeNode> nodes,
            Set<UUID> unavailable
    ) {
        for (FamilyTreeNode node : List.copyOf(nodes.values())) {
            UUID partnerId = node.partner();
            if (!FamilyTreeNode.isValid(partnerId)) {
                continue;
            }
            Optional<FamilyTreeNode> partner = tree.getOrEmpty(partnerId);
            if (partner.isEmpty()) {
                addUnavailable(unavailable, partnerId);
            } else if (!nodes.containsKey(partnerId)) {
                tryAddNode(nodes, partnerId, partner.orElseThrow());
            }
        }
    }

    private static boolean tryAddNode(Map<UUID, FamilyTreeNode> nodes, UUID id, FamilyTreeNode node) {
        if (nodes.containsKey(id)) {
            return true;
        }
        if (nodes.size() >= FamilyTreeView.MAX_NODES) {
            return false;
        }
        nodes.put(id, node);
        return true;
    }

    private static void addContinuation(
            Set<FamilyTreeView.Continuation> continuations,
            UUID anchor,
            FamilyTreeView.Direction direction
    ) {
        if (continuations.size() < FamilyTreeView.MAX_CONTINUATIONS) {
            continuations.add(new FamilyTreeView.Continuation(anchor, direction));
        }
    }

    private static void addUnavailable(Set<UUID> unavailable, UUID id) {
        if (unavailable.size() < FamilyTreeView.MAX_UNAVAILABLE) {
            unavailable.add(id);
        }
    }

    private static Map<UUID, FamilyTreeNode> snapshotNodes(
            FamilyTree tree,
            Map<UUID, FamilyTreeNode> nodes,
            Set<FamilyTreeView.Continuation> continuations
    ) {
        Map<UUID, FamilyTreeNode> snapshots = new LinkedHashMap<>(nodes.size());
        for (FamilyTreeNode node : nodes.values()) {
            Set<UUID> children = new LinkedHashSet<>();
            node.streamChildren().filter(nodes::containsKey).forEach(children::add);
            boolean hasMoreDescendants = continuations.contains(new FamilyTreeView.Continuation(
                    node.id(),
                    FamilyTreeView.Direction.DESCENDANTS
            ));
            if (hasMoreDescendants) {
                node.streamChildren()
                        .filter(childId -> !nodes.containsKey(childId))
                        .filter(childId -> tree.getOrEmpty(childId).isPresent())
                        .findFirst()
                        .ifPresent(children::add);
            }
            snapshots.put(node.id(), FamilyTreeNode.detachedCopy(node, children));
        }
        return snapshots;
    }
}
