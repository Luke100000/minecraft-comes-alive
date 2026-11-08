package net.conczin.mca.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DialogueMenuScrollWidgetTest {
    @Test
    void expandedCategoryAnchorsItsHeaderAtTheViewportTop() {
        DialogueMenuScrollWidget scroller = new DialogueMenuScrollWidget();
        scroller.layout(50, 30, 100, 40, 500, 100);
        scroller.scrollToSection(180);
        assertEquals(180, scroller.offset());
        scroller.layout(50, 30, 100, 100, 500, 100);
        assertEquals(180, scroller.offset(), "expansion must not reclamp to a different position");
        scroller.scrollToSection(180);
        assertEquals(180, scroller.offset());
        scroller.scrollToSection(100);
        assertEquals(100, scroller.offset());
    }

    @Test
    void fittingExpandedListNeverFlashesScrollbarDuringHeightTween() {
        DialogueMenuScrollWidget scroller = new DialogueMenuScrollWidget();
        scroller.layout(50, 100, 100, 40, 90, 100);
        assertFalse(scroller.hasScrollbar(), "transient clipping is not settled overflow");
        scroller.mouseScrolled(55, 120, 0, -1);
        assertEquals(0, scroller.offset(), "a fitting list must not acquire a temporary scroll offset");
        scroller.layout(50, 70, 100, 70, 90, 100);
        assertFalse(scroller.hasScrollbar());
        scroller.layout(50, 40, 100, 100, 90, 100);
        assertFalse(scroller.hasScrollbar());
    }

    @Test
    void genuinelyOverflowingListKeepsNativeScrollbarDuringExpansion() {
        DialogueMenuScrollWidget scroller = new DialogueMenuScrollWidget();
        scroller.layout(50, 100, 100, 40, 180, 100);
        assertTrue(scroller.hasScrollbar());
        scroller.layout(50, 40, 100, 100, 180, 100);
        assertTrue(scroller.hasScrollbar());
        scroller.mouseScrolled(55, 65, 0, -1);
        assertEquals(24, scroller.offset());
    }

    @Test
    void fractionalTouchpadDeltasAccumulateWithoutJumpingEntireRows() {
        DialogueMenuScrollWidget scroller = new DialogueMenuScrollWidget();
        scroller.layout(50, 30, 100, 100, 260);
        scroller.mouseScrolled(55, 65, 0, -0.125);
        assertEquals(3, scroller.offset());
        scroller.mouseScrolled(55, 65, 0, -0.125);
        assertEquals(6, scroller.offset());
        scroller.mouseScrolled(55, 65, 0, 0.25);
        assertEquals(0, scroller.offset());
    }

    @Test
    void wheelScrollsOnlyWhenRowsOverflowAndClampsAtEnd() {
        DialogueMenuScrollWidget scroller = new DialogueMenuScrollWidget();
        scroller.layout(50, 30, 100, 100, 260);
        assertTrue(scroller.hasScrollbar());
        assertTrue(scroller.mouseScrolled(55, 65, 0, -1));
        assertEquals(24, scroller.offset());

        for (int i = 0; i < 30; i++) {
            scroller.mouseScrolled(55, 65, 0, -1);
        }
        assertEquals(160, scroller.offset());
        scroller.layout(50, 30, 100, 100, 80);
        assertFalse(scroller.hasScrollbar());
        assertEquals(0, scroller.offset());
        scroller.mouseScrolled(55, 65, 0, -1);
        assertEquals(0, scroller.offset());
    }

    @Test
    void draggingNativeScrollerChangesOffsetAndReleaseStopsDrag() {
        DialogueMenuScrollWidget scroller = new DialogueMenuScrollWidget();
        scroller.layout(50, 30, 100, 100, 300);
        assertEquals(150, scroller.getX() + scroller.getWidth());
        assertTrue(scroller.mouseClicked(155, 45, 0));
        scroller.setFocused(true);
        assertTrue(scroller.mouseDragged(155, 80, 0, 0, 35));
        assertTrue(scroller.offset() > 0);

        assertTrue(scroller.mouseReleased(155, 80, 0));
        int releasedOffset = scroller.offset();
        assertFalse(scroller.mouseDragged(155, 95, 0, 0, 15));
        assertEquals(releasedOffset, scroller.offset());
    }
}
