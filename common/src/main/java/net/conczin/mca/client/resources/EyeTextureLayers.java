package net.conczin.mca.client.resources;

import com.mojang.blaze3d.platform.NativeImage;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

public final class EyeTextureLayers {
    private static final int SCLERA_MIN_CHANNEL = 160;
    private static final int SCLERA_MAX_CHANNEL_SPREAD = 32;
    private static final int IRIS_MIN_CHANNEL = 32;
    private static final int NATURAL_DYE = 0xFFFFFFFF;

    private static final int ALBINISM_EYE_COLOR = 0xFFE8A0A0;
    private static final int BLUE_EYE_COLOR = 0xFF3A98E8;
    private static final int GREEN_EYE_COLOR = 0xFF4CB346;
    private static final int HAZEL_EYE_COLOR = 0xFFC29B35;
    private static final int BROWN_EYE_COLOR = 0xFF7C4825;
    public static final int DETAILS_TINT = 0xFF808080;

    private EyeTextureLayers() {
    }

    public static int getStaticEyeColor(VillagerLike<?> villager, boolean left) {
        boolean heterochromia = villager.getTraits().hasTrait(Traits.HETEROCHROMIA);
        int dye = left && heterochromia ? villager.getEyeLeftDye() : villager.getEyeDye();
        return dye != NATURAL_DYE ? dye : getGeneticEyeColor(villager, left && heterochromia);
    }

    private static int getGeneticEyeColor(VillagerLike<?> villager, boolean shifted) {
        if (villager.getTraits().hasTrait(Traits.ALBINISM)) {
            return ALBINISM_EYE_COLOR;
        }

        float eyeColor = Mth.frac(villager.getGenetics().getGene(Genetics.FACE) + (shifted ? 0.43F : 0.0F));
        if (eyeColor < 0.35F) {
            return FastColor.ARGB32.lerp(eyeColor / 0.35F, BLUE_EYE_COLOR, GREEN_EYE_COLOR);
        }
        if (eyeColor < 0.70F) {
            return FastColor.ARGB32.lerp((eyeColor - 0.35F) / 0.35F, GREEN_EYE_COLOR, HAZEL_EYE_COLOR);
        }
        return FastColor.ARGB32.lerp((eyeColor - 0.70F) / 0.30F, HAZEL_EYE_COLOR, BROWN_EYE_COLOR);
    }

    public static boolean hasExplicitLayerMarker(int pixel) {
        return EyeTintPixel.isLayerMarker(FastColor.ABGR32.alpha(pixel));
    }

    public static boolean hasExplicitLayerMarker(NativeImage image) {
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (hasExplicitLayerMarker(image.getPixelRGBA(x, y))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int neutralMaskPixel(EyeTintPixel.Mask mask) {
        return FastColor.ABGR32.color(255, mask.intensity(), mask.intensity(), mask.intensity());
    }

    public static Bounds findBounds(NativeImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                int pixel = image.getPixelRGBA(x, y);
                int alpha = FastColor.ABGR32.alpha(pixel);
                if (alpha == 0) {
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

    private static boolean isScleraPixel(int pixel) {
        int alpha = FastColor.ABGR32.alpha(pixel);
        if (alpha != 255) {
            return false;
        }

        int red = FastColor.ABGR32.red(pixel);
        int green = FastColor.ABGR32.green(pixel);
        int blue = FastColor.ABGR32.blue(pixel);
        int min = Math.min(red, Math.min(green, blue));
        int max = Math.max(red, Math.max(green, blue));
        return min >= SCLERA_MIN_CHANNEL && max - min <= SCLERA_MAX_CHANNEL_SPREAD;
    }

    public static boolean isPixelForLayer(Layer layer, int pixel) {
        return layer == layerForPixel(pixel);
    }

    public static Layer layerForPixel(int pixel) {
        int alpha = FastColor.ABGR32.alpha(pixel);
        if (alpha == 0) {
            return null;
        }

        if (EyeTintPixel.isIrisMarker(alpha)) {
            return Layer.IRIS;
        }
        if (EyeTintPixel.isFixedMarker(alpha)) {
            return Layer.SCLERA;
        }

        boolean sclera = isScleraPixel(pixel);
        int red = FastColor.ABGR32.red(pixel);
        int green = FastColor.ABGR32.green(pixel);
        int blue = FastColor.ABGR32.blue(pixel);
        int max = Math.max(red, Math.max(green, blue));
        if (sclera) {
            return Layer.SCLERA;
        }
        return max >= IRIS_MIN_CHANNEL ? Layer.IRIS : Layer.DETAILS;
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
