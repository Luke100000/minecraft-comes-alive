package net.conczin.mca.client.gui;

/** Shared text, icon and disclosure coordinates for dialogue menu rows. */
final class DialogueRowLayout {
    enum Kind {
        FEATURED, CATEGORY, CHILD, ORDINARY
    }

    private DialogueRowLayout() {
    }

    static Insets insets(int panelWidth, float scale, Kind kind) {
        int iconX = Math.round(16 * scale);
        int textX = Math.round(switch (kind) {
            case FEATURED, CATEGORY -> 36;
            case CHILD -> 44;
            case ORDINARY -> 16;
        } * scale);
        int trailingX = panelWidth - Math.round(22 * scale);
        int textRight = kind == Kind.FEATURED || kind == Kind.CATEGORY
                ? trailingX - Math.round(8 * scale)
                : panelWidth - Math.round(16 * scale);
        return new Insets(iconX, textX, Math.max(1, textRight - textX), trailingX);
    }

    static int height(float scale, int wrappedLines, Kind kind) {
        int singleLine = switch (kind) {
            case FEATURED -> 28;
            case CATEGORY -> 24;
            case CHILD, ORDINARY -> 22;
        };
        return Math.round(Math.max(singleLine, Math.max(1, wrappedLines) * 10 + 10) * scale);
    }

    static DialogueOverlayLayout.Rect surface(int left, int panelWidth, int y, int rowHeight) {
        return new DialogueOverlayLayout.Rect(left + 8, y + 1, panelWidth - 16, rowHeight - 2);
    }

    /** Inset the loading strip from the prompt scissor edges, with symmetric ends. */
    static DialogueOverlayLayout.Rect loadingSurface(int left, int panelWidth, int y, int rowHeight, float scale) {
        int inset = Math.round(16 * scale);
        return new DialogueOverlayLayout.Rect(left + inset, y + 1,
                Math.max(1, panelWidth - inset * 2), rowHeight - 2);
    }

    static DialogueOverlayLayout.Rect featuredHoverFill(DialogueOverlayLayout.Rect frame) {
        return new DialogueOverlayLayout.Rect(frame.x() + 4, frame.y() + 4,
                frame.width() - 8, frame.height() - 7);
    }

    record Insets(int iconX, int textX, int textWidth, int trailingX) {
    }

}
