package net.conczin.mca.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DialogueOverlayLayoutTest {
    @Test
    void dockedSpeechSitsAboveUnmovedChoicesWithoutPointer() {
        DialogueOverlayLayout.Geometry normal = DialogueOverlayLayout.calculate(640, 360, 120, 110);
        DialogueOverlayLayout.Geometry docked = DialogueOverlayLayout.speechAboveChoices(normal);
        assertEquals(normal.menu(), docked.menu(), "the toggle must move speech, not choices");
        assertEquals(docked.bubble().x(), docked.menu().x());
        assertEquals(docked.bubble().width(), docked.menu().width());
        assertTrue(docked.menu().y() >= docked.bubble().bottom() + 8);
        assertFalse(docked.canShowTail());
        assertTrue(docked.menu().bottom() <= 330);
        assertInside(docked.menu(), 640, 360);
    }

    @Test
    void dockedSpeechStaysInBoundsOnSmallScreen() {
        DialogueOverlayLayout.Geometry normal = DialogueOverlayLayout.calculate(320, 150, 100, 90);
        DialogueOverlayLayout.Geometry docked = DialogueOverlayLayout.speechAboveChoices(normal);
        assertEquals(normal.menu(), docked.menu());
        assertInside(docked.bubble(), 320, 150);
        assertTrue(docked.bubble().bottom() + 8 <= docked.menu().y());
    }

    @Test
    void largeGuiKeepsDialogueReadableWithoutEnlargingSmallScreens() {
        assertEquals(1.0f, DialogueOverlayLayout.contentScale(320, 240));
        assertEquals(1.0f, DialogueOverlayLayout.contentScale(640, 360));
        assertEquals(1.5f, DialogueOverlayLayout.contentScale(1024, 610));
        assertEquals(2.0f, DialogueOverlayLayout.contentScale(1280, 720));
        assertTrue(DialogueOverlayLayout.calculate(1280, 720, 250, 100).menu().width() >= 740,
                "large GUI canvases must not leave a thin menu capped at 560 pixels");
    }

    @Test
    void shortSpeechBubbleFitsTextInsteadOfLeavingBlankSpace() {
        assertEquals(50, DialogueOverlayLayout.paperHeightForLineCount(1));
        assertEquals(80, DialogueOverlayLayout.paperHeightForLineCount(4));
    }

    @Test
    void footerActionsAndDividerStayInsideFrameWithoutTouchingBorder() {
        DialogueOverlayLayout.Rect menu = new DialogueOverlayLayout.Rect(100, 150, 300, 140);
        DialogueOverlayLayout.FooterLayout footer = DialogueOverlayLayout.footer(menu, 27);
        for (DialogueOverlayLayout.Rect element : new DialogueOverlayLayout.Rect[]{
                footer.divider(), footer.back(), footer.close()}) {
            assertTrue(element.x() >= menu.x() + 7);
            assertTrue(element.right() <= menu.right() - 7);
            assertTrue(element.y() > menu.y() + 7);
            assertTrue(element.bottom() <= menu.bottom() - 7);
        }
        assertTrue(footer.back().right() < footer.close().x());
        assertTrue(footer.divider().bottom() < footer.back().y());
    }

    @Test
    void speechWidthCanShrinkWithoutBreakingRightAlignment() {
        DialogueOverlayLayout.Geometry layout = DialogueOverlayLayout.calculate(640, 360, 100, 50, 185);
        assertEquals(185, layout.bubble().width());
        assertEquals(640 - Math.max(12, Math.min(24, 640 / 32)) - 185,
                layout.bubble().x());
    }

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

    @Test
    void normalScaleKeepsChoicesCompactAndOffTheScreenEdges() {
        DialogueOverlayLayout.Rect menu = DialogueOverlayLayout.calculate(530, 300, 115, 85).menu();
        assertTrue(menu.width() >= 300 && menu.width() <= 350,
                "The conversation menu should occupy about 60% of the available width");
        assertTrue(menu.bottom() <= 272 && menu.bottom() >= 260,
                "Menu must clear the hotbar while staying near the bottom");
        assertTrue(menu.height() <= 120);
    }

    @Test
    void upperRightTailDoesNotVanishWhenExpandedMenuGetsCloser() {
        assertTrue(DialogueOverlayLayout.calculate(640, 360, 140, 75).canShowTail());
        assertTrue(DialogueOverlayLayout.calculate(640, 360, 144, 200).canShowTail(),
                "tail ends at the bubble bottom and needs no extra 24-pixel clearance");
        assertFalse(DialogueOverlayLayout.calculate(640, 360, 100, 10).canShowTail());
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
