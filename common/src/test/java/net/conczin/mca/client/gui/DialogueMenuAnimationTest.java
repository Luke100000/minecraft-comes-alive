package net.conczin.mca.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DialogueMenuAnimationTest {
    @Test
    void movingViewportCannotSnapNativeScrollerDuringDrag() {
        DialogueMenuAnimation animation = new DialogueMenuAnimation();
        assertEquals(90, animation.update(90, 0, false));
        animation.update(180, 10, false);
        int displayed = animation.update(180, 50, false);
        assertTrue(displayed > 90 && displayed < 180);
        animation.hold(55);
        DialogueMenuScrollWidget scroller = new DialogueMenuScrollWidget();
        int viewportY = 300 - displayed;
        scroller.layout(50, viewportY, 100, displayed, 500);
        scroller.mouseScrolled(55, viewportY + 5, 0, -3);
        int offset = scroller.offset();
        scroller.mouseClicked(155, viewportY + 5, 0);
        scroller.setFocused(true);
        int heldHeight = animation.update(40, 60, true);
        assertEquals(displayed, heldHeight);
        scroller.layout(50, 300 - heldHeight, 100, heldHeight, 500);
        scroller.mouseDragged(155, viewportY + 5, 0, 0, 0);
        assertEquals(offset, scroller.offset(), "stationary pointer must not snap to the new viewport top");
        assertEquals(displayed, animation.update(40, 1000, true));
        assertEquals(displayed, animation.update(40, 1001, false));
        assertEquals(40, animation.update(40, 1201, false));
    }

    @Test
    void wheelGestureKeepsRowsStillThenResumesHeightTween() {
        DialogueMenuAnimation animation = new DialogueMenuAnimation();
        animation.update(100, 0, false);
        animation.hold(20);
        assertEquals(100, animation.update(150, 50, false));
        assertEquals(100, animation.update(150, 199, false));
        assertEquals(100, animation.update(150, 201, false));
        assertEquals(150, animation.update(150, 400, false));
    }
}
