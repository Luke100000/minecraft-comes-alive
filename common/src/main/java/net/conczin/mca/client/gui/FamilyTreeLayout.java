package net.conczin.mca.client.gui;

import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.network.FamilyTreeView;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class FamilyTreeLayout {
    public static final int CARD_WIDTH = 128;
    public static final int CARD_HEIGHT = 64;
    public static final int HORIZONTAL_GAP = 18;
    public static final int GENERATION_GAP = 56;
    public static final int PARTNER_GAP = 26;

    private static final int CONTINUATION_SIZE = 18;
    private static final Comparator<UUID> UUID_ORDER = (one, two) -> {
        int mostSignificant = Long.compareUnsigned(one.getMostSignificantBits(), two.getMostSignificantBits());
        return mostSignificant != 0
                ? mostSignificant
                : Long.compareUnsigned(one.getLeastSignificantBits(), two.getLeastSignificantBits());
    };

    private FamilyTreeLayout() {
    }

    public static Result layout(FamilyTreeViewModel.Snapshot snapshot) {
        UUID layoutRoot = snapshot.layoutRootId();
        Map<UUID, FamilyTreeNode> nodes = snapshot.nodes();
        if (!nodes.containsKey(layoutRoot)) {
            return Result.empty();
        }

        Map<UUID, UUID> displayPartners = collectDisplayPartners(nodes);
        Map<UUID, Placement> placements = collectPlacements(layoutRoot, nodes, displayPartners);
        Map<Integer, List<UUID>> generations = new LinkedHashMap<>();
        placements.forEach((uuid, placement) ->
                generations.computeIfAbsent(placement.generation(), ignored -> new ArrayList<>()).add(uuid));

        Map<UUID, Bounds> boundsById = new LinkedHashMap<>();
        List<Integer> orderedGenerations = generations.keySet().stream().sorted().toList();
        Map<UUID, Integer> branchWidths = ancestorBranchWidths(nodes, placements, orderedGenerations, generations);
        List<UUID> anchorIds = generations.get(0);
        anchorIds.sort(UUID_ORDER);
        placeAnchorGeneration(layoutRoot, anchorIds, placements, displayPartners, branchWidths, boundsById);
        for (int generation : orderedGenerations) {
            if (generation > 0) {
                placeGeneration(generation, generations.get(generation), nodes, displayPartners, branchWidths, boundsById);
            }
        }
        for (int index = orderedGenerations.size() - 1; index >= 0; index--) {
            int generation = orderedGenerations.get(index);
            if (generation < 0) {
                placeGeneration(generation, generations.get(generation), nodes, displayPartners, branchWidths, boundsById);
            }
        }

        List<Card> cards = boundsById.entrySet().stream()
                .map(entry -> new Card(entry.getKey(), entry.getValue()))
                .toList();
        List<Edge> edges = buildEdges(nodes, boundsById, displayPartners);
        List<ContinuationControl> continuations = buildContinuations(snapshot.continuations(), boundsById);
        Bounds contentBounds = computeContentBounds(cards, continuations);

        return new Result(cards, edges, continuations, contentBounds);
    }

    private static Map<UUID, Placement> collectPlacements(
            UUID layoutRoot,
            Map<UUID, FamilyTreeNode> nodes,
            Map<UUID, UUID> displayPartners
    ) {
        Map<UUID, Placement> placements = new LinkedHashMap<>();
        placements.put(layoutRoot, new Placement(0, Role.ANCHOR));

        FamilyTreeNode rootNode = nodes.get(layoutRoot);
        walkAncestors(rootNode, 0, nodes, placements);
        walkDescendants(rootNode, 0, nodes, placements);
        addSiblings(rootNode, nodes, placements);
        addPartnerBranches(nodes, placements, displayPartners);
        return placements;
    }

    private static void walkAncestors(
            FamilyTreeNode root,
            int rootGeneration,
            Map<UUID, FamilyTreeNode> nodes,
            Map<UUID, Placement> placements
    ) {
        Deque<GenerationNode> queue = new ArrayDeque<>();
        queue.add(new GenerationNode(root, rootGeneration));
        Set<UUID> visited = new HashSet<>();
        visited.add(root.id());

        while (!queue.isEmpty()) {
            GenerationNode current = queue.removeFirst();
            int parentGeneration = current.generation() - 1;
            for (UUID parentId : parentIds(current.node())) {
                FamilyTreeNode parent = nodes.get(parentId);
                if (parent == null) {
                    continue;
                }

                if (!visited.add(parentId)) {
                    continue;
                }
                placements.putIfAbsent(parentId, new Placement(parentGeneration, Role.ANCESTOR));
                queue.addLast(new GenerationNode(parent, parentGeneration));
            }
        }
    }

    private static void walkDescendants(
            FamilyTreeNode root,
            int rootGeneration,
            Map<UUID, FamilyTreeNode> nodes,
            Map<UUID, Placement> placements
    ) {
        Deque<GenerationNode> queue = new ArrayDeque<>();
        queue.add(new GenerationNode(root, rootGeneration));
        Set<UUID> visited = new HashSet<>();
        visited.add(root.id());

        while (!queue.isEmpty()) {
            GenerationNode current = queue.removeFirst();
            int childGeneration = current.generation() + 1;
            List<UUID> childIds = current.node().streamChildren().sorted(UUID_ORDER).toList();
            for (UUID childId : childIds) {
                FamilyTreeNode child = nodes.get(childId);
                if (child == null) {
                    continue;
                }

                if (!visited.add(childId)) {
                    continue;
                }
                placements.putIfAbsent(childId, new Placement(childGeneration, Role.DESCENDANT));
                queue.addLast(new GenerationNode(child, childGeneration));
            }
        }
    }

    private static void addSiblings(
            FamilyTreeNode anchor,
            Map<UUID, FamilyTreeNode> nodes,
            Map<UUID, Placement> placements
    ) {
        Set<UUID> anchorParents = new HashSet<>(parentIds(anchor));
        if (anchorParents.isEmpty()) {
            return;
        }

        nodes.values().stream()
                .filter(node -> !node.id().equals(anchor.id()))
                .filter(node -> parentIds(node).stream().anyMatch(anchorParents::contains))
                .sorted(Comparator.comparing(FamilyTreeNode::id, UUID_ORDER))
                .forEach(node -> placements.putIfAbsent(node.id(), new Placement(0, Role.SIBLING)));
    }

    private static void addPartnerBranches(
            Map<UUID, FamilyTreeNode> nodes,
            Map<UUID, Placement> placements,
            Map<UUID, UUID> displayPartners
    ) {
        for (Map.Entry<UUID, Placement> entry : List.copyOf(placements.entrySet())) {
            UUID partnerId = displayPartners.get(entry.getKey());
            if (partnerId == null) {
                continue;
            }

            FamilyTreeNode partner = nodes.get(partnerId);
            int generation = entry.getValue().generation();
            placements.putIfAbsent(partner.id(), new Placement(generation, Role.PARTNER));
            walkAncestors(partner, generation, nodes, placements);
            walkDescendants(partner, generation, nodes, placements);
        }
    }

    private static List<UUID> parentIds(FamilyTreeNode node) {
        return node.streamParents().toList();
    }

    private static Map<UUID, UUID> collectDisplayPartners(Map<UUID, FamilyTreeNode> nodes) {
        Map<UUID, UUID> partners = new LinkedHashMap<>();
        for (FamilyTreeNode node : nodes.values()) {
            if (FamilyTreeNode.isValid(node.partner()) && nodes.containsKey(node.partner())) {
                partners.put(node.id(), node.partner());
            }
        }

        // Villager spawning creates two deceased parent records without a stored marriage.
        // Use the existing generated-parent heuristic and their shared child to pair those
        // records only for display. Recorded relationships, including widows, take precedence.
        for (FamilyTreeNode child : nodes.values()) {
            FamilyTreeNode father = nodes.get(child.father());
            FamilyTreeNode mother = nodes.get(child.mother());
            if (father == null || mother == null || father.id().equals(mother.id())
                    || !isUnpairedGeneratedParent(father, child.id())
                    || !isUnpairedGeneratedParent(mother, child.id())) {
                continue;
            }
            partners.put(father.id(), mother.id());
            partners.put(mother.id(), father.id());
        }
        return partners;
    }

    private static boolean isUnpairedGeneratedParent(FamilyTreeNode node, UUID childId) {
        return node.probablyGenerated()
                && node.children().contains(childId)
                && !FamilyTreeNode.isValid(node.partner())
                && node.getRelationshipState() == RelationshipState.SINGLE;
    }

    private static Map<UUID, Integer> ancestorBranchWidths(
            Map<UUID, FamilyTreeNode> nodes,
            Map<UUID, Placement> placements,
            List<Integer> orderedGenerations,
            Map<Integer, List<UUID>> generations
    ) {
        Map<UUID, Integer> widths = new LinkedHashMap<>();
        // Reserve each person's ancestry before positioning couples, so both spouses'
        // parents have separate lanes instead of sharing a horizontal connector rail.
        for (int generation : orderedGenerations) {
            for (UUID id : generations.get(generation)) {
                int parentCount = 0;
                int parentWidth = CARD_WIDTH;
                for (UUID parentId : parentIds(nodes.get(id))) {
                    Placement parent = placements.get(parentId);
                    if (parent != null && parent.generation() == generation - 1) {
                        parentCount++;
                        parentWidth = Math.max(parentWidth, widths.getOrDefault(parentId, CARD_WIDTH));
                    }
                }
                widths.put(id, parentCount > 1 ? parentWidth * 2 + PARTNER_GAP : parentWidth);
            }
        }
        return widths;
    }

    private static void placeAnchorGeneration(
            UUID layoutRoot,
            List<UUID> ids,
            Map<UUID, Placement> placements,
            Map<UUID, UUID> displayPartners,
            Map<UUID, Integer> branchWidths,
            Map<UUID, Bounds> boundsById
    ) {
        boundsById.put(layoutRoot, cardBounds(0, 0));

        UUID partnerId = displayPartners.get(layoutRoot);
        boolean hasPartner = FamilyTreeNode.isValid(partnerId) && ids.contains(partnerId);
        int laneWidth = branchWidths.get(layoutRoot);
        if (hasPartner) {
            laneWidth = Math.max(laneWidth, branchWidths.get(partnerId));
        }
        int partnerX = laneWidth + PARTNER_GAP;
        if (hasPartner) {
            boundsById.put(partnerId, cardBounds(partnerX, 0));
        }

        List<UUID> siblings = ids.stream()
                .filter(id -> !id.equals(layoutRoot))
                .filter(id -> !id.equals(partnerId))
                .filter(id -> placements.get(id).role() == Role.SIBLING)
                .toList();
        List<UUID> others = ids.stream()
                .filter(id -> !id.equals(layoutRoot))
                .filter(id -> !id.equals(partnerId))
                .filter(id -> placements.get(id).role() != Role.SIBLING)
                .toList();

        int leftEdge = -laneWidth / 2;
        int rightEdge = (hasPartner ? partnerX : 0) + laneWidth / 2;
        for (int index = 0; index < siblings.size(); index++) {
            UUID id = siblings.get(index);
            int width = branchWidths.get(id);
            if ((index & 1) == 0) {
                int x = leftEdge - HORIZONTAL_GAP - width / 2;
                boundsById.put(id, cardBounds(x, 0));
                leftEdge = x - width / 2;
            } else {
                int x = rightEdge + HORIZONTAL_GAP + width / 2;
                boundsById.put(id, cardBounds(x, 0));
                rightEdge = x + width / 2;
            }
        }

        for (UUID id : others) {
            int width = branchWidths.get(id);
            int x = rightEdge + HORIZONTAL_GAP + width / 2;
            boundsById.put(id, cardBounds(x, 0));
            rightEdge = x + width / 2;
        }
    }

    private static void placeGeneration(
            int generation,
            List<UUID> ids,
            Map<UUID, FamilyTreeNode> nodes,
            Map<UUID, UUID> displayPartners,
            Map<UUID, Integer> branchWidths,
            Map<UUID, Bounds> boundsById
    ) {
        List<GenerationUnit> units = new ArrayList<>();
        Set<UUID> assigned = new HashSet<>();
        Set<UUID> generationIds = new HashSet<>(ids);
        for (UUID id : ids.stream().sorted(UUID_ORDER).toList()) {
            if (!assigned.add(id)) {
                continue;
            }
            UUID partnerId = displayPartners.get(id);
            List<UUID> members;
            if (FamilyTreeNode.isValid(partnerId) && generationIds.contains(partnerId) && assigned.add(partnerId)) {
                members = List.of(id, partnerId);
            } else {
                members = List.of(id);
            }
            int laneWidth = members.stream().mapToInt(branchWidths::get).max().orElse(CARD_WIDTH);
            units.add(new GenerationUnit(members, laneWidth, generationTarget(generation, members, nodes, boundsById)));
        }

        units.sort(Comparator.comparingInt(GenerationUnit::targetX)
                .thenComparing(unit -> unit.ids().get(0), UUID_ORDER));
        List<Integer> centers = new ArrayList<>();
        int rightEdge = Integer.MIN_VALUE;
        long totalOffset = 0;
        for (GenerationUnit unit : units) {
            int left = unit.targetX() - unit.width() / 2;
            if (!centers.isEmpty()) {
                left = Math.max(left, rightEdge + HORIZONTAL_GAP);
            }
            int center = left + unit.width() / 2;
            centers.add(center);
            totalOffset += unit.targetX() - center;
            rightEdge = left + unit.width();
        }
        int offset = units.isEmpty() ? 0 : (int) (totalOffset / units.size());
        int y = generation * (CARD_HEIGHT + GENERATION_GAP);
        for (int index = 0; index < units.size(); index++) {
            GenerationUnit unit = units.get(index);
            int center = centers.get(index) + offset;
            if (unit.ids().size() == 2) {
                int separation = unit.laneWidth() + PARTNER_GAP;
                int firstCenter = center - separation / 2;
                boundsById.put(unit.ids().get(0), cardBounds(firstCenter, y));
                boundsById.put(unit.ids().get(1), cardBounds(firstCenter + separation, y));
            } else {
                boundsById.put(unit.ids().get(0), cardBounds(center, y));
            }
        }
    }

    private static int generationTarget(
            int generation,
            List<UUID> ids,
            Map<UUID, FamilyTreeNode> nodes,
            Map<UUID, Bounds> boundsById
    ) {
        int left = Integer.MAX_VALUE;
        int right = Integer.MIN_VALUE;
        int adjacentY = (generation + (generation < 0 ? 1 : -1)) * (CARD_HEIGHT + GENERATION_GAP);
        for (FamilyTreeNode node : nodes.values()) {
            if (generation < 0) {
                Bounds child = boundsById.get(node.id());
                if (child != null && child.centerY() == adjacentY
                        && parentIds(node).stream().anyMatch(ids::contains)) {
                    left = Math.min(left, child.centerX());
                    right = Math.max(right, child.centerX());
                }
            } else if (ids.contains(node.id())) {
                for (UUID parentId : parentIds(node)) {
                    Bounds parent = boundsById.get(parentId);
                    if (parent != null && parent.centerY() == adjacentY) {
                        left = Math.min(left, parent.centerX());
                        right = Math.max(right, parent.centerX());
                    }
                }
            }
        }
        return left == Integer.MAX_VALUE ? 0 : (left + right) / 2;
    }

    private static Bounds cardBounds(int centerX, int centerY) {
        int left = centerX - CARD_WIDTH / 2;
        int top = centerY - CARD_HEIGHT / 2;
        return new Bounds(left, left + CARD_WIDTH, top, top + CARD_HEIGHT);
    }

    private static List<Edge> buildEdges(
            Map<UUID, FamilyTreeNode> nodes,
            Map<UUID, Bounds> boundsById,
            Map<UUID, UUID> displayPartners
    ) {
        Set<EdgeKey> seen = new LinkedHashSet<>();
        List<Edge> edges = new ArrayList<>();

        for (UUID id : boundsById.keySet()) {
            FamilyTreeNode node = nodes.get(id);
            if (node == null) {
                continue;
            }

            List<UUID> parents = parentIds(node).stream().distinct().filter(boundsById::containsKey).toList();
            if (parents.size() == 2
                    && (parents.get(1).equals(displayPartners.get(parents.get(0)))
                    || parents.get(0).equals(displayPartners.get(parents.get(1))))
                    && boundsById.get(parents.get(0)).centerY() == boundsById.get(parents.get(1)).centerY()) {
                edges.add(new Edge(parents.get(0), id, EdgeType.PARENT_CHILD, parents.get(1)));
            } else {
                for (UUID parentId : parents) {
                    EdgeKey key = EdgeKey.ordered(parentId, id, EdgeType.PARENT_CHILD);
                    if (seen.add(key)) {
                        edges.add(new Edge(parentId, id, EdgeType.PARENT_CHILD, null));
                    }
                }
            }

            UUID partnerId = displayPartners.get(id);
            if (FamilyTreeNode.isValid(partnerId) && boundsById.containsKey(partnerId)) {
                EdgeKey key = EdgeKey.ordered(id, partnerId, EdgeType.PARTNER);
                if (seen.add(key)) {
                    edges.add(new Edge(id, partnerId, EdgeType.PARTNER, null));
                }
            }
        }
        return List.copyOf(edges);
    }

    private static List<ContinuationControl> buildContinuations(
            Set<FamilyTreeView.Continuation> source,
            Map<UUID, Bounds> boundsById
    ) {
        return source.stream()
                .sorted(Comparator
                        .comparing(FamilyTreeView.Continuation::anchor, UUID_ORDER)
                        .thenComparing(item -> item.direction().ordinal()))
                .filter(item -> boundsById.containsKey(item.anchor()))
                .map(item -> {
                    Bounds anchor = boundsById.get(item.anchor());
                    int x = anchor.centerX();
                    int offset = CARD_HEIGHT / 2 + GENERATION_GAP / 2 + CONTINUATION_SIZE / 2;
                    int y = anchor.centerY() + (item.direction() == FamilyTreeView.Direction.ANCESTORS ? -offset : offset);
                    int half = CONTINUATION_SIZE / 2;
                    return new ContinuationControl(
                            item.anchor(),
                            item.direction(),
                            new Bounds(x - half, x - half + CONTINUATION_SIZE, y - half, y - half + CONTINUATION_SIZE)
                    );
                })
                .toList();
    }

    private static Bounds computeContentBounds(List<Card> cards, List<ContinuationControl> continuations) {
        Bounds bounds = null;
        for (Card card : cards) {
            bounds = bounds == null ? card.bounds() : bounds.union(card.bounds());
        }
        for (ContinuationControl control : continuations) {
            bounds = bounds == null ? control.bounds() : bounds.union(control.bounds());
        }
        return bounds == null ? new Bounds(0, 0, 0, 0) : bounds;
    }

    private enum Role {
        ANCHOR,
        ANCESTOR,
        DESCENDANT,
        SIBLING,
        PARTNER
    }

    public enum EdgeType {
        PARENT_CHILD,
        PARTNER
    }

    public record Bounds(int left, int right, int top, int bottom) {
        public int centerX() {
            return (left + right) / 2;
        }

        public int centerY() {
            return (top + bottom) / 2;
        }

        public boolean contains(int x, int y) {
            return x >= left && x < right && y >= top && y < bottom;
        }

        public boolean contains(Bounds other) {
            return other.left >= left
                    && other.right <= right
                    && other.top >= top
                    && other.bottom <= bottom;
        }

        public boolean intersects(Bounds other) {
            return left < other.right
                    && right > other.left
                    && top < other.bottom
                    && bottom > other.top;
        }

        public Bounds union(Bounds other) {
            return new Bounds(
                    Math.min(left, other.left),
                    Math.max(right, other.right),
                    Math.min(top, other.top),
                    Math.max(bottom, other.bottom)
            );
        }
    }

    public record Card(UUID uuid, Bounds bounds) {
    }

    public record Edge(UUID from, UUID to, EdgeType type, @Nullable UUID secondParent) {
    }

    public record ContinuationControl(UUID anchor, FamilyTreeView.Direction direction, Bounds bounds) {
    }

    public record Result(
            List<Card> cards,
            List<Edge> edges,
            List<ContinuationControl> continuations,
            Bounds contentBounds
    ) {
        public Result {
            cards = List.copyOf(cards);
            edges = List.copyOf(edges);
            continuations = List.copyOf(continuations);
        }

        private static Result empty() {
            return new Result(List.of(), List.of(), List.of(), new Bounds(0, 0, 0, 0));
        }
    }

    private record Placement(int generation, Role role) {
    }

    private record GenerationNode(FamilyTreeNode node, int generation) {
    }

    private record GenerationUnit(List<UUID> ids, int laneWidth, int targetX) {
        int width() {
            return ids.size() == 2 ? laneWidth * 2 + PARTNER_GAP : laneWidth;
        }
    }

    private record EdgeKey(UUID one, UUID two, EdgeType type) {
        private static EdgeKey ordered(UUID one, UUID two, EdgeType type) {
            if (UUID_ORDER.compare(one, two) <= 0) {
                return new EdgeKey(one, two, type);
            }
            return new EdgeKey(two, one, type);
        }
    }
}
