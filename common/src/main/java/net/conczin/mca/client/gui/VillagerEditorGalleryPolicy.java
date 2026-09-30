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
        int spacing = Math.min(88, Math.max(48, (width - 24) / 4));
        int widthLimitedRadius = Math.max(22, (spacing - 8) / 2);
        int heightLimitedRadius = Math.max(22, (height - filterY - 66) / 4);
        int cardRadius = Math.min(39, Math.min(widthLimitedRadius, heightLimitedRadius));

        int row0CenterY = filterY + 20 + 8 + cardRadius;
        int row1CenterY = row0CenterY + cardRadius * 2 + 8;
        int footerY = row1CenterY + cardRadius + 10;
        int previewSize = Math.max(30, cardRadius + 4);

        return new GalleryLayout(
                searchY,
                filterY,
                row0CenterY,
                row1CenterY,
                footerY,
                spacing,
                cardRadius,
                previewSize,
                previewSize + 4
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
            int spacing,
            int cardRadius,
            int previewSize,
            int hoveredPreviewSize
    ) {
    }
}
