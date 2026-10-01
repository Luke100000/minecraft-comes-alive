package net.conczin.mca.client.gui;

import net.conczin.mca.network.FamilyTreeView;
import net.conczin.mca.server.world.data.FamilyTreeNode;

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

public final class FamilyTreeLayout {
    public static final int CARD_WIDTH = 110;
    public static final int CARD_HEIGHT = 40;
    public static final int HORIZONTAL_GAP = 18;
    public static final int GENERATION_GAP = 56;
    public static final int PARTNER_GAP = 26;

    private static final int CONTINUATION_SIZE = 18;
    private static final Comparator<UUID> UUID_ORDER = Comparator.comparing(UUID::toString);

    private FamilyTreeLayout() {
    }

    public static Result layout(UUID focus, FamilyTreeViewModel.Snapshot snapshot) {
        Map<UUID, FamilyTreeNode> nodes = snapshot.nodes();
        if (!nodes.containsKey(focus)) {
            return Result.empty();
        }

        Map<UUID, Placement> placements = collectPlacements(focus, nodes);
        Map<Integer, List<UUID>> generations = new LinkedHashMap<>();
        placements.forEach((uuid, placement) ->
                generations.computeIfAbsent(placement.generation(), ignored -> new ArrayList<>()).add(uuid));

        Map<UUID, Bounds> boundsById = new LinkedHashMap<>();
        List<Integer> orderedGenerations = generations.keySet().stream().sorted().toList();
        for (int generation : orderedGenerations) {
            List<UUID> ids = generations.get(generation);
            ids.sort(UUID_ORDER);
            if (generation == 0) {
                placeFocusGeneration(focus, ids, placements, boundsById);
            } else {
                placeGeneration(generation, ids, boundsById);
            }
        }

        List<Card> cards = boundsById.entrySet().stream()
                .map(entry -> new Card(entry.getKey(), placements.get(entry.getKey()).role(), entry.getValue()))
                .toList();
        List<Edge> edges = buildEdges(nodes, boundsById);
        List<ContinuationControl> continuations = buildContinuations(snapshot.continuations(), boundsById);
        Bounds contentBounds = computeContentBounds(cards, continuations);

        return new Result(cards, edges, continuations, contentBounds);
    }

    private static Map<UUID, Placement> collectPlacements(UUID focus, Map<UUID, FamilyTreeNode> nodes) {
        Map<UUID, Placement> placements = new LinkedHashMap<>();
        placements.put(focus, new Placement(0, Role.FOCUS));

        FamilyTreeNode focusNode = nodes.get(focus);
        walkAncestors(focusNode, nodes, placements);
        walkDescendants(focusNode, nodes, placements);
        addSiblings(focusNode, nodes, placements);
        addPartners(nodes, placements);
        return placements;
    }

    private static void walkAncestors(
            FamilyTreeNode root,
            Map<UUID, FamilyTreeNode> nodes,
            Map<UUID, Placement> placements
    ) {
        Deque<GenerationNode> queue = new ArrayDeque<>();
        queue.add(new GenerationNode(root, 0));
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
            Map<UUID, FamilyTreeNode> nodes,
            Map<UUID, Placement> placements
    ) {
        Deque<GenerationNode> queue = new ArrayDeque<>();
        queue.add(new GenerationNode(root, 0));
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
            FamilyTreeNode focus,
            Map<UUID, FamilyTreeNode> nodes,
            Map<UUID, Placement> placements
    ) {
        Set<UUID> focusParents = new HashSet<>(parentIds(focus));
        if (focusParents.isEmpty()) {
            return;
        }

        nodes.values().stream()
                .filter(node -> !node.id().equals(focus.id()))
                .filter(node -> parentIds(node).stream().anyMatch(focusParents::contains))
                .sorted(Comparator.comparing(node -> node.id().toString()))
                .forEach(node -> placements.putIfAbsent(node.id(), new Placement(0, Role.SIBLING)));
    }

    private static void addPartners(Map<UUID, FamilyTreeNode> nodes, Map<UUID, Placement> placements) {
        for (Map.Entry<UUID, Placement> entry : List.copyOf(placements.entrySet())) {
            FamilyTreeNode node = nodes.get(entry.getKey());
            if (node == null || !FamilyTreeNode.isValid(node.partner()) || !nodes.containsKey(node.partner())) {
                continue;
            }
            placements.putIfAbsent(node.partner(), new Placement(entry.getValue().generation(), Role.PARTNER));
        }
    }

