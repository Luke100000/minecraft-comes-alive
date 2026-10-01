package net.conczin.mca.server.world.data;

import net.conczin.mca.network.FamilyTreeView;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
        includeRootSiblings(tree, rootEntry, nodes, unavailable);
        includePartners(tree, nodes, unavailable);

        return Optional.of(new FamilyTreeView(nodes, continuations, unavailable));
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
            if (hasResolvableUnseenReference(tree, node.streamParents().toList(), nodes, unavailable)) {
                continuations.add(new FamilyTreeView.Continuation(node.id(), FamilyTreeView.Direction.ANCESTORS));
            }
            return;
        }

        node.streamParents().forEach(parentId -> tree.getOrEmpty(parentId).ifPresentOrElse(parent -> {
            nodes.putIfAbsent(parentId, parent);
            walkAncestors(tree, parent, remainingDepth - 1, nodes, continuations, unavailable, visitedRemaining);
        }, () -> unavailable.add(parentId)));
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
            if (hasResolvableUnseenReference(tree, node.streamChildren().toList(), nodes, unavailable)) {
                continuations.add(new FamilyTreeView.Continuation(node.id(), FamilyTreeView.Direction.DESCENDANTS));
            }
            return;
        }

        node.streamChildren().forEach(childId -> tree.getOrEmpty(childId).ifPresentOrElse(child -> {
            nodes.putIfAbsent(childId, child);
            walkDescendants(tree, child, remainingDepth - 1, nodes, continuations, unavailable, visitedRemaining);
        }, () -> unavailable.add(childId)));
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
            List<UUID> references,
            Map<UUID, FamilyTreeNode> nodes,
            Set<UUID> unavailable
    ) {
        boolean hasMore = false;
        for (UUID reference : references) {
            Optional<FamilyTreeNode> related = tree.getOrEmpty(reference);
            if (related.isEmpty()) {
                unavailable.add(reference);
            } else if (!nodes.containsKey(reference)) {
                hasMore = true;
            }
        }
        return hasMore;
    }

    private static void includeRootSiblings(
            FamilyTree tree,
            FamilyTreeNode root,
            Map<UUID, FamilyTreeNode> nodes,
            Set<UUID> unavailable
    ) {
        root.streamParents().forEach(parentId -> tree.getOrEmpty(parentId).ifPresentOrElse(parent ->
                parent.streamChildren()
                        .filter(childId -> !root.id().equals(childId))
                        .forEach(childId -> tree.getOrEmpty(childId).ifPresentOrElse(
                                sibling -> nodes.putIfAbsent(childId, sibling),
                                () -> unavailable.add(childId)
                        )),
                () -> unavailable.add(parentId)
        ));
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
            tree.getOrEmpty(partnerId).ifPresentOrElse(
                    partner -> nodes.putIfAbsent(partnerId, partner),
                    () -> unavailable.add(partnerId)
            );
        }
    }
}
