package net.conczin.mca.client.gui;

import net.conczin.mca.server.world.data.FamilyTreeNode;
import org.jetbrains.annotations.Nullable;

import java.util.*;

final class FamilyTreeLayout {
    public static final int CARD_WIDTH = 128;
    public static final int CARD_HEIGHT = 64;
    public static final int HORIZONTAL_GAP = 64;
    private static final int BRANCH_GAP = 112;
    public static final int GENERATION_GAP = 56;
    public static final int PARTNER_GAP = 44;
    private static final int CONNECTOR_CLEARANCE = 26;

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
        List<UUID> anchorIds = generations.get(0);
        anchorIds.sort(UUID_ORDER);
        placeAnchorGeneration(layoutRoot, anchorIds, placements, displayPartners, boundsById);
        for (int generation : orderedGenerations) {
            if (generation > 0) {
                placeGeneration(generation, generations.get(generation), nodes, displayPartners, boundsById);
            }
        }
        for (int index = orderedGenerations.size() - 1; index >= 0; index--) {
            int generation = orderedGenerations.get(index);
            if (generation < 0) {
                placeGeneration(generation, generations.get(generation), nodes, displayPartners, boundsById);
            }
        }

        List<Card> cards = boundsById.entrySet().stream()
                .map(entry -> new Card(entry.getKey(), entry.getValue()))
                .toList();
        List<Edge> edges = buildEdges(nodes, boundsById, displayPartners);
        Bounds contentBounds = computeContentBounds(cards);

