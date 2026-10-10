package net.conczin.mca.client.resources;

import com.mojang.blaze3d.platform.NativeImage;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.resources.EyeDefinition;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;

public final class EyeTextureLayers {
    private static final int NATURAL_DYE = 0xFFFFFFFF;
    private static final int IRIS_MARKER_ALPHA = 254;

    private static final int ALBINISM_EYE_COLOR = 0xFFE8A0A0;
    private static final int BLUE_EYE_COLOR = 0xFF3A98E8;
    private static final int GREEN_EYE_COLOR = 0xFF4CB346;
    private static final int HAZEL_EYE_COLOR = 0xFFC29B35;
    private static final int BROWN_EYE_COLOR = 0xFF7C4825;
    private EyeTextureLayers() {
    }

    public static int getStaticEyeColor(VillagerLike<?> villager, boolean left) {
        boolean heterochromia = villager.getTraits().hasTrait(Traits.HETEROCHROMIA);
        int dye = left && heterochromia ? villager.getEyeLeftDye() : villager.getEyeDye();
        return dye != NATURAL_DYE ? dye : getGeneticEyeColor(villager, left && heterochromia);
    }

    public static int getBaseEyeColor(VillagerLike<?> villager, boolean left, float tickDelta) {
        if (!villager.getTraits().hasTrait(Traits.RAINBOW_EYES)) {
            return getStaticEyeColor(villager, left);
        }

        int colorCount = DyeColor.values().length;
        int offset = left && villager.getTraits().hasTrait(Traits.HETEROCHROMIA)
                ? (25 * colorCount) / 2
                : 0;
        Entity entity = villager.asEntity();
        int ticks = Math.abs(entity.tickCount) + offset;
        int first = (ticks / 25 + entity.getId()) % colorCount;
        float mix = ((float)(ticks % 25) + tickDelta) / 25.0F;
        return FastColor.ARGB32.lerp(
                mix,
                Sheep.getColor(DyeColor.byId(first)),
                Sheep.getColor(DyeColor.byId((first + 1) % colorCount))
        );
    }

    private static int getGeneticEyeColor(VillagerLike<?> villager, boolean shifted) {
        if (villager.getTraits().hasTrait(Traits.ALBINISM)) {
            return ALBINISM_EYE_COLOR;
        }

        float eyeColor = Mth.frac(villager.getGenetics().getGene(Genetics.EYE_COLOR) + (shifted ? 0.43F : 0.0F));
        if (eyeColor < 0.35F) {
            return FastColor.ARGB32.lerp(eyeColor / 0.35F, BLUE_EYE_COLOR, GREEN_EYE_COLOR);
        }
        if (eyeColor < 0.70F) {
            return FastColor.ARGB32.lerp((eyeColor - 0.35F) / 0.35F, GREEN_EYE_COLOR, HAZEL_EYE_COLOR);
        }
        return FastColor.ARGB32.lerp((eyeColor - 0.70F) / 0.30F, HAZEL_EYE_COLOR, BROWN_EYE_COLOR);
    }

    public static DecodedPixel decodePixel(int pixel) {
        int alpha = FastColor.ABGR32.alpha(pixel);
        if (alpha == 0) {
            return null;
        }
        if (alpha != IRIS_MARKER_ALPHA) {
            return DecodedPixel.fixed(pixel);
        }

        int red = FastColor.ABGR32.red(pixel);
        int green = FastColor.ABGR32.green(pixel);
        int blue = FastColor.ABGR32.blue(pixel);
        if (activeChannels(red, green, blue) != 1) {
            return DecodedPixel.fixed(pixel);
        }

        Tone tone;
        int intensity;
        if (red > 0) {
            tone = Tone.SHADOW;
            intensity = red;
        } else if (green > 0) {
            tone = Tone.PRIMARY;
            intensity = green;
        } else {
            tone = Tone.HIGHLIGHT;
            intensity = blue;
        }
        int neutralPixel = 0xFF000000 | (intensity << 16) | (intensity << 8) | intensity;
        return DecodedPixel.tint(tone, neutralPixel);
    }

    public static EyeDefinition.Tones resolveTones(EyeDefinition definition, int selectedArgb, float brightness) {
        EyeDefinition.Tones tones = definition.tones(selectedArgb);
        return new EyeDefinition.Tones(
                applyBrightness(tones.shadow(), brightness),
                applyBrightness(tones.primary(), brightness),
                applyBrightness(tones.highlight(), brightness)
        );
    }

    public static int multiplyPixel(int packedAbgr, int tintArgb) {
        int tintRed = (tintArgb >>> 16) & 0xFF;
        int tintGreen = (tintArgb >>> 8) & 0xFF;
        int tintBlue = tintArgb & 0xFF;
        int tintAlpha = (tintArgb >>> 24) & 0xFF;

        int alpha = ((packedAbgr >>> 24) & 0xFF) * tintAlpha / 255;
        int red = (packedAbgr & 0xFF) * tintRed / 255;
        int green = ((packedAbgr >>> 8) & 0xFF) * tintGreen / 255;
        int blue = ((packedAbgr >>> 16) & 0xFF) * tintBlue / 255;
        return (alpha << 24) | (blue << 16) | (green << 8) | red;
    }

    private static int activeChannels(int red, int green, int blue) {
        return (red > 0 ? 1 : 0) + (green > 0 ? 1 : 0) + (blue > 0 ? 1 : 0);
    }

    private static int applyBrightness(int argb, float brightness) {
        float factor = 0.5F + Mth.clamp(brightness, 0.0F, 1.0F);
        int alpha = (argb >>> 24) & 0xFF;
        int red = scaleChannel((argb >>> 16) & 0xFF, factor);
        int green = scaleChannel((argb >>> 8) & 0xFF, factor);
        int blue = scaleChannel(argb & 0xFF, factor);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
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

    public enum Side {
        FULL,
        LEFT,
        RIGHT
    }

    public enum PixelKind {
        FIXED,
        TINT
    }

    public enum Tone {
        SHADOW,
        PRIMARY,
        HIGHLIGHT
    }

    public record DecodedPixel(PixelKind kind, Tone tone, int pixel) {
        private static DecodedPixel fixed(int pixel) {
            return new DecodedPixel(PixelKind.FIXED, null, pixel);
        }

        private static DecodedPixel tint(Tone tone, int pixel) {
            return new DecodedPixel(PixelKind.TINT, tone, pixel);
        }
    }

    public record Bounds(int minX, int minY, int maxX, int maxY) {
        public int width() {
            return maxX - minX + 1;
        }
    }
}