    private static List<UUID> parentIds(FamilyTreeNode node) {
        List<UUID> parents = new ArrayList<>(2);
        if (FamilyTreeNode.isValid(node.father())) {
            parents.add(node.father());
        }
        if (FamilyTreeNode.isValid(node.mother())) {
            parents.add(node.mother());
        }
        return parents;
    }

    private static void placeFocusGeneration(
            UUID focus,
            List<UUID> ids,
            Map<UUID, Placement> placements,
            Map<UUID, Bounds> boundsById
    ) {
        boundsById.put(focus, cardBounds(0, 0));

        List<UUID> siblings = ids.stream()
                .filter(id -> !id.equals(focus))
                .filter(id -> placements.get(id).role() == Role.SIBLING)
                .toList();
        List<UUID> others = ids.stream()
                .filter(id -> !id.equals(focus))
                .filter(id -> placements.get(id).role() != Role.SIBLING)
                .toList();

        int step = CARD_WIDTH + HORIZONTAL_GAP;
        int leftSlot = -1;
        int rightSlot = 1;
        for (int index = 0; index < siblings.size(); index++) {
            UUID id = siblings.get(index);
            if ((index & 1) == 0) {
                boundsById.put(id, cardBounds(leftSlot * step, 0));
                leftSlot--;
            } else {
                boundsById.put(id, cardBounds(rightSlot * step, 0));
                rightSlot++;
            }
        }

        for (UUID id : others) {
            Placement placement = placements.get(id);
            int spacing = placement.role() == Role.PARTNER ? CARD_WIDTH + PARTNER_GAP : step;
            int x = rightSlot * step;
            if (placement.role() == Role.PARTNER && rightSlot == 1) {
                x = spacing;
            }
            boundsById.put(id, cardBounds(x, 0));
            rightSlot++;
        }
    }

    private static void placeGeneration(int generation, List<UUID> ids, Map<UUID, Bounds> boundsById) {
        int step = CARD_WIDTH + HORIZONTAL_GAP;
        int totalWidth = ids.isEmpty() ? 0 : CARD_WIDTH + (ids.size() - 1) * step;
        int firstCenter = -(totalWidth / 2) + CARD_WIDTH / 2;
        int y = generation * (CARD_HEIGHT + GENERATION_GAP);
        for (int index = 0; index < ids.size(); index++) {
            boundsById.put(ids.get(index), cardBounds(firstCenter + index * step, y));
        }
    }

    private static Bounds cardBounds(int centerX, int centerY) {
        int left = centerX - CARD_WIDTH / 2;
        int top = centerY - CARD_HEIGHT / 2;
        return new Bounds(left, left + CARD_WIDTH, top, top + CARD_HEIGHT);
    }

    private static List<Edge> buildEdges(Map<UUID, FamilyTreeNode> nodes, Map<UUID, Bounds> boundsById) {
        Set<EdgeKey> seen = new LinkedHashSet<>();
        List<Edge> edges = new ArrayList<>();

        for (UUID id : boundsById.keySet()) {
            FamilyTreeNode node = nodes.get(id);
            if (node == null) {
                continue;
            }

            for (UUID parentId : parentIds(node)) {
                if (!boundsById.containsKey(parentId)) {
                    continue;
                }
                EdgeKey key = EdgeKey.ordered(parentId, id, EdgeType.PARENT_CHILD);
                if (seen.add(key)) {
                    edges.add(new Edge(parentId, id, EdgeType.PARENT_CHILD));
                }
            }

            UUID partnerId = node.partner();
            if (FamilyTreeNode.isValid(partnerId) && boundsById.containsKey(partnerId)) {
                EdgeKey key = EdgeKey.ordered(id, partnerId, EdgeType.PARTNER);
                if (seen.add(key)) {
                    edges.add(new Edge(id, partnerId, EdgeType.PARTNER));
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
                        .comparing((FamilyTreeView.Continuation item) -> item.anchor().toString())
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

    public enum Role {
        FOCUS,
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

    public record Card(UUID uuid, Role role, Bounds bounds) {
    }

    public record Edge(UUID from, UUID to, EdgeType type) {
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

    private record EdgeKey(UUID one, UUID two, EdgeType type) {
        private static EdgeKey ordered(UUID one, UUID two, EdgeType type) {
            if (one.toString().compareTo(two.toString()) <= 0) {
                return new EdgeKey(one, two, type);
            }
            return new EdgeKey(two, one, type);
        }
    }
}
