package net.conczin.mca.client.gui;

import net.conczin.mca.FamilyTreeTestSupport;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.FamilyTreeView;
import net.conczin.mca.network.s2c.GetFamilyTreeResponse;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static net.conczin.mca.FamilyTreeTestSupport.uuid;
import static net.conczin.mca.network.FamilyTreeView.Direction.ANCESTORS;
import static net.conczin.mca.network.FamilyTreeView.Direction.DESCENDANTS;
import static org.junit.jupiter.api.Assertions.*;

class FamilyTreeViewModelTest {
    private static final UUID ROOT = uuid(1);
    private static final UUID PARENT = uuid(2);
    private static final UUID GRANDPARENT = uuid(3);
    private static final UUID CHILD = uuid(4);
    private static final UUID OTHER = uuid(5);
    private static final UUID MISSING = uuid(6);

    @BeforeAll
    static void bootstrapMinecraft() {
        FamilyTreeTestSupport.bootstrapMinecraft();
    }

    @Test
    void selectingLoadedPersonChangesOnlyFocusAndCanGoBack() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeNode parent = node(ROOT);
        parent.addChild(CHILD);
        long request = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(request, ROOT, view(Map.of(ROOT, parent, CHILD, node(CHILD)),
                Set.of(), Set.of())));
        FamilyTreeViewModel.Snapshot before = model.snapshot();
        FamilyTreeViewModel.ViewportState camera = viewport(17, -23, 0.75F);

        assertTrue(model.selectVisible(CHILD, camera));
        assertEquals(CHILD, model.focusId());
        assertEquals(before, model.snapshot(), "Selection must not add or remove family records or change root");
        assertFalse(model.loading(), "Selecting a loaded card must not request a new server snapshot");
        assertTrue(model.canGoBack());
        assertFalse(model.selectVisible(CHILD, viewport(90, 90, 2)));
        assertEquals(camera, model.back().orElseThrow().viewport());
        assertEquals(ROOT, model.focusId());
        assertFalse(model.canGoBack(), "Repeated selection must not add extra history entries");
    }

    @Test
    void selectionDuringGenerationBatchDoesNotCancelExpansionOrMergeExtraBranches() {
        FamilyTreeNode parent = node(ROOT);
        parent.addChild(CHILD);
        parent.addChild(MISSING);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(initial, ROOT, view(Map.of(ROOT, parent, CHILD, node(CHILD)),
                Set.of(new FamilyTreeView.Continuation(ROOT, DESCENDANTS)), Set.of())));
        FamilyTreeViewModel.GenerationRequest page = model.beginGeneration(DESCENDANTS,
                Set.of(ROOT, CHILD)).orElseThrow();

        assertTrue(model.selectVisible(CHILD, viewport(0, 0, 1)));
        assertTrue(model.loading(), "Selection cannot cancel a generation-wide request");
        assertTrue(model.acceptGeneration(new GetFamilyTreeResponse(page.requestId(), ROOT, true,
                view(Map.of(ROOT, parent, CHILD, node(CHILD), MISSING, node(MISSING)),
                        Set.of(), Set.of()), Optional.empty())).layoutChanged());
        assertEquals(CHILD, model.focusId());
        assertTrue(model.nodes().containsKey(MISSING));
    }

    @Test
    void searchingDifferentRootReplacesViewAndBackRestoresItsMetadata() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        GlobalPos grave = GlobalPos.of(Level.OVERWORLD, new BlockPos(15, 65, 9));
        long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(initial, ROOT, view(Map.of(ROOT, node(ROOT), CHILD, node(CHILD)),
                Set.of(), Set.of(), Set.of(ROOT), Map.of(ROOT, grave))));
        FamilyTreeViewModel.ViewportState oldCamera = viewport(27, -19, 1.5F);
        long search = model.beginRootFocus(OTHER, oldCamera);
        model.accept(response(search, OTHER, view(Map.of(OTHER, node(OTHER)),
                Set.of(), Set.of(), Set.of(OTHER), Map.of())));

        assertEquals(Set.of(OTHER), model.nodes().keySet(), "A new root must start a separate visible family view");
        assertEquals(Set.of(OTHER), model.orphans());
        assertTrue(model.graves().isEmpty());
        assertEquals(OTHER, model.layoutRootId());
        assertEquals(oldCamera, model.back().orElseThrow().viewport());
        assertEquals(ROOT, model.layoutRootId());
        assertEquals(Set.of(ROOT, CHILD), model.nodes().keySet());
        assertEquals(Set.of(ROOT), model.orphans());
        assertEquals(Map.of(ROOT, grave), model.graves());
    }

    @Test
    void localSelectionIgnoresPreviouslyRequestedRemoteRoot() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(initial, ROOT, view(Map.of(ROOT, node(ROOT), CHILD, node(CHILD)),
                Set.of(), Set.of())));
        long pendingSearch = model.beginRootFocus(OTHER, viewport(2, 3, 1));

        assertTrue(model.selectVisible(CHILD, viewport(4, 5, 1)));
        assertEquals(FamilyTreeViewModel.MergeResult.STALE,
                model.accept(response(pendingSearch, OTHER,
                        view(Map.of(OTHER, node(OTHER)), Set.of(), Set.of()))));
        assertEquals(CHILD, model.focusId());
        assertEquals(ROOT, model.layoutRootId());
        assertEquals(Set.of(ROOT, CHILD), model.nodes().keySet());
        assertEquals(ROOT, model.back().orElseThrow().focusId());
        assertFalse(model.canGoBack());
    }

    @Test
    void acceptedResponseMergesWithoutDiscardingPreviouslyLoadedNodes() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeNode parent = node(PARENT);
        parent.setFather(node(GRANDPARENT));
        FamilyTreeView.Continuation frontier = new FamilyTreeView.Continuation(PARENT, ANCESTORS);
        long initialRequest = model.beginFocus(ROOT, viewport(0, 0, 1));
        assertEquals(FamilyTreeViewModel.MergeResult.APPLIED, model.accept(response(
                initialRequest,
                ROOT,
                view(Map.of(ROOT, node(ROOT), PARENT, parent), Set.of(frontier), Set.of())
        )));

        FamilyTreeViewModel.GenerationRequest expansion = model.beginGeneration(ANCESTORS,
                Set.of(PARENT)).orElseThrow();
        assertTrue(model.acceptGeneration(new GetFamilyTreeResponse(expansion.requestId(), PARENT, true,
                view(Map.of(PARENT, parent, GRANDPARENT, node(GRANDPARENT)), Set.of(), Set.of()),
                Optional.empty())).layoutChanged());

        assertEquals(Set.of(ROOT, PARENT, GRANDPARENT), model.nodes().keySet());
        assertEquals(ROOT, model.focusId());
    }

    @Test
    void refocusingWithABoundedViewRetainsPreviouslyExpandedChildLinks() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeNode parent = node(ROOT);
        parent.addChild(CHILD);
        parent.addChild(OTHER);
        long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
        assertEquals(FamilyTreeViewModel.MergeResult.APPLIED, model.accept(response(initial, ROOT,
                view(Map.of(ROOT, parent, CHILD, node(CHILD), OTHER, node(OTHER)),
                        Set.of(), Set.of()))));

        FamilyTreeNode boundedParent = node(ROOT);
        boundedParent.addChild(CHILD);
        long refocus = model.beginFocus(CHILD, viewport(5, 5, 1));
        assertEquals(FamilyTreeViewModel.MergeResult.APPLIED, model.accept(response(refocus, CHILD,
                view(Map.of(ROOT, boundedParent, CHILD, node(CHILD)),
                        Set.of(new FamilyTreeView.Continuation(ROOT, DESCENDANTS)), Set.of()))));

        assertTrue(model.nodes().get(ROOT).children().containsAll(Set.of(CHILD, OTHER)),
                "A bounded refocus must not erase edges to relatives that are still loaded");
    }

    @Test
    void aCompleteFreshParentSnapshotRemovesAFormerChildRelationship() {
        FamilyTreeNode oldParent = node(ROOT);
        FamilyTreeNode child = node(CHILD);
        child.setFather(oldParent);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(initial, ROOT, view(Map.of(ROOT, oldParent, CHILD, child),
                Set.of(), Set.of())));
        assertTrue(model.nodes().get(ROOT).children().contains(CHILD));

        // The server now reports no children and no omitted descendants for this parent.
        long refresh = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(refresh, ROOT, view(Map.of(ROOT, node(ROOT)), Set.of(), Set.of())));

        assertFalse(model.nodes().get(ROOT).children().contains(CHILD),
                "Removed parent-child relations must not be resurrected from the client cache");
        assertFalse(FamilyTreeLayout.layout(model.snapshot()).edges().stream()
                .anyMatch(edge -> edge.type() == FamilyTreeLayout.EdgeType.PARENT_CHILD
                        && edge.from().equals(ROOT) && edge.to().equals(CHILD)));
    }

    @Test
    void nodesViewIsStableAndReadOnly() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long request = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(request, ROOT, view(Map.of(ROOT, node(ROOT)), Set.of(), Set.of())));

        Map<UUID, FamilyTreeNode> nodes = model.nodes();

        assertSame(nodes, model.nodes());
        assertThrows(UnsupportedOperationException.class, () -> nodes.put(CHILD, node(CHILD)));
    }

    @Test
    void newFocusPushesOldFocusAndViewportOntoHistory() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeViewModel.ViewportState previousViewport = viewport(12, -8, 0.75f);

        model.beginFocus(CHILD, previousViewport);

        FamilyTreeViewModel.HistoryEntry entry = model.back().orElseThrow();
        assertEquals(ROOT, entry.focusId());
        assertEquals(previousViewport, entry.viewport());
    }

    @Test
    void backRestoresFocusAndViewport() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeViewModel.ViewportState previousViewport = viewport(20, 30, 1.25f);
        long request = model.beginFocus(CHILD, previousViewport);
        model.accept(response(request, CHILD, view(Map.of(CHILD, node(CHILD)), Set.of(), Set.of())));
        assertEquals(CHILD, model.focusId());

        FamilyTreeViewModel.HistoryEntry restored = model.back().orElseThrow();

        assertEquals(ROOT, model.focusId());
        assertEquals(ROOT, restored.focusId());
        assertEquals(previousViewport, restored.viewport());
    }

    @Test
    void changingFocusKeepsTheOriginalLayoutRoot() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long request = model.beginFocus(OTHER, viewport(0, 0, 1));

        model.accept(response(request, OTHER, view(Map.of(OTHER, node(OTHER)), Set.of(), Set.of())));

        assertEquals(ROOT, model.snapshot().layoutRootId());
        assertEquals(OTHER, model.focusId());
        assertEquals(ROOT, model.snapshot().layoutRootId());
    }

    @Test
    void rootNavigationChangesLayoutRootAndBackRestoresIt() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeViewModel.ViewportState previousViewport = viewport(7, -3, 1.25f);
        long request = model.beginRootFocus(OTHER, previousViewport);

        model.accept(response(request, OTHER, view(Map.of(OTHER, node(OTHER)), Set.of(), Set.of())));

        assertEquals(OTHER, model.snapshot().layoutRootId());
        assertEquals(OTHER, model.focusId());

        FamilyTreeViewModel.HistoryEntry restored = model.back().orElseThrow();
        assertEquals(ROOT, model.snapshot().layoutRootId());
        assertEquals(ROOT, model.focusId());
        assertEquals(ROOT, restored.layoutRootId());
        assertEquals(previousViewport, restored.viewport());
    }

    @Test
    void staleFocusResponseDoesNotReplaceNewerPendingState() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long first = model.beginFocus(CHILD, viewport(0, 0, 1));
        long second = model.beginFocus(OTHER, viewport(1, 2, 1));

        FamilyTreeViewModel.MergeResult result = model.accept(response(
                first,
                CHILD,
                view(Map.of(CHILD, node(CHILD)), Set.of(), Set.of())
        ));

        assertEquals(FamilyTreeViewModel.MergeResult.STALE, result);
        assertEquals(ROOT, model.focusId());
        assertEquals(OTHER, model.pendingFocusId().orElseThrow());
        assertFalse(model.nodes().containsKey(CHILD));
        assertTrue(model.loading());
        assertTrue(second > first);
    }

    @Test
    void failedLatestFocusKeepsLastValidGraphAndMarksUnavailable() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(initial, ROOT, view(Map.of(ROOT, node(ROOT)), Set.of(), Set.of())));
        long missing = model.beginFocus(MISSING, viewport(3, 4, 1));

        FamilyTreeViewModel.MergeResult result = model.accept(new GetFamilyTreeResponse(
                missing,
                MISSING,
                false,
                FamilyTreeView.empty()
        ));

        assertEquals(FamilyTreeViewModel.MergeResult.NOT_FOUND, result);
        assertEquals(ROOT, model.focusId());
        assertEquals(Set.of(ROOT), model.nodes().keySet());
        assertEquals(MISSING, model.unavailableFocusId().orElseThrow());
        assertFalse(model.loading());
    }

    @Test
    void generationExpansionWaitsForEveryVisibleFrontierBeforeChangingGraph() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode sibling = node(OTHER);
        root.addChild(CHILD);
        sibling.addChild(GRANDPARENT);
        long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(initial, ROOT, view(Map.of(ROOT, root, OTHER, sibling), Set.of(
                new FamilyTreeView.Continuation(ROOT, DESCENDANTS),
                new FamilyTreeView.Continuation(OTHER, DESCENDANTS)), Set.of())));

        FamilyTreeViewModel.GenerationRequest first = model.beginGeneration(DESCENDANTS,
                Set.of(ROOT, OTHER)).orElseThrow();
        assertTrue(model.beginGeneration(DESCENDANTS, Set.of(ROOT, OTHER)).isEmpty());
        FamilyTreeNode child = node(CHILD);
        child.setFather(root);
        FamilyTreeViewModel.GenerationResult partial = model.acceptGeneration(new GetFamilyTreeResponse(
                first.requestId(), first.anchor(), true, view(Map.of(ROOT, root, CHILD, child),
                Set.of(), Set.of()), Optional.empty()));

        assertTrue(partial.handled());
        assertFalse(partial.layoutChanged());
        assertFalse(model.nodes().containsKey(CHILD), "Do not reflow after the first response");
        FamilyTreeViewModel.GenerationRequest second = partial.nextRequest().orElseThrow();
        assertNotEquals(first.anchor(), second.anchor());

        FamilyTreeNode niece = node(GRANDPARENT);
        niece.setFather(sibling);
        FamilyTreeViewModel.GenerationResult complete = model.acceptGeneration(new GetFamilyTreeResponse(
                second.requestId(), second.anchor(), true, view(Map.of(OTHER, sibling, GRANDPARENT, niece),
                Set.of(), Set.of()), Optional.empty()));

        assertTrue(complete.layoutChanged());
        assertTrue(complete.nextRequest().isEmpty());
        assertTrue(model.nodes().keySet().containsAll(Set.of(CHILD, GRANDPARENT)));
        assertEquals(ROOT, model.focusId());
        assertFalse(model.loading());
    }

    @Test
    void completedGenerationWithStaleReferencesDoesNotOfferTheSameExpansionAgain() {
        for (FamilyTreeView.Direction direction : FamilyTreeView.Direction.values()) {
            FamilyTreeNode root = node(ROOT);
            if (direction == ANCESTORS) {
                root.setFather(node(PARENT));
            } else {
                root.addChild(CHILD);
            }
            FamilyTreeView.Continuation pending = new FamilyTreeView.Continuation(ROOT, direction);
            FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
            long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
            model.accept(response(initial, ROOT, view(Map.of(ROOT, root), Set.of(pending), Set.of())));

            FamilyTreeViewModel.GenerationRequest request = model.beginGeneration(direction,
                    Set.of(ROOT)).orElseThrow();
            model.acceptGeneration(new GetFamilyTreeResponse(request.requestId(), ROOT, true,
                    view(Map.of(ROOT, root), Set.of(pending), Set.of()), Optional.empty()));

            assertFalse(model.hasGenerationFrontier(direction, Set.of(ROOT)),
                    "A completed " + direction + " page must retire the requested frontier");
        }
    }

    @Test
    void missingGenerationAnchorRetiresItsFrontierWithoutDroppingCachedCards() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode child = node(CHILD);
        child.addChild(MISSING);
        root.addChild(CHILD);
        FamilyTreeView.Continuation frontier = new FamilyTreeView.Continuation(CHILD, DESCENDANTS);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(initial, ROOT, view(Map.of(ROOT, root, CHILD, child),
                Set.of(frontier), Set.of())));
        FamilyTreeViewModel.Snapshot before = model.snapshot();

        FamilyTreeViewModel.GenerationRequest request = model.beginGeneration(DESCENDANTS,
                Set.of(CHILD)).orElseThrow();
        FamilyTreeViewModel.GenerationResult result = model.acceptGeneration(new GetFamilyTreeResponse(
                request.requestId(), CHILD, false, FamilyTreeView.empty(), Optional.empty()));

        assertTrue(result.handled());
        assertTrue(result.incomplete());
        assertFalse(result.layoutChanged());
        assertTrue(result.nextRequest().isEmpty());
        assertEquals(before.nodes(), model.snapshot().nodes(),
                "A missing server anchor must not erase already visible cards or edges");
        assertFalse(model.hasGenerationFrontier(DESCENDANTS, Set.of(CHILD)));
        assertTrue(model.beginGeneration(DESCENDANTS, Set.of(CHILD)).isEmpty(),
                "An exhausted generation must not send the same request again");
    }

    @Test
    void generationPagesUnionChildrenAndRejectStaleReplies() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeNode root = node(ROOT);
        root.addChild(CHILD);
        long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(initial, ROOT, view(Map.of(ROOT, root),
                Set.of(new FamilyTreeView.Continuation(ROOT, DESCENDANTS)), Set.of())));

        FamilyTreeViewModel.GenerationRequest request = model.beginGeneration(DESCENDANTS,
                Set.of(ROOT)).orElseThrow();
        FamilyTreeNode firstChild = node(CHILD);
        firstChild.setFather(root);
        FamilyTreeViewModel.GenerationResult page = model.acceptGeneration(new GetFamilyTreeResponse(
                request.requestId(), ROOT, true, view(Map.of(ROOT, root, CHILD, firstChild),
                Set.of(), Set.of()), Optional.of(CHILD)));
        FamilyTreeViewModel.GenerationRequest next = page.nextRequest().orElseThrow();
        assertEquals(Optional.of(CHILD), next.after());
        assertFalse(model.nodes().containsKey(CHILD));
        assertFalse(model.acceptGeneration(new GetFamilyTreeResponse(request.requestId(), ROOT,
                true, view(Map.of(OTHER, node(OTHER)), Set.of(), Set.of()), Optional.empty())).handled());

        FamilyTreeNode secondSnapshot = node(ROOT);
        secondSnapshot.addChild(OTHER);
        FamilyTreeNode secondChild = node(OTHER);
        secondChild.setFather(secondSnapshot);
        FamilyTreeViewModel.GenerationResult complete = model.acceptGeneration(new GetFamilyTreeResponse(
                next.requestId(), ROOT, true, view(Map.of(ROOT, secondSnapshot, OTHER, secondChild),
                Set.of(), Set.of()), Optional.empty()));

        assertTrue(complete.layoutChanged());
        assertTrue(model.nodes().get(ROOT).children().containsAll(Set.of(CHILD, OTHER)));
        assertTrue(model.nodes().keySet().containsAll(Set.of(CHILD, OTHER)));
    }

    @Test
    void repeatedOrBackwardCursorStopsTheBatchWithoutAnotherRequest() {
        for (UUID stalled : Set.of(CHILD, PARENT)) {
            FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
            FamilyTreeNode root = node(ROOT);
            root.addChild(CHILD);
            long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
            model.accept(response(initial, ROOT, view(Map.of(ROOT, root),
                    Set.of(new FamilyTreeView.Continuation(ROOT, DESCENDANTS)), Set.of())));

            FamilyTreeViewModel.GenerationRequest first = model.beginGeneration(DESCENDANTS,
                    Set.of(ROOT)).orElseThrow();
            FamilyTreeViewModel.GenerationResult advancing = model.acceptGeneration(new GetFamilyTreeResponse(
                    first.requestId(), ROOT, true, view(Map.of(ROOT, root), Set.of(), Set.of()),
                    Optional.of(CHILD)));
            FamilyTreeViewModel.GenerationRequest second = advancing.nextRequest().orElseThrow();
            FamilyTreeViewModel.GenerationResult stalledResult = model.acceptGeneration(new GetFamilyTreeResponse(
                    second.requestId(), ROOT, true, view(Map.of(ROOT, root), Set.of(), Set.of()),
                    Optional.of(stalled)));

            assertTrue(stalledResult.handled());
            assertTrue(stalledResult.incomplete());
            assertTrue(stalledResult.nextRequest().isEmpty(), "Non-advancing cursor must not loop");
            assertFalse(model.loading());
            assertTrue(model.hasGenerationFrontier(DESCENDANTS, Set.of(ROOT)));
        }
    }

    @Test
    void completedDirectDescendantGenerationRetiresDeletedChildEdges() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeNode cached = node(ROOT);
        cached.addChild(CHILD);
        cached.addChild(OTHER);
        cached.addChild(MISSING); // Previously indicated an unloaded child frontier.
        long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(initial, ROOT, view(Map.of(ROOT, cached, CHILD, node(CHILD), OTHER, node(OTHER)),
                Set.of(new FamilyTreeView.Continuation(ROOT, DESCENDANTS)), Set.of())));

        FamilyTreeViewModel.GenerationRequest request = model.beginGeneration(DESCENDANTS,
                Set.of(ROOT)).orElseThrow();
        FamilyTreeNode fresh = node(ROOT);
        fresh.addChild(CHILD); // OTHER is deleted on the server.
        FamilyTreeViewModel.GenerationResult complete = model.acceptGeneration(new GetFamilyTreeResponse(
                request.requestId(), ROOT, true, view(Map.of(ROOT, fresh, CHILD, node(CHILD)),
                Set.of(), Set.of()), Optional.empty()));

        assertTrue(complete.handled());
        assertFalse(complete.incomplete());
        assertFalse(model.nodes().get(ROOT).children().contains(OTHER),
                "A completed direct generation is authoritative for the parent's links");
        assertFalse(model.hasGenerationFrontier(DESCENDANTS, Set.of(ROOT)));
    }

    @Test
    void expandingBothParentsProducesOneSharedChildCard() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeNode father = node(ROOT);
        FamilyTreeNode mother = node(OTHER);
        FamilyTreeNode child = node(CHILD);
        child.setFather(father);
        child.setMother(mother);
        long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(initial, ROOT, view(Map.of(ROOT, father, OTHER, mother), Set.of(
                new FamilyTreeView.Continuation(ROOT, DESCENDANTS),
                new FamilyTreeView.Continuation(OTHER, DESCENDANTS)), Set.of())));

        FamilyTreeViewModel.GenerationRequest first = model.beginGeneration(DESCENDANTS,
                Set.of(ROOT, OTHER)).orElseThrow();
        FamilyTreeViewModel.GenerationResult staged = model.acceptGeneration(new GetFamilyTreeResponse(
                first.requestId(), first.anchor(), true, view(Map.of(ROOT, father, CHILD, child),
                Set.of(), Set.of()), Optional.empty()));
        assertFalse(staged.layoutChanged());
        FamilyTreeViewModel.GenerationRequest second = staged.nextRequest().orElseThrow();
        FamilyTreeViewModel.GenerationResult done = model.acceptGeneration(new GetFamilyTreeResponse(
                second.requestId(), second.anchor(), true, view(Map.of(OTHER, mother, CHILD, child),
                Set.of(), Set.of()), Optional.empty()));

        assertTrue(done.layoutChanged());
        assertEquals(3, model.nodes().size());
        assertEquals(1L, FamilyTreeLayout.layout(model.snapshot()).cards().stream()
                .filter(card -> card.uuid().equals(CHILD)).count());
        assertEquals(ROOT, model.nodes().get(CHILD).father());
        assertEquals(OTHER, model.nodes().get(CHILD).mother());
    }

    @Test
    void navigatingAwayInvalidatesGenerationResponses() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeNode root = node(ROOT);
        root.addChild(CHILD);
        long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(initial, ROOT, view(Map.of(ROOT, root),
                Set.of(new FamilyTreeView.Continuation(ROOT, DESCENDANTS)), Set.of())));

        FamilyTreeViewModel.GenerationRequest expansion = model.beginGeneration(DESCENDANTS,
                Set.of(ROOT)).orElseThrow();
        model.beginRootFocus(OTHER, viewport(10, 20, 1));

        assertFalse(model.acceptGeneration(new GetFamilyTreeResponse(expansion.requestId(), ROOT,
                true, view(Map.of(CHILD, node(CHILD)), Set.of(), Set.of()), Optional.empty())).handled());
        assertFalse(model.nodes().containsKey(CHILD));
    }

    @Test
    void expandingSiblingDescendantsKeepsFocusAndPlacesNieceBelowSibling() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode father = node(PARENT);
        FamilyTreeNode sibling = node(OTHER);
        FamilyTreeNode niece = node(CHILD);
        root.setFather(father);
        sibling.setFather(father);
        niece.setFather(sibling);
        FamilyTreeView.Continuation omitted = new FamilyTreeView.Continuation(OTHER, DESCENDANTS);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, viewport(20, 30, 1.25f));
        model.accept(response(initial, ROOT, view(Map.of(ROOT, root, PARENT, father, OTHER, sibling),
                Set.of(omitted), Set.of())));

        assertTrue(model.snapshot().continuations().contains(omitted));
        assertEquals(ROOT, model.focusId());
        assertTrue(FamilyTreeLayout.layout(model.snapshot()).cards().stream()
                .noneMatch(card -> card.uuid().equals(CHILD)));

        FamilyTreeViewModel.GenerationRequest expansion = model.beginGeneration(DESCENDANTS,
                Set.of(OTHER)).orElseThrow();
        assertTrue(model.acceptGeneration(new GetFamilyTreeResponse(expansion.requestId(), OTHER, true,
                view(Map.of(OTHER, sibling, CHILD, niece), Set.of(), Set.of()), Optional.empty())).layoutChanged());

        assertEquals(ROOT, model.focusId());
        assertEquals(ROOT, model.layoutRootId());
        assertFalse(model.snapshot().continuations().contains(omitted));
        FamilyTreeLayout.Result layout = FamilyTreeLayout.layout(model.snapshot());
        FamilyTreeLayout.Card siblingCard = layout.cards().stream()
                .filter(card -> card.uuid().equals(OTHER)).findFirst().orElseThrow();
        FamilyTreeLayout.Card nieceCard = layout.cards().stream()
                .filter(card -> card.uuid().equals(CHILD)).findFirst().orElseThrow();
        assertTrue(nieceCard.bounds().centerY() > siblingCard.bounds().centerY());
        assertTrue(layout.edges().stream().anyMatch(edge -> edge.type() == FamilyTreeLayout.EdgeType.PARENT_CHILD
                && edge.from().equals(OTHER) && edge.to().equals(CHILD)));
    }

    @Test
    void expansionPrunesReverseContinuationWhenReferencedRelativeIsAlreadyLoaded() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode parent = node(PARENT);
        root.setFather(parent);
        parent.addChild(ROOT);
        FamilyTreeView.Continuation ancestorContinuation = new FamilyTreeView.Continuation(ROOT, ANCESTORS);
        FamilyTreeView.Continuation reverseContinuation = new FamilyTreeView.Continuation(PARENT, DESCENDANTS);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(initial, ROOT, view(Map.of(ROOT, root), Set.of(ancestorContinuation), Set.of())));

        FamilyTreeViewModel.GenerationRequest expansion = model.beginGeneration(ANCESTORS,
                Set.of(ROOT)).orElseThrow();
        assertTrue(model.acceptGeneration(new GetFamilyTreeResponse(expansion.requestId(), ROOT, true,
                view(Map.of(ROOT, root, PARENT, parent), Set.of(reverseContinuation), Set.of()),
                Optional.empty())).layoutChanged());

        assertFalse(model.snapshot().continuations().contains(ancestorContinuation));
        assertFalse(model.snapshot().continuations().contains(reverseContinuation));
    }

    @Test
    void partnersAncestorControlLoadsParentsWithoutChangingSelection() {
        FamilyTreeNode root = node(ROOT);
        FamilyTreeNode child = node(CHILD);
        FamilyTreeNode partner = node(OTHER);
        FamilyTreeNode mother = node(PARENT);
        FamilyTreeNode father = node(GRANDPARENT);
        child.setFather(root);
        child.updatePartner(partner);
        partner.updatePartner(child);
        partner.setMother(mother);
        partner.setFather(father);
        FamilyTreeView.Continuation ancestors = new FamilyTreeView.Continuation(OTHER, ANCESTORS);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(initial, ROOT, view(
                Map.of(ROOT, root, CHILD, child, OTHER, partner), Set.of(ancestors), Set.of()
        )));

        assertTrue(model.hasGenerationFrontier(ANCESTORS, Set.of(ROOT, CHILD, OTHER)));

        FamilyTreeViewModel.GenerationRequest expansion = model.beginGeneration(ANCESTORS,
                Set.of(OTHER)).orElseThrow();
        assertTrue(model.acceptGeneration(new GetFamilyTreeResponse(expansion.requestId(), OTHER, true,
                view(Map.of(OTHER, partner, PARENT, mother, GRANDPARENT, father), Set.of(), Set.of()),
                Optional.empty())).layoutChanged());

        assertEquals(ROOT, model.focusId());
        assertEquals(ROOT, model.layoutRootId());
        assertTrue(model.nodes().keySet().containsAll(Set.of(ROOT, CHILD, OTHER, PARENT, GRANDPARENT)));
        assertFalse(model.snapshot().continuations().contains(ancestors));
        assertTrue(FamilyTreeLayout.layout(model.snapshot()).cards().stream().anyMatch(card -> card.uuid().equals(PARENT)));
        assertTrue(FamilyTreeLayout.layout(model.snapshot()).cards().stream().anyMatch(card -> card.uuid().equals(GRANDPARENT)));
    }

    @Test
    void unavailableUuidPrunesContinuationWithoutCreatingSyntheticPerson() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeNode root = node(ROOT);
        root.addChild(MISSING);
        FamilyTreeView.Continuation continuation = new FamilyTreeView.Continuation(ROOT, DESCENDANTS);
        long request = model.beginFocus(ROOT, viewport(0, 0, 1));

        model.accept(response(
                request,
                ROOT,
                view(Map.of(ROOT, root), Set.of(continuation), Set.of(MISSING))
        ));

        assertFalse(model.nodes().containsKey(MISSING));
        assertFalse(model.snapshot().continuations().contains(continuation));
    }

    @Test
    void appliedResponseAddsExactGrave() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        GlobalPos grave = GlobalPos.of(Level.NETHER, new BlockPos(12, 64, -31));
        long request = model.beginFocus(ROOT, viewport(0, 0, 1));

        model.accept(response(
                request,
                ROOT,
                view(Map.of(ROOT, node(ROOT)), Set.of(), Set.of(), Map.of(ROOT, grave))
        ));

        assertEquals(Map.of(ROOT, grave), model.graves());
    }

    @Test
    void newerAppliedResponseWithoutGraveRemovesCachedGraveForReturnedNode() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        GlobalPos grave = GlobalPos.of(Level.OVERWORLD, new BlockPos(3, 70, 8));
        long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(
                initial,
                ROOT,
                view(Map.of(ROOT, node(ROOT)), Set.of(), Set.of(), Map.of(ROOT, grave))
        ));

        long refresh = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(refresh, ROOT, view(Map.of(ROOT, node(ROOT)), Set.of(), Set.of())));

        assertTrue(model.graves().isEmpty());
    }

    @Test
    void staleResponseDoesNotMutateGraveCache() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        GlobalPos rootGrave = GlobalPos.of(Level.OVERWORLD, new BlockPos(3, 70, 8));
        GlobalPos childGrave = GlobalPos.of(Level.END, new BlockPos(30, 80, 9));
        long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(
                initial,
                ROOT,
                view(Map.of(ROOT, node(ROOT)), Set.of(), Set.of(), Map.of(ROOT, rootGrave))
        ));
        long stale = model.beginFocus(CHILD, viewport(0, 0, 1));
        model.beginFocus(OTHER, viewport(0, 0, 1));

        assertEquals(FamilyTreeViewModel.MergeResult.STALE, model.accept(response(
                stale,
                CHILD,
                view(Map.of(CHILD, node(CHILD)), Set.of(), Set.of(), Map.of(CHILD, childGrave))
        )));

        assertEquals(Map.of(ROOT, rootGrave), model.graves());
    }

    @Test
    void orphanMetadataSurvivesUnrelatedPartialMergeAndRefreshesReturnedNodes() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(
                initial,
                ROOT,
                view(Map.of(ROOT, node(ROOT)), Set.of(), Set.of(), Set.of(ROOT), Map.of())
        ));

        long unrelated = model.beginFocus(OTHER, viewport(0, 0, 1));
        model.accept(response(
                unrelated,
                OTHER,
                view(Map.of(OTHER, node(OTHER)), Set.of(), Set.of(), Set.of(), Map.of())
        ));

        assertEquals(Set.of(ROOT), model.orphans());

        long refresh = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(
                refresh,
                ROOT,
                view(Map.of(ROOT, node(ROOT)), Set.of(), Set.of(), Set.of(), Map.of())
        ));

        assertTrue(model.orphans().isEmpty());
    }

    private static GetFamilyTreeResponse response(long requestId, UUID uuid, FamilyTreeView view) {
        return new GetFamilyTreeResponse(requestId, uuid, true, view);
    }

    private static FamilyTreeView view(
            Map<UUID, FamilyTreeNode> nodes,
            Set<FamilyTreeView.Continuation> continuations,
            Set<UUID> unavailable
    ) {
        return new FamilyTreeView(nodes, continuations, unavailable, Set.of(), Map.of());
    }

    private static FamilyTreeView view(
            Map<UUID, FamilyTreeNode> nodes,
            Set<FamilyTreeView.Continuation> continuations,
            Set<UUID> unavailable,
            Map<UUID, GlobalPos> graves
    ) {
        return new FamilyTreeView(nodes, continuations, unavailable, Set.of(), graves);
    }

    private static FamilyTreeView view(
            Map<UUID, FamilyTreeNode> nodes,
            Set<FamilyTreeView.Continuation> continuations,
            Set<UUID> unavailable,
            Set<UUID> orphans,
            Map<UUID, GlobalPos> graves
    ) {
        return new FamilyTreeView(nodes, continuations, unavailable, orphans, graves);
    }

    private static FamilyTreeNode node(UUID id) {
        return new FamilyTreeNode(null, id, id.toString(), false, Gender.MALE, Util.NIL_UUID, Util.NIL_UUID);
    }

    private static FamilyTreeViewModel.ViewportState viewport(double x, double y, float zoom) {
        return new FamilyTreeViewModel.ViewportState(x, y, zoom);
    }

}
