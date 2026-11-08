package net.conczin.mca.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DialogueMenuScrollWidgetTest {
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
