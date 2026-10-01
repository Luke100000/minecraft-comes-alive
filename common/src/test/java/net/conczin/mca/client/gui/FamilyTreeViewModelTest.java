package net.conczin.mca.client.gui;

import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.FamilyTreeView;
import net.conczin.mca.network.s2c.GetFamilyTreeResponse;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.conczin.mca.network.FamilyTreeView.Direction.ANCESTORS;
import static net.conczin.mca.network.FamilyTreeView.Direction.DESCENDANTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
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
        assertTrue(model.nodes().containsKey(CHILD));
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
        assertTrue(model.unavailable().contains(MISSING));
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
        assertFalse(model.continuations().contains(continuation));
    }

    @Test
    void unavailableUuidNeverCreatesSyntheticPerson() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long request = model.beginFocus(ROOT, viewport(0, 0, 1));

        model.accept(response(
                request,
                ROOT,
                view(Map.of(ROOT, node(ROOT)), Set.of(), Set.of(MISSING))
        ));

        assertTrue(model.unavailable().contains(MISSING));
        assertFalse(model.nodes().containsKey(MISSING));
    }

    private static GetFamilyTreeResponse response(long requestId, UUID uuid, FamilyTreeView view) {
        return new GetFamilyTreeResponse(requestId, uuid, true, view);
    }

    private static FamilyTreeView view(
            Map<UUID, FamilyTreeNode> nodes,
            Set<FamilyTreeView.Continuation> continuations,
            Set<UUID> unavailable
    ) {
        return new FamilyTreeView(nodes, continuations, unavailable);
    }

    private static FamilyTreeNode node(UUID id) {
        return new FamilyTreeNode(null, id, id.toString(), false, Gender.MALE, Util.NIL_UUID, Util.NIL_UUID);
    }

    private static FamilyTreeViewModel.ViewportState viewport(double x, double y, float zoom) {
        return new FamilyTreeViewModel.ViewportState(x, y, zoom);
    }

    private static UUID uuid(int value) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", value));
    }
}
