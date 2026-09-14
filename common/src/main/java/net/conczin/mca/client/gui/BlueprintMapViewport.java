package net.conczin.mca.client.gui;

/**
 * Immutable per-frame map viewport shared by every Blueprint render layer.
 *
 * <p>All world/screen conversions use the same exact map center so terrain, rooms,
 * outlines, icons, hover tests and the player marker share one projection.</p>
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

        return new BlueprintMapViewport(
                centerX,
                centerY,
                centerX - halfSize,
                centerY - halfSize,
                centerX + halfSize,
                centerY + halfSize,
                requestedMapCenterX,
                requestedMapCenterZ,
                scale
        );
    }

    double screenX(double worldX) {
        return centerX + (worldX - mapCenterX) * scale;
    }

    double screenY(double worldZ) {
        return centerY + (worldZ - mapCenterZ) * scale;
    }

    double worldX(double screenX) {
        return mapCenterX + (screenX - centerX) / scale;
    }

    double worldZ(double screenY) {
        return mapCenterZ + (screenY - centerY) / scale;
    }

    BlueprintMapFootprint.Cell screenToCell(double screenX, double screenY) {
        return new BlueprintMapFootprint.Cell(
                (int) Math.floor(worldX(screenX)),
                (int) Math.floor(worldZ(screenY))
        );
    }

    BlueprintMapViewport zoomedAround(double screenX, double screenY, float newScale) {
        double cursorWorldX = worldX(screenX);
        double cursorWorldZ = worldZ(screenY);
        double newCenterX = cursorWorldX - (screenX - centerX) / newScale;
        double newCenterZ = cursorWorldZ - (screenY - centerY) / newScale;
        return create(centerX, centerY, halfSize(), newCenterX, newCenterZ, newScale);
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

        double x = centerX + dx * factor;
        double y = centerY + dy * factor;
        x = Math.max(minCenterX, Math.min(maxCenterX, x));
        y = Math.max(minCenterY, Math.min(maxCenterY, y));
        return new ScreenPoint(x, y);
    }

    record ScreenPoint(double x, double y) {
    }
}
