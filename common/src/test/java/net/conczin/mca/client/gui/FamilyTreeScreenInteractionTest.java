package net.conczin.mca.client.gui;

import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.FamilyTreeSearchEntry;
import net.conczin.mca.network.FamilyTreeView;
import net.conczin.mca.network.s2c.GetFamilyTreeResponse;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static net.conczin.mca.network.FamilyTreeView.Direction.ANCESTORS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FamilyTreeScreenInteractionTest {
    private static final UUID ROOT = uuid(1);
    private static final UUID OTHER = uuid(2);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void zoomAroundCursorKeepsWorldPointUnderCursor() {
        FamilyTreeViewModel.ViewportState before = new FamilyTreeViewModel.ViewportState(20, -10, 1.0F);
        double cursorX = 500;
        double cursorY = 360;
        double centerX = 400;
        double centerY = 300;
        double worldX = (cursorX - centerX - before.panX()) / before.zoom();
        double worldY = (cursorY - centerY - before.panY()) / before.zoom();

        FamilyTreeViewModel.ViewportState after =
                FamilyTreeScreen.zoomAround(before, cursorX, cursorY, centerX, centerY, 1.5F);

        assertEquals(cursorX, centerX + after.panX() + worldX * after.zoom(), 0.0001);
        assertEquals(cursorY, centerY + after.panY() + worldY * after.zoom(), 0.0001);
    }

    @Test
    void zoomClampsToQuarterAndDoubleScale() {
        FamilyTreeViewModel.ViewportState state = new FamilyTreeViewModel.ViewportState(0, 0, 1.0F);

        assertEquals(0.25F, FamilyTreeScreen.zoomAround(state, 0, 0, 0, 0, 0.01F).zoom());
        assertEquals(2.0F, FamilyTreeScreen.zoomAround(state, 0, 0, 0, 0, 5.0F).zoom());
    }

    @Test
    void graphTextCancelsViewportScaleSoGlyphsStayPixelSharp() {
        assertEquals(2.0F, FamilyTreeScreen.inverseTextScale(0.5F), 0.0001F);
        assertEquals(1.0F, FamilyTreeScreen.inverseTextScale(1.0F), 0.0001F);
        assertEquals(0.5F, FamilyTreeScreen.inverseTextScale(2.0F), 0.0001F);
    }

    @Test
    void headerKeepsNavigationBalancedAndCentersTheControlRow() {
        FamilyTreeScreen.HeaderLayout header = FamilyTreeScreen.headerLayout(854);

        assertEquals(5, header.backX());
        assertEquals(854 - 5 - 72, header.doneX());
        assertEquals(
                854 - header.controlsRight(),
                header.controlsLeft()
        );
        assertTrue(header.searchWidth() <= 180);
    }

    @Test
    void headerShrinksSearchInsteadOfPushingControlsOffSmallScreens() {
        FamilyTreeScreen.HeaderLayout header = FamilyTreeScreen.headerLayout(320);

        assertTrue(header.searchWidth() >= 80);
        assertTrue(header.controlsLeft() >= 5);
        assertTrue(header.controlsRight() <= 315);
    }

    @Test
    void fitViewKeepsContentInsideCanvasWithPadding() {
        FamilyTreeLayout.Result result = result(new FamilyTreeLayout.Bounds(-200, 200, -100, 100));

        FamilyTreeViewModel.ViewportState state = FamilyTreeScreen.fitView(result, 300, 200, 20);

        FamilyTreeLayout.Bounds bounds = result.contentBounds();
        double left = 150 + state.panX() + bounds.left() * state.zoom();
        double right = 150 + state.panX() + bounds.right() * state.zoom();
        double top = 100 + state.panY() + bounds.top() * state.zoom();
        double bottom = 100 + state.panY() + bounds.bottom() * state.zoom();
        assertTrue(left >= 20);
        assertTrue(right <= 280);
        assertTrue(top >= 20);
        assertTrue(bottom <= 180);
    }

    @Test
    void centerViewPreservesZoom() {
        FamilyTreeLayout.Result result = result(new FamilyTreeLayout.Bounds(40, 140, -30, 70));
        FamilyTreeViewModel.ViewportState current = new FamilyTreeViewModel.ViewportState(18, 22, 1.5F);

        FamilyTreeViewModel.ViewportState centered = FamilyTreeScreen.centerView(result, current);

        assertEquals(1.5F, centered.zoom());
        assertEquals(-135.0, centered.panX(), 0.0001);
        assertEquals(-30.0, centered.panY(), 0.0001);
    }

    @Test
    void backRestoresPreviousViewport() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeViewModel.ViewportState previous = new FamilyTreeViewModel.ViewportState(12, -8, 1.25F);
        long requestId = model.beginFocus(OTHER, previous);
        model.accept(new GetFamilyTreeResponse(requestId, OTHER, true, FamilyTreeView.empty()));

        Optional<FamilyTreeViewModel.HistoryEntry> back = model.back();

        assertTrue(back.isPresent());
        assertEquals(ROOT, model.focusId());
        assertEquals(previous, back.orElseThrow().viewport());
    }

    @Test
    void restoredViewportDisplaysItsRestoredZoom() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeViewModel.ViewportState previous = new FamilyTreeViewModel.ViewportState(12, -8, 1.0F);
        long requestId = model.beginFocus(OTHER, previous);
        model.accept(new GetFamilyTreeResponse(requestId, OTHER, true, FamilyTreeView.empty()));

        FamilyTreeViewModel.ViewportState restored = model.back().orElseThrow().viewport();

        assertEquals(Component.literal("100%"), FamilyTreeScreen.zoomLabel(restored));
    }

    @Test
    void layoutHitTestIdentifiesKnownCardByUuid() {
        FamilyTreeLayout.Card card = new FamilyTreeLayout.Card(
                ROOT,
                FamilyTreeLayout.Role.FOCUS,
                new FamilyTreeLayout.Bounds(-55, 55, -20, 20)
        );
        FamilyTreeLayout.Result result = new FamilyTreeLayout.Result(
                List.of(card),
                List.of(),
                List.of(),
                card.bounds()
        );

        assertEquals(
                Optional.of(new FamilyTreeScreen.PersonTarget(ROOT)),
                FamilyTreeScreen.hitTargetAt(result, 0, 0)
        );
    }

    @Test
    void layoutHitTestIdentifiesContinuationByAnchorAndDirection() {
        FamilyTreeLayout.ContinuationControl control = new FamilyTreeLayout.ContinuationControl(
                ROOT,
                ANCESTORS,
                new FamilyTreeLayout.Bounds(-9, 9, -80, -62)
        );
        FamilyTreeLayout.Result result = new FamilyTreeLayout.Result(
                List.of(),
                List.of(),
                List.of(control),
                control.bounds()
        );

        assertEquals(
                Optional.of(new FamilyTreeScreen.ContinuationTarget(ROOT, ANCESTORS)),
                FamilyTreeScreen.hitTargetAt(result, 0, -70)
        );
    }

    @Test
    void selectingIntegratedSearchResultFocusesExactUuidAndPushesHistory() {
        FamilyTreeViewModel model = loadedModel();
        FamilyTreeSearchEntry selected = searchEntry(OTHER, "Same Name");
        FamilyTreeViewModel.ViewportState viewport = new FamilyTreeViewModel.ViewportState(5, 7, 1.25F);

        long requestId = FamilyTreeScreen.beginSearchSelection(model, selected, viewport);
        model.accept(new GetFamilyTreeResponse(requestId, OTHER, true, view(OTHER)));

        assertEquals(OTHER, model.focusId());
        FamilyTreeViewModel.HistoryEntry back = model.back().orElseThrow();
        assertEquals(ROOT, back.focusId());
        assertEquals(viewport, back.viewport());
    }

    @Test
    void duplicateSearchNamesStillSelectByUuid() {
        FamilyTreeViewModel model = loadedModel();
        FamilyTreeSearchEntry first = searchEntry(uuid(2), "Same Name");
        FamilyTreeSearchEntry second = searchEntry(uuid(3), "Same Name");

        FamilyTreeScreen.beginSearchSelection(
                model,
                second,
                new FamilyTreeViewModel.ViewportState(0, 0, 1.0F)
        );

        assertEquals(Optional.of(second.uuid()), model.pendingFocusId());
        assertTrue(model.pendingFocusId().filter(id -> !id.equals(first.uuid())).isPresent());
    }

    @Test
    void emptyIntegratedQueryHidesResultsAndDoesNotSeedPlayerName() {
        assertTrue(FamilyTreeScreen.integratedSearchQuery("  ").isEmpty());
        assertEquals(Optional.of("Alex"), FamilyTreeScreen.integratedSearchQuery(" Alex "));
    }

    @Test
    void emptyResultsExposeNoFamilyRecordsState() {
        FamilyTreeViewModel model = loadedModel();

        assertEquals(
                Optional.of(Component.translatable("gui.family_tree.no_records")),
                FamilyTreeScreen.statusMessage(model, true, List.of())
        );
    }

    @Test
    void unavailableLatestFocusLeavesGraphAndExposesUnavailableState() {
        FamilyTreeViewModel model = loadedModel();
        long requestId = model.beginFocus(OTHER, new FamilyTreeViewModel.ViewportState(0, 0, 1.0F));

        model.accept(new GetFamilyTreeResponse(requestId, OTHER, false, FamilyTreeView.empty()));

        assertEquals(ROOT, model.focusId());
        assertTrue(model.nodes().containsKey(ROOT));
        assertEquals(
                Optional.of(Component.translatable("gui.family_tree.family_record_unavailable")),
                FamilyTreeScreen.statusMessage(model, false, List.of())
        );
    }

    @Test
    void blankNodeDisplayNeverUsesQuestionMarkPlaceholder() {
        FamilyTreeNode node = new FamilyTreeNode(
                null,
                ROOT,
                " ",
                false,
                Gender.MALE,
                Util.NIL_UUID,
                Util.NIL_UUID
        );

        Component display = FamilyTreeScreen.nodeDisplayName(node);

        assertEquals(Component.translatable("gui.family_tree.unnamed_villager"), display);
        assertTrue(!display.getString().contains("???"));
    }

    private static FamilyTreeViewModel loadedModel() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long requestId = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1.0F));
        model.accept(new GetFamilyTreeResponse(requestId, ROOT, true, view(ROOT)));
        return model;
    }

    private static FamilyTreeView view(UUID id) {
        FamilyTreeNode node = new FamilyTreeNode(
                null,
                id,
                "Person",
                false,
                Gender.MALE,
                Util.NIL_UUID,
                Util.NIL_UUID
        );
        return new FamilyTreeView(Map.of(id, node), Set.of(), Set.of());
    }

    private static FamilyTreeSearchEntry searchEntry(UUID id, String name) {
        return new FamilyTreeSearchEntry(id, name, false, "", false, "", false);
    }

    private static FamilyTreeLayout.Result result(FamilyTreeLayout.Bounds bounds) {
        return new FamilyTreeLayout.Result(List.of(), List.of(), List.of(), bounds);
    }

    private static UUID uuid(int value) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", value));
    }
}
