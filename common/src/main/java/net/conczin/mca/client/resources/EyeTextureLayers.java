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
        if (dye != NATURAL_DYE) {
            return dye;
        }
        return getGeneticEyeColor(villager, left && heterochromia);
    }

    private static int getGeneticEyeColor(VillagerLike<?> villager, boolean shifted) {
        if (villager.getTraits().hasTrait(Traits.ALBINISM)) {
            return ALBINISM_EYE_COLOR;
        }

        float eyeColor = Mth.frac(
                villager.getGenetics().getGene(Genetics.FACE) + (shifted ? 0.43F : 0.0F)
        );
        if (eyeColor < 0.35F) {
            return FastColor.ARGB32.lerp(eyeColor / 0.35F, BLUE_EYE_COLOR, GREEN_EYE_COLOR);
        }
        if (eyeColor < 0.70F) {
            return FastColor.ARGB32.lerp((eyeColor - 0.35F) / 0.35F, GREEN_EYE_COLOR, HAZEL_EYE_COLOR);
        }
        return FastColor.ARGB32.lerp((eyeColor - 0.70F) / 0.30F, HAZEL_EYE_COLOR, BROWN_EYE_COLOR);
    }

    /**
     * Adjusts an ARGB iris colour without washing it toward white. The gene's
     * default value of 0.5 leaves the natural colour unchanged.
     *
     * @param argb       base ARGB colour
     * @param brightness gene value in [0, 1] — 0 = darker, 0.5 = natural, 1 = brighter
     */
    public static int applyBrightness(int argb, float brightness) {
        brightness = Mth.clamp(brightness, 0.0F, 1.0F);
        float factor = 0.5F + brightness;
        int a = (argb >>> 24) & 0xFF;
        int r = scaleChannel((argb >>> 16) & 0xFF, factor);
        int g = scaleChannel((argb >>> 8) & 0xFF, factor);
        int b = scaleChannel(argb & 0xFF, factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int scaleChannel(int channel, float factor) {
        return Mth.clamp(Math.round(channel * factor), 0, 255);
    }

    public static Bounds findBounds(NativeImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                int pixel = image.getPixelRGBA(x, y);
                int alpha = (pixel >> 24) & 0xFF;
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
