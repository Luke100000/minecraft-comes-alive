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
import java.util.Set;
import java.util.UUID;

import static net.conczin.mca.network.FamilyTreeView.Direction.ANCESTORS;
import static net.conczin.mca.network.FamilyTreeView.Direction.DESCENDANTS;
import static net.conczin.mca.FamilyTreeTestSupport.uuid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void acceptedResponseMergesWithoutDiscardingPreviouslyLoadedNodes() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initialRequest = model.beginFocus(ROOT, viewport(0, 0, 1));
        assertEquals(FamilyTreeViewModel.MergeResult.APPLIED, model.accept(response(
                initialRequest,
                ROOT,
                view(Map.of(ROOT, node(ROOT), PARENT, node(PARENT)), Set.of(), Set.of())
        )));

        long expansionRequest = model.beginExpansion(PARENT, ANCESTORS);
        assertEquals(FamilyTreeViewModel.MergeResult.APPLIED, model.accept(response(
                expansionRequest,
                PARENT,
                view(Map.of(GRANDPARENT, node(GRANDPARENT)), Set.of(), Set.of())
        )));

        assertEquals(Set.of(ROOT, PARENT, GRANDPARENT), model.nodes().keySet());
        assertEquals(ROOT, model.focusId());
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
    void rootNavigationInvalidatesPendingExpansionFromPreviousLayout() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(initial, ROOT, view(Map.of(ROOT, node(ROOT)), Set.of(), Set.of())));
        long oldExpansion = model.beginExpansion(ROOT, ANCESTORS);
        long rootRequest = model.beginRootFocus(OTHER, viewport(4, 5, 1));
        model.accept(response(rootRequest, OTHER, view(Map.of(OTHER, node(OTHER)), Set.of(), Set.of())));

        FamilyTreeViewModel.MergeResult result = model.accept(response(
                oldExpansion,
                ROOT,
                view(Map.of(GRANDPARENT, node(GRANDPARENT)), Set.of(), Set.of())
        ));

        assertEquals(FamilyTreeViewModel.MergeResult.STALE, result);
        assertFalse(model.nodes().containsKey(GRANDPARENT));
        assertFalse(model.loading());
    }

    @Test
    void backInvalidatesPendingExpansionFromLayoutBeingLeft() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long rootRequest = model.beginRootFocus(OTHER, viewport(2, 3, 1));
        model.accept(response(rootRequest, OTHER, view(Map.of(OTHER, node(OTHER)), Set.of(), Set.of())));
        long oldExpansion = model.beginExpansion(OTHER, DESCENDANTS);

        model.back().orElseThrow();
        FamilyTreeViewModel.MergeResult result = model.accept(response(
                oldExpansion,
                OTHER,
                view(Map.of(CHILD, node(CHILD)), Set.of(), Set.of())
        ));

        assertEquals(FamilyTreeViewModel.MergeResult.STALE, result);
        assertFalse(model.nodes().containsKey(CHILD));
        assertFalse(model.loading());
    }

    @Test
    void failedRootNavigationKeepsPendingExpansionForCurrentLayout() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(initial, ROOT, view(Map.of(ROOT, node(ROOT)), Set.of(), Set.of())));
        long expansion = model.beginExpansion(ROOT, ANCESTORS);
        long missingRoot = model.beginRootFocus(MISSING, viewport(4, 5, 1));

        assertEquals(FamilyTreeViewModel.MergeResult.NOT_FOUND, model.accept(new GetFamilyTreeResponse(
                missingRoot,
                MISSING,
                false,
                FamilyTreeView.empty()
        )));
        assertTrue(model.loading());

        assertEquals(FamilyTreeViewModel.MergeResult.APPLIED, model.accept(response(
                expansion,
                ROOT,
                view(Map.of(PARENT, node(PARENT)), Set.of(), Set.of())
        )));
        assertTrue(model.nodes().containsKey(PARENT));
        assertFalse(model.loading());
    }

    @Test
    void backWithinSameLayoutKeepsPendingExpansion() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long childFocus = model.beginFocus(CHILD, viewport(0, 0, 1));
        model.accept(response(childFocus, CHILD, view(Map.of(CHILD, node(CHILD)), Set.of(), Set.of())));
        long expansion = model.beginExpansion(CHILD, DESCENDANTS);

        model.back().orElseThrow();

        assertTrue(model.loading());
        assertEquals(FamilyTreeViewModel.MergeResult.APPLIED, model.accept(response(
                expansion,
                CHILD,
                view(Map.of(GRANDPARENT, node(GRANDPARENT)), Set.of(), Set.of())
        )));
        assertTrue(model.nodes().containsKey(GRANDPARENT));
        assertFalse(model.loading());
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
    void staleExpansionResponseDoesNotMutateGraphOrConsumePendingExpansion() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(initial, ROOT, view(Map.of(ROOT, node(ROOT)), Set.of(), Set.of())));
        long expansion = model.beginExpansion(ROOT, ANCESTORS);

        FamilyTreeViewModel.MergeResult result = model.accept(response(
                expansion,
                OTHER,
                view(Map.of(OTHER, node(OTHER)), Set.of(), Set.of())
        ));

        assertEquals(FamilyTreeViewModel.MergeResult.STALE, result);
        assertEquals(Set.of(ROOT), model.nodes().keySet());
        assertTrue(model.loading());
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
    void expansionResponseMergesWithoutChangingFocus() {
        FamilyTreeView.Continuation continuation = new FamilyTreeView.Continuation(ROOT, DESCENDANTS);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, viewport(0, 0, 1));
        model.accept(response(initial, ROOT, view(Map.of(ROOT, node(ROOT)), Set.of(continuation), Set.of())));

        long expansion = model.beginExpansion(ROOT, DESCENDANTS);
        FamilyTreeViewModel.MergeResult result = model.accept(response(
                expansion,
                ROOT,
                view(Map.of(CHILD, node(CHILD)), Set.of(), Set.of())
        ));

        assertEquals(FamilyTreeViewModel.MergeResult.APPLIED, result);
        assertEquals(ROOT, model.focusId());
        assertTrue(model.nodes().containsKey(CHILD));
        assertFalse(model.snapshot().continuations().contains(continuation));
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

        long expansion = model.beginExpansion(ROOT, ANCESTORS);
        model.accept(response(
                expansion,
                ROOT,
                view(Map.of(PARENT, parent), Set.of(reverseContinuation), Set.of())
        ));

        assertFalse(model.snapshot().continuations().contains(ancestorContinuation));
        assertFalse(model.snapshot().continuations().contains(reverseContinuation));
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
