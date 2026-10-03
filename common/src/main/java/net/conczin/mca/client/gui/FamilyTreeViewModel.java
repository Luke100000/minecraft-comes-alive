package net.conczin.mca.client.gui;

import net.conczin.mca.network.FamilyTreeView;
import net.conczin.mca.network.s2c.GetFamilyTreeResponse;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.core.GlobalPos;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

final class FamilyTreeViewModel {
    private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong(1L);

    public enum MergeResult {
        APPLIED,
        STALE,
        NOT_FOUND
    }

    public record ViewportState(double panX, double panY, float zoom) {
    }

    public record HistoryEntry(UUID layoutRootId, UUID focusId, ViewportState viewport) {
    }

    public record Snapshot(
            UUID layoutRootId,
            Map<UUID, FamilyTreeNode> nodes,
            Set<FamilyTreeView.Continuation> continuations
    ) {
        public Snapshot {
            nodes = Map.copyOf(nodes);
            continuations = Set.copyOf(continuations);
        }
    }

    private final Map<UUID, FamilyTreeNode> nodes = new LinkedHashMap<>();
    private final Map<UUID, FamilyTreeNode> nodesView = Collections.unmodifiableMap(nodes);
    private final Map<UUID, GlobalPos> graves = new LinkedHashMap<>();
    private final Map<UUID, GlobalPos> gravesView = Collections.unmodifiableMap(graves);
    private final Set<UUID> orphans = new LinkedHashSet<>();
    private final Set<UUID> orphansView = Collections.unmodifiableSet(orphans);
    private final Set<FamilyTreeView.Continuation> continuations = new LinkedHashSet<>();
    private final Set<UUID> unavailable = new LinkedHashSet<>();
    private final Deque<HistoryEntry> history = new ArrayDeque<>();
    private final Map<Long, FamilyTreeView.Continuation> pendingExpansions = new LinkedHashMap<>();

    private UUID layoutRootId;
    private UUID focusId;
    private UUID pendingFocusId;
    private UUID pendingLayoutRootId;
    private UUID unavailableFocusId;
    private long pendingFocusRequestId = -1L;

    public FamilyTreeViewModel(UUID initialFocus) {
        this.layoutRootId = initialFocus;
        this.focusId = initialFocus;
    }

    public long beginFocus(UUID id, ViewportState viewport) {
        return beginFocus(id, viewport, false);
    }

    public long beginRootFocus(UUID id, ViewportState viewport) {
        return beginFocus(id, viewport, true);
    }

    private long beginFocus(UUID id, ViewportState viewport, boolean changeLayoutRoot) {
        if (pendingFocusId == null
                && (!id.equals(focusId) || changeLayoutRoot && !id.equals(layoutRootId))) {
            history.push(new HistoryEntry(layoutRootId, focusId, viewport));
        }
        pendingFocusId = id;
        pendingLayoutRootId = changeLayoutRoot ? id : null;
        unavailableFocusId = null;
        pendingFocusRequestId = NEXT_REQUEST_ID.getAndIncrement();
        return pendingFocusRequestId;
    }

    public long beginExpansion(UUID anchor, FamilyTreeView.Direction direction) {
        long requestId = NEXT_REQUEST_ID.getAndIncrement();
        pendingExpansions.put(requestId, new FamilyTreeView.Continuation(anchor, direction));
        return requestId;
    }

    public MergeResult accept(GetFamilyTreeResponse response) {
        if (response.requestId() == pendingFocusRequestId
                && pendingFocusId != null
                && pendingFocusId.equals(response.uuid())) {
            UUID nextLayoutRoot = pendingLayoutRootId;
            pendingFocusId = null;
            pendingLayoutRootId = null;
            pendingFocusRequestId = -1L;

            if (!response.found()) {
                unavailable.add(response.uuid());
                unavailableFocusId = response.uuid();
                if (!history.isEmpty()
                        && history.peek().focusId().equals(focusId)
                        && history.peek().layoutRootId().equals(layoutRootId)) {
                    history.pop();
                }
                return MergeResult.NOT_FOUND;
            }

            merge(response.view());
            focusId = response.uuid();
            if (nextLayoutRoot != null) {
                if (!nextLayoutRoot.equals(layoutRootId)) {
                    pendingExpansions.clear();
                }
                layoutRootId = nextLayoutRoot;
            }
            unavailableFocusId = null;
            pruneContinuations();
            return MergeResult.APPLIED;
        }

        FamilyTreeView.Continuation expansion = pendingExpansions.get(response.requestId());
        if (expansion != null && expansion.anchor().equals(response.uuid())) {
            pendingExpansions.remove(response.requestId());
            if (!response.found()) {
                unavailable.add(response.uuid());
                return MergeResult.NOT_FOUND;
            }
            merge(response.view());
            continuations.remove(expansion);
            pruneContinuations();
            return MergeResult.APPLIED;
        }

        return MergeResult.STALE;
    }

    private void merge(FamilyTreeView view) {
        view.nodes().keySet().forEach(graves::remove);
        graves.putAll(view.graves());
        orphans.removeAll(view.nodes().keySet());
        orphans.addAll(view.orphans());
        nodes.putAll(view.nodes());
        continuations.addAll(view.continuations());
        unavailable.addAll(view.unavailable());
        unavailable.removeAll(view.nodes().keySet());
    }

    private void pruneContinuations() {
        continuations.removeIf(continuation -> {
            FamilyTreeNode node = nodes.get(continuation.anchor());
            if (node == null) {
                return true;
            }
            return switch (continuation.direction()) {
                case ANCESTORS -> node.streamParents().noneMatch(this::isUnloaded);
                case DESCENDANTS -> node.streamChildren().noneMatch(this::isUnloaded);
            };
        });
    }

    private boolean isUnloaded(UUID id) {
        return FamilyTreeNode.isValid(id) && !nodes.containsKey(id) && !unavailable.contains(id);
    }

    public Optional<HistoryEntry> back() {
        if (history.isEmpty()) {
            return Optional.empty();
        }
        HistoryEntry entry = history.pop();
        if (!entry.layoutRootId().equals(layoutRootId)) {
            pendingExpansions.clear();
        }
        layoutRootId = entry.layoutRootId();
        focusId = entry.focusId();
        pendingFocusId = null;
        pendingLayoutRootId = null;
        pendingFocusRequestId = -1L;
        unavailableFocusId = null;
        return Optional.of(entry);
    }

    public UUID focusId() {
        return focusId;
    }

    UUID layoutRootId() {
        return layoutRootId;
    }

    Optional<UUID> previousFocusId() {
        return history.isEmpty() ? Optional.empty() : Optional.of(history.peek().focusId());
    }

    public Map<UUID, FamilyTreeNode> nodes() {
        return nodesView;
    }

    public Map<UUID, GlobalPos> graves() {
        return gravesView;
    }

    public Set<UUID> orphans() {
        return orphansView;
    }

    public Optional<UUID> pendingFocusId() {
        return Optional.ofNullable(pendingFocusId);
    }

    public Optional<UUID> unavailableFocusId() {
        return Optional.ofNullable(unavailableFocusId);
    }

    public boolean loading() {
        return pendingFocusId != null || !pendingExpansions.isEmpty();
    }

    public Snapshot snapshot() {
        return new Snapshot(layoutRootId, nodes, continuations);
    }
}
