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
        return !usesUnifiedGallery(page);
    }

    static GalleryLayout layout(int width, int height) {
        int centerY = height / 2;
        int searchY = centerY - 116;
        int filterY = centerY - 93;
        boolean compact = width < 560 || height < 300;
        int columns = compact ? 4 : 6;
        int itemsPerPage = columns * 2;
        int spacing = compact
                ? Math.min(88, Math.max(48, (width - 24) / columns))
                : Math.min(72, Math.max(54, (width - 32) / columns));
        int widthLimitedRadius = Math.max(22, (spacing - 8) / 2);
        int heightLimitedRadius = Math.max(22, (height - filterY - 66) / 4);
        int cardRadius = Math.min(compact ? 36 : 32, Math.min(widthLimitedRadius, heightLimitedRadius));

        int row0CenterY = filterY + 20 + 8 + cardRadius;
        int row1CenterY = row0CenterY + cardRadius * 2 + 8;
        int footerY = row1CenterY + cardRadius + (compact ? 10 : 34);
        int previewSize = Math.max(30, cardRadius + 4);

        return new GalleryLayout(
                searchY,
                filterY,
                row0CenterY,
                row1CenterY,
                footerY,
                columns,
                itemsPerPage,
                spacing,
                cardRadius,
                previewSize,
                previewSize
        );
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
            int columns,
            int itemsPerPage,
            int spacing,
            int cardRadius,
            int previewSize,
            int hoveredPreviewSize
    ) {
    }
}
