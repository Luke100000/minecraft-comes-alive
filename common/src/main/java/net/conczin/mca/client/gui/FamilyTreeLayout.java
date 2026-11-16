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
    public static final int PARTNER_GAP = 26;

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

    public static Result layout(FamilyTreeViewModel.Snapshot snapshot, Result previous) {
        Result fresh = layout(snapshot);
        if (previous == null || previous.cards().isEmpty()
                || fresh.cards().stream().noneMatch(card -> card.uuid().equals(snapshot.layoutRootId()))) {
            return fresh;
        }

        Map<UUID, Bounds> before = new HashMap<>();
        previous.cards().forEach(card -> before.put(card.uuid(), card.bounds()));
        Bounds previousRoot = before.get(snapshot.layoutRootId());
        Bounds newRoot = fresh.cards().stream()
                .filter(card -> card.uuid().equals(snapshot.layoutRootId()))
                .map(Card::bounds)
                .findFirst().orElse(null);
        if (previousRoot == null || newRoot == null) {
            return fresh;
        }
        int rowShift = previousRoot.centerY() - newRoot.centerY();
        if (rowShift != 0) {
            List<Card> alignedCards = fresh.cards().stream()
                    .map(card -> new Card(card.uuid(), new Bounds(
                            card.bounds().left(), card.bounds().right(),
                            card.bounds().top() + rowShift, card.bounds().bottom() + rowShift)))
                    .toList();
            fresh = new Result(alignedCards, fresh.edges(), computeContentBounds(alignedCards));
        }
        Map<UUID, Bounds> canonical = new HashMap<>();
        fresh.cards().forEach(card -> canonical.put(card.uuid(), card.bounds()));
        List<Edge> updatedEdges = fresh.edges();
        Map<UUID, UUID> branchOwners = new HashMap<>();
        int focusY = canonical.get(snapshot.layoutRootId()).centerY();
        for (Edge edge : updatedEdges) {
            if (edge.type() != EdgeType.PARENT_CHILD) {
                continue;
            }
            Bounds parent = canonical.get(edge.from());
            Bounds child = canonical.get(edge.to());
            if (parent == null || child == null) {
                continue;
            }
            if (child.centerY() > focusY) {
                branchOwners.putIfAbsent(edge.to(), edge.from());
            }
            if (parent.centerY() < focusY) {
                branchOwners.putIfAbsent(edge.from(), edge.to());
                if (edge.secondParent() != null) {
                    branchOwners.putIfAbsent(edge.secondParent(), edge.to());
                }
            }
        }
        for (Edge edge : updatedEdges) {
            if (edge.type() == EdgeType.PARTNER) {
                UUID fromOwner = branchOwners.get(edge.from());
                UUID toOwner = branchOwners.get(edge.to());
                if (fromOwner != null) {
                    branchOwners.putIfAbsent(edge.to(), fromOwner);
                }
                if (toOwner != null) {
                    branchOwners.putIfAbsent(edge.from(), toOwner);
                }
            }
        }

        Map<UUID, Bounds> stable = new LinkedHashMap<>();
        for (Card card : fresh.cards()) {
            Bounds old = before.get(card.uuid());
            if (old != null && old.centerY() == card.bounds().centerY()) {
                stable.put(card.uuid(), old);
            }
        }

        Map<UUID, UUID> partners = new HashMap<>();
        Map<UUID, UUID> newChildParents = new HashMap<>();
        for (Edge edge : fresh.edges()) {
            if (edge.type() == EdgeType.PARTNER) {
                partners.put(edge.from(), edge.to());
                partners.put(edge.to(), edge.from());
            } else if (!stable.containsKey(edge.to())) {
                newChildParents.putIfAbsent(edge.to(), edge.from());
            }
        }

        Map<Integer, List<Set<UUID>>> rowGroups = new HashMap<>();
        Set<UUID> grouped = new HashSet<>();
        for (Map.Entry<UUID, Bounds> entry : stable.entrySet()) {
            if (!grouped.add(entry.getKey())) {
                continue;
            }
            Set<UUID> group = new LinkedHashSet<>();
            group.add(entry.getKey());
            UUID branch = branchOwners.get(entry.getKey());
            if (branch != null) {
                for (Map.Entry<UUID, Bounds> other : stable.entrySet()) {
                    if (other.getValue().centerY() == entry.getValue().centerY()
                            && branch.equals(branchOwners.get(other.getKey()))) {
                        group.add(other.getKey());
                        grouped.add(other.getKey());
                    }
                }
            }
            for (UUID relative : List.copyOf(group)) {
                UUID partner = partners.get(relative);
                Bounds partnerBounds = stable.get(partner);
                if (partnerBounds != null && partnerBounds.centerY() == entry.getValue().centerY()) {
                    group.add(partner);
                    grouped.add(partner);
                }
            }
            rowGroups.computeIfAbsent(entry.getValue().centerY(), ignored -> new ArrayList<>()).add(group);
        }

        Map<UUID, List<Card>> siblingGroups = new HashMap<>();
        for (Card card : fresh.cards()) {
            UUID parent = newChildParents.get(card.uuid());
            if (parent != null) {
                siblingGroups.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(card);
            }
        }
        Map<UUID, UUID> siblingGroupOwners = new HashMap<>();
        for (Map.Entry<UUID, List<Card>> entry : siblingGroups.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            for (Card child : entry.getValue()) {
                siblingGroupOwners.put(child.uuid(), entry.getKey());
                UUID partner = partners.get(child.uuid());
                if (partner != null && !stable.containsKey(partner)
                        && !newChildParents.containsKey(partner)) {
                    siblingGroupOwners.put(partner, entry.getKey());
                }
            }
        }
        Set<UUID> scheduled = new HashSet<>(stable.keySet());
        List<List<Card>> newUnits = new ArrayList<>();
        for (Card card : fresh.cards()) {
            if (!scheduled.add(card.uuid())) {
                continue;
            }
            UUID parent = siblingGroupOwners.get(card.uuid());
            List<Card> siblings = parent == null ? null : siblingGroups.get(parent);
            if (siblings != null) {
                List<Card> sorted = siblings.stream()
                        .sorted(Comparator.comparingInt((Card member) -> member.bounds().centerX())
                                .thenComparing(Card::uuid, UUID_ORDER))
                        .toList();
                // Keep each sibling's newly discovered partner immediately beside them.
                // Position the entire branch as one block, never split by unrelated cards.
                List<Card> together = new ArrayList<>();
                int left = sorted.get(0).bounds().left();
                for (Card sibling : sorted) {
                    if (!scheduled.add(sibling.uuid()) && !sibling.uuid().equals(card.uuid())) {
                        continue;
                    }
                    UUID partnerId = partners.get(sibling.uuid());
                    boolean includePartner = partnerId != null
                            && parent.equals(siblingGroupOwners.get(partnerId));
                    Bounds partnerBounds = includePartner ? canonical.get(partnerId) : null;
                    Bounds b = sibling.bounds();
                    if (includePartner && partnerBounds.centerX() < b.centerX()) {
                        scheduled.add(partnerId);
                        together.add(new Card(partnerId,
                                new Bounds(left, left + CARD_WIDTH, b.top(), b.bottom())));
                        left += CARD_WIDTH + PARTNER_GAP;
                    }
                    together.add(new Card(sibling.uuid(),
                            new Bounds(left, left + CARD_WIDTH, b.top(), b.bottom())));
                    left += CARD_WIDTH;
                    if (includePartner && partnerBounds.centerX() >= b.centerX()) {
                        scheduled.add(partnerId);
                        left += PARTNER_GAP;
                        together.add(new Card(partnerId,
                                new Bounds(left, left + CARD_WIDTH, b.top(), b.bottom())));
                        left += CARD_WIDTH;
                    }
                    left += HORIZONTAL_GAP;
                }
                newUnits.add(together);
                continue;
            }
            UUID partnerId = partners.get(card.uuid());
            Bounds partnerBounds = canonical.get(partnerId);
            if (partnerBounds != null && partnerBounds.centerY() == card.bounds().centerY()
                    && scheduled.add(partnerId)) {
                newUnits.add(List.of(card, new Card(partnerId, partnerBounds)));
            } else {
                newUnits.add(List.of(card));
            }
        }
        // Resolve the nearest rows first: descendants can then follow their already placed parents,
        // and ancestors their already placed children, without repositioning existing cards.
        newUnits.sort(Comparator
                .comparingInt((List<Card> unit) -> Math.abs(unit.get(0).bounds().centerY()))
                .thenComparingInt(unit -> unit.stream().mapToInt(c -> c.bounds().centerX()).min().orElse(0))
                .thenComparing(unit -> unit.get(0).uuid(), UUID_ORDER));

        int anchorOffset = before.get(snapshot.layoutRootId()).centerX()
                - canonical.get(snapshot.layoutRootId()).centerX();
        for (List<Card> unit : newUnits) {
            int minLeft = unit.stream().mapToInt(card -> card.bounds().left()).min().orElseThrow();
            int maxRight = unit.stream().mapToInt(card -> card.bounds().right()).max().orElseThrow();
            Set<UUID> members = new HashSet<>();
            unit.forEach(card -> members.add(card.uuid()));
            int offset = relativeAnchorOffset(members, fresh.edges(), canonical, stable, anchorOffset);
            int desiredLeft = minLeft + offset;
            if (unit.size() == 1) {
                UUID partnerId = partners.get(unit.get(0).uuid());
                Bounds existingPartner = stable.get(partnerId);
                if (existingPartner != null) {
                    boolean right = unit.get(0).bounds().centerX() >= canonical.get(partnerId).centerX();
                    desiredLeft = right
                            ? existingPartner.right() + PARTNER_GAP
                            : existingPartner.left() - PARTNER_GAP - CARD_WIDTH;
                }
            }
            int width = maxRight - minLeft;
            int row = unit.get(0).bounds().centerY();
            UUID siblingParent = siblingGroupOwners.get(unit.get(0).uuid());
            Bounds parentBounds = stable.get(siblingParent);
            List<Set<UUID>> groups = rowGroups.computeIfAbsent(row, ignored -> new ArrayList<>());
            if (unit.size() > 1 && siblingParent != null && parentBounds != null) {
                List<UUID> existingSiblings = stable.keySet().stream()
                        .filter(id -> updatedEdges.stream().anyMatch(edge -> edge.type() == EdgeType.PARENT_CHILD
                                && edge.from().equals(siblingParent) && edge.to().equals(id)))
                        .filter(id -> stable.get(id).centerY() == row)
                        .sorted(Comparator.comparingInt(id -> stable.get(id).centerX()))
                        .toList();
                if (!existingSiblings.isEmpty()) {
                    int split = unit.size() / 2;
                    if (split > 0 && split < unit.size()
                            && unit.get(split).uuid().equals(partners.get(unit.get(split - 1).uuid()))) {
                        split++;
                    }
                    List<Card> leftHalf = unit.subList(0, split);
                    List<Card> rightHalf = unit.subList(split, unit.size());
                    if (!leftHalf.isEmpty()) {
                        int halfWidth = unitWidth(leftHalf);
                        insertUnit(leftHalf, stable.get(existingSiblings.get(0)).left() - HORIZONTAL_GAP - halfWidth,
                                row, groups, stable, partners, branchOwners, snapshot.layoutRootId(), true);
                    }
                    if (!rightHalf.isEmpty()) {
                        insertUnit(rightHalf, stable.get(existingSiblings.get(existingSiblings.size() - 1)).right() + HORIZONTAL_GAP,
                                row, groups, stable, partners, branchOwners, snapshot.layoutRootId(), true);
                    }
                    continue;
                }
                desiredLeft = parentBounds.centerX() - width / 2;
            }
            boolean reflow = unit.size() > 1 && siblingParent != null && parentBounds != null
                    && !groups.isEmpty();
            insertUnit(unit, desiredLeft, row, groups, stable, partners, branchOwners,
                    snapshot.layoutRootId(), reflow);
        }

        List<Card> cards = fresh.cards().stream()
                .map(card -> new Card(card.uuid(), stable.getOrDefault(card.uuid(), card.bounds())))
                .toList();
        return new Result(cards, fresh.edges(), computeContentBounds(cards));
    }

    private static int unitWidth(List<Card> cards) {
        return cards.stream().mapToInt(card -> card.bounds().right()).max().orElseThrow()
                - cards.stream().mapToInt(card -> card.bounds().left()).min().orElseThrow();
    }

    private static void insertUnit(List<Card> unit, int desiredLeft, int row,
                                   List<Set<UUID>> groups, Map<UUID, Bounds> stable,
                                   Map<UUID, UUID> partners, Map<UUID, UUID> branchOwners,
                                   UUID anchor, boolean allowReflow) {
        Set<UUID> members = new HashSet<>();
        unit.forEach(card -> members.add(card.uuid()));
        int minLeft = unit.stream().mapToInt(card -> card.bounds().left()).min().orElseThrow();
        int width = unitWidth(unit);
        int left = allowReflow && makeRoom(desiredLeft, width, groups, stable, members,
                partners, branchOwners, anchor)
                ? desiredLeft : nearestFreeLeft(desiredLeft, width, row, members, partners, branchOwners, stable);
        for (Card card : unit) {
            Bounds b = card.bounds();
            int translatedLeft = left + b.left() - minLeft;
            stable.put(card.uuid(), new Bounds(translatedLeft, translatedLeft + CARD_WIDTH,
                    b.top(), b.bottom()));
        }
        groups.add(members);
    }

    private static boolean makeRoom(int desiredLeft, int width, List<Set<UUID>> groups,
                                    Map<UUID, Bounds> positions, Set<UUID> members,
                                    Map<UUID, UUID> partners, Map<UUID, UUID> branchOwners,
                                    UUID fixedAnchor) {
        int midpoint = desiredLeft + width / 2;
        for (Set<UUID> group : groups) {
            if (group.contains(fixedAnchor)
                    && groupRight(group, positions) + gapBetween(members, group, partners, branchOwners) > desiredLeft
                    && groupLeft(group, positions) - gapBetween(members, group, partners, branchOwners) < desiredLeft + width) {
                return false;
            }
        }

        List<Set<UUID>> left = new ArrayList<>();
        List<Set<UUID>> right = new ArrayList<>();
        for (Set<UUID> group : groups) {
            int center = (groupLeft(group, positions) + groupRight(group, positions)) / 2;
            (center <= midpoint ? left : right).add(group);
        }
        left.sort(Comparator.comparingInt((Set<UUID> group) -> groupRight(group, positions)).reversed());
        right.sort(Comparator.comparingInt(group -> groupLeft(group, positions)));

        int boundary = desiredLeft;
        Set<UUID> neighbor = members;
        for (Set<UUID> group : left) {
            int shift = Math.min(0, boundary - gapBetween(neighbor, group, partners, branchOwners)
                    - groupRight(group, positions));
            shiftGroup(group, shift, positions);
            boundary = groupLeft(group, positions);
            neighbor = group;
        }
        boundary = desiredLeft + width;
        neighbor = members;
        for (Set<UUID> group : right) {
            int shift = Math.max(0, boundary + gapBetween(neighbor, group, partners, branchOwners)
                    - groupLeft(group, positions));
            shiftGroup(group, shift, positions);
            boundary = groupRight(group, positions);
            neighbor = group;
        }
        return true;
    }

    private static int groupLeft(Set<UUID> group, Map<UUID, Bounds> positions) {
        return group.stream().mapToInt(id -> positions.get(id).left()).min().orElseThrow();
    }

    private static int groupRight(Set<UUID> group, Map<UUID, Bounds> positions) {
        return group.stream().mapToInt(id -> positions.get(id).right()).max().orElseThrow();
    }

    private static void shiftGroup(Set<UUID> group, int dx, Map<UUID, Bounds> positions) {
        if (dx == 0) {
            return;
        }
        for (UUID id : group) {
            Bounds before = positions.get(id);
            positions.put(id, new Bounds(before.left() + dx, before.right() + dx,
                    before.top(), before.bottom()));
        }
    }

    private static int relativeAnchorOffset(Set<UUID> members, List<Edge> edges,
                                            Map<UUID, Bounds> canonical, Map<UUID, Bounds> stable,
                                            int fallback) {
        long total = 0;
        int count = 0;
        for (Edge edge : edges) {
            if (members.contains(edge.from()) && stable.containsKey(edge.to())) {
                total += stable.get(edge.to()).centerX() - canonical.get(edge.to()).centerX();
                count++;
            } else if (members.contains(edge.to()) && stable.containsKey(edge.from())) {
                total += stable.get(edge.from()).centerX() - canonical.get(edge.from()).centerX();
                count++;
            }
            if (edge.secondParent() != null && members.contains(edge.to())
                    && stable.containsKey(edge.secondParent())) {
                total += stable.get(edge.secondParent()).centerX()
                        - canonical.get(edge.secondParent()).centerX();
                count++;
            }
        }
        return count == 0 ? fallback : (int) Math.round(total / (double) count);
    }

    private static int nearestFreeLeft(int desiredLeft, int width, int y, Set<UUID> members,
                                       Map<UUID, UUID> partners, Map<UUID, UUID> branchOwners,
                                       Map<UUID, Bounds> placed) {
        List<Integer> candidates = new ArrayList<>();
        candidates.add(desiredLeft);
        for (Map.Entry<UUID, Bounds> entry : placed.entrySet()) {
            Bounds occupied = entry.getValue();
            if (occupied.centerY() != y) {
                continue;
            }
            int gap = gapBetween(members, Set.of(entry.getKey()), partners, branchOwners);
            candidates.add(occupied.left() - gap - width);
            candidates.add(occupied.right() + gap);
        }
        int best = desiredLeft;
        long bestDistance = Long.MAX_VALUE;
        for (int candidate : candidates) {
            boolean free = true;
            for (Map.Entry<UUID, Bounds> entry : placed.entrySet()) {
                Bounds occupied = entry.getValue();
                if (occupied.centerY() != y) {
                    continue;
                }
                int gap = gapBetween(members, Set.of(entry.getKey()), partners, branchOwners);
                if (candidate < occupied.right() + gap
                        && candidate + width > occupied.left() - gap) {
                    free = false;
                    break;
                }
            }
            if (free && Math.abs((long) candidate - desiredLeft) < bestDistance) {
                bestDistance = Math.abs((long) candidate - desiredLeft);
                best = candidate;
            }
        }
        return best;
    }

    private static int gapBetween(Set<UUID> first, Set<UUID> second,
                                  Map<UUID, UUID> partners, Map<UUID, UUID> branchOwners) {
        for (UUID id : first) {
            UUID partner = partners.get(id);
            if (partner != null && second.contains(partner)) {
                return PARTNER_GAP;
            }
        }
        Set<UUID> firstBranches = new HashSet<>();
        for (UUID id : first) {
            UUID owner = branchOwners.get(id);
            if (owner != null) {
                firstBranches.add(owner);
            }
        }
        if (firstBranches.isEmpty()) {
            return HORIZONTAL_GAP;
        }
        boolean hasSecondBranch = false;
        for (UUID id : second) {
            UUID owner = branchOwners.get(id);
            if (owner != null) {
                hasSecondBranch = true;
                if (firstBranches.contains(owner)) {
                    return HORIZONTAL_GAP;
                }
            }
        }
        return hasSecondBranch ? BRANCH_GAP : HORIZONTAL_GAP;
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
                while (track < trackEnds.size() && trackEnds.get(track) + PARTNER_GAP >= span.left()) {
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
