package net.conczin.mca.server.world.data;

import net.conczin.mca.network.FamilyTreeView;
import net.minecraft.core.GlobalPos;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public final class FamilyTreeViewBuilder {
    public static final int MAX_DEPTH = 8;

    private FamilyTreeViewBuilder() {
    }

    public static Optional<FamilyTreeView> build(FamilyTree tree, UUID root, int ancestorDepth, int descendantDepth) {
        return build(tree, root, ancestorDepth, descendantDepth, id -> Optional.empty());
    }

    public static Optional<FamilyTreeView> build(
            FamilyTree tree,
            UUID root,
            int ancestorDepth,
            int descendantDepth,
            Function<UUID, Optional<GlobalPos>> graveLookup
    ) {
        Optional<FamilyTreeNode> rootNode = tree.getOrEmpty(root);
        if (rootNode.isEmpty()) {
            return Optional.empty();
        }

        Map<UUID, FamilyTreeNode> nodes = new LinkedHashMap<>();
        Set<FamilyTreeView.Continuation> continuations = new LinkedHashSet<>();
        Set<UUID> unavailable = new LinkedHashSet<>();
        FamilyTreeNode.LineageTraversal ancestors = new FamilyTreeNode.LineageTraversal(
                FamilyTreeNode.LineageDirection.ANCESTORS
        );
        FamilyTreeNode.LineageTraversal descendants = new FamilyTreeNode.LineageTraversal(
                FamilyTreeNode.LineageDirection.DESCENDANTS
        );

        FamilyTreeNode rootEntry = rootNode.orElseThrow();
        nodes.put(root, rootEntry);
        includePartners(tree, nodes, unavailable);

        walkLineage(
                ancestors,
                rootEntry,
                clampDepth(ancestorDepth),
                nodes,
                continuations,
                unavailable
        );
        walkLineage(
                descendants,
                rootEntry,
                clampDepth(descendantDepth),
                nodes,
                continuations,
                unavailable
        );
        includePartnerLineage(
                tree,
                nodes,
                continuations,
                unavailable,
                ancestors,
                descendants
        );
        includeRootSiblings(tree, rootEntry, nodes, continuations, unavailable);
        includePartners(tree, nodes, unavailable);

        unavailable.removeAll(nodes.keySet());
        Map<UUID, FamilyTreeNode> snapshots = snapshotNodes(tree, nodes, continuations);
        Set<UUID> orphans = collectOrphans(tree, snapshots.values());
        Map<UUID, GlobalPos> graves = new LinkedHashMap<>();
        snapshots.keySet().forEach(id -> graveLookup.apply(id).ifPresent(grave -> graves.put(id, grave)));
        return Optional.of(new FamilyTreeView(snapshots, continuations, unavailable, orphans, graves));
    }

    private static Set<UUID> collectOrphans(FamilyTree tree, Iterable<FamilyTreeNode> nodes) {
        Set<UUID> orphans = new LinkedHashSet<>();
        for (FamilyTreeNode node : nodes) {
            if (tree.isOrphan(node)) {
                orphans.add(node.id());
            }
        }
        return orphans;
    }

    private static int clampDepth(int depth) {
        return Math.max(0, Math.min(MAX_DEPTH, depth));
    }

    private static void walkLineage(
            FamilyTreeNode.LineageTraversal traversal,
            FamilyTreeNode node,
            int remainingDepth,
            Map<UUID, FamilyTreeNode> nodes,
            Set<FamilyTreeView.Continuation> continuations,
            Set<UUID> unavailable
    ) {
        FamilyTreeView.Direction direction = switch (traversal.direction()) {
            case ANCESTORS -> FamilyTreeView.Direction.ANCESTORS;
            case DESCENDANTS -> FamilyTreeView.Direction.DESCENDANTS;
        };
        traversal.walk(node, remainingDepth, (source, relativeId, relative, depth) -> {
            if (relative == null) {
                addUnavailable(unavailable, relativeId);
                return FamilyTreeNode.TraversalDecision.CONTINUE;
            }
            if (depth == 0) {
                if (!nodes.containsKey(relativeId)) {
                    addContinuation(continuations, source.id(), direction);
                    return FamilyTreeNode.TraversalDecision.STOP;
                }
                return FamilyTreeNode.TraversalDecision.CONTINUE;
            }
            if (!nodes.containsKey(relativeId) && !tryAddNode(nodes, relativeId, relative)) {
                addContinuation(continuations, source.id(), direction);
                return direction == FamilyTreeView.Direction.DESCENDANTS
                        ? FamilyTreeNode.TraversalDecision.STOP
                        : FamilyTreeNode.TraversalDecision.CONTINUE;
            }
            return FamilyTreeNode.TraversalDecision.DESCEND;
        });
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
            FamilyTreeNode.LineageTraversal ancestors,
            FamilyTreeNode.LineageTraversal descendants
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

            Integer ancestorDepth = ancestors.remainingDepth(node.id());
            if (ancestorDepth != null) {
                walkLineage(
                        ancestors,
                        partner,
                        ancestorDepth,
                        nodes,
                        continuations,
                        unavailable
                );
            }

            Integer descendantDepth = descendants.remainingDepth(node.id());
            if (descendantDepth != null) {
                walkLineage(
                        descendants,
                        partner,
                        descendantDepth,
                        nodes,
                        continuations,
                        unavailable
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
