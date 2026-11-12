package net.conczin.mca.client.gui;

/** Geometry in scaled GUI pixels. The world and entity remain visible behind both panels. */
final class DialogueOverlayLayout {
    private DialogueOverlayLayout() {
    }

    static float contentScale(int width, int height) {
        float available = Math.min(width / 640.0f, height / 360.0f);
        return Math.max(1.0f, Math.min(2.0f, (float) Math.floor(available * 2) / 2));
    }

    static int paperHeightForLineCount(int wrappedLines) {
        return Math.max(50, Math.max(1, wrappedLines) * 10 + 40);
    }

    /** Match the header, line stepping and bottom reservations used by InteractScreen. */
    static int minimumSpeechHeight(int textLineCount, int historyHeight, boolean progress, float scale) {
        int top = Math.round(23 * scale) + Math.round(8 * scale);
        int lineStep = Math.round(10 * scale);
        int bottom = Math.round((progress ? 19 : 12) * scale);
        return top + Math.max(2, textLineCount) * lineStep + bottom + historyHeight;
    }

    /** Maximum menu height that leaves the speech bubble its minimum vertical space. */
    static int maximumMenuHeightForSpeech(int height, int minimumSpeechHeight) {
        int bubbleTop = Math.max(12, Math.min(50, height / 11));
        int bottomMargin = Math.min(height / 3, 44);
        return Math.max(0, height - bottomMargin - bubbleTop - 8 - minimumSpeechHeight);
    }

    /** Extra wrapped lines scroll rather than consuming the two-line reservation for the menu. */
    static int menuHeightWithSpeechMinimum(int height, int targetMenuHeight,
                                           int minimumMenuHeight, int minimumSpeechHeight) {
        int limit = maximumMenuHeightForSpeech(height, minimumSpeechHeight);
        return Math.min(targetMenuHeight, Math.max(minimumMenuHeight, limit));
    }

    /** A single loading placeholder should fit the prompt viewport, never become a scrollable list. */
    static int loadingRowHeight(Rect menu, int footerHeight, int desiredRowHeight) {
        return Math.min(desiredRowHeight, Math.max(0, menu.height() - footerHeight - 10));
    }

    /** The speech viewport always uses the regular header and history layout. */
    static SpeechViewport speechViewport(Rect bubble, int historyHeight, boolean progress, float scale) {
        int top = bubble.y() + Math.round(23 * scale) + Math.round(8 * scale);
        int bottom = bubble.bottom() - Math.round(12 * scale) - historyHeight;
        Rect strip = progress ? progressStrip(bubble, historyHeight, scale) : new Rect(0, 0, 0, 0);
        if (strip.height() > 0) {
            bottom = Math.min(bottom, strip.y() - Math.round(4 * scale));
        }
        return new SpeechViewport(top, bottom);
    }

    static Geometry calculate(int width, int height, int menuHeight, int bubbleHeight) {
        return calculate(width, height, menuHeight, bubbleHeight, Integer.MAX_VALUE);
    }

    static Geometry calculate(int width, int height, int menuHeight, int bubbleHeight, int desiredBubbleWidth) {
        int sideMargin = Math.max(12, Math.min(24, width / 32));
        // Hearts and hunger start 39 GUI pixels above the bottom; leave a five-pixel gap.
        int bottomMargin = Math.min(height / 3, 44);
        int availableWidth = Math.max(1, width - sideMargin * 2);
        int menuWidth = Math.min(availableWidth, Math.max(250,
                Math.min(Math.round(560 * contentScale(width, height)), width * 3 / 5)));
        int maximumMenuHeight = Math.max(1, Math.min(height - bottomMargin - 26, height * 2 / 5));
        int actualMenuHeight = Math.min(Math.max(1, menuHeight), maximumMenuHeight);
        Rect menu = new Rect((width - menuWidth) / 2, height - bottomMargin - actualMenuHeight,
                menuWidth, actualMenuHeight);

        if (bubbleHeight <= 0) {
            return new Geometry(menu, new Rect(0, 0, 0, 0));
        }
        int bubbleTop = Math.max(12, Math.min(50, height / 11));
        int bubbleWidth = Math.max(1, Math.min(Math.min(Math.min(420, width * 2 / 5), width / 2 - 16),
                desiredBubbleWidth));
        int maximumBubbleHeight = Math.max(0, menu.y() - bubbleTop - 8);
        Rect bubble = new Rect(width - sideMargin - bubbleWidth, bubbleTop, bubbleWidth,
                Math.min(bubbleHeight, maximumBubbleHeight));
        return new Geometry(menu, bubble);
    }

