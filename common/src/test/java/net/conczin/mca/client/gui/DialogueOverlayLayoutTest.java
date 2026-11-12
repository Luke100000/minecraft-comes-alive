package net.conczin.mca.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DialogueOverlayLayoutTest {
    @Test
    void twoLinesHaveRoomBetweenSpeakerHeaderAndBottomInsetAtEveryContentScale() {
        assertEquals(63, DialogueOverlayLayout.minimumSpeechHeight(2, 0, false, 1.0f));
        assertEquals(63, DialogueOverlayLayout.minimumSpeechHeight(1, 0, false, 1.0f));
        assertEquals(95, DialogueOverlayLayout.minimumSpeechHeight(2, 0, false, 1.5f));
        assertEquals(126, DialogueOverlayLayout.minimumSpeechHeight(2, 0, false, 2.0f));
    }

    @Test
    void historyAndProgressReserveSpaceOutsideTheSpeechLines() {
        assertEquals(87, DialogueOverlayLayout.minimumSpeechHeight(2, 24, false, 1.0f));
        assertEquals(94, DialogueOverlayLayout.minimumSpeechHeight(2, 24, true, 1.0f));
    }

    @Test
    void speechSpaceLimitsTallMenusButNeverMovesFooterIntoHud() {
        int desiredSpeechHeight = DialogueOverlayLayout.minimumSpeechHeight(2, 0, false, 1.0f);
        int menuLimit = DialogueOverlayLayout.maximumMenuHeightForSpeech(360, desiredSpeechHeight);
        assertEquals(213, menuLimit);
        DialogueOverlayLayout.Geometry upper = DialogueOverlayLayout.calculate(640, 360, menuLimit,
                desiredSpeechHeight);
        assertEquals(desiredSpeechHeight, upper.bubble().height());
        assertTrue(upper.bubble().bottom() + 8 <= upper.menu().y());
        assertEquals(360 - 44, upper.menu().bottom());

        int tinyLimit = DialogueOverlayLayout.maximumMenuHeightForSpeech(150, desiredSpeechHeight);
        assertTrue(tinyLimit < 52, "tiny screens should fall back to speech overflow instead of hiding menu actions");
        DialogueOverlayLayout.Geometry tiny = DialogueOverlayLayout.calculate(320, 150, 52,
                desiredSpeechHeight);
        assertTrue(tiny.menu().bottom() <= 150 - 44);
        assertTrue(tiny.bubble().bottom() + 8 <= tiny.menu().y());
    }

    @Test
    void longSpeechStillReservesTwoLinesBeforeGivingHeightToChoices() {
        int fourLines = DialogueOverlayLayout.minimumSpeechHeight(4, 0, false, 1.0f);
        int twoLines = DialogueOverlayLayout.minimumSpeechHeight(2, 0, false, 1.0f);
        int menuHeight = DialogueOverlayLayout.menuHeightWithSpeechMinimum(200, 120, 52, twoLines);
        DialogueOverlayLayout.Geometry layout = DialogueOverlayLayout.calculate(320, 200, menuHeight, fourLines);

        assertEquals(67, menuHeight, "the 4-line request must not discard a feasible 2-line reservation");
        assertTrue(layout.bubble().height() >= 63);
        assertTrue(layout.menu().height() >= 52);
        assertTrue(layout.bubble().bottom() + 8 <= layout.menu().y());
    }

    @Test
    void tinyViewportKeepsStandardSpeechInsetWithoutMovingControls() {
        int menuHeight = DialogueOverlayLayout.menuHeightWithSpeechMinimum(150, 120, 52, 63);
        DialogueOverlayLayout.Geometry layout = DialogueOverlayLayout.calculate(320, 150, menuHeight, 63);
        DialogueOverlayLayout.SpeechViewport text = DialogueOverlayLayout.speechViewport(layout.bubble(),
                0, false, 1.0f);

        assertEquals(52, menuHeight, "an impossible 2-line reservation must not give extra space to choices");
        assertEquals(layout.bubble().y() + 31, text.top(), "the speech inset must not change into a compact layout");
        assertTrue(text.bottom() <= text.top(), "unplayably small sizes may have no speech viewport");
        assertTrue(layout.menu().bottom() <= 150 - 44);
    }

    @Test
    void casualMessageLoadingRowFitsInsideTheAvailablePromptViewport() {
        DialogueOverlayLayout.Rect tinyMenu = DialogueOverlayLayout.calculate(320, 150, 68, 0).menu();
        // The 40%-of-screen menu cap leaves 18 pixels after the 32-pixel footer and 10-pixel insets.
        assertEquals(60, tinyMenu.height());
        assertEquals(18, DialogueOverlayLayout.loadingRowHeight(tinyMenu, 32, 26));

        DialogueOverlayLayout.Rect normalMenu = DialogueOverlayLayout.calculate(640, 360, 68, 0).menu();
        assertEquals(26, DialogueOverlayLayout.loadingRowHeight(normalMenu, 32, 26),
                "normal GUI sizes must keep the full three-dot loading row");
    }

    @Test
    void regularSpeechViewportKeepsHistoryBelowTheText() {
        DialogueOverlayLayout.Rect bubble = new DialogueOverlayLayout.Rect(180, 12, 160, 120);
        for (int historyHeight : new int[]{24, 39}) {
            DialogueOverlayLayout.SpeechViewport text = DialogueOverlayLayout.speechViewport(
                    bubble, historyHeight, false, 1.0f);
            assertEquals(bubble.y() + 31, text.top());
            assertEquals(bubble.bottom() - 12 - historyHeight, text.bottom());
            assertTrue(text.bottom() > text.top());
        }
    }

    @Test
    void progressDisappearsWhenTooLittleRoomBelowNormalSpeech() {
        DialogueOverlayLayout.Rect bubble = new DialogueOverlayLayout.Rect(180, 12, 128, 96);
        DialogueOverlayLayout.SpeechViewport speech = DialogueOverlayLayout.speechViewport(
                bubble, 39, true, 1.0f);
        DialogueOverlayLayout.Rect progress = DialogueOverlayLayout.progressStrip(bubble, 39, 1.0f);

        assertEquals(bubble.y() + 31, speech.top());
        assertEquals(0, progress.height(), "do not relocate progress into the speaker header");
    }

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
    void dockedPanelsShiftTogetherToClearTheInspectorAtLargeGuiScale() {
        DialogueOverlayLayout.Column column = DialogueOverlayLayout.menuColumn(427, 240, 133);
        DialogueOverlayLayout.Geometry docked = DialogueOverlayLayout.speechAboveChoices(
                DialogueOverlayLayout.calculate(427, 240, 96, 63, column.width(), column));

        assertTrue(docked.bubble().x() > 133, "the inspector must not cover the parchment");
        assertEquals(docked.menu().x(), docked.bubble().x());
        assertEquals(docked.menu().width(), docked.bubble().width());
        assertEquals(DialogueOverlayLayout.menuColumn(427, 240, 0).width(), column.width(),
                "shift before reducing the text width");
        assertInside(docked.menu(), 427, 240);
    }

    @Test
    void dockedPanelsKeepSpaceForTextWhenTheInspectorIsWide() {
        int inspectorRight = DialogueOverlayLayout.inspectorRightLimit(320, 180);
        DialogueOverlayLayout.Column centered = DialogueOverlayLayout.menuColumn(320, 180, 0);
        DialogueOverlayLayout.Column shortLabels = DialogueOverlayLayout.menuColumn(320, 180, 20);
        DialogueOverlayLayout.Column wideLabels = DialogueOverlayLayout.menuColumn(320, 180, inspectorRight);

        assertEquals(centered, shortLabels);
        assertTrue(wideLabels.x() > inspectorRight);
        assertTrue(wideLabels.width() < centered.width());
        assertTrue(wideLabels.width() >= 180);
        assertTrue(wideLabels.x() + wideLabels.width() < 320);
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
    void pendingStateKeepsFooterAndHudClearAt320By150() {
        DialogueOverlayLayout.Geometry normal = DialogueOverlayLayout.calculate(320, 150, 100, 80);
        for (DialogueOverlayLayout.Geometry geometry : new DialogueOverlayLayout.Geometry[]{
                normal, DialogueOverlayLayout.speechAboveChoices(normal)}) {
            DialogueOverlayLayout.Rect strip = DialogueOverlayLayout.progressStrip(geometry.bubble(), 0, 1.0f);
            DialogueOverlayLayout.FooterLayout footer = DialogueOverlayLayout.footer(geometry.menu(), 27);
            assertInside(geometry.bubble(), 320, 150);
            assertInside(strip, 320, 150);
            assertTrue(strip.height() == 0 || strip.bottom() <= geometry.bubble().bottom());
            assertTrue(geometry.bubble().bottom() + 8 <= geometry.menu().y());
            assertTrue(footer.close().bottom() <= 150 - 44 - 3,
                    "the dialogue footer must stay above the hearts and hunger region");
        }
    }

    @Test
    void parchmentLoadingStripStaysInsideBothDockingModesAndAboveHistory() {
        DialogueOverlayLayout.Geometry normal = DialogueOverlayLayout.calculate(640, 360, 120, 105);
        for (DialogueOverlayLayout.Geometry geometry : new DialogueOverlayLayout.Geometry[]{
                normal, DialogueOverlayLayout.speechAboveChoices(normal)}) {
            DialogueOverlayLayout.Rect strip = DialogueOverlayLayout.progressStrip(geometry.bubble(), 24, 1.0f);
            assertTrue(strip.width() > 0 && strip.height() > 0);
            assertTrue(strip.x() > geometry.bubble().x());
            assertTrue(strip.right() < geometry.bubble().right());
            assertTrue(strip.y() > geometry.bubble().y() + 32);
            assertTrue(strip.bottom() <= geometry.bubble().bottom() - 24);
            assertTrue(strip.bottom() < geometry.menu().y());
        }
    }

    @Test
    void indeterminateProgressMovesAndCanBecomeStatic() {
        DialogueOverlayLayout.Rect strip = new DialogueOverlayLayout.Rect(10, 50, 90, 3);
        assertNotEquals(DialogueOverlayLayout.progressSegmentStart(strip, 0L, false),
                DialogueOverlayLayout.progressSegmentStart(strip, 500L, false));
        assertEquals(DialogueOverlayLayout.progressSegmentStart(strip, 0L, true),
                DialogueOverlayLayout.progressSegmentStart(strip, 500L, true));
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
    void footerActionsAndDividerLeaveEntireBottomBorderIntact() {
        DialogueOverlayLayout.Rect menu = new DialogueOverlayLayout.Rect(100, 150, 300, 140);
        DialogueOverlayLayout.FooterLayout footer = DialogueOverlayLayout.footer(menu, 27);
        for (DialogueOverlayLayout.Rect element : new DialogueOverlayLayout.Rect[]{
                footer.divider(), footer.back(), footer.close()}) {
            assertTrue(element.x() >= menu.x() + 4);
            assertTrue(element.right() <= menu.right() - 4);
            assertTrue(element.y() > menu.y() + 7);
            assertTrue(element.bottom() <= menu.bottom() - 3);
        }
        assertEquals(footer.back().right(), footer.close().x());
        assertEquals(footer.divider().bottom(), footer.back().y());
        assertEquals(menu.bottom() - 3, footer.back().bottom());
        assertEquals(menu.bottom() - 3, footer.close().bottom());
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
    void normalScaleKeepsChoicesCompactAndJustAboveHeartsAndHunger() {
        DialogueOverlayLayout.Rect menu = DialogueOverlayLayout.calculate(530, 300, 115, 85).menu();
        assertTrue(menu.width() >= 300 && menu.width() <= 350,
                "The conversation menu should occupy about 60% of the available width");
        assertEquals(300 - 39 - 5, menu.bottom(),
                "Menu must sit just above hearts and hunger without reserving armour space");
        assertTrue(menu.height() <= 120);
    }

    @Test
    void upperRightTailDoesNotVanishWhenExpandedMenuGetsCloser() {
        assertTrue(DialogueOverlayLayout.calculate(640, 360, 140, 75).canShowTail());
        assertTrue(DialogueOverlayLayout.calculate(640, 360, 144, 200).canShowTail(),
                "tail ends at the bubble bottom and needs no extra 24-pixel clearance");
        assertFalse(DialogueOverlayLayout.calculate(640, 360, 100, 10).canShowTail());
    }

    @Test
    void speakerHeaderAvoidsPositionToggleAtDifferentScales() {
        for (DialogueOverlayLayout.Rect bubble : new DialogueOverlayLayout.Rect[]{
                new DialogueOverlayLayout.Rect(180, 15, 128, 75),
                new DialogueOverlayLayout.Rect(400, 35, 300, 120)}) {
            for (float scale : new float[]{1.0f, 2.0f}) {
                DialogueOverlayLayout.Rect header = DialogueOverlayLayout.speakerHeader(bubble, scale);
                assertTrue(header.x() >= bubble.x());
                assertTrue(header.y() >= bubble.y());
                assertTrue(header.right() <= bubble.right() - Math.round(27 * scale),
                        "Name label must end before the position toggle");
                assertTrue(header.bottom() <= bubble.y() + Math.round(23 * scale),
                        "Nameplate must stay above the speech divider");
            }
        }
    }

    @Test
    void pointerJoinsStraightLeftBorderOnlyInUpperRightMode() {
        DialogueOverlayLayout.Geometry upper = DialogueOverlayLayout.calculate(640, 360, 140, 75);
        DialogueOverlayLayout.Rect bubble = upper.bubble();
        DialogueOverlayLayout.Rect tail = DialogueOverlayLayout.tailBounds(bubble);
        assertTrue(upper.canShowTail());
        assertEquals(bubble.x() + 6, tail.right());
        assertTrue(tail.y() >= bubble.y() + 7);
        assertTrue(tail.bottom() <= bubble.bottom() - 7);
        assertFalse(DialogueOverlayLayout.speechAboveChoices(upper).canShowTail());
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
