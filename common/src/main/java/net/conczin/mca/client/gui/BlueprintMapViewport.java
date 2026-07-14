package net.conczin.mca.client.gui;

/**
 * Immutable per-frame map viewport shared by every Blueprint render layer.
 *
 * <p>All world/screen conversions use the same pixel-locked map center so terrain,
 * rooms, outlines, icons, hover tests and the player marker cannot drift apart from
 * independent rounding.</p>
 */
record BlueprintMapViewport(int centerX,
                            int centerY,
                            int left,
                            int top,
                            int right,
                            int bottom,
                            double mapCenterX,
                            double mapCenterZ,
                            float scale) {
    static BlueprintMapViewport create(int centerX,
                                       int centerY,
                                       int halfSize,
                                       double requestedMapCenterX,
                                       double requestedMapCenterZ,
                                       float scale) {
        if (scale <= 0.0F) {
            throw new IllegalArgumentException("scale must be positive");
        }

        double mapOriginX = Math.rint(centerX - requestedMapCenterX * scale);
        double mapOriginZ = Math.rint(centerY - requestedMapCenterZ * scale);
        double mapCenterX = (centerX - mapOriginX) / scale;
        double mapCenterZ = (centerY - mapOriginZ) / scale;
        return new BlueprintMapViewport(
                centerX,
                centerY,
                centerX - halfSize,
                centerY - halfSize,
                centerX + halfSize,
                centerY + halfSize,
                mapCenterX,
                mapCenterZ,
                scale
        );
    }

    double screenX(double worldX) {
        return centerX + (worldX - mapCenterX) * scale;
    }

    double screenY(double worldZ) {
        return centerY + (worldZ - mapCenterZ) * scale;
    }

    BlueprintMapFootprint.Cell screenToCell(double screenX, double screenY) {
        int worldX = (int) Math.floor((screenX - centerX) / scale + mapCenterX);
        int worldZ = (int) Math.floor((screenY - centerY) / scale + mapCenterZ);
        return new BlueprintMapFootprint.Cell(worldX, worldZ);
    }

    boolean containsInner(double screenX, double screenY) {
        return screenX >= left + 1 && screenX < right - 1
                && screenY >= top + 1 && screenY < bottom - 1;
    }

    int halfSize() {
        return (right - left) / 2;
    }

    ScreenPoint clampMarker(double markerScreenX,
                            double markerScreenY,
                            int markerSize,
                            int edgePadding) {
        double halfMarker = markerSize / 2.0D;
        double minCenterX = left + edgePadding + halfMarker;
        double maxCenterX = right - edgePadding - halfMarker;
        double minCenterY = top + edgePadding + halfMarker;
        double maxCenterY = bottom - edgePadding - halfMarker;

        double dx = markerScreenX - centerX;
        double dy = markerScreenY - centerY;
        boolean outside = markerScreenX < minCenterX || markerScreenX > maxCenterX
                || markerScreenY < minCenterY || markerScreenY > maxCenterY;
        double factor = 1.0D;
        if (outside) {
            double maxDx = Math.min(centerX - minCenterX, maxCenterX - centerX);
            double maxDy = Math.min(centerY - minCenterY, maxCenterY - centerY);
            double xFactor = dx == 0.0D ? Double.POSITIVE_INFINITY : maxDx / Math.abs(dx);
            double yFactor = dy == 0.0D ? Double.POSITIVE_INFINITY : maxDy / Math.abs(dy);
            factor = Math.min(xFactor, yFactor);
        }

        int x = (int) Math.round(centerX + dx * factor);
        int y = (int) Math.round(centerY + dy * factor);
        x = Math.max((int) Math.ceil(minCenterX), Math.min((int) Math.floor(maxCenterX), x));
        y = Math.max((int) Math.ceil(minCenterY), Math.min((int) Math.floor(maxCenterY), y));
        return new ScreenPoint(x, y);
    }

    record ScreenPoint(int x, int y) {
    }
}
