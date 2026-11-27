package net.conczin.mca.client.gui;

import net.conczin.mca.network.FamilyTreeView;
import net.conczin.mca.network.s2c.GetFamilyTreeResponse;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.core.GlobalPos;
import org.jetbrains.annotations.Nullable;

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

    public record GenerationRequest(UUID anchor, FamilyTreeView.Direction direction, long requestId,
                                    Optional<UUID> after) {
    }

    private record GenerationProgress(UUID cursor, Set<UUID> children) {
    }

    public record GenerationResult(boolean handled, boolean layoutChanged, @Nullable UUID anchor) {
    }

    public record HistoryEntry(UUID layoutRootId, UUID focusId, ViewportState viewport,
                               ViewState previousView, FamilyTreeLayout.Result layout) {
        public HistoryEntry(UUID layoutRootId, UUID focusId, ViewportState viewport) {
            this(layoutRootId, focusId, viewport, null, null);
        }
    }

    record ViewState(Map<UUID, FamilyTreeNode> nodes, Map<UUID, GlobalPos> graves,
                     Set<UUID> orphans, Set<FamilyTreeView.Continuation> continuations,
                     Set<UUID> unavailable, Set<UUID> completeDescendants,
                     Map<FamilyTreeView.Continuation, GenerationProgress> generationProgress) {
        ViewState {
            nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
            graves = Map.copyOf(graves);
            orphans = Set.copyOf(orphans);
            continuations = Set.copyOf(continuations);
            unavailable = Set.copyOf(unavailable);
            completeDescendants = Set.copyOf(completeDescendants);
            generationProgress = Map.copyOf(generationProgress);
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
    private final Set<UUID> completeDescendants = new LinkedHashSet<>();
    private final Map<FamilyTreeView.Continuation, GenerationProgress> generationProgress = new HashMap<>();
    private final Deque<HistoryEntry> history = new ArrayDeque<>();
    @Nullable
    private GenerationRequest pendingGeneration;

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
        return beginFocus(id, viewport, false, null);
    }

    public long beginRootFocus(UUID id, ViewportState viewport) {
        return beginRootFocus(id, viewport, null);
    }

    long beginRootFocus(UUID id, ViewportState viewport, FamilyTreeLayout.Result layout) {
        return beginFocus(id, viewport, true, layout);
    }

    private long beginFocus(UUID id, ViewportState viewport, boolean changeLayoutRoot,
                            FamilyTreeLayout.Result layout) {
        pendingGeneration = null;
        if (pendingFocusId == null
                && (!id.equals(focusId) || changeLayoutRoot && !id.equals(layoutRootId))) {
            history.push(new HistoryEntry(layoutRootId, focusId, viewport,
                    changeLayoutRoot && !id.equals(layoutRootId) ? saveView() : null, layout));
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
        return new ViewState(nodes, graves, orphans, continuations, unavailable, completeDescendants, generationProgress);
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
        completeDescendants.clear();
        completeDescendants.addAll(state.completeDescendants());
        generationProgress.clear();
        generationProgress.putAll(state.generationProgress());
    }

    private void clearView() {
        nodes.clear();
        graves.clear();
        orphans.clear();
        continuations.clear();
        unavailable.clear();
        completeDescendants.clear();
        generationProgress.clear();
    }

    public Optional<GenerationRequest> beginGeneration(FamilyTreeView.Direction direction, Set<UUID> visibleCards) {
        if (loading()) {
            return Optional.empty();
        }
        return continuations.stream()
                .filter(continuation -> continuation.direction() == direction)
                .filter(continuation -> nodes.containsKey(continuation.anchor()))
                .filter(continuation -> visibleCards.contains(continuation.anchor()))
                .min(Comparator
                        .comparingInt((FamilyTreeView.Continuation item) -> item.anchor().equals(focusId) ? 0
                                : item.anchor().equals(layoutRootId) ? 1 : 2)
                        .thenComparing(item -> item.anchor().toString()))
                .map(target -> {
                    pendingGeneration = new GenerationRequest(
                            target.anchor(),
                            target.direction(),
                            NEXT_REQUEST_ID.getAndIncrement(),
                            Optional.ofNullable(generationProgress.get(target)).map(GenerationProgress::cursor)
                    );
                    return pendingGeneration;
                });
    }

    public GenerationResult acceptGeneration(GetFamilyTreeResponse response) {
        GenerationRequest pending = pendingGeneration;
        if (pending == null
                || pending.requestId() != response.requestId()
                || !pending.anchor().equals(response.uuid())) {
            return new GenerationResult(false, false, null);
        }
        pendingGeneration = null;

        FamilyTreeView.Continuation frontier = new FamilyTreeView.Continuation(
                pending.anchor(),
                pending.direction()
        );
        if (!response.found()) {
            generationProgress.remove(frontier);
            continuations.remove(frontier);
            if (pending.direction() == FamilyTreeView.Direction.DESCENDANTS) {
                completeDescendants.add(pending.anchor());
            }
            return new GenerationResult(true, false, pending.anchor());
        }

        boolean changed = merge(response.view());
        GenerationProgress previousPage = generationProgress.remove(frontier);
        if (response.nextCursor().isPresent() || previousPage != null) {
            boolean descendants = pending.direction() == FamilyTreeView.Direction.DESCENDANTS;
            Set<UUID> children = new LinkedHashSet<>();
            if (descendants) {
                if (previousPage != null) children.addAll(previousPage.children());
                FamilyTreeNode pageAnchor = response.view().nodes().get(pending.anchor());
                if (pageAnchor != null) children.addAll(pageAnchor.children());
            }
            if (response.nextCursor().isPresent()) {
                generationProgress.put(frontier, new GenerationProgress(response.nextCursor().orElseThrow(), Set.copyOf(children)));
                completeDescendants.remove(pending.anchor());
                continuations.add(frontier);
                return new GenerationResult(true, changed, pending.anchor());
            }
            if (descendants) {
                FamilyTreeNode anchor = nodes.get(pending.anchor()).detachedCopy();
                changed |= !anchor.children().equals(children);
                anchor.children().clear();
                anchor.children().addAll(children);
                nodes.put(anchor.id(), anchor);
            }
        }
        continuations.remove(frontier);
        if (pending.direction() == FamilyTreeView.Direction.DESCENDANTS) {
            completeDescendants.add(pending.anchor());
        }
        pruneContinuations();
        return new GenerationResult(true, changed, pending.anchor());
    }

    boolean hasGenerationFrontier(FamilyTreeView.Direction direction, Set<UUID> visibleCards) {
        return continuations.stream()
                .anyMatch(continuation -> continuation.direction() == direction
                        && nodes.containsKey(continuation.anchor())
                        && visibleCards.contains(continuation.anchor()));
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

    private boolean merge(FamilyTreeView view) {
        boolean changed = false;
        view.nodes().keySet().forEach(graves::remove);
        graves.putAll(view.graves());
        orphans.removeAll(view.nodes().keySet());
        orphans.addAll(view.orphans());

        for (Map.Entry<UUID, FamilyTreeNode> entry : view.nodes().entrySet()) {
            UUID id = entry.getKey();
            FamilyTreeNode snapshot = entry.getValue().detachedCopy();
            FamilyTreeNode previous = nodes.get(id);
            FamilyTreeView.Continuation descendantsContinuation =
                    new FamilyTreeView.Continuation(id, FamilyTreeView.Direction.DESCENDANTS);
            boolean partialDescendantsInView = view.continuations().contains(descendantsContinuation);
            if (!partialDescendantsInView) {
                completeDescendants.add(id);
                continuations.remove(descendantsContinuation);
            } else if (previous != null) {
                // Omitted children are not deletions; a complete refreshed relation list is authoritative.
                snapshot.children().addAll(previous.children());
            }
            if (partialDescendantsInView) {
                for (FamilyTreeNode existing : nodes.values()) {
                    if (id.equals(existing.father()) || id.equals(existing.mother())) {
                        snapshot.addChild(existing.id());
                    }
                }
            }
            if (previous == null
                    || !previous.children().equals(snapshot.children())
                    || !Objects.equals(previous.father(), snapshot.father())
                    || !Objects.equals(previous.mother(), snapshot.mother())
                    || !Objects.equals(previous.partner(), snapshot.partner())) {
                changed = true;
            }
            nodes.put(id, snapshot);
        }

        for (FamilyTreeNode snapshot : view.nodes().values()) {
            for (UUID parentId : snapshot.streamParents().toList()) {
                if (!view.nodes().containsKey(parentId)) {
                    FamilyTreeNode loadedParent = nodes.get(parentId);
                    if (loadedParent != null && continuations.contains(
                            new FamilyTreeView.Continuation(parentId, FamilyTreeView.Direction.DESCENDANTS))) {
                        if (loadedParent.children().add(snapshot.id())) {
                            changed = true;
                        }
                    }
                }
            }
        }

        for (FamilyTreeView.Continuation continuation : view.continuations()) {
            if (continuation.direction() == FamilyTreeView.Direction.DESCENDANTS
                    && completeDescendants.contains(continuation.anchor())) {
                continue;
            }
            continuations.add(continuation);
        }
        unavailable.addAll(view.unavailable());
        unavailable.removeAll(nodes.keySet());
        return changed;
    }

    private void pruneContinuations() {
        continuations.removeIf(continuation -> {
            FamilyTreeNode node = nodes.get(continuation.anchor());
            if (node == null) {
                return true;
            }
            return switch (continuation.direction()) {
                case ANCESTORS -> node.streamParents().noneMatch(this::isUnloaded);
                case DESCENDANTS -> false;
            };
        });
    }

    private boolean isUnloaded(UUID id) {
        return FamilyTreeNode.isValid(id) && !nodes.containsKey(id) && !unavailable.contains(id);
    }

    public Optional<HistoryEntry> back() {
        pendingGeneration = null;
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
        return pendingFocusId != null || pendingGeneration != null;
    }

    boolean isPendingRequest(long requestId) {
        return (pendingFocusId != null && pendingFocusRequestId == requestId)
                || (pendingGeneration != null && pendingGeneration.requestId() == requestId);
    }

    boolean expirePendingRequests() {
        boolean expired = false;
        if (pendingFocusId != null) {
            if (!history.isEmpty()
                    && history.peek().focusId().equals(focusId)
                    && history.peek().layoutRootId().equals(layoutRootId)) {
                history.pop();
            }
            pendingFocusId = null;
            pendingLayoutRootId = null;
            pendingFocusRequestId = -1L;
            expired = true;
        }
        if (pendingGeneration != null) {
            pendingGeneration = null;
            expired = true;
        }
        return expired;
    }

    public Snapshot snapshot() {
        return new Snapshot(layoutRootId, nodes, continuations);
    }
}
