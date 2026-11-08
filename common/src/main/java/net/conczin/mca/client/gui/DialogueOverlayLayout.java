package net.conczin.mca.client.gui;

/** Geometry in scaled GUI pixels. The world and entity remain visible behind both panels. */
final class DialogueOverlayLayout {
    private DialogueOverlayLayout() {
    }

    static Geometry calculate(int width, int height, int menuHeight, int bubbleHeight) {
        int sideMargin = Math.max(12, Math.min(24, width / 32));
        int bottomMargin = Math.max(12, Math.min(34, height / 12));
        int menuWidth = Math.max(1, Math.min(width - sideMargin * 2, 560));
        int maximumMenuHeight = Math.max(1, Math.min(height - bottomMargin - 26, height * 3 / 5));
        int actualMenuHeight = Math.min(Math.max(1, menuHeight), maximumMenuHeight);
        Rect menu = new Rect((width - menuWidth) / 2, height - bottomMargin - actualMenuHeight,
                menuWidth, actualMenuHeight);

        if (bubbleHeight <= 0) {
            return new Geometry(menu, new Rect(0, 0, 0, 0));
        }
        int bubbleTop = Math.max(12, Math.min(50, height / 11));
        int bubbleWidth = Math.max(1, Math.min(Math.min(420, width * 2 / 5), width / 2 - 16));
        int maximumBubbleHeight = Math.max(0, menu.y() - bubbleTop - 8);
        Rect bubble = new Rect(width - sideMargin - bubbleWidth, bubbleTop, bubbleWidth,
                Math.min(bubbleHeight, maximumBubbleHeight));
        return new Geometry(menu, bubble);
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
    }
}