    static Geometry speechAboveChoices(Geometry layout) {
        Rect menu = layout.menu();
        int bottom = Math.max(8, menu.y() - 8);
        int speechHeight = Math.min(layout.bubble().height(), Math.max(0, bottom - 8));
        return new Geometry(menu, new Rect(menu.x(), bottom - speechHeight, menu.width(), speechHeight), true);
    }

    /** Fills the frame interior; text padding belongs to each action, not its background. */
    static FooterLayout footer(Rect menu, int footerHeight) {
        int innerLeft = menu.x() + 4;
        int innerWidth = Math.max(2, menu.width() - 8);
        int half = innerWidth / 2;
        int dividerY = menu.bottom() - footerHeight + 1;
        int actionY = dividerY + 1;
        // The visible bottom stroke is three pixels from the edge. Cover its inner
        // shadow pixel so hover fills and the centre divider meet the stroke.
        int actionHeight = Math.max(1, menu.bottom() - 3 - actionY);
        return new FooterLayout(
                new Rect(innerLeft, dividerY, innerWidth, 1),
                new Rect(innerLeft, actionY, half, actionHeight),
                new Rect(innerLeft + half, actionY, innerWidth - half, actionHeight));
    }

    /** Reserve the strip below speech and above history controls, including in the docked layout. */
    static Rect progressStrip(Rect paper, int historyHeight, float contentScale) {
        int inset = Math.round(16 * contentScale);
        int top = paper.bottom() - historyHeight - Math.round(15 * contentScale);
        int thickness = Math.max(2, Math.round(3 * contentScale));
        if (paper.width() <= inset * 2 + 2) {
            return new Rect(0, 0, 0, 0);
        }
        int textTop = paper.y() + Math.round(23 * contentScale) + Math.round(8 * contentScale);
        int textBottom = Math.min(paper.bottom() - historyHeight - Math.round(12 * contentScale),
                top - Math.round(4 * contentScale));
        if (top < paper.y() + Math.round(42 * contentScale)
                || textBottom - textTop < Math.round(10 * contentScale)) {
            return new Rect(0, 0, 0, 0);
        }
        return new Rect(paper.x() + inset, top, paper.width() - inset * 2, thickness);
    }

    /** The speaker label stays clear of the upper-right speech-position toggle. */
    static Rect speakerHeader(Rect paper, float scale) {
        int left = paper.x() + Math.round(13 * scale);
        int top = paper.y() + Math.round(7 * scale);
        int right = Math.max(left, paper.right() - Math.round(32 * scale));
        int bottom = Math.min(paper.bottom(), top + Math.round(14 * scale));
        return new Rect(left, top, right - left, Math.max(0, bottom - top));
    }

    /** The pointer is placed on the straight left edge, above the lower rounded corner. */
    static Rect tailBounds(Rect paper) {
        return new Rect(paper.x() - 14, paper.bottom() - 28, 20, 20);
    }

    /** A traveling segment is indeterminate. Its initial off-track position is clipped when drawn. */
    static int progressSegmentStart(Rect strip, long nowMillis, boolean reducedMotion) {
        int segmentWidth = Math.max(1, strip.width() / 3);
        return reducedMotion ? strip.x() + (strip.width() - segmentWidth) / 2
                : strip.x() + (int) Math.floorMod(nowMillis / 25L,
                strip.width() + segmentWidth) - segmentWidth;
    }

    record FooterLayout(Rect divider, Rect back, Rect close) {
    }

    record SpeechViewport(int top, int bottom) {
    }

    record Rect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }
    }

    record Geometry(Rect menu, Rect bubble, boolean speechAboveChoices) {
        Geometry(Rect menu, Rect bubble) {
            this(menu, bubble, false);
        }

        boolean canShowTail() {
            return !speechAboveChoices && bubble.height() >= 40 && bubble.x() >= 14
                    && bubble.bottom() + 6 <= menu.y();
        }
    }
}
