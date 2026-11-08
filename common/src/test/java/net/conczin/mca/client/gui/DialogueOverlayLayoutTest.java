package net.conczin.mca.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DialogueOverlayLayoutTest {
    @Test
    void largeScreenSeparatesSpeechAndMenu() {
        assertInsideAndSeparated(1672, 941, 280, 120);
    }

    @Test
    void normalScreenSeparatesSpeechAndMenu() {
        assertInsideAndSeparated(640, 360, 140, 75);
    }

    @Test
    void smallScreenKeepsBothPanelsVisible() {
        assertInsideAndSeparated(320, 240, 160, 65);
    }

    @Test
    void absentSpeechOmitsBubble() {
        DialogueOverlayLayout.Geometry result = DialogueOverlayLayout.calculate(640, 360, 150, 0);
        assertEquals(0, result.bubble().height());
        assertInside(result.menu(), 640, 360);
    }

    private static void assertInsideAndSeparated(int width, int height, int menuHeight, int bubbleHeight) {
        DialogueOverlayLayout.Geometry result = DialogueOverlayLayout.calculate(width, height, menuHeight, bubbleHeight);
        assertInside(result.menu(), width, height);
        assertInside(result.bubble(), width, height);
        assertTrue(result.bubble().height() > 0);
        assertTrue(result.bubble().bottom() + 6 <= result.menu().y(), "Bubble overlaps menu");
        assertTrue(result.bubble().x() > width / 2, "Bubble should be on the right");
    }

    private static void assertInside(DialogueOverlayLayout.Rect box, int width, int height) {
        assertTrue(box.x() >= 0 && box.y() >= 0);
        assertTrue(box.width() >= 0 && box.height() >= 0);
        assertTrue(box.right() <= width && box.bottom() <= height);
    }
}
