package net.conczin.mca.client.gui;

import org.junit.jupiter.api.Test;

import static net.conczin.mca.client.gui.DialogueRowLayout.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class DialogueRowLayoutTest {
    @Test
    void nestedLabelsHaveTheirOwnGridAndDoNotOverlapDisclosureIcons() {
        for (int width : new int[]{250, 350}) {
            for (float scale : new float[]{1.0f, 2.0f}) {
                DialogueRowLayout.Insets category = DialogueRowLayout.insets(width, scale, CATEGORY);
                DialogueRowLayout.Insets featured = DialogueRowLayout.insets(width, scale, FEATURED);
                DialogueRowLayout.Insets child = DialogueRowLayout.insets(width, scale, CHILD);
                DialogueRowLayout.Insets ordinary = DialogueRowLayout.insets(width, scale, ORDINARY);

                assertEquals(Math.round(16 * scale), category.iconX());
                assertEquals(Math.round(36 * scale), category.textX());
                assertEquals(category.textX(), featured.textX());
                assertEquals(Math.round(44 * scale), child.textX());
                assertEquals(Math.round(16 * scale), ordinary.textX());
                assertTrue(child.textX() > category.textX());
                assertTrue(category.textX() + category.textWidth() <= category.trailingX() - Math.round(8 * scale));
                assertEquals(width - Math.round(16 * scale), child.textX() + child.textWidth());
                assertEquals(width - Math.round(16 * scale), ordinary.textX() + ordinary.textWidth());
            }
        }
    }

    @Test
    void rowHeightsUseTextLinesAndDeliberateMinimumTargets() {
        assertEquals(22, DialogueRowLayout.height(1.0f, 1, ORDINARY));
        assertEquals(22, DialogueRowLayout.height(1.0f, 1, CHILD));
        assertEquals(24, DialogueRowLayout.height(1.0f, 1, CATEGORY));
        assertEquals(28, DialogueRowLayout.height(1.0f, 1, FEATURED));
        assertEquals(30, DialogueRowLayout.height(1.0f, 2, CHILD));
        assertEquals(40, DialogueRowLayout.height(1.0f, 3, CHILD));
        assertEquals(33, DialogueRowLayout.height(1.5f, 1, ORDINARY));
        assertEquals(45, DialogueRowLayout.height(1.5f, 2, CHILD));
        for (DialogueRowLayout.Kind kind : DialogueRowLayout.Kind.values()) {
            assertTrue(DialogueRowLayout.height(2.0f, 1, kind) >= 24,
                    "Every row must contain the 12-pixel optical icon slot at scale 2");
            assertTrue(DialogueRowLayout.height(1.5f, 3, kind) > DialogueRowLayout.height(1.5f, 2, kind));
        }
    }

    @Test
    void rowSurfaceHasEqualTopAndBottomMarginsAtEveryRowHeight() {
        for (int height : new int[]{22, 24, 28, 40}) {
            DialogueOverlayLayout.Rect surface = DialogueRowLayout.surface(100, 300, 200, height);
            assertEquals(108, surface.x());
            assertEquals(201, surface.y());
            assertEquals(284, surface.width());
            assertEquals(height - 2, surface.height());
            assertEquals(1, surface.y() - 200);
            assertEquals(1, 200 + height - surface.bottom());
        }
    }

    @Test
    void loadingStripLeavesBothEndsInsideThePromptClipAtAllGuiScales() {
        for (float scale : new float[]{1.0f, 1.5f, 2.0f}) {
            int left = 100;
            int panelWidth = 300;
            DialogueOverlayLayout.Rect surface = DialogueRowLayout.loadingSurface(
                    left, panelWidth, 200, Math.round(26 * scale), scale);

            assertEquals(left + Math.round(16 * scale), surface.x());
            assertEquals(left + panelWidth - Math.round(16 * scale), surface.right());
            assertTrue(surface.x() > left + 8, "the left stroke must not be scissored at the clip edge");
            assertTrue(surface.right() < left + panelWidth - 8,
                    "the right stroke must not be scissored at the clip edge");
            assertEquals(201, surface.y());
            assertEquals(Math.round(26 * scale) - 2, surface.height());
        }
    }

    @Test
    void featuredHoverTintReachesTheLowerGoldStroke() {
        DialogueOverlayLayout.Rect frame = DialogueRowLayout.surface(100, 300, 200, 28);
        DialogueOverlayLayout.Rect tint = DialogueRowLayout.featuredHoverFill(frame);

        assertEquals(112, tint.x());
        assertEquals(205, tint.y());
        assertEquals(276, tint.width());
        assertEquals(224, tint.bottom(), "Tint must cover the dark row directly above the lower gold stroke");
        assertEquals(3, frame.bottom() - tint.bottom(), "Keep the three-pixel outer border untouched");
    }
}
