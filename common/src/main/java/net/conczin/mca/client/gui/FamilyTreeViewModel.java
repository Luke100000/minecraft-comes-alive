package net.conczin.mca.client.gui;

import net.conczin.mca.network.FamilyTreeView;
import net.conczin.mca.network.s2c.GetFamilyTreeResponse;
import net.conczin.mca.server.world.data.FamilyTreeNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class FamilyTreeViewModel {
    public enum MergeResult {
        APPLIED,
        STALE,
        NOT_FOUND
    }

    public record ViewportState(double panX, double panY, float zoom) {
    }

    public record HistoryEntry(UUID focusId, ViewportState viewport) {
    }

    public record Snapshot(
            UUID focusId,
            Map<UUID, FamilyTreeNode> nodes,
            Set<FamilyTreeView.Continuation> continuations,
            Set<UUID> unavailable
    ) {
        public Snapshot {
            nodes = Map.copyOf(nodes);
            continuations = Set.copyOf(continuations);
            unavailable = Set.copyOf(unavailable);
        }
    }

    private final Map<UUID, FamilyTreeNode> nodes = new LinkedHashMap<>();
    private final Set<FamilyTreeView.Continuation> continuations = new LinkedHashSet<>();
    private final Set<UUID> unavailable = new LinkedHashSet<>();
    private final Deque<HistoryEntry> history = new ArrayDeque<>();
    private final Map<Long, FamilyTreeView.Continuation> pendingExpansions = new LinkedHashMap<>();

    private UUID focusId;
    private UUID selection;
    private UUID pendingFocusId;
    private UUID unavailableFocusId;
    private long pendingFocusRequestId = -1L;
    private long nextRequestId = 1L;

    public FamilyTreeViewModel(UUID initialFocus) {
        this.focusId = initialFocus;
    }

    public long beginFocus(UUID id, ViewportState viewport) {
        if (!id.equals(focusId) && pendingFocusId == null) {
            history.push(new HistoryEntry(focusId, viewport));
        }
        pendingFocusId = id;
        unavailableFocusId = null;
        pendingFocusRequestId = nextRequestId++;
        return pendingFocusRequestId;
    }

    public long beginExpansion(UUID anchor, FamilyTreeView.Direction direction) {
        long requestId = nextRequestId++;
        pendingExpansions.put(requestId, new FamilyTreeView.Continuation(anchor, direction));
        return requestId;
    }

    public MergeResult accept(GetFamilyTreeResponse response) {
        merge(response.view());

        if (response.requestId() == pendingFocusRequestId
                && pendingFocusId != null
                && pendingFocusId.equals(response.uuid())) {
            pendingFocusId = null;
            pendingFocusRequestId = -1L;

            if (!response.found()) {
                unavailable.add(response.uuid());
                unavailableFocusId = response.uuid();
                if (!history.isEmpty() && history.peek().focusId().equals(focusId)) {
                    history.pop();
                }
                return MergeResult.NOT_FOUND;
            }

            focusId = response.uuid();
            unavailableFocusId = null;
            return MergeResult.APPLIED;
        }

        FamilyTreeView.Continuation expansion = pendingExpansions.remove(response.requestId());
        if (expansion != null && expansion.anchor().equals(response.uuid())) {
            continuations.remove(expansion);
            if (!response.found()) {
                unavailable.add(response.uuid());
                return MergeResult.NOT_FOUND;
            }
            return MergeResult.APPLIED;
        }

        return MergeResult.STALE;
    }

    private void merge(FamilyTreeView view) {
        nodes.putAll(view.nodes());
        continuations.addAll(view.continuations());
        unavailable.addAll(view.unavailable());
    }

    public Optional<HistoryEntry> back() {
        if (history.isEmpty()) {
            return Optional.empty();
        }
        HistoryEntry entry = history.pop();
        focusId = entry.focusId();
        pendingFocusId = null;
        pendingFocusRequestId = -1L;
        unavailableFocusId = null;
        selection = null;
        return Optional.of(entry);
    }

    public UUID focusId() {
        return focusId;
    }

    public Map<UUID, FamilyTreeNode> nodes() {
        return Map.copyOf(nodes);
    }

    public Set<FamilyTreeView.Continuation> continuations() {
        return Set.copyOf(continuations);
    }

    public Set<UUID> unavailable() {
        return Set.copyOf(unavailable);
    }

    public Optional<UUID> selection() {
        return Optional.ofNullable(selection);
    }

    public void select(UUID id) {
        selection = id;
    }

    public Optional<UUID> pendingFocusId() {
        return Optional.ofNullable(pendingFocusId);
    }

    public Set<FamilyTreeView.Continuation> pendingExpansions() {
        return Set.copyOf(pendingExpansions.values());
    }

    public Optional<UUID> unavailableFocusId() {
        return Optional.ofNullable(unavailableFocusId);
    }

    public boolean loading() {
        return pendingFocusId != null || !pendingExpansions.isEmpty();
    }

    public Snapshot snapshot() {
        return new Snapshot(focusId, nodes, continuations, unavailable);
    }
}
