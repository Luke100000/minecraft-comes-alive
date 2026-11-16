package net.conczin.mca.client.gui;

import net.conczin.mca.network.FamilyTreeView;
import net.conczin.mca.network.s2c.GetFamilyTreeResponse;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.core.GlobalPos;

import java.util.*;
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

    public record GenerationRequest(UUID anchor, FamilyTreeView.Direction direction, Optional<UUID> after, long requestId) {
    }

    public record GenerationResult(boolean handled, boolean layoutChanged, boolean incomplete,
                                   Optional<GenerationRequest> nextRequest) {
    }

    public record HistoryEntry(UUID layoutRootId, UUID focusId, ViewportState viewport, ViewState previousView) {
        public HistoryEntry(UUID layoutRootId, UUID focusId, ViewportState viewport) {
            this(layoutRootId, focusId, viewport, null);
        }
    }

    record ViewState(Map<UUID, FamilyTreeNode> nodes, Map<UUID, GlobalPos> graves,
                     Set<UUID> orphans, Set<FamilyTreeView.Continuation> continuations,
                     Set<UUID> unavailable) {
        ViewState {
            nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
            graves = Map.copyOf(graves);
            orphans = Set.copyOf(orphans);
            continuations = Set.copyOf(continuations);
            unavailable = Set.copyOf(unavailable);
        }
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
    private GenerationBatch generationBatch;

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
        generationBatch = null;
        if (pendingFocusId == null
                && (!id.equals(focusId) || changeLayoutRoot && !id.equals(layoutRootId))) {
            history.push(new HistoryEntry(layoutRootId, focusId, viewport,
                    changeLayoutRoot && !id.equals(layoutRootId) ? saveView() : null));
        }
        pendingFocusId = id;
        pendingLayoutRootId = changeLayoutRoot ? id : null;
        unavailableFocusId = null;
        pendingFocusRequestId = NEXT_REQUEST_ID.getAndIncrement();
        return pendingFocusRequestId;
    }

    boolean selectVisible(UUID id, ViewportState viewport) {
        if (id.equals(focusId) || !nodes.containsKey(id)) {
            return false;
        }
        if (pendingFocusId != null) {
            if (!history.isEmpty() && history.peek().layoutRootId().equals(layoutRootId)
                    && history.peek().focusId().equals(focusId)) {
                history.pop();
            }
            pendingFocusId = null;
            pendingLayoutRootId = null;
            pendingFocusRequestId = -1L;
        }
        history.push(new HistoryEntry(layoutRootId, focusId, viewport));
        focusId = id;
        unavailableFocusId = null;
        return true;
    }

    boolean canGoBack() {
        return !history.isEmpty();
    }

    private ViewState saveView() {
        return new ViewState(nodes, graves, orphans, continuations, unavailable);
    }

    private void restoreView(ViewState state) {
        nodes.clear();
        nodes.putAll(state.nodes());
        graves.clear();
        graves.putAll(state.graves());
        orphans.clear();
        orphans.addAll(state.orphans());
        continuations.clear();
        continuations.addAll(state.continuations());
        unavailable.clear();
        unavailable.addAll(state.unavailable());
    }

    private void clearView() {
        nodes.clear();
        graves.clear();
        orphans.clear();
        continuations.clear();
        unavailable.clear();
    }

    public Optional<GenerationRequest> beginGeneration(FamilyTreeView.Direction direction, Set<UUID> visibleCards) {
        if (loading()) {
            return Optional.empty();
        }
        List<FamilyTreeView.Continuation> frontiers = continuations.stream()
                .filter(continuation -> continuation.direction() == direction)
                .filter(continuation -> nodes.containsKey(continuation.anchor()))
                .filter(continuation -> visibleCards.contains(continuation.anchor()))
                .sorted(Comparator.comparing(item -> item.anchor().toString()))
                .toList();
        if (frontiers.isEmpty()) {
            return Optional.empty();
        }
        generationBatch = new GenerationBatch(frontiers);
        return Optional.of(nextGenerationPage(generationBatch, Optional.empty()));
    }

    public GenerationResult acceptGeneration(GetFamilyTreeResponse response) {
        GenerationBatch batch = generationBatch;
        if (batch == null || batch.pending == null
                || batch.pending.requestId() != response.requestId()
                || !batch.pending.anchor().equals(response.uuid())) {
            return new GenerationResult(false, false, false, Optional.empty());
        }

        if (response.found()) {
            if (response.nextCursor().isPresent()) {
                UUID cursor = response.nextCursor().orElseThrow();
                if (batch.pending.after().map(previous -> cursor.compareTo(previous) > 0).orElse(true)) {
                    stageGenerationPage(batch, response.view());
                    GenerationRequest next = nextGenerationPage(batch, Optional.of(cursor));
                    return new GenerationResult(true, false, false, Optional.of(next));
                }
                batch.incomplete = true; // A malformed or stalled cursor must never loop.
            } else {
                stageGenerationPage(batch, response.view());
                batch.completedFrontiers.add(batch.frontiers.get(batch.targetIndex));
            }
        } else {
            batch.incomplete = true;
            batch.missingFrontiers.add(batch.frontiers.get(batch.targetIndex));
        }

        batch.targetIndex++;
        if (batch.targetIndex < batch.frontiers.size()) {
            return new GenerationResult(true, false, false,
                    Optional.of(nextGenerationPage(batch, Optional.empty())));
        }

        boolean changed = !batch.stagedNodes.isEmpty();
        mergeGeneration(batch);
        generationBatch = null;
        return new GenerationResult(true, changed, batch.incomplete, Optional.empty());
    }

    boolean hasGenerationFrontier(FamilyTreeView.Direction direction, Set<UUID> visibleCards) {
        return continuations.stream()
                .anyMatch(continuation -> continuation.direction() == direction
                        && nodes.containsKey(continuation.anchor())
                        && visibleCards.contains(continuation.anchor()));
    }

    private static void stageGenerationPage(GenerationBatch batch, FamilyTreeView page) {
        page.nodes().forEach((id, node) -> {
            FamilyTreeNode snapshot = node.detachedCopy();
            FamilyTreeNode previous = batch.stagedNodes.get(id);
            if (previous != null) {
                snapshot.children().addAll(previous.children());
            }
            batch.stagedNodes.put(id, snapshot);
        });
        batch.stagedContinuations.addAll(page.continuations());
        batch.stagedUnavailable.addAll(page.unavailable());
        batch.stagedOrphans.addAll(page.orphans());
        batch.stagedGraves.putAll(page.graves());
    }

    private GenerationRequest nextGenerationPage(GenerationBatch batch, Optional<UUID> after) {
        FamilyTreeView.Continuation target = batch.frontiers.get(batch.targetIndex);
        batch.pending = new GenerationRequest(target.anchor(), target.direction(), after,
                NEXT_REQUEST_ID.getAndIncrement());
        return batch.pending;
    }

    private void mergeGeneration(GenerationBatch batch) {
        batch.stagedNodes.forEach((id, node) -> {
            FamilyTreeNode snapshot = node.detachedCopy();
            FamilyTreeNode previous = nodes.get(id);
            boolean completeDirectDescendants = batch.completedFrontiers.contains(
                    new FamilyTreeView.Continuation(id, FamilyTreeView.Direction.DESCENDANTS));
            if (previous != null && !completeDirectDescendants
                    && batch.stagedContinuations.contains(new FamilyTreeView.Continuation(
                    id, FamilyTreeView.Direction.DESCENDANTS))) {
                snapshot.children().addAll(previous.children());
            }
            nodes.put(id, snapshot);
        });
        graves.keySet().removeAll(batch.stagedNodes.keySet());
        graves.putAll(batch.stagedGraves);
        orphans.removeAll(batch.stagedNodes.keySet());
        orphans.addAll(batch.stagedOrphans);
        unavailable.addAll(batch.stagedUnavailable);
        unavailable.removeAll(nodes.keySet());
        continuations.addAll(batch.stagedContinuations);
        continuations.removeAll(batch.completedFrontiers);
        continuations.removeAll(batch.missingFrontiers);
        pruneContinuations();
    }

    private static final class GenerationBatch {
        private final List<FamilyTreeView.Continuation> frontiers;
        private final Map<UUID, FamilyTreeNode> stagedNodes = new LinkedHashMap<>();
        private final Map<UUID, GlobalPos> stagedGraves = new LinkedHashMap<>();
        private final Set<FamilyTreeView.Continuation> stagedContinuations = new LinkedHashSet<>();
        private final Set<FamilyTreeView.Continuation> completedFrontiers = new LinkedHashSet<>();
        private final Set<FamilyTreeView.Continuation> missingFrontiers = new LinkedHashSet<>();
        private final Set<UUID> stagedUnavailable = new LinkedHashSet<>();
        private final Set<UUID> stagedOrphans = new LinkedHashSet<>();
        private int targetIndex;
        private GenerationRequest pending;
        private boolean incomplete;

        private GenerationBatch(List<FamilyTreeView.Continuation> frontiers) {
            this.frontiers = frontiers;
        }
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

            if (nextLayoutRoot != null && !nextLayoutRoot.equals(layoutRootId)) {
                clearView();
            }
            merge(response.view());
            focusId = response.uuid();
            if (nextLayoutRoot != null) {
                layoutRootId = nextLayoutRoot;
            }
            unavailableFocusId = null;
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
        view.nodes().forEach((id, node) -> {
            FamilyTreeNode snapshot = node.detachedCopy();
            FamilyTreeNode previous = nodes.get(id);
            if (previous != null && view.continuations().contains(new FamilyTreeView.Continuation(
                    id, FamilyTreeView.Direction.DESCENDANTS))) {
                // Omitted children are not deletions; a complete refreshed relation list is authoritative.
                snapshot.children().addAll(previous.children());
            }
            nodes.put(id, snapshot);
        });
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
        generationBatch = null;
        if (history.isEmpty()) {
            return Optional.empty();
        }
        HistoryEntry entry = history.pop();
        if (entry.previousView() != null) {
            restoreView(entry.previousView());
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
        return pendingFocusId != null || generationBatch != null;
    }

    public Snapshot snapshot() {
        return new Snapshot(layoutRootId, nodes, continuations);
    }
}
