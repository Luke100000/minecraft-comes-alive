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

    static Geometry calculate(int width, int height, int menuHeight, int bubbleHeight) {
        return calculate(width, height, menuHeight, bubbleHeight, Integer.MAX_VALUE);
    }

    static Geometry calculate(int width, int height, int menuHeight, int bubbleHeight, int desiredBubbleWidth) {
        int sideMargin = Math.max(12, Math.min(24, width / 32));
        int bottomMargin = Math.max(6, Math.min(16, height / 32));
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

    /** The footer sits entirely inside the eight-pixel menu frame on every side. */
    static FooterLayout footer(Rect menu, int footerHeight) {
        int innerLeft = menu.x() + 8;
        int innerWidth = Math.max(2, menu.width() - 16);
        int half = innerWidth / 2;
        int actionY = menu.bottom() - footerHeight + 5;
        int actionHeight = Math.max(1, footerHeight - 12);
        return new FooterLayout(
                new Rect(innerLeft, menu.bottom() - footerHeight + 1, innerWidth, 1),
                new Rect(innerLeft, actionY, Math.max(1, half - 2), actionHeight),
                new Rect(innerLeft + half + 2, actionY, Math.max(1, innerWidth - half - 2), actionHeight));
    }

    record FooterLayout(Rect divider, Rect back, Rect close) {
    }

    record Rect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }
    }

    record Geometry(Rect menu, Rect bubble) {
        boolean canShowTail() {
            return bubble.height() > 0 && bubble.bottom() + 24 <= menu.y();
        }
    }
}