        return new Result(cards, edges, contentBounds);
    }

    private static Map<UUID, Placement> collectPlacements(
            UUID layoutRoot,
            Map<UUID, FamilyTreeNode> nodes,
            Map<UUID, UUID> displayPartners
    ) {
        Map<UUID, Placement> placements = new LinkedHashMap<>();
        placements.put(layoutRoot, new Placement(0, Role.ANCHOR));

        FamilyTreeNode rootNode = nodes.get(layoutRoot);
        walkLineage(rootNode, 0, nodes, placements, Role.ANCESTOR);
        walkLineage(rootNode, 0, nodes, placements, Role.DESCENDANT);
        addSiblings(rootNode, nodes, placements);
        for (Map.Entry<UUID, Placement> entry : List.copyOf(placements.entrySet())) {
            if (entry.getValue().role() == Role.SIBLING) {
                walkLineage(nodes.get(entry.getKey()), entry.getValue().generation(),
                        nodes, placements, Role.DESCENDANT);
            }
        }
        addPartnerBranches(nodes, placements, displayPartners);
        addContextualBranches(nodes, placements, displayPartners);
        return placements;
    }

    private static void addContextualBranches(Map<UUID, FamilyTreeNode> nodes, Map<UUID, Placement> placements,
                                              Map<UUID, UUID> displayPartners) {
        // Keep the main lineage's rows, then place any loaded side branch reachable from them.
        Deque<UUID> pending = new ArrayDeque<>(placements.keySet());
        while (!pending.isEmpty()) {
            UUID id = pending.removeFirst();
            FamilyTreeNode node = nodes.get(id);
            int generation = placements.get(id).generation();
            Placement parent = new Placement(generation - 1, Role.ANCESTOR);
            for (UUID relative : parentIds(node)) {
                enqueueUnplacedRelative(relative, parent, nodes, placements, pending);
            }
            Placement child = new Placement(generation + 1, Role.DESCENDANT);
            for (UUID relative : node.streamChildren().sorted(UUID_ORDER).toList()) {
                enqueueUnplacedRelative(relative, child, nodes, placements, pending);
            }
            UUID partner = displayPartners.get(id);
            if (partner != null) {
                enqueueUnplacedRelative(partner, new Placement(generation, Role.PARTNER), nodes, placements, pending);
            }
        }
    }

    private static void enqueueUnplacedRelative(UUID id, Placement placement, Map<UUID, FamilyTreeNode> nodes,
                                                Map<UUID, Placement> placements, Deque<UUID> pending) {
        if (nodes.containsKey(id) && placements.putIfAbsent(id, placement) == null) {
            pending.addLast(id);
        }
    }

    private static void walkLineage(
            FamilyTreeNode root,
            int rootGeneration,
            Map<UUID, FamilyTreeNode> nodes,
            Map<UUID, Placement> placements,
            Role role
    ) {
        Deque<GenerationNode> queue = new ArrayDeque<>();
        queue.add(new GenerationNode(root, rootGeneration));
        Set<UUID> visited = new HashSet<>();
        visited.add(root.id());

        boolean ancestors = role == Role.ANCESTOR;
        int step = ancestors ? -1 : 1;
        while (!queue.isEmpty()) {
            GenerationNode current = queue.removeFirst();
            int generation = current.generation() + step;
            List<UUID> relatives = ancestors
                    ? parentIds(current.node())
                    : current.node().streamChildren().sorted(UUID_ORDER).toList();
            for (UUID relativeId : relatives) {
                FamilyTreeNode relative = nodes.get(relativeId);
                if (relative == null || !visited.add(relativeId)) {
                    continue;
                }
                placements.putIfAbsent(relativeId, new Placement(generation, role));
                queue.addLast(new GenerationNode(relative, generation));
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
            walkLineage(partner, generation, nodes, placements, Role.ANCESTOR);
            walkLineage(partner, generation, nodes, placements, Role.DESCENDANT);
        }
    }

    private static List<UUID> parentIds(FamilyTreeNode node) {
        return node.streamParents().toList();
    }

    private static Map<UUID, UUID> collectDisplayPartners(Map<UUID, FamilyTreeNode> nodes) {
        Map<UUID, UUID> partners = new LinkedHashMap<>();
        for (FamilyTreeNode node : nodes.values()) {
            if (FamilyTreeNode.isValid(node.partner()) && !node.id().equals(node.partner())
                    && nodes.containsKey(node.partner())) {
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
                    || !father.isUnpairedGeneratedParentOf(child.id())
                    || !mother.isUnpairedGeneratedParentOf(child.id())) {
                continue;
            }
            partners.put(father.id(), mother.id());
            partners.put(mother.id(), father.id());
        }
        return partners;
    }

    private static void placeAnchorGeneration(
            UUID layoutRoot,
            List<UUID> ids,
            Map<UUID, Placement> placements,
            Map<UUID, UUID> displayPartners,
            Map<UUID, Bounds> boundsById
    ) {
        boundsById.put(layoutRoot, cardBounds(0, 0));

        UUID partnerId = displayPartners.get(layoutRoot);
        boolean hasPartner = FamilyTreeNode.isValid(partnerId) && ids.contains(partnerId);
        int partnerX = CARD_WIDTH + PARTNER_GAP;
        if (hasPartner) {
            boundsById.put(partnerId, cardBounds(partnerX, 0));
        }

        int leftEdge = -CARD_WIDTH / 2;
        int rightEdge = (hasPartner ? partnerX : 0) + CARD_WIDTH / 2;
        List<UUID> ordered = ids.stream()
                .sorted(Comparator.comparing((UUID id) -> placements.get(id).role() != Role.SIBLING)
                        .thenComparing(UUID_ORDER))
                .toList();
        int siblingIndex = 0;
        for (UUID id : ordered) {
            if (boundsById.containsKey(id)) {
                continue;
            }
            UUID spouse = displayPartners.get(id);
            boolean paired = spouse != null && ids.contains(spouse) && !boundsById.containsKey(spouse);
            int width = paired ? 2 * CARD_WIDTH + PARTNER_GAP : CARD_WIDTH;
            boolean left = placements.get(id).role() == Role.SIBLING && (siblingIndex++ & 1) == 0;
            int start = left ? leftEdge - HORIZONTAL_GAP - width : rightEdge + HORIZONTAL_GAP;
            boundsById.put(id, cardBounds(start + CARD_WIDTH / 2, 0));
            if (paired) {
                boundsById.put(spouse, cardBounds(start + CARD_WIDTH / 2 + CARD_WIDTH + PARTNER_GAP, 0));
            }
            if (left) {
                leftEdge = start;
            } else {
                rightEdge = start + width;
            }
        }
    }

    private static void placeGeneration(
            int generation,
            List<UUID> ids,
            Map<UUID, FamilyTreeNode> nodes,
            Map<UUID, UUID> displayPartners,
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
                if (generation > 0
                        && parentIds(nodes.get(id)).stream().anyMatch(boundsById::containsKey)
                        && parentIds(nodes.get(partnerId)).stream().anyMatch(boundsById::containsKey)
                        && generationTarget(generation, List.of(id), nodes, boundsById)
                        > generationTarget(generation, List.of(partnerId), nodes, boundsById)) {
                    // Keep each spouse on the side of their own parents, rather than sorting by UUID.
                    members = List.of(partnerId, id);
                }
            } else {
                members = List.of(id);
            }
            units.add(new GenerationUnit(members, generationTarget(generation, members, nodes, boundsById)));
        }

        units.sort(Comparator.comparingInt(GenerationUnit::targetX)
                .thenComparing(unit -> unit.ids().get(0), UUID_ORDER));
        List<Integer> centers = new ArrayList<>();
        int rightEdge = Integer.MIN_VALUE;
        long totalOffset = 0;
        GenerationUnit previousUnit = null;
        for (GenerationUnit unit : units) {
            int left = unit.targetX() - unit.width() / 2;
            if (previousUnit != null) {
                int gap = unit.targetX() == previousUnit.targetX() ? HORIZONTAL_GAP : BRANCH_GAP;
                left = Math.max(left, rightEdge + gap);
            }
            int center = left + unit.width() / 2;
            centers.add(center);
            totalOffset += unit.targetX() - center;
            rightEdge = left + unit.width();
            previousUnit = unit;
        }
        int offset = units.isEmpty() ? 0 : (int) (totalOffset / units.size());
        int y = generation * (CARD_HEIGHT + GENERATION_GAP);
        for (int index = 0; index < units.size(); index++) {
            GenerationUnit unit = units.get(index);
            int center = centers.get(index) + offset;
            if (unit.ids().size() == 2) {
                int separation = CARD_WIDTH + PARTNER_GAP;
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

    private static Bounds computeContentBounds(List<Card> cards) {
        Bounds bounds = null;
        for (Card card : cards) {
            bounds = bounds == null ? card.bounds() : bounds.union(card.bounds());
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

    public record FamilyConnector(UUID parent, @Nullable UUID secondParent, List<UUID> children,
                                  int track, int trackCount) {
        public FamilyConnector {
            children = List.copyOf(children);
        }

        int railY(Bounds parentBounds, Bounds childBounds) {
            return parentBounds.bottom() + (childBounds.top() - parentBounds.bottom())
                    * (track + 1) / (trackCount + 1);
        }
    }

    private static List<FamilyConnector> buildConnectors(List<Card> cards, List<Edge> edges) {
        Map<UUID, Bounds> bounds = new HashMap<>();
        cards.forEach(card -> bounds.put(card.uuid(), card.bounds()));
        Map<ParentPair, List<UUID>> families = new LinkedHashMap<>();
        for (Edge edge : edges) {
            if (edge.type() != EdgeType.PARENT_CHILD || !bounds.containsKey(edge.from())
                    || !bounds.containsKey(edge.to())) {
                continue;
            }
            UUID first = edge.from();
            UUID second = edge.secondParent();
            if (!bounds.containsKey(second)) {
                second = null;
            }
            if (second != null && UUID_ORDER.compare(first, second) > 0) {
                first = second;
                second = edge.from();
            }
            families.computeIfAbsent(new ParentPair(first, second), ignored -> new ArrayList<>()).add(edge.to());
        }
        Map<Integer, List<ConnectorSpan>> rows = new TreeMap<>();
        families.forEach((parents, children) -> {
            Bounds parent = bounds.get(parents.first());
            Bounds second = bounds.get(parents.second());
            int x = second == null ? parent.centerX() : (parent.centerX() + second.centerX()) / 2;
            int left = x;
            int right = x;
            for (UUID child : children) {
                left = Math.min(left, bounds.get(child).centerX());
                right = Math.max(right, bounds.get(child).centerX());
            }
            rows.computeIfAbsent(parent.bottom(), ignored -> new ArrayList<>())
                    .add(new ConnectorSpan(parents, children, left, right));
        });
        List<FamilyConnector> result = new ArrayList<>();
        for (List<ConnectorSpan> row : rows.values()) {
            row.sort(Comparator.comparingInt(ConnectorSpan::left)
                    .thenComparing(span -> span.parents().first(), UUID_ORDER));
            List<Integer> trackEnds = new ArrayList<>();
            List<Integer> tracks = new ArrayList<>();
            for (ConnectorSpan span : row) {
                int track = 0;
                while (track < trackEnds.size() && trackEnds.get(track) + CONNECTOR_CLEARANCE >= span.left()) {
                    track++;
                }
                if (track == trackEnds.size()) {
                    trackEnds.add(span.right());
                } else {
                    trackEnds.set(track, span.right());
                }
                tracks.add(track);
            }
            for (int i = 0; i < row.size(); i++) {
                ConnectorSpan span = row.get(i);
                result.add(new FamilyConnector(span.parents().first(), span.parents().second(),
                        span.children(), tracks.get(i), trackEnds.size()));
            }
        }
        return List.copyOf(result);
    }

    private record ParentPair(UUID first, @Nullable UUID second) {
    }

    private record ConnectorSpan(ParentPair parents, List<UUID> children, int left, int right) {
    }

    public record Result(
            List<Card> cards,
            List<Edge> edges,
            Bounds contentBounds,
            List<FamilyConnector> connectors
    ) {
        public Result(List<Card> cards, List<Edge> edges, Bounds contentBounds) {
            this(cards, edges, contentBounds, buildConnectors(cards, edges));
        }

        public Result {
            cards = List.copyOf(cards);
            edges = List.copyOf(edges);
            connectors = List.copyOf(connectors);
        }

        private static Result empty() {
            return new Result(List.of(), List.of(), new Bounds(0, 0, 0, 0));
        }
    }

    private record Placement(int generation, Role role) {
    }

    private record GenerationNode(FamilyTreeNode node, int generation) {
    }

    private record GenerationUnit(List<UUID> ids, int targetX) {
        int width() {
            return ids.size() == 2 ? CARD_WIDTH * 2 + PARTNER_GAP : CARD_WIDTH;
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
