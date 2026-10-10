package net.conczin.mca.client.gui;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

final class VillagerEditorGalleryPolicy {
    private VillagerEditorGalleryPolicy() {
    }

    static boolean usesUnifiedGallery(String page) {
        return page.equals("clothing")
                || page.equals("hair")
                || page.equals("skin")
                || page.equals("eyes_catalog");
    }

    static boolean showPreviewControls(String page) {
        return true;
    }

    static GalleryLayout layout(int width, int height) {
        int searchY = Math.max(8, (height - 450) / 2 + 8);
        int filterY = searchY + 26;
        int previewControlsY = height - 58;
        int footerY = height - 34;
        int columns = width >= 344 ? 6 : 4;
        int itemsPerPage = columns * 2;
        int spacing = Math.min(104, (width - 24) / columns);
        int cardRadius = Math.max(1, (spacing - 8) / 2);
        int galleryTop = filterY + 28;
        int rowGap = 8;
        int heightLimitedRadius = Math.max(1, (previewControlsY - 8 - galleryTop - rowGap) / 4);
        int cardHalfHeight = Math.min(cardRadius * 3 / 2, heightLimitedRadius);
        int row0CenterY = galleryTop + cardHalfHeight;
        int row1CenterY = row0CenterY + cardHalfHeight * 2 + rowGap;

        return new GalleryLayout(
                searchY,
                filterY,
                row0CenterY,
                row1CenterY,
                footerY,
                previewControlsY,
                columns,
                itemsPerPage,
                spacing,
                cardRadius,
                cardHalfHeight
        );
    }

    static float fitPreviewScale(int cardWidth, int cardHeight, float modelHeight, float modelWidth, float zoom) {
        // Scale is pixels per world unit, not the model's final height in pixels.
        // Reserve space for the border and clamp zoom to the card's projected bounds.
        float fit = Math.min(Math.max(1, cardWidth - 12) / Math.max(0.01F, modelWidth),
                Math.max(1, cardHeight - 12) / Math.max(0.01F, modelHeight));
        return fit * Math.min(1.0F, 0.9F * zoom);
    }

    static <T> List<FilterOption<T>> filterOptions(List<T> filters, Predicate<T> hasItems, T allFilter) {
        return filters.stream()
                .map(filter -> new FilterOption<>(filter, Objects.equals(filter, allFilter) || hasItems.test(filter)))
                .toList();
    }

    record FilterOption<T>(T value, boolean available) {
    }

    record GalleryLayout(
            int searchY,
            int filterY,
            int row0CenterY,
            int row1CenterY,
            int footerY,
            int previewControlsY,
            int columns,
            int itemsPerPage,
            int spacing,
            int cardRadius,
            int cardHalfHeight
    ) {
    }
}
