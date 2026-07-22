package net.conczin.mca.client.resources;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.util.ARGB;

public final class EyeTextureLayers {
    private static final int SCLERA_MIN_CHANNEL = 160;
    private static final int SCLERA_MAX_CHANNEL_SPREAD = 32;
    private static final int IRIS_MIN_CHANNEL = 32;
    public static final int DETAILS_TINT = 0xFF808080;

    private EyeTextureLayers() {
    }

    public static Bounds findBounds(NativeImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (ARGB.alpha(image.getPixel(x, y)) == 0) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        if (maxX < minX || maxY < minY) {
            throw new IllegalStateException("Face eye texture has no visible pixels");
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    public static boolean isInSide(int x, int splitX, Side side) {
        return switch (side) {
            case FULL -> true;
            case LEFT -> x >= splitX;
            case RIGHT -> x < splitX;
        };
    }

    public static boolean isScleraPixel(int alpha, int red, int green, int blue) {
        if (alpha == 1) {
            return true;
        }
        if (alpha != 255) {
            return false;
        }

        int min = Math.min(red, Math.min(green, blue));
        int max = Math.max(red, Math.max(green, blue));
        return min >= SCLERA_MIN_CHANNEL && max - min <= SCLERA_MAX_CHANNEL_SPREAD;
    }

    public static boolean isPixelForLayer(Layer layer, int alpha, int red, int green, int blue) {
        if (alpha == 0) {
            return false;
        }

        boolean sclera = isScleraPixel(alpha, red, green, blue);
        int max = Math.max(red, Math.max(green, blue));
        return switch (layer) {
            case SCLERA -> sclera;
            case IRIS -> !sclera && max >= IRIS_MIN_CHANNEL;
            case DETAILS -> !sclera && max < IRIS_MIN_CHANNEL;
        };
    }

    public enum Side {
        FULL,
        LEFT,
        RIGHT
    }

    public enum Layer {
        SCLERA,
        IRIS,
        DETAILS
    }

    public record Bounds(int minX, int minY, int maxX, int maxY) {
        public int width() {
            return maxX - minX + 1;
        }
    }
}
