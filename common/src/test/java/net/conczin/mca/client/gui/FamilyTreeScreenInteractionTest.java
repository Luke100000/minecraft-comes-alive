package net.conczin.mca.client.gui;

import net.conczin.mca.network.FamilyTreeView;
import net.conczin.mca.network.s2c.GetFamilyTreeResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static net.conczin.mca.network.FamilyTreeView.Direction.ANCESTORS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FamilyTreeScreenInteractionTest {
    private static final UUID ROOT = uuid(1);
    private static final UUID OTHER = uuid(2);

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

    private static FamilyTreeLayout.Result result(FamilyTreeLayout.Bounds bounds) {
        return new FamilyTreeLayout.Result(List.of(), List.of(), List.of(), bounds);
    }

    private static UUID uuid(int value) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", value));
    }
}
